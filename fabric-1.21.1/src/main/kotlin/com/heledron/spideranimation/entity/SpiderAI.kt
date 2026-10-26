package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.spider.DirectionBehaviour
import com.heledron.spideranimation.spider.SpiderBehaviour
import com.heledron.spideranimation.spider.SpiderBody
import com.heledron.spideranimation.spider.StayStillBehaviour
import com.heledron.spideranimation.spider.TargetBehaviour
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class SpiderMode { WANDER, ALERT, CHASE, LURE, REPEL, FIGHT }

/**
 * Per-spider AI memory: current mode, how long it's been in it, and the patrol anchor/target.
 * Kept as a side table (same pattern AppState already uses for its `instances` map of personal
 * spiders) rather than a real ECS component, since nothing else needs to query it.
 */
class SpiderAIState(var anchor: Vector3d) {
    var mode: SpiderMode = SpiderMode.WANDER
    var modeTimer: Int = 0
    var squeezing: Boolean = false   // pressing over a dug-in player: shrink to fit their hole
    var steerSign: Int = 1           // which way it last steered around an obstacle (sticky)
    var passageClearance: Int = 0    // 1/2 = crawl-hole/doorway on the direct path: shrink to fit

    // The doorway it is currently walking to (see SpiderAI.commitOpening): committing for a
    // while stops it dithering between two doors, and rate-limits the (blocks-heavy) search.
    var openingX = 0.0
    var openingY = 0.0
    var openingZ = 0.0
    var openingHeight = 0            // 0 = nothing committed
    var openingAlongX = false        // the axis it is threaded on (walls stand on the other one)
    var openingLo = 0.0              // the passage's two mouths along that axis (equal for a
    var openingHi = 0.0              //   one-block-thick doorway; metres apart for a tunnel)
    var openingTimer = 0             // ticks left on the commitment, or on the rescan cooldown
    var waypointArrive = 1.0         // how close the current waypoint wants to be reached

    // THE LASER POINTER's dot, and how many ticks are left on it. Outranks every other mode.
    var lureX = 0.0
    var lureY = 0.0
    var lureZ = 0.0
    var lureTimer = 0

    // REPEL: the furthest from the idol it has got so far, and how long since that last grew.
    var repelBest = 0.0
    var repelStall = 0

