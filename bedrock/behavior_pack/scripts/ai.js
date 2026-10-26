// Port of entity/SpiderAI.kt (the Java lead, arachnomod-fabric-1.21.1): the WANDER / ALERT / CHASE
// state machine and the chase's route planning - steering round walls, hunting for the door,
// threading doorways and corridors squarely, and THE SQUEEZE after a dug-in player.
//
// Also THE HUNTER'S STALK (Java v1.4.0): it only moves while nobody is looking at it.
//
// And the ENRAGED boss's speed (Java v1.5.0), and Java v1.8.5's two newest modes:
//   LURE  - the laser pointer's dot: it scurries after it, outranking a chase (and it can't bite).
//   REPEL - inside a Warding Idol's zone with nobody it may reach: run straight out, away from the
//           idol. Outranks everything, even the laser. Idols also fence the body out (SpiderBody's
//           barriers) and hide every player more than a block inside. The ENRAGED boss ignores all
//           of it - and blows idols apart.
// And Java v1.8.6's:
//   FIGHT - a TAMED spider whose owner, in creative, just punched a mob goes for it (the bite is
//           the controller's) until it dies or gets away. Only REPEL and the laser outrank it.
//   Who it notices (enums.js notices): never a spectator, and a creative player only while it is
//   tamed - a wild one ignores you in creative and wanders, like vanilla mobs.
//
// One SpiderAI per spider (Java keeps a side table keyed by entity; same state, same names).

import { Vec3 } from "./engine/vec.js";
import { StayStillBehaviour, TargetBehaviour, DirectionBehaviour } from "./engine/behaviour.js";
import { groundYAt, roomAt, openingHeight, collectOpenings, passageExtent, Opening, walledIn } from "./ground.js";
import { CONFIG, scaleToSpeedFactor } from "./mob.js";
import { zonesNear, isSafe, isProtected, shatterNear, BITE_BAND, ENRAGED_SHATTER_RANGE } from "./wards.js";
import { notices } from "./enums.js";

/** Is `entity` still worth fighting: there, alive, and in this dimension? */
export function fightable(entity, dimensionId) {
  try {
    if (!entity || !(typeof entity.isValid === "function" ? entity.isValid() : entity.isValid)) return false;
    if (entity.dimension.id !== dimensionId) return false;
    const health = entity.getComponent("minecraft:health");
    return !!health && health.currentValue > 0;
  } catch (e) {
    return false;
  }
}

export const SpiderMode = { WANDER: "WANDER", ALERT: "ALERT", CHASE: "CHASE", LURE: "LURE", REPEL: "REPEL", FIGHT: "FIGHT" };

// WARDING IDOLS: how far beyond a zone's edge the fence is already handed to the body (so a fast
// spider - or a lunge - can never cover the gap between two AI ticks), how far past the bite band
// it may be found before it counts as caught INSIDE, how far past the edge it runs when repelled,
// and how long it may go without gaining a block on the way out before it is judged stuck.
const WARD_FENCE_MARGIN = 16.0;
const REPEL_SLACK = 0.25;
const REPEL_CLEARANCE = 3.0;
const REPEL_STALL_TICKS = 200;

// ---- chase pathfinding constants (exactly Java's) ---------------------------------------------
const STEER_ANGLES = [0.611, 1.222, 1.833];   // ~35/70/105 degrees off the direct line
const CHASE_LOOKAHEAD = 6.0;
const STEER_PROBE_DIST = 4.0;
export const PASSAGE_FIT_DOORWAY = 0.6;
export const PASSAGE_FIT_CRAWL = 0.3;
const OPENING_SEARCH_RADIUS = 7;
const OPENING_COMMIT_TICKS = 80;
const OPENING_RESCAN_TICKS = 20;
const OPENING_SHRINK_RANGE = 7.0;
const OPENING_STAGE_DIST = 1.6;
const OPENING_PIN_RANGE = 4.5;

// THE HUNTER (Java v1.4.0): inside this range watching it no longer helps; a view within ~45
// degrees of it counts as watching.
const HUNTER_COMMIT_RANGE = 6.0;
const HUNTER_SEEN_DOT = 0.7;

const clampTo = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

class LineProbe {
  constructor(walkable, opening, endGroundY, hitX = 0, hitZ = 0) {
    this.walkable = walkable; this.opening = opening; this.endGroundY = endGroundY; this.hitX = hitX; this.hitZ = hitZ;
  }
}

