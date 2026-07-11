package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.SpiderAnimationMod
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

    /** Random respawn delay in ticks from the configured min/max minutes. */
    private fun rollRespawnDelay(): Int {
        val minTicks = (Config.SPAWN_MIN_MINUTES.get() * 1200.0).toInt().coerceAtLeast(1)
        val maxTicks = (Config.SPAWN_MAX_MINUTES.get() * 1200.0).toInt().coerceAtLeast(minTicks)
        return minTicks + Random.nextInt(maxTicks - minTicks + 1)
    }

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
    }

    fun tick(server: MinecraftServer) {
        // Peaceful: monsters don't exist. The mob itself despawns via the vanilla peaceful check;
        // here we pause natural spawning too, so it doesn't churn spawn/despawn cycles.
        if (server.worldData.difficulty == Difficulty.PEACEFUL) return

        val cur = current

        // A spider is loaded and alive: hold the timer, nothing to do.
        if (cur != null && !cur.isRemoved) {
            respawnTimer = -1
            return
        }

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
            // Killed / discarded: fall through to the normal 5–30 minute respawn timer.
            respawnTimer = -1
        }

        val players = server.playerList.players
        if (players.isEmpty()) return   // no one to spawn near; pause the timer

        if (respawnTimer < 0) {
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

    /** Spawn guard: refuse if any spider already exists (belt-and-suspenders for "never two"). */
    private fun spawnNear(server: MinecraftServer, player: ServerPlayer): Boolean {
        if (countSpiders(server) > 0) return false
        return trySpawnNear(player)
    }

    private fun countSpiders(server: MinecraftServer): Int {
        var count = 0
        for (level in server.allLevels) {
            count += level.getEntities(SpiderAnimationMod.SPIDER_ENTITY) { true }.size
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

    /** Test [angles] evenly-spread directions (random phase) at one distance; first safe one wins. */
    private fun findSafeSpot(level: ServerLevel, player: ServerPlayer, distance: Double, angles: Int): Triple<Double, Double, Double>? {
        val phase = Random.nextDouble() * 2.0 * PI
        repeat(angles) { i ->
            val angle = phase + (2.0 * PI / angles) * i
            val x = player.x + cos(angle) * distance
            val z = player.z + sin(angle) * distance
            val y = SafeGroundFinder.findSafeY(level, x, z)
            if (y != null) return Triple(x, y, z)
        }
        return null
    }

    private fun spawnAt(level: ServerLevel, x: Double, y: Double, z: Double): Boolean {
        val spider = SpiderAnimationMod.SPIDER_ENTITY.create(level) ?: return false
        // Roll the visual variant: mossy CAMO or classic netherite (still only ONE spider total).
        if (Random.nextDouble() < Config.CAMO_VARIANT_CHANCE.get()) {
            spider.variant = SpiderVariant.CAMO
        }
        spider.moveTo(x, y, z, (Random.nextDouble() * 360.0).toFloat(), 0f)
        if (!level.addFreshEntity(spider)) return false
        current = spider
        return true
    }
}
