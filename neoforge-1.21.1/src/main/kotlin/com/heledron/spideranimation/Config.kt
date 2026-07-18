package com.heledron.spideranimation

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * All gameplay tunables, exposed as `config/arachnomod-common.toml` (created on first launch).
 * Values are read LIVE every tick, and the file hot-reloads when edited on disk - most changes
 * apply to a running world within moments, no restart needed.
 *
 * Every entry also self-registers a uniform [Entry] wrapper into [entries]; the /spider config
 * command tree is generated from that list, so adding a key here automatically adds its command.
 * (The wrapper carries min/max so commands can range-check, since ModConfigSpec doesn't expose
 * its bounds for reading.)
 */
object Config {
    private val BUILDER = ModConfigSpec.Builder()

    val entries = mutableListOf<Entry>()

    /**
     * Write the loaded config back to disk NOW. ModConfigSpec.ConfigValue.set() only updates the
     * IN-MEMORY value — without an explicit spec save, every /spider config change silently
     * reverts on restart (verified: file still had the old value after a "(saved)" set).
     */
    fun saveNow() {
        SPEC.save()
    }

    /** Uniform, backend-agnostic view over one config value for the command generator. */
    sealed class Entry(val path: String) {
        abstract fun get(): Any

        class D(path: String, private val value: ModConfigSpec.DoubleValue, val min: Double, val max: Double) : Entry(path) {
            override fun get() = value.get()
            fun set(v: Double) { value.set(v.coerceIn(min, max)); saveNow() }
        }
        class I(path: String, private val value: ModConfigSpec.IntValue, val min: Int, val max: Int) : Entry(path) {
            override fun get() = value.get()
            fun set(v: Int) { value.set(v.coerceIn(min, max)); saveNow() }
        }
        class B(path: String, private val value: ModConfigSpec.BooleanValue) : Entry(path) {
            override fun get() = value.get()
            fun set(v: Boolean) { value.set(v); saveNow() }
        }
        /** A sound-event resource location like "minecraft:block.moss.step". */
        class Sound(path: String, private val value: ModConfigSpec.ConfigValue<String>) : Entry(path) {
            override fun get() = value.get()
            fun set(v: String) { value.set(v); saveNow() }
        }
    }

    private fun define(path: String, default: Double, min: Double, max: Double, vararg comment: String): ModConfigSpec.DoubleValue {
        val v = BUILDER.comment(*comment).defineInRange(path, default, min, max)
        entries.add(Entry.D(path, v, min, max))
        return v
    }
    private fun define(path: String, default: Int, min: Int, max: Int, vararg comment: String): ModConfigSpec.IntValue {
        val v = BUILDER.comment(*comment).defineInRange(path, default, min, max)
        entries.add(Entry.I(path, v, min, max))
        return v
    }
    private fun define(path: String, default: Boolean, vararg comment: String): ModConfigSpec.BooleanValue {
        val v = BUILDER.comment(*comment).define(path, default)
        entries.add(Entry.B(path, v))
        return v
    }
    private fun defineSound(path: String, default: String, vararg comment: String): ModConfigSpec.ConfigValue<String> {
        val v = BUILDER.comment(*comment).define(path, default)
        entries.add(Entry.Sound(path, v))
        return v
    }

    // ---- One-time migration of stale defaults --------------------------------------------
    // Defaults only apply to NEWLY generated files: without this, everyone updating from an
    // older version silently keeps the old pacing (5-30 min first spawn) and the weaker spawn
    // search — "the spider never shows up". Runs on the RAW file BEFORE the spec loads it
    // (call migrateConfigFile from the mod constructor, before registerConfig). Each migration
    // upgrades a value ONLY if it still equals its OLD default — customization is never touched.
    private const val CONFIG_VERSION = 3
    // Grouped by the config version that INTRODUCED each batch: a file older than that
    // version gets the batch applied (each value still only if it sits at its old default).
    private val MIGRATIONS: Map<Int, List<Triple<String, Any, Any>>> = mapOf(
        2 to listOf(
            Triple("spawnMinMinutes", 5.0, 1.0),     // v1.1.5: the hunt begins one minute in
            Triple("spawnMaxMinutes", 30.0, 1.0),
            Triple("spawnAngleAttempts", 12, 24),    // v1.1.4: reliable rough-terrain spawning
        ),
        3 to listOf(
            Triple("maxHealth", 1000.0, 600.0),      // v1.2.6: health nerf; netherite wears armor now
        ),
    )