    // Set by SpiderMob every tick: is it tamed (a pet follows creative players too), and the mob
    // its owner last punched while in creative (FIGHT), if it is still worth going after.
    var tamed = false
    var fightTarget: LivingEntity? = null
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
 *  REPEL  - inside a Warding Idol's zone with nobody it may reach: run straight away from the
 *           idol until it is out, then go back to wandering. Outranks everything, even the laser.
 *  FIGHT  - a TAMED spider whose owner, in creative, just punched a mob: go for that mob (the
 *           bite is SpiderMob's) until it dies or gets away. Only REPEL and the laser outrank it.
 *
 * Who it notices ([notices]): never a spectator, and a creative player only while it is tamed -
 * like vanilla mobs, a wild spider does not hunt anyone in creative; it just wanders. A tamed one
 * still follows its people around in creative, as a pet does.
 *
 * Warding idols also fence the body out (SpiderBody's barriers, at most one block past the
 * edge) and hide every player deeper inside than that, so running into one breaks a chase.
 * None of that applies to the ENRAGED boss, which blows idols apart instead.
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

    /** Can this spider see [player] at all? Never a spectator; a creative player only if it is
     *  tamed (a pet keeps following you, a wild one ignores you, as vanilla mobs do). */
    fun notices(player: Player, tamed: Boolean): Boolean =
        !player.isSpectator && (tamed || !player.isCreative)

    /** SpiderMob, every tick: whether it is tamed, and what (if anything) it should fight. */
    fun setPet(entity: EcsEntity, body: SpiderBody, tamed: Boolean, fightTarget: LivingEntity?) {
        val state = states.getOrPut(entity) { SpiderAIState(Vector3d(body.position)) }
        state.tamed = tamed
        state.fightTarget = fightTarget
    }

    /** True while a tamed spider is going after the mob its owner punched (SpiderMob bites it). */
    fun isFighting(entity: EcsEntity): Boolean = states[entity]?.mode == SpiderMode.FIGHT

    /** The body was just moved somewhere else entirely (the Nether rescue): forget the route,
     *  patrol from the new spot. */
    fun onTeleported(entity: EcsEntity, body: SpiderBody) {
        val state = states[entity] ?: return
        if (state.mode != SpiderMode.FIGHT) enterWander(state, body)
        state.anchor = Vector3d(body.position)
        clearOpening(state)
    }

    fun update(entity: EcsEntity, body: SpiderBody, chaseRadius: Double) {
        val level = body.level
        val state = states.getOrPut(entity) { SpiderAIState(Vector3d(body.position)) }

        // Squeeze descent and doorway-floor pins are re-asserted by tickChase every tick they
        // apply; clearing them up front means wander/alert (and a chase that stopped squeezing
        // or finished threading a door) can never leave a stale height order on the body.
        body.squeezeTargetY = null
        body.passageFloorY = null
        body.passageLaneX = null
        body.passageLaneZ = null

        // THE ENRAGED BOSS is immune to warding idols - no fence, no REPEL, nobody hidden from it -
        // and blows apart any idol it gets near, measured from its FEET (a giant's body centre
        // rides far above the ground the idol sits on).
        if (body.enraged) {
            WardRegistry.shatterNear(level, body.position.x,
                body.position.y - body.walkGait.stationary.bodyHeight, body.position.z,
                WardRegistry.ENRAGED_SHATTER_RANGE)
            if (state.mode == SpiderMode.REPEL) enterWander(state, body)
        }

        // WARDING IDOLS. Fence the body out of every armed zone nearby - SpiderBody holds it at
        // most BITE_BAND past the edge, whatever the steering below asks for - and find out
        // whether it is standing inside one right now.
        val zones = if (body.enraged) emptyList()
            else WardRegistry.zonesNear(level, body.position.x, body.position.z, WARD_FENCE_MARGIN)
        body.barriers = zones.map { SpiderBody.Barrier(it.x, it.z, it.radius - WardRegistry.BITE_BAND) }
        val zone = zones.filter { it.distance(body.position.x, body.position.z) < it.radius }
            .minByOrNull { it.distance(body.position.x, body.position.z) - it.radius }

        // REPEL outranks everything, the laser included: a spider inside a zone runs for the
        // edge, and only once it is out does it go back to its usual business. The fence keeps
        // it from ever getting deeper than the bite band on its own, so landing here means it
        // was already inside - the idol armed around it, or it was summoned there.
        if (state.mode == SpiderMode.REPEL) {
            if (zone != null) {
                tickRepel(entity, body, state, zone)
                return
            }
            enterWander(state, body)   // out: carry on below as a wanderer
        } else if (zone != null &&
            zone.radius - zone.distance(body.position.x, body.position.z) > WardRegistry.BITE_BAND + REPEL_SLACK) {
            enterRepel(state, body, zone)
            tickRepel(entity, body, state, zone)
            return
        }

        // THE LASER POINTER outranks everything, including a chase — that is the whole point of
        // it: shine the dot and the spider breaks off and goes for it instead. LURE is not CHASE,
        // so SpiderMob's bite stays holstered while it is distracted.
        if (state.lureTimer > 0) {
            state.lureTimer--
            if (state.mode != SpiderMode.LURE) {
                state.mode = SpiderMode.LURE
                state.modeTimer = 0
            }
            tickLure(entity, body, state)
            return
        }
        if (state.mode == SpiderMode.LURE) enterWander(state, body)   // the dot just went out

        // A TAMED spider whose owner punched a mob in creative goes for it (SpiderMob decides
        // what counts and lands the bite). Only the ward and the laser outrank it.
        val fightTarget = state.fightTarget
        if (fightTarget != null && fightTarget.isAlive && fightTarget.level() === level &&
            fightTarget.distanceToSqr(body.position.x, body.position.y, body.position.z) <=
                (chaseRadius * Config.CHASE_EXIT_MULTIPLIER.get()).let { it * it }) {
            if (state.mode != SpiderMode.FIGHT) {
                state.mode = SpiderMode.FIGHT
                state.modeTimer = 0
                state.squeezing = false
                state.passageClearance = 0
                clearOpening(state)
            }
            tickFight(entity, body, fightTarget)
            return
        }
        if (state.mode == SpiderMode.FIGHT) enterWander(state, body)   // it died, or got away

        // A player more than the bite band inside a warding idol's zone is out of reach, so it
        // does not see them at all: no hunt starts on them, and a running one breaks off. The
        // enraged boss sees everyone. Nobody in spectator, and nobody in creative unless it is
        // tamed (see notices).
        var nearestPlayer = level.players().filter {
            notices(it, state.tamed) && (body.enraged || !WardRegistry.isSafe(level, it.x, it.z))
        }.minByOrNull {
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
                    wanderOrRepel(entity, body, state, zone)
                }
            }
            SpiderMode.ALERT -> {
                if (nearestPlayer == null || distanceToPlayer > exitRadius) {
                    enterWander(state, body)
                    wanderOrRepel(entity, body, state, zone)
                } else {
                    tickAlert(entity, body, state, nearestPlayer)
                }
            }
            SpiderMode.CHASE -> {
                if (nearestPlayer == null || distanceToPlayer > exitRadius) {
                    enterWander(state, body)
                    wanderOrRepel(entity, body, state, zone)
                } else {
                    tickChase(entity, body, state, nearestPlayer)
                }
            }
            // Unreachable: the lure, repel and fight branches above return, and an expired lure
            // (or a finished repel or fight) is turned back into WANDER before we get here.
            // Kotlin still wants the cases spelled out.
            SpiderMode.LURE, SpiderMode.REPEL, SpiderMode.FIGHT -> tickWander(entity, body, state)
        }
    }

    /** Nobody to hunt: wander - unless it is standing in a ward's zone, in which case it backs
     *  out first (a chase that ended at the edge, say, because its quarry ran inside). */
    private fun wanderOrRepel(entity: EcsEntity, body: SpiderBody, state: SpiderAIState, zone: WardRegistry.Zone?) {
        if (zone == null) {
            tickWander(entity, body, state)
            return
        }
        enterRepel(state, body, zone)
        tickRepel(entity, body, state, zone)
    }

    /** A spider that has been stuck inside a ward's zone for a while, unable to run out (walled
     *  in, say). SpiderSpawnManager moves that one along the old way, alive. */
    fun repelStalled(): Boolean =
        states.values.any { it.mode == SpiderMode.REPEL && it.repelStall >= REPEL_STALL_TICKS }

    /** True while this spider is actively hunting (used by SpiderMob to gate the melee bite). */
    fun isChasing(entity: EcsEntity): Boolean = states[entity]?.mode == SpiderMode.CHASE

    /** Is ANY spider hunting right now? There is only ever one, so this answers "is the hunt on". */
    fun anyChasing(): Boolean = states.values.any { it.mode == SpiderMode.CHASE }

    /**
     * Shine the laser pointer's dot at [point] for [ticks]. Applied to every wild spider (there
     * is only ever one) and returns false when there is none to take the bait. Personal spiders
     * from /spider newinstance are under manual control and never reach SpiderAI, so the pointer
     * leaves them alone.
     */
    fun lure(point: Vector3d, ticks: Int): Boolean {
        if (states.isEmpty()) return false
        for (state in states.values) {
            state.lureX = point.x
            state.lureY = point.y
            state.lureZ = point.z
            state.lureTimer = ticks
        }
        return true
    }

    /** Go for the mob the owner punched, at chase speed, stopping inside bite reach like a chase
     *  does. Straight at it: a mob is rarely holed up behind walls the way a player is. */
    private fun tickFight(entity: EcsEntity, body: SpiderBody, target: LivingEntity) {
        body.setSpeedScale(chaseSpeedFactor(body))
        val arrive = (body.walkGait.stationary.bodyHeight * 2.0).coerceAtMost(4.0)
        entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(fenced(body, Vector3d(target.x, target.y, target.z)), arrive))
    }

    /** Scurry to the dot, and keep drawing it so the pointer is visible while it lasts. */
    private fun tickLure(entity: EcsEntity, body: SpiderBody, state: SpiderAIState) {
        val point = Vector3d(state.lureX, state.lureY, state.lureZ)
        body.setSpeedScale(chaseSpeedFactor(body))
        // A dot inside a ward's zone draws it only as far as the fence.
        entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(fenced(body, point), 0.75))
        body.level.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.1, point.z, 1, 0.0, 0.0, 0.0, 0.0)
    }

    // ---------------------------------------------------------------- warding idols

    // How far beyond a zone's edge the fence is already handed to the body (so a fast spider -
    // or a lunge - can never cover the gap between two AI ticks), how far past the bite band it
    // may be found before it counts as having been caught INSIDE, how far past the edge it runs
    // when repelled, and how long it may go without gaining a block on the way out before it is
    // judged stuck.
    private const val WARD_FENCE_MARGIN = 16.0
    private const val REPEL_SLACK = 0.25
    private const val REPEL_CLEARANCE = 3.0
    private const val REPEL_STALL_TICKS = 200

    private fun enterRepel(state: SpiderAIState, body: SpiderBody, zone: WardRegistry.Zone) {
        state.mode = SpiderMode.REPEL
        state.modeTimer = 0
        state.squeezing = false
        state.passageClearance = 0
        clearOpening(state)
        state.repelBest = zone.distance(body.position.x, body.position.z)
        state.repelStall = 0
    }

    /** Run straight away from the idol - the opposite direction to it - until clear of the edge. */
    private fun tickRepel(entity: EcsEntity, body: SpiderBody, state: SpiderAIState, zone: WardRegistry.Zone) {
        state.squeezing = false
        state.passageClearance = 0
        body.setSpeedScale(chaseSpeedFactor(body))   // it RUNS
        val dist = zone.distance(body.position.x, body.position.z)
        var ux = body.position.x - zone.x
        var uz = body.position.z - zone.z
        if (dist < 1.0e-3) {
            // Standing on the idol itself: any way out is the shortest way out.
            val facing = body.forwardDirection()
            ux = facing.x
            uz = facing.z
        }
        val len = sqrt(ux * ux + uz * uz).coerceAtLeast(1.0e-6)
        val out = zone.radius - dist + REPEL_CLEARANCE
        val x = body.position.x + ux / len * out
        val z = body.position.z + uz / len * out
        val y = SafeGroundFinder.groundYAt(body.level, x, z, refY = body.position.y) ?: body.position.y
        entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(Vector3d(x, y, z), 0.5))

        if (dist > state.repelBest + 1.0) {
            state.repelBest = dist
            state.repelStall = 0
        } else {
            state.repelStall++
        }
    }

    /**
     * Where to steer for [target] without asking the body to go deeper into a ward than it is
     * allowed: a point past the fence is pulled back out onto it, radially. SpiderBody's barrier
     * would hold the body there anyway - this just stops it straining at the line toward
     * something it can never reach.
     */
    private fun fenced(body: SpiderBody, target: Vector3d): Vector3d {
        if (body.enraged) return target   // the boss goes wherever it likes
        var x = target.x
        var z = target.z
        var moved = false
        for (zone in WardRegistry.zonesNear(body.level, x, z, 0.0)) {
            val fence = zone.radius - WardRegistry.BITE_BAND
            val dist = zone.distance(x, z)
            if (dist >= fence) continue
            val ux = if (dist > 1.0e-6) (x - zone.x) / dist else 1.0
            val uz = if (dist > 1.0e-6) (z - zone.z) / dist else 0.0
            x = zone.x + ux * fence
            z = zone.z + uz * fence
            moved = true
        }
        if (!moved) return target
        // The passage pins belong to the doorway it was threading; they don't apply on the fence.
        body.passageFloorY = null
        body.passageLaneX = null
        body.passageLaneZ = null
        val y = SafeGroundFinder.groundYAt(body.level, x, z, refY = target.y) ?: target.y
        return Vector3d(x, y, z)
    }

    /** Does the straight walk from [from] to [to] cut into any armed ward's zone? */
    private fun crossesWard(level: ServerLevel, from: Vector3d, to: Vector3d): Boolean {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val lenSq = dx * dx + dz * dz
        val reach = sqrt(lenSq) + 1.0
        for (zone in WardRegistry.zonesNear(level, from.x, from.z, reach)) {
            // Closest point of the segment to the idol.
            val t = if (lenSq < 1.0e-9) 0.0
                else (((zone.x - from.x) * dx + (zone.z - from.z) * dz) / lenSq).coerceIn(0.0, 1.0)
            if (zone.distance(from.x + dx * t, from.z + dz * t) < zone.radius) return true
        }
        return false
    }

    /** True while the spider is pressing over a dug-in player and should SQUEEZE down to
     *  Config.SQUEEZE_SIZE to fit into their hole (SpiderMob drives the actual scale). */
    fun isSqueezing(entity: EcsEntity): Boolean = states[entity]?.squeezing == true

    /** The size cap needed to slip through the opening (doorway/crawl-hole) currently on the
     *  chase path, or null when nothing constrains it (SpiderMob applies it to the scale). */
    fun passageFitScale(entity: EcsEntity): Double? = when (states[entity]?.passageClearance) {
        1 -> PASSAGE_FIT_CRAWL
        2 -> PASSAGE_FIT_DOORWAY
        else -> null
    }

    // ---------------------------------------------------------------- mode transitions

    private fun enterWander(state: SpiderAIState, body: SpiderBody) {
        state.mode = SpiderMode.WANDER
        state.modeTimer = 0
        state.squeezing = false
        state.passageClearance = 0
        clearOpening(state)
        state.anchor = Vector3d(body.position)
    }

    private fun enterAlert(state: SpiderAIState) {
        state.mode = SpiderMode.ALERT
        state.modeTimer = Config.ALERT_REACTION_TICKS.get()
        state.squeezing = false
        state.passageClearance = 0
        clearOpening(state)
    }

    private fun clearOpening(state: SpiderAIState) {
        state.openingHeight = 0
        state.openingTimer = 0
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
        // THE HUNTER doesn't charge — it stalks. Entirely different close-in rules: see tickStalk.
        if (body.variantKey == "hunter") {
            tickStalk(entity, body, state, player)
            return
        }
        val rageBoost = if (body.enraged) Config.ENRAGED_SPEED_MULTIPLIER.get() else 1.0
        body.setSpeedScale(chaseSpeedFactor(body) * rageBoost)
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

        // CORNER PATHFINDING (chasePathfinding, default true). The old chase walked the straight
        // line into wall faces: the body has no horizontal collision, so it slid INSIDE the wall
        // and the height correction rode it up and out the top — the reported "gets stuck in the
        // wall then teleports up it". Now the chase plans its move each tick; pressure mode
        // (player vertically out of reach) keeps the old direct press — that machinery (v1.2.1's
        // constant pressure and THE SQUEEZE) already handles verticality.
        state.passageClearance = 0
        var targetPos = Vector3d(player.x, player.y, player.z)
        var arrive = arriveDistance
        if (!pressureMode && Config.CHASE_PATHFINDING.get()) {
            state.waypointArrive = 1.0   // waypoints are re-planned every tick; flow through them
            val waypoint = planChaseMove(body, state, player)
            if (waypoint != null) {
                targetPos = waypoint
                arrive = state.waypointArrive
            }
        }
        entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(fenced(body, targetPos), arrive))
    }

    // ---------------------------------------------------------------- the hunter's stalk

    // How the HUNTER hunts ("a hiding mechanic where it just stalks you or camps"): it closes in
    // ONLY while nobody is looking at it, and freezes dead-still — mid-stride — the moment a
    // view swings toward it. Every glance away brings it closer, in silence. Inside commit range
    // your eyes stop helping: it takes you anyway. And if you're vertically out of its reach (a
    // hole, a pillar), it doesn't press or squeeze like the others — it CAMPS, motionless, just
    // outside. It can wait longer than you can.
    private const val HUNTER_COMMIT_RANGE = 6.0    // blocks: watching it no longer helps
    private const val HUNTER_SEEN_DOT = 0.7        // cos ~45°: "on your screen" counts as watched

    private fun tickStalk(entity: EcsEntity, body: SpiderBody, state: SpiderAIState, player: ServerPlayer) {
        // The hunter never squeezes and never shrinks: clear anything the shared machinery set.
        state.squeezing = false
        state.passageClearance = 0
        body.squeezeTargetY = null

        val dx = player.x - body.position.x
        val dz = player.z - body.position.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val groundLevelY = body.position.y - body.walkGait.stationary.bodyHeight
        val verticalGap = abs(groundLevelY - player.y)

        // Player dug in / pillared up nearby: CAMP. Dead still, right outside the hideout.
        if (verticalGap > 2.0 && horizontal < 8.0) {
            entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
            return
        }

        // Watched from outside commit range: freeze. Not a step, not a sway.
        val anyoneWatching = body.level.players().any { notices(it, state.tamed) && isLookingAt(it, body) }
        if (anyoneWatching && horizontal > HUNTER_COMMIT_RANGE) {
            entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
            return
        }

        // Unseen (or committed): close in, fast and silent, with the same route planning as the
        // normal chase — but crawl-holes count as walls (it never shrinks; doorways fit as-is).
        body.setSpeedScale(Config.HUNTER_SPEED_MULTIPLIER.get())
        var targetPos = Vector3d(player.x, player.y, player.z)
        var arrive = (body.walkGait.stationary.bodyHeight * 2.0).coerceAtMost(4.0)
        if (Config.CHASE_PATHFINDING.get()) {
            state.waypointArrive = 1.0
            val waypoint = planChaseMove(body, state, player, minOpening = 2)
            if (waypoint != null) {
                targetPos = waypoint
                arrive = state.waypointArrive
            }
        }
        state.passageClearance = 0   // fixed size: openings are walked through, never shrunk for
        entity.replaceComponent<SpiderBehaviour>(TargetBehaviour(fenced(body, targetPos), arrive))
    }

    /** Is this player's view direction pointing at the spider's body (within ~45°)? */
    private fun isLookingAt(player: ServerPlayer, body: SpiderBody): Boolean {
        val look = player.lookAngle
        val dx = body.position.x - player.x
        val dy = body.position.y - (player.y + player.eyeHeight)
        val dz = body.position.z - player.z
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1.0e-6) return true
        return (look.x * dx + look.y * dy + look.z * dz) / len > HUNTER_SEEN_DOT
    }

    // ---------------------------------------------------------------- chase pathfinding

    // Steering probe angles away from the direct line (radians: ~35/70/105 degrees), the
    // direct-line lookahead, the waypoint distance, and the fit sizes for slipping through
    // ground-level openings (top of the body ~1.35x scale: 1.15 clears a 2-high doorway,
    // 0.45 a 1-high crawl-hole, with margin).
    private val STEER_ANGLES = doubleArrayOf(0.611, 1.222, 1.833)
    private const val CHASE_LOOKAHEAD = 6.0
    private const val STEER_PROBE_DIST = 4.0
    // Fit sizes for slipping through a gap. These are small on purpose: the leg spread is
    // roughly 2.6x the scale, so anything near 1.0 wears a 3-block-wide skirt of legs against a
    // 1-block doorway — it looked (and physically behaved) like it did not fit, because it
    // didn't. At 0.6 the body threads a doorway with its legs splayed around the frame.
    internal const val PASSAGE_FIT_DOORWAY = 0.6
    internal const val PASSAGE_FIT_CRAWL = 0.3

    // Door-seeking: how far around the blocked spot to hunt for a way in, how long to commit to
    // one once found (so it doesn't dither between two doors), how long to wait before searching
    // again after finding nothing, and how close it must be before it shrinks down to fit.
    private const val OPENING_SEARCH_RADIUS = 7
    private const val OPENING_COMMIT_TICKS = 80
    private const val OPENING_RESCAN_TICKS = 20
    private const val OPENING_SHRINK_RANGE = 7.0
    private const val OPENING_STAGE_DIST = 1.6    // where it lines up, square in front of the gap
    private const val OPENING_PIN_RANGE = 4.5     // within this, the body is pinned to the floor

    private class LineProbe(
        val walkable: Boolean,
        val opening: Int,
        val endGroundY: Double,
        val hitX: Double = 0.0,
        val hitZ: Double = 0.0,
    )

    /**
     * Plan this tick's chase move. Returns null to charge STRAIGHT at the player — either the
     * direct line is walkable, there's an opening in the wall to shrink through (recorded in
     * [SpiderAIState.passageClearance]), or everything is blocked (old behaviour: press/climb).
     * Returns a WAYPOINT when a wall blocks the direct line but a steering angle gets around it:
     * nearest angle to the target wins, same side as last tick preferred so corners don't
     * flip-flop, and the waypoint flows into the direct line again the moment it clears.
     */
    private fun planChaseMove(body: SpiderBody, state: SpiderAIState, player: ServerPlayer, minOpening: Int = 1): Vector3d? {
        val level = body.level
        val bodyHeight = body.walkGait.stationary.bodyHeight
        val startGround = body.position.y - bodyHeight
        // Comfortable step-up scales with the spider; anything taller is a WALL to go around.
        val maxClimb = (bodyHeight * 1.25).coerceIn(2.0, 8.0)
        val maxDrop = (bodyHeight * 2.0).coerceIn(4.0, 16.0)

        val dx = player.x - body.position.x
        val dz = player.z - body.position.z
        val horizontal = sqrt(dx * dx + dz * dz)
        if (horizontal < 1.5) return null   // effectively on top of them already

        val dirX = dx / horizontal
        val dirZ = dz / horizontal

        val direct = probeLine(level, body.position.x, body.position.z, startGround,
            dirX, dirZ, min(horizontal, CHASE_LOOKAHEAD), maxClimb, maxDrop)

        // Can this body even fit where the player is standing? With the player just inside a
        // corridor mouth the straight line to them reads as perfectly walkable, so the chase
        // used to charge at full size, meet the wall beside the entrance, and climb it. If the
        // prey is somewhere too low for the spider as it currently is, that line is a lie:
        // hunt for the way in and thread it instead.
        // roomAt, not confinementAt: this asks how much room is where the PLAYER is, which must
        // not be gated on the spider's own size — the whole point is to notice when the spider is
        // far too big for it.
        val playerRoom = SafeGroundFinder.roomAt(level, player.x, player.y, player.z)
        val tooBigForPlayersSpot = playerRoom != null && playerRoom < bodyHeight + 0.5

        if (direct.walkable && !tooBigForPlayersSpot) {
            clearOpening(state)   // through it (or never needed one)
            return null
        }
        if (direct.opening >= minOpening) {
            // A doorway/crawl-hole where the wall blocks the line: fit through it and go
            // STRAIGHT (minOpening lets the fixed-size hunter treat crawl-holes as walls).
            state.passageClearance = direct.opening
            return null
        }

        // LET ITSELF IN. A doorway is one block wide and the straight line to you virtually
        // never crosses it, so a blocked line used to mean "steer around the building" — the
        // spider would circle your house forever, doorway or not. Now it goes looking for the
        // way in: the best opening in the obstacle (closest to you, and one that leads
        // somewhere), committed to for a while so it walks a straight line to your door
        // instead of dithering. It shrinks to fit only once it's close, so the approach still
        // looks like a full-size spider bearing down on the house.
        // Where to hunt for the way in: around the point the line hit a wall — or, when the
        // line never hit anything and the problem is simply that the spider is too big for
        // where the player is, around the PLAYER (the probe carries no hit point in that case).
        val scanX = if (direct.walkable) player.x else direct.hitX
        val scanZ = if (direct.walkable) player.z else direct.hitZ
        val scanY = if (direct.walkable) player.y else direct.endGroundY

        val opening = commitOpening(body, state, player, scanX, scanZ, scanY, minOpening, startGround, maxClimb, maxDrop)
        if (opening != null) return threadOpening(body, state, opening)

        for (angle in STEER_ANGLES) {
            for (sign in intArrayOf(state.steerSign, -state.steerSign)) {
                val a = angle * sign
                val cosA = cos(a)
                val sinA = sin(a)
                val sx = dirX * cosA - dirZ * sinA
                val sz = dirX * sinA + dirZ * cosA
                val probe = probeLine(level, body.position.x, body.position.z, startGround,
                    sx, sz, STEER_PROBE_DIST, maxClimb, maxDrop)
                if (probe.walkable) {
                    state.steerSign = sign
                    return Vector3d(
                        body.position.x + sx * STEER_PROBE_DIST,
                        probe.endGroundY,
                        body.position.z + sz * STEER_PROBE_DIST,
                    )
                }
            }
        }
        return null   // boxed in on every side: fall back to the old direct press
    }

    /**
     * Walk through the gap, squarely. Aiming straight at a doorway from an angle puts the body
     * CENTRE into the frame rather than the hole, and a body inside a block gets shoved a block
     * upward every tick by collision resolution — that is the "teleports up the wall" everyone
     * sees. So: line up on the passage axis first, one and a half blocks out, then walk straight
     * through to the same distance on the far side. Meanwhile the body is pinned to the
     * opening's floor (no climbing) and shrunk to fit (no 3-block leg-skirt in a 1-block door).
     */
    private fun threadOpening(body: SpiderBody, state: SpiderAIState, opening: SafeGroundFinder.Opening): Vector3d {
        val alongX = opening.alongX
        // Split the world into "along the passage" and "across it": everything below is 1-D.
        val bodyAlong = if (alongX) body.position.x else body.position.z
        val bodyLane = if (alongX) body.position.z else body.position.x
        val lane = if (alongX) opening.z else opening.x
        fun point(along: Double, across: Double) =
            if (alongX) Vector3d(along, opening.y, across) else Vector3d(across, opening.y, along)

        // Enter by the near mouth, leave by the far one — a doorway's two mouths are the same
        // block, a tunnel's are its two ends.
        val lo = state.openingLo
        val hi = state.openingHi
        val enterAtLo = bodyAlong <= (lo + hi) * 0.5
        val entry = if (enterAtLo) lo else hi
        val exit = if (enterAtLo) hi else lo
        val dir = if (enterAtLo) 1.0 else -1.0

        val laneOff = abs(bodyLane - lane)
        val distToEntry = abs(bodyAlong - entry) + laneOff
        if (distToEntry < OPENING_SHRINK_RANGE) state.passageClearance = opening.height

        // Inside the tube (or in its mouth): hold the floor AND the centre line for the whole
        // length. This is what makes a long corridor survivable — steering alone drifts.
        val insideSpan = bodyAlong > lo - 1.5 && bodyAlong < hi + 1.5
        if (insideSpan && laneOff < 1.5) {
            body.passageFloorY = opening.y
            if (alongX) body.passageLaneZ = lane else body.passageLaneX = lane
        } else if (distToEntry < OPENING_PIN_RANGE) {
            body.passageFloorY = opening.y   // stops the wall-climb while it walks up to the mouth
        }

        state.waypointArrive = 0.4   // commit to these waypoints; don't stop short of the frame

        // Line up square on the centre line first; once lined up (or already inside), drive
        // straight down the axis and out the far end.
        val lined = laneOff < 0.75
        return if (!lined && !insideSpan) point(entry - dir * OPENING_STAGE_DIST, lane)
        else point(exit + dir * OPENING_STAGE_DIST, lane)
    }

    /**
     * The opening the spider is currently heading for: the committed one if it still holds,
     * otherwise a fresh scan (rate-limited — the scan reads a lot of blocks, and re-running it
     * every tick while walled in would be wasteful and jittery).
     */
    private fun commitOpening(
        body: SpiderBody, state: SpiderAIState, player: ServerPlayer,
        scanX: Double, scanZ: Double, scanY: Double, minOpening: Int,
        startGround: Double, maxClimb: Double, maxDrop: Double,
    ): SafeGroundFinder.Opening? {
        if (state.openingTimer > 0) {
            state.openingTimer--
            return if (state.openingHeight > 0)
                SafeGroundFinder.Opening(state.openingX, state.openingY, state.openingZ,
                    state.openingHeight, state.openingAlongX)
            else null   // cooling down after a fruitless search
        }

        // Candidates come back nearest-to-the-player first; take the first one the spider can
        // actually WALK to from where it stands. That reachability test is what keeps it from
        // fixating on the far side of the building (or on a gap it would have to pass through
        // the wall to use) — it always picks the way in that its own side of the wall offers.
        val candidates = SafeGroundFinder.collectOpenings(
            body.level, scanX, scanZ, scanY,
            player.x, player.z, OPENING_SEARCH_RADIUS, minOpening,
        )
        val found = candidates.firstOrNull { canReach(body, startGround, it, maxClimb, maxDrop, minOpening) }
        if (found == null) {
            state.openingHeight = 0
            state.openingTimer = OPENING_RESCAN_TICKS
            return null
        }
        state.openingX = found.x
        state.openingY = found.y
        state.openingZ = found.z
        state.openingHeight = found.height
        state.openingAlongX = found.alongX
        val extent = SafeGroundFinder.passageExtent(body.level, found, minOpening)
        state.openingLo = extent.first
        state.openingHi = extent.second
        state.openingTimer = OPENING_COMMIT_TICKS
        return found
    }

    /** Can the spider walk from where it stands to this opening? The line may end blocked AT
     *  the opening itself — that gap IS the way through — but not before it. */
    private fun canReach(
        body: SpiderBody, startGround: Double, opening: SafeGroundFinder.Opening,
        maxClimb: Double, maxDrop: Double, minOpening: Int,
    ): Boolean {
        val dx = opening.x - body.position.x
        val dz = opening.z - body.position.z
        val len = sqrt(dx * dx + dz * dz)
        if (len < 1.0e-6) return true
        val probe = probeLine(body.level, body.position.x, body.position.z, startGround,
            dx / len, dz / len, len, maxClimb, maxDrop)
        if (probe.walkable) return true
        val hx = probe.hitX - opening.x
        val hz = probe.hitZ - opening.z
        return hx * hx + hz * hz <= 2.25 && probe.opening >= minOpening
    }

    /**
     * March a horizontal line in 1-block steps, requiring walkable ground (within step-up/drop
     * limits) at every column — the same dimension-aware scan the wander pre-scan uses. On the
     * first blocked column, also report whether it carries a ground-level OPENING (doorway or
     * crawl-hole) the spider could shrink through; the top-down ground scans can't see those.
     */
    private fun probeLine(
        level: ServerLevel, x0: Double, z0: Double, ground0: Double,
        dirX: Double, dirZ: Double, length: Double, maxClimb: Double, maxDrop: Double,
    ): LineProbe {
        var ground = ground0
        var x = x0
        var z = z0
        val steps = ceil(length).toInt().coerceAtLeast(1)
        repeat(steps) {
            x += dirX
            z += dirZ
            val groundY = SafeGroundFinder.groundYAt(level, x, z, refY = ground)
            if (groundY == null || groundY - ground > maxClimb || ground - groundY > maxDrop) {
                return LineProbe(false, SafeGroundFinder.openingHeight(level, x, ground, z), ground, x, z)
            }
            ground = groundY
        }
        return LineProbe(true, 0, ground)
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
            // Warding idols are no-go ground: a patrolling spider never picks a spot inside one,
            // nor a route that cuts across the edge of one on the way. (Not the enraged boss.)
            if (!body.enraged && WardRegistry.isProtected(level, x, z)) return@repeat
            val safeY = SafeGroundFinder.groundYAt(level, x, z, refY = anchor.y) ?: return@repeat
            val target = Vector3d(x, safeY, z)
            if (!body.enraged && crossesWard(level, body.position, target)) return@repeat
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
