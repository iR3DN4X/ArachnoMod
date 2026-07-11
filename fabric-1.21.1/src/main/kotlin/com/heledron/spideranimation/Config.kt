package com.heledron.spideranimation

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import java.nio.file.Files
import java.nio.file.Path

/**
 * All gameplay tunables, exposed as `config/arachnomod-common.toml` (created on first launch).
 * Values are read LIVE every tick, and the file hot-reloads when edited on disk - most changes
 * apply to a running world within moments, no restart needed.
 *
 * Fabric has no built-in config system, so this is a thin wrapper over **night-config** (the same
 * TOML library NeoForge's ModConfigSpec is built on, bundled jar-in-jar). It reproduces the same
 * commented file, the same hot-reload, the same `.get()/.set()` accessor API the rest of the mod
 * already uses, and the same write-back for the /spider config commands.
 *
 * Every entry self-registers into [entries]; the /spider config command tree is generated from
 * that list, so adding a key here automatically adds its command.
 *
 * Existing keys keep their path names/defaults, so a pre-1.1 arachnomod-common.toml keeps working:
 * writeDefaultAndComment() only fills in values that are missing, so old files simply gain the new
 * keys with defaults on next load - nothing already tuned gets overwritten.
 */
object Config {
    val entries = mutableListOf<Entry<*>>()
    private var backing: CommentedFileConfig? = null

    /** Join comment lines with a leading space each, so night-config writes `# line`. */
    private fun comment(vararg lines: String): String = lines.joinToString("\n") { " $it" }

    abstract class Entry<T : Any>(
        val path: String,
        val default: T,
        private val comment: String,
    ) {
        init { @Suppress("LeakingThis") entries.add(this) }

        /** The raw stored value, or null if the config isn't loaded / the key is absent. */
        protected fun raw(): Any? = backing?.get<Any?>(path)

        /** Persist a value (autosave writes it straight to disk). No-op before load. */
        protected fun store(value: T) { backing?.set<Any?>(path, value) }

        abstract fun get(): T

        internal fun writeDefaultAndComment(cfg: CommentedFileConfig) {
            if (cfg.get<Any?>(path) == null) cfg.set<Any?>(path, default)
            cfg.setComment(path, comment)
        }
    }

    class DoubleValue(
        path: String, default: Double, val min: Double, val max: Double, comment: String,
    ) : Entry<Double>(path, default, comment) {
        // TOML stores 5 as an integer and 5.0 as a double; accept either and coerce to range.
        override fun get(): Double = (raw() as? Number)?.toDouble()?.coerceIn(min, max) ?: default
        fun set(value: Double) = store(value.coerceIn(min, max))
    }

    class IntValue(
        path: String, default: Int, val min: Int, val max: Int, comment: String,
    ) : Entry<Int>(path, default, comment) {
        override fun get(): Int = (raw() as? Number)?.toInt()?.coerceIn(min, max) ?: default
        fun set(value: Int) = store(value.coerceIn(min, max))
    }

    class BooleanValue(
        path: String, default: Boolean, comment: String,
    ) : Entry<Boolean>(path, default, comment) {
        override fun get(): Boolean = (raw() as? Boolean) ?: default
        fun set(value: Boolean) = store(value)
    }

    /** A sound-event resource location like "minecraft:block.moss.step". */
    class SoundIdValue(
        path: String, default: String, comment: String,
    ) : Entry<String>(path, default, comment) {
        override fun get(): String = (raw() as? String) ?: default
        fun set(value: String) = store(value)
    }

    /**
     * Create/open `config/arachnomod-common.toml`, fill in any missing defaults + comments, and
     * start watching it for live edits. Call once from the mod initializer.
     */
    fun init(configDir: Path) {
        Files.createDirectories(configDir)
        val file = configDir.resolve("arachnomod-common.toml")
        // autoreload() attaches a file watcher, which needs the file to already exist.
        if (Files.notExists(file)) Files.createFile(file)

        val cfg = CommentedFileConfig.builder(file)
            .preserveInsertionOrder()  // keep the tidy section ordering below
            .sync()        // thread-safe: the watcher reloads off-thread while the server reads it
            .autosave()    // .set(...) writes straight back to disk (used by /spider config)
            .autoreload()  // edits to the file on disk are picked up live
            .build()
        cfg.load()
        for (e in entries) e.writeDefaultAndComment(cfg)
        cfg.save()
        backing = cfg
    }

