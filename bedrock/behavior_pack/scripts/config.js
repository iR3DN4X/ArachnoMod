// The Java mod's config/arachnomod-common.toml, for Bedrock. A Bedrock add-on has no config file, so
// the values live in the WORLD (a dynamic property, saved with it) and are edited in game with
// /spiderconfig (see commands.js) - Java's /spider config - or the Spider Config Book's settings
// menu (menu.js), grouped as CATEGORIES below. Same key names, defaults and ranges as the
// Java mod (v1.8.5's values for every key ported so far). Every value is read LIVE, so a change
// applies to the spider on the next tick, as in Java.

import { world } from "@minecraft/server";

const STORE_KEY = "arachno:config";

// key: [default, min, max, description]. Booleans have min/max null.
export const SPEC = {
  // ---- spawning ----
  spawnMinMinutes: [1.0, 0.05, 1440, "Earliest first spawn of a session, in minutes."],
  spawnMaxMinutes: [1.0, 0.05, 1440, "Latest first spawn of a session, in minutes."],
  respawnAfterKillMinutes: [40.0, 0.05, 1440, "How long a slain spider stays gone (40 = two Minecraft days)."],
  permadeath: [false, null, null, "BOSS MODE: once a spider is killed in this world it NEVER comes back (saved with the world; spawn eggs still work)."],
  peacefulExitSpawnMinutes: [1.0, 0.05, 1440, "How soon it returns after Peaceful is switched off."],
  spawnDistanceMin: [30.0, 4, 128, "Closest it spawns to a player, in blocks."],
  spawnDistanceMax: [34.0, 4, 128, "Farthest it spawns from a player, in blocks."],
  spawnAngleAttempts: [24, 4, 1024, "Directions tested for safe ground per spawn distance (raise it for SkyBlock: an island is a thin slice of the circle)."],
  spawnMaxVerticalSearch: [48, 4, 384, "How far down the safe-ground scan looks from the surface."],
  spawnCloseFallbackDistance: [6.0, 0, 128, "SkyBlock last resort: when every ring from spawnDistanceMin out is void, search inward this close (0 disables)."],
  relocateDistanceBlocks: [192.0, 0, 4096, "A living spider this far from every player, with its chunk still loaded, moves near someone (never counts as a kill; 0 disables)."],
  spawnInOverworld: [true, null, null, "May it spawn in (and follow players into) the Overworld? Spawn eggs work anywhere."],
  spawnInNether: [true, null, null, "May it spawn in (and follow players into) the Nether? Spawn eggs work anywhere."],
  spawnInEnd: [true, null, null, "May it spawn in (and follow players into) the End? Spawn eggs work anywhere."],
  hunterVariantChance: [0.15, 0, 1, "Chance a spawn is the pitch-black HUNTER (rolled first)."],
  poisonVariantChance: [0.2, 0, 1, "Chance a spawn is the venomous POISON variant (rolled before camo)."],
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
  enableGrowth: [true, null, null, "false: EVERY spider holds one size, fixedSizeBlocks tall - near or far, in water, ridden (it still shrinks to fit doorways and low ceilings)."],
  fixedSizeBlocks: [2.0, 0.5, 64, "How tall every spider stands, in blocks, when enableGrowth is false."],
  minSize: [0.6, 0.1, 10, "Its size up close."],
  maxSize: [15.0, 0.5, 50, "Its size far away."],
  sizeNearDistance: [4.0, 0, 64, "At or inside this distance it is at minSize, in blocks."],
  sizeFarDistance: [32.0, 1, 128, "At or beyond this distance it is at maxSize, in blocks."],
  growPercentPerTick: [12.0, 0.5, 100, "Fastest it grows, in percent per tick."],
  shrinkPercentPerTick: [25.0, 0.5, 100, "Fastest it shrinks, in percent per tick."],
  squeezeSize: [0.25, 0.1, 1, "How small it shrinks to follow you into a 1x1 hole."],
  riddenSize: [2.0, 0.3, 20, "Its size while you ride it (ignored when enableGrowth is false)."],
  growInWater: [true, null, null, "Grow just tall enough to ride above deep water instead of drowning."],
  poisonSize: [1.0, 0.3, 5, "The POISON variant's fixed size (it never grows with distance; still threads doorways and squeezes)."],
  hunterSize: [1.1, 0.3, 5, "The HUNTER's fixed size - it never grows or shrinks (fits 1x2 doorways as it is)."],
  // ---- the hunter ----
  hunterSpeedMultiplier: [1.6, 1, 8, "The HUNTER's speed (x chase speed) while it moves - and it only moves while nobody looks at it."],
  hunterBlindnessRange: [16.0, 0, 128, "How close the stalking HUNTER blinds you, in blocks (topped up while you stay in range; 0 disables)."],
  hunterBlindnessSeconds: [30.0, 1, 3600, "How long the HUNTER's blindness lasts once you are out of its range, in seconds."],
  // ---- combat ----
  netheriteMaxHealth: [350.0, 1, 1000000, "Netherite variant health (it also wears a full netherite suit's armour)."],
  netheriteArmor: [20.0, 0, 30, "Netherite variant armour points (20 = a full netherite suit; 0 = no armour at all). Applies at spawn."],
  netheriteArmorToughness: [12.0, 0, 20, "Netherite variant armour toughness (12 = a full netherite suit). Applies at spawn."],
  netheriteLegs: [8, 1, 16, "How many legs the NETHERITE spider has (8 = the classic; 1-16 all walk). Applies at spawn."],
  camoMaxHealth: [600.0, 1, 1000000, "Camo variant health (no armour)."],
  poisonMaxHealth: [500.0, 1, 1000000, "Poison variant health (no armour - its danger is the bite)."],
  hunterMaxHealth: [400.0, 1, 1000000, "Hunter variant health (no armour - it was never meant to be seen)."],
  netheriteAttackDamageHearts: [6.0, 0, 100, "Netherite bite, in hearts (before your armour)."],
  camoAttackDamageHearts: [5.0, 0, 100, "Camo bite, in hearts (before your armour)."],
  hunterAttackDamageHearts: [4.0, 0, 100, "Hunter bite, in hearts (before your armour) - it takes your sight, not your health."],
  poisonAttackDamageHearts: [3.0, 0, 100, "Poison bite, in hearts (before your armour) - every bite also injects Poison II."],
  poisonEffectSeconds: [30.0, 0, 3600, "How long the poison bite's Poison II lasts, in seconds."],
  attackCooldownTicks: [60, 10, 120, "Ticks between bites (60 = every 3 seconds)."],
  netheriteDropChance: [0.5, 0, 1, "Chance it drops a netherite ingot when slain."],
  // ---- the enraged boss ----
  enragedMaxHealth: [700.0, 1, 1000000, "Health of an ENRAGED netherite spider (feed the netherite variant a netherite ingot; it keeps its armour)."],
  enragedSpeedMultiplier: [1.5, 1, 8, "Chase-speed multiplier while enraged."],
  enragedAttackDamageHearts: [10.0, 0, 100, "The enraged boss's bite, in hearts (before your armour). It always drops a FULL netherite block."],
  // ---- the Warding Idol ----
  wardEnabled: [true, null, null, "Are WARDING IDOLS active? Each makes a SAFE ZONE wardRadius blocks round it: no spawns, no hunting or biting anyone more than 1 block inside."],
  wardRadius: [128.0, 8, 512, "How far a Warding Idol reaches, in blocks, horizontally (a cylinder: bedrock to sky)."],
  wardMinSpacing: [256.0, 0, 4096, "Minimum distance between two idols, in blocks (one placed too close is handed back; 0 = no limit)."],
  wardArmingSeconds: [5.0, 0, 86400, "Seconds a freshly placed idol takes to wake up (0 = at once)."],
  // ---- tools ----
  laserPointerRange: [64.0, 4, 256, "How far the SPIDER LASER POINTER throws its dot, in blocks (creative only)."],
  laserPointerLureSeconds: [4.0, 0.5, 120, "How long the spider chases the dot per click, in seconds - and it can't bite while it does."],
  giveConfigBook: [true, null, null, "Hand every player who may change these settings (operators, and the owner of a single-player world) a SPIDER CONFIG BOOK the first time they join. Use it for a menu with every setting, its description and a reset."],
  // ---- variant sounds ----
  variantStepVolume: [0.3, 0, 10, "Camo and poison footstep volume."],
  variantLandVolume: [1.0, 0, 10, "Camo and poison landing volume."],
  // ---- Bedrock only ----
  debugHud: [false, null, null, "Developer status line (tick rate, cost, AI mode, steps, drag) in the action bar."],
};

