package com.heledron.spideranimation

import com.heledron.spideranimation.ecs.Ecs
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.entity.ArachnoClient
import com.heledron.spideranimation.entity.GroundCamo
import com.heledron.spideranimation.entity.ConfigBook
import com.heledron.spideranimation.entity.ConfigBookItem
import com.heledron.spideranimation.entity.ConfigBookNet
import com.heledron.spideranimation.entity.LaserPointerItem
import com.heledron.spideranimation.entity.SpiderAI
import com.heledron.spideranimation.entity.SpiderDebug
import com.heledron.spideranimation.entity.SpiderMob
import com.heledron.spideranimation.entity.SpiderSpawnManager
import com.heledron.spideranimation.entity.WardBlock
import com.heledron.spideranimation.entity.WardRegistry
import com.heledron.spideranimation.platform.DisplayTracker
import com.heledron.spideranimation.platform.playSoundAt
import com.heledron.spideranimation.platform.removeOrphanDisplays
import com.heledron.spideranimation.spider.*
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.permissions.Permissions
import net.minecraft.sounds.SoundEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.SpawnEggItem
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import org.joml.Vector3d
import java.util.UUID
import java.util.function.Supplier
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(SpiderAnimationMod.ID)
object SpiderAnimationMod {
    const val ID = "arachnomod"

