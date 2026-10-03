package com.heledron.spideranimation.spider

import com.heledron.spideranimation.platform.RenderBlockDisplay
import com.heledron.spideranimation.platform.RenderGroup
import com.heledron.spideranimation.platform.renderBlock
import com.heledron.spideranimation.util.FORWARD_VECTOR
import com.heledron.spideranimation.util.RIGHT_VECTOR
import com.heledron.spideranimation.util.UP_VECTOR
import com.heledron.spideranimation.util.centredMatrix
import com.heledron.spideranimation.util.horizontalLength
import com.heledron.spideranimation.util.isZero
import com.heledron.spideranimation.util.toV3f
import net.minecraft.util.Brightness
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f

/*
 * Ported from `spider/rendering/spiderDebugRenderEntities.kt` in TheCymaera/minecraft-spider, at the
 * version this engine was ported from (Dec 2024 - Feb 2025) — the debug graphics from his videos.
 * Same parts, same blocks, same sizes and the same placements, with every part on, as the
 * original's SpiderDebugOptions defaulted. The only thing missing is the point detector's
 * "selected leg" highlight (a slightly bigger foot cube), because the original's leg-picking tool
 * was never ported; every foot draws at the unselected size.
 *
 * The original spawned each part as a free-standing display at its own location and let position
 * interpolation (`teleportDuration`) carry it. Here every part is anchored at the spider's body,
 * exactly like the spider's own displays (see SpiderRenderer), with its location folded into the
 * client-interpolated transform: the same picture, it shares the spider's smooth re-parking, and
 * it works on 1.20.1, which has no position interpolation.
 */

private val FULL_BRIGHT = Brightness(15, 15)

