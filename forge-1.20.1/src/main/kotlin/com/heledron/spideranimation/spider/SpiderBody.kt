package com.heledron.spideranimation.spider

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.ecs.Ecs
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.platform.isOnGround
import com.heledron.spideranimation.platform.raycastGround
import com.heledron.spideranimation.platform.resolveCollision
import com.heledron.spideranimation.util.*
import net.minecraft.server.level.ServerLevel
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector2d
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class SpiderBodyHitGroundEvent(val spider: SpiderBody)

// Terminal speed (blocks/tick) for the symmetric height correction's downward pull — fast enough
// that a shrinking giant's body keeps pace with the ~0.7s shrink (it must drop ~15 blocks), slow
// enough not to slam into the ground (the pull tapers to the exact remaining gap near arrival).
private const val MAX_DESCENT_SPEED = 1.2

/*
 * Ported from `spider/components/body/SpiderBody.kt`. Bukkit World/Vector/Location replaced with
 * ServerLevel / Vector3d / (level + position + orientation). The physics, gait blending, support
 * polygon and normal-force logic are otherwise faithful to the original.
 */
class SpiderBody(
    val level: ServerLevel,
    val position: Vector3d,
    val orientation: Quaternionf,
    var bodyPlan: BodyPlan,
    var gallopGait: Gait,
    var walkGait: Gait,
) {
    var onGround = false; private set
    var legs: List<Leg> = emptyList()
    var normal: NormalInfo? = null; private set
    var normalAcceleration = Vector3d(0.0, 0.0, 0.0); private set

    // params
    var gallop = false
    val gait get() = if (gallop) gallopGait else walkGait

    // state
    var isWalking = false
    var isRotatingYaw = false

    // When true, something else (a rider or the /spider possession) is driving this body's
    // behaviour — the default "chase the nearest player" system must leave it alone.
    var manualControl = false

    // Which visual variant this body belongs to ("netherite", "camo", ...). The default variant
    // always keeps its iconic netherite step/fall sounds; other variants use the configurable
    // variant sounds (see the LegStepEvent handler in AppState).
    var variantKey = "netherite"

    // Idle grooming state (see updateGrooming): 0 = not grooming, else ticks remaining.
    private var stationaryTimer = 0
    private var groomingTimer = 0
    private val groomingOriginalPos0 = Vector3d()
    private val groomingOriginalPos1 = Vector3d()

    // The spider's current absolute physical size (1.0 = as spawned). Driven by SpiderMob from the
    // nearest player's distance (close = small, far = large). Scales the body plan + gait so the IK
    // re-solves and the feet stay planted at any size — see [setSizeScale].
    var sizeScale = 1.0; private set

    fun lerpedGait(): LerpGait {
        if (isRotatingYaw) return gait.moving.clone()
        val speedFraction = velocity.length() / gait.maxSpeed
        return gait.stationary.clone().lerp(gait.moving, speedFraction)
    }

    companion object {
        fun fromSpawn(
            level: ServerLevel,
            position: Vector3d,
            yaw: Float,
            pitch: Float,
            bodyPlan: BodyPlan,
            gallopGait: Gait,
            walkGait: Gait,
        ): SpiderBody {
            // yaw/pitch in degrees, mirroring Location.yawRadians()/pitchRadians()
            val orientation = Quaternionf().rotationYXZ((-yaw).toRadians(), pitch.toRadians(), 0f)
            return SpiderBody(level, position, orientation, bodyPlan, gallopGait = gallopGait, walkGait = walkGait)
        }
    }

    fun forwardDirection() = FORWARD_VECTOR.rotate(Quaterniond(orientation))

    // memo
    var preferredPitch = orientation.getEulerAnglesYXZ(Vector3f()).x
    var preferredRoll = orientation.getEulerAnglesYXZ(Vector3f()).z
    var preferredOrientation = Quaternionf(orientation)

    val velocity = Vector3d(0.0, 0.0, 0.0)
    val rotationalVelocity = Vector3f(0f, 0f, 0f)

    fun accelerateRotation(axis: Vector3d, angle: Float) {
        val acceleration = Quaternionf().rotateAxis(angle, axis.toV3f())
        val oldVelocity = Quaternionf().rotationYXZ(rotationalVelocity.y, rotationalVelocity.x, rotationalVelocity.z)
        val rotVelocity = acceleration.mul(oldVelocity)
        rotationalVelocity.set(rotVelocity.getEulerAnglesYXZ(Vector3f()))
    }

    fun teleport(newPosition: Vector3d) {
        val diff = Vector3d(newPosition).sub(position)
        position.set(newPosition)
        for (leg in legs) leg.endEffector.add(diff)
    }

    /**
     * Set the spider's absolute physical size (1.0 = as spawned). Scales the body plan (leg spread +
     * segment lengths) and the gait (stance height + step/zone distances) so the IK legs re-target
     * and the feet stay planted at any size. Movement speeds/accelerations are intentionally left
     * unscaled. Negligible changes are a cheap no-op so we don't thrash the gait every tick.
     */
    fun setSizeScale(target: Double) {
        val ratio = target / sizeScale
        // Fine 0.1% deadband: scale must track the distance CONTINUOUSLY in micro-steps. A coarser
        // deadband (tried 1%) makes the size change in discrete chunks every few ticks — each chunk
        // pops the gait/legs/body forward slightly, which reads as periodic forward lurching.
        if (ratio in 0.999..1.001) return

        bodyPlan.scale(ratio)
        walkGait.scaleSize(ratio)
        gallopGait.scaleSize(ratio)

        // Chains are only rebuilt on a segment-COUNT change, so push the freshly scaled segment
        // lengths into the existing IK chains; otherwise the rendered segments desync from the
        // solved joints (and the leg tip stops reaching the ground — the "floating legs" bug).
        for (leg in legs) {
            for ((i, segment) in leg.chain.segments.withIndex()) {
                segment.length = leg.legPlan.segments.getOrNull(i)?.length ?: segment.length
            }
        }

        // Posture-preserving rescale: the zones (trigger/comfort) just scaled around rest positions,
        // but the FEET didn't move — without this, a rescale throws several legs outside their
        // zones at once and they re-step in a synchronized wave (a visible forward lurch), and
        // per-tick micro-rescaling keeps legs perpetually "uncomfortable", which gates the walk
        // speed to zero (the stuck-spider bug). Scale each foot's HORIZONTAL offset from the body
        // by the same ratio (leave y — feet stay planted), so feet and zones stay aligned.
        for (leg in legs) {
            leg.endEffector.x = position.x + (leg.endEffector.x - position.x) * ratio
            leg.endEffector.z = position.z + (leg.endEffector.z - position.z) * ratio
        }

        sizeScale = target
    }

    // Absolute travel-speed multiplier (1.0 = the base ~4 blocks/sec). See [setSpeedScale].
    var speedScale = 1.0; private set

    /**
     * Scale the body's *travel* speed (and matching acceleration) to an absolute factor, independent
     * of the physical [setSizeScale]. Driven by SpiderMob so the spider rushes in fast from far away
     * and eases back to the ~4 blocks/sec baseline as it closes in. Leg-swing speed already scales
     * with size (and the spider is biggest exactly when it's fastest), so the legs keep pace.
     */
    fun setSpeedScale(target: Double) {
        val ratio = target / speedScale
        if (ratio in 0.999..1.001) return   // same fine deadband as setSizeScale
        for (gait in listOf(walkGait, gallopGait)) {
            gait.maxSpeed *= ratio
            gait.moveAcceleration *= ratio
        }
        speedScale = target
    }

    private fun updatePreferredAngles() {
        val currentEuler = orientation.getEulerAnglesYXZ(Vector3f())

        if (gait.disableAdvancedRotation) {
            preferredPitch = 0f
            preferredRoll = 0f
            preferredOrientation = Quaternionf().rotationYXZ(currentEuler.y, 0f, 0f)
            return
        }

        fun getPos(leg: Leg): Vector3d = leg.groundPosition ?: leg.restPosition

        val frontLeft = getPos(legs.getOrNull(0) ?: return)
        val frontRight = getPos(legs.getOrNull(1) ?: return)
        val backLeft = getPos(legs.getOrNull(legs.size - 2) ?: return)
        val backRight = getPos(legs.getOrNull(legs.size - 1) ?: return)

        val forwardLeft = frontLeft.copy().subtract(backLeft)
        val forwardRight = frontRight.copy().subtract(backRight)
        val forward = listOf(forwardLeft, forwardRight).average()

        val sideways = Vector3d(0.0, 0.0, 0.0)
        for (i in 0 until legs.size step 2) {
            val left = legs.getOrNull(i) ?: continue
            val right = legs.getOrNull(i + 1) ?: continue
            sideways.add(getPos(right).copy().subtract(getPos(left)))
        }

        preferredPitch = forward.pitch().lerp(preferredPitch, gait.preferredRotationLerpFraction)
        preferredRoll = sideways.pitch().lerp(preferredRoll, gait.preferredRotationLerpFraction)

        if (preferredPitch < gait.preferLevelBreakpoint) preferredPitch *= 1 - gait.preferLevelBias
        if (preferredRoll < gait.preferLevelBreakpoint) preferredRoll *= 1 - gait.preferLevelBias

        preferredOrientation = Quaternionf().rotationYXZ(currentEuler.y, preferredPitch, preferredRoll)
    }

    fun init(ecs: Ecs, entity: EcsEntity) {
        legs = bodyPlan.legs.map { Leg(ecs, entity, this, it) }
    }

    fun update(ecs: Ecs, entity: EcsEntity) {
        if (legs.isEmpty()) {
            init(ecs, entity)
            if (legs.isEmpty()) return
        }

        updatePreferredAngles()

        val groundedLegs = legs.filter { it.isGrounded() }
        val fractionOfLegsGrounded = groundedLegs.size.toDouble() / legs.size

        // gravity + air resistance
        velocity.y -= gait.gravityAcceleration
        velocity.y *= (1 - gait.airDragCoefficient)

        // apply rotational velocity
        val rotVelocity = Quaternionf().rotationYXZ(rotationalVelocity.y, rotationalVelocity.x, rotationalVelocity.z)
        orientation.set(rotVelocity.mul(orientation))

        // drag while leg on ground
        if (!isWalking) {
            val legDrag = 1 - gait.groundDragCoefficient * fractionOfLegsGrounded
            velocity.x *= legDrag
            velocity.z *= legDrag
        }

        // rotational drag
        val rotDrag = 1 - gait.rotationalDragCoefficient * fractionOfLegsGrounded.toFloat()
        rotationalVelocity.mul(rotDrag)

        // drag while body on ground
        if (onGround) {
            val bodyDrag = .5f
            velocity.x *= bodyDrag
            velocity.z *= bodyDrag
            rotationalVelocity.mul(bodyDrag)
        }

        val normal = calcNormal()
        this.normal = normal

        normalAcceleration = Vector3d(0.0, 0.0, 0.0)
        if (normal != null) {
            val preferredY = calcPreferredY()
            val preferredYAcceleration = (preferredY - position.y - velocity.y).coerceAtLeast(0.0)
            val capableAcceleration = gait.bodyHeightCorrectionAcceleration * fractionOfLegsGrounded
            val accelerationMagnitude = min(preferredYAcceleration, capableAcceleration)

            normalAcceleration = normal.normal.copy().multiply(accelerationMagnitude)
            if (normalAcceleration.horizontalLength() > normalAcceleration.y) normalAcceleration.multiply(0.0)
            velocity.add(normalAcceleration)
        }

        // Symmetric height correction: the normal force above only ever pushes UP, so a spider
        // whose preferred height just DROPPED (i.e. it's shrinking) would otherwise be stranded in
        // the air, descending on base gravity alone with its shortened legs dangling — shrinking
        // "takes forever" and the stranded legs destabilise the gait. Actively pull the body down
        // toward its preferred height, with a terminal-speed cap so it descends decisively but
        // lands softly (the pull tapers to exactly the remaining gap near arrival).
        run {
            val preferredY = calcPreferredY()
            val excess = position.y + velocity.y - preferredY
            if (excess > 0.0) {
                velocity.y -= min(excess, gait.bodyHeightCorrectionAcceleration)
                if (velocity.y < -MAX_DESCENT_SPEED) velocity.y = -MAX_DESCENT_SPEED
            }
        }

        // apply velocity
        position.add(velocity)

        // resolve collision
        val collision = level.resolveCollision(position, Vector3d(0.0, min(-1.0, -abs(velocity.y)), 0.0))
        if (collision != null) {
            onGround = true
            val didHit = collision.offset.length() > (gait.gravityAcceleration * 2) * (1 - gait.airDragCoefficient)
            if (didHit) ecs.emit(SpiderBodyHitGroundEvent(spider = this))

            position.y = collision.position.y
            if (velocity.y < 0) velocity.y *= -gait.bounceFactor
            if (velocity.y < gait.gravityAcceleration) velocity.y = .0
        } else {
            onGround = level.isOnGround(position, DOWN_VECTOR.rotate(orientation))
        }

        val updateOrder = gait.type.getLegsInUpdateOrder(this)
        for (leg in updateOrder) leg.updateMemo()
        for (leg in updateOrder) leg.update()

        // Idle grooming overrides the front legs' pose, so it must run AFTER the leg updates.
        updateGrooming()

        updatePreferredAngles()
    }

    // ---- Idle grooming (contributed by NetherySiloX; completed & integrated here) ------------
    // Only while wandering is DISABLED: once the spider has stood still for 3 seconds, a
    // per-second chance (Config.GROOMING_CHANCE) starts a 5-second sequence — the front leg pair
    // lifts to the mouth and "cleans" with a smooth sweeping rub (opposite phases per leg, eased
    // in/out), while calcPreferredY leans the body down. The legs are flagged isDisabled so the
    // gait leaves them alone, and the ease returns them to their stored ground positions before
    // the flag is lifted — no snap at either end.
    private fun updateGrooming() {
        val speedSq = velocity.lengthSquared()

        if (!Config.ENABLE_WANDERING.get()) {
            if (speedSq < 0.001) {
                stationaryTimer++
                if (groomingTimer <= 0 && stationaryTimer > 60) {   // still for 3s before considering it
                    val chancePerTick = Config.GROOMING_CHANCE.get() / 20.0
                    if (chancePerTick > 0 && Random.nextDouble() < chancePerTick) beginGrooming()
                }
            } else {
                stationaryTimer = 0
                // Real movement interrupts grooming; tiny physics jitter (< 0.01 sq) does not.
                if (speedSq > 0.01 && groomingTimer > 0) cancelGrooming()
            }
        } else {
            stationaryTimer = 0
            if (groomingTimer > 0) cancelGrooming()
        }

        if (groomingTimer <= 0) return
        val leg0 = legs.getOrNull(0)
        val leg1 = legs.getOrNull(1)
        if (leg0 == null || leg1 == null) { cancelGrooming(); return }

        val progress = 1.0 - groomingTimer / 100.0
        val smoothT = groomingSmoothT()

        val forward = FORWARD_VECTOR.rotate(Quaterniond(orientation))
        val up = UP_VECTOR.rotate(Quaterniond(orientation))
        val right = forward.cross(up, Vector3d()).normalize()

        // The "mouth": just in front of and slightly below the body centre.
        val mouthPos = position.copy()
            .add(forward.copy().mul(0.5 * sizeScale))
            .add(up.copy().mul(-0.05 * sizeScale))

        // Rub only through the middle of the sequence (fades in after the lift, out before the return).
        val rubIntensity = when {
            progress < 0.2 || progress > 0.8 -> 0.0
            progress < 0.3 -> (progress - 0.2) / 0.1
            progress > 0.7 -> (0.8 - progress) / 0.1
            else -> 1.0
        }

        for (i in 0..1) {
            val leg = if (i == 0) leg0 else leg1
            val originalPos = if (i == 0) groomingOriginalPos0 else groomingOriginalPos1
            val sideDir = if (i == 0) 1.0 else -1.0   // legs[0] = front-right, legs[1] = front-left

            val targetPos = mouthPos.copy().add(right.copy().mul(0.15 * sideDir * sizeScale))

            if (rubIntensity > 0.0) {
                // Smooth, deliberate sweeping motion: 3 full cycles, opposite phase per leg.
                val rubPhase = progress * Math.PI * 6.0 + if (i == 0) 0.0 else Math.PI
                targetPos.add(up.copy().mul(sin(rubPhase) * 0.08 * sizeScale * rubIntensity))
                targetPos.add(forward.copy().mul(cos(rubPhase) * 0.04 * sizeScale * rubIntensity))
            }

            // Arc from the stored ground position up to the mouth; smoothT eases back to 0 at the
            // end, so the same lerp carries the leg home. The renderer re-solves the leg chain
            // from endEffector every pass, so steering the end effector is all that's needed.
            val arcHeight = sin(smoothT * Math.PI) * 0.4 * sizeScale
            leg.endEffector.set(originalPos.copy().lerp(targetPos, smoothT).add(up.copy().mul(arcHeight)))
        }

        groomingTimer--
        if (groomingTimer == 0) cancelGrooming()
    }

    /** Smoothstep envelope for the grooming sequence: eases in over the first 20%, holds, eases out. */
    private fun groomingSmoothT(): Double {
        val progress = 1.0 - groomingTimer / 100.0
        val lerpFactor = when {
            progress < 0.2 -> progress / 0.2
            progress > 0.8 -> (1.0 - progress) / 0.2
            else -> 1.0
        }
        return lerpFactor * lerpFactor * (3 - 2 * lerpFactor)
    }

    private fun beginGrooming() {
        val leg0 = legs.getOrNull(0) ?: return
        val leg1 = legs.getOrNull(1) ?: return
        if (leg0.isMoving || leg1.isMoving) return   // wait for a tick when both feet are planted
        groomingTimer = 100   // 5 seconds
        leg0.isDisabled = true
        leg1.isDisabled = true
        groomingOriginalPos0.set(leg0.endEffector)
        groomingOriginalPos1.set(leg1.endEffector)
    }

    private fun cancelGrooming() {
        legs.getOrNull(0)?.isDisabled = false
        legs.getOrNull(1)?.isDisabled = false
        groomingTimer = 0
    }

    private fun legsInPolygonalOrder(): List<Int> {
        val lefts = legs.indices.filter { LegLookUp.isLeftLeg(it) }
        val rights = legs.indices.filter { LegLookUp.isRightLeg(it) }
        return lefts + rights.reversed()
    }

    private fun calcPreferredY(): Double {
        val lookAhead = position.copy().add(velocity)
        val ground = level.raycastGround(lookAhead, DOWN_VECTOR.rotate(preferredOrientation), lerpedGait().bodyHeight)
        val groundY = ground?.y ?: -Double.MAX_VALUE

        val averageY = legs.map { it.target.position.y }.average() + lerpedGait().bodyHeight

        val pivot = gait.legChainPivotMode.get(this)
        val target = UP_VECTOR.rotate(pivot).multiply(gait.maxBodyDistanceFromGround)
        var targetY = max(averageY, groundY + target.y)

        // ---- Idle breathing + grooming lean (contributed by NetherySiloX; only while wandering
        // is DISABLED, and blended out as soon as the spider actually moves) -------------------
        if (!Config.ENABLE_WANDERING.get()) {
            val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
            val breathingFactor = (1.0 - horizontalSpeed / 0.05).coerceIn(0.0, 1.0)
            if (breathingFactor > 0.0) {
                // ~4.5 s per breath cycle; depth scales with the spider's size.
                targetY += sin(level.gameTime * 0.07) * 0.08 * sizeScale * breathingFactor
            }
            // Lean the body down toward the front legs while grooming.
            if (groomingTimer > 0) targetY -= groomingSmoothT() * 0.15 * sizeScale
        }

        return position.y.lerp(targetY, gait.bodyHeightCorrectionFactor)
    }

    private fun applyStabilization(normal: NormalInfo) {
        if (normal.origin == null) return
        if (normal.centreOfMass == null) return

        if (normal.origin.horizontalDistance(normal.centreOfMass) < gait.polygonLeeway) {
            normal.origin.x = normal.centreOfMass.x
            normal.origin.z = normal.centreOfMass.z
        }

        val stabilizationTarget = normal.origin.copy().setY(normal.centreOfMass.y)
        normal.centreOfMass.lerp(stabilizationTarget, gait.stabilizationFactor)
        normal.normal.set(normal.centreOfMass).sub(normal.origin).normalize()
    }

    private fun calcLegacyNormal(): NormalInfo? {
        val pairs = LegLookUp.diagonalPairs(legs.indices.toList())
        if (pairs.any { pair -> pair.mapNotNull { legs.getOrNull(it) }.all { it.isGrounded() } }) {
            return NormalInfo(normal = Vector3d(0.0, 1.0, 0.0))
        }
        return null
    }

    private fun calcNormal(): NormalInfo? {
        if (gait.useLegacyNormalForce) return calcLegacyNormal()

        val centreOfMass = legs.map { it.endEffector }.average()
        centreOfMass.lerp(position, 0.5)
        centreOfMass.y += 0.01

        val groundedLegs = legsInPolygonalOrder().map { legs[it] }.filter { it.isGrounded() }
        if (groundedLegs.isEmpty()) return null

        val legsPolygon = groundedLegs.map { it.endEffector.copy() }
        val polygonCenterY = legsPolygon.map { it.y }.average()

        if (legsPolygon.size == 1) {
            val origin = groundedLegs.first().endEffector.copy()
            return NormalInfo(
                normal = centreOfMass.copy().subtract(origin).normalize(),
                origin = origin,
                centreOfMass = centreOfMass,
                contactPolygon = legsPolygon,
            ).apply { applyStabilization(this) }
        }

        val polygon2D = legsPolygon.map { Vector2d(it.x, it.z) }

        if (pointInPolygon(Vector2d(centreOfMass.x, centreOfMass.z), polygon2D)) return NormalInfo(
            normal = Vector3d(0.0, 1.0, 0.0),
            origin = Vector3d(centreOfMass.x, polygonCenterY, centreOfMass.z),
            centreOfMass = centreOfMass,
            contactPolygon = legsPolygon,
        )

        val point = nearestPointInPolygon(Vector2d(centreOfMass.x, centreOfMass.z), polygon2D)
        val origin = Vector3d(point.x, polygonCenterY, point.y)
        return NormalInfo(
            normal = centreOfMass.copy().subtract(origin).normalize(),
            origin = origin,
            centreOfMass = centreOfMass,
            contactPolygon = legsPolygon,
        ).apply { applyStabilization(this) }
    }
}

class NormalInfo(
    val normal: Vector3d,
    val origin: Vector3d? = null,
    val contactPolygon: List<Vector3d>? = null,
    val centreOfMass: Vector3d? = null,
)

fun setupSpiderBody(ecs: Ecs) {
    ecs.onTick {
        for ((entity, spider) in ecs.query<EcsEntity, SpiderBody>()) {
            spider.update(ecs, entity)
        }
    }
}