export class SpiderAI {
  constructor(body) {
    this.body = body;
    this.world = body.level.blocks;           // BlockWorld (blocks.js)
    // SpiderAIState
    this.anchor = body.position.copy();
    this.mode = SpiderMode.WANDER;
    this.modeTimer = 0;
    this.squeezing = false;
    this.steerSign = 1;
    this.passageClearance = 0;
    this.openingX = 0; this.openingY = 0; this.openingZ = 0;
    this.openingHeight = 0;
    this.openingAlongX = false;
    this.openingLo = 0; this.openingHi = 0;
    this.openingTimer = 0;
    this.waypointArrive = 1.0;
    // The behaviour to apply next tick (Java's entity.replaceComponent<SpiderBehaviour>).
    this.behaviour = new StayStillBehaviour();
    // Every player in the spider's dimension this tick (the hunter asks whether ANYONE watches).
    this.players = [];
    // THE LASER POINTER's dot, and how many ticks are left on it.
    this.lurePoint = new Vec3();
    this.lureTimer = 0;
    // REPEL: the furthest from the idol it has got so far, and how long since that last grew.
    this.repelBest = 0;
    this.repelStall = 0;
    // Set by the controller every tick (Java v1.8.6): is it tamed (a pet follows creative players
    // too), and the mob its owner last punched in creative (FIGHT), while still worth going after.
    this.tamed = false;
    this.fightTarget = null;
  }

  get dimensionId() { return this.world.dimension.id; }

  /** The controller, every tick: whether it is tamed, and what (if anything) it should fight. */
  setPet(tamed, fightTarget) {
    this.tamed = tamed;
    this.fightTarget = fightTarget;
  }

  /** True while a tamed spider is going after the mob its owner punched (the controller bites). */
  isFighting() { return this.mode === SpiderMode.FIGHT; }

  /** Shine the laser pointer's dot at `point` for `ticks` ticks. */
  lure(point, ticks) {
    this.lurePoint = Vec3.of(point);
    this.lureTimer = ticks;
  }

  /** Stuck inside a ward's zone a while, unable to run out (walled in, say): the spawn manager
   *  moves it along, alive. */
  repelStalled() { return this.mode === SpiderMode.REPEL && this.repelStall >= REPEL_STALL_TICKS; }

  isChasing() { return this.mode === SpiderMode.CHASE; }
  isSqueezing() { return this.squeezing; }
  passageFitScale() {
    if (this.passageClearance === 1) return PASSAGE_FIT_CRAWL;
    if (this.passageClearance === 2) return PASSAGE_FIT_DOORWAY;
    return null;
  }

