package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.spider.DirectionBehaviour
import com.heledron.spideranimation.spider.SpiderBehaviour
import com.heledron.spideranimation.spider.SpiderBody
import com.heledron.spideranimation.spider.StayStillBehaviour
import com.heledron.spideranimation.spider.TargetBehaviour
import net.minecraft.server.level.ServerPlayer
import org.joml.Vector3d
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
                    tickChase(entity, body, nearestPlayer)
                }
            }
        }
    }

    /** True while this spider is actively hunting (used by SpiderMob to gate the melee bite). */
    fun isChasing(entity: EcsEntity): Boolean = states[entity]?.mode == SpiderMode.CHASE

    // ---------------------------------------------------------------- mode transitions

    private fun enterWander(state: SpiderAIState, body: SpiderBody) {
        state.mode = SpiderMode.WANDER
        state.modeTimer = 0
        state.anchor = Vector3d(body.position)
    }

    private fun enterAlert(state: SpiderAIState) {
        state.mode = SpiderMode.ALERT
        state.modeTimer = Config.ALERT_REACTION_TICKS.get()
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

    private fun tickChase(entity: EcsEntity, body: SpiderBody, player: ServerPlayer) {
        body.setSpeedScale(chaseSpeedFactor(body))
        // Stop distance must be CLAMPED: bodyHeight scales with size, and a size-15 spider's
        // bodyHeight*2 is ~33 blocks - it would consider itself "arrived" while still far away.
        val arriveDistance = (body.walkGait.stationary.bodyHeight * 2.0).coerceAtMost(4.0)
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
            val safeY = SafeGroundFinder.findSafeY(level, x, z) ?: return@repeat
            return Vector3d(x, safeY, z)
        }
        return null
    }

    private fun wanderSpeedFactor(body: SpiderBody): Double =
        SpiderMob.scaleToSpeedFactor(body.sizeScale) * Config.WANDER_SPEED_FACTOR.get()

    private fun chaseSpeedFactor(body: SpiderBody): Double =
        SpiderMob.scaleToSpeedFactor(body.sizeScale)
}