    // ---- Spawning --------------------------------------------------------------------------
    val SPAWN_MIN_MINUTES = DoubleValue("spawnMinMinutes", 5.0, 0.05, 1440.0,
        comment("Minimum minutes before a spider spawns (after world load or the last one's death)."))
    val SPAWN_MAX_MINUTES = DoubleValue("spawnMaxMinutes", 30.0, 0.05, 1440.0,
        comment("Maximum minutes before a spider spawns. The actual delay is random between min and max."))
    val SPAWN_DISTANCE_MIN = DoubleValue("spawnDistanceMin", 30.0, 4.0, 128.0,
        comment("Closest distance (blocks) from a player that a spider may naturally spawn."))
    val SPAWN_DISTANCE_MAX = DoubleValue("spawnDistanceMax", 34.0, 4.0, 128.0,
        comment("Farthest distance (blocks) from a player that a spider may naturally spawn."))
    val SPAWN_ANGLE_ATTEMPTS = IntValue("spawnAngleAttempts", 12, 4, 64,
        comment("How many directions around the player are tested at each candidate distance when",
                "looking for safe ground. Higher = more thorough (and slightly more work per attempt)."))
    val SPAWN_MAX_VERTICAL_SEARCH = IntValue("spawnMaxVerticalSearch", 48, 4, 384,
        comment("How many blocks downward from the surface a candidate column is scanned looking for",
                "solid, dry ground before that candidate is rejected. Keep this generous for SkyBlock",
                "and OneBlock style maps where solid ground can be far below the heightmap hit."))
    val CAMO_VARIANT_CHANCE = DoubleValue("camoVariantChance", 0.25, 0.0, 1.0,
        comment("Chance (0.0-1.0) that a naturally-spawned spider is the mossy CAMO variant instead",
                "of the netherite one. There is still only ever ONE spider in the world at a time."))

    // ---- Chase & speed ---------------------------------------------------------------------
    val CHASE_DISTANCE = DoubleValue("chaseDistance", 64.0, 8.0, 256.0,
        comment("How far away (blocks) the spider spots and chases players.",
                "Also settable in-game with /spider config chaseDistance set <blocks>."))
    val CHASE_EXIT_MULTIPLIER = DoubleValue("chaseExitDistanceMultiplier", 1.25, 1.0, 3.0,
        comment("Once chasing, the spider keeps chasing until the player is this many times",
                "chaseDistance away, instead of letting go right at the edge of its detection range.",
                "Prevents flickering between chase and wander when a player paces the boundary."))
    val ALERT_REACTION_TICKS = IntValue("alertReactionTicks", 10, 0, 200,
        comment("When a wandering spider first spots a player, it freezes and snaps to face them for",
                "this many ticks (20 = 1 second) before charging - the 'it just noticed you' beat.",
                "Set to 0 to disable and chase immediately."))
    val HOSTILE_ONLY_AT_NIGHT = BooleanValue("hostileOnlyAtNight", false,
        comment("If true, the spider only hunts (and bites) players at night, like a vanilla spider.",
                "During the day it just wanders."))
    val CHASE_SPEED = DoubleValue("chaseSpeedBlocksPerSecond", 8.0, 0.5, 40.0,
        comment("Top chase speed in blocks/second at NORMAL size (cruising speed is a bit lower).",
                "The spider moves faster than this as it grows - see speedGrowthFactor."))
    val SPEED_GROWTH_FACTOR = DoubleValue("speedGrowthFactor", 8.0, 1.0, 32.0,
        comment("Speed multiplier at maximum size: a huge, far-away spider charges at chase speed x this."))
    val LEG_STEP_SPEED = DoubleValue("legStepSpeed", 1.1, 0.1, 5.0,
        comment("How fast the legs swing when taking a step (blocks/tick at normal size) - the 'scurry'."))

