package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.spider.DirectionBehaviour
import com.heledron.spideranimation.spider.SpiderBehaviour
import com.heledron.spideranimation.spider.SpiderBody
import com.heledron.spideranimation.spider.StayStillBehaviour
import com.heledron.spideranimation.spider.TargetBehaviour
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class SpiderMode { WANDER, ALERT, CHASE }

/**
 * Per-spider AI memory: current mode, how long it's been in it, and the patrol anchor/target.
 * Kept as a side table (same pattern AppState already uses for its `instances` map of personal
 * spiders) rather than a real ECS component, since nothing else needs to query it.
 */
class SpiderAIState(var anchor: Vector3d) {
    var mode: SpiderMode = SpiderMode.WANDER
    var modeTimer: Int = 0
    var squeezing: Boolean = false   // pressing over a dug-in player: shrink to fit their hole
}

/**
 * Replaces the old "chase if a player is in range, otherwise freeze in place" logic with a
 * three-state loop (contributed by the community Fabric patch; Yarn -> Mojmap):
 *
 *  WANDER - patrol randomly around wherever it started wandering, at a slow, calm speed.
 *           Patrol points are vetted by [SafeGroundFinder], so it never strolls into water/void.
 *  ALERT  - the instant a player enters chaseDistance, freeze and snap to face them for
 *           alertReactionTicks. This is the cinematic "it just spotted you" beat.
 *  CHASE  - charge at full chase speed until the player escapes past chaseDistance *
 *           chaseExitDistanceMultiplier (a wider exit radius than entry, so the behaviour and
 *           speed don't flicker back and forth for players pacing the boundary - this hysteresis
 *           is what fixed the gait-swap glitching at the chase edge).
 *
 * With hostileOnlyAtNight enabled, daytime players are simply not "seen": the spider wanders.
 *
 * SpiderAI is the ONLY driver of wild-spider movement and speed scale; it must be ticked from
 * exactly one place (AppState's driveWildSpiders) or mode timers would run double-speed.
 */
object SpiderAI {
    private val states = HashMap<EcsEntity, SpiderAIState>()

    fun forget(entity: EcsEntity) {
        states.remove(entity)
    }

    fun reset() {
        states.clear()
    }

    fun update(entity: EcsEntity, body: SpiderBody, chaseRadius: Double) {
        val level = body.level
        val state = states.getOrPut(entity) { SpiderAIState(Vector3d(body.position)) }

        // Squeeze descent target is re-asserted by tickChase every tick it applies; clearing it
        // up front means wander/alert (and a chase that stopped squeezing) can never leave a
        // stale "sink into the ground" order on the body.
        body.squeezeTargetY = null

        var nearestPlayer = level.players().minByOrNull {
            it.distanceToSqr(body.position.x, body.position.y, body.position.z)
        }

        // "Vanilla spider" mode: players are invisible to it during the day.
        if (Config.HOSTILE_ONLY_AT_NIGHT.get() && level.isDay) nearestPlayer = null

        val distanceToPlayer = nearestPlayer?.let {
            sqrt(it.distanceToSqr(body.position.x, body.position.y, body.position.z))
        } ?: Double.MAX_VALUE

        val exitRadius = chaseRadius * Config.CHASE_EXIT_MULTIPLIER.get()

        when (state.mode) {
            SpiderMode.WANDER -> {
                if (nearestPlayer != null && distanceToPlayer <= chaseRadius) {
                    enterAlert(state)
                    tickAlert(entity, body, state, nearestPlayer)
                } else {
                    tickWander(entity, body, state)
                }
            }
            SpiderMode.ALERT -> {
                if (nearestPlayer == null || distanceToPlayer > exitRadius) {
                    enterWander(state, body)
                    tickWander(entity, body, state)
                } else {
                    tickAlert(entity, body, state, nearestPlayer)
                }
            }
            SpiderMode.CHASE -> {
                if (nearestPlayer == null || distanceToPlayer > exitRadius) {
                    enterWander(state, body)
                    tickWander(entity, body, state)
                } else {
                    tickChase(entity, body, state, nearestPlayer)
                }
            }
        }
    }

