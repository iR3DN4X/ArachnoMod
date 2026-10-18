package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.platform.raycastGround
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import org.joml.Vector3d

/**
 * The SPIDER LASER POINTER. Right-click to shine the dot at whatever you are looking at and the
 * spider scurries after it, like a cat. This is the mod's answer to the original's carrot-on-a-
 * stick following: you are not leading it by the nose, you are playing with it.
 *
 * It lures rather than commands: [SpiderAI] holds the dot for laserPointerLureSeconds and the
 * spider is in LURE mode for that time, which outranks a chase — so a pointer genuinely pulls it
 * off a player — and LURE is not CHASE, so **it will not bite while it is distracted**.
 *
 * Creative-mode only, like /spider newinstance: no recipe, creative tab only, and it refuses to
 * fire for a survival player.
 *
 * PER-VERSION FILE — the 1.21.11 copy (from the 26.1.2 one). use() returns a plain InteractionResult
 * here (not an InteractionResultHolder); 1.21.11 still sends the action-bar line with
 * displayClientMessage(text, true) - 26.1 renamed it sendOverlayMessage.
 */
class LaserPointerItem(properties: Item.Properties) : Item(properties) {

    private fun Player.overlay(text: Component) = displayClientMessage(text, true)

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        // Let the client play the swing; all the work is server-side.
        if (level !is ServerLevel) return InteractionResult.SUCCESS

        if (!player.isCreative) {
            player.overlay(Component.literal("The Spider Laser Pointer is a creative-mode-only toy."))
            return InteractionResult.FAIL
        }

        val eye = player.eyePosition
        val look = player.lookAngle
        val hit = level.raycastGround(
            Vector3d(eye.x, eye.y, eye.z),
            Vector3d(look.x, look.y, look.z),
            Config.LASER_POINTER_RANGE.get(),
        )
        if (hit == null) {
            player.overlay(Component.literal("The dot lands on nothing — point it at a surface."))
            return InteractionResult.FAIL
        }

        // A flash where the dot hits, whether or not anything is listening.
        level.sendParticles(ParticleTypes.FLAME, hit.x, hit.y + 0.1, hit.z, 4, 0.05, 0.05, 0.05, 0.0)

        val ticks = (Config.LASER_POINTER_LURE_SECONDS.get() * 20.0).toInt().coerceAtLeast(1)
        val took = SpiderAI.lure(hit, ticks)
        player.overlay(
            Component.literal(if (took) "The spider fixes on the dot." else "Nothing is around to chase the dot.")
        )
        return InteractionResult.SUCCESS
    }
}
