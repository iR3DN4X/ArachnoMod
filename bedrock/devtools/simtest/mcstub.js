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
for (const file of ["leg", "spider"]) {
  const json = await (await fetch(`/bp/entities/${file}.json`)).json();
  const desc = json["minecraft:entity"].description;
  const props = {};
  for (const [k, def] of Object.entries(desc.properties || {})) props[k] = def;
  TYPES[desc.identifier] = { props, health: json["minecraft:entity"].components["minecraft:health"] };
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
const after = { entitySpawn: signal(), entityLoad: signal(), entityDie: signal(), entityRemove: signal() };
const before = { entityHurt: signal(), playerInteractWithEntity: signal() };
const emitLater = (sig, ev) => pendingAfter.push(() => { for (const fn of sig.subs) fn(ev); });

class MockEntity {
  constructor(typeId, loc, dimension) {
    this.typeId = typeId; this.location = { ...loc }; this.id = String(++MockEntity.n);
    this.props = {}; this.isValid = true; this.nameTag = ""; this.rotation = { x: 0, y: 0 };
    this.teleports = 0; this.dimension = dimension; this.groups = new Set(); this.riders = [];
    this.fireTicks = 0;
    const type = TYPES[typeId];
    if (type) for (const [k, def] of Object.entries(type.props)) this.props[k] = def.default;
    if (type && type.health) this.health = { current: type.health.value, max: type.health.max ?? type.health.value };
    MockEntity.all.push(this);
  }
  teleport(loc, opts) {
    if (!Number.isFinite(loc.x + loc.y + loc.z)) { errors.push("bad teleport " + JSON.stringify(loc)); throw new Error("bad teleport"); }
    this.location = { ...loc }; this.teleports++;
    if (opts && opts.rotation) this.rotation = opts.rotation;
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
  triggerEvent(name) {
    log.push(`event ${name} on ${this.typeId}`);
    if (name === "arachno:tamed") this.groups.add("arachno:saddle");
    if (name === "arachno:tamer_used") { this.groups.add("arachno:saddle"); this.props["arachno:tamed"] = true; }
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
MockEntity.n = 0; MockEntity.all = [];
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
function idAt(x, y, z) {
  const s = scene.get(key(x, y, z));
  if (s !== undefined) return s;
  return y < FLOOR ? "stone" : "air";
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

const dim = {
  id: "minecraft:overworld",
  heightRange: { min: -64, max: 320 },
  getBlockFromRay(loc, dir, opts) {
    counts.rays++;
    return voxelRay(loc, dir, opts);
  },
  getBlock(p) {
    counts.blocks++;
    const id = idAt(p.x, p.y, p.z);
    return {
      location: p, typeId: "minecraft:" + id, isAir: id === "air", isLiquid: id === "water" || id === "lava",
      isWaterlogged: false, permutation: { getState: () => undefined }, setPermutation() {},
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
  spawnParticle(id) { counts.particles = (counts.particles || 0) + 1; },
  spawnItem(item, loc) { log.push(`item ${item.typeId} at ${loc.x.toFixed(1)},${loc.y.toFixed(2)},${loc.z.toFixed(1)}`); return new MockEntity("minecraft:item", loc, dim); },
};

// ?ox=N moves the whole test N blocks from the world origin (anchors wrap at +-512 on the wire).
export const ORIGIN_OFFSET = Number(new URLSearchParams(globalThis.location ? globalThis.location.search : "").get("ox")) || 0;
let view = { x: 1, y: 0, z: 0 };
const player = {
  id: "p1", dimension: dim, location: { x: ORIGIN_OFFSET + 0.5, y: FLOOR, z: ORIGIN_OFFSET + 0.5 },
  getViewDirection() { return { ...view }; },
  getRotation() { return { x: 0, y: -90 }; },
  onScreenDisplay: { setActionBar(t) { log.push("hud " + t); } },
  typeId: "minecraft:player", isValid: true, isSneaking: false, gameMode: "Survival", health: 20, bites: 0,
  move: { x: 0, y: 0 },
  inputInfo: { getMovementVector() { return { ...player.move }; } },
  getGameMode() { return player.gameMode; },
  getComponent(id) {
    if (id === "minecraft:health") return { get currentValue() { return player.health; } };
    if (id === "minecraft:riding" && player.ridingOn) return { entityRidingOn: player.ridingOn };
    return undefined;
  },
  applyDamage(amount, options = {}) { player.bites++; log.push(`player hurt ${amount} (${options.cause}) by ${options.damagingEntity ? options.damagingEntity.typeId : "?"}`); return true; },
};

const store = {};
const intervals = [];
const runQueue = [];          // system.run callbacks, run at the start of the next tick
const handlers = [];

export const world = {
  getAllPlayers: () => [player],
  getDimension: () => dim,
  sendMessage: (m) => log.push("msg " + m),
  getDynamicProperty: (k) => store[k],
  setDynamicProperty: (k, v) => { store[k] = v; },
  getTimeOfDay: () => 18000,
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
export const system = {
  runInterval(cb) { intervals.push(cb); return intervals.length; },
  run(cb) { runQueue.push(cb); },
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
  log, errors, dim, player, MockEntity, counts, scene,
  fire(id, message = "") { for (const h of handlers) h({ id, message, sourceEntity: player }); },
  tick(n = 1) {
    for (let i = 0; i < n; i++) {
      for (const fn of pendingAfter.splice(0)) fn();
      for (const fn of runQueue.splice(0)) fn();
      for (const e of MockEntity.all) if (e.dying && --e.dying <= 0) e.isValid = false;
      for (const cb of intervals) cb();
    }
  },
  /** A player right-clicking an entity with an item (null = empty hand). */
  interact(entity, itemTypeId = null) {
    const ev = { player, target: entity, itemStack: itemTypeId ? new ItemStack(itemTypeId) : undefined, cancel: false };
    for (const fn of before.playerInteractWithEntity.subs) fn(ev);
    return ev;
  },
  after, before, commands, enums,
  /** Run a registered custom command as the player: __test.command("arachno:spider", "size", 3). */
  command(name, ...args) {
    const c = commands[name];
    if (!c) return { status: 1, message: `no command ${name}` };
    if ((c.def.mandatoryParameters || []).some((p, i) => p.type === "Enum" && !enums[p.name].includes(String(args[i]).toLowerCase())))
      return { status: 1, message: `bad enum value ${args}` };
    return c.cb({ sourceEntity: player }, ...args) || { status: 0 };
  },
  setView(v) { view = { ...v }; },
  /** Put a block (typeId without prefix; "air" digs into the floor). */
  set(x, y, z, id) { setBlock(x, y, z, id); },
  fill(x0, y0, z0, x1, y1, z1, id) {
    for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
      for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
        for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) setBlock(x, y, z, id);
  },
  isSolid(x, y, z) { return info(Math.floor(x), Math.floor(y), Math.floor(z)).collision; },
};
