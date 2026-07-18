package com.heledron.spideranimation.spider

import com.heledron.spideranimation.util.FORWARD_VECTOR
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Matrix4f
import org.joml.Vector3d

/*
 * Ported from `spider/presets/presets.kt` + `applyLegModels.kt`.
 *
 * The original "bot" presets used hand-authored BlockDisplay art (FLAT torso + mechanical leg
 * segments parsed from /summon NBT). That art is deferred: here every preset uses the procedural
 * LINE leg model, and the bot presets get a simple placeholder netherite torso. The leg *geometry*
 * (segment counts / lengths / rest positions / robot joint angles) is faithful to the original.
 */

private val NETHERITE: BlockState get() = Blocks.NETHERITE_BLOCK.defaultBlockState()
private val MOSS: BlockState get() = Blocks.MOSS_BLOCK.defaultBlockState()
private val SLIME: BlockState get() = Blocks.SLIME_BLOCK.defaultBlockState()

private fun equalLength(segmentCount: Int, length: Double): List<SegmentPlan> =
    List(segmentCount) { SegmentPlan(length, FORWARD_VECTOR) }

private fun BodyPlan.addLegPair(root: Vector3d, rest: Vector3d, segments: List<SegmentPlan>) {
    legs = legs + LegPlan(Vector3d(root.x, root.y, root.z), Vector3d(rest.x, rest.y, rest.z), segments)
    legs = legs + LegPlan(Vector3d(-root.x, root.y, root.z), Vector3d(-rest.x, rest.y, rest.z), segments.map { it.clone() })
}

private fun lineSegmentModel(block: BlockState, length: Double, thickness: Double): DisplayModel =
    DisplayModel(listOf(BlockDisplayPiece(
        block = block,
        transform = Matrix4f()
            .scale(thickness.toFloat(), thickness.toFloat(), length.toFloat())
            .translate(-.5f, -.5f, 0f),
        tags = listOf("cloak"),
    )))

private fun applyLineLegModel(bodyPlan: BodyPlan, block: BlockState) {
    val rootThickness = 1.0 / 16 * 4.5
    val tipThickness = 1.0 / 16 * 1.5
    for (leg in bodyPlan.legs) {
        for ((index, segment) in leg.segments.withIndex()) {
            val fraction = if (leg.segments.size <= 1) 0.0 else index.toDouble() / (leg.segments.size - 1)
            val thickness = rootThickness + (tipThickness - rootThickness) * fraction
            segment.model = lineSegmentModel(block, segment.length, thickness)
        }
    }
}

private fun createRobotSegments(segmentCount: Int, lengthScale: Double): List<SegmentPlan> =
    List(segmentCount) { index ->
        var length = lengthScale
        var initDirection = FORWARD_VECTOR
        if (index == 0) {
            length *= .5
            initDirection = initDirection.rotateX(Math.PI / 3)
        }
        if (index == 1) length *= .8
        SegmentPlan(length, initDirection)
    }

fun biped(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val o = SpiderOptions()
    o.bodyPlan.addLegPair(Vector3d(.0, .0, .0), Vector3d(1.0, .0, .0), equalLength(segmentCount, 1.0 * segmentLength))
    applyLineLegModel(o.bodyPlan, NETHERITE)
    return o
}

fun quadruped(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val o = SpiderOptions()
    o.bodyPlan.addLegPair(Vector3d(.0, .0, .0), Vector3d(0.9, .0, 0.9), equalLength(segmentCount, 0.9 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.0, .0, .0), Vector3d(1.0, .0, -1.1), equalLength(segmentCount, 1.2 * segmentLength))
    applyLineLegModel(o.bodyPlan, NETHERITE)
    return o
}

fun hexapod(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val o = SpiderOptions()
    o.bodyPlan.addLegPair(Vector3d(.0, .0, 0.1), Vector3d(1.0, .0, 1.1), equalLength(segmentCount, 1.1 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.0, .0, 0.0), Vector3d(1.3, .0, -0.3), equalLength(segmentCount, 1.1 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.0, .0, -.1), Vector3d(1.2, .0, -2.0), equalLength(segmentCount, 1.6 * segmentLength))
    applyLineLegModel(o.bodyPlan, NETHERITE)
    return o
}

fun octopod(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val o = SpiderOptions()
    o.bodyPlan.addLegPair(Vector3d(.0, .0, .1), Vector3d(1.0, .0, 1.6), equalLength(segmentCount, 1.1 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.0, .0, .0), Vector3d(1.3, .0, 0.4), equalLength(segmentCount, 1.0 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.0, .0, -.1), Vector3d(1.3, .0, -0.9), equalLength(segmentCount, 1.1 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.0, .0, -.2), Vector3d(1.1, .0, -2.5), equalLength(segmentCount, 1.6 * segmentLength))
    applyLineLegModel(o.bodyPlan, NETHERITE)
    return o
}

