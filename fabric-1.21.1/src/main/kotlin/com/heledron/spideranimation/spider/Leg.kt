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