    /** True while this spider is actively hunting (used by SpiderMob to gate the melee bite). */
    fun isChasing(entity: EcsEntity): Boolean = states[entity]?.mode == SpiderMode.CHASE

    /** True while the spider is pressing over a dug-in player and should SQUEEZE down to
     *  Config.SQUEEZE_SIZE to fit into their hole (SpiderMob drives the actual scale). */
    fun isSqueezing(entity: EcsEntity): Boolean = states[entity]?.squeezing == true

    // ---------------------------------------------------------------- mode transitions

    private fun enterWander(state: SpiderAIState, body: SpiderBody) {
        state.mode = SpiderMode.WANDER
        state.modeTimer = 0
        state.squeezing = false
        state.anchor = Vector3d(body.position)
    }

    private fun enterAlert(state: SpiderAIState) {
        state.mode = SpiderMode.ALERT
        state.modeTimer = Config.ALERT_REACTION_TICKS.get()
        state.squeezing = false
    }

    private fun enterChase(state: SpiderAIState) {
        state.mode = SpiderMode.CHASE
        state.modeTimer = 0
    }

    // ---------------------------------------------------------------- per-mode behaviour

    private fun tickWander(entity: EcsEntity, body: SpiderBody, state: SpiderAIState) {
        if (!Config.ENABLE_WANDERING.get()) {
            entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
            return
        }

        body.setSpeedScale(wanderSpeedFactor(body))

        // Commit to the current heading/pause until the timer runs out.
        if (state.modeTimer > 0) {
            state.modeTimer--
            return
        }

        if (Random.nextDouble() < Config.WANDER_PAUSE_CHANCE.get()) {
            entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
        } else {
            val point = pickWanderPoint(state.anchor, body)
            if (point != null) {
                entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(point, 1.0))
            } else {
                entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
            }
        }