    val ENTITY_TYPES: DeferredRegister<EntityType<*>> = DeferredRegister.create(Registries.ENTITY_TYPE, ID)
    val SPIDER_ENTITY: DeferredHolder<EntityType<*>, EntityType<SpiderMob>> =
        ENTITY_TYPES.register("spider", Supplier {
            EntityType.Builder.of<SpiderMob>({ type, level -> SpiderMob(type, level) }, MobCategory.MONSTER)
                .sized(2.0f, 2.0f)
                .clientTrackingRange(16)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "spider")))
        })

    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(ID)
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(ID)
    val SPIDER_SPAWN_EGG: DeferredItem<SpawnEggItem> =
        ITEMS.registerItem("spider_spawn_egg") { props ->
            // 26.x: NeoForge dropped DeferredSpawnEggItem — it only existed because the vanilla
            // constructor needed a resolved EntityType up front. Vanilla now binds the entity as
            // a data component via Properties.spawnEgg(...), so plain SpawnEggItem is enough.
            // The old explicit egg colours (grey base, dark-grey spots) are gone with it.
            SpawnEggItem(props.spawnEgg(SPIDER_ENTITY.get()))
        }

    /** Creative-only (no recipe): right-click the spider to tame it; tamed + empty hand = ride. */
    val SPIDER_TAMER: DeferredItem<Item> =
        ITEMS.registerItem("spider_tamer") { props -> Item(props.stacksTo(1)) }

    /** The WARDING IDOL: placed, it stops players near it anchoring a spider spawn. See WardRegistry. */
    val WARDING_IDOL: DeferredBlock<Block> = BLOCKS.registerBlock(
        "warding_idol",
        { props -> WardBlock(props) },
        // 26.1 dropped the plain-Properties overload: the properties arrive as an operator on a
        // Properties NeoForge has already stamped with the registry id.
        java.util.function.UnaryOperator { props ->
            props.strength(3.0f, 12.0f)     // pickaxe work, and it shrugs off a creeper
                .requiresCorrectToolForDrops()
                .lightLevel { 7 }           // eyes glow like a redstone torch (light 7), same on every build
                .sound(SoundType.DEEPSLATE)
        },
    )

    // registerSimpleBlockItem, not a hand-rolled BlockItem: it defers the block lookup instead of
    // calling .get() while the registries are still filling.
    val WARDING_IDOL_ITEM: DeferredItem<BlockItem> =
        ITEMS.registerSimpleBlockItem("warding_idol", WARDING_IDOL)

    /** Creative-only (no recipe): right-click to shine the dot and the spider chases it. */
    val SPIDER_LASER_POINTER: DeferredItem<Item> =
        ITEMS.registerItem("spider_laser_pointer") { props -> LaserPointerItem(props.stacksTo(1)) }

    /** Right-click for the in-game settings screen (see ConfigBook). Given on first join. */
    val SPIDER_CONFIG_BOOK: DeferredItem<Item> =
        ITEMS.registerItem("spider_config_book") { props -> ConfigBookItem(props.stacksTo(1)) }

    // THE MOD'S OWN CREATIVE TAB: everything it adds, in one place. Each item is ALSO still
    // listed in the vanilla tab it belongs to (spawn eggs, tools, functional blocks).
    val CREATIVE_TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID)
    val CREATIVE_TAB = CREATIVE_TABS.register("arachnomod", Supplier {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arachnomod"))
            .icon { ItemStack(SPIDER_SPAWN_EGG.get()) }
            .displayItems { _, output ->
                output.accept(SPIDER_SPAWN_EGG.get())
                output.accept(WARDING_IDOL_ITEM.get())
                output.accept(SPIDER_TAMER.get())
                output.accept(SPIDER_LASER_POINTER.get())
                output.accept(SPIDER_CONFIG_BOOK.get())
            }
            .build()
    })

    init {
        // All gameplay tunables live in config/arachnomod-common.toml (see Config). Migrate any
        // stale pre-1.1.6 defaults in the RAW file first — the spec load would otherwise keep
        // the old pacing forever (defaults only apply to newly generated files).
        Config.migrateConfigFile(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get())
        ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)

        val modBus: IEventBus = MOD_BUS
        ENTITY_TYPES.register(modBus)
        ITEMS.register(modBus)
        BLOCKS.register(modBus)
        CREATIVE_TABS.register(modBus)

        // The registry never touches the block handle itself - .get() here, a plain Block on
        // Fabric - because that difference has silently broken copied code before.
        WardRegistry.isWardBlock = { state -> state.block === WARDING_IDOL.get() }

        // The config book: its item, its two packets, and one for each new player who may use it.
        ConfigBook.bookItem = { SPIDER_CONFIG_BOOK.get() }
        ConfigBookNet.register(modBus)
        modBus.addListener(::onBuildCreativeTabs)
        modBus.addListener(::onCreateAttributes)

        // Client-only renderer registration (NoopRenderer). Never touched on a dedicated server.
        if (FMLEnvironment.getDist() == Dist.CLIENT) ArachnoClient.register(modBus)

        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStopping)
        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)

        AppState.setup()
    }

    private fun onBuildCreativeTabs(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.SPAWN_EGGS) event.accept(SPIDER_SPAWN_EGG.get())
        if (event.tabKey == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(SPIDER_TAMER.get())
        if (event.tabKey == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(SPIDER_LASER_POINTER.get())
        if (event.tabKey == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(SPIDER_CONFIG_BOOK.get())
        if (event.tabKey == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(WARDING_IDOL_ITEM.get())
    }

    private fun onCreateAttributes(event: EntityAttributeCreationEvent) {
        event.put(SPIDER_ENTITY.get(), SpiderMob.createAttributes().build())
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        AppState.ecs.update()
        AppState.ecs.render()
        DisplayTracker.endRender()
        SpiderSpawnManager.tick(event.server)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        registerCommands(event.dispatcher)
    }

    private fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        (event.entity as? ServerPlayer)?.let { ConfigBook.onJoin(it) }
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        AppState.removeAllBodies()
        SpiderSpawnManager.reset()
        WardRegistry.reset()
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
        //  - HUNTER: dead silent. You will not hear it coming — that is its job.
        //  - any other/future variant: the configured variant sounds.
        ecs.onEvent<LegStepEvent> { e ->
            when (e.spider.variantKey) {
                "netherite" -> e.spider.level.playSoundAt(e.leg.endEffector, SoundEvents.NETHERITE_BLOCK_STEP, 0.3f, 1.0f)
                "hunter" -> {}   // silent steps
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
                "hunter" -> {}   // silent landings too
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
            for (spider in ecs.query<SpiderBody>()) {
                renderSpider(spider).submit(spider)
                if (SpiderDebug.enabled) renderSpiderDebug(spider).submit(spider to "debug")
            }
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
        val loc = Identifier.tryParse(id) ?: return fallback
        return BuiltInRegistries.SOUND_EVENT.getValue(loc) ?: fallback
    }

    // ---- /spider newinstance ------------------------------------------------------------------

    /** /spider newinstance [size] — spawn the player's personal spider: it follows them around at
     *  the chosen size and can be resized live with /spider size. Creative-only. */
    fun newInstance(player: ServerPlayer, size: Double) {
        releaseSpider(player)

        val level = player.level() as ServerLevel
        val spawn = Vector3d(player.x, player.y + 1.0, player.z)
        // netheriteSpider(), not defaultSpider(): the personal spider IS the netherite one, so it
        // must honour netheriteLegs - otherwise the easiest way to summon one is also the one
        // way you can never see your own leg-count setting.
        val (entity, body) = spawnBody(level, spawn, player.yRot, netheriteSpider())
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
            // The settings screen, without needing the book (read-only for non-operators).
            .then(Commands.literal("menu")
                .executes { ctx -> ConfigBook.open(ctx.source.playerOrException); 1 })
            .then(Commands.literal("debug")
                .requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                .executes { ctx ->
                    SpiderDebug.enabled = !SpiderDebug.enabled
                    val msg =
                        if (SpiderDebug.enabled)
                            SpiderDebug.LEGEND
                        else "Spider debug view OFF."
                    ctx.source.sendSuccess({ Component.literal(msg) }, false)
                    1
                })
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
            .then(Commands.literal("chasedistance").requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(8.0, 256.0))
                    .executes { ctx ->
                        // Kept as a shorthand for /spider config chaseDistance set <blocks>.
                        Config.CHASE_DISTANCE.set(DoubleArgumentType.getDouble(ctx, "blocks"))
                        Config.saveNow()   // ConfigValue.set is in-memory only (see Config.saveNow)
                        ctx.source.sendSuccess({ Component.literal("Spider chase distance set to ${AppState.chaseRadius} blocks (saved to config).") }, true)
                        1
                    }))
            .then(buildConfigSubtree()))
}

/**
 * /spider config <key> set <value> | get  — one node per config entry, generated straight from
 * Config.entries, so every tunable is live-editable in-game with the right argument type (and
 * range). Sound-id entries tab-complete against EVERY built-in Minecraft sound. OP-only; every
 * set persists immediately into config/arachnomod-common.toml and, because the mod reads config
 * values live, applies to the active spider on the very next tick.
 */
private fun buildConfigSubtree(): com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> {
    // CommandSourceStack implements SharedSuggestionProvider; vanilla's own /playsound does the
    // same adaptation, Kotlin just needs it spelled out.
    @Suppress("UNCHECKED_CAST")
    val soundSuggestions = SuggestionProviders.AVAILABLE_SOUNDS as SuggestionProvider<CommandSourceStack>

    val config = Commands.literal("config").requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }

    for (entry in Config.entries) {
        fun feedback(ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
            // Spawn-timing keys re-arm the LIVE countdown (crediting time served) so the change
            // applies now — without this, an armed timer kept its old roll until the next relog.
            SpiderSpawnManager.onConfigSet(entry.path)
            ctx.source.sendSuccess({ Component.literal("Spider config '${entry.path}' set to ${entry.get()} (saved).") }, true)
            return 1
        }

        val setArg: RequiredArgumentBuilder<CommandSourceStack, *> = when (entry) {
            is Config.Entry.D ->
                Commands.argument("value", DoubleArgumentType.doubleArg(entry.min, entry.max))
                    .executes { ctx -> entry.set(DoubleArgumentType.getDouble(ctx, "value")); feedback(ctx) }
            is Config.Entry.I ->
                Commands.argument("value", IntegerArgumentType.integer(entry.min, entry.max))
                    .executes { ctx -> entry.set(IntegerArgumentType.getInteger(ctx, "value")); feedback(ctx) }
            is Config.Entry.B ->
                Commands.argument("value", BoolArgumentType.bool())
                    .executes { ctx -> entry.set(BoolArgumentType.getBool(ctx, "value")); feedback(ctx) }
            is Config.Entry.Sound ->
                Commands.argument("value", IdentifierArgument.id())
                    .suggests(soundSuggestions)
                    .executes { ctx -> entry.set(IdentifierArgument.getId(ctx, "value").toString()); feedback(ctx) }
            is Config.Entry.Str ->
                // greedyString so a whole comma-separated list can be typed, spaces and all.
                Commands.argument("value", StringArgumentType.greedyString())
                    .executes { ctx ->
                        entry.set(SpiderSpawnManager.normalizeDimensionInput(
                            StringArgumentType.getString(ctx, "value")))
                        feedback(ctx)
                    }
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
