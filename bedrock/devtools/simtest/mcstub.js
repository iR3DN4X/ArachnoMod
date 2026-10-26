// Stand-in for @minecraft/server: a flat world (top surface y = FLOOR) plus optional voxel SCENE
// blocks (walls, trees, pits, rooms - see setScene), entities that validate every property write
// against the ranges declared in the pack's entities/leg.json, script events and a scripted
// player. Lets the pack's real main.js/render.js/platform.js/ai.js run in a browser.
//
// Raycasts are real voxel traversals (full-cube collision for every block that has collision -
// the same classification blocks.js gives the AI), and reproduce what the game was seen doing:
// a hit on a block's far face (Up/East/South) reports that faceLocation coordinate as 0, not 1.
import { BlockInfo } from "/blocks.js";

export const FLOOR = -60;
// Read from the pack's own entities/leg.json, so the stand-in can never drift from it.
const RANGES = {};
// Per entity type, from the pack's own entity files.
const TYPES = {};
for (const file of ["leg", "spider", "debug"]) {
  const json = await (await fetch(`/bp/entities/${file}.json`)).json();
  const desc = json["minecraft:entity"].description;
  const props = {};
  for (const [k, def] of Object.entries(desc.properties || {})) props[k] = def;
  const e = json["minecraft:entity"];
  TYPES[desc.identifier] = { props, health: e.components["minecraft:health"], groups: e.component_groups || {}, events: e.events || {} };
}
for (const [k, def] of Object.entries(TYPES["arachno:leg"].props)) RANGES[k] = def.range;
export const log = [];
const errors = [];
export const counts = { rays: 0, blocks: 0, writes: 0 };   // native-API calls - each one crosses into the game in Bedrock

// After-events are DEFERRED in Bedrock: queued here, delivered at the start of the next tick.
const pendingAfter = [];
function signal() {
  const subs = [];
  return { subs, subscribe(fn) { subs.push(fn); return fn; }, unsubscribe(fn) { const i = subs.indexOf(fn); if (i >= 0) subs.splice(i, 1); } };
}
const after = { entitySpawn: signal(), entityLoad: signal(), entityDie: signal(), entityRemove: signal(),
  playerPlaceBlock: signal(), playerBreakBlock: signal(), blockExplode: signal(), itemUse: signal(), playerSpawn: signal(),
  entityHitEntity: signal() };
const before = { entityHurt: signal(), playerInteractWithEntity: signal() };
const emitLater = (sig, ev) => pendingAfter.push(() => { for (const fn of sig.subs) fn(ev); });

// Vanilla mobs a test can spawn (they stand still - no vanilla AI here - and take damage like ours).
const VANILLA_HEALTH = { "minecraft:pig": 10, "minecraft:cow": 10, "minecraft:zombie": 20, "minecraft:armor_stand": 6 };

