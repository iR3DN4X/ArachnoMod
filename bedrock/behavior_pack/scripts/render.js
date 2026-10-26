// Port of spider/SpiderRenderer.kt for Bedrock.
//
// Java draws every leg segment as a BlockDisplay PARKED at an anchor, with the segment's position
// folded into its client-interpolated transform - because a display's position and transform sync
// separately, and moving the entity every tick makes segments slide apart. Bedrock has the same
// trap (an entity's position and its properties reach the client in separate packets), so the
// same answers apply:
//
//   - ONE `arachno:leg` entity per leg; its three segments are bones of one model, all driven by
//     that entity's properties, so a leg's segments always update together and cannot separate.
//   - The entity stays PARKED at an anchor near the body. It carries the leg's four joints
//     (x0..z3, relative to the anchor; joint 3 is the foot) and each segment's rotation (YXZ Euler
//     a/p/r) and size (l, t). Position and rotation therefore always arrive in the same update.
//   - ZERO-FLASH RE-PARKS (Java's recipe, user-confirmed "PERFECT" in July): a live leg entity is
//     NEVER moved. Teleporting one makes its new position and new properties arrive a frame apart,
//     and for that frame the whole spider is drawn 24+ blocks away - the "lurch" (a whole-spider
//     blink) seen in rounds 4 and 8. Instead a re-park spawns REPLACEMENT legs at the new anchor
//     and keeps the old ones alive for RETIRE_TICKS more ticks, still updated with the live pose
//     relative to their own anchor, so a whole leg is on screen at every frame of the handoff.
//   - The CLIENT smooths the JOINTS over one tick (pre_animation in entity/leg.entity.json), the
//     Bedrock stand-in for Java's 1-tick display interpolation. It draws `property + offset`: the
//     offsets hold each segment on its glided joints and shrink to 0 within a tick, so if that
//     script ever fails the legs still draw exactly from the properties.
//
// The bone math (see animations/leg.animation.json) follows from vanilla's own models: fronts face
// -Z, the model is mirrored on X, and the root bone cancels the body rotation exactly as vanilla's
// sleeping-player animation does. The entity is also turned to rotation 0 so nothing depends on it.
//
// /spider debug's parts (debug.js) are drawn here too, one `arachno:debug` box entity each, parked
// at the same anchor and handed over at the same re-parks, so they move exactly with the legs.

import { world } from "@minecraft/server";
import { toDegrees, clamp } from "./engine/vec.js";
import { DEBUG, DEBUG_TYPE, debugParts } from "./debug.js";

export const LEG_TYPE = "arachno:leg";
const OLD_TYPES = ["arachno:segment"];   // earlier builds' entity, swept if any are left behind
/** Every entity type a renderer draws with (the orphan sweep's list). */
export const DRAWN_TYPES = [LEG_TYPE, DEBUG_TYPE, ...OLD_TYPES];
export const MAX_SEGMENTS = 3;
const PART_OFFSET = 128;                 // a debug part's offset range in entities/debug.json
const PART_SIZE = 256;                   // ...and its size range
const FORCE_REPARK = 32;                 // blocks the body may travel from the anchor (Java: 32)
const QUIET_REPARK = 12;                 // ...or this far, whenever it is standing still (Java: 12)
const QUIET_SPEED = 0.03;
const RETIRE_TICKS = 4;                  // old legs stay this many ticks after a re-park (Java: 4)

export const isValid = (e) => {
  if (!e) return false;
  try { return typeof e.isValid === "function" ? e.isValid() : !!e.isValid; } catch (err) { return false; }
};

export class SpiderRenderer {
  constructor(dimension) {
    this.dimension = dimension;
    this.entities = [];      // the live leg entities, one per leg, parked at `anchor`
    this.debug = new Map();  // debug part name -> its live box entity, parked at `anchor` too
    this.anchor = null;
    this.retiring = [];      // [{ entity, legIndex | partKey, anchor, ticksLeft }] - the previous anchor's
    this.spawnError = null;  // why the last leg spawn failed, for the in-game check line
    this.writeError = null;  // ...and the last property write that threw
    this.written = new Map(); // entity id -> { property: last value sent }
    this.writes = 0;         // property writes since last read (perf HUD)
  }

  /** setProperty, skipped when the value has not changed. Every write is a call into the game
   *  and a property sync to every client; a spider standing still used to cost 216 a tick. */
  set(e, key, value) {
    let last = this.written.get(e.id);
    if (!last) { last = {}; this.written.set(e.id, last); }
    const prev = last[key];
    if (prev !== undefined && (typeof value === "boolean" ? prev === value : Math.abs(prev - value) < 1e-4)) return;
    e.setProperty(key, value);
    last[key] = value;
    this.writes++;
  }

  legEntity(legIndex) {
    const existing = this.entities[legIndex];
    if (isValid(existing)) return existing;
    const e = this.spawnParked(LEG_TYPE);
    if (e) this.entities[legIndex] = e;
    return e;
  }

