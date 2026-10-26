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
//   menu.js     the Spider Config Book's settings menu                    (ConfigBook*.kt)

import { world, system, ItemStack } from "@minecraft/server";
import { Vec3, DOWN } from "./engine/vec.js";
import { SpiderBody } from "./engine/body.js";
import { netheriteSpider } from "./engine/presets.js";
import { applyBehaviour, TargetBehaviour } from "./engine/behaviour.js";
import { BedrockLevel } from "./platform.js";
import { SpiderRenderer, sweepOrphans, isValid, DRAWN_TYPES } from "./render.js";
import { CONFIG, loadConfig, onConfigChange } from "./config.js";
import { scaleToSpeedFactor } from "./mob.js";
import { SpiderController, SPIDER_TYPE, TAMER_ITEM, Variant, rollVariant } from "./spider.js";
import { SpawnManager } from "./spawn.js";
import { registerCommands } from "./commands.js";
import { isCreative } from "./enums.js";
import { WARD_BLOCK, addWard, removeWard, blockingNeighbour, secondsUntilArmed, tickWards } from "./wards.js";
import { BOOK_ITEM, openMenu, giveBookOnFirstJoin } from "./menu.js";
import { DEBUG } from "./debug.js";

const LASER_ITEM = "arachno:spider_laser_pointer";

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
// A changed spawn timing re-arms the countdown already running (Java v1.3.2).
onConfigChange((key) => spawnManager.onConfigSet(key));

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

// A spider placed by a spawn egg or /summon: adopt it (rolling the variant chances, as Java's spawn
// egg does). The one already in the world is replaced - there is only ever one.
world.afterEvents.entitySpawn.subscribe((ev) => {
  const e = ev.entity;
  if (!isValid(e) || e.typeId !== SPIDER_TYPE || controllers.has(e.id)) return;
  adopt(e, rollVariant());
});

// A spider loaded back in from the save is a leftover of an earlier session or of a chunk it was
// lost in (Java never saves the spider at all): remove it. The spawn manager keeps the real one.
// The same goes for LEGS: a leg entity still standing when its chunk unloaded (going through a
// portal) was saved with it and comes back frozen - the "afterimage". Gone the moment the chunk
// loads, instead of lingering until the next 10-second sweep.
world.afterEvents.entityLoad.subscribe((ev) => {
  const e = ev.entity;
  if (!isValid(e)) return;
  if (e.typeId === SPIDER_TYPE && !controllers.has(e.id)) {
    try { e.remove(); } catch (err) {}
  } else if (DRAWN_TYPES.includes(e.typeId) && !ownedLegIds().has(e.id)) {
    try { e.remove(); } catch (err) {}
  }
});

/** Every leg entity a live spider (or personal spider) is drawing with right now. */
function ownedLegIds() {
  const owned = new Set();
  for (const c of controllers.values()) for (const id of c.renderer.ownedIds()) owned.add(id);
  for (const p of personal.values()) for (const id of p.renderer.ownedIds()) owned.add(id);
  return owned;
}

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
  spawnManager.recordKill();          // every real death counts toward permadeath
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

// The Spider Tamer tames it; once tamed, an empty hand climbs on (not while crouching, like the
// entity's own ride interaction: crouching_skip_interact). A netherite ingot ENRAGES the untamed
// netherite variant - feed it the metal it is made of - and is used up (kept in Creative).
world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
  const target = ev.target;
  if (!target || target.typeId !== SPIDER_TYPE) return;
  const controller = controllers.get(target.id);
  if (!controller) return;
  const item = ev.itemStack;
  if (item && item.typeId === "minecraft:netherite_ingot" && controller.variant === Variant.NETHERITE && !controller.tamed) {
    ev.cancel = true;
    const player = ev.player;
    system.run(() => { if (controller.enrage() && !isCreative(player)) takeOneFromHand(player, "minecraft:netherite_ingot"); });
  } else if (item && item.typeId === TAMER_ITEM) {
    ev.cancel = true;
    const player = ev.player;
    system.run(() => controller.tame(player));   // whoever tames it is its owner
  } else if (!item && controller.tamed && !ev.player.isSneaking) {
    ev.cancel = true;
    const player = ev.player;
    system.run(() => controller.mount(player));
  }
});

