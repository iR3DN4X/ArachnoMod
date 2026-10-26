// Port of entity/SpiderSpawnManager.kt: keeps exactly ONE spider alive in the world.
//
//  - The FIRST spider of a session comes spawnMinMinutes-spawnMaxMinutes in (default ~1 minute).
//  - After a KILL the next one comes respawnAfterKillMinutes later (default 40 = two Minecraft days).
//  - Peaceful removes it and pauses spawning; it returns peacefulExitSpawnMinutes after peace ends
//    (but toggling Peaceful never shortcuts a kill's cooldown).
//  - Changing one of those timings re-arms the countdown already running, crediting the time
//    served (Java v1.3.2): a new timer applies at once, not after a relog.
//  - If it is lost alive (its chunk unloaded under it) a fresh one is dropped near the nearest
//    player at once; if every player has left its dimension it FOLLOWS them; and if it is still
//    loaded but relocateDistanceBlocks from everyone for 10 s, it moves near someone (Java v1.8.2).
//    None of these ever counts as a kill.
//  - In the NETHER only, where clipping through walls and the bedrock ceiling strands it, a spider
//    up on the roof or out of range TELEPORTS straight to the nearest player - the same spider.
//  - spawnInOverworld / spawnInNether / spawnInEnd say where it may spawn and follow players to
//    (Java v1.8.5's dimension filter - Bedrock has just these three, so three switches instead of
//    Java's id lists). Spawn eggs work anywhere.
//  - Spawns are SAFE: a rolled distance in [spawnDistanceMin, spawnDistanceMax], tested in
//    spawnAngleAttempts directions on solid, dry ground (SafeGroundFinder), rings nearest the
//    rolled distance first (ties farther), widening OUTWARD only - it never pops up in your face.
//    Only on a SkyBlock island, where everything from spawnDistanceMin out is void, a last-resort
//    sweep comes INWARD to spawnCloseFallbackDistance (Java v1.8.4).
//  - PERMADEATH (off by default): one kill in a world ends natural spawning there for good.
// The Java original also never SAVES the spider with the world; Bedrock always does, so main.js
// removes any spider it finds left over from a previous session, which has the same effect.

import { world } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { BlockWorld } from "./blocks.js";
import { groundYAt, SPAWN_CEILING_MARGIN } from "./ground.js";
import { rollVariant } from "./spider.js";
import { isPeacefulDifficulty, isSpectator } from "./enums.js";
import { isProtected } from "./wards.js";

const RETRY_TICKS = 10 * 20;          // re-try 10 s later when no safe spot was found
const KILLS_KEY = "arachno:kills";    // world dynamic property: spiders slain in this world
const FOLLOW_GRACE_TICKS = 100;       // 5 s: a quick portal peek doesn't yo-yo it
const STRANDED_GRACE_TICKS = 200;     // 10 s: sprinting off and straight back doesn't move it
const NETHER_GRACE_TICKS = 20;        // 1 s stuck or out of range in the Nether before it teleports
const LANDING_DISTANCES = [4, 3, 5, 6, 2];   // a teleport lands this close to you, on safe ground
const RING_STEP = 2.0;
// The SkyBlock sweep steps one block at a time: a 2-block stride was measured hopping straight
// over the only usable ring of a small island (Java's skyblock fix; most Java builds kept 2).
const INWARD_STEP = 1.0;

// What the armed countdown IS, so a live config change can recompute the right one.
const Schedule = { NONE: "NONE", FIRST_SPAWN: "FIRST_SPAWN", KILL_COOLDOWN: "KILL_COOLDOWN", PEACEFUL_EXIT: "PEACEFUL_EXIT", RETRY: "RETRY" };

const DIMENSION_SWITCH = {
  "minecraft:overworld": "spawnInOverworld",
  "minecraft:nether": "spawnInNether",
  "minecraft:the_end": "spawnInEnd",
};