class MockEntity {
  constructor(typeId, loc, dimension) {
    this.typeId = typeId; this.location = { ...loc }; this.id = String(++MockEntity.n);
    this.props = {}; this.isValid = true; this.nameTag = ""; this.rotation = { x: 0, y: 0 };
    this.teleports = 0; this.dimension = dimension; this.groups = new Set(); this.riders = [];
    this.fireTicks = 0;
    const type = TYPES[typeId];
    if (type) for (const [k, def] of Object.entries(type.props)) this.props[k] = def.default;
    if (type && type.health) this.health = { current: type.health.value, max: type.health.max ?? type.health.value };
    else if (VANILLA_HEALTH[typeId]) this.health = { current: VANILLA_HEALTH[typeId], max: VANILLA_HEALTH[typeId] };
    MockEntity.all.push(this);
  }
  teleport(loc, opts) {
    if (!Number.isFinite(loc.x + loc.y + loc.z)) { errors.push("bad teleport " + JSON.stringify(loc)); throw new Error("bad teleport"); }
    this.location = { ...loc }; this.teleports++;
    if (opts && opts.rotation) this.rotation = opts.rotation;
    // MockEntity.unseatOnTeleport: a teleport knocks the riders off, at once.
    if (MockEntity.unseatOnTeleport) { for (const r of this.riders) r.ridingOn = null; this.riders = []; }
  }
  setProperty(k, v) {
    counts.writes++;
    const def = (TYPES[this.typeId] || { props: {} }).props[k];
    if (!def) { errors.push(`unknown property ${k} on ${this.typeId}`); throw new Error("unknown property " + k); }
    if (def.type === "bool") { if (typeof v !== "boolean") { errors.push(`${k}=${v} not bool`); throw new Error("type"); } }
    else {
      if (!Number.isFinite(v) || v < def.range[0] || v > def.range[1]) { errors.push(`${k}=${v} out of range`); throw new Error("range"); }
      if (def.type === "int" && !Number.isInteger(v)) { errors.push(`${k}=${v} not int`); throw new Error("int"); }
    }
    this.props[k] = v;
  }
  getProperty(k) { return this.props[k]; }
  setRotation(r) { this.rotation = { ...r }; }
  getRotation() { return { ...this.rotation }; }
  remove() { if (this.isValid) { this.isValid = false; emitLater(after.entityRemove, { removedEntityId: this.id, typeId: this.typeId }); } }
  /** Run an event exactly as the pack's own entity file defines it: add its component groups (a
   *  group's minecraft:health replaces the health pool, as in game) and set its properties. */
  triggerEvent(name) {
    log.push(`event ${name} on ${this.typeId}`);
    const type = TYPES[this.typeId];
    const ev = type && type.events[name];
    if (!ev) { errors.push(`unknown event ${name} on ${this.typeId}`); throw new Error("unknown event " + name); }
    for (const g of (ev.add && ev.add.component_groups) || []) {
      const group = type.groups[g];
      if (!group) { errors.push(`event ${name}: unknown group ${g}`); continue; }
      this.groups.add(g);
      const h = group["minecraft:health"];
      if (h) this.health = { current: h.value, max: h.max ?? h.value };
    }
    for (const [k, v] of Object.entries(ev.set_property || {})) this.props[k] = v;
  }
  extinguishFire() { this.fireTicks = 0; return true; }
  getComponent(id) {
    const self = this;
    if (id === "minecraft:health" && this.health) return {
      get currentValue() { return self.health.current; },
      get effectiveMax() { return self.health.max; },
      setCurrentValue(v) { if (v > self.health.max) throw new Error("above max"); self.health.current = v; return true; },
    };
    if (id === "minecraft:rideable" && this.groups.has("arachno:saddle")) return {
      getRiders: () => self.riders.filter((r) => r.isValid !== false),
      addRider: (r) => { if (self.riders.length) return false; self.riders.push(r); r.ridingOn = self; return true; },
      ejectRider: (r) => { if (r.ridingOn === self) r.ridingOn = null; self.riders = self.riders.filter((x) => x !== r); },
      ejectRiders: () => { for (const r of self.riders) r.ridingOn = null; self.riders = []; },
    };
    if (id === "minecraft:onfire" && this.fireTicks > 0) return { onFireTicksRemaining: this.fireTicks };
    return undefined;
  }
  /** Damage as Bedrock deals it: the before-event may cancel or change it; 0 health = death. */
  applyDamage(amount, options = {}) {
    if (!this.health) { log.push(`damage ${amount} ${options.cause} -> ${this.typeId}`); return true; }
    const ev = { hurtEntity: this, damage: amount, damageSource: { cause: options.cause || "none", damagingEntity: options.damagingEntity }, cancel: false };
    for (const fn of before.entityHurt.subs) fn(ev);
    if (ev.cancel) return false;
    this.health.current = Math.max(0, this.health.current - ev.damage);
    log.push(`hurt ${this.typeId} by ${ev.damage.toFixed(2)} (${options.cause}) -> ${this.health.current.toFixed(1)}`);
    if (this.health.current <= 0 && this.isValid && !this.dying) {
      // Bedrock keeps a slain mob for its ~1 s death animation; entityDie arrives first.
      this.dying = 20;
      emitLater(after.entityDie, { deadEntity: this, damageSource: ev.damageSource });
    }
    return true;
  }
}
MockEntity.n = 0; MockEntity.all = []; MockEntity.unseatOnTeleport = false;
export class ItemStack { constructor(typeId, amount = 1) { this.typeId = typeId; this.amount = amount; } }

