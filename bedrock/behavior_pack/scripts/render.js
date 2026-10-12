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

import { world } from "@minecraft/server";
import { toDegrees } from "./engine/vec.js";

export const LEG_TYPE = "arachno:leg";
const OLD_TYPES = ["arachno:segment"];   // earlier builds' entity, swept if any are left behind
export const MAX_SEGMENTS = 3;
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
    this.anchor = null;
    this.retiring = [];      // [{ entity, legIndex, anchor, ticksLeft }] - the previous anchor's legs
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
    if (prev !== undefined && Math.abs(prev - value) < 1e-4) return;
    e.setProperty(key, value);
    last[key] = value;
    this.writes++;
  }

  legEntity(legIndex) {
    const existing = this.entities[legIndex];
    if (isValid(existing)) return existing;
    try {
      const a = this.anchor;
      const e = this.dimension.spawnEntity(LEG_TYPE, { x: a.x, y: a.y, z: a.z });
      try { e.setRotation({ x: 0, y: 0 }); } catch (err) {}
      this.entities[legIndex] = e;
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
        this.entities = [];
        this.anchor = body.copy();
      }
    }

    const pivot = spider.gait.legChainPivotMode(spider);
    const poses = spider.legs.map((leg) => (leg.chain.segments.length ? leg.chain.getRotations(pivot) : null));

    spider.legs.forEach((leg, legIndex) => {
      if (!poses[legIndex]) return;
      const e = this.legEntity(legIndex);
      if (e) this.writeLeg(e, this.anchor, spider, legIndex, poses[legIndex]);
    });

    // Retiring legs keep drawing the live pose (relative to their own anchor) until they go.
    this.retiring = this.retiring.filter((r) => {
      if (r.ticksLeft-- <= 0 || !isValid(r.entity)) {
        if (isValid(r.entity)) try { r.entity.remove(); } catch (err) {}
        this.written.delete(r.entity.id);
        return false;
      }
      if (poses[r.legIndex]) this.writeLeg(r.entity, r.anchor, spider, r.legIndex, poses[r.legIndex]);
      return true;
    });
  }

  /** Sends one leg's pose to one leg entity parked at anchor `a`. */
  writeLeg(e, a, spider, legIndex, rotations) {
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
    } catch (err) { this.writeError = String(err); }  // usually just unloaded this tick
  }

  /** How many of this spider's (live, not retiring) leg entities exist right now. */
  liveCount() {
    return this.entities.filter(isValid).length;
  }

  ownedIds() {
    const ids = new Set();
    for (const e of this.entities) if (isValid(e)) ids.add(e.id);
    for (const r of this.retiring) if (isValid(r.entity)) ids.add(r.entity.id);
    return ids;
  }

  removeAll() {
    for (const e of this.entities) if (isValid(e)) try { e.remove(); } catch (err) {}
    for (const r of this.retiring) if (isValid(r.entity)) try { r.entity.remove(); } catch (err) {}
    this.entities = [];
    this.retiring = [];
    this.anchor = null;
    this.written.clear();
  }
}

/** Discard every leg entity (and any old-build segment entity) that no live renderer owns. */
export function sweepOrphans(ownedIds) {
  for (const id of ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"]) {
    let dim;
    try { dim = world.getDimension(id); } catch (err) { continue; }
    for (const type of [LEG_TYPE, ...OLD_TYPES]) {
      let list = [];
      try { list = dim.getEntities({ type }); } catch (err) { continue; }
      for (const e of list) {
        if (!ownedIds.has(e.id)) try { e.remove(); } catch (err) {}
      }
    }
  }
}