  /** players: [{ location, ... }] in this spider's dimension. isDay: for hostileOnlyAtNight. */
  update(players, chaseRadius, isDay = false) {
    const body = this.body;
    // Squeeze descent and doorway pins are re-asserted by tickChase every tick they apply.
    body.squeezeTargetY = null;
    body.passageFloorY = null;
    body.passageLaneX = null;
    body.passageLaneZ = null;
    this.players = players;
    body.carefulGait = false;   // re-derived at the end of a normal tick (lure and repel return early)
    const dim = this.dimensionId;

    // THE ENRAGED BOSS is immune to warding idols - no fence, no REPEL, nobody hidden from it - and
    // blows apart any idol it gets near, measured from its FEET (a giant's body rides far above).
    if (body.enraged) {
      shatterNear(this.world.dimension, body.position.x, body.position.y - body.walkGait.stationary.bodyHeight,
        body.position.z, ENRAGED_SHATTER_RANGE);
      if (this.mode === SpiderMode.REPEL) this.enterWander();
    }

    // WARDING IDOLS: fence the body out of every armed zone nearby (SpiderBody holds it at most
    // BITE_BAND past the edge, whatever the steering asks), and find out whether it stands in one.
    const zones = body.enraged ? [] : zonesNear(dim, body.position.x, body.position.z, WARD_FENCE_MARGIN);
    body.barriers = zones.map((z) => ({ x: z.x, z: z.z, radius: z.radius - BITE_BAND }));
    let zone = null;
    for (const z of zones) {
      const d = z.distance(body.position.x, body.position.z);
      if (d < z.radius && (!zone || d - z.radius < zone.distance(body.position.x, body.position.z) - zone.radius)) zone = z;
    }

    // REPEL outranks everything, the laser included: a spider inside a zone runs for the edge. The
    // fence keeps it from ever getting deeper than the bite band on its own, so landing here means
    // it was already inside - the idol armed around it, or it was summoned there.
    if (this.mode === SpiderMode.REPEL) {
      if (zone) {
        this.tickRepel(zone);
        return;
      }
      this.enterWander();   // out: carry on below as a wanderer
    } else if (zone && zone.radius - zone.distance(body.position.x, body.position.z) > BITE_BAND + REPEL_SLACK) {
      this.enterRepel(zone);
      this.tickRepel(zone);
      return;
    }

    // THE LASER POINTER outranks everything else, a chase included - shine the dot and the spider
    // breaks off and goes for it. LURE is not CHASE, so its bite stays holstered meanwhile.
    if (this.lureTimer > 0) {
      this.lureTimer--;
      if (this.mode !== SpiderMode.LURE) {
        this.mode = SpiderMode.LURE;
        this.modeTimer = 0;
      }
      this.tickLure();
      return;
    }
    if (this.mode === SpiderMode.LURE) this.enterWander();   // the dot just went out

    // A TAMED spider whose owner punched a mob in creative goes for it (Java v1.8.6; the controller
    // decides what counts and lands the bite). Only the ward and the laser outrank it.
    const target = this.fightTarget;
    if (target && fightable(target, dim) &&
        body.position.distanceSquared(target.location) <= (chaseRadius * CONFIG.chaseExitDistanceMultiplier) ** 2) {
      if (this.mode !== SpiderMode.FIGHT) {
        this.mode = SpiderMode.FIGHT;
        this.modeTimer = 0;
        this.squeezing = false;
        this.passageClearance = 0;
        this.clearOpening();
      }
      this.tickFight(target);
      return;
    }
    if (this.mode === SpiderMode.FIGHT) this.enterWander();   // it died, or got away

    // A player more than the bite band inside a ward is out of reach: it doesn't see them at all.
    // Nobody in spectator, and nobody in creative unless it is tamed (enums.js notices).
    // The enraged boss sees everyone.
    let nearestPlayer = null, bestSq = Infinity;
    for (const p of players) {
      if (!notices(p, this.tamed)) continue;
      if (!body.enraged && isSafe(dim, p.location.x, p.location.z)) continue;
      const d = body.position.distanceSquared(p.location);
      if (d < bestSq) { bestSq = d; nearestPlayer = p; }
    }
    // "Vanilla spider" mode: players are invisible to it during the day.
    if (CONFIG.hostileOnlyAtNight && isDay) nearestPlayer = null;
    const distanceToPlayer = nearestPlayer ? Math.sqrt(bestSq) : Number.MAX_VALUE;
    const exitRadius = chaseRadius * CONFIG.chaseExitDistanceMultiplier;

    switch (this.mode) {
      case SpiderMode.WANDER:
        if (nearestPlayer && distanceToPlayer <= chaseRadius) {
          this.enterAlert();
          this.tickAlert(nearestPlayer);
        } else {
          this.wanderOrRepel(zone);
        }
        break;
      case SpiderMode.ALERT:
        if (!nearestPlayer || distanceToPlayer > exitRadius) {
          this.enterWander();
          this.wanderOrRepel(zone);
        } else {
          this.tickAlert(nearestPlayer);
        }
        break;
      case SpiderMode.CHASE:
        if (!nearestPlayer || distanceToPlayer > exitRadius) {
          this.enterWander();
          this.wanderOrRepel(zone);
        } else {
          this.tickChase(nearestPlayer);
        }
        break;
    }
    // Round 14's emergency steps (engine/gait.js) change WHEN the body reaches a tunnel mouth by a
    // few centimetres, and Java's AI decides "through or over the hill" on centimetres there: keep
    // Java's exact gait while lining up for an opening or squeezing.
    body.carefulGait = this.passageClearance > 0 || this.squeezing || body.passageFloorY !== null ||
      body.passageLaneX !== null || body.passageLaneZ !== null;
  }

  // ---------------------------------------------------------------- warding idols + the laser

  /** Nobody to hunt: wander - unless it stands in a ward's zone, in which case it backs out first
   *  (a chase that ended at the edge because its quarry ran inside, say). */
  wanderOrRepel(zone) {
    if (!zone) { this.tickWander(); return; }
    this.enterRepel(zone);
    this.tickRepel(zone);
  }

