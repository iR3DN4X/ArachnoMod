package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.SpiderAnimationMod
import com.heledron.spideranimation.platform.DISPLAY_TAG
import com.heledron.spideranimation.platform.DisplayTracker
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Keeps exactly ONE spider alive in the world at a time. When none exists it waits a random
 * 5–30 minutes, then spawns one near a random online player (who it then chases — see the
 * "chase nearest player" system in [com.heledron.spideranimation.AppState]).
 *
 * It tracks the single living spider so it can tell *why* it disappeared:
 *  - killed / discarded  -> wait out the normal 5–30 minute respawn timer,
 *  - unloaded (walked into an unloaded chunk) -> immediately drop a fresh one near the nearest
 *    player, so the spider never gets "lost" out in unloaded terrain.
 *
 * Because [SpiderMob] is never saved to disk, an unloaded spider is gone for good and can't reload
 * into a second copy — combined with [notifyAlive] enforcing a single instance, there are never two.
 *
 * Ticked once per server tick from [SpiderAnimationMod].
 */
object SpiderSpawnManager {
    // Spawn timing and distance come from config/arachnomod-common.toml (see Config).
    private const val RETRY_TICKS = 10 * 20             // re-try 10s later if a spawn attempt fails

    /** Random FIRST-spawn delay in ticks from the configured min/max minutes (world load only —
     *  respawns after a kill use the flat respawnAfterKillMinutes instead). */
    private fun rollRespawnDelay(): Int {
        val minTicks = (Config.SPAWN_MIN_MINUTES.get() * 1200.0).toInt().coerceAtLeast(1)
        val maxTicks = (Config.SPAWN_MAX_MINUTES.get() * 1200.0).toInt().coerceAtLeast(minTicks)
        return minTicks + Random.nextInt(maxTicks - minTicks + 1)
    }

    /** The long post-kill cooldown (default 40 min = 2 Minecraft days): slaying the spider buys
     *  real peace — it must never feel like a respawn treadmill. */
    private fun killRespawnTicks(): Int =
        (Config.RESPAWN_AFTER_KILL_MINUTES.get() * 1200.0).toInt().coerceAtLeast(1)

    private var current: SpiderMob? = null
    private var respawnTimer = -1   // ticks until next spawn; < 0 = not yet scheduled

    /**
     * Called by every [SpiderMob] on its first tick. Enforces the single-spider rule: if another one
     * is already alive (e.g. spawned from an egg) the older one is removed, so there are never two.
     */
    fun notifyAlive(spider: SpiderMob) {
        val existing = current
        if (existing != null && existing !== spider && !existing.isRemoved) existing.discard()
        current = spider
        respawnTimer = -1
    }

    /** Reset between worlds (the @Mod object is reused across single-player worlds in one session). */
    fun reset() {
        current = null
        respawnTimer = -1
        pendingPeacefulExit = false
        abandonedTicks = 0
        janitorTimer = 0
    }

    // Set while the difficulty is Peaceful: the moment peace ends, the hunt resumes on the SHORT
    // post-peaceful timer (peacefulExitSpawnMinutes, default 1 min) instead of the 5-30 min roll.
    private var pendingPeacefulExit = false

    // Dimension following: ticks the live spider has spent in a dimension with NO players in it.
    private var abandonedTicks = 0
    private const val FOLLOW_GRACE_TICKS = 100   // 5s grace so a quick portal peek doesn't yo-yo it

