package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

/**
 * The WARDING IDOL block. All it does is exist in a place: [WardRegistry] holds the position and
 * the spawn manager reads it. Placing and breaking keep that registry in step, and a periodic
 * sweep catches any removal no hook sees (another mod, an edited world, a restored backup).
 *
 * PER-VERSION FILE. The removal hook differs: 1.20.1 and 1.21.1 have onRemove; 26.1.2 replaced it
 * with affectNeighborsAfterRemoval. onPlace is public on 1.20.1 and protected from 1.21.1.
 */
class WardBlock(properties: BlockBehaviour.Properties) : Block(properties) {

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        super.onPlace(state, level, pos, oldState, movedByPiston)
        // Compare blocks rather than BlockState.is(...): `is` is a Kotlin keyword and the method
        // is one of the names that moves between versions.
        if (level is ServerLevel && oldState.block !== this) WardRegistry.add(level, pos)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (level is ServerLevel && newState.block !== this) WardRegistry.remove(level, pos)
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    /**
     * Placement feedback, and the spacing rule. Refusing here rather than in canSurvive keeps the
     * decision server-side: the registry is server state, and a client that guessed wrong would
     * show a ghost block.
     */
    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level !is ServerLevel) return
        val player = placer as? Player

        val blocking = WardRegistry.blockingNeighbour(level, pos)
        if (blocking != null) {
            // Too close to another idol: give it back rather than swallowing an expensive craft.
            level.destroyBlock(pos, false)          // no drops - we hand the idol back ourselves
            Block.popResource(level, pos, ItemStack(this))
            player?.displayClientMessage(
                Component.literal(
                    "Too close to another Warding Idol (${blocking.x}, ${blocking.y}, ${blocking.z}). " +
                        "They must be at least ${Config.WARD_MIN_SPACING.get().toInt()} blocks apart."
                ), false
            )
            return
        }

        if (!Config.WARD_ENABLED.get()) {
            player?.displayClientMessage(
                Component.literal("Warding Idols are switched off on this server (wardEnabled = false)."), false
            )
            return
        }

        // NeoForge and Forge place blocks with snapshot capture on, which runs setPlacedBy BEFORE
        // onPlace - the hook that registers the ward. Reading the countdown here then found no
        // ward and reported a sleeping idol as already watching. Registering is idempotent, so do
        // it now and the message is right on every loader.
        WardRegistry.add(level, pos)
        val seconds = WardRegistry.secondsUntilArmed(level, pos)
        val radius = Config.WARD_RADIUS.get().toInt()
        player?.displayClientMessage(
            if (seconds == null || seconds <= 0)
                Component.literal("The Warding Idol is watching: everything within $radius blocks is a safe zone. No spider can follow you in.")
            else
                Component.literal("The Warding Idol is waking: it will be warding $radius blocks in $seconds second(s)."),
            false
        )
    }
}