    fun migrateConfigFile(configDir: java.nio.file.Path) {
        val file = configDir.resolve("arachnomod-common.toml")
        if (!java.nio.file.Files.exists(file)) return   // fresh install: spec creates a new file
        val cfg = com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(file)
            .preserveInsertionOrder().build()
        cfg.use {
            it.load()
            val hasContent = it.get<Any?>("spawnMinMinutes") != null
            val fileVersion = (it.get<Any?>("configVersion") as? Number)?.toInt()
                ?: if (hasContent) 1 else CONFIG_VERSION   // content but no version = pre-1.1.6 file
            for ((sinceVersion, batch) in MIGRATIONS) {
                if (fileVersion >= sinceVersion) continue
                for ((path, old, new) in batch) {
                    val current = it.get<Any?>(path)
                    val stillAtOldDefault = when (old) {
                        is Double -> (current as? Number)?.toDouble() == old
                        is Int -> (current as? Number)?.toInt() == old
                        else -> current == old
                    }
                    if (stillAtOldDefault) it.set<Any?>(path, new)
                }
            }
            it.set<Any?>("configVersion", CONFIG_VERSION)
            it.save()
        }
    }

    // Defined in the spec so load-correction KEEPS the version stamp (unknown keys get removed).
    // Deliberately NOT in `entries`: it isn't a gameplay setting and gets no /spider config node.
    val CONFIG_VERSION_VALUE: ModConfigSpec.IntValue = BUILDER
        .comment("Internal config-format version - do not edit.")
        .defineInRange("configVersion", CONFIG_VERSION, 1, CONFIG_VERSION)

    // ---- Spawning --------------------------------------------------------------------------
    val SPAWN_MIN_MINUTES = define("spawnMinMinutes", 1.0, 0.05, 1440.0,
        "Minimum minutes before the FIRST spider of a session spawns (default 1 - the hunt begins fast).")
    val SPAWN_MAX_MINUTES = define("spawnMaxMinutes", 1.0, 0.05, 1440.0,
        "Maximum minutes for the FIRST spawn (random between min and max; killed spiders use respawnAfterKillMinutes).")
    val PEACEFUL_EXIT_SPAWN_MINUTES = define("peacefulExitSpawnMinutes", 1.0, 0.05, 1440.0,
        "Minutes until the spider spawns after Peaceful difficulty is switched OFF.",
        "Only applies when Peaceful itself removed the spider - a killed spider keeps its",
        "respawnAfterKillMinutes cooldown (toggling Peaceful can't shortcut it).")
    val RESPAWN_AFTER_KILL_MINUTES = define("respawnAfterKillMinutes", 40.0, 0.05, 1440.0,
        "Minutes until the next spider after one is KILLED (40 = 2 Minecraft days).",
        "Slaying it buys real peace; spawnMin/spawnMax only govern the FIRST spawn.")
    val SPAWN_DISTANCE_MIN = define("spawnDistanceMin", 30.0, 4.0, 128.0,
        "Closest distance (blocks) from a player that a spider may naturally spawn.")
    val SPAWN_DISTANCE_MAX = define("spawnDistanceMax", 34.0, 4.0, 128.0,
        "Farthest distance (blocks) from a player that a spider may naturally spawn.")
    val SPAWN_ANGLE_ATTEMPTS = define("spawnAngleAttempts", 24, 4, 64,
        "How many directions around the player are tested at each candidate distance when",
        "looking for safe ground. If the spider fails to spawn in rough terrain (dense",
        "forest, snowy peaks, cliffs), RAISE this - more directions = more spawn spots found.")
    val SPAWN_MAX_VERTICAL_SEARCH = define("spawnMaxVerticalSearch", 48, 4, 384,
        "How many blocks downward from the surface a candidate column is scanned looking for",
        "solid, dry ground before that candidate is rejected. Keep this generous for SkyBlock",
        "and OneBlock style maps where solid ground can be far below the heightmap hit.")
    val CAMO_VARIANT_CHANCE = define("camoVariantChance", 0.25, 0.0, 1.0,
        "Chance (0.0-1.0) that a naturally-spawned spider is the mossy CAMO variant instead",
        "of the netherite one. There is still only ever ONE spider in the world at a time.")