  enterRepel(zone) {
    this.mode = SpiderMode.REPEL;
    this.modeTimer = 0;
    this.squeezing = false;
    this.passageClearance = 0;
    this.clearOpening();
    this.repelBest = zone.distance(this.body.position.x, this.body.position.z);
    this.repelStall = 0;
  }

  /** Run straight away from the idol - the opposite direction to it - until clear of the edge. */
  tickRepel(zone) {
    const body = this.body;
    this.squeezing = false;
    this.passageClearance = 0;
    body.setSpeedScale(chaseSpeedFactor(body));   // it RUNS
    const dist = zone.distance(body.position.x, body.position.z);
    let ux = body.position.x - zone.x;
    let uz = body.position.z - zone.z;
    if (dist < 1e-3) {
      // Standing on the idol itself: any way out is the shortest way out.
      const facing = body.forwardDirection();
      ux = facing.x;
      uz = facing.z;
    }
    const len = Math.max(Math.sqrt(ux * ux + uz * uz), 1e-6);
    const out = zone.radius - dist + REPEL_CLEARANCE;
    const x = body.position.x + ux / len * out;
    const z = body.position.z + uz / len * out;
    const y = groundYAt(this.world, x, z, body.position.y) ?? body.position.y;
    this.behaviour = new TargetBehaviour(new Vec3(x, y, z), 0.5);
    if (dist > this.repelBest + 1.0) {
      this.repelBest = dist;
      this.repelStall = 0;
    } else {
      this.repelStall++;
    }
  }

  /** Scurry to the dot, and keep drawing it so the pointer is visible while it lasts. */
  /** Go for the mob the owner punched, at chase speed, stopping inside bite reach like a chase
   *  does. Straight at it: a mob is rarely holed up behind walls the way a player is. */
  tickFight(target) {
    const body = this.body;
    body.setSpeedScale(chaseSpeedFactor(body));
    const arrive = Math.min(body.walkGait.stationary.bodyHeight * 2, 4);
    this.behaviour = new TargetBehaviour(this.fenced(Vec3.of(target.location)), arrive);
  }

  tickLure() {
    const body = this.body, p = this.lurePoint;
    body.setSpeedScale(chaseSpeedFactor(body));
    // A dot inside a ward's zone draws it only as far as the fence.
    this.behaviour = new TargetBehaviour(this.fenced(p), 0.75);
    body.level.spawnParticle("minecraft:basic_flame_particle", new Vec3(p.x, p.y + 0.1, p.z));
  }

  /** Where to steer for `target` without asking the body to go deeper into a ward than it may: a
   *  point past the fence is pulled back out onto it, radially (the barrier would hold it there
   *  anyway - this just stops it straining at the line toward something it can never reach). */
  fenced(target) {
    const body = this.body;
    if (body.enraged) return target;   // the boss goes wherever it likes
    let x = target.x, z = target.z, moved = false;
    for (const zone of zonesNear(this.dimensionId, x, z, 0)) {
      const fence = zone.radius - BITE_BAND;
      const dist = zone.distance(x, z);
      if (dist >= fence) continue;
      const ux = dist > 1e-6 ? (x - zone.x) / dist : 1;
      const uz = dist > 1e-6 ? (z - zone.z) / dist : 0;
      x = zone.x + ux * fence;
      z = zone.z + uz * fence;
      moved = true;
    }
    if (!moved) return target;
    // The passage pins belong to the doorway it was threading; they don't apply on the fence.
    body.passageFloorY = null;
    body.passageLaneX = null;
    body.passageLaneZ = null;
    return new Vec3(x, groundYAt(this.world, x, z, target.y) ?? target.y, z);
  }

  /** Does the straight walk from `from` to `to` cut into any armed ward's zone? */
  crossesWard(from, to) {
    const dx = to.x - from.x, dz = to.z - from.z;
    const lenSq = dx * dx + dz * dz;
    for (const zone of zonesNear(this.dimensionId, from.x, from.z, Math.sqrt(lenSq) + 1)) {
      const t = lenSq < 1e-9 ? 0 : clampTo(((zone.x - from.x) * dx + (zone.z - from.z) * dz) / lenSq, 0, 1);
      if (zone.distance(from.x + dx * t, from.z + dz * t) < zone.radius) return true;
    }
    return false;
  }

  // ---------------------------------------------------------------- mode transitions