        val minTicks = (Config.WANDER_MIN_INTERVAL_SECONDS.get() * 20.0).toInt().coerceAtLeast(1)
        val maxTicks = (Config.WANDER_MAX_INTERVAL_SECONDS.get() * 20.0).toInt().coerceAtLeast(minTicks)
        state.modeTimer = minTicks + Random.nextInt(maxTicks - minTicks + 1)
    }

    private fun tickAlert(entity: EcsEntity, body: SpiderBody, state: SpiderAIState, player: ServerPlayer) {
        // Face the player without moving: DirectionBehaviour(facing, walkDirection = zero).
        val direction = Vector3d(player.x, player.y, player.z).sub(body.position)
        if (direction.lengthSquared() > 1.0e-6) direction.normalize()
        entity.replaceComponent<SpiderBehaviour>(DirectionBehaviour(direction, Vector3d(0.0, 0.0, 0.0)))
        body.setSpeedScale(wanderSpeedFactor(body))

        if (state.modeTimer <= 0) enterChase(state) else state.modeTimer--
    }

    private fun tickChase(entity: EcsEntity, body: SpiderBody, state: SpiderAIState, player: ServerPlayer) {
        body.setSpeedScale(chaseSpeedFactor(body))
        // Stop distance must be CLAMPED: bodyHeight scales with size, and a size-15 spider's
        // bodyHeight*2 is ~33 blocks - it would consider itself "arrived" while still far away.
        //
        // DUG-IN PRESSURE: arrival is measured HORIZONTALLY, so a player who digs a pit under
        // the spider (or pillars up) reads as "arrived" - the spider used to stand at the rim
        // watching forever, even after the player opened a walkable path. When the player is
        // vertically separated from the spider's ground plane, the stop distance collapses to
        // ~0 instead: constant pressure toward their exact spot. Because this target refreshes
        // EVERY tick, the moment the surroundings change (a block broken, a ramp dug) the
        // pressure carries the spider straight through the new opening - it re-evaluates the
        // path continuously and punishes the player's first mistake.
        val groundLevelY = body.position.y - body.walkGait.stationary.bodyHeight
        val verticalGap = abs(groundLevelY - player.y)
        val pressureMode = verticalGap > 2.0
        val arriveDistance =
            if (pressureMode) 0.25
            else (body.walkGait.stationary.bodyHeight * 2.0).coerceAtMost(4.0)

        // THE SQUEEZE: pressing directly over a hidden player, the spider shrinks to
        // Config.SQUEEZE_SIZE - small enough to slip into a 1x1x1 hole - and comes in after
        // them. Only when horizontally on top of the target AND the player is genuinely BELOW
        // (shrinking at the base of a pillared-up player would only shorten the bite reach);
        // while still closing in it keeps its distance-based size. SpiderMob reads this flag
        // and drives the actual scale, and regrows the moment the squeeze ends.
        val dx = player.x - body.position.x
        val dz = player.z - body.position.z
        val playerBelow = groundLevelY - player.y > 2.0
        state.squeezing = playerBelow && (dx * dx + dz * dz) < 6.0 * 6.0

        // Drive the descent once the body has ACTUALLY shrunk to (near) squeeze size — gating
        // on the real sizeScale, not the flag, so a still-big spider never rams its bulk down a
        // hole it doesn't fit yet: the shrink runs first (~5 ticks at the shrink cap), then the
        // body pours in. SpiderBody.calcPreferredY does the rest.
        body.squeezeTargetY =
            if (state.squeezing && body.sizeScale <= Config.SQUEEZE_SIZE.get() * 1.25) player.y
            else null

        entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(Vector3d(player.x, player.y, player.z), arriveDistance))
    }

    // ---------------------------------------------------------------- helpers

    private fun pickWanderPoint(anchor: Vector3d, body: SpiderBody): Vector3d? {
        val radius = Config.WANDER_RADIUS.get()
        val level = body.level
        repeat(8) {
            val angle = Random.nextDouble() * 2.0 * Math.PI
            val dist = Random.nextDouble() * radius
            val x = anchor.x + cos(angle) * dist
            val z = anchor.z + sin(angle) * dist
            val safeY = SafeGroundFinder.groundYAt(level, x, z, refY = anchor.y) ?: return@repeat
            val target = Vector3d(x, safeY, z)
            // Route pre-scan: the destination being safe isn't enough — the WAY there must be too.
            // A comfortable step-down scales with the spider (a giant strides off ledges a small
            // one would tumble from); climbs UP are never rejected — climbing is what spiders do.
            val maxDrop = (body.walkGait.stationary.bodyHeight * 1.5).coerceIn(3.0, 12.0)
            if (!isPathSafe(body.position, target, level, maxDrop)) return@repeat
            return target
        }
        return null
    }

    /**
     * Route pre-scan (contributed by NetherySiloX): before committing to a wander target, walk
     * the straight line to it in ~1-block steps and require safe ground at every step — columns
     * of lava, water, or open void reject the route. Additionally (the cliff-edge guard), if the
     * ground level between consecutive steps falls away by more than [maxDrop], the route crosses
     * a chasm lip and is rejected too. Greatly reduces wander falls.
     */
    private fun isPathSafe(start: Vector3d, end: Vector3d, level: ServerLevel, maxDrop: Double): Boolean {
        val dx = end.x - start.x
        val dz = end.z - start.z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < 1.0) return true

        val steps = ceil(distance).toInt()
        val stepX = dx / steps
        val stepZ = dz / steps
        var x = start.x
        var z = start.z
        // Dimension-aware ground sampling: each step's reference altitude is the previous step's
        // ground (seeded from the walk start), so the scan follows the terrain — and works under
        // the Nether roof, where heightmap-based sampling would see only bedrock.
        var prevGroundY = SafeGroundFinder.groundYAt(level, start.x, start.z, refY = start.y)
        repeat(steps) {
            x += stepX
            z += stepZ
            val groundY = SafeGroundFinder.groundYAt(level, x, z, refY = prevGroundY ?: start.y) ?: return false
            val last = prevGroundY
            if (last != null && last - groundY > maxDrop) return false   // cliff edge ahead
            prevGroundY = groundY
        }
        return true
    }

    private fun wanderSpeedFactor(body: SpiderBody): Double =
        SpiderMob.scaleToSpeedFactor(body.sizeScale) * Config.WANDER_SPEED_FACTOR.get()

    private fun chaseSpeedFactor(body: SpiderBody): Double =
        SpiderMob.scaleToSpeedFactor(body.sizeScale)
}