// The settings menu's groups: Java's config book categories (the `section`s of Config.kt), in its
// order, with Bedrock's own keys where they belong (the three spawnIn switches stand in for Java's
// dimension lists; debugHud joins the tools). Every SPEC key is in exactly one.
export const CATEGORIES = [
  ["Spawning", ["spawnMinMinutes", "spawnMaxMinutes", "peacefulExitSpawnMinutes", "respawnAfterKillMinutes", "permadeath",
    "spawnInOverworld", "spawnInNether", "spawnInEnd", "spawnDistanceMin", "spawnDistanceMax", "relocateDistanceBlocks",
    "spawnAngleAttempts", "spawnCloseFallbackDistance", "spawnMaxVerticalSearch", "camoVariantChance", "poisonVariantChance",
    "hunterVariantChance"]],
  ["Warding Idol", ["wardEnabled", "wardRadius", "wardMinSpacing", "wardArmingSeconds"]],
  ["Tools & items", ["laserPointerRange", "laserPointerLureSeconds", "giveConfigBook", "debugHud"]],
  ["Chase & speed", ["chaseDistance", "chaseExitDistanceMultiplier", "alertReactionTicks", "hostileOnlyAtNight", "chasePathfinding",
    "chaseSpeedBlocksPerSecond", "speedGrowthFactor", "legStepSpeed"]],
  ["Wandering", ["enableWandering", "wanderSpeedFactor", "wanderRadius", "wanderMinIntervalSeconds", "wanderMaxIntervalSeconds",
    "wanderPauseChance", "groomingChance"]],
  ["Size & growth", ["minSize", "squeezeSize", "maxSize", "sizeNearDistance", "sizeFarDistance", "growPercentPerTick",
    "shrinkPercentPerTick", "riddenSize", "growInWater", "enableGrowth", "fixedSizeBlocks"]],
  ["Combat & drops", ["netheriteMaxHealth", "netheriteArmor", "netheriteArmorToughness", "netheriteLegs", "camoMaxHealth",
    "poisonMaxHealth", "poisonSize", "hunterMaxHealth", "hunterSize", "hunterSpeedMultiplier", "hunterBlindnessRange",
    "hunterBlindnessSeconds", "netheriteAttackDamageHearts", "camoAttackDamageHearts", "hunterAttackDamageHearts",
    "poisonAttackDamageHearts", "poisonEffectSeconds", "attackCooldownTicks", "netheriteDropChance", "enragedMaxHealth",
    "enragedSpeedMultiplier", "enragedAttackDamageHearts"]],
  ["Variant sounds", ["variantStepVolume", "variantLandVolume"]],
];

