// The Java mod's config/arachnomod-common.toml, for Bedrock. A Bedrock add-on has no config file, so
// the values live in the WORLD (a dynamic property, saved with it) and are edited in game with
// /spiderconfig (see commands.js) - Java's /spider config. Same key names, defaults and ranges as the
// Java mod (the v1.0-v1.2 keys; where a later Java release changed a default the user asked for -
// the 3-second bite, the per-variant bite - the current Java value is used). Every value is read
// LIVE, so a change applies to the spider on the next tick, as in Java.

import { world } from "@minecraft/server";

const STORE_KEY = "arachno:config";

// key: [default, min, max, description]. Booleans have min/max null.
export const SPEC = {
  // ---- spawning ----
  spawnMinMinutes: [1.0, 0.05, 1440, "Earliest first spawn of a session, in minutes."],
  spawnMaxMinutes: [1.0, 0.05, 1440, "Latest first spawn of a session, in minutes."],
  respawnAfterKillMinutes: [40.0, 0.05, 1440, "How long a slain spider stays gone (40 = two Minecraft days)."],
  peacefulExitSpawnMinutes: [1.0, 0.05, 1440, "How soon it returns after Peaceful is switched off."],
  spawnDistanceMin: [30.0, 4, 128, "Closest it spawns to a player, in blocks."],
  spawnDistanceMax: [34.0, 4, 128, "Farthest it spawns from a player, in blocks."],
  spawnAngleAttempts: [24, 4, 64, "Directions tested for safe ground per spawn distance."],
  spawnMaxVerticalSearch: [48, 4, 384, "How far down the safe-ground scan looks from the surface."],
  camoVariantChance: [0.25, 0, 1, "Chance a spawn is the CAMO variant."],
  // ---- the hunt ----
  chaseDistance: [64.0, 8, 256, "How far it spots and chases a player, in blocks."],
  chaseExitDistanceMultiplier: [1.25, 1, 3, "It gives up only past chaseDistance x this (no flicker at the edge)."],
  alertReactionTicks: [10, 0, 200, "How long it freezes and stares before charging, in ticks."],
  hostileOnlyAtNight: [false, null, null, "Daytime: it cannot see players (a vanilla spider's rules)."],
  chasePathfinding: [true, null, null, "Route around walls and through doorways (false = the straight-line charge)."],
  chaseSpeedBlocksPerSecond: [8.0, 0.5, 40, "Chase speed at its smallest size, in blocks per second (applies at spawn)."],
  speedGrowthFactor: [8.0, 1, 32, "How much faster it is at its largest size."],
  legStepSpeed: [1.1, 0.1, 5, "How fast each leg swings (applies at spawn)."],
  // ---- wandering + idle life ----
  enableWandering: [true, null, null, "Patrol when nobody is around (false = stand still, breathe and groom)."],
  wanderSpeedFactor: [0.35, 0.05, 1, "Patrol speed as a fraction of the chase speed."],
  wanderRadius: [24.0, 4, 128, "How far from its patrol anchor it wanders, in blocks."],
  wanderMinIntervalSeconds: [3.0, 0.5, 120, "Shortest patrol leg or pause, in seconds."],
  wanderMaxIntervalSeconds: [9.0, 0.5, 300, "Longest patrol leg or pause, in seconds."],
  wanderPauseChance: [0.25, 0, 1, "Chance each patrol leg is a pause instead."],
  groomingChance: [0.03, 0, 1, "Per-second chance an idle spider grooms (only with wandering off; 0 disables)."],
  // ---- size ----
  minSize: [0.6, 0.1, 10, "Its size up close."],
  maxSize: [15.0, 0.5, 50, "Its size far away."],
  sizeNearDistance: [4.0, 0, 64, "At or inside this distance it is at minSize, in blocks."],
  sizeFarDistance: [32.0, 1, 128, "At or beyond this distance it is at maxSize, in blocks."],
  growPercentPerTick: [12.0, 0.5, 100, "Fastest it grows, in percent per tick."],
  shrinkPercentPerTick: [25.0, 0.5, 100, "Fastest it shrinks, in percent per tick."],
  squeezeSize: [0.25, 0.1, 1, "How small it shrinks to follow you into a 1x1 hole."],
  riddenSize: [2.0, 0.3, 20, "Its size while you ride it."],
  growInWater: [true, null, null, "Grow just tall enough to ride above deep water instead of drowning."],
  // ---- combat ----
  netheriteMaxHealth: [350.0, 1, 1000000, "Netherite variant health (it also wears a full netherite suit's armour)."],
  camoMaxHealth: [600.0, 1, 1000000, "Camo variant health (no armour)."],
  netheriteAttackDamageHearts: [6.0, 0, 100, "Netherite bite, in hearts (before your armour)."],
  camoAttackDamageHearts: [5.0, 0, 100, "Camo bite, in hearts (before your armour)."],
  attackCooldownTicks: [60, 10, 120, "Ticks between bites (60 = every 3 seconds)."],
  netheriteDropChance: [0.5, 0, 1, "Chance it drops a netherite ingot when slain."],
  // ---- camo sounds ----
  variantStepVolume: [0.3, 0, 10, "Camo footstep volume."],
  variantLandVolume: [1.0, 0, 10, "Camo landing volume."],
  // ---- Bedrock only ----
  debugHud: [false, null, null, "Developer status line (tick rate, cost, AI mode, steps, drag) in the action bar."],
};