/** May the spider naturally spawn in - or follow players into - this dimension? */
export function dimensionAllowed(dimensionId) {
  const key = DIMENSION_SWITCH[dimensionId];
  return key ? CONFIG[key] !== false : true;
}

const minutesToTicks = (m) => Math.max(1, Math.round(m * 1200));

/** The FIRST-spawn delay: a roll between spawnMinMinutes and spawnMaxMinutes. */
function rollFirstSpawn() {
  const min = minutesToTicks(CONFIG.spawnMinMinutes);
  const max = Math.max(min, minutesToTicks(CONFIG.spawnMaxMinutes));
  return min + Math.floor(Math.random() * (max - min + 1));
}

export class SpawnManager {
  /** host: { spawnSpider(dimension, x, y, z, variant) -> controller|null, removeSpider(controller) } */
  constructor(host) {
    this.host = host;
    this.current = null;
    this.respawnTimer = -1;           // ticks until the next spawn; < 0 = not scheduled
    this.scheduleKind = Schedule.NONE;
    this.scheduleElapsed = 0;         // ticks of the armed countdown already served
    this.pendingPeacefulExit = false;
    this.abandonedTicks = 0;
    this.strandedTicks = 0;
    this.netherTicks = 0;
    this.lost = null;                 // { dimension, position }: relocate a spider lost alive
    this.wardedPlayers = new Set();   // ids of players standing in a Warding Idol's zone
  }

  /** Refill whatever countdown is running. Stepping into a ward should mean the hunt restarts from
   *  the top when you leave, not that it resumes mid-way and jumps you at the door. */
  rearmCurrentSchedule() {
    if (this.respawnTimer < 0) return;
    let total;
    if (this.scheduleKind === Schedule.FIRST_SPAWN) total = rollFirstSpawn();
    else if (this.scheduleKind === Schedule.KILL_COOLDOWN) total = minutesToTicks(CONFIG.respawnAfterKillMinutes);
    else if (this.scheduleKind === Schedule.PEACEFUL_EXIT) total = minutesToTicks(CONFIG.peacefulExitSpawnMinutes);
    else return;
    this.respawnTimer = total;
    this.scheduleElapsed = 0;
  }

  /** Watch for players crossing INTO a ward, and refill the countdown when they do. */
  tickWardEntries(players) {
    if (!CONFIG.wardEnabled) { this.wardedPlayers.clear(); return; }
    for (const p of players) {
      const inside = isProtected(p.dimension.id, p.location.x, p.location.z);
      const was = this.wardedPlayers.has(p.id);
      if (inside && !was) { this.wardedPlayers.add(p.id); this.rearmCurrentSchedule(); }
      else if (!inside && was) this.wardedPlayers.delete(p.id);
    }
  }

  arm(ticks, kind) {
    this.respawnTimer = ticks;
    this.scheduleKind = kind;
    this.scheduleElapsed = 0;
  }

  /** A setting changed (config.js onConfigChange). If it governs the countdown that is running,
   *  re-arm it from the new value, CREDITING the time already served: raising a half-elapsed
   *  cooldown extends it by the difference, lowering it below the time served spawns promptly. */
  onConfigSet(key) {
    if (this.respawnTimer < 0) return;
    let total;
    if (this.scheduleKind === Schedule.FIRST_SPAWN && (key === "spawnMinMinutes" || key === "spawnMaxMinutes")) total = rollFirstSpawn();
    else if (this.scheduleKind === Schedule.KILL_COOLDOWN && key === "respawnAfterKillMinutes") total = minutesToTicks(CONFIG.respawnAfterKillMinutes);
    else if (this.scheduleKind === Schedule.PEACEFUL_EXIT && key === "peacefulExitSpawnMinutes") total = minutesToTicks(CONFIG.peacefulExitSpawnMinutes);
    else return;
    this.respawnTimer = Math.max(1, total - this.scheduleElapsed);
  }