    fun tick(server: MinecraftServer) {
        janitorSweep(server)

        // Peaceful: monsters don't exist. The mob itself despawns via the vanilla peaceful check;
        // here we pause natural spawning too, so it doesn't churn spawn/despawn cycles.
        if (server.worldData.difficulty == Difficulty.PEACEFUL) {
            pendingPeacefulExit = true
            return
        }

        val cur = current

        // A spider is loaded and alive: hold the timer, nothing to do — unless every player has
        // left ITS dimension, in which case it FOLLOWS them (Nether, End, or any modded
        // dimension: this keys purely on where the players are, not on how they travelled).
        if (cur != null && !cur.isRemoved) {
            respawnTimer = -1
            pendingPeacefulExit = false   // it survived peaceful (e.g. was unloaded); nothing owed

            val hasCompany = cur.level().players().isNotEmpty()
            val anyPlayers = server.playerList.players.isNotEmpty()
            if (hasCompany || !anyPlayers) {
                abandonedTicks = 0
            } else if (++abandonedTicks >= FOLLOW_GRACE_TICKS) {
                abandonedTicks = 0
                followToPlayer(server, cur)
            }
            return
        }
        abandonedTicks = 0

        // The tracked spider left the world — figure out why.
        if (cur != null) {
            val reason = cur.removalReason
            current = null
            if (reason != null && !reason.shouldDestroy()) {
                // Unloaded into an unloaded chunk (not killed). It isn't saved, so it's gone for
                // good — immediately drop a fresh one near the nearest player.
                relocateNear(server, cur)
                return
            }
            // Killed / discarded: the trophy hunt is over — schedule the LONG post-kill cooldown
            // (default 40 min = 2 Minecraft days). Exception: if we were just in Peaceful, this
            // "discard" was the peaceful despawn, not a kill — leave it to the fast timer below.
            respawnTimer = if (pendingPeacefulExit) -1 else killRespawnTicks()
        }

        val players = server.playerList.players
        if (players.isEmpty()) return   // no one to spawn near; pause the timer

        // Peaceful was just switched off. Fast-track (default 1 minute) ONLY when nothing is
        // already scheduled — i.e. the spider vanished because of Peaceful itself. A spider
        // KILLED before peace keeps its long cooldown: toggling Peaceful must never shortcut it.
        if (pendingPeacefulExit) {
            pendingPeacefulExit = false
            if (respawnTimer < 0) {
                respawnTimer = (Config.PEACEFUL_EXIT_SPAWN_MINUTES.get() * 1200.0).toInt().coerceAtLeast(1)
                return
            }
        }

        if (respawnTimer < 0) {
            // No spider has existed yet this session (world load / reset): the FIRST-spawn roll.
            respawnTimer = rollRespawnDelay()
            return
        }
        if (--respawnTimer > 0) return

        respawnTimer = if (spawnNear(server, players.random())) -1 else RETRY_TICKS
    }

    private fun relocateNear(server: MinecraftServer, lastSpider: SpiderMob) {
        val players = server.playerList.players
        if (players.isEmpty()) { respawnTimer = RETRY_TICKS; return }
        val nearest = players.minByOrNull { it.distanceToSqr(lastSpider.x, lastSpider.y, lastSpider.z) } ?: return
        if (!spawnNear(server, nearest)) respawnTimer = RETRY_TICKS
    }

    /**
     * DIMENSION FOLLOWING: every player has left the spider's dimension, so it goes where the
     * hunt is. The old body is discarded ALIVE (no trophy, and — because `current` is nulled
     * first — no 40-minute kill cooldown either) and a fresh spider emerges near a player in
     * whatever dimension they travelled to, through the normal safe-spawn algorithm. Keying on
     * player location makes this work with ANY dimension: Nether, End, Twilight Forest,
     * Dimensional Doors, anything.
     */
    private fun followToPlayer(server: MinecraftServer, oldSpider: SpiderMob) {
        val players = server.playerList.players
        if (players.isEmpty()) return
        current = null            // detach BEFORE discarding so the removal isn't read as a kill
        oldSpider.discard()       // alive -> no trophy (see SpiderMob.remove)
        if (!trySpawnNear(players.random())) respawnTimer = RETRY_TICKS   // e.g. mid-lava-ocean: retry soon
    }

    // ---- Display janitor ------------------------------------------------------------------
    // Old versions let the BlockDisplay leg entities get SAVED into chunks when a player changed
    // dimension and the spider's chunks unloaded — reloading the area then showed frozen "corpse
    // pile" spiders. New displays are never saved (see Platform.spawnDisplayAtVisual), and this
    // sweep heals worlds infested by older versions: any display carrying our tag that is not in
    // the live tracker is a leftover, in ANY dimension, and gets discarded as its chunk loads.
    private var janitorTimer = 0

    private fun janitorSweep(server: MinecraftServer) {
        if (++janitorTimer < 200) return   // every 10s; the scan is cheap but no need to spam it
        janitorTimer = 0
        for (level in server.allLevels) {
            val orphans = level.getEntities(net.minecraft.world.entity.EntityType.BLOCK_DISPLAY) {
                it.tags.contains(DISPLAY_TAG) && !DisplayTracker.isTracked(it)
            }
            for (orphan in orphans) orphan.discard()
        }
    }

    /** Spawn guard: refuse if any spider already exists (belt-and-suspenders for "never two"). */
    private fun spawnNear(server: MinecraftServer, player: ServerPlayer): Boolean {
        if (countSpiders(server) > 0) return false
        return trySpawnNear(player)
    }