  enterWander() {
    this.mode = SpiderMode.WANDER;
    this.modeTimer = 0;
    this.squeezing = false;
    this.passageClearance = 0;
    this.clearOpening();
    this.anchor = this.body.position.copy();
  }

  enterAlert() {
    this.mode = SpiderMode.ALERT;
    this.modeTimer = CONFIG.alertReactionTicks;
    this.squeezing = false;
    this.passageClearance = 0;
    this.clearOpening();
  }

  clearOpening() {
    this.openingHeight = 0;
    this.openingTimer = 0;
  }

  enterChase() {
    this.mode = SpiderMode.CHASE;
    this.modeTimer = 0;
  }

  // ---------------------------------------------------------------- per-mode behaviour

  tickWander() {
    const body = this.body;
    if (!CONFIG.enableWandering) {
      this.behaviour = new StayStillBehaviour();
      return;
    }
    body.setSpeedScale(wanderSpeedFactor(body));
    // Commit to the current heading/pause until the timer runs out.
    if (this.modeTimer > 0) {
      this.modeTimer--;
      return;
    }
    if (Math.random() < CONFIG.wanderPauseChance) {
      this.behaviour = new StayStillBehaviour();
    } else {
      const point = this.pickWanderPoint(this.anchor);
      this.behaviour = point ? new TargetBehaviour(point, 1.0) : new StayStillBehaviour();
    }
    const minTicks = Math.max(1, Math.trunc(CONFIG.wanderMinIntervalSeconds * 20));
    const maxTicks = Math.max(minTicks, Math.trunc(CONFIG.wanderMaxIntervalSeconds * 20));
    this.modeTimer = minTicks + Math.floor(Math.random() * (maxTicks - minTicks + 1));
  }

  tickAlert(player) {
    const body = this.body;
    // Face the player without moving.
    const direction = Vec3.of(player.location).sub(body.position);
    if (direction.lengthSquared() > 1e-6) direction.normalize();
    this.behaviour = new DirectionBehaviour(direction, new Vec3(0, 0, 0));
    body.setSpeedScale(wanderSpeedFactor(body));
    if (this.modeTimer <= 0) this.enterChase(); else this.modeTimer--;
  }

  tickChase(player) {
    const body = this.body;
    // THE HUNTER doesn't charge - it stalks. Entirely different close-in rules: see tickStalk.
    if (body.variantKey === "hunter") {
      this.tickStalk(player);
      return;
    }
    const p = player.location;
    const rageBoost = body.enraged ? CONFIG.enragedSpeedMultiplier : 1.0;
    body.setSpeedScale(chaseSpeedFactor(body) * rageBoost);
    // Stop distance CLAMPED; DUG-IN PRESSURE when the player is vertically out of reach.
    const groundLevelY = body.position.y - body.walkGait.stationary.bodyHeight;
    const verticalGap = Math.abs(groundLevelY - p.y);
    const pressureMode = verticalGap > 2.0;
    const arriveDistance = pressureMode ? 0.25 : Math.min(body.walkGait.stationary.bodyHeight * 2.0, 4.0);

    // THE SQUEEZE: pressing directly over a hidden player BELOW, shrink to fit their hole.
    const dx = p.x - body.position.x;
    const dz = p.z - body.position.z;
    const playerBelow = groundLevelY - p.y > 2.0;
    // DELIBERATE DEVIATION FROM JAVA (Bedrock only; body.javaWalk restores it): Java squeezes
    // whenever the player is 2+ blocks lower and close - on hilly ground (the user's sand dunes)
    // that is just someone at the foot of a slope, and the spider shrank to 0.25 and regrew for
    // nothing, its feet sliding with every rescale. Here the player must actually be IN a hole or
    // trench (walled in on an axis).
    this.squeezing = playerBelow && (dx * dx + dz * dz) < 6.0 * 6.0 &&
      (body.javaWalk || walledIn(this.world, p.x, p.y, p.z));
    // Drive the descent once the body has ACTUALLY shrunk to (near) squeeze size.
    body.squeezeTargetY = this.squeezing && body.sizeScale <= CONFIG.squeezeSize * 1.25 ? p.y : null;

    // CORNER PATHFINDING: plan this tick's move (pressure mode keeps the direct press).
    this.passageClearance = 0;
    let targetPos = Vec3.of(p);
    let arrive = arriveDistance;
    if (!pressureMode && CONFIG.chasePathfinding) {
      this.waypointArrive = 1.0;
      const waypoint = this.planChaseMove(player);
      if (waypoint) {
        targetPos = waypoint;
        arrive = this.waypointArrive;
      }
    }
    this.behaviour = new TargetBehaviour(this.fenced(targetPos), arrive);
  }