  debugEntity(key) {
    const existing = this.debug.get(key);
    if (isValid(existing)) return existing;
    const e = this.spawnParked(DEBUG_TYPE);
    if (e) this.debug.set(key, e);
    return e;
  }

  spawnParked(type) {
    try {
      const a = this.anchor;
      const e = this.dimension.spawnEntity(type, { x: a.x, y: a.y, z: a.z });
      try { e.setRotation({ x: 0, y: 0 }); } catch (err) {}
      return e;
    } catch (err) {
      this.spawnError = String(err);
      return null;
    }
  }

  render(spider) {
    const body = spider.position;
    if (!this.anchor) {
      this.anchor = body.copy();
    } else {
      const d = this.anchor.distance(body);
      const quiet = spider.velocity.length() < QUIET_SPEED;
      if (d > FORCE_REPARK || (quiet && d > QUIET_REPARK)) {
        // Hand the current legs over to retirement and park fresh ones at the body.
        this.entities.forEach((entity, legIndex) => {
          if (isValid(entity)) this.retiring.push({ entity, legIndex, anchor: this.anchor, ticksLeft: RETIRE_TICKS });
        });
        for (const [partKey, entity] of this.debug) {
          if (isValid(entity)) this.retiring.push({ entity, partKey, anchor: this.anchor, ticksLeft: RETIRE_TICKS });
        }
        this.entities = [];
        this.debug = new Map();
        this.anchor = body.copy();
      }
    }

    const pivot = spider.gait.legChainPivotMode(spider);
    const poses = spider.legs.map((leg) => (leg.chain.segments.length ? leg.chain.getRotations(pivot) : null));
    const hops = spider.legs.map((leg) => stepHop(spider, leg));

    spider.legs.forEach((leg, legIndex) => {
      if (!poses[legIndex]) return;
      const e = this.legEntity(legIndex);
      if (e) this.writeLeg(e, this.anchor, spider, legIndex, poses[legIndex], hops[legIndex]);
    });

    // The debug view, while it is on.
    const parts = DEBUG.enabled ? debugParts(spider) : null;
    for (const [key, part] of parts || []) {
      const e = this.debugEntity(key);
      if (e) this.writePart(e, this.anchor, part);
    }
    for (const [key, e] of this.debug) {
      if (!parts) this.discard(e);
      else if (!parts.has(key)) this.hidePart(e);   // e.g. a corner the support polygon lost
    }
    if (!parts) this.debug.clear();

    // Retiring legs (and parts) keep drawing the live pose (relative to their own anchor) until
    // they go.
    this.retiring = this.retiring.filter((r) => {
      const isPart = r.partKey !== undefined;
      if (r.ticksLeft-- <= 0 || !isValid(r.entity) || (isPart && !parts)) {
        this.discard(r.entity);
        return false;
      }
      if (isPart) {
        const part = parts.get(r.partKey);
        if (part) this.writePart(r.entity, r.anchor, part);
        else this.hidePart(r.entity);
      } else if (poses[r.legIndex]) {
        this.writeLeg(r.entity, r.anchor, spider, r.legIndex, poses[r.legIndex], hops[r.legIndex]);
      }
      return true;
    });
  }

  discard(e) {
    if (isValid(e)) try { e.remove(); } catch (err) {}
    this.written.delete(e.id);
  }

  /** Sends one debug part (debug.js) to one box entity parked at anchor `a`. */
  writePart(e, a, part) {
    const x = part.x - a.x, y = part.y - a.y, z = part.z - a.z;
    // Out of the entity's reach (a giant's far corner, say): leave it out rather than throw.
    if (Math.abs(x) > PART_OFFSET || Math.abs(y) > PART_OFFSET || Math.abs(z) > PART_OFFSET) {
      this.hidePart(e);
      return;
    }
    try {
      this.set(e, "arachno:x", x);
      this.set(e, "arachno:y", y);
      this.set(e, "arachno:z", z);
      this.set(e, "arachno:a", part.a);
      this.set(e, "arachno:p", part.p);
      this.set(e, "arachno:r", part.r);
      this.set(e, "arachno:sx", clamp(part.sx, 0, PART_SIZE));
      this.set(e, "arachno:sy", clamp(part.sy, 0, PART_SIZE));
      this.set(e, "arachno:sz", clamp(part.sz, 0, PART_SIZE));
      this.set(e, "arachno:tex", part.tex);
      this.set(e, "arachno:bright", part.bright);
      this.set(e, "arachno:snap", part.snap);
    } catch (err) { this.writeError = String(err); }
  }

  /** A part with nothing to show this tick: shrunk to nothing (and the client snaps it to its
   *  next place instead of gliding there from the old one - see entity/debug.entity.json). */
  hidePart(e) {
    try {
      this.set(e, "arachno:sx", 0);
      this.set(e, "arachno:sy", 0);
      this.set(e, "arachno:sz", 0);
    } catch (err) { this.writeError = String(err); }
  }