    private fun countSpiders(server: MinecraftServer): Int {
        var count = 0
        for (level in server.allLevels) {
            count += level.getEntities(SpiderAnimationMod.SPIDER_ENTITY.get()) { true }.size
        }
        return count
    }

    /**
     * SAFE SPAWNING. Picks a random distance in [spawnDistanceMin, spawnDistanceMax] exactly like
     * before, then hunts for a position that [SafeGroundFinder] confirms is a solid, dry, clear
     * surface — never open air, water, lava or the void.
     *
     * Search order (per the community-requested design):
     *  1. Several directions at the chosen distance itself.
     *  2. Rings stepping AWAY from the chosen distance within the configured band, ordered by how
     *     close they stay to the chosen distance, ties preferring the FARTHER ring — so the spider
     *     spawns as close as possible to the rolled distance, and when it must move, it prefers
     *     farther-from-the-player over closer (it should never spawn on top of you).
     *  3. Only if the whole configured band is unsafe (mid-ocean, a SkyBlock gap): expanding rings
     *     BEYOND spawnDistanceMax until safe ground is found or we give up and retry later.
     */
    private fun trySpawnNear(player: ServerPlayer): Boolean {
        // A ServerPlayer's level() is always a ServerLevel.
        val level = player.level() as ServerLevel

        val minDistance = Config.SPAWN_DISTANCE_MIN.get()
        val maxDistance = Config.SPAWN_DISTANCE_MAX.get().coerceAtLeast(minDistance)
        val chosen = minDistance + Random.nextDouble() * (maxDistance - minDistance)
        val angles = Config.SPAWN_ANGLE_ATTEMPTS.get()

        // Stage 1 + 2: candidate distances inside the band, nearest-to-chosen first, ties farther.
        val ringStep = 2.0
        val candidates = ArrayList<Double>()
        candidates.add(chosen)
        var offset = ringStep
        while (chosen + offset <= maxDistance || chosen - offset >= minDistance) {
            if (chosen + offset <= maxDistance) candidates.add(chosen + offset)   // farther first on ties
            if (chosen - offset >= minDistance) candidates.add(chosen - offset)
            offset += ringStep
        }
        // (Already ordered by |d - chosen| with farther-first ties, by construction.)
        for (distance in candidates) {
            val spot = findSafeSpot(level, player, distance, angles)
            if (spot != null) return spawnAt(level, spot.first, spot.second, spot.third)
        }

        // Stage 3: the whole band is unsafe. Widen outward past the band rather than spawning
        // closer than spawnDistanceMin — the spider must never pop up in your face.
        var ringDistance = maxDistance + ringStep
        repeat(16) {
            val spot = findSafeSpot(level, player, ringDistance, angles)
            if (spot != null) return spawnAt(level, spot.first, spot.second, spot.third)
            ringDistance += ringStep
        }

        return false   // truly nowhere safe right now; the manager retries in RETRY_TICKS
    }

    /** Test [angles] evenly-spread directions (random phase) at one distance; first safe one wins.
     *  Dimension-aware: in ceiling'd dimensions (Nether & modded roofed dims) ground is searched
     *  around the player's altitude instead of the (bedrock-roof) heightmap. */
    private fun findSafeSpot(level: ServerLevel, player: ServerPlayer, distance: Double, angles: Int): Triple<Double, Double, Double>? {
        val phase = Random.nextDouble() * 2.0 * PI
        repeat(angles) { i ->
            val angle = phase + (2.0 * PI / angles) * i
            val x = player.x + cos(angle) * distance
            val z = player.z + sin(angle) * distance
            val y = SafeGroundFinder.groundYAt(level, x, z, refY = player.y)
            if (y != null) return Triple(x, y, z)
        }
        return null
    }

    private fun spawnAt(level: ServerLevel, x: Double, y: Double, z: Double): Boolean {
        val spider = SpiderAnimationMod.SPIDER_ENTITY.get().create(level) ?: return false
        // Roll the variant: venomous POISON, mossy CAMO, or the classic armored netherite
        // (still only ONE spider in the world, whichever face it wears).
        if (Random.nextDouble() < Config.POISON_VARIANT_CHANCE.get()) {
            spider.variant = SpiderVariant.POISON
        } else if (Random.nextDouble() < Config.CAMO_VARIANT_CHANCE.get()) {
            spider.variant = SpiderVariant.CAMO
        }
        spider.moveTo(x, y, z, (Random.nextDouble() * 360.0).toFloat(), 0f)
        if (!level.addFreshEntity(spider)) return false
        current = spider
        return true
    }
}
