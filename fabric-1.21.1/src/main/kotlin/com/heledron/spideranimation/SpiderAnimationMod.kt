package com.heledron.spideranimation

import com.heledron.spideranimation.ecs.Ecs
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.entity.GroundCamo
import com.heledron.spideranimation.entity.SpiderAI
import com.heledron.spideranimation.entity.SpiderMob
import com.heledron.spideranimation.entity.SpiderSpawnManager
import com.heledron.spideranimation.platform.DisplayTracker
import com.heledron.spideranimation.platform.playSoundAt
import com.heledron.spideranimation.platform.removeOrphanDisplays
import com.heledron.spideranimation.spider.*
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem
import org.joml.Vector3d
import java.util.UUID

/**
 * Fabric entrypoint (the "main" ModInitializer). This is the loader-specific layer; the entire
 * spider engine (ecs/, spider/, platform/, SpiderMob, SpiderSpawnManager) is loader-agnostic
 * Mojang-mapped code shared verbatim with the NeoForge port.
 *
 * Registration is done immediately via [Registry.register] (Fabric has no DeferredRegister); the
 * object's static init runs before [onInitialize], so the types exist before anything references
 * them. Client-only renderer registration lives in the separate `client` entrypoint (ArachnoClient).
 */
object SpiderAnimationMod : ModInitializer {
    const val ID = "arachnomod"

    private fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ID, path)

    val SPIDER_ENTITY: EntityType<SpiderMob> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE, id("spider"),
        EntityType.Builder.of({ type, level -> SpiderMob(type, level) }, MobCategory.MONSTER)
            .sized(2.0f, 2.0f)
            .clientTrackingRange(16)
            .build("spider")
    )

    val SPIDER_SPAWN_EGG: SpawnEggItem = Registry.register(
        BuiltInRegistries.ITEM, id("spider_spawn_egg"),
        // Grey base with dark-grey spots. Vanilla ItemColors auto-tints registered SpawnEggItems.
        SpawnEggItem(SPIDER_ENTITY, 0x909090, 0x3a3a3a, Item.Properties())
    )

    /** Creative-only (no recipe): right-click the spider to tame it; tamed + empty hand = ride. */
    val SPIDER_TAMER: Item = Registry.register(
        BuiltInRegistries.ITEM, id("spider_tamer"),
        Item(Item.Properties().stacksTo(1))
    )

    override fun onInitialize() {
        // All gameplay tunables live in config/arachnomod-common.toml (see Config).
        Config.init(FabricLoader.getInstance().configDir)

        FabricDefaultAttributeRegistry.register(SPIDER_ENTITY, SpiderMob.createAttributes().build())

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register { entries ->
            entries.accept(SPIDER_SPAWN_EGG)
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(SPIDER_TAMER)
        }

        ServerTickEvents.END_SERVER_TICK.register { server -> onServerTick(server) }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> registerCommands(dispatcher) }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            AppState.removeAllBodies()
            SpiderSpawnManager.reset()
        }

        AppState.setup()
    }

    private fun onServerTick(server: MinecraftServer) {
        AppState.ecs.update()
        AppState.ecs.render()
        DisplayTracker.endRender()
        SpiderSpawnManager.tick(server)
    }
}

/**
 * Hosts the ECS world and its systems. Each [SpiderMob] owns one ECS [SpiderBody] (created via
 * [spawnBody], removed via [removeBody]); the systems below tick/animate/render every body.
 * Also hosts the /spider "newinstance" feature: a per-player spider body with no mob behind it
 * that follows its player around at a chosen size.
 */
object AppState {
    /** The spider spots and chases a player within this radius (blocks). Config-backed; also
     *  settable in-game via /spider chasedistance, which persists back into the config file. */
    val chaseRadius: Double get() = Config.CHASE_DISTANCE.get()

    val ecs = Ecs()
    private var started = false

    private class PersonalSpider(val entity: EcsEntity, val body: SpiderBody)
    private val instances = HashMap<UUID, PersonalSpider>()