  /** Every spider registers here on adoption: a second one (a spawn egg) replaces the first. */
  notifyAlive(controller) {
    const existing = this.current;
    if (existing && existing !== controller && existing.alive) this.host.removeSpider(existing);
    this.current = controller;
    this.respawnTimer = -1;
    this.scheduleKind = Schedule.NONE;
    this.lost = null;
  }

  // ---- Permadeath: the one thing that must outlive the session (Java v1.6.0) ----
  // Every timer here lives in memory, which is fine for a cooldown but useless for "killed for
  // good" - a relog would resurrect it. So the kill tally is saved in the WORLD (a dynamic property).

  /** How many spiders have been slain in this world (saved with it). */
  kills() {
    if (this.killCount === undefined) {
      let saved = 0;
      try { saved = Number(world.getDynamicProperty(KILLS_KEY)) || 0; } catch (e) { saved = 0; }
      this.killCount = saved;
    }
    return this.killCount;
  }

  /** A spider was genuinely KILLED (main.js's entityDie, never a despawn, unload or follow). With
   *  permadeath on, this is the last one that will ever hunt this world. */
  recordKill() {
    this.killCount = this.kills() + 1;
    try { world.setDynamicProperty(KILLS_KEY, this.killCount); } catch (e) { /* read-only context */ }
  }

  /** Slain: the long post-kill cooldown - unless this was Peaceful's own despawn. */
  notifyKilled(controller) {
    if (controller !== this.current) return;
    this.current = null;
    if (this.pendingPeacefulExit) this.respawnTimer = -1;
    else this.arm(minutesToTicks(CONFIG.respawnAfterKillMinutes), Schedule.KILL_COOLDOWN);
  }

  /** Gone while still alive, and not by our hand (its chunk unloaded): replace it near a player. */
  notifyLost(controller) {
    if (controller !== this.current) return;
    this.current = null;
    this.lost = { dimension: controller.dimension, position: controller.body.position.copy() };
  }

