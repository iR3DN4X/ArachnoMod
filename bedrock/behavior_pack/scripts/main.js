// ArachnoMod for Bedrock - the Netherite Octoarachnopod.
//
// The leg engine (IK chains, gait, body physics) is a line-for-line port of the Java mod's shared
// engine, verified tick for tick against it, plus the Bedrock-only gait fixes in engine/. The rest
// ports the Java mod's first updates (v1.0 - v1.2):
//   spider.js   the mob: health per variant, the bite, taming + riding, the trophy, camo, lava
//   spawn.js    exactly one spider: first spawn ~1 min in, 40 min after a kill, Peaceful,
//               safe-ground spawning, relocation, following you between dimensions
//   ai.js       wander / alert / chase, route planning, the squeeze      (SpiderAI.kt)
//   mob.js      size with distance, in water, through doorways          (SpiderMob.kt)
//   config.js   every setting, saved with the world                      (Config.kt)
//   commands.js /spider and /spiderconfig                                 (the /spider tree)

import { world, system, ItemStack } from "@minecraft/server";
import { Vec3, DOWN } from "./engine/vec.js";
import { SpiderBody } from "./engine/body.js";
import { netheriteSpider } from "./engine/presets.js";
import { applyBehaviour, TargetBehaviour } from "./engine/behaviour.js";
import { BedrockLevel } from "./platform.js";
import { SpiderRenderer, sweepOrphans, isValid } from "./render.js";
import { CONFIG, loadConfig } from "./config.js";
import { scaleToSpeedFactor } from "./mob.js";
import { SpiderController, SPIDER_TYPE, TAMER_ITEM, Variant } from "./spider.js";
import { SpawnManager } from "./spawn.js";
import { registerCommands } from "./commands.js";
import { isCreative } from "./enums.js";

const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];
const controllers = new Map();      // mob entity id -> SpiderController
const personal = new Map();         // player id -> { player, body, level, renderer }
const spiders = [];                 // the live controllers, for the browser test tools

// ---------------------------------------------------------------- spiders in and out of the world

const spawnManager = new SpawnManager({
  spawnSpider(dimension, x, y, z, variant) {
    let entity;
    try { entity = dimension.spawnEntity(SPIDER_TYPE, { x, y, z }); } catch (e) { return null; }
    try { entity.setRotation({ x: 0, y: Math.random() * 360 }); } catch (e) {}
    return adopt(entity, variant, false);
  },
  removeSpider(controller) { removeSpider(controller); },
});

/** Give a spider mob its body. `register` = tell the spawn manager (it does that itself for its own). */
function adopt(entity, variant, register = true) {
  if (controllers.has(entity.id)) return controllers.get(entity.id);
  const controller = new SpiderController(entity, variant);
  controllers.set(entity.id, controller);
  spiders.push(controller);
  if (register) spawnManager.notifyAlive(controller);
  return controller;
}

/** Take a spider out of play alive (no trophy, no kill cooldown): legs and mob both go. */
function removeSpider(controller) {
  controller.detached = true;
  forget(controller);
  try { if (isValid(controller.entity)) controller.entity.remove(); } catch (e) {}
}

function forget(controller) {
  controller.dispose();
  controllers.delete(controller.id);
  const i = spiders.indexOf(controller);
  if (i >= 0) spiders.splice(i, 1);
}

const rollVariant = () => (Math.random() < CONFIG.camoVariantChance ? Variant.CAMO : Variant.NETHERITE);

// A spider placed by a spawn egg or /summon: adopt it (rolling the camo chance, as Java's spawn egg
// does). The one already in the world is replaced - there is only ever one.
world.afterEvents.entitySpawn.subscribe((ev) => {
  const e = ev.entity;
  if (!isValid(e) || e.typeId !== SPIDER_TYPE || controllers.has(e.id)) return;
  adopt(e, rollVariant());
});

// A spider loaded back in from the save is a leftover of an earlier session or of a chunk it was
// lost in (Java never saves the spider at all): remove it. The spawn manager keeps the real one.
world.afterEvents.entityLoad.subscribe((ev) => {
  const e = ev.entity;
  if (isValid(e) && e.typeId === SPIDER_TYPE && !controllers.has(e.id)) {
    try { e.remove(); } catch (err) {}
  }
});

// Slain: the trophy, then the long cooldown.
world.afterEvents.entityDie.subscribe((ev) => {
  const e = ev.deadEntity;
  if (!e || e.typeId !== SPIDER_TYPE) return;
  const controller = controllers.get(e.id);
  if (!controller) return;
  controller.dead = true;
  controller.dropTrophy((dimension, item, at) => {
    try { dimension.spawnItem(new ItemStack(item, 1), at); } catch (err) {}
  });
  forget(controller);
  spawnManager.notifyKilled(controller);
});

