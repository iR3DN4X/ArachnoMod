package com.heledron.spideranimation.entity

import com.heledron.spideranimation.SpiderAnimationMod
import net.fabricmc.api.ClientModInitializer
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
    }
}
