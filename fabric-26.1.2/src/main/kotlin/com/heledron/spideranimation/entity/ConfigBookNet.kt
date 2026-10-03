package com.heledron.spideranimation.entity

import com.heledron.spideranimation.SpiderAnimationMod
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

/**
 * The config book's two packets: the server's snapshot of every setting (to the client), and the
 * screen's edit requests (to the server). Both carry one string - see [ConfigBook].
 *
 * PER-LOADER/VERSION FILE. Fabric 26.1.2: payload types registered with clientboundPlay/serverboundPlay. The
 * client half lives in ArachnoClient.
 */
object ConfigBookNet {
    private const val MAX_REQUEST = 4096

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

    fun register() {
        PayloadTypeRegistry.clientboundPlay().register(ToClient.TYPE, ToClient.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(ToServer.TYPE, ToServer.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(ToServer.TYPE) { payload, ctx ->
            ctx.server().execute { ConfigBook.handle(ctx.player(), payload.data) }
        }
    }

    fun sendToPlayer(player: ServerPlayer, data: String) = ServerPlayNetworking.send(player, ToClient(data))
}