// Java IntValue keys (everything else numeric is a DoubleValue).
const INTEGER_KEYS = new Set(["spawnAngleAttempts", "spawnMaxVerticalSearch", "alertReactionTicks", "attackCooldownTicks"]);

/** The live values, read by every system each tick. */
export const CONFIG = {};
for (const [key, [def]] of Object.entries(SPEC)) CONFIG[key] = def;

/** Load the world's saved values over the defaults (unknown keys ignored, ranges enforced). */
export function loadConfig() {
  let saved = {};
  try {
    const raw = world.getDynamicProperty(STORE_KEY);
    if (typeof raw === "string") saved = JSON.parse(raw);
  } catch (e) { saved = {}; }
  for (const [key, value] of Object.entries(saved)) {
    if (key in SPEC) {
      const checked = check(key, value);
      if (checked.ok) CONFIG[key] = checked.value;
    }
  }
}

function save() {
  const changed = {};
  for (const [key, [def]] of Object.entries(SPEC)) if (CONFIG[key] !== def) changed[key] = CONFIG[key];
  try { world.setDynamicProperty(STORE_KEY, JSON.stringify(changed)); } catch (e) { /* read-only context */ }
}

function check(key, value) {
  const [def, min, max] = SPEC[key];
  if (typeof def === "boolean") {
    if (typeof value === "boolean") return { ok: true, value };
    const s = String(value).toLowerCase();
    if (s === "true" || s === "1" || s === "on") return { ok: true, value: true };
    if (s === "false" || s === "0" || s === "off") return { ok: true, value: false };
    return { ok: false, error: `${key} is true or false` };
  }
  const n = typeof value === "number" ? value : Number(String(value).trim());
  if (!Number.isFinite(n)) return { ok: false, error: `${key} needs a number` };
  if (n < min || n > max) return { ok: false, error: `${key} must be between ${min} and ${max}` };
  return { ok: true, value: INTEGER_KEYS.has(key) ? Math.round(n) : n };
}

/** Set one value from a command; saved into the world at once. */
export function setConfig(key, raw) {
  const real = Object.keys(SPEC).find((k) => k.toLowerCase() === String(key).toLowerCase());
  if (!real) return { ok: false, error: `unknown setting '${key}'` };
  const checked = check(real, raw);
  if (!checked.ok) return checked;
  CONFIG[real] = checked.value;
  save();
  return { ok: true, key: real, value: checked.value };
}

export function describe(key) {
  const real = Object.keys(SPEC).find((k) => k.toLowerCase() === String(key).toLowerCase());
  if (!real) return null;
  const [def, min, max, text] = SPEC[real];
  const range = min === null ? "true/false" : `${min}-${max}`;
  return { key: real, value: CONFIG[real], def, range, text };
}