// THE PET'S FIGHT (Java v1.8.6): a player punches something - if it is the owner of a tamed spider,
// in creative, and the thing is a mob, that spider goes for it (SpiderController.ownerPunched).
world.afterEvents.entityHitEntity.subscribe((ev) => {
  const player = ev.damagingEntity;
  if (!player || player.typeId !== "minecraft:player") return;
  for (const c of controllers.values()) if (c.alive) c.ownerPunched(player, ev.hitEntity);
});

// ---------------------------------------------------------------- the Warding Idol (Java v1.8.5)

const tell = (player, text) => { try { player.sendMessage(text); } catch (e) {} };

// Placing one: the spacing rule first, then the ward - and say whether it is waking or watching.
world.afterEvents.playerPlaceBlock.subscribe((ev) => {
  const block = ev.block;
  if (!block || block.typeId !== WARD_BLOCK) return;
  const player = ev.player, d = ev.dimension.id;
  const { x, y, z } = block.location;
  const blocking = blockingNeighbour(d, x, y, z);
  if (blocking) {
    // Too close to another idol: give it back rather than swallowing an expensive craft.
    try { block.setType("minecraft:air"); } catch (e) {}
    if (!isCreative(player)) {
      try {
        const left = player.getComponent("minecraft:inventory").container.addItem(new ItemStack(WARD_BLOCK, 1));
        if (left) ev.dimension.spawnItem(left, { x: x + 0.5, y: y + 0.5, z: z + 0.5 });
      } catch (e) {}
    }
    tell(player, `Too close to another Warding Idol (${blocking.x}, ${blocking.y}, ${blocking.z}). They must be at least ${Math.trunc(CONFIG.wardMinSpacing)} blocks apart.`);
    return;
  }
  addWard(d, x, y, z);
  if (!CONFIG.wardEnabled) {
    tell(player, "Warding Idols are switched off in this world (wardEnabled = false).");
    return;
  }
  const seconds = secondsUntilArmed(d, x, y, z);
  const radius = Math.trunc(CONFIG.wardRadius);
  tell(player, seconds === null || seconds <= 0
    ? `The Warding Idol is watching: everything within ${radius} blocks is a safe zone. No spider can follow you in.`
    : `The Warding Idol is waking: it will be warding ${radius} blocks in ${seconds} second(s).`);
});

// Breaking or blowing one up releases the ground (any other way is caught by the periodic sweep).
world.afterEvents.playerBreakBlock.subscribe((ev) => {
  if (ev.brokenBlockPermutation && ev.brokenBlockPermutation.type.id === WARD_BLOCK) {
    removeWard(ev.dimension.id, ev.block.location.x, ev.block.location.y, ev.block.location.z);
  }
});
world.afterEvents.blockExplode.subscribe((ev) => {
  if (ev.explodedBlockPermutation && ev.explodedBlockPermutation.type.id === WARD_BLOCK) {
    removeWard(ev.dimension.id, ev.block.location.x, ev.block.location.y, ev.block.location.z);
  }
});

// ---------------------------------------------------------------- the Spider Laser Pointer (Java v1.8.5)

// Right-click: shine the dot at whatever you look at, and the spider scurries after it like a cat.
// It lures rather than commands - LURE outranks a chase and is not CHASE, so it won't bite while
// it is distracted. Creative only, like /spider newinstance.
world.afterEvents.itemUse.subscribe((ev) => {
  const item = ev.itemStack;
  if (!item || item.typeId !== LASER_ITEM) return;
  const player = ev.source;
  const say = (text) => { try { player.onScreenDisplay.setActionBar(text); } catch (e) {} };
  if (!isCreative(player)) { say("The Spider Laser Pointer is a creative-mode-only toy."); return; }
  const level = new BedrockLevel(player.dimension);
  let eye, look;
  try { eye = Vec3.of(player.getHeadLocation()); look = Vec3.of(player.getViewDirection()); } catch (e) { return; }
  const hit = level.raycastGround(eye, look, CONFIG.laserPointerRange);
  if (!hit) { say("The dot lands on nothing - point it at a surface."); return; }
  for (let i = 0; i < 4; i++) {
    level.spawnParticle("minecraft:basic_flame_particle", new Vec3(hit.x + (Math.random() - 0.5) * 0.1, hit.y + 0.1, hit.z + (Math.random() - 0.5) * 0.1));
  }
  const ticks = Math.max(1, Math.round(CONFIG.laserPointerLureSeconds * 20));
  let took = false;
  for (const c of controllers.values()) {
    if (c.alive && c.dimension.id === player.dimension.id) { c.ai.lure(hit, ticks); took = true; }
  }
  say(took ? "The spider fixes on the dot." : "Nothing is around to chase the dot.");
});