    fun setup() {
        if (started) return
        started = true

        setupSpiderBody(ecs)
        setupBehaviours(ecs)

        // Control: every wild (non-manual) body is driven by SpiderAI's wander/alert/chase state
        // machine — calm patrol until a player comes within chaseRadius, a freeze-and-face-you
        // beat, then the charge (with exit hysteresis so it doesn't flicker at the boundary).
        // This is the ONLY place SpiderAI.update is called (mode timers must tick exactly once).
        // The melee hit is done by SpiderMob. Bodies under manual control (a rider, or a
        // /spider newinstance) are driven elsewhere.
        ecs.onTick {
            for ((entity, spider) in ecs.query<EcsEntity, SpiderBody>()) {
                if (spider.manualControl) continue
                SpiderAI.update(entity, spider, chaseRadius)
            }
        }

        // Personal spiders (/spider newinstance): each one follows ITS OWN player around at the
        // chosen size, resizable live with /spider size. Removed on logout/death or when the owner
        // leaves creative (it's a creative-mode toy).
        ecs.onTick {
            val ended = mutableListOf<UUID>()
            for ((uuid, instance) in instances) {
                val player = instance.body.level.players().find { it.uuid == uuid }
                if (player == null || !player.isAlive || !player.isCreative) {
                    ended.add(uuid)
                    continue
                }
                val follow = (instance.body.walkGait.stationary.bodyHeight * 1.5).coerceAtMost(6.0)
                instance.entity.replaceComponent<SpiderBehaviour>(
                    TargetBehaviour(Vector3d(player.x, player.y, player.z), follow))
            }
            for (uuid in ended) {
                instances.remove(uuid)?.let { removeBody(it.entity) }
            }
        }

        // ACTIVE CAMOUFLAGE: camo bodies continuously repaint each leg with the block its foot is
        // standing on, so the spider matches the terrain it crosses (see GroundCamo).
        ecs.onTick {
            for (spider in ecs.query<SpiderBody>()) {
                if (spider.variantKey == "camo") GroundCamo.update(spider)
            }
        }

        // Sounds (ported from SoundsAndParticles, step + land only).
        //  - NETHERITE: locked to its iconic clank.
        //  - CAMO: plays the step/fall sound OF THE BLOCK under the foot, at the block's natural
        //    pitch — exactly like player footsteps — falling back to the configured variant sound
        //    when the foot is over air. variantStepVolume/variantLandVolume still scale loudness.
        //  - POISON: locked to the warped wart squish (it IS eerie Nether fungus).
        //  - any other/future variant: the configured variant sounds.
        ecs.onEvent<LegStepEvent> { e ->
            when (e.spider.variantKey) {
                "netherite" -> e.spider.level.playSoundAt(e.leg.endEffector, SoundEvents.NETHERITE_BLOCK_STEP, 0.3f, 1.0f)
                "poison" -> e.spider.level.playSoundAt(e.leg.endEffector, SoundEvents.WART_BLOCK_STEP,
                    Config.VARIANT_STEP_VOLUME.get().toFloat(), 0.9f)
                "camo" -> {
                    val ground = GroundCamo.blockUnder(e.spider.level, e.leg.endEffector)
                    if (ground != null) {
                        val sound = ground.soundType
                        e.spider.level.playSoundAt(e.leg.endEffector, sound.stepSound,
                            sound.volume * Config.VARIANT_STEP_VOLUME.get().toFloat(), sound.pitch)
                    } else {
                        e.spider.level.playSoundAt(e.leg.endEffector,
                            resolveSound(Config.VARIANT_STEP_SOUND.get(), SoundEvents.NETHERITE_BLOCK_STEP),
                            Config.VARIANT_STEP_VOLUME.get().toFloat(), 1.0f)
                    }
                }
                else -> e.spider.level.playSoundAt(e.leg.endEffector,
                    resolveSound(Config.VARIANT_STEP_SOUND.get(), SoundEvents.NETHERITE_BLOCK_STEP),
                    Config.VARIANT_STEP_VOLUME.get().toFloat(), 1.0f)
            }
        }
        ecs.onEvent<SpiderBodyHitGroundEvent> { e ->
            when (e.spider.variantKey) {
                "netherite" -> e.spider.level.playSoundAt(e.spider.position, SoundEvents.NETHERITE_BLOCK_FALL, 1.0f, 0.8f)
                "poison" -> e.spider.level.playSoundAt(e.spider.position, SoundEvents.WART_BLOCK_FALL,
                    Config.VARIANT_LAND_VOLUME.get().toFloat(), 0.8f)
                "camo" -> {
                    // The body sits ~bodyHeight above ground, so scan a bit deeper than a foot.
                    val ground = GroundCamo.blockUnder(e.spider.level, e.spider.position, depth = 6)
                    if (ground != null) {
                        val sound = ground.soundType
                        e.spider.level.playSoundAt(e.spider.position, sound.fallSound,
                            sound.volume * Config.VARIANT_LAND_VOLUME.get().toFloat(), sound.pitch)
                    } else {
                        e.spider.level.playSoundAt(e.spider.position,
                            resolveSound(Config.VARIANT_LAND_SOUND.get(), SoundEvents.NETHERITE_BLOCK_FALL),
                            Config.VARIANT_LAND_VOLUME.get().toFloat(), 0.8f)
                    }
                }
                else -> e.spider.level.playSoundAt(e.spider.position,
                    resolveSound(Config.VARIANT_LAND_SOUND.get(), SoundEvents.NETHERITE_BLOCK_FALL),
                    Config.VARIANT_LAND_VOLUME.get().toFloat(), 0.8f)
            }
        }

        // Render.
        ecs.onRender {
            for (spider in ecs.query<SpiderBody>()) renderSpider(spider).submit(spider)
        }

        ecs.start()
    }

