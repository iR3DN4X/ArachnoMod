package com.heledron.spideranimation.entity

import com.heledron.spideranimation.spider.SpiderBody
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3d
import kotlin.math.floor

/**
 * ACTIVE CAMOUFLAGE for the camo variant: every tick, each leg is repainted as the block its foot
 * is standing on, so the spider continuously matches whatever terrain it crosses — grass legs on
 * grass, sand legs on the beach, one-leg-on-a-log-still-green, etc.
 *
 * This works because the renderer reads each BlockDisplayPiece.block EVERY render pass and
 * re-sends the display's block state each tick — mutating the piece is picked up by the live
 * displays instantly, with no display respawn and no flash. Each spawned spider owns its own
 * model instances (built fresh in Presets), so per-spider mutation never leaks between bodies.
 *
 * A mid-swing foot (nothing but air below) keeps its previous block until it lands somewhere new,
 * so legs change color as they STEP, not randomly in flight. The moss-block spawn palette is only
 * the placeholder until each leg's first ground contact.
 */
object GroundCamo {

    /** Repaint every leg of [body] to match the block under its foot. Call once per tick. */
    fun update(body: SpiderBody) {
        for (leg in body.legs) {
            val state = blockUnder(body.level, leg.endEffector) ?: continue   // airborne: keep last
            for (segment in leg.legPlan.segments) {
                for (piece in segment.model.pieces) {
                    if (piece.block !== state) piece.block = state
                }
            }
        }
    }

    /**
     * The supporting block at/just below [pos]: the first block with a collision shape, scanning
     * up to [depth] blocks down. Skips tall grass and other no-collision decoration (a foot rests
     * on the dirt, not the grass tuft). Null when there is only air below (mid-swing foot, ledge).
     */
    fun blockUnder(level: ServerLevel, pos: Vector3d, depth: Int = 3): BlockState? {
        val x = floor(pos.x).toInt()
        val z = floor(pos.z).toInt()
        val startY = floor(pos.y + 0.01).toInt()
        val bp = BlockPos.MutableBlockPos()
        for (dy in 0..depth) {
            bp.set(x, startY - dy, z)
            val state = level.getBlockState(bp)
            if (state.isAir) continue
            if (!state.getCollisionShape(level, bp).isEmpty) return state
        }
        return null
    }
}