  // ---------------------------------------------------------------- the hunter's stalk

  // How the HUNTER hunts: it closes in ONLY while nobody is looking at it, and freezes dead still -
  // mid-stride - the moment a view swings toward it. Inside commit range your eyes stop helping:
  // it takes you anyway. And if you're vertically out of its reach (a hole, a pillar), it doesn't
  // press or squeeze like the others - it CAMPS, motionless, just outside.
  tickStalk(player) {
    const body = this.body;
    const p = player.location;
    // The hunter never squeezes and never shrinks: clear anything the shared machinery set.
    this.squeezing = false;
    this.passageClearance = 0;
    body.squeezeTargetY = null;

    const dx = p.x - body.position.x;
    const dz = p.z - body.position.z;
    const horizontal = Math.sqrt(dx * dx + dz * dz);
    const groundLevelY = body.position.y - body.walkGait.stationary.bodyHeight;
    const verticalGap = Math.abs(groundLevelY - p.y);

    // Player dug in / pillared up nearby: CAMP. Dead still, right outside the hideout.
    if (verticalGap > 2.0 && horizontal < 8.0) {
      this.behaviour = new StayStillBehaviour();
      return;
    }

    // Watched from outside commit range: freeze. Not a step, not a sway.
    const anyoneWatching = this.players.some((other) => notices(other, this.tamed) && isLookingAt(other, body));
    if (anyoneWatching && horizontal > HUNTER_COMMIT_RANGE) {
      this.behaviour = new StayStillBehaviour();
      return;
    }

    // Unseen (or committed): close in, fast and silent, with the same route planning as the
    // normal chase - but crawl-holes count as walls (it never shrinks; doorways fit as-is).
    body.setSpeedScale(CONFIG.hunterSpeedMultiplier);
    let targetPos = Vec3.of(p);
    let arrive = Math.min(body.walkGait.stationary.bodyHeight * 2.0, 4.0);
    if (CONFIG.chasePathfinding) {
      this.waypointArrive = 1.0;
      const waypoint = this.planChaseMove(player, 2);
      if (waypoint) {
        targetPos = waypoint;
        arrive = this.waypointArrive;
      }
    }
    this.passageClearance = 0;   // fixed size: openings are walked through, never shrunk for
    this.behaviour = new TargetBehaviour(this.fenced(targetPos), arrive);
  }

  // ---------------------------------------------------------------- chase pathfinding

  /** Null = charge STRAIGHT at the player; a Vec3 = a waypoint round/through the obstacle. */
  planChaseMove(player, minOpening = 1) {
    const body = this.body, world = this.world, p = player.location;
    const bodyHeight = body.walkGait.stationary.bodyHeight;
    const startGround = body.position.y - bodyHeight;
    // Comfortable step-up scales with the spider; anything taller is a WALL to go around.
    const maxClimb = clampTo(bodyHeight * 1.25, 2.0, 8.0);
    const maxDrop = clampTo(bodyHeight * 2.0, 4.0, 16.0);

    const dx = p.x - body.position.x;
    const dz = p.z - body.position.z;
    const horizontal = Math.sqrt(dx * dx + dz * dz);
    if (horizontal < 1.5) return null;   // effectively on top of them already
    const dirX = dx / horizontal;
    const dirZ = dz / horizontal;

    const direct = this.probeLine(body.position.x, body.position.z, startGround, dirX, dirZ,
      Math.min(horizontal, CHASE_LOOKAHEAD), maxClimb, maxDrop);

    // Can this body even fit where the player is standing?
    const playerRoom = roomAt(world, p.x, p.y, p.z);
    const tooBigForPlayersSpot = playerRoom !== null && playerRoom < bodyHeight + 0.5;

    if (direct.walkable && !tooBigForPlayersSpot) {
      this.clearOpening();
      return null;
    }
    if (direct.opening >= minOpening) {
      this.passageClearance = direct.opening;
      return null;
    }

    // LET ITSELF IN: hunt for the way in around where the line hit the wall (or around the
    // player, when the line is clear but the spider is simply too big for their spot).
    const scanX = direct.walkable ? p.x : direct.hitX;
    const scanZ = direct.walkable ? p.z : direct.hitZ;
    const scanY = direct.walkable ? p.y : direct.endGroundY;
    const opening = this.commitOpening(player, scanX, scanZ, scanY, minOpening, startGround, maxClimb, maxDrop);
    if (opening) return this.threadOpening(opening);

    for (const angle of STEER_ANGLES) {
      for (const sign of [this.steerSign, -this.steerSign]) {
        const a = angle * sign;
        const cosA = Math.cos(a);
        const sinA = Math.sin(a);
        const sx = dirX * cosA - dirZ * sinA;
        const sz = dirX * sinA + dirZ * cosA;
        const probe = this.probeLine(body.position.x, body.position.z, startGround, sx, sz, STEER_PROBE_DIST, maxClimb, maxDrop);
        if (probe.walkable) {
          this.steerSign = sign;
          return new Vec3(body.position.x + sx * STEER_PROBE_DIST, probe.endGroundY, body.position.z + sz * STEER_PROBE_DIST);
        }
      }
    }
    return null;   // boxed in on every side: fall back to the old direct press
  }

