// Port of entity/WardRegistry.kt: WARDING IDOLS - the placed blocks that make a base a place the
// hunt does not start (Java v1.8.5).
//
// A player standing inside a ward's radius stops being a SPAWN ANCHOR, and no spider is ever
// dropped inside one. It works per PLACE rather than by freezing the one global timer, so one
// player idling at base doesn't make the whole server safe.
//
// It is also a SAFE ZONE you can run to: the spider's body can never get more than BITE_BAND (one
// block) past the edge (SpiderBody's barriers), and anyone deeper inside is invisible to it - no
// hunt, no bite, no blindness. A spider that ends up inside anyway runs straight out (SpiderAI's
// REPEL). The ENRAGED boss ignores all of it - and blows apart any idol within 16 blocks.
//
// The positions live in a world dynamic property, so protection holds while the base's chunks are
// unloaded (an offline or far-away base is exactly when it matters). Arming uses world time
// (world.getAbsoluteTime), so a half-woken idol keeps its progress across a restart.

import { world } from "@minecraft/server";
import { CONFIG } from "./config.js";

export const WARD_BLOCK = "arachno:warding_idol";
const STORE_KEY = "arachno:wards";

/** How far past the edge the spider may still reach: its body stops here, and a player standing
 *  within this last stretch of the zone can still be hunted and bitten. */
export const BITE_BAND = 1.0;
/** How close (blocks, from its feet) the ENRAGED boss has to get to an idol to blow it apart. */
export const ENRAGED_SHATTER_RANGE = 16.0;
const SHATTER_NEWS_RANGE = 256.0;   // who hears that an idol went (horizontally)
const SWEEP_TICKS = 200;            // a periodic check for idols that vanished without a hook

let wards = null;                   // [{ d, x, y, z, t }] - t = world time it was placed
let sweepTimer = 0;
let fallbackTime = 0;               // world time when getAbsoluteTime is unavailable

/** World time in ticks (survives restarts). */
export function worldTime() {
  try { return world.getAbsoluteTime(); } catch (e) { return fallbackTime; }
}

function load() {
  if (wards) return wards;
  wards = [];
  try {
    const raw = world.getDynamicProperty(STORE_KEY);
    if (typeof raw === "string") {
      for (const w of JSON.parse(raw)) {
        if (w && typeof w.d === "string" && [w.x, w.y, w.z, w.t].every(Number.isFinite)) wards.push(w);
      }
    }
  } catch (e) { wards = []; }   // unreadable record: no wards rather than a broken world
  return wards;
}

function save() {
  try { world.setDynamicProperty(STORE_KEY, JSON.stringify(load())); } catch (e) { /* read-only context */ }
}

/** Forget the in-memory copy (the browser tests start each world fresh). */
export function resetWards() { wards = null; sweepTimer = 0; }

const same = (w, d, x, y, z) => w.d === d && w.x === x && w.y === y && w.z === z;
const horizontalDistance = (w, x, z) => Math.hypot(w.x + 0.5 - x, w.z + 0.5 - z);
const armTicks = () => Math.round(CONFIG.wardArmingSeconds * 20);
const isArmed = (w) => worldTime() - w.t >= armTicks();

export function wardCount() { return load().length; }

// ---- placement ----

/** The ward that blocks building here, or null. Spacing is the real anti-spam lever: cost alone
 *  never stops a group carpeting a map, a minimum separation does. */
export function blockingNeighbour(d, x, y, z) {
  const spacing = CONFIG.wardMinSpacing;
  if (spacing <= 0) return null;
  return load().find((w) => w.d === d && !same(w, d, x, y, z) && horizontalDistance(w, x + 0.5, z + 0.5) < spacing) || null;
}

export function addWard(d, x, y, z) {
  if (load().some((w) => same(w, d, x, y, z))) return;
  wards.push({ d, x, y, z, t: worldTime() });
  save();
}

export function removeWard(d, x, y, z) {
  const before = load().length;
  wards = wards.filter((w) => !same(w, d, x, y, z));
  if (wards.length !== before) save();
}

/** How long until this idol arms, in whole seconds (rounded UP); null once it protects. */
export function secondsUntilArmed(d, x, y, z) {
  const w = load().find((ward) => same(ward, d, x, y, z));
  if (!w || isArmed(w)) return null;
  return Math.ceil((w.t + armTicks() - worldTime()) / 20);
}

// ---- protection ----

/** Inside an ARMED ward? A CYLINDER: horizontal distance only, so a base is covered bedrock to sky. */
export function isProtected(d, x, z) {
  if (!CONFIG.wardEnabled) return false;
  const list = load();
  if (list.length === 0) return false;
  for (const w of list) {
    if (w.d === d && horizontalDistance(w, x, z) <= CONFIG.wardRadius && isArmed(w)) return true;
  }
  return false;
}