// Damage: the netherite variant's armour suit and fire immunity; neither variant takes fall damage
// (it never falls: its position is the simulation's) or suffocates in the blocks it reaches into.
world.beforeEvents.entityHurt.subscribe((ev) => {
  const e = ev.hurtEntity;
  if (!e || e.typeId !== SPIDER_TYPE) return;
  const cause = ev.damageSource ? ev.damageSource.cause : "";
  if (cause === "fall" || cause === "suffocation" || cause === "flyIntoWall") { ev.cancel = true; return; }
  const controller = controllers.get(e.id);
  if (!controller) return;
  const result = controller.filterDamage(cause, ev.damage);
  if (result.cancel) ev.cancel = true; else ev.damage = result.damage;
});

// The Spider Tamer tames it; once tamed, an empty hand climbs on.
world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
  const target = ev.target;
  if (!target || target.typeId !== SPIDER_TYPE) return;
  const controller = controllers.get(target.id);
  if (!controller) return;
  const item = ev.itemStack;
  if (item && item.typeId === TAMER_ITEM) {
    ev.cancel = true;
    system.run(() => controller.tame());
  } else if (!item && controller.tamed) {
    ev.cancel = true;
    const player = ev.player;
    system.run(() => controller.mount(player));
  }
});

// ---------------------------------------------------------------- personal spiders (/spider newinstance)

const commandHandlers = {
  hasInstance: (player) => personal.has(player.id),
  newInstance(player, size) {
    this.release(player);
    const dim = player.dimension;
    const level = new BedrockLevel(dim);
    const options = netheriteSpider(8, CONFIG.chaseSpeedBlocksPerSecond, CONFIG.legStepSpeed);
    const at = player.location;
    let yaw = 0;
    try { yaw = player.getRotation().y; } catch (e) {}
    const body = SpiderBody.fromSpawn(level, new Vec3(at.x, at.y + 1, at.z), yaw, 0, options.bodyPlan, options.gallopGait, options.walkGait);
    body.onLegStep = (leg) => level.playSound("step.netherite", leg.endEffector, 0.3, 1.0);
    body.onHitGround = () => level.playSound("fall.netherite", body.position, 1.0, 0.8);
    body.setSizeScale(size);
    body.setSpeedScale(Math.max(scaleToSpeedFactor(size), 2.0));   // keeps up with sprinting
    personal.set(player.id, { player, body, level, renderer: new SpiderRenderer(dim) });
  },
  resize(player, size) {
    const p = personal.get(player.id);
    if (!p) return;
    p.body.setSizeScale(size);
    p.body.setSpeedScale(Math.max(scaleToSpeedFactor(size), 2.0));
  },
  release(player) {
    const p = personal.get(player.id);
    if (!p) return;
    p.renderer.removeAll();
    personal.delete(player.id);
  },
};
registerCommands(commandHandlers);

function tickPersonal() {
  for (const [id, p] of personal) {
    let keep = isValid(p.player) && p.player.dimension.id === p.level.dimension.id;
    if (keep) {
      keep = isCreative(p.player);
    }
    if (!keep) { p.renderer.removeAll(); personal.delete(id); continue; }
    p.level.blocks.newTick();
    p.body.update();
    const follow = Math.min(p.body.walkGait.stationary.bodyHeight * 1.5, 6.0);
    applyBehaviour(p.body, new TargetBehaviour(Vec3.of(p.player.location), follow));
    p.renderer.render(p.body);
  }
}

// ---------------------------------------------------------------- the tick

/** Java's level.isDay, near enough for hostileOnlyAtNight: daylight runs 0..12000 of 24000. */
function isDay() {
  try { const t = world.getTimeOfDay(); return t < 12000 || t >= 23500; } catch (e) { return false; }
}

function playersIn(dimensionId) {
  return world.getAllPlayers().filter((p) => p.dimension.id === dimensionId);
}

let started = false;
function start() {
  started = true;
  loadConfig();
  // Leftovers from the previous session (Java never saves the spider): the hunt starts fresh.
  for (const id of DIMENSIONS) {
    let list = [];
    try { list = world.getDimension(id).getEntities({ type: SPIDER_TYPE }); } catch (e) {}
    for (const e of list) if (!controllers.has(e.id)) try { e.remove(); } catch (err) {}
  }
}

function tick() {
  if (!started) start();
  const day = isDay();
  for (const controller of [...controllers.values()]) {
    if (!controller.alive) {
      // Gone without dying and not by our hand: its chunk unloaded under it.
      if (!controller.dead && !controller.detached) {
        forget(controller);
        spawnManager.notifyLost(controller);
      }
      continue;
    }
    controller.tick(playersIn(controller.dimension.id), day);
  }
  tickPersonal();
  spawnManager.tick();
}

let ticks = 0;
system.runInterval(() => {
  ticks++;
  hud.beforeTick();
  try { tick(); } catch (e) { console.warn(`[arachno] tick failed: ${e}\n${e && e.stack ? e.stack : ""}`); }
  hud.afterTick();
  if (ticks % 200 === 5) {
    const owned = new Set();
    for (const c of controllers.values()) for (const id of c.renderer.ownedIds()) owned.add(id);
    for (const p of personal.values()) for (const id of p.renderer.ownedIds()) owned.add(id);
    sweepOrphans(owned);
  }
}, 1);

