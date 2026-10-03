package com.heledron.spideranimation.entity

import com.heledron.spideranimation.SpiderAnimationMod
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.renderer.entity.NoopRenderer

/**
 * Fabric `client` entrypoint. Only loaded on the physical client, so it can safely reference
 * client-only classes (NoopRenderer, the renderer registry). On a dedicated server this class
 * is never touched.
 *
 * The spider mob has no vanilla model; it is drawn entirely by the BlockDisplay simulation. We
 * register a NoopRenderer so the client has *a* renderer for the entity type (otherwise it errors)
 * that simply draws nothing.
 */
object ArachnoClient : ClientModInitializer {
    override fun onInitializeClient() {
        EntityRendererRegistry.register(SpiderAnimationMod.SPIDER_ENTITY) { context -> NoopRenderer(context) }

        // The config book: the server sends a snapshot, the screen sends edits back.
        ClientPlayNetworking.registerGlobalReceiver(ConfigBookNet.TO_CLIENT) { client, _, buf, _ ->
            val data = buf.readUtf(ConfigBook.MAX_LENGTH)
            client.execute {
                ConfigBookScreen.receive(data) { request ->
                    val out = PacketByteBufs.create()
                    out.writeUtf(request, ConfigBookNet.MAX_REQUEST)
                    ClientPlayNetworking.send(ConfigBookNet.TO_SERVER, out)
                }
            }
        }
    }
}
