package com.heledron.spideranimation.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * The SPIDER CONFIG BOOK. Right-click to open the settings screen ([ConfigBook]): every setting
 * with its description, default and a reset button. Anyone can read it; operators and the owner
 * of a single-player world can change things. Handed out on first join (giveConfigBook), and in
 * the creative tab; /spider menu opens the same screen without it.
 *
 * PER-VERSION FILE: use() returns InteractionResultHolder here and a plain InteractionResult on
 * 26.1.2.
 */
class ConfigBookItem(properties: Item.Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        // The server owns the settings: it sends the snapshot that opens the screen.
        if (player is ServerPlayer) ConfigBook.open(player)
        return InteractionResultHolder.success(stack)
    }
}
