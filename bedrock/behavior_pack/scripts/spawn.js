// Port of entity/SpiderSpawnManager.kt: keeps exactly ONE spider alive in the world.
//
//  - The FIRST spider of a session comes spawnMinMinutes-spawnMaxMinutes in (default ~1 minute).
//  - After a KILL the next one comes respawnAfterKillMinutes later (default 40 = two Minecraft days).
//  - Peaceful removes it and pauses spawning; it returns peacefulExitSpawnMinutes after peace ends
//    (but toggling Peaceful never shortcuts a kill's cooldown).
//  - If it is lost alive (its chunk unloaded under it) a fresh one is dropped near the nearest
//    player at once; if every player has left its dimension it FOLLOWS them (any dimension).
//  - Spawns are SAFE: a rolled distance in [spawnDistanceMin, spawnDistanceMax], tested in
//    spawnAngleAttempts directions on solid, dry ground (SafeGroundFinder), rings nearest the
//    rolled distance first (ties farther), widening OUTWARD only - it never pops up in your face.
// The Java original also never SAVES the spider with the world; Bedrock always does, so main.js
// removes any spider it finds left over from a previous session, which has the same effect.

import { world } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { BlockWorld } from "./blocks.js";
import { groundYAt } from "./ground.js";
import { Variant } from "./spider.js";
import { isPeacefulDifficulty } from "./enums.js";

const RETRY_TICKS = 10 * 20;          // re-try 10 s later when no safe spot was found
const FOLLOW_GRACE_TICKS = 100;       // 5 s: a quick portal peek doesn't yo-yo it
const RING_STEP = 2.0;

const minutesToTicks = (m) => Math.max(1, Math.round(m * 1200));

export class SpawnManager {
  /** host: { spawnSpider(dimension, x, y, z, variant) -> controller|null, removeSpider(controller) } */
  constructor(host) {
    this.host = host;
    this.current = null;
    this.respawnTimer = -1;           // ticks until the next spawn; < 0 = not scheduled
    this.pendingPeacefulExit = false;
    this.abandonedTicks = 0;
    this.lost = null;                 // { dimension, position }: relocate a spider lost alive
  }

  /** Every spider registers here on adoption: a second one (a spawn egg) replaces the first. */
  notifyAlive(controller) {
    const existing = this.current;
    if (existing && existing !== controller && existing.alive) this.host.removeSpider(existing);
    this.current = controller;
    this.respawnTimer = -1;
    this.lost = null;
  }

  /** Slain: the long post-kill cooldown - unless this was Peaceful's own despawn. */
  notifyKilled(controller) {
    if (controller !== this.current) return;
    this.current = null;
    this.respawnTimer = this.pendingPeacefulExit ? -1 : minutesToTicks(CONFIG.respawnAfterKillMinutes);
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
    const cur = this.current;
    if (cur) {
      this.respawnTimer = -1;
      this.pendingPeacefulExit = false;   // it survived peaceful; nothing owed
      // DIMENSION FOLLOWING: every player has left its dimension -> it goes where the hunt is.
      const hasCompany = players.some((p) => p.dimension.id === cur.dimension.id);
      if (hasCompany || players.length === 0) {
        this.abandonedTicks = 0;
      } else if (++this.abandonedTicks >= FOLLOW_GRACE_TICKS) {
        this.abandonedTicks = 0;
        this.current = null;              // detach BEFORE removing: not a kill, no cooldown
        this.host.removeSpider(cur);
        if (!this.trySpawnNear(pick(players))) this.respawnTimer = RETRY_TICKS;
      }
      return;
    }
    this.abandonedTicks = 0;
    if (players.length === 0) return;      // no one to spawn near: the timer pauses

    if (this.lost) {
      const { dimension, position } = this.lost;
      this.lost = null;
      const near = players.filter((p) => p.dimension.id === dimension.id);
      const pool = near.length ? near : players;
      const nearest = pool.reduce((a, p) => (dist2(p.location, position) < dist2(a.location, position) ? p : a), pool[0]);
      if (!this.trySpawnNear(nearest)) this.respawnTimer = RETRY_TICKS;
      return;
    }

    // Peaceful was just switched off: fast-track ONLY when nothing is already scheduled (the
    // spider vanished because of Peaceful itself); a kill's cooldown is never shortened.
    if (this.pendingPeacefulExit) {
      this.pendingPeacefulExit = false;
      if (this.respawnTimer < 0) {
        this.respawnTimer = minutesToTicks(CONFIG.peacefulExitSpawnMinutes);
        return;
      }
    }

    if (this.respawnTimer < 0) {
      // Nothing yet this session: the FIRST-spawn roll.
      const min = minutesToTicks(CONFIG.spawnMinMinutes);
      const max = Math.max(min, minutesToTicks(CONFIG.spawnMaxMinutes));
      this.respawnTimer = min + Math.floor(Math.random() * (max - min + 1));
      return;
    }
    if (--this.respawnTimer > 0) return;
    this.respawnTimer = this.trySpawnNear(pick(players)) ? -1 : RETRY_TICKS;
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
    return false;
  }

  spawnAt(dimension, spot) {
    const variant = Math.random() < CONFIG.camoVariantChance ? Variant.CAMO : Variant.NETHERITE;
    const controller = this.host.spawnSpider(dimension, spot.x, spot.y, spot.z, variant);
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
    const y = groundYAt(blocks, x, z, p.y);
    if (y !== null) return { x, y, z };
  }
  return null;
}

export function isPeaceful() {
  return isPeacefulDifficulty();
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const dist2 = (a, b) => (a.x - b.x) ** 2 + (a.y - b.y) ** 2 + (a.z - b.z) ** 2;