    // ---- Wandering -------------------------------------------------------------------------
    val ENABLE_WANDERING = BooleanValue("enableWandering", true,
        comment("If false, the spider stands still (pre-1.1 behaviour) until it spots a player."))
    val WANDER_SPEED_FACTOR = DoubleValue("wanderSpeedFactor", 0.35, 0.05, 1.0,
        comment("Wander/patrol speed as a fraction of chase speed (0.35 = 35%). Keep this well below",
                "1.0 so the sudden jump to full chase speed actually feels sudden."))
    val WANDER_RADIUS = DoubleValue("wanderRadius", 24.0, 4.0, 128.0,
        comment("Max distance (blocks) the spider patrols from the spot it started wandering at."))
    val WANDER_MIN_INTERVAL_SECONDS = DoubleValue("wanderMinIntervalSeconds", 3.0, 0.5, 120.0,
        comment("Shortest time the spider commits to a patrol heading before picking a new one."))
    val WANDER_MAX_INTERVAL_SECONDS = DoubleValue("wanderMaxIntervalSeconds", 9.0, 0.5, 300.0,
        comment("Longest time the spider commits to a patrol heading before picking a new one."))
    val WANDER_PAUSE_CHANCE = DoubleValue("wanderPauseChance", 0.25, 0.0, 1.0,
        comment("Chance (0.0-1.0) that, instead of walking somewhere new, the spider just pauses a beat."))

    // ---- Size & growth ---------------------------------------------------------------------
    val MIN_SIZE = DoubleValue("minSize", 0.6, 0.1, 10.0,
        comment("The spider's size when right next to a player (1.0 = the original spider's size)."))
    val MAX_SIZE = DoubleValue("maxSize", 15.0, 0.5, 50.0,
        comment("The spider's size when far away. 15 towers over the trees (~16-block body)."))
    val SIZE_NEAR_DISTANCE = DoubleValue("sizeNearDistance", 4.0, 0.0, 64.0,
        comment("At/below this distance (blocks) from the nearest player the spider is at minSize."))
    val SIZE_FAR_DISTANCE = DoubleValue("sizeFarDistance", 32.0, 1.0, 128.0,
        comment("At/above this distance (blocks) the spider is at maxSize."))
    val GROW_PERCENT_PER_TICK = DoubleValue("growPercentPerTick", 12.0, 0.5, 100.0,
        comment("Fastest the spider can GROW, in percent per tick (12 = full grow in ~1.5s)."))
    val SHRINK_PERCENT_PER_TICK = DoubleValue("shrinkPercentPerTick", 25.0, 0.5, 100.0,
        comment("Fastest the spider can SHRINK, in percent per tick (25 = full shrink in ~0.7s).",
                "Kept faster than growing so it melts down promptly as it reaches you."))
    val RIDDEN_SIZE = DoubleValue("riddenSize", 2.0, 0.3, 20.0,
        comment("The stable size the spider settles to while a player is riding it."))

    // ---- Combat & drops --------------------------------------------------------------------
    val MAX_HEALTH = DoubleValue("maxHealth", 1000.0, 1.0, 1000000.0,
        comment("The spider's maximum health (1000 = a boss-grade fight)."))
    val ATTACK_DAMAGE_HEARTS = DoubleValue("attackDamageHearts", 6.0, 0.0, 100.0,
        comment("Melee damage in HEARTS per hit."))
    val ATTACK_COOLDOWN_TICKS = IntValue("attackCooldownTicks", 20, 1, 400,
        comment("Ticks between melee hits (20 = one hit per second)."))
    val NETHERITE_DROP_CHANCE = DoubleValue("netheriteDropChance", 0.5, 0.0, 1.0,
        comment("Chance (0.0-1.0) to drop a single netherite ingot on death. 0.5 = half the time."))

    // ---- Variant sounds --------------------------------------------------------------------
    // The default (netherite) spider ALWAYS uses its iconic netherite step/fall sounds. The CAMO
    // variant automatically plays the step/fall sound OF THE BLOCK it walks on (like a player's
    // footsteps) - these entries are its fallback (foot over air) and the sound set for any
    // other/future variants. Any built-in sound id works, with tab-completion in
    // /spider config variantStepSound set <id>.
    val VARIANT_STEP_SOUND = SoundIdValue("variantStepSound", "minecraft:block.moss.step",
        comment("Fallback/other-variant step sound (camo normally plays the walked-on block's own",
                "step sound). Any built-in sound id, e.g. minecraft:block.amethyst_block.chime."))
    val VARIANT_STEP_VOLUME = DoubleValue("variantStepVolume", 0.3, 0.0, 10.0,
        comment("Volume of variant step sounds (scales camo's block-matched steps too)."))
    val VARIANT_LAND_SOUND = SoundIdValue("variantLandSound", "minecraft:block.moss.fall",
        comment("Fallback/other-variant landing sound (camo normally plays the block's own fall sound)."))
    val VARIANT_LAND_VOLUME = DoubleValue("variantLandVolume", 1.0, 0.0, 10.0,
        comment("Volume of variant landing sounds (scales camo's block-matched landings too)."))
}
