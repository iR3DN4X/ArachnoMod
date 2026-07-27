package com.heledron.spideranimation.spider

import com.heledron.spideranimation.ecs.Ecs
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.platform.isOnGround
import com.heledron.spideranimation.platform.isPassableAt
import com.heledron.spideranimation.platform.raycastGround
import com.heledron.spideranimation.platform.resolveCollision
import com.heledron.spideranimation.util.*
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector3d
import kotlin.math.ceil
import kotlin.math.floor

class LegStepEvent(val entity: EcsEntity, val spider: SpiderBody, val leg: Leg)

class Leg(
    val ecs: Ecs,
    val entity: EcsEntity,
    val spider: SpiderBody,
    var legPlan: LegPlan,
) {
    // memo (recomputed each tick from the spider's current state)
    lateinit var triggerZone: SplitDistanceZone; private set
    lateinit var comfortZone: SplitDistanceZone; private set
    var groundPosition: Vector3d? = null; private set
    lateinit var restPosition: Vector3d; private set
    lateinit var lookAheadPosition: Vector3d; private set
    lateinit var scanStartPosition: Vector3d; private set
    lateinit var scanVector: Vector3d; private set
    lateinit var attachmentPosition: Vector3d; private set

    init { updateMemo() }

    // state
    var target = locateGround() ?: strandedTarget()
    var endEffector = target.position.copy()
    var previousEndEffector = endEffector.copy()
    var chain = KinematicChain(Vector3d(0.0, 0.0, 0.0), listOf())

    var touchingGround = true; private set
    var isMoving = false; private set
    var timeSinceBeginMove = 0; private set
    var timeSinceStopMove = 0; private set
    private var ticksOffGround = 0

    var isDisabled = false
    var isPrimary = false
    var canMove = false

    val isOutsideTriggerZone: Boolean get() = !triggerZone.contains(endEffector)
    val isUncomfortable: Boolean get() = !comfortZone.contains(endEffector)

    fun isGrounded(): Boolean = touchingGround && !isMoving && !isDisabled

    fun updateMemo() {
        val lerpedGait = spider.lerpedGait()
        val orientation = spider.gait.scanPivotMode.get(spider)

        val upVector = UP_VECTOR.rotate(Quaterniond(orientation))
        val scanStartAxis = upVector.copy().multiply(lerpedGait.bodyHeight * 1.6)
        val scanAxis = upVector.copy().multiply(-lerpedGait.bodyHeight * 3.5)

        restPosition = legPlan.restPosition.copy()
        restPosition.add(upVector.copy().multiply(-lerpedGait.bodyHeight))
        restPosition.rotate(orientation).add(spider.position)

        triggerZone = SplitDistanceZone(restPosition, lerpedGait.triggerZone)

        val comfortZoneCenter = restPosition.copy()
        comfortZoneCenter.y = restPosition.y.lerp(spider.position.y, .5)
        val comfortZoneSize = SplitDistance(
            horizontal = spider.gait.comfortZone.horizontal,
            vertical = spider.gait.comfortZone.vertical + (spider.position.y - restPosition.y).coerceAtLeast(.0)
        )
        comfortZone = SplitDistanceZone(comfortZoneCenter, comfortZoneSize)

        lookAheadPosition = lookAheadPosition(restPosition, triggerZone.size.horizontal)

        scanStartPosition = lookAheadPosition.copy().add(scanStartAxis)
        scanVector = scanAxis

        attachmentPosition = legPlan.attachmentPosition.copy().rotate(spider.orientation).add(spider.position)
    }

    fun update() {
        legPlan = spider.bodyPlan.legs.getOrNull(spider.legs.indexOf(this)) ?: legPlan
        updateMovement()
        chain = chain()
    }

    private fun updateMovement() {
        previousEndEffector = endEffector.copy()

        val gait = spider.gait
        var didStep = false

        timeSinceBeginMove += 1
        timeSinceStopMove += 1

        val ground = locateGround()
        groundPosition = ground?.position

        if (isDisabled) {
            target = disabledTarget()
        } else {
            if (ground != null) target = ground
            if (!target.isGrounded || !comfortZone.contains(target.position)) target = strandedTarget()
        }

        // OUT OF REACH: a foot the leg physically cannot reach any more. FABRIK responds by
        // pointing the whole chain straight at it, which is exactly the spike of legs standing
        // upright after a hard fall — the body outran its feet, they stayed up on the ledge,
        // and nothing let go of them. Reach is the honest test here: it is scale-correct (a
        // giant's legs reach much further than a small one's) and it applies whether the spider
        // is still falling or has already landed, which the old airborne-only check did not.
        // The foot is also pulled back inside the leg's reach so it never renders as a spike
        // for even one frame; the gait then steps it down normally.
        val reach = legPlan.segments.sumOf { it.length }
        if (reach > 0.0 && endEffector.distance(attachmentPosition) > reach * 0.95) {
            touchingGround = false
            val pullBack = endEffector.copy().subtract(attachmentPosition)
            if (pullBack.lengthSquared() > 1.0e-6) {
                pullBack.normalize().multiply(reach * 0.9)
                endEffector.set(attachmentPosition.copy().add(pullBack))
            }
        }

        // FREE-FALL: release feet the body has outrun, so the legs fall WITH it in their
        // splayed pose and re-plant together on impact instead of trailing behind it. The
        // comfort-zone test keeps this clear of an ordinary shrink-descent, where the feet stay
        // at or below their rest height.
        if (touchingGround && !spider.onGround &&
            endEffector.y - restPosition.y > comfortZone.size.vertical) {
            touchingGround = false
        }

        // inherit parent velocity while airborne
        if (!isGrounded()) {
            endEffector.add(spider.velocity)
            endEffector.rotateAroundY(spider.rotationalVelocity.y.toDouble(), spider.position)
        }

        // resolve ground collision
        if (!touchingGround) {
            val collision = spider.level.resolveCollision(endEffector, DOWN_VECTOR)
            if (collision != null) {
                didStep = true
                touchingGround = true
                endEffector.y = collision.position.y
            }
        }

        if (isMoving) {
            val legMoveSpeed = gait.legMoveSpeed
            endEffector.moveTowards(target.position, legMoveSpeed)

            val targetY = target.position.y + gait.legLiftHeight
            val hDistance = endEffector.horizontalDistance(target.position)
            if (hDistance > gait.legDropDistance) {
                endEffector.y = endEffector.y.moveTowards(targetY, legMoveSpeed)
            }

            if (endEffector.distance(target.position) < 0.0001) {
                isMoving = false
                touchingGround = touchingGround()
                didStep = touchingGround
            }
        } else {
            canMove = spider.gait.type.canMoveLeg(this)
            if (canMove) {
                isMoving = true
                timeSinceBeginMove = 0
            }
        }

        // STUCK RECOVERY. Whatever else went wrong, a leg should never hang in the air while
        // the spider is standing on solid ground. If one does — a stranded target it can't
        // step off, a foot left behind by a fall, ground that moved out from under it — hunt
        // straight down from where the foot belongs and put it back. Cheap, and it means no
        // single stuck leg can wedge the gait (its neighbours wait on it) for more than a
        // second.
        // NOTE the support test: `spider.onGround` means the BODY is resting on the ground,
        // which for this spider is almost never true — it stands with its body held up at leg
        // height. Gating recovery on that (as a first attempt did) meant it effectively never
        // ran. What matters is whether the spider is standing at all, i.e. some other leg is
        // carrying it.
        // Don't test `isMoving` here: a leg parked on a mid-air target flickers it on and off
        // every single tick (fly to the phantom target, arrive, get re-authorised, repeat),
        // which resets any counter that looks at it. Time spent off the ground is the honest
        // measure — a real step only ever takes a handful of ticks at any size.
        val supported = spider.onGround || spider.legs.any { it !== this && it.isGrounded() }
        if (!touchingGround && supported) ticksOffGround++ else ticksOffGround = 0
        if (ticksOffGround > 8) {   // ~0.4s worst case; a real step never takes half that
            ticksOffGround = 0
            val bodyHeight = spider.lerpedGait().bodyHeight
            val lift = Vector3d(0.0, bodyHeight * 1.5, 0.0)
            var hit = spider.level.raycastGround(restPosition.copy().add(lift), DOWN_VECTOR, bodyHeight * 4.0)
            // Nothing under where the foot belongs — it is dangling over a drop. Walk the probe
            // back in toward the body until it finds footing, so the leg tucks onto solid
            // ground at the lip instead of hanging in the void forever.
            if (hit == null) {
                val inward = spider.position.copy().subtract(restPosition)
                inward.y = 0.0
                if (inward.lengthSquared() > 1.0e-6) {
                    inward.normalize()
                    for (step in 1..4) {
                        val probe = restPosition.copy()
                            .add(inward.copy().multiply(step * 0.5 * bodyHeight))
                            .add(lift)
                        hit = spider.level.raycastGround(probe, DOWN_VECTOR, bodyHeight * 4.0)
                        if (hit != null) break
                    }
                }
            }
            if (hit != null) {
                endEffector.set(hit)
                target = LegTarget(position = hit.copy(), isGrounded = true, id = -2)
                touchingGround = true
                isMoving = false
                didStep = true
            }
        }

        if (didStep) ecs.emit(LegStepEvent(entity = entity, spider = spider, leg = this))
    }

    private fun chain(): KinematicChain {
        if (chain.segments.size != legPlan.segments.size) {
            var stride = 0.0
            chain = KinematicChain(attachmentPosition, legPlan.segments.map {
                stride += it.length
                val position = spider.position.copy().add(legPlan.restPosition.copy().normalize().multiply(stride))
                ChainSegment(position, it.length, it.initDirection)
            })
        }

        chain.root.set(attachmentPosition)

        if (spider.gait.straightenLegs) {
            val pivot = Quaternionf(spider.gait.legChainPivotMode.get(spider))
            val direction = endEffector.copy().subtract(attachmentPosition)
            val rotation = direction.getRotationAroundAxis(pivot)
            rotation.x += spider.gait.legStraightenRotation
            val orientation = pivot.rotateYXZ(rotation.y, rotation.x, .0f)
            chain.straightenDirection(orientation)
        }

        chain.fabrik(endEffector)
        return chain
    }

    private fun touchingGround(): Boolean =
        spider.level.isOnGround(endEffector, DOWN_VECTOR.rotate(spider.orientation))

    private fun lookAheadPosition(restPosition: Vector3d, triggerZoneRadius: Double): Vector3d {
        if (!spider.isWalking) return restPosition
        val direction = if (spider.velocity.isZero) spider.forwardDirection() else spider.velocity.copy().normalize()
        val lookAhead = direction.multiply(triggerZoneRadius * spider.gait.legLookAheadFraction).add(restPosition)
        lookAhead.rotateAroundY(spider.rotationalVelocity.y.toDouble(), spider.position)
        return lookAhead
    }

    private fun locateGround(): LegTarget? {
        val lookAhead = lookAheadPosition
        val scanLength = scanVector.length()
        val level = spider.level

        var id = 0
        fun rayCast(x: Double, z: Double): LegTarget? {
            id += 1
            val start = Vector3d(x, scanStartPosition.y, z)
            val hit = level.raycastGround(start, scanVector, scanLength) ?: return null
            return LegTarget(position = hit, isGrounded = true, id = id)
        }

        val x = scanStartPosition.x
        val z = scanStartPosition.z
        val mainCandidate = rayCast(x, z)

        if (!spider.gait.legScanAlternativeGround) return mainCandidate

        if (mainCandidate != null && mainCandidate.position.y in (lookAhead.y - 0.24)..(lookAhead.y + 1.5)) {
            return mainCandidate
        }

        val margin = 2 / 16.0
        val nx = floor(x) - margin
        val nz = floor(z) - margin
        val pz = ceil(z) + margin
        val px = ceil(x) + margin

        val candidates = listOf(
            rayCast(nx, nz), rayCast(nx, z), rayCast(nx, pz),
            rayCast(x, nz), mainCandidate, rayCast(x, pz),
            rayCast(px, nz), rayCast(px, z), rayCast(px, pz),
        )

        val preferredPosition = lookAhead.copy()
        val front = lookAhead.copy().add(spider.forwardDirection().copy().multiply(1.0))
        if (!level.isPassableAt(front)) preferredPosition.y += spider.gait.legScanHeightBias

        val best = candidates.filterNotNull().minByOrNull { it.position.distanceSquared(preferredPosition) }
        if (best != null && !comfortZone.contains(best.position)) return null
        return best
    }

    private fun strandedTarget(): LegTarget =
        LegTarget(position = lookAheadPosition.copy(), isGrounded = false, id = -1)

    private fun disabledTarget(): LegTarget {
        val lerpedGait = spider.lerpedGait()
        val upVector = UP_VECTOR.rotate(spider.orientation)
        val target = strandedTarget()
        target.position.add(upVector.copy().multiply(lerpedGait.bodyHeight * .5))
        val minY = (groundPosition?.y ?: -Double.MAX_VALUE) + lerpedGait.bodyHeight * .1
        target.position.y = target.position.y.coerceAtLeast(minY)
        return target
    }
}

class LegTarget(val position: Vector3d, val isGrounded: Boolean, val id: Int)