// ---- the voxel world ---------------------------------------------------------------------------
const scene = new Map();   // "x,y,z" -> typeId (no "minecraft:" prefix); "air" erases the floor
const columns = new Map(); // "x,z" -> Map(y -> typeId): getTopmostBlock without scanning the whole scene
const key = (x, y, z) => `${x},${y},${z}`;
function setBlock(x, y, z, id) {
  scene.set(key(x, y, z), id);
  const ck = `${x},${z}`;
  let col = columns.get(ck);
  if (!col) columns.set(ck, (col = new Map()));
  col.set(y, id);
}
let voidWorld = false;   // __test.setVoid(true): no stone floor at all - a SkyBlock world
function idAt(x, y, z) {
  const s = scene.get(key(x, y, z));
  if (s !== undefined) return s;
  return y < FLOOR && !voidWorld ? "stone" : "air";
}
const infoCache = new Map();
function info(x, y, z) {
  const id = idAt(x, y, z);
  let i = infoCache.get(id);
  if (!i) {
    const liquid = id === "water" || id === "lava";
    i = new BlockInfo(true, id, id === "air", liquid, id === "water", false, false);
    infoCache.set(id, i);
  }
  return i;
}
function rayStops(x, y, z, opts) {
  const i = info(x, y, z);
  if (i.liquid) return !!opts.includeLiquidBlocks;
  if (i.collision) return true;
  return !i.air && !!opts.includePassableBlocks;
}

/** Amanatides-Woo voxel traversal; returns Bedrock's BlockRaycastHit shape, quirk included. */
function voxelRay(loc, dir, opts) {
  const len = Math.hypot(dir.x, dir.y, dir.z);
  if (!(len > 0)) return undefined;
  const d = { x: dir.x / len, y: dir.y / len, z: dir.z / len };
  let cx = Math.floor(loc.x), cy = Math.floor(loc.y), cz = Math.floor(loc.z);
  // A ray that STARTS inside a solid block reports nothing (as the flat-floor stand-in always did).
  if (rayStops(cx, cy, cz, opts)) return undefined;
  const step = { x: Math.sign(d.x), y: Math.sign(d.y), z: Math.sign(d.z) };
  const next = (p, c, s, dd) => (s > 0 ? (c + 1 - p) / dd : s < 0 ? (p - c) / -dd : Infinity);
  let tMaxX = next(loc.x, cx, step.x, d.x), tMaxY = next(loc.y, cy, step.y, d.y), tMaxZ = next(loc.z, cz, step.z, d.z);
  const tDX = step.x ? Math.abs(1 / d.x) : Infinity, tDY = step.y ? Math.abs(1 / d.y) : Infinity, tDZ = step.z ? Math.abs(1 / d.z) : Infinity;
  const max = opts.maxDistance;
  for (let guard = 0; guard < 4096; guard++) {
    let t, face;
    if (tMaxX <= tMaxY && tMaxX <= tMaxZ) { t = tMaxX; cx += step.x; tMaxX += tDX; face = step.x > 0 ? "West" : "East"; }
    else if (tMaxY <= tMaxZ) { t = tMaxY; cy += step.y; tMaxY += tDY; face = step.y > 0 ? "Down" : "Up"; }
    else { t = tMaxZ; cz += step.z; tMaxZ += tDZ; face = step.z > 0 ? "North" : "South"; }
    if (t > max) return undefined;
    if (cy < FLOOR - 64 || cy > 400) return undefined;
    if (rayStops(cx, cy, cz, opts)) {
      const hx = loc.x + d.x * t, hy = loc.y + d.y * t, hz = loc.z + d.z * t;
      const f = { x: hx - cx, y: hy - cy, z: hz - cz };
      // THE QUIRK: the far face's own coordinate wraps from 1 to 0.
      if (face === "Up") f.y = 0;
      if (face === "East") f.x = 0;
      if (face === "South") f.z = 0;
      return { block: { location: { x: cx, y: cy, z: cz } }, face, faceLocation: f };
    }
  }
  return undefined;
}

