package com.heledron.spideranimation.entity

import com.heledron.spideranimation.SpiderAnimationMod
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * The config book's two packets: the server's snapshot of every setting (to the client), and the
 * screen's edit requests (to the server). Both carry one string - see [ConfigBook].
 *
 * PER-LOADER/VERSION FILE. Fabric 1.20.1 predates payload types: plain channels carrying a
 * buffer. The client half lives in ArachnoClient.
 */
object ConfigBookNet {
    const val MAX_REQUEST = 4096
    val TO_CLIENT = ResourceLocation(SpiderAnimationMod.ID, "config_book")
    val TO_SERVER = ResourceLocation(SpiderAnimationMod.ID, "config_book_edit")

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(TO_SERVER) { server, player, _, buf, _ ->
            val data = buf.readUtf(MAX_REQUEST)
            server.execute { ConfigBook.handle(player, data) }
        }
    }

    fun sendToPlayer(player: ServerPlayer, data: String) {
        val buf: FriendlyByteBuf = PacketByteBufs.create()
        buf.writeUtf(data, ConfigBook.MAX_LENGTH)
        ServerPlayNetworking.send(player, TO_CLIENT, buf)
    }
}
