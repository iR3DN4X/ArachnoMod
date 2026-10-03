package com.heledron.spideranimation.entity

import com.heledron.spideranimation.SpiderAnimationMod
import net.minecraft.client.renderer.entity.NoopRenderer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent

/**
 * Client-only setup. This object references client-only classes (NoopRenderer, the client render
 * event), so it must ONLY be touched on the physical client — [SpiderAnimationMod] guards the call
 * with `FMLEnvironment.dist == Dist.CLIENT`. On a dedicated server this class is never loaded.
 *
 * The spider mob has no vanilla model; it is drawn entirely by the BlockDisplay simulation. We
 * register a NoopRenderer so the client has *a* renderer for the entity type (otherwise it errors)
 * that simply draws nothing.
 */
object ArachnoClient {
    fun register(modBus: IEventBus) {
        modBus.addListener(::onRegisterRenderers)
        modBus.addListener(::onRegisterPayloadHandlers)
    }

    /** The config book's snapshot (see ConfigBookNet): open or refresh the settings screen. */
    private fun onRegisterPayloadHandlers(event: RegisterClientPayloadHandlersEvent) {
        event.register(ConfigBookNet.ToClient.TYPE) { payload, ctx ->
            ctx.enqueueWork {
                ConfigBookScreen.receive(payload.data) { request -> ClientPacketDistributor.sendToServer(ConfigBookNet.ToServer(request)) }
            }
        }
    }

    private fun onRegisterRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(SpiderAnimationMod.SPIDER_ENTITY.get()) { context -> NoopRenderer(context) }
    }
}
