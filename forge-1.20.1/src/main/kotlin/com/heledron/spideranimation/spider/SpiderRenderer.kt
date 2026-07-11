package com.heledron.spideranimation.spider

import com.heledron.spideranimation.platform.RenderGroup
import com.heledron.spideranimation.platform.renderBlock
import org.joml.Matrix4f
import org.joml.Vector3d

/*
 * Ported from `spider/components/rendering/renderSpiderEntities.kt` (minus the cloak layer).
 * Builds a RenderGroup of BlockDisplay submissions: one for the torso model, and one per
 * leg-segment placed at the segment's parent joint and rotated by the chain's solved rotation.
 */
fun renderSpider(spider: SpiderBody): RenderGroup {
    val group = RenderGroup()

    val bodyTransform = Matrix4f().rotate(spider.orientation)
    group["body"] = renderModel(spider, spider.position, spider.bodyPlan.bodyModel, bodyTransform)

    for ((legIndex, leg) in spider.legs.withIndex()) {
        val chain = leg.chain
        val pivot = spider.gait.legChainPivotMode.get(spider)
        val rotations = chain.getRotations(pivot)
        for ((segmentIndex, rotation) in rotations.withIndex()) {
            val segmentPlan = spider.bodyPlan.legs.getOrNull(legIndex)?.segments?.getOrNull(segmentIndex) ?: continue
            val parent = chain.segments.getOrNull(segmentIndex - 1)?.position ?: chain.root
            val segmentTransform = Matrix4f().rotate(rotation)
            group[legIndex to segmentIndex] = renderModel(spider, parent, segmentPlan.model, segmentTransform)
        }
    }

    return group
}

private fun renderModel(spider: SpiderBody, position: Vector3d, model: DisplayModel, transform: Matrix4f): RenderGroup {
    val group = RenderGroup()

    // Anchor every display at the spider's body position and fold each piece's world offset into the
    // (client-interpolated) transform, so legs can never desync from the body. The body's velocity
    // is passed along so RenderBlockDisplay can park the underlying entities AHEAD of a moving
    // spider (see the re-park logic there).
    val anchor = spider.position
    val ox = (position.x - anchor.x).toFloat()
    val oy = (position.y - anchor.y).toFloat()
    val oz = (position.z - anchor.z).toFloat()

    for ((index, piece) in model.pieces.withIndex()) {
        val matrix = Matrix4f().translate(ox, oy, oz).mul(transform).mul(piece.transform)
        group[index] = renderBlock(spider.level, anchor, spider.velocity, piece.block, matrix)
    }
    return group
}
