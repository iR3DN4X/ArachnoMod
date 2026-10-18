package com.heledron.spideranimation.spider

import com.heledron.spideranimation.ecs.Ecs
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.util.*
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.PI

/*
 * Ported from `spider/components/Behaviour.kt`. The TridentHitDetector hooks are dropped from this
 * foundation (trident knock-back is deferred); everything else is faithful.
 */

interface SpiderBehaviour
class StayStillBehaviour : SpiderBehaviour
class TargetBehaviour(val target: Vector3d, val distance: Double) : SpiderBehaviour
class DirectionBehaviour(val targetDirection: Vector3d, val walkDirection: Vector3d) : SpiderBehaviour

fun setupBehaviours(ecs: Ecs) {
    ecs.onTick {
        for ((_, spider, _) in ecs.query<EcsEntity, SpiderBody, StayStillBehaviour>()) {
            spider.walkAt(Vector3d(0.0, 0.0, 0.0))
            spider.rotateTowards(spider.forwardDirection().setY(0.0))
        }
    }

    ecs.onTick {
        for ((_, spider, behaviour) in ecs.query<EcsEntity, SpiderBody, TargetBehaviour>()) {
            val direction = behaviour.target.copy().subtract(spider.position).normalize()
            spider.rotateTowards(direction)

            val currentSpeed = spider.velocity.length()
            val decelerateDistance = (currentSpeed * currentSpeed) / (2 * spider.gait.moveAcceleration)
            val currentDistance = spider.position.horizontalDistance(behaviour.target)

            if (currentDistance > behaviour.distance + decelerateDistance) {
                spider.walkAt(direction.copy().multiply(spider.gait.maxSpeed))
            } else {
                spider.walkAt(Vector3d(0.0, 0.0, 0.0))
            }
        }
    }

    ecs.onTick {
        for ((_, spider, behaviour) in ecs.query<EcsEntity, SpiderBody, DirectionBehaviour>()) {
            spider.rotateTowards(behaviour.targetDirection)
            spider.walkAt(behaviour.walkDirection.copy().multiply(spider.gait.maxSpeed))
        }
    }
}

private fun SpiderBody.rotateTowards(targetVector: Vector3d) {
    val currentEuler = orientation.getEulerAnglesYXZ(Vector3f())

    val targetEuler = Quaternionf()
        .rotationTo(FORWARD_VECTOR.toV3f(), targetVector.toV3f())
        .getEulerAnglesYXZ(Vector3f())

    targetEuler.x = targetEuler.x.coerceIn(preferredPitch - gait.preferredPitchLeeway, preferredPitch + gait.preferredPitchLeeway)
    targetEuler.z = preferredRoll
    if (legs.any { it.isUncomfortable && !it.isMoving }) targetEuler.y = currentEuler.y

    val diffEuler = Vector3f(targetEuler).sub(currentEuler)
    if (diffEuler.y > PI) diffEuler.y -= 2 * PI.toFloat()
    if (diffEuler.y < -PI) diffEuler.y += 2 * PI.toFloat()

    isRotatingYaw = (diffEuler.x + diffEuler.y + diffEuler.z) > 0.001f
    diffEuler.lerp(Vector3f(), gait.rotationLerp)

    val diff = Quaternionf().rotationYXZ(diffEuler.y, diffEuler.x, diffEuler.z)
    val conjugated = Quaternionf(orientation).mul(diff).mul(Quaternionf(orientation).invert())
    val conjugatedEuler = conjugated.getEulerAnglesYXZ(Vector3f())

    val maxAcceleration = gait.rotateAcceleration * legs.filter { it.isGrounded() }.size / legs.size
    rotationalVelocity.moveTowards(conjugatedEuler, maxAcceleration)
}

private fun SpiderBody.walkAt(targetVelocity: Vector3d) {
    val acceleration = gait.moveAcceleration
    val target = targetVelocity.copy()

    if (legs.any { it.isUncomfortable && !it.isMoving }) {
        val scaled = target.setY(velocity.y).multiply(gait.uncomfortableSpeedMultiplier)
        velocity.moveTowards(scaled, acceleration)
        isWalking = targetVelocity.x != 0.0 && targetVelocity.z != 0.0
    } else {
        velocity.moveTowards(target.setY(velocity.y), acceleration)
        isWalking = velocity.x != 0.0 && velocity.z != 0.0
    }
}