// ---------------------------------------------------------------- the Spider Config Book (Java v1.8.5)

// Use it: the settings menu (anyone may look; operators may change things).
world.afterEvents.itemUse.subscribe((ev) => {
  if (ev.itemStack && ev.itemStack.typeId === BOOK_ITEM) openMenu(ev.source);
});

// Joining: everyone who may edit gets one book, once ever (giveConfigBook). The world's saved
// settings are loaded first - this can come before the first tick.
world.afterEvents.playerSpawn.subscribe((ev) => {
  if (!ev.initialSpawn) return;
  if (!started) start();
  giveBookOnFirstJoin(ev.player);
});

/** Use up one of the item in the player's hand (if it is still that item). */
function takeOneFromHand(player, typeId) {
  try {
    const container = player.getComponent("minecraft:inventory").container;
    const slot = player.selectedSlotIndex;
    const stack = container.getItem(slot);
    if (!stack || stack.typeId !== typeId) return;
    if (stack.amount > 1) { stack.amount -= 1; container.setItem(slot, stack); } else container.setItem(slot, undefined);
  } catch (e) {}
}

// ---------------------------------------------------------------- personal spiders (/spider newinstance)

const commandHandlers = {
  openMenu: (player) => openMenu(player),
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
  tickWards();
  spawnManager.tick();
}

let ticks = 0;
system.runInterval(() => {
  ticks++;
  hud.beforeTick();
  try { tick(); } catch (e) { console.warn(`[arachno] tick failed: ${e}\n${e && e.stack ? e.stack : ""}`); }
  hud.afterTick();
  if (ticks % 200 === 5) sweepOrphans(ownedLegIds());
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
  const asked = String(ev.message || "").trim().toLowerCase();
  const variant = Object.values(Variant).includes(asked) ? asked : Variant.NETHERITE;
  let entity;
  try { entity = player.dimension.spawnEntity(SPIDER_TYPE, { x: spot.x, y: spot.y, z: spot.z }); } catch (e) { return; }
  // Face the player.
  const yaw = -Math.atan2(player.location.x - spot.x, player.location.z - spot.z) * 180 / Math.PI;
  try { entity.setRotation({ x: 0, y: yaw }); } catch (e) {}
  adopt(entity, variant);
});

// ---------------------------------------------------------------- status lines (debugHud, /spider debug)

// `/spider debug` adds a GAIT line to the debug view: the numbers devtools/simtest/chase.html
// reports for the same walk, over the last 2 seconds, so one screenshot can be held up against the
// simulator - steps per leg per second, how much of the time legs are red (stretched: an
// uncomfortable leg in the debug view), feet off the ground, and planted feet sliding.
// debugHud's full developer line ends with the same numbers.

