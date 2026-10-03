package com.heledron.spideranimation.entity

import com.heledron.spideranimation.SpiderAnimationMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * The config book's two packets: the server's snapshot of every setting (to the client), and the
 * screen's edit requests (to the server). Both carry one string - see [ConfigBook].
 *
 * PER-LOADER/VERSION FILE. Forge 1.20.1: a SimpleChannel. The to-client handler only calls into
 * ArachnoClient, which is loaded on the physical client alone.
 */
object ConfigBookNet {
    const val MAX_REQUEST = 4096
    private const val VERSION = "1"

    class ToClient(val data: String)
    class ToServer(val data: String)

    private val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(SpiderAnimationMod.ID, "config_book"),
        Supplier { VERSION }, Predicate { it == VERSION }, Predicate { it == VERSION })

    fun register() {
        CHANNEL.messageBuilder(ToClient::class.java, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder { msg, buf -> buf.writeUtf(msg.data, ConfigBook.MAX_LENGTH) }
            .decoder { buf -> ToClient(buf.readUtf(ConfigBook.MAX_LENGTH)) }
            .consumerMainThread { msg, _ -> ArachnoClient.receiveConfigBook(msg.data) }
            .add()
        CHANNEL.messageBuilder(ToServer::class.java, 1, NetworkDirection.PLAY_TO_SERVER)
            .encoder { msg, buf -> buf.writeUtf(msg.data, MAX_REQUEST) }
            .decoder { buf -> ToServer(buf.readUtf(MAX_REQUEST)) }
            .consumerMainThread { msg, ctx -> ctx.get().sender?.let { ConfigBook.handle(it, msg.data) } }
            .add()
    }

    fun sendToPlayer(player: ServerPlayer, data: String) = CHANNEL.send(PacketDistributor.PLAYER.with { player }, ToClient(data))

    fun sendToServer(data: String) = CHANNEL.sendToServer(ToServer(data))
}