fun renderSpiderDebug(spider: SpiderBody): RenderGroup {
    val group = RenderGroup()

    val scale = spider.bodyPlan.scale.toFloat()

    /** A part drawn at [position] (the original's display location) with a local [transform]. */
    fun part(
        position: Vector3d,
        block: BlockState,
        transform: Matrix4f,
        brightness: Brightness? = FULL_BRIGHT,
        interpolate: Boolean = true,
    ): RenderBlockDisplay {
        val anchor = spider.position
        val offset = Vector3f(
            (position.x - anchor.x).toFloat(),
            (position.y - anchor.y).toFloat(),
            (position.z - anchor.z).toFloat(),
        )
        return renderBlock(spider.level, anchor, spider.velocity, block,
            Matrix4f().translate(offset).mul(transform), brightness, interpolate)
    }

    /** The original's lineRenderEntity: a square bar from [start] along [vector]. */
    fun line(start: Vector3d, vector: Vector3d, thickness: Float, block: BlockState, interpolate: Boolean = true): RenderBlockDisplay? {
        // A zero-length line has no direction to face (rotateTowards would produce NaNs).
        if (vector.lengthSquared() < 1e-12) return null
        val upVector = if (vector.x + vector.z != 0.0) UP_VECTOR else FORWARD_VECTOR
        val transform = Matrix4f().rotateTowards(vector.toV3f(), upVector.toV3f())
            .translate(-thickness / 2, -thickness / 2, 0f)
            .scale(thickness, thickness, vector.length().toFloat())
        return part(start, block, transform, interpolate = interpolate)
    }

    for ((legIndex, leg) in spider.legs.withIndex()) {
        // Render scan bars
        line(
            start = leg.scanStartPosition,
            vector = leg.scanVector,
            thickness = .05f * scale,
            block = (if (leg.isPrimary) Blocks.GOLD_BLOCK else Blocks.IRON_BLOCK).defaultBlockState(),
        )?.let { group["scanBar" to legIndex] = it }

        val zoneBlock = (if (leg.isUncomfortable) Blocks.RED_STAINED_GLASS else Blocks.CYAN_STAINED_GLASS).defaultBlockState()
        val scanPivot = spider.gait.scanPivotMode.get(spider)

        // Render trigger zone (vertical extent)
        group["triggerZoneVertical" to legIndex] = part(
            position = leg.triggerZone.center,
            block = zoneBlock,
            transform = Matrix4f()
                .rotate(scanPivot)
                .scale(.07f * scale, 2 * leg.triggerZone.vertical.toFloat(), .07f * scale)
                .translate(-.5f, -.5f, -.5f),
        )

        // Render trigger zone (horizontal extent), at the step target's height within the zone
        group["triggerZoneHorizontal" to legIndex] = part(
            position = Vector3d(leg.triggerZone.center).also {
                it.y = leg.target.position.y.coerceIn(it.y - leg.triggerZone.vertical, it.y + leg.triggerZone.vertical)
            },
            block = zoneBlock,
            transform = run {
                val size = 2 * leg.triggerZone.horizontal.toFloat()
                Matrix4f()
                    .rotate(scanPivot)
                    .scale(size, 0.02f, size)
                    .translate(-.5f, -.5f, -.5f)
            },
        )

        // Render end effector
        val footSize = .15f * scale
        group["endEffector" to legIndex] = part(
            position = leg.endEffector,
            block = when {
                leg.isDisabled -> Blocks.BLACK_CONCRETE
                leg.isGrounded() -> Blocks.DIAMOND_BLOCK
                leg.touchingGround -> Blocks.LAPIS_BLOCK
                else -> Blocks.REDSTONE_BLOCK
            }.defaultBlockState(),
            transform = centredMatrix(footSize, footSize, footSize),
        )

        // Render target position
        val targetSize = 0.2f * scale
        group["targetPosition" to legIndex] = part(
            position = leg.target.position,
            block = (if (leg.target.isGrounded) Blocks.LIME_STAINED_GLASS else Blocks.RED_STAINED_GLASS).defaultBlockState(),
            transform = centredMatrix(targetSize, targetSize, targetSize),
        )
    }

    // Render spider direction
    run {
        val size = .1f * scale
        val displacement = 1f * scale
        group["direction"] = part(
            position = spider.position,
            block = (if (spider.gallop) Blocks.REDSTONE_BLOCK else Blocks.EMERALD_BLOCK).defaultBlockState(),
            transform = Matrix4f()
                .rotate(spider.orientation)
                .translate(FORWARD_VECTOR.toV3f().mul(displacement))
                .scale(size, size, size)
                .translate(-.5f, -.5f, -.5f),
        )
    }

    // Render preferred orientation (the original leaves these at world lighting)
    run {
        fun axis(orientation: Quaternionf, direction: Vector3d, thickness: Float, length: Float): RenderBlockDisplay {
            val mTranslation = Vector3f(-1f, -1f, -1f).add(direction.toV3f()).mul(.5f)
            val mScale = Vector3f(thickness, thickness, thickness).add(direction.toV3f().mul(length))
            return part(
                position = spider.position,
                block = Blocks.DIAMOND_BLOCK.defaultBlockState(),
                transform = Matrix4f()
                    .rotate(orientation)
                    .scale(mScale)
                    .translate(mTranslation),
                brightness = null,
            )
        }

        val thickness = .025f * scale
        group["preferredForwards"] = axis(spider.preferredOrientation, FORWARD_VECTOR, thickness, 2.0f * scale)
        group["preferredRight"   ] = axis(spider.preferredOrientation, RIGHT_VECTOR  , thickness, 1.0f * scale)
        group["preferredUp"      ] = axis(spider.preferredOrientation, UP_VECTOR     , thickness, 1.0f * scale)
    }

    val normal = spider.normal ?: return group

    // Render the support polygon (snaps instead of interpolating, as in the original)
    val points = normal.contactPolygon
    if (points != null) {
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            line(
                start = a,
                vector = Vector3d(b).sub(a),
                thickness = .05f * scale,
                block = Blocks.EMERALD_BLOCK.defaultBlockState(),
                interpolate = false,
            )?.let { group["polygon" to i] = it }
        }
    }

    // Render centre of mass
    val centreOfMass = normal.centreOfMass
    if (centreOfMass != null) {
        val size = 0.1f * scale
        group["centreOfMass"] = part(
            position = centreOfMass,
            block = (if (normal.normal.horizontalLength() == .0) Blocks.LAPIS_BLOCK else Blocks.REDSTONE_BLOCK).defaultBlockState(),
            transform = centredMatrix(size, size, size),
        )
    }

    // Render normal force
    val origin = normal.origin
    if (centreOfMass != null && origin != null) {
        line(
            start = origin,
            vector = Vector3d(centreOfMass).sub(origin),
            thickness = .02f * scale,
            block = (if (spider.normalAcceleration.isZero) Blocks.BLACK_CONCRETE else Blocks.WHITE_CONCRETE).defaultBlockState(),
        )?.let { group["acceleration"] = it }
    }

    return group
}