fun quadBot(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val o = SpiderOptions()
    o.bodyPlan.bodyModel = placeholderTorso()
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, .2), Vector3d(1.3 * 1.0, .0, 1.0), createRobotSegments(segmentCount, .9 * .7 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, -.2), Vector3d(1.3 * 1.1, .0, -1.2), createRobotSegments(segmentCount, 1.2 * .7 * segmentLength))
    applyLineLegModel(o.bodyPlan, NETHERITE)
    return o
}

fun hexBot(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val o = SpiderOptions()
    o.bodyPlan.bodyModel = placeholderTorso()
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, .2), Vector3d(1.3 * 1.0, .0, 1.3), createRobotSegments(segmentCount, 1.1 * .7 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, .0), Vector3d(1.3 * 1.2, .0, -0.1), createRobotSegments(segmentCount, 1.1 * .7 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, -.2), Vector3d(1.3 * 1.1, .0, -1.6), createRobotSegments(segmentCount, 1.3 * .7 * segmentLength))
    applyLineLegModel(o.bodyPlan, NETHERITE)
    return o
}

fun octoBot(segmentCount: Int, segmentLength: Double): SpiderOptions {
    val o = SpiderOptions()
    o.bodyPlan.bodyModel = placeholderTorso()
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, .3), Vector3d(1.3 * 1.0, .0, 1.3), createRobotSegments(segmentCount, 1.1 * .7 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, .1), Vector3d(1.3 * 1.2, .0, 0.5), createRobotSegments(segmentCount, 1.0 * .7 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, .1), Vector3d(1.3 * 1.2, .0, -0.7), createRobotSegments(segmentCount, 1.1 * .7 * segmentLength))
    o.bodyPlan.addLegPair(Vector3d(.2, -.2 - .15, -.3), Vector3d(1.3 * 1.1, .0, -1.6), createRobotSegments(segmentCount, 1.3 * .7 * segmentLength))
    applyLineLegModel(o.bodyPlan, NETHERITE)
    return o
}

/**
 * The spider the mod spawns: a faithful octopod (8 legs, netherite texture) at 3 segments / length
 * 1, with NO central torso — the legs converge to the body point exactly like the original mod.
 * Speed tuning comes from config/arachnomod-common.toml (read at spawn time).
 */
fun defaultSpider(): SpiderOptions {
    val o = octopod(3, 1.0)
    val maxSpeed = com.heledron.spideranimation.Config.CHASE_SPEED.get() / 20.0   // blocks/sec -> blocks/tick
    o.walkGait.maxSpeed = maxSpeed
    o.walkGait.moveAcceleration = maxSpeed * 0.375   // keeps the tuned accel:speed ratio (.15 at .4)
    o.walkGait.legMoveSpeed = com.heledron.spideranimation.Config.LEG_STEP_SPEED.get()
    return o
}

/**
 * The CAMO variant: identical geometry/gait to [defaultSpider], with ACTIVE camouflage — every
 * tick each leg is repainted as the block its foot stands on (see GroundCamo), and its steps play
 * that block's own step sound like player footsteps. The moss palette here is only the spawn
 * placeholder until each leg's first ground contact.
 */
fun camoSpider(): SpiderOptions {
    val o = defaultSpider()
    applyLineLegModel(o.bodyPlan, MOSS)   // re-skin every leg segment (replaces the netherite model)
    return o
}

/**
 * The POISON variant: identical geometry/gait to [defaultSpider], skinned in slime block — the
 * closest vanilla block to the Potion of Poison's pale sickly green (#87A363), and translucent
 * like the brew itself. Its tarantula rear-up/lunge/venom-bite behaviour lives in
 * SpiderBody.beginLunge and SpiderMob's poison melee branch — presets are looks + gait only.
 */
fun poisonSpider(): SpiderOptions {
    val o = defaultSpider()
    applyLineLegModel(o.bodyPlan, SLIME)
    return o
}

val PRESETS: Map<String, (Int, Double) -> SpiderOptions> = mapOf(
    "biped" to ::biped,
    "quadruped" to ::quadruped,
    "hexapod" to ::hexapod,
    "octopod" to ::octopod,
    "quadbot" to ::quadBot,
    "hexbot" to ::hexBot,
    "octobot" to ::octoBot,
)