    // ---- Chase & speed ---------------------------------------------------------------------
    val CHASE_DISTANCE = define("chaseDistance", 64.0, 8.0, 256.0,
        "How far away (blocks) the spider spots and chases players.",
        "Also settable in-game with /spider config chaseDistance set <blocks>.")
    val CHASE_EXIT_MULTIPLIER = define("chaseExitDistanceMultiplier", 1.25, 1.0, 3.0,
        "Once chasing, the spider keeps chasing until the player is this many times",
        "chaseDistance away, instead of letting go right at the edge of its detection range.",
        "Prevents flickering between chase and wander when a player paces the boundary.")
    val ALERT_REACTION_TICKS = define("alertReactionTicks", 10, 0, 200,
        "When a wandering spider first spots a player, it freezes and snaps to face them for",
        "this many ticks (20 = 1 second) before charging - the 'it just noticed you' beat.",
        "Set to 0 to disable and chase immediately.")
    val HOSTILE_ONLY_AT_NIGHT = define("hostileOnlyAtNight", false,
        "If true, the spider only hunts (and bites) players at night, like a vanilla spider.",
        "During the day it just wanders.")
    val CHASE_SPEED = define("chaseSpeedBlocksPerSecond", 8.0, 0.5, 40.0,
        "Top chase speed in blocks/second at NORMAL size (cruising speed is a bit lower).",
        "The spider moves faster than this as it grows - see speedGrowthFactor.")
    val SPEED_GROWTH_FACTOR = define("speedGrowthFactor", 8.0, 1.0, 32.0,
        "Speed multiplier at maximum size: a huge, far-away spider charges at chase speed x this.")
    val LEG_STEP_SPEED = define("legStepSpeed", 1.1, 0.1, 5.0,
        "How fast the legs swing when taking a step (blocks/tick at normal size) - the 'scurry'.")

    // ---- Wandering -------------------------------------------------------------------------
    val ENABLE_WANDERING = define("enableWandering", true,
        "If false, the spider stands still (pre-1.1 behaviour) until it spots a player.")
    val WANDER_SPEED_FACTOR = define("wanderSpeedFactor", 0.35, 0.05, 1.0,
        "Wander/patrol speed as a fraction of chase speed (0.35 = 35%). Keep this well below",
        "1.0 so the sudden jump to full chase speed actually feels sudden.")
    val WANDER_RADIUS = define("wanderRadius", 24.0, 4.0, 128.0,
        "Max distance (blocks) the spider patrols from the spot it started wandering at.")
    val WANDER_MIN_INTERVAL_SECONDS = define("wanderMinIntervalSeconds", 3.0, 0.5, 120.0,
        "Shortest time the spider commits to a patrol heading before picking a new one.")
    val WANDER_MAX_INTERVAL_SECONDS = define("wanderMaxIntervalSeconds", 9.0, 0.5, 300.0,
        "Longest time the spider commits to a patrol heading before picking a new one.")
    val WANDER_PAUSE_CHANCE = define("wanderPauseChance", 0.25, 0.0, 1.0,
        "Chance (0.0-1.0) that, instead of walking somewhere new, the spider just pauses a beat.")
    val GROOMING_CHANCE = define("groomingChance", 0.03, 0.0, 1.0,
        "Only while wandering is DISABLED: chance PER SECOND that the idle spider grooms -",
        "lifting its front legs to its mouth and cleaning them (0.03 = 3%/sec). 0 turns it off.",
        "The idle spider also breathes (a gentle body bob) whenever wandering is disabled.")