// Dimensions share the one voxel world (enough for the spawn manager's dimension rules).
function makeDimension(id) {
const dim = {
  id,
  // The Nether's real build limit is 128 (its bedrock roof); its floor here stays the stub's FLOOR.
  heightRange: { min: -64, max: id === "minecraft:nether" ? 128 : 320 },
  getBlockFromRay(loc, dir, opts) {
    counts.rays++;
    return voxelRay(loc, dir, opts);
  },
  getBlock(p) {
    counts.blocks++;
    const id = idAt(p.x, p.y, p.z);
    return {
      location: { x: p.x, y: p.y, z: p.z }, typeId: id.includes(":") ? id : "minecraft:" + id, isAir: id === "air",
      isLiquid: id === "water" || id === "lava",
      isWaterlogged: false, permutation: { getState: () => undefined }, setPermutation() {},
      setType(type) { setBlock(p.x, p.y, p.z, String(type).replace(/^minecraft:/, "")); },
    };
  },
  getTopmostBlock(p) {
    counts.blocks++;
    const bx = Math.floor(p.x), bz = Math.floor(p.z);
    let top = null;
    const col = columns.get(`${bx},${bz}`);
    if (col) for (const [y, id] of col) if (id !== "air" && (top === null || y > top)) top = y;
    if (top === null || top < FLOOR - 1) top = idAt(bx, FLOOR - 1, bz) === "air" ? null : FLOOR - 1;
    if (top === null) { for (let y = FLOOR - 1; y > FLOOR - 64; y--) if (idAt(bx, y, bz) !== "air") { top = y; break; } }
    return top === null ? undefined : { location: { x: bx, y: top, z: bz } };
  },
  spawnEntity(typeId, loc) {
    const e = new MockEntity(typeId, loc, dim);
    emitLater(after.entitySpawn, { entity: e, cause: "Spawned" });
    return e;
  },
  getEntities(q) { return MockEntity.all.filter((e) => e.isValid && (!q || !q.type || e.typeId === q.type)); },
  playSound(id) { log.push("sound " + id); },
  spawnParticle(id) { counts.particles = (counts.particles || 0) + 1; counts["p:" + id] = (counts["p:" + id] || 0) + 1; },
  spawnItem(item, loc) { log.push(`item ${item.typeId} at ${loc.x.toFixed(1)},${loc.y.toFixed(2)},${loc.z.toFixed(1)}`); return new MockEntity("minecraft:item", loc, dim); },
};
return dim;
}
const dim = makeDimension("minecraft:overworld");
const nether = makeDimension("minecraft:nether");

// ?ox=N moves the whole test N blocks from the world origin (anchors wrap at +-512 on the wire).
export const ORIGIN_OFFSET = Number(new URLSearchParams(globalThis.location ? globalThis.location.search : "").get("ox")) || 0;
let view = { x: 1, y: 0, z: 0 };
const player = {
  id: "p1", dimension: dim, location: { x: ORIGIN_OFFSET + 0.5, y: FLOOR, z: ORIGIN_OFFSET + 0.5 },
  getViewDirection() { return { ...view }; },
  getRotation() { return { x: 0, y: -90 }; },
  onScreenDisplay: { setActionBar(t) { log.push("hud " + t); } },
  sendMessage(t) { log.push("chat " + t); },
  typeId: "minecraft:player", isValid: true, isSneaking: false, gameMode: "Survival", health: 20, bites: 0,
  commandPermissionLevel: 1,   // CommandPermissionLevel.GameDirectors: an operator
  playerPermissionLevel: 2,    // PlayerPermissionLevel.Operator (a single-player world's owner is one)
  tags: [],
  hasTag(t) { return player.tags.includes(t); },
  addTag(t) { if (player.tags.includes(t)) return false; player.tags.push(t); return true; },
  move: { x: 0, y: 0 },
  inputInfo: { getMovementVector() { return { ...player.move }; } },
  getGameMode() { return player.gameMode; },
  selectedSlotIndex: 0,
  slots: [],                 // the hotbar: ItemStack | undefined
  getComponent(id) {
    if (id === "minecraft:health") return { get currentValue() { return player.health; } };
    if (id === "minecraft:riding" && player.ridingOn) return { entityRidingOn: player.ridingOn };
    if (id === "minecraft:inventory") return { container: {
      getItem: (i) => player.slots[i],
      setItem: (i, stack) => { if (stack !== undefined && !(stack.amount >= 1)) { errors.push(`setItem amount ${stack && stack.amount}`); throw new Error("amount"); } player.slots[i] = stack; },
      addItem: (stack) => { let i = 0; while (player.slots[i]) i++; player.slots[i] = stack; return undefined; },
    } };
    return undefined;
  },
  applyDamage(amount, options = {}) { player.bites++; log.push(`player hurt ${amount} (${options.cause}) by ${options.damagingEntity ? options.damagingEntity.typeId : "?"}`); return true; },
  getHeadLocation() { return { x: player.location.x, y: player.location.y + 1.62, z: player.location.z }; },
  effects: [],
  addEffect(type, duration, options = {}) {
    if (!Number.isInteger(duration) || duration < 1) { errors.push(`effect ${type} duration ${duration}`); throw new Error("duration"); }
    player.effects.push({ type, duration, amplifier: options.amplifier ?? 0 });
    log.push(`effect ${type} ${duration} ${options.amplifier ?? 0}`);
    return {};
  },
};