    /** Create a simulated spider body for a mob. Returns its ECS entity + body. */
    fun spawnBody(level: ServerLevel, spawn: Vector3d, yaw: Float, options: SpiderOptions): Pair<EcsEntity, SpiderBody> {
        // Sweep leftover displays from a previous session only when no spider currently exists, so
        // we never clobber the displays of concurrently-living spiders.
        if (ecs.query<SpiderBody>().isEmpty()) level.removeOrphanDisplays(spawn)

        val body = SpiderBody.fromSpawn(level, spawn, yaw, 0f, options.bodyPlan, options.gallopGait, options.walkGait)
        val entity = ecs.spawn(body)
        return entity to body
    }

    /** Remove one spider's body; its displays are culled on the next render pass. */
    fun removeBody(entity: EcsEntity) {
        ecs.remove(entity)
    }

    /** Tear everything down (server stopping). */
    fun removeAllBodies() {
        instances.clear()
        ecs.entities.clear()
        DisplayTracker.removeAll()
        SpiderAI.reset()
    }

    /** Look up a sound event by config id, falling back to the classic sound on a bad id. */
    private fun resolveSound(id: String, fallback: SoundEvent): SoundEvent {
        val loc = ResourceLocation.tryParse(id) ?: return fallback
        return BuiltInRegistries.SOUND_EVENT.get(loc) ?: fallback
    }

    // ---- /spider newinstance ------------------------------------------------------------------

    /** /spider newinstance [size] — spawn the player's personal spider: it follows them around at
     *  the chosen size and can be resized live with /spider size. Creative-only. */
    fun newInstance(player: ServerPlayer, size: Double) {
        releaseSpider(player)

        val level = player.level() as ServerLevel
        val spawn = Vector3d(player.x, player.y + 1.0, player.z)
        val (entity, body) = spawnBody(level, spawn, player.yRot, defaultSpider())
        body.manualControl = true   // follows its OWNER, not the nearest player
        body.setSizeScale(size)
        body.setSpeedScale(SpiderMob.scaleToSpeedFactor(size).coerceAtLeast(2.0))   // keeps up with sprinting
        instances[player.uuid] = PersonalSpider(entity, body)
    }

    /** /spider release — remove the player's personal spider. Returns false if there was none. */
    fun releaseSpider(player: ServerPlayer): Boolean {
        val instance = instances.remove(player.uuid) ?: return false
        removeBody(instance.entity)
        return true
    }

    /** /spider size <n> — live-resize the player's personal spider. Returns false if none. */
    fun resizeSpider(player: ServerPlayer, size: Double): Boolean {
        val instance = instances[player.uuid] ?: return false
        instance.body.setSizeScale(size)
        instance.body.setSpeedScale(SpiderMob.scaleToSpeedFactor(size).coerceAtLeast(2.0))
        return true
    }
}