  tick() {
    // Peaceful: monsters don't exist - it despawns, and spawning pauses.
    if (isPeaceful()) {
      this.pendingPeacefulExit = true;
      if (this.current) {
        const c = this.current;
        this.current = null;
        this.host.removeSpider(c);
      }
      this.lost = null;
      return;
    }

    const players = world.getAllPlayers();
    this.tickWardEntries(players);
    // Only players standing somewhere the spider may hunt are spawn anchors - not in a dimension
    // the switches rule out, and not inside a Warding Idol's zone.
    const eligible = players.filter((p) => dimensionAllowed(p.dimension.id) &&
      !isProtected(p.dimension.id, p.location.x, p.location.z));
    const cur = this.current;
    if (cur) {
      this.respawnTimer = -1;
      this.pendingPeacefulExit = false;   // it survived peaceful; nothing owed
      // A spider stuck inside a ward's zone, making no headway out (walled in, say), is moved
      // along the old way, alive. (SpiderAI fences and repels it; this is only the last resort.)
      if (cur.ai.repelStalled() && !cur.currentRider()) {
        this.follow(cur, eligible);
        return;
      }
      const company = players.filter((p) => p.dimension.id === cur.dimension.id);
      // DIMENSION FOLLOWING: every player has left its dimension -> it goes where the hunt is
      // (only ever into a dimension it is allowed in).
      if (company.length === 0 && eligible.length > 0) {
        if (++this.abandonedTicks >= FOLLOW_GRACE_TICKS) {
          this.abandonedTicks = 0;
          this.follow(cur, eligible);
        }
        return;
      }
      this.abandonedTicks = 0;
      // THE NETHER (only): clipping through its walls and its bedrock ceiling can leave the spider
      // somewhere it can never get back from - up on the roof, or simply out of range. There it
      // TELEPORTS to the nearest player: the very same spider (variant, health, rage, taming), never
      // a kill. Falling out of the BOTTOM of the world is left alone: that is how you kill it.
      // (Never to a spectator, nor to someone sheltering in a Warding Idol's zone.)
      const huntable = company.filter((p) => !isSpectator(p) && !isProtected(p.dimension.id, p.location.x, p.location.z));
      if (cur.dimension.id === "minecraft:nether" && !cur.fallingOutOfWorld() && huntable.length > 0 &&
          (cur.stuckInNetherRoof(huntable) || cur.outOfRangeOf(huntable))) {
        if (++this.netherTicks >= NETHER_GRACE_TICKS) {
          this.netherTicks = 0;
          const below = huntable.filter((p) => p.location.y < 126);   // off the roof, if anyone is
          const pool = below.length ? below : huntable;
          const at = cur.body.position;
          const target = pool.reduce((a, p) => (dist2(p.location, at) < dist2(a.location, at) ? p : a), pool[0]);
          cur.teleportTo(landingNear(target));
        }
        return;
      }
      this.netherTicks = 0;
      // STRANDED BUT STILL LOADED: walking far away normally unloads its chunk (notifyLost
      // relocates it at once), but something can keep the chunk loaded - the spawn chunks, a
      // ticking area, another player elsewhere on a server - and it would idle out of play forever.
      const limit = CONFIG.relocateDistanceBlocks;
      if (company.length === 0 || limit <= 0) { this.strandedTicks = 0; return; }
      const at = cur.body.position;
      const closestSq = Math.min(...company.map((p) => dist2(p.location, at)));
      if (closestSq <= limit * limit) { this.strandedTicks = 0; return; }
      if (++this.strandedTicks >= STRANDED_GRACE_TICKS) {
        this.strandedTicks = 0;
        if (eligible.length > 0) this.follow(cur, eligible);
      }
      return;
    }
    this.abandonedTicks = 0;
    this.strandedTicks = 0;

    if (this.lost) {
      const { dimension, position } = this.lost;
      this.lost = null;
      if (eligible.length === 0) { this.arm(RETRY_TICKS, Schedule.RETRY); return; }
      const near = eligible.filter((p) => p.dimension.id === dimension.id);
      const pool = near.length ? near : eligible;
      const nearest = pool.reduce((a, p) => (dist2(p.location, position) < dist2(a.location, position) ? p : a), pool[0]);
      if (!this.trySpawnNear(nearest)) this.arm(RETRY_TICKS, Schedule.RETRY);
      return;
    }

    // PERMADEATH: it was slain in this world and the hunt is over. Natural spawning stops for
    // good; a spawn egg still works for anyone who wants one back. (A spider lost ALIVE, above, is
    // still put back - it was never killed.)
    if (CONFIG.permadeath && this.kills() > 0) {
      this.respawnTimer = -1;
      return;
    }

    // No one to spawn near - nobody online, or everyone is somewhere the dimension switches rule
    // out. The timer pauses rather than burns.
    if (eligible.length === 0) return;

    // Peaceful was just switched off: fast-track ONLY when nothing is already scheduled (the
    // spider vanished because of Peaceful itself); a kill's cooldown is never shortened.
    if (this.pendingPeacefulExit) {
      this.pendingPeacefulExit = false;
      if (this.respawnTimer < 0) {
        this.arm(minutesToTicks(CONFIG.peacefulExitSpawnMinutes), Schedule.PEACEFUL_EXIT);
        return;
      }
    }

    if (this.respawnTimer < 0) {
      // Nothing yet this session: the FIRST-spawn roll.
      this.arm(rollFirstSpawn(), Schedule.FIRST_SPAWN);
      return;
    }
    this.scheduleElapsed++;
    if (--this.respawnTimer > 0) return;
    if (this.trySpawnNear(pick(eligible))) {
      this.respawnTimer = -1;
      this.scheduleKind = Schedule.NONE;
    } else {
      this.arm(RETRY_TICKS, Schedule.RETRY);
    }
  }

  /** Move a living spider to a fresh safe spot near an eligible player. Removed ALIVE and detached
   *  first, so it never reads as a kill: no trophy, no long cooldown, no permadeath tick. */
  follow(cur, eligible) {
    this.current = null;
    this.host.removeSpider(cur);
    if (eligible.length === 0 || !this.trySpawnNear(pick(eligible))) this.arm(RETRY_TICKS, Schedule.RETRY);
  }