const HAS_DATE = typeof Date !== "undefined" && typeof Date.now === "function";
const nowMs = () => (HAS_DATE ? Date.now() : 0);
const DRAG_RED_PER_SIZE = 0.25;
const GAIT_WINDOW = 40;   // ticks the gait numbers cover
const hud = {
  tickTimes: [], costs: [], t0: 0, drag: 0, feet: null, window: [],
  beforeTick() {
    this.t0 = nowMs();
    if (HAS_DATE) { this.tickTimes.push(this.t0); if (this.tickTimes.length > 41) this.tickTimes.shift(); }
  },
  afterTick() {
    if (!CONFIG.debugHud && !DEBUG.enabled) { this.window = []; this.feet = null; return; }
    const s = spawnManager.current;
    if (!s) return;
    this.costs.push(nowMs() - this.t0);
    if (this.costs.length > 20) this.costs.shift();
    this.measureFeet(s.body);
    if (ticks % 10 === 0) this.show(s);
  },
  /** One tick of the gait: steps begun, red (uncomfortable) legs, feet in the air, how far planted
   *  feet slid, and the worst single slide (a drag) - size changes left out, as in the simulator. */
  measureFeet(body) {
    const legs = body.legs;
    if (!this.feet || this.feet.length !== legs.length) this.feet = legs.map(() => null);
    const t = { steps: 0, red: 0, air: 0, legs: legs.length, slide: 0 };
    legs.forEach((l, i) => {
      const prev = this.feet[i], foot = l.endEffector, planted = l.isGrounded();
      if (prev) {
        if (planted && prev.planted && !body.sizeChanging) {
          const moved = Math.hypot(foot.x - prev.x, foot.y - prev.y, foot.z - prev.z);
          this.drag = Math.max(this.drag, moved);
          t.slide += Math.hypot(foot.x - prev.x, foot.z - prev.z);
        }
        if (l.isMoving && !prev.moving) t.steps++;
      }
      if (l.comfortZone && l.isUncomfortable) t.red++;
      if (!l.touchingGround && !l.isDisabled) t.air++;
      this.feet[i] = { x: foot.x, y: foot.y, z: foot.z, planted, moving: l.isMoving };
    });
    this.window.push(t);
    if (this.window.length > GAIT_WINDOW) this.window.shift();
  },
  /** "steps 2.9/s  red 13%  air 4%  slide 0.00 b/s  drag 0.00" over the window. */
  gaitText(b) {
    const w = this.window, n = Math.max(1, w.length);
    const sum = (k) => w.reduce((a, t) => a + t[k], 0);
    const legTicks = Math.max(1, sum("legs"));
    const stepsPerSec = sum("steps") / Math.max(1, b.legs.length) / (n / 20);
    const red = sum("red") / legTicks, air = sum("air") / legTicks;
    const dragRed = this.drag > DRAG_RED_PER_SIZE * Math.max(0.6, b.sizeScale);
    const pct = (f, warn) => `${f >= warn ? "§c" : "§f"}${Math.round(f * 100)}%§7`;
    return `steps §f${stepsPerSec.toFixed(1)}§7/s  red ${pct(red, 0.25)}  air ${pct(air, 0.15)}` +
      `  slide §f${(sum("slide") / (n / 20)).toFixed(2)}§7 b/s  drag ${dragRed ? "§c" : "§f"}${this.drag.toFixed(2)}`;
  },
  show(s) {
    const b = s.body;
    let text;
    if (CONFIG.debugHud) {
      const span = this.tickTimes.length > 1 ? this.tickTimes[this.tickTimes.length - 1] - this.tickTimes[0] : 0;
      const tps = span > 0 ? (this.tickTimes.length - 1) * 1000 / span : null;
      const cost = this.costs.length ? this.costs.reduce((a, c) => a + c, 0) / this.costs.length : 0;
      let hp = "?";
      try { hp = Math.round(s.realHealth()); } catch (e) {}
      text = `§7ArachnoMod  tps ${tps === null ? "?" : `${tps < 19 ? "§c" : "§a"}${tps.toFixed(1)}`}§7  script §f${cost.toFixed(1)}ms` +
        `§7  ${s.variant} ${s.ai.mode.toLowerCase()}${s.tamed ? " tamed" : ""}  hp §f${hp}§7  size §f${b.sizeScale.toFixed(1)}§7  §f${(b.velocity.horizontalLength() * 20).toFixed(1)}§7 b/s  ` +
        this.gaitText(b);
    } else {
      text = `§7gait  size §f${b.sizeScale.toFixed(1)}§7  §f${(b.velocity.horizontalLength() * 20).toFixed(1)}§7 b/s  ${this.gaitText(b)}`;
    }
    this.drag = 0;
    for (const p of world.getAllPlayers()) {
      try { p.onScreenDisplay.setActionBar(text); } catch (e) {}
    }
  },
};

export { spiders as __spiders, spawnManager as __spawnManager, controllers as __controllers };   // for devtools/simtest