  /** Walk through the gap, squarely: line up on the passage axis, then straight through. */
  threadOpening(opening) {
    const body = this.body;
    const alongX = opening.alongX;
    const bodyAlong = alongX ? body.position.x : body.position.z;
    const bodyLane = alongX ? body.position.z : body.position.x;
    const lane = alongX ? opening.z : opening.x;
    const point = (along, across) => (alongX ? new Vec3(along, opening.y, across) : new Vec3(across, opening.y, along));

    const lo = this.openingLo;
    const hi = this.openingHi;
    const enterAtLo = bodyAlong <= (lo + hi) * 0.5;
    const entry = enterAtLo ? lo : hi;
    const exit = enterAtLo ? hi : lo;
    const dir = enterAtLo ? 1.0 : -1.0;

    const laneOff = Math.abs(bodyLane - lane);
    const distToEntry = Math.abs(bodyAlong - entry) + laneOff;
    if (distToEntry < OPENING_SHRINK_RANGE) this.passageClearance = opening.height;

    // Inside the tube (or in its mouth): hold the floor AND the centre line for its whole length.
    const insideSpan = bodyAlong > lo - 1.5 && bodyAlong < hi + 1.5;
    if (insideSpan && laneOff < 1.5) {
      body.passageFloorY = opening.y;
      if (alongX) body.passageLaneZ = lane; else body.passageLaneX = lane;
    } else if (distToEntry < OPENING_PIN_RANGE) {
      body.passageFloorY = opening.y;   // stops the wall-climb while it walks up to the mouth
    }

    this.waypointArrive = 0.4;   // commit to these waypoints; don't stop short of the frame
    const lined = laneOff < 0.75;
    return !lined && !insideSpan
      ? point(entry - dir * OPENING_STAGE_DIST, lane)
      : point(exit + dir * OPENING_STAGE_DIST, lane);
  }

  /** The committed opening, or a fresh (rate-limited) scan for one. */
  commitOpening(player, scanX, scanZ, scanY, minOpening, startGround, maxClimb, maxDrop) {
    if (this.openingTimer > 0) {
      this.openingTimer--;
      return this.openingHeight > 0
        ? new Opening(this.openingX, this.openingY, this.openingZ, this.openingHeight, this.openingAlongX)
        : null;   // cooling down after a fruitless search
    }
    const p = player.location;
    const candidates = collectOpenings(this.world, scanX, scanZ, scanY, p.x, p.z, OPENING_SEARCH_RADIUS, minOpening);
    const found = candidates.find((c) => this.canReach(startGround, c, maxClimb, maxDrop, minOpening));
    if (!found) {
      this.openingHeight = 0;
      this.openingTimer = OPENING_RESCAN_TICKS;
      return null;
    }
    this.openingX = found.x;
    this.openingY = found.y;
    this.openingZ = found.z;
    this.openingHeight = found.height;
    this.openingAlongX = found.alongX;
    const extent = passageExtent(this.world, found, minOpening);
    this.openingLo = extent[0];
    this.openingHi = extent[1];
    this.openingTimer = OPENING_COMMIT_TICKS;
    return found;
  }

