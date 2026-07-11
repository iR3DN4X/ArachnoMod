package com.heledron.spideranimation.spider

import com.heledron.spideranimation.util.SplitDistance
import com.heledron.spideranimation.util.lerp
import com.heledron.spideranimation.util.toRadians
import org.joml.Quaternionf
import org.joml.Vector3f

/*
 * Ported from `spider/configuration/Gait.kt` and `spider/components/body/{GaitType,LegLookUp}.kt`.
 * This is the heart of *when* each leg decides to take a step. Ported faithfully.
 */

class LerpGait(var bodyHeight: Double, var triggerZone: SplitDistance) {
    fun scale(scale: Double): LerpGait { bodyHeight *= scale; triggerZone = triggerZone.scale(scale); return this }
    fun clone() = LerpGait(bodyHeight, triggerZone)
    fun lerp(target: LerpGait, factor: Double): LerpGait {
        bodyHeight = bodyHeight.lerp(target.bodyHeight, factor)
        triggerZone = triggerZone.lerp(target.triggerZone, factor)
        return this
    }
}

class Gait(walkSpeed: Double, val type: GaitType) {
    companion object {
        fun defaultWalk() = Gait(.15, GaitType.WALK).apply {
            // Faster pursuit. maxSpeed is in blocks/tick (*20 = blocks/sec cap); real chase cruise
            // lands a bit under. .4 -> ~4 blocks/sec feel. Tune maxSpeed to taste.
            maxSpeed = .4
            moveAcceleration = .15  // ramp up to speed quickly so it doesn't feel sluggish
            legMoveSpeed = 1.1      // quick scurry: each step swings ~40% faster
            // Tighter moving trigger zone: legs step SOONER (less lag behind the body), raising
            // the step cadence — the "scurry". Default was .8.
            moving.triggerZone = SplitDistance(.6, 1.5)
            // KEEP MOVING WHILE GROWING. Growth sweeps the comfort zones every tick, so legs keep
            // flickering "uncomfortable". The default multiplier of 0.0 hard-stops walking when any
            // leg is uncomfortable — during growth that (a) freezes the spider mid-grow, and (b)
            // flickers speed between 0 and full at the 8x-scaled acceleration, which reads as
            // violent forward teleporting. 0.6 (the original's own gallop tuning) keeps a 60% floor:
            // no freeze, and the flicker becomes a gentle ripple. The wider comfort zone (default
            // 1.2/1.6) gives resizing legs extra slack so the flicker is rare to begin with.
            uncomfortableSpeedMultiplier = .6
            comfortZone = SplitDistance(1.4, 1.8)
        }
        fun defaultGallop() = Gait(.4, GaitType.GALLOP).apply {
            moving.bodyHeight = 1.6
            legMoveSpeed = .5
            rotateAcceleration = .25f / 4
            uncomfortableSpeedMultiplier = .6
            samePairCooldown = 2
            crossPairCooldown = 4
            polygonLeeway = .5
        }
    }

    var stationary = LerpGait(bodyHeight = 1.1, triggerZone = SplitDistance(.25, 1.5))
    var moving = LerpGait(bodyHeight = 1.1, triggerZone = SplitDistance(.8, 1.5))

    var maxBodyDistanceFromGround = .25
    var maxSpeed = walkSpeed
    var moveAcceleration = .15 / 4

    var rotateAcceleration = .15f / 4
    var rotationalDragCoefficient = .2f

    var legMoveSpeed = walkSpeed * 2.5
    var legLiftHeight = .35
    var legDropDistance = legLiftHeight

    var comfortZone = SplitDistance(1.2, 1.6)

    var gravityAcceleration = .08
    var airDragCoefficient = .02
    var bounceFactor = .5

    var bodyHeightCorrectionAcceleration = gravityAcceleration * 4
    var bodyHeightCorrectionFactor = .25

    var legScanAlternativeGround = true
    var legScanHeightBias = .5

    var legLookAheadFraction = .6
    var groundDragCoefficient = .2

    var samePairCooldown = 1
    var crossPairCooldown = 1

    var useLegacyNormalForce = false
    var polygonLeeway = .0
    var stabilizationFactor = .0

    var uncomfortableSpeedMultiplier = 0.0

    var disableAdvancedRotation = false
    var preferredPitchLeeway = 10f.toRadians()

    var straightenLegs = true
    var legStraightenRotation = (-80f).toRadians()

    var scanPivotMode = PivotMode.YAxis
    var legChainPivotMode = PivotMode.SpiderOrientation

    var preferLevelBreakpoint = 45f.toRadians()
    var preferLevelBias = .0f
    var preferredRotationLerpFraction = .3f

    var rotationLerp = .3f

    /**
     * Scale the spider's physical *size* — stance height + step/zone distances — without touching
     * movement speeds, accelerations, or drag, so a resized spider keeps its feet planted and its
     * gait stable. Called by [SpiderBody.setSizeScale].
     */
    fun scaleSize(factor: Double) {
        stationary.scale(factor)
        moving.scale(factor)
        maxBodyDistanceFromGround *= factor
        legLiftHeight *= factor
        legDropDistance *= factor
        legMoveSpeed *= factor
        comfortZone = comfortZone.scale(factor)
    }
}