// Java IntValue keys (everything else numeric is a DoubleValue).
const INTEGER_KEYS = new Set(["spawnAngleAttempts", "spawnMaxVerticalSearch", "alertReactionTicks", "attackCooldownTicks", "netheriteLegs"]);

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

/** Validate (and round, for whole-number settings) a value without saving it. */
export function check(key, value) {
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

const listeners = [];

/** Be told the key of every setting that changes (Java's onConfigSet: the spawn timer re-arms). */
export function onConfigChange(fn) { listeners.push(fn); }

/** Set one value from a command; saved into the world at once. */
export function setConfig(key, raw) {
  const real = Object.keys(SPEC).find((k) => k.toLowerCase() === String(key).toLowerCase());
  if (!real) return { ok: false, error: `unknown setting '${key}'` };
  const checked = check(real, raw);
  if (!checked.ok) return checked;
  CONFIG[real] = checked.value;
  save();
  for (const fn of listeners) { try { fn(real); } catch (e) {} }
  return { ok: true, key: real, value: checked.value };
}

export function describe(key) {
  const real = Object.keys(SPEC).find((k) => k.toLowerCase() === String(key).toLowerCase());
  if (!real) return null;
  const [def, min, max, text] = SPEC[real];
  const range = min === null ? "true/false" : `${min}-${max}`;
  return { key: real, value: CONFIG[real], def, range, text };
}
