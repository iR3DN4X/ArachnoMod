package com.heledron.spideranimation.entity

import com.heledron.spideranimation.SpiderAnimationMod
import net.minecraft.client.renderer.entity.NoopRenderer
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.eventbus.api.IEventBus

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
    }

    private fun onRegisterRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(SpiderAnimationMod.SPIDER_ENTITY.get()) { context -> NoopRenderer(context) }
    }
}