  /** Sends one leg's pose to one leg entity parked at anchor `a`. */
  writeLeg(e, a, spider, legIndex, rotations, hop = 0) {
    const chain = spider.legs[legIndex].chain;
    const plan = spider.bodyPlan.legs[legIndex];
    try {
      // The joints: segment s runs from joint s to joint s+1; joint 3 is the foot. A shorter leg
      // repeats its last joint, so its missing segments sit at the foot with length 0.
      const joints = [chain.root, ...chain.segments.map((segment) => segment.position)];
      for (let j = 0; j <= MAX_SEGMENTS; j++) {
        const p = joints[Math.min(j, joints.length - 1)];
        this.set(e, `arachno:x${j}`, p.x - a.x);
        this.set(e, `arachno:y${j}`, p.y - a.y);
        this.set(e, `arachno:z${j}`, p.z - a.z);
      }
      for (let s = 0; s < MAX_SEGMENTS; s++) {
        const segmentPlan = plan ? plan.segments[s] : undefined;
        if (!segmentPlan || s >= rotations.length) {
          this.set(e, `arachno:l${s}`, 0);
          this.set(e, `arachno:t${s}`, 0);
          continue;
        }
        const euler = rotations[s].getEulerAnglesYXZ();
        this.set(e, `arachno:a${s}`, toDegrees(euler.y));
        this.set(e, `arachno:p${s}`, toDegrees(euler.x));
        this.set(e, `arachno:r${s}`, toDegrees(euler.z));
        this.set(e, `arachno:l${s}`, segmentPlan.length);
        this.set(e, `arachno:t${s}`, segmentPlan.thickness);
      }
      // The leg's texture: an index into camo.js TEXTURES (0 = netherite; the camo variant
      // repaints each leg as the block under its foot).
      this.set(e, "arachno:tex", spider.legTex ? (spider.legTex[legIndex] ?? 0) : 0);
      // A step begun and finished inside this one tick: the client lifts the foot in an arc while
      // it glides it over (gen-leg-rp.sh), instead of sliding it along the ground.
      this.set(e, "arachno:hop", hop);
    } catch (err) { this.writeError = String(err); }  // usually just unloaded this tick
  }

  /** How many of this spider's (live, not retiring) leg entities exist right now. */
  liveCount() {
    return this.entities.filter(isValid).length;
  }

  ownedIds() {
    const ids = new Set();
    for (const e of this.entities) if (isValid(e)) ids.add(e.id);
    for (const e of this.debug.values()) if (isValid(e)) ids.add(e.id);
    for (const r of this.retiring) if (isValid(r.entity)) ids.add(r.entity.id);
    return ids;
  }

  removeAll() {
    for (const e of this.entities) if (isValid(e)) try { e.remove(); } catch (err) {}
    for (const e of this.debug.values()) if (isValid(e)) try { e.remove(); } catch (err) {}
    for (const r of this.retiring) if (isValid(r.entity)) try { r.entity.remove(); } catch (err) {}
    this.entities = [];
    this.debug = new Map();
    this.retiring = [];
    this.anchor = null;
    this.written.clear();
  }
}

/**
 * THE DRAWN HOP. The engine sometimes begins AND finishes a step inside one tick: a step shorter
 * than legMoveSpeed + legDropDistance never leaves the ground, in Java too (devtools/simtest/
 * chase.html "skating": at the close-range size 0.6 that is most short steps, up to a block in one
 * tick). Drawn as it is, the foot slides along the floor. The engine is left alone - making those
 * steps last two ticks changed where the AI's tunnel route goes - and the hop is drawn instead:
 * how high the client should arc this tick's glide of the foot (0 = no hop). A step landed this
 * tick that also began this tick is exactly `!isMoving && timeSinceBeginMove === 1`.
 */
const HOP_PER_BLOCK = 0.5;        // arc height per block the foot travels
const HOP_MAX_LIFTS = 1.5;        // ...at most this many of the gait's own lift heights
const HOP_MIN_TRAVEL = 0.05;      // blocks; less is a twitch, not a step

export function stepHop(spider, leg) {
  if (leg.isMoving || leg.timeSinceBeginMove !== 1 || !leg.previousEndEffector) return 0;
  const h = leg.endEffector.horizontalDistance(leg.previousEndEffector);
  if (h < HOP_MIN_TRAVEL) return 0;
  return Math.min(h * HOP_PER_BLOCK, spider.gait.legLiftHeight * HOP_MAX_LIFTS);
}

/** Discard every leg and debug entity (and any old-build segment entity) that no live renderer
 *  owns. */
export function sweepOrphans(ownedIds) {
  for (const id of ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"]) {
    let dim;
    try { dim = world.getDimension(id); } catch (err) { continue; }
    for (const type of DRAWN_TYPES) {
      let list = [];
      try { list = dim.getEntities({ type }); } catch (err) { continue; }
      for (const e of list) {
        if (!ownedIds.has(e.id)) try { e.remove(); } catch (err) {}
      }
    }
  }
}