enum class PivotMode(val get: (spider: SpiderBody) -> Quaternionf) {
    YAxis({ spider -> Quaternionf().rotateY(spider.orientation.getEulerAnglesYXZ(Vector3f()).y) }),
    SpiderOrientation({ spider -> spider.orientation }),
    GroundOrientation({ spider -> spider.preferredOrientation }),
}

enum class GaitType(
    val canMoveLeg: (Leg) -> Boolean,
    val getLegsInUpdateOrder: (SpiderBody) -> List<Leg>,
) {
    WALK(WalkGaitType::canMoveLeg, WalkGaitType::getLegsInUpdateOrder),
    GALLOP(GallopGaitType::canMoveLeg, GallopGaitType::getLegsInUpdateOrder),
}

object WalkGaitType {
    fun getLegsInUpdateOrder(spider: SpiderBody): List<Leg> {
        val legs = spider.legs
        val diagonal1 = legs.indices.filter { LegLookUp.isDiagonal1(it) }
        val diagonal2 = legs.indices.filter { LegLookUp.isDiagonal2(it) }
        return (diagonal1 + diagonal2).map { spider.legs[it] }
    }

    fun canMoveLeg(leg: Leg): Boolean {
        val spider = leg.spider
        val index = spider.legs.indexOf(leg)

        if (!leg.target.isGrounded) return true

        leg.isPrimary = true

        val crossPair = unIndexLeg(spider, LegLookUp.adjacent(index))
        if (crossPair.any { !it.isGrounded() && !it.isDisabled && it.target.isGrounded }) return false
        if (crossPair.any { it.target.isGrounded && it.timeSinceStopMove < spider.gait.crossPairCooldown }) return false

        val samePair = unIndexLeg(spider, LegLookUp.diagonal(index))
        if (samePair.any { it.target.isGrounded && it.timeSinceBeginMove < spider.gait.samePairCooldown }) return false

        val wantsToMove = leg.isOutsideTriggerZone || !leg.touchingGround
        val alreadyAtTarget = leg.endEffector.distanceSquared(leg.target.position) < 0.01
        val onGround = spider.legs.any { it.isGrounded() } || spider.onGround

        return wantsToMove && !alreadyAtTarget && onGround
    }
}

object GallopGaitType {
    fun getLegsInUpdateOrder(spider: SpiderBody): List<Leg> = WalkGaitType.getLegsInUpdateOrder(spider)

    fun canMoveLeg(leg: Leg): Boolean {
        val spider = leg.spider
        val index = spider.legs.indexOf(leg)

        if (!spider.isWalking) return WalkGaitType.canMoveLeg(leg)
        if (!leg.target.isGrounded) return true

        val onGround = spider.legs.any { it.isGrounded() } || spider.onGround
        if (!onGround) return false

        val pair = spider.legs[LegLookUp.horizontal(index)]
        leg.isPrimary = LegLookUp.isDiagonal1(index) || pair.isDisabled || !pair.target.isGrounded

        return if (leg.isPrimary) {
            val front = spider.legs.getOrNull(LegLookUp.diagonalFront(index))
            if (listOfNotNull(front).any { leg.target.isGrounded && (leg.timeSinceBeginMove < spider.gait.crossPairCooldown) }) return false
            leg.isOutsideTriggerZone || !leg.touchingGround
        } else {
            val hasCooldown = pair.target.isGrounded && (pair.timeSinceBeginMove < spider.gait.samePairCooldown)
            pair.isMoving && !hasCooldown
        }
    }
}

fun unIndexLeg(spider: SpiderBody, indices: List<Int>): List<Leg> =
    indices.mapNotNull { spider.legs.getOrNull(it) }

object LegLookUp {
    fun diagonalPairs(legs: List<Int>): List<List<Int>> = legs.map { diagonal(it) + it }
    fun isLeftLeg(leg: Int): Boolean = leg % 2 == 0
    fun isRightLeg(leg: Int): Boolean = !isLeftLeg(leg)
    fun getPairIndex(leg: Int): Int = leg / 2
    fun isDiagonal1(leg: Int): Boolean = if (getPairIndex(leg) % 2 == 0) isLeftLeg(leg) else isRightLeg(leg)
    fun isDiagonal2(leg: Int): Boolean = !isDiagonal1(leg)
    fun diagonalFront(leg: Int): Int = if (isLeftLeg(leg)) leg - 1 else leg - 3
    fun diagonalBack(leg: Int): Int = if (isLeftLeg(leg)) leg + 3 else leg + 1
    fun front(leg: Int): Int = leg - 2
    fun back(leg: Int): Int = leg + 2
    fun horizontal(leg: Int): Int = if (isLeftLeg(leg)) leg + 1 else leg - 1
    fun diagonal(leg: Int): List<Int> = listOf(diagonalFront(leg), diagonalBack(leg))
    fun adjacent(leg: Int): List<Int> = listOf(front(leg), back(leg), horizontal(leg))
}