  /** SAFE SPAWNING near one player (see the header). True when a spider was spawned. */
  trySpawnNear(player) {
    const dimension = player.dimension;
    const blocks = new BlockWorld(dimension);
    const p = player.location;
    const minDistance = CONFIG.spawnDistanceMin;
    const maxDistance = Math.max(minDistance, CONFIG.spawnDistanceMax);
    const chosen = minDistance + Math.random() * (maxDistance - minDistance);
    const angles = CONFIG.spawnAngleAttempts;

    // Stages 1 + 2: distances inside the band, nearest to the rolled one first, ties farther.
    const candidates = [chosen];
    for (let offset = RING_STEP; chosen + offset <= maxDistance || chosen - offset >= minDistance; offset += RING_STEP) {
      if (chosen + offset <= maxDistance) candidates.push(chosen + offset);
      if (chosen - offset >= minDistance) candidates.push(chosen - offset);
    }
    for (const distance of candidates) {
      const spot = findSafeSpot(blocks, p, distance, angles);
      if (spot) return this.spawnAt(dimension, spot);
    }
    // Stage 3: the whole band is unsafe - widen OUTWARD, never closer than spawnDistanceMin.
    let ring = maxDistance + RING_STEP;
    for (let i = 0; i < 16; i++, ring += RING_STEP) {
      const spot = findSafeSpot(blocks, p, ring, angles);
      if (spot) return this.spawnAt(dimension, spot);
    }
    // Stage 4: SKYBLOCK. Everything from spawnDistanceMin outwards is void, and on a skybase that
    // is the normal state of the world - the only ground is the island under the player, usually
    // INSIDE the minimum. Having exhausted every polite option, sweep INWARD, farthest first, so it
    // still takes the farthest spot the island offers. Off when the distance is 0.
    const closeLimit = CONFIG.spawnCloseFallbackDistance;
    if (closeLimit > 0 && closeLimit < minDistance) {
      for (let inward = minDistance - INWARD_STEP; inward >= closeLimit; inward -= INWARD_STEP) {
        const spot = findSafeSpot(blocks, p, inward, angles);
        if (spot) return this.spawnAt(dimension, spot);
      }
    }
    return false;
  }

  spawnAt(dimension, spot) {
    const controller = this.host.spawnSpider(dimension, spot.x, spot.y, spot.z, rollVariant());
    if (!controller) return false;
    this.notifyAlive(controller);
    return true;
  }
}

/** Test `angles` evenly spread directions (random phase) at one distance; the first safe one wins.
 *  groundYAt searches around the player's altitude under a ceiling (the Nether). */
function findSafeSpot(blocks, p, distance, angles) {
  const phase = Math.random() * 2 * Math.PI;
  for (let i = 0; i < angles; i++) {
    const angle = phase + (2 * Math.PI / angles) * i;
    const x = p.x + Math.cos(angle) * distance;
    const z = p.z + Math.sin(angle) * distance;
    // Never drop one inside a ward, even when anchoring on an unprotected player nearby.
    if (isProtected(blocks.dimension.id, x, z)) continue;
    const y = groundYAt(blocks, x, z, p.y, SPAWN_CEILING_MARGIN);
    if (y !== null) return { x, y, z };
  }
  return null;
}

/** Where a teleported spider lands: safe ground a few blocks from the player, else at their feet. */
function landingNear(player) {
  const blocks = new BlockWorld(player.dimension);
  const p = player.location;
  for (const distance of LANDING_DISTANCES) {
    const spot = findSafeSpot(blocks, p, distance, 16);
    if (spot) return spot;
  }
  return { x: p.x, y: p.y, z: p.z };
}

export function isPeaceful() {
  return isPeacefulDifficulty();
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const dist2 = (a, b) => (a.x - b.x) ** 2 + (a.y - b.y) ** 2 + (a.z - b.z) ** 2;