    // ---- Size & growth ---------------------------------------------------------------------
    val MIN_SIZE = define("minSize", 0.6, 0.1, 10.0,
        "The spider's size when right next to a player (1.0 = the original spider's size).")
    val SQUEEZE_SIZE = define("squeezeSize", 0.25, 0.1, 1.0,
        "When a hiding player is vertically out of reach and the spider is right on top of",
        "them, it SQUEEZES below minSize down to this - 0.25 fits a 1x1x1 hole, just barely -",
        "to come in after them. It regrows the moment the squeeze is over. No hole is safe.")
    val MAX_SIZE = define("maxSize", 15.0, 0.5, 50.0,
        "The spider's size when far away. 15 towers over the trees (~16-block body).")
    val SIZE_NEAR_DISTANCE = define("sizeNearDistance", 4.0, 0.0, 64.0,
        "At/below this distance (blocks) from the nearest player the spider is at minSize.")
    val SIZE_FAR_DISTANCE = define("sizeFarDistance", 32.0, 1.0, 128.0,
        "At/above this distance (blocks) the spider is at maxSize.")
    val GROW_PERCENT_PER_TICK = define("growPercentPerTick", 12.0, 0.5, 100.0,
        "Fastest the spider can GROW, in percent per tick (12 = full grow in ~1.5s).")
    val SHRINK_PERCENT_PER_TICK = define("shrinkPercentPerTick", 25.0, 0.5, 100.0,
        "Fastest the spider can SHRINK, in percent per tick (25 = full shrink in ~0.7s).",
        "Kept faster than growing so it melts down promptly as it reaches you.")
    val RIDDEN_SIZE = define("riddenSize", 2.0, 0.3, 20.0,
        "The stable size the spider settles to while a player is riding it.")
    val GROW_IN_WATER = define("growInWater", true,
        "If true, a spider standing in water GROWS just big enough for its body to ride",
        "above the surface - whatever the depth - so it never drowns and keeps chasing",
        "swimmers. Set to false to keep water as a weakness: a small spider lured into",
        "deep water will stay small and drown.")

    // ---- Combat & drops --------------------------------------------------------------------
    val MAX_HEALTH = define("maxHealth", 600.0, 1.0, 1000000.0,
        "The spider's maximum health (default 600). The netherite variant is additionally",
        "protected by the stats of a full netherite armor suit; the camo variant is not.")
    val ATTACK_DAMAGE_HEARTS = define("attackDamageHearts", 6.0, 0.0, 100.0,
        "Melee damage in HEARTS per hit.")
    val ATTACK_COOLDOWN_TICKS = define("attackCooldownTicks", 20, 1, 400,
        "Ticks between melee hits (20 = one hit per second).")
    val NETHERITE_DROP_CHANCE = define("netheriteDropChance", 0.5, 0.0, 1.0,
        "Chance (0.0-1.0) to drop a single netherite ingot on death. 0.5 = half the time.")

    // ---- Variant sounds --------------------------------------------------------------------
    // The default (netherite) spider ALWAYS uses its iconic netherite step/fall sounds. The CAMO
    // variant automatically plays the step/fall sound OF THE BLOCK it walks on (like a player's
    // footsteps) - these entries are its fallback (foot over air) and the sound set for any
    // other/future variants.
    val VARIANT_STEP_SOUND = defineSound("variantStepSound", "minecraft:block.moss.step",
        "Fallback/other-variant step sound (camo normally plays the walked-on block's own",
        "step sound). Any built-in sound id, e.g. minecraft:block.amethyst_block.chime.")
    val VARIANT_STEP_VOLUME = define("variantStepVolume", 0.3, 0.0, 10.0,
        "Volume of variant step sounds (scales camo's block-matched steps too).")
    val VARIANT_LAND_SOUND = defineSound("variantLandSound", "minecraft:block.moss.fall",
        "Fallback/other-variant landing sound (camo normally plays the block's own fall sound).")
    val VARIANT_LAND_VOLUME = define("variantLandVolume", 1.0, 0.0, 10.0,
        "Volume of variant landing sounds (scales camo's block-matched landings too).")

    val SPEC: ModConfigSpec = BUILDER.build()
}