/** An armed ward's circle, in block-centre coordinates. */
export class Zone {
  constructor(x, z, radius) { this.x = x; this.z = z; this.radius = radius; }
  distance(px, pz) { return Math.hypot(px - this.x, pz - this.z); }
}

/** Every armed zone whose edge lies within `margin` of (x, z), or that holds it. */
export function zonesNear(d, x, z, margin) {
  if (!CONFIG.wardEnabled) return [];
  const list = load();
  if (list.length === 0) return [];
  const radius = CONFIG.wardRadius;
  const out = [];
  for (const w of list) {
    if (w.d !== d || horizontalDistance(w, x, z) > radius + margin || !isArmed(w)) continue;
    out.push(new Zone(w.x + 0.5, w.z + 0.5, radius));
  }
  return out;
}

/** The zone holding (x, z) that it sits DEEPEST inside, or null when outside them all. */
export function zoneAt(d, x, z) {
  let best = null, bestDepth = Infinity;
  for (const zone of zonesNear(d, x, z, 0)) {
    const depth = zone.distance(x, z) - zone.radius;
    if (depth < bestDepth) { bestDepth = depth; best = zone; }
  }
  return best;
}

/** Out of the spider's reach: more than BITE_BAND inside an armed zone. Such a player is invisible
 *  to it - no hunt starts on them, a running one breaks off, it can neither bite nor blind them. */
export function isSafe(d, x, z) {
  const zone = zoneAt(d, x, z);
  return !!zone && zone.distance(x, z) < zone.radius - BITE_BAND;
}

// ---- the enraged boss ----

/** Blow apart every idol within `range` of (x, y, z) - the enraged boss's answer to them. It looks
 *  and sounds like an explosion but leaves no crater, and nothing drops: the idol is gone.
 *  Everyone nearby is told. Returns how many went. */
export function shatterNear(dimension, x, y, z, range) {
  const d = dimension.id;
  const hit = load().filter((w) => w.d === d && (w.x + 0.5 - x) ** 2 + (w.y + 0.5 - y) ** 2 + (w.z + 0.5 - z) ** 2 <= range * range);
  if (hit.length === 0) return 0;
  for (const w of hit) {
    const c = { x: w.x + 0.5, y: w.y + 0.5, z: w.z + 0.5 };
    try { dimension.spawnParticle("minecraft:huge_explosion_emitter", c); } catch (e) {}
    for (let i = 0; i < 12; i++) {
      try { dimension.spawnParticle("minecraft:basic_smoke_particle", { x: c.x + (Math.random() - 0.5) * 1.2, y: c.y + (Math.random() - 0.5) * 1.2, z: c.z + (Math.random() - 0.5) * 1.2 }); } catch (e) {}
    }
    try { dimension.playSound("random.explode", c, { volume: 4, pitch: 0.8 }); } catch (e) {}
    try { dimension.playSound("mob.wither.break_block", c, { volume: 2, pitch: 0.7 }); } catch (e) {}
    try {
      const block = dimension.getBlock({ x: w.x, y: w.y, z: w.z });
      if (block && block.typeId === WARD_BLOCK) block.setType("minecraft:air");
    } catch (e) { /* unloaded: the registry entry still goes */ }
    for (const p of world.getAllPlayers()) {
      if (p.dimension.id !== d || (p.location.x - c.x) ** 2 + (p.location.z - c.z) ** 2 > SHATTER_NEWS_RANGE ** 2) continue;
      try { p.sendMessage(`The enraged spider blew apart the Warding Idol at (${w.x}, ${w.y}, ${w.z}). Nowhere is safe from it.`); } catch (e) {}
    }
  }
  wards = wards.filter((w) => !hit.includes(w));
  save();
  return hit.length;
}

// ---- upkeep ----

/** Once a tick: every 10 s, drop any idol whose block is LOADED and gone (another add-on, an edited
 *  world, an explosion with no hook). Unloaded ones are kept on purpose: that is an offline base,
 *  and forgetting it there would quietly un-protect it. */
export function tickWards() {
  fallbackTime++;
  if (++sweepTimer < SWEEP_TICKS) return;
  sweepTimer = 0;
  const list = load();
  if (list.length === 0) return;
  const gone = list.filter((w) => {
    try {
      const block = world.getDimension(w.d).getBlock({ x: w.x, y: w.y, z: w.z });
      return block !== undefined && block.typeId !== WARD_BLOCK;
    } catch (e) { return false; }
  });
  if (gone.length) { wards = list.filter((w) => !gone.includes(w)); save(); }
}
