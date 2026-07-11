package com.heledron.spideranimation.spider

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Matrix4f
import org.joml.Vector3d

/*
 * Ported from `utilities/DisplayModel.kt` and `spider/configuration/BodyPlan.kt`.
 *
 * Simplifications vs the original (see notes in the accompanying message):
 *  - BlockData -> BlockState.
 *  - Per-piece brightness override and the "cloak"/eye/blinking palette tags are dropped from
 *    this foundation (they belong to the cloak/animated-palette systems, which are deferred).
 *    `tags` is kept so those systems can be layered on later exactly as in the original.
 */

class BlockDisplayPiece(
    var block: BlockState,
    var transform: Matrix4f,
    var tags: List<String> = emptyList(),
) {
    fun scale(x: Float, y: Float, z: Float) {
        transform.set(Matrix4f().scale(x, y, z).mul(transform))
    }
    fun clone() = BlockDisplayPiece(block, Matrix4f(transform), tags)
}

class DisplayModel(var pieces: List<BlockDisplayPiece>) {
    fun scale(scale: Float) = apply { pieces.forEach { it.scale(scale, scale, scale) } }
    fun clone() = DisplayModel(pieces.map { it.clone() })
    companion object { fun empty() = DisplayModel(emptyList()) }
}

class SegmentPlan(
    var length: Double,
    var initDirection: Vector3d,
    var model: DisplayModel = DisplayModel.empty(),
) {
    fun clone() = SegmentPlan(length, Vector3d(initDirection), model.clone())
}

class LegPlan(
    var attachmentPosition: Vector3d,
    var restPosition: Vector3d,
    var segments: List<SegmentPlan>,
)

class BodyPlan {
    var scale = 1.0
    var legs = emptyList<LegPlan>()

    // Original default torso is EMPTY: the line-leg presets (biped..octopod) render only legs.
    var bodyModel = DisplayModel.empty()

    fun scale(scale: Double) {
        this.scale *= scale
        bodyModel.scale(scale.toFloat())
        legs.forEach { leg ->
            leg.attachmentPosition.mul(scale)
            leg.restPosition.mul(scale)
            leg.segments.forEach { segment ->
                segment.length *= scale
                segment.model.scale(scale.toFloat())
            }
        }
    }
}

/** Default placeholder torso (a small netherite cube). Used by the "bot" presets in this
 *  foundation in place of the original parsed BlockDisplay art (deferred). */
fun placeholderTorso(): DisplayModel = DisplayModel(listOf(
    BlockDisplayPiece(
        block = Blocks.NETHERITE_BLOCK.defaultBlockState(),
        transform = com.heledron.spideranimation.util.centredMatrix(0.7f, 0.45f, 1.0f),
        tags = listOf("torso"),
    )
))

class SpiderOptions {
    var walkGait = Gait.defaultWalk()
    var gallopGait = Gait.defaultGallop()
    var bodyPlan = BodyPlan()
}