const store = {};
let absTime = 0;              // world.getAbsoluteTime: ticks since the world began (advanced by __test.tick)
const intervals = [];
const runQueue = [];          // system.run callbacks, run at the start of the next tick
const handlers = [];

export const world = {
  getAllPlayers: () => [player],
  getDimension: (id) => (String(id || "").includes("nether") ? nether : dim),
  sendMessage: (m) => log.push("msg " + m),
  getDynamicProperty: (k) => store[k],
  setDynamicProperty: (k, v) => { store[k] = v; },
  getTimeOfDay: () => 18000,
  getAbsoluteTime: () => absTime,
  difficulty: "Normal",
  getDifficulty() { return world.difficulty; },
  afterEvents: after,
  beforeEvents: before,
};
// Custom commands: registrations are captured so a test can run them (__test.command).
const commands = {};
const enums = {};
export const CustomCommandParamType = { Enum: "Enum", Float: "Float", String: "String", Integer: "Integer", Boolean: "Boolean" };
export const CommandPermissionLevel = { Any: 0, GameDirectors: 1, Admin: 2, Host: 3, Owner: 4 };
export const CustomCommandStatus = { Success: 0, Failure: 1 };
export const PlayerPermissionLevel = { Visitor: 0, Member: 1, Operator: 2, Custom: 3 };
const timeouts = [];          // system.runTimeout: [{ at, cb }], run when the tick count reaches `at`
export const system = {
  runInterval(cb) { intervals.push(cb); return intervals.length; },
  run(cb) { runQueue.push(cb); },
  runTimeout(cb, ticks = 1) { timeouts.push({ at: absTime + Math.max(1, ticks), cb }); return timeouts.length; },
  afterEvents: { scriptEventReceive: { subscribe(h) { handlers.push(h); } } },
  beforeEvents: {
    startup: {
      subscribe(fn) {
        fn({ customCommandRegistry: {
          registerEnum(name, values) {
            if (!/^[a-z0-9_]+:[a-z0-9_]+$/.test(name)) throw new Error(`bad enum name ${name}`);
            for (const v of values) if (!/^[a-z0-9_]+$/.test(v)) throw new Error(`enum ${name}: bad value ${v}`);
            enums[name] = values;
          },
          registerCommand(def, cb) {
            if (!/^[a-z0-9_]+:[a-z0-9_]+$/.test(def.name)) throw new Error(`bad command name ${def.name}`);
            for (const p of [...(def.mandatoryParameters || []), ...(def.optionalParameters || [])]) {
              if (p.type === undefined) throw new Error(`${def.name}: parameter ${p.name} has no type`);
              if (p.type === "Enum" && !enums[p.name]) throw new Error(`${def.name}: enum ${p.name} not registered first`);
            }
            if (def.permissionLevel === undefined) throw new Error(`${def.name}: no permission level`);
            commands[def.name] = { def, cb };
          },
        } });
      },
    },
  },
};
export const BlockPermutation = { resolve: (id) => ({ id }) };

