package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.platform.raycastGround
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
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
 * PER-VERSION FILE: use() returns InteractionResultHolder here and a plain InteractionResult on
 * 26.1.2.
 */
class LaserPointerItem(properties: Item.Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        // Let the client play the swing; all the work is server-side.
        if (level !is ServerLevel) return InteractionResultHolder.success(stack)

        if (!player.isCreative) {
            player.displayClientMessage(
                Component.literal("The Spider Laser Pointer is a creative-mode-only toy."), true
            )
            return InteractionResultHolder.fail(stack)
        }

        val eye = player.eyePosition
        val look = player.lookAngle
        val hit = level.raycastGround(
            Vector3d(eye.x, eye.y, eye.z),
            Vector3d(look.x, look.y, look.z),
            Config.LASER_POINTER_RANGE.get(),
        )
        if (hit == null) {
            player.displayClientMessage(
                Component.literal("The dot lands on nothing — point it at a surface."), true
            )
            return InteractionResultHolder.fail(stack)
        }

        // A flash where the dot hits, whether or not anything is listening.
        level.sendParticles(ParticleTypes.FLAME, hit.x, hit.y + 0.1, hit.z, 4, 0.05, 0.05, 0.05, 0.0)

        val ticks = (Config.LASER_POINTER_LURE_SECONDS.get() * 20.0).toInt().coerceAtLeast(1)
        val took = SpiderAI.lure(hit, ticks)
        player.displayClientMessage(
            Component.literal(
                if (took) "The spider fixes on the dot." else "Nothing is around to chase the dot."
            ), true
        )
        return InteractionResultHolder.success(stack)
    }
}