// Developer hook (not a player feature): spawn a real spider mob 6 blocks in front of you, as a
// spawn egg would. `/scriptevent arachno:spawn camo` forces the camo variant. The browser test
// tools use it too.
system.afterEvents.scriptEventReceive.subscribe((ev) => {
  if (ev.id !== "arachno:spawn") return;
  const player = ev.sourceEntity;
  if (!player) return;
  const level = new BedrockLevel(player.dimension);
  const view = player.getViewDirection();
  const h = Math.hypot(view.x, view.z) || 1;
  const spot = new Vec3(player.location.x + (view.x / h) * 6, player.location.y, player.location.z + (view.z / h) * 6);
  const ground = level.raycastGround(spot.copy().add(new Vec3(0, 8, 0)), DOWN(), 24);
  if (ground) spot.y = ground.y;
  const variant = String(ev.message || "").trim().toLowerCase() === "camo" ? Variant.CAMO : Variant.NETHERITE;
  let entity;
  try { entity = player.dimension.spawnEntity(SPIDER_TYPE, { x: spot.x, y: spot.y, z: spot.z }); } catch (e) { return; }
  // Face the player.
  const yaw = -Math.atan2(player.location.x - spot.x, player.location.z - spot.z) * 180 / Math.PI;
  try { entity.setRotation({ x: 0, y: yaw }); } catch (e) {}
  adopt(entity, variant);
});

// ---------------------------------------------------------------- developer status line (debugHud)

const HAS_DATE = typeof Date !== "undefined" && typeof Date.now === "function";
const nowMs = () => (HAS_DATE ? Date.now() : 0);
const DRAG_RED_PER_SIZE = 0.25;
const hud = {
  tickTimes: [], costs: [], t0: 0, steps: 0, drag: 0, ticks: 0, feet: null,
  beforeTick() {
    this.t0 = nowMs();
    if (HAS_DATE) { this.tickTimes.push(this.t0); if (this.tickTimes.length > 41) this.tickTimes.shift(); }
  },
  afterTick() {
    if (!CONFIG.debugHud) return;
    const s = spawnManager.current;
    if (!s) return;
    this.costs.push(nowMs() - this.t0);
    if (this.costs.length > 20) this.costs.shift();
    this.ticks++;
    this.measureFeet(s.body);
    if (ticks % 10 === 0) this.show(s);
  },
  /** Steps taken, and the furthest a PLANTED foot moved in one tick (a drag), ignoring fast size changes. */
  measureFeet(body) {
    const legs = body.legs;
    if (!this.feet || this.feet.length !== legs.length) this.feet = legs.map(() => null);
    legs.forEach((l, i) => {
      const prev = this.feet[i], foot = l.endEffector, planted = l.isGrounded();
      if (prev) {
        if (planted && prev.planted && !body.sizeChanging) this.drag = Math.max(this.drag, Math.hypot(foot.x - prev.x, foot.y - prev.y, foot.z - prev.z));
        if (l.isMoving && !prev.moving) this.steps++;
      }
      this.feet[i] = { x: foot.x, y: foot.y, z: foot.z, planted, moving: l.isMoving };
    });
  },
  show(s) {
    const span = this.tickTimes.length > 1 ? this.tickTimes[this.tickTimes.length - 1] - this.tickTimes[0] : 0;
    const tps = span > 0 ? (this.tickTimes.length - 1) * 1000 / span : null;
    const cost = this.costs.length ? this.costs.reduce((a, b) => a + b, 0) / this.costs.length : 0;
    const b = s.body;
    const stepsPerSec = this.steps / Math.max(1, b.legs.length) / (Math.max(1, this.ticks) / 20);
    const dragRed = this.drag > DRAG_RED_PER_SIZE * Math.max(0.6, b.sizeScale);
    let hp = "?";
    try { hp = Math.round(s.entity.getComponent("minecraft:health").currentValue); } catch (e) {}
    const text = `§7ArachnoMod  tps ${tps === null ? "?" : `${tps < 19 ? "§c" : "§a"}${tps.toFixed(1)}`}§7  script §f${cost.toFixed(1)}ms` +
      `§7  ${s.variant} ${s.ai.mode.toLowerCase()}${s.tamed ? " tamed" : ""}  hp §f${hp}§7  size §f${b.sizeScale.toFixed(1)}§7  §f${(b.velocity.horizontalLength() * 20).toFixed(1)}§7 b/s` +
      `  steps §f${stepsPerSec.toFixed(1)}§7/s  drag ${dragRed ? "§c" : "§f"}${this.drag.toFixed(2)}`;
    this.steps = 0; this.drag = 0; this.ticks = 0;
    for (const p of world.getAllPlayers()) {
      try { p.onScreenDisplay.setActionBar(text); } catch (e) {}
    }
  },
};

export { spiders as __spiders, spawnManager as __spawnManager, controllers as __controllers };   // for devtools/simtest
