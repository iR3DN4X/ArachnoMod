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

    // ---- One-time migration of stale defaults --------------------------------------------
    // Defaults only apply to NEWLY generated files: without this, everyone updating from an
    // older version silently keeps the old pacing (5-30 min first spawn) and the weaker spawn
    // search — "the spider never shows up". Each migration upgrades a value ONLY if it still
    // equals its OLD default, so deliberate customization is never overwritten.
    private const val CONFIG_VERSION = 5
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

    private fun migrate(cfg: CommentedFileConfig) {
        val hasContent = cfg.get<Any?>("spawnMinMinutes") != null
        val fileVersion = (cfg.get<Any?>("configVersion") as? Number)?.toInt()
            ?: if (hasContent) 1 else CONFIG_VERSION   // content but no version = a pre-1.1.6 file
        for ((sinceVersion, batch) in MIGRATIONS) {
            if (fileVersion >= sinceVersion) continue
            for ((path, old, new) in batch) {
                val current = cfg.get<Any?>(path)
                val stillAtOldDefault = when (old) {
                    is Double -> (current as? Number)?.toDouble() == old
                    is Int -> (current as? Number)?.toInt() == old
                    else -> current == old
                }
                if (stillAtOldDefault) cfg.set<Any?>(path, new)
            }
        }
        // v4 (mod v1.2.7): maxHealth split into per-variant netheriteMaxHealth/camoMaxHealth.
        // A CUSTOMIZED old value carries into both new keys; a default value (600, or a
        // pre-v3 1000 the batch above just normalized to 600) is dropped so the new
        // per-variant defaults apply.
        if (fileVersion < 4) {
            val old = (cfg.get<Any?>("maxHealth") as? Number)?.toDouble()
            if (old != null && old != 600.0) {
                cfg.set<Any?>("netheriteMaxHealth", old)
                cfg.set<Any?>("camoMaxHealth", old)
            }
            cfg.remove<Any?>("maxHealth")
        }
        // v5 (mod v1.8.0): attackDamageHearts split per-variant (netherite 6 / camo 5 /
        // hunter 4). A CUSTOMIZED old value carries into all three; the old 6.0 default is
        // dropped so the new per-variant defaults apply.
        if (fileVersion < 5) {
            val old = (cfg.get<Any?>("attackDamageHearts") as? Number)?.toDouble()
            if (old != null && old != 6.0) {
                cfg.set<Any?>("netheriteAttackDamageHearts", old)
                cfg.set<Any?>("camoAttackDamageHearts", old)
                cfg.set<Any?>("hunterAttackDamageHearts", old)
            }
            cfg.remove<Any?>("attackDamageHearts")
        }
        cfg.set<Any?>("configVersion", CONFIG_VERSION)
        cfg.setComment("configVersion", " Internal config-format version - do not edit.")
    }

    /**
     * Create/open `config/arachnomod-common.toml`, migrate stale defaults, fill in any missing
     * defaults + comments, and start watching it for live edits. Call once from the initializer.
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
        migrate(cfg)
        for (e in entries) e.writeDefaultAndComment(cfg)
        cfg.save()
        backing = cfg
    }

    // ---- Spawning --------------------------------------------------------------------------
    val SPAWN_MIN_MINUTES = DoubleValue("spawnMinMinutes", 1.0, 0.05, 1440.0,
        comment("Minimum minutes before the FIRST spider of a session spawns (default 1 - the hunt begins fast)."))
    val SPAWN_MAX_MINUTES = DoubleValue("spawnMaxMinutes", 1.0, 0.05, 1440.0,
        comment("Maximum minutes for the FIRST spawn (random between min and max; killed spiders use respawnAfterKillMinutes)."))
    val PEACEFUL_EXIT_SPAWN_MINUTES = DoubleValue("peacefulExitSpawnMinutes", 1.0, 0.05, 1440.0,
        comment("Minutes until the spider spawns after Peaceful difficulty is switched OFF.",
                "Only applies when Peaceful itself removed the spider - a killed spider keeps its",
                "respawnAfterKillMinutes cooldown (toggling Peaceful can't shortcut it)."))
    val RESPAWN_AFTER_KILL_MINUTES = DoubleValue("respawnAfterKillMinutes", 40.0, 0.05, 1440.0,
        comment("Minutes until the next spider after one is KILLED (40 = 2 Minecraft days).",
                "Slaying it buys real peace; spawnMin/spawnMax only govern the FIRST spawn."))
    val PERMADEATH = BooleanValue("permadeath", false,
        comment("BOSS MODE: if true, the spider NEVER comes back once it has been killed in this world.",
                "One kill ends the hunt for good - the victory is written into the world save, so it",
                "survives restarts. Spawn eggs and /spider newinstance still work if you want one back.",
                "Default false: it returns after respawnAfterKillMinutes."))
    val SPAWN_DISTANCE_MIN = DoubleValue("spawnDistanceMin", 30.0, 4.0, 128.0,
        comment("Closest distance (blocks) from a player that a spider may naturally spawn."))
    val SPAWN_DISTANCE_MAX = DoubleValue("spawnDistanceMax", 34.0, 4.0, 128.0,
        comment("Farthest distance (blocks) from a player that a spider may naturally spawn."))
    val SPAWN_ANGLE_ATTEMPTS = IntValue("spawnAngleAttempts", 24, 4, 64,
        comment("How many directions around the player are tested at each candidate distance when",
                "looking for safe ground. If the spider fails to spawn in rough terrain (dense",
                "forest, snowy peaks, cliffs), RAISE this - more directions = more spawn spots found."))
    val SPAWN_MAX_VERTICAL_SEARCH = IntValue("spawnMaxVerticalSearch", 48, 4, 384,
        comment("How many blocks downward from the surface a candidate column is scanned looking for",
                "solid, dry ground before that candidate is rejected. Keep this generous for SkyBlock",
                "and OneBlock style maps where solid ground can be far below the heightmap hit."))
    val CAMO_VARIANT_CHANCE = DoubleValue("camoVariantChance", 0.25, 0.0, 1.0,
        comment("Chance (0.0-1.0) that a naturally-spawned spider is the mossy CAMO variant instead",
                "of the netherite one. There is still only ever ONE spider in the world at a time."))
    val POISON_VARIANT_CHANCE = DoubleValue("poisonVariantChance", 0.2, 0.0, 1.0,
        comment("Chance (0.0-1.0) that a spawned spider is the venomous POISON variant (rolled BEFORE",
                "the camo chance; applies to natural spawns and spawn eggs alike)."))
    val HUNTER_VARIANT_CHANCE = DoubleValue("hunterVariantChance", 0.15, 0.0, 1.0,
        comment("Chance (0.0-1.0) that a spawned spider is the pitch-black HUNTER variant (rolled",
                "FIRST, before poison and camo; natural spawns and spawn eggs alike)."))

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
    val CHASE_PATHFINDING = BooleanValue("chasePathfinding", true,
        comment("If true, a chasing spider STEERS around walls and cliffs it cannot climb instead of",
                "walking into them (the old 'stuck in the wall, rides up it' behaviour), and SHRINKS",
                "to slip through doorways (1x2) and crawl-holes (1x1) that lie on its path to you.",
                "Set false for the old straight-line charge."))
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
    val GROOMING_CHANCE = DoubleValue("groomingChance", 0.03, 0.0, 1.0,
        comment("Only while wandering is DISABLED: chance PER SECOND that the idle spider grooms -",
                "lifting its front legs to its mouth and cleaning them (0.03 = 3%/sec). 0 turns it off.",
                "The idle spider also breathes (a gentle body bob) whenever wandering is disabled."))

    // ---- Size & growth ---------------------------------------------------------------------
    val MIN_SIZE = DoubleValue("minSize", 0.6, 0.1, 10.0,
        comment("The spider's size when right next to a player (1.0 = the original spider's size)."))
    val SQUEEZE_SIZE = DoubleValue("squeezeSize", 0.25, 0.1, 1.0,
        comment("When a hiding player is vertically out of reach and the spider is right on top of",
                "them, it SQUEEZES below minSize down to this - 0.25 fits a 1x1x1 hole, just barely -",
                "to come in after them. It regrows the moment the squeeze is over. No hole is safe."))
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
    val GROW_IN_WATER = BooleanValue("growInWater", true,
        comment("If true, a spider standing in water GROWS just big enough for its body to ride",
                "above the surface - whatever the depth - so it never drowns and keeps chasing",
                "swimmers. Set to false to keep water as a weakness: a small spider lured into",
                "deep water will stay small and drown."))

    // ---- Combat & drops --------------------------------------------------------------------
    val NETHERITE_MAX_HEALTH = DoubleValue("netheriteMaxHealth", 350.0, 1.0, 1000000.0,
        comment("Max health of the NETHERITE variant (default 350). It also wears the stats of a full",
                "netherite armor suit, so it soaks far more weapon damage than the raw number suggests."))
    val NETHERITE_ARMOR = DoubleValue("netheriteArmor", 20.0, 0.0, 30.0,
        comment("The NETHERITE variant's armor points (default 20 = a full netherite suit; vanilla",
                "caps player armor at 20 too). SET TO 0 FOR NO ARMOR - it then takes weapon damage",
                "straight off its health pool, which makes it far quicker to kill. Armor only",
                "reduces damage coming IN; it has no effect on the spider's own bite (that is",
                "netheriteAttackDamageHearts). Applied when a spider SPAWNS - the change shows up",
                "on the next one, not the one already walking around."))
    val NETHERITE_ARMOR_TOUGHNESS = DoubleValue("netheriteArmorToughness", 12.0, 0.0, 20.0,
        comment("The NETHERITE variant's armor toughness (default 12 = a full netherite suit).",
                "Toughness is what stops heavy hits punching straight through armor. Set this and",
                "netheriteArmor to 0 to fight a bare 350 HP spider."))
    val NETHERITE_KNOCKBACK_RESISTANCE = DoubleValue("netheriteKnockbackResistance", 0.4, 0.0, 1.0,
        comment("The NETHERITE variant's knockback resistance, 0-1 (default 0.4 - a netherite suit",
                "is 0.1 per piece). Mostly cosmetic while the simulation owns the body's position,",
                "but it keeps the suit honest."))
    val NETHERITE_LEGS = IntValue("netheriteLegs", 8, 1, 16,
        comment("How many legs the NETHERITE spider has (default 8 - the classic octopod). The",
                "original mod shipped 2/4/6/8-legged bodies; any count from 1 to 16 works here and",
                "the body shape is interpolated to match, so 12 or 16 reads as a centipede and 4",
                "as a crab. ONLY the netherite variant - camo, poison and hunter are always 8.",
                "BELOW 4 IS EXPERIMENTAL: the walk is a diagonal gait built out of opposing pairs,",
                "so 3 limps, and 1-2 cannot really walk at all - the body just drags itself along",
                "on whatever it can plant. Odd counts add one extra leg on the centre line.",
                "Applied when a spider SPAWNS, so it takes effect on the next one."))
    val CAMO_MAX_HEALTH = DoubleValue("camoMaxHealth", 600.0, 1.0, 1000000.0,
        comment("Max health of the CAMO variant (default 600). No armor - easier to put down, if you",
                "can find it."))
    val POISON_MAX_HEALTH = DoubleValue("poisonMaxHealth", 500.0, 1.0, 1000000.0,
        comment("Max health of the POISON variant (default 500). No armor - its danger is the bite."))
    val POISON_SIZE = DoubleValue("poisonSize", 1.0, 0.3, 5.0,
        comment("The POISON variant's FIXED size - it never grows or shrinks with distance (default",
                "1.0: roughly player height, about 2 blocks tall). A tarantula is an ambusher, not a",
                "siege engine - it stays low and the same size however far away you are. This also",
                "keeps it from bobbing up and down as the distance-based sizing chases you. It can",
                "still size down situationally to thread a doorway or drop into a squeeze hole."))
    val HUNTER_MAX_HEALTH = DoubleValue("hunterMaxHealth", 400.0, 1.0, 1000000.0,
        comment("Max health of the HUNTER variant (default 400). No armor - it was never supposed to",
                "be seen at all."))
    val HUNTER_SIZE = DoubleValue("hunterSize", 1.1, 0.3, 5.0,
        comment("The HUNTER's FIXED size - it never grows or shrinks (default 1.1: a head taller than",
                "a player, and it slips through 1x2 doorways at that size without shrinking)."))
    val HUNTER_SPEED_MULTIPLIER = DoubleValue("hunterSpeedMultiplier", 1.6, 1.0, 8.0,
        comment("The HUNTER's speed (x base chase speed) while it moves - faster up close than any",
                "other variant. It only moves while nobody is looking at it."))
    val HUNTER_BLINDNESS_RANGE = DoubleValue("hunterBlindnessRange", 16.0, 0.0, 128.0,
        comment("How close (blocks) the stalking HUNTER has to be before it blinds you. While you stay",
                "inside this range it keeps the blindness topped up - the only way to clear it is to",
                "actually get away from it. Set to 0 to disable the blindness entirely."))
    val HUNTER_BLINDNESS_SECONDS = DoubleValue("hunterBlindnessSeconds", 30.0, 1.0, 3600.0,
        comment("How long (seconds) the HUNTER's blindness lasts once you are out of its range."))
    // Bite damage is per-variant. NOTE: these are RAW hearts before armour. Vanilla armour
    // reduction applies on top automatically (the bite is an ordinary mob attack), so a player
    // in full diamond takes roughly a quarter of these numbers - do not pre-reduce them here.
    val NETHERITE_ATTACK_DAMAGE_HEARTS = DoubleValue("netheriteAttackDamageHearts", 6.0, 0.0, 100.0,
        comment("The NETHERITE variant's bite damage in HEARTS, before the victim's armour."))
    val CAMO_ATTACK_DAMAGE_HEARTS = DoubleValue("camoAttackDamageHearts", 5.0, 0.0, 100.0,
        comment("The CAMO variant's bite damage in HEARTS, before the victim's armour."))
    val HUNTER_ATTACK_DAMAGE_HEARTS = DoubleValue("hunterAttackDamageHearts", 4.0, 0.0, 100.0,
        comment("The HUNTER variant's bite damage in HEARTS, before the victim's armour. It hits",
                "softest of all - it hunts by taking your sight and your nerve, not by brute force."))
    val POISON_ATTACK_DAMAGE_HEARTS = DoubleValue("poisonAttackDamageHearts", 3.0, 0.0, 100.0,
        comment("The POISON variant's bite damage in HEARTS - weaker than the other variants, but",
                "every bite that lands also injects Poison II (see poisonEffectSeconds)."))
    val POISON_EFFECT_SECONDS = DoubleValue("poisonEffectSeconds", 30.0, 0.0, 3600.0,
        comment("How long (seconds) the Poison II from the poison variant's bite lasts."))
    val ATTACK_COOLDOWN_TICKS = IntValue("attackCooldownTicks", 20, 1, 400,
        comment("Ticks between melee hits (20 = one hit per second)."))
    val NETHERITE_DROP_CHANCE = DoubleValue("netheriteDropChance", 0.5, 0.0, 1.0,
        comment("Chance (0.0-1.0) to drop a single netherite ingot on death. 0.5 = half the time."))
    val ENRAGED_MAX_HEALTH = DoubleValue("enragedMaxHealth", 700.0, 1.0, 1000000.0,
        comment("Max health of an ENRAGED netherite spider (right-click the netherite variant with a",
                "netherite ingot to enrage it into a boss; it keeps its full armor suit)."))
    val ENRAGED_SPEED_MULTIPLIER = DoubleValue("enragedSpeedMultiplier", 1.5, 1.0, 8.0,
        comment("Chase-speed multiplier while enraged."))
    val ENRAGED_ATTACK_DAMAGE_HEARTS = DoubleValue("enragedAttackDamageHearts", 10.0, 0.0, 100.0,
        comment("The enraged boss's bite damage in HEARTS. It always drops a FULL netherite block."))

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