  /** Can the spider walk from where it stands to this opening? (The line may end AT it.) */
  canReach(startGround, opening, maxClimb, maxDrop, minOpening) {
    const body = this.body;
    const dx = opening.x - body.position.x;
    const dz = opening.z - body.position.z;
    const len = Math.sqrt(dx * dx + dz * dz);
    if (len < 1e-6) return true;
    const probe = this.probeLine(body.position.x, body.position.z, startGround, dx / len, dz / len, len, maxClimb, maxDrop);
    if (probe.walkable) return true;
    const hx = probe.hitX - opening.x;
    const hz = probe.hitZ - opening.z;
    return hx * hx + hz * hz <= 2.25 && probe.opening >= minOpening;
  }

  /** March a horizontal line in 1-block steps requiring walkable ground at every column. */
  probeLine(x0, z0, ground0, dirX, dirZ, length, maxClimb, maxDrop) {
    let ground = ground0;
    let x = x0;
    let z = z0;
    const steps = Math.max(1, Math.ceil(length));
    for (let i = 0; i < steps; i++) {
      x += dirX;
      z += dirZ;
      const groundY = groundYAt(this.world, x, z, ground);
      if (groundY === null || groundY - ground > maxClimb || ground - groundY > maxDrop) {
        return new LineProbe(false, openingHeight(this.world, x, ground, z), ground, x, z);
      }
      ground = groundY;
    }
    return new LineProbe(true, 0, ground);
  }

  // ---------------------------------------------------------------- helpers

  pickWanderPoint(anchor) {
    const body = this.body, world = this.world;
    const radius = CONFIG.wanderRadius;
    for (let attempt = 0; attempt < 8; attempt++) {
      const angle = Math.random() * 2 * Math.PI;
      const dist = Math.random() * radius;
      const x = anchor.x + Math.cos(angle) * dist;
      const z = anchor.z + Math.sin(angle) * dist;
      // Warding idols are no-go ground: a patrol never picks a spot inside one, nor a route that
      // cuts across the edge of one on the way. (Not the enraged boss.)
      if (!body.enraged && isProtected(this.dimensionId, x, z)) continue;
      const safeY = groundYAt(world, x, z, anchor.y);
      if (safeY === null) continue;
      const target = new Vec3(x, safeY, z);
      if (!body.enraged && this.crossesWard(body.position, target)) continue;
      // Route pre-scan: the WAY there must be safe too; a comfortable step-down scales with size.
      const maxDrop = clampTo(body.walkGait.stationary.bodyHeight * 1.5, 3.0, 12.0);
      if (!this.isPathSafe(body.position, target, maxDrop)) continue;
      return target;
    }
    return null;
  }

  /** Walk the straight line in ~1-block steps requiring safe ground; reject cliff-edge drops. */
  isPathSafe(start, end, maxDrop) {
    const world = this.world;
    const dx = end.x - start.x;
    const dz = end.z - start.z;
    const distance = Math.sqrt(dx * dx + dz * dz);
    if (distance < 1.0) return true;
    const steps = Math.ceil(distance);
    const stepX = dx / steps;
    const stepZ = dz / steps;
    let x = start.x;
    let z = start.z;
    let prevGroundY = groundYAt(world, start.x, start.z, start.y);
    for (let i = 0; i < steps; i++) {
      x += stepX;
      z += stepZ;
      const groundY = groundYAt(world, x, z, prevGroundY !== null ? prevGroundY : start.y);
      if (groundY === null) return false;
      if (prevGroundY !== null && prevGroundY - groundY > maxDrop) return false;   // cliff edge ahead
      prevGroundY = groundY;
    }
    return true;
  }
}

function wanderSpeedFactor(body) { return scaleToSpeedFactor(body.sizeScale) * CONFIG.wanderSpeedFactor; }
function chaseSpeedFactor(body) { return scaleToSpeedFactor(body.sizeScale); }

/** Is this player's view pointing at the spider's body (within ~45 degrees)? From the eyes. */
export function isLookingAt(player, body) {
  let look, eye;
  try { look = player.getViewDirection(); } catch (e) { return false; }
  try { eye = player.getHeadLocation(); } catch (e) { eye = { x: player.location.x, y: player.location.y + 1.62, z: player.location.z }; }
  const dx = body.position.x - eye.x;
  const dy = body.position.y - eye.y;
  const dz = body.position.z - eye.z;
  const len = Math.sqrt(dx * dx + dy * dy + dz * dz);
  if (len < 1e-6) return true;
  return (look.x * dx + look.y * dy + look.z * dz) / len > HUNTER_SEEN_DOT;
}