export const __test = {
  log, errors, dim, nether, player, MockEntity, counts, scene,
  fire(id, message = "") { for (const h of handlers) h({ id, message, sourceEntity: player }); },
  tick(n = 1) {
    for (let i = 0; i < n; i++) {
      absTime++;
      for (const fn of pendingAfter.splice(0)) fn();
      for (const fn of runQueue.splice(0)) fn();
      for (const t of timeouts.filter((t) => t.at <= absTime)) { timeouts.splice(timeouts.indexOf(t), 1); t.cb(); }
      for (const e of MockEntity.all) if (e.dying && --e.dying <= 0) e.isValid = false;
      for (const cb of intervals) cb();
    }
  },
  /** `attacker` (the player by default) punches `target`: entityHitEntity, delivered next tick. */
  punch(target, attacker = player) {
    if (target.applyDamage) try { target.applyDamage(1, { cause: "entityAttack", damagingEntity: attacker }); } catch (e) {}
    emitLater(after.entityHitEntity, { damagingEntity: attacker, hitEntity: target });
  },
  /** The player (re)joins the world: playerSpawn with initialSpawn, delivered next tick like the game. */
  join() { emitLater(after.playerSpawn, { player, initialSpawn: true }); },
  /** The player places a block (typeId with namespace) at block (x, y, z): set, then the after-event. */
  place(x, y, z, typeId, targetDim = dim) {
    setBlock(x, y, z, typeId.replace(/^minecraft:/, ""));
    const ev = { block: targetDim.getBlock({ x, y, z }), dimension: targetDim, player };
    for (const fn of after.playerPlaceBlock.subs) fn(ev);
  },
  /** The player breaks the block at (x, y, z): air, then the after-event with what was there. */
  breakBlock(x, y, z, targetDim = dim) {
    const was = idAt(x, y, z);
    setBlock(x, y, z, "air");
    const ev = { block: targetDim.getBlock({ x, y, z }), dimension: targetDim, player,
      brokenBlockPermutation: { type: { id: was.includes(":") ? was : "minecraft:" + was } } };
    for (const fn of after.playerBreakBlock.subs) fn(ev);
  },
  /** The player right-clicks with an item in hand (the item-use after-event). */
  useItem(typeId) {
    const ev = { itemStack: new ItemStack(typeId), source: player };
    for (const fn of after.itemUse.subs) fn(ev);
  },
  /** A player right-clicking an entity with an item (null = empty hand). */
  interact(entity, itemTypeId = null) {
    const ev = { player, target: entity, itemStack: itemTypeId ? new ItemStack(itemTypeId) : undefined, cancel: false };
    for (const fn of before.playerInteractWithEntity.subs) fn(ev);
    return ev;
  },
  after, before, commands, enums,
  /** Run a registered custom command as the player TYPED it: __test.command("arachno:spider", "size", "3").
   *  Every word goes through the game's parser rules first, as in game: an enum word must be one of
   *  its values (any case); a Float/Integer must be a number; a Boolean is true or false; and a
   *  String slot REJECTS a bare number ("Syntax error: Unexpected "3"" - seen in game, 2026-10-24). */
  command(name, ...words) {
    const c = commands[name];
    if (!c) return { status: 1, message: `no command ${name}` };
    const mandatory = c.def.mandatoryParameters || [], optional = c.def.optionalParameters || [];
    const params = [...mandatory, ...optional];
    if (words.length < mandatory.length) return { status: 1, message: `Syntax error: missing ${mandatory[words.length].name}` };
    if (words.length > params.length) return { status: 1, message: `Syntax error: Unexpected "${words[params.length]}"` };
    const values = [];
    for (let i = 0; i < words.length; i++) {
      const p = params[i], w = String(words[i]);
      const bad = { status: 1, message: `Syntax error: Unexpected "${w}"` };
      const num = Number(w);
      if (p.type === "Enum") {
        const v = enums[p.name].find((e) => e === w.toLowerCase());
        if (!v) return bad;
        values.push(v);
      } else if (p.type === "Float") {
        if (w.trim() === "" || !Number.isFinite(num)) return bad;
        values.push(num);
      } else if (p.type === "Integer") {
        if (!/^[+-]?\d+$/.test(w)) return bad;
        values.push(num);
      } else if (p.type === "Boolean") {
        if (w !== "true" && w !== "false") return bad;
        values.push(w === "true");
      } else if (p.type === "String") {
        if (w.trim() !== "" && Number.isFinite(num)) return bad;
        values.push(w);
      } else {
        values.push(w);
      }
    }
    return c.cb({ sourceEntity: player }, ...values) || { status: 0 };
  },
  setView(v) { view = { ...v }; },
  setVoid(on) { voidWorld = !!on; infoCache.clear(); },
  /** Put a block (typeId without prefix; "air" digs into the floor). */
  set(x, y, z, id) { setBlock(x, y, z, id); },
  fill(x0, y0, z0, x1, y1, z1, id) {
    for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
      for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
        for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) setBlock(x, y, z, id);
  },
  isSolid(x, y, z) { return info(Math.floor(x), Math.floor(y), Math.floor(z)).collision; },
};
