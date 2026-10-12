// Port of entity/SpiderAI.kt (the Java lead, arachnomod-fabric-1.21.1): the WANDER / ALERT / CHASE
// state machine and the chase's route planning - steering round walls, hunting for the door,
// threading doorways and corridors squarely, and THE SQUEEZE after a dug-in player.
//
// Not yet ported (they arrive with their features): LURE (laser pointer), REPEL and the ward
// fence (Warding Idol), the hunter's stalk, and the enraged boss. Their hooks are the same as in
// Java, so they slot in without reshaping this file.
//
// One SpiderAI per spider (Java keeps a side table keyed by entity; same state, same names).

import { Vec3 } from "./engine/vec.js";
import { StayStillBehaviour, TargetBehaviour, DirectionBehaviour } from "./engine/behaviour.js";
import { groundYAt, roomAt, openingHeight, collectOpenings, passageExtent, Opening, walledIn } from "./ground.js";
import { CONFIG, scaleToSpeedFactor } from "./mob.js";

export const SpiderMode = { WANDER: "WANDER", ALERT: "ALERT", CHASE: "CHASE" };

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
  }

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

    let nearestPlayer = null, bestSq = Infinity;
    for (const p of players) {
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
          this.tickWander();
        }
        break;
      case SpiderMode.ALERT:
        if (!nearestPlayer || distanceToPlayer > exitRadius) {
          this.enterWander();
          this.tickWander();
        } else {
          this.tickAlert(nearestPlayer);
        }
        break;
      case SpiderMode.CHASE:
        if (!nearestPlayer || distanceToPlayer > exitRadius) {
          this.enterWander();
          this.tickWander();
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
    const p = player.location;
    body.setSpeedScale(chaseSpeedFactor(body));
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
    this.behaviour = new TargetBehaviour(targetPos, arrive);
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
      const safeY = groundYAt(world, x, z, anchor.y);
      if (safeY === null) continue;
      const target = new Vec3(x, safeY, z);
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