private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
    // /spider newinstance [size] | release | size <n> | chasedistance <blocks>
    dispatcher.register(
        Commands.literal("spider")
            .then(Commands.literal("newinstance")
                .executes { ctx ->
                    val player = ctx.source.playerOrException
                    if (!player.isCreative) {
                        ctx.source.sendFailure(Component.literal("/spider newinstance is a creative-mode-only feature."))
                        0
                    } else {
                        AppState.newInstance(player, 1.0)
                        ctx.source.sendSuccess({ Component.literal("Spawned your personal spider — it follows you. /spider size <n> to resize, /spider release to remove.") }, false)
                        1
                    }
                }
                .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.3, 20.0))
                    .executes { ctx ->
                        val player = ctx.source.playerOrException
                        if (!player.isCreative) {
                            ctx.source.sendFailure(Component.literal("/spider newinstance is a creative-mode-only feature."))
                            0
                        } else {
                            val size = DoubleArgumentType.getDouble(ctx, "size")
                            AppState.newInstance(player, size)
                            ctx.source.sendSuccess({ Component.literal("Spawned your personal spider (size $size) — it follows you. /spider size <n> to resize, /spider release to remove.") }, false)
                            1
                        }
                    }))
            .then(Commands.literal("release")
                .executes { ctx ->
                    if (AppState.releaseSpider(ctx.source.playerOrException)) {
                        ctx.source.sendSuccess({ Component.literal("Spider released.") }, false)
                        1
                    } else {
                        ctx.source.sendFailure(Component.literal("You have no spider. Use /spider newinstance first."))
                        0
                    }
                })
            .then(Commands.literal("size")
                .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.3, 20.0))
                    .executes { ctx ->
                        // No gamemode gate needed: this only acts on YOUR personal spider, and
                        // creating one already required creative.
                        val player = ctx.source.playerOrException
                        val size = DoubleArgumentType.getDouble(ctx, "size")
                        if (AppState.resizeSpider(player, size)) {
                            ctx.source.sendSuccess({ Component.literal("Spider size set to $size.") }, false)
                            1
                        } else {
                            ctx.source.sendFailure(Component.literal("You have no spider. Use /spider newinstance first."))
                            0
                        }
                    }))
            .then(Commands.literal("chasedistance").requires { it.hasPermission(2) }
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(8.0, 256.0))
                    .executes { ctx ->
                        // Kept as a shorthand for /spider config chaseDistance set <blocks>.
                        Config.CHASE_DISTANCE.set(DoubleArgumentType.getDouble(ctx, "blocks"))
                        ctx.source.sendSuccess({ Component.literal("Spider chase distance set to ${AppState.chaseRadius} blocks (saved to config).") }, true)
                        1
                    }))
            .then(buildConfigSubtree()))
}

/**
 * /spider config <key> set <value> | get  — one node per config entry, generated straight from
 * Config.entries, so every tunable is live-editable in-game with the right argument type (and
 * range). Sound-id entries tab-complete against EVERY built-in Minecraft sound. OP-only; every
 * set persists immediately into config/arachnomod-common.toml (autosave) and, because the mod
 * reads config values live, applies to the active spider on the very next tick.
 */
private fun buildConfigSubtree(): com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> {
    // CommandSourceStack implements SharedSuggestionProvider; vanilla's own /playsound does the
    // same adaptation, Kotlin just needs it spelled out.
    @Suppress("UNCHECKED_CAST")
    val soundSuggestions = SuggestionProviders.AVAILABLE_SOUNDS as SuggestionProvider<CommandSourceStack>

    val config = Commands.literal("config").requires { it.hasPermission(2) }

    for (entry in Config.entries) {
        fun feedback(ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
            ctx.source.sendSuccess({ Component.literal("Spider config '${entry.path}' set to ${entry.get()} (saved).") }, true)
            return 1
        }

        val setArg: RequiredArgumentBuilder<CommandSourceStack, *> = when (entry) {
            is Config.DoubleValue ->
                Commands.argument("value", DoubleArgumentType.doubleArg(entry.min, entry.max))
                    .executes { ctx -> entry.set(DoubleArgumentType.getDouble(ctx, "value")); feedback(ctx) }
            is Config.IntValue ->
                Commands.argument("value", IntegerArgumentType.integer(entry.min, entry.max))
                    .executes { ctx -> entry.set(IntegerArgumentType.getInteger(ctx, "value")); feedback(ctx) }
            is Config.BooleanValue ->
                Commands.argument("value", BoolArgumentType.bool())
                    .executes { ctx -> entry.set(BoolArgumentType.getBool(ctx, "value")); feedback(ctx) }
            is Config.SoundIdValue ->
                Commands.argument("value", ResourceLocationArgument.id())
                    .suggests(soundSuggestions)
                    .executes { ctx -> entry.set(ResourceLocationArgument.getId(ctx, "value").toString()); feedback(ctx) }
            else -> continue
        }

        config.then(Commands.literal(entry.path)
            .then(Commands.literal("set").then(setArg))
            .then(Commands.literal("get").executes { ctx ->
                ctx.source.sendSuccess({ Component.literal("Spider config '${entry.path}' is ${entry.get()}.") }, false)
                1
            }))
    }

    return config
}
