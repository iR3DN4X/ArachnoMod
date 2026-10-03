package com.heledron.spideranimation.entity

import com.heledron.spideranimation.SpiderAnimationMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * The config book's two packets: the server's snapshot of every setting (to the client), and the
 * screen's edit requests (to the server). Both carry one string - see [ConfigBook].
 *
 * PER-LOADER/VERSION FILE. NeoForge 26.1.2: the to-client type is declared here but its handler
 * is registered by ArachnoClient (RegisterClientPayloadHandlersEvent), on the physical client alone.
 */
object ConfigBookNet {
    const val MAX_REQUEST = 4096

    class ToClient(val data: String) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<ToClient> = TYPE
        companion object {
            val TYPE = CustomPacketPayload.Type<ToClient>(Identifier.fromNamespaceAndPath(SpiderAnimationMod.ID, "config_book"))
            val CODEC: StreamCodec<FriendlyByteBuf, ToClient> = StreamCodec.of(
                StreamEncoder { buf, p -> buf.writeUtf(p.data, ConfigBook.MAX_LENGTH) },
                StreamDecoder { buf -> ToClient(buf.readUtf(ConfigBook.MAX_LENGTH)) })
        }
    }

    class ToServer(val data: String) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<ToServer> = TYPE
        companion object {
            val TYPE = CustomPacketPayload.Type<ToServer>(Identifier.fromNamespaceAndPath(SpiderAnimationMod.ID, "config_book_edit"))
            val CODEC: StreamCodec<FriendlyByteBuf, ToServer> = StreamCodec.of(
                StreamEncoder { buf, p -> buf.writeUtf(p.data, MAX_REQUEST) },
                StreamDecoder { buf -> ToServer(buf.readUtf(MAX_REQUEST)) })
        }
    }

    fun register(modBus: IEventBus) {
        modBus.addListener(::onRegisterPayloads)
    }

    private fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(ToClient.TYPE, ToClient.CODEC)
        registrar.playToServer(ToServer.TYPE, ToServer.CODEC) { payload, ctx ->
            ctx.enqueueWork { (ctx.player() as? ServerPlayer)?.let { ConfigBook.handle(it, payload.data) } }
        }
    }

    fun sendToPlayer(player: ServerPlayer, data: String) = PacketDistributor.sendToPlayer(player, ToClient(data))
}
