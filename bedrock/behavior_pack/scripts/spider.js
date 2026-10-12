// Port of entity/SpiderMob.kt: the spider as a real mob.
//
// As in Java, the mob is an INVISIBLE hittable entity (`arachno:spider`) that owns one simulated
// body; what you see is the legs (render.js). The mob provides what the Java SpiderMob did:
//   - a health pool per variant (netherite 350 inside a full netherite suit's armour, camo 600),
//   - a hitbox that follows the simulated body,
//   - the bite (per-variant hearts, every attackCooldownTicks, only while CHASING, never tamed),
//   - taming with the Spider Tamer, then riding it (all four movement keys, facing your view),
//   - a netherite-ingot trophy on death, dropped on the floor directly beneath the body,
//   - the variants: NETHERITE (fireproof, clanks) and CAMO (active camouflage, burns visibly).
// Movement, facing and speed come from SpiderAI (ai.js); this mob owns size and the bite.

import { Vec3 } from "./engine/vec.js";
import { SpiderBody } from "./engine/body.js";
import { netheriteSpider, camoSpider } from "./engine/presets.js";
import { applyBehaviour, DirectionBehaviour, StayStillBehaviour } from "./engine/behaviour.js";
import { BedrockLevel } from "./platform.js";
import { SpiderRenderer, isValid } from "./render.js";
import { CONFIG } from "./config.js";
import { approachScale, targetScale, scaleToSpeedFactor } from "./mob.js";
import { SpiderAI } from "./ai.js";
import { findFloorBelow } from "./ground.js";
import { camoFor, blockUnder, NETHERITE_TEX, CAMO_SPAWN_TEX } from "./camo.js";

export const SPIDER_TYPE = "arachno:spider";
export const TAMER_ITEM = "arachno:spider_tamer";
export const Variant = { NETHERITE: "netherite", CAMO: "camo" };

const ATTACK_REACH = 3.5;
const REACH_SCALE_CAP = 2.0;       // the giant form doesn't melee from absurd distances
const SIZE_CHANGING = 0.08;        // size change per tick that counts as fast (see engine/gait.js)
const FIRE_CAUSES = new Set(["fire", "fireTick", "lava", "magma", "campfire", "soulCampfire"]);
// Damage a worn armour suit reduces in Java (everything not tagged BYPASSES_ARMOR).
const ARMOURED_CAUSES = new Set(["entityAttack", "projectile", "entityExplosion", "blockExplosion",
  "contact", "thorns", "fallingBlock", "anvil", "ramAttack", "maceSmash", "stalactite", "piston"]);
const NETHERITE_SUIT = { armour: 20, toughness: 12 };   // 3+8+6+3 armour, 3x4 toughness

function component(entity, id) {
  try { return entity.getComponent(id); } catch (e) { return undefined; }
}

export class SpiderController {
  /** entity: the arachno:spider mob. variant: Variant.*. */
  constructor(entity, variant) {
    this.entity = entity;
    this.id = entity.id;
    this.variant = variant;
    this.dimension = entity.dimension;
    this.level = new BedrockLevel(this.dimension);
    this.tamed = false;
    this.dead = false;          // entityDie seen
    this.detached = false;      // removed by the mod itself (dimension follow, only-one, peaceful)
    this.attackCooldown = 0;
    this.scale = 1.0;
    this.rider = null;          // the player we seated (re-seated if a teleport knocks them off)

    const options = variant === Variant.CAMO
      ? camoSpider(CONFIG.chaseSpeedBlocksPerSecond, CONFIG.legStepSpeed)
      : netheriteSpider(8, CONFIG.chaseSpeedBlocksPerSecond, CONFIG.legStepSpeed);
    const bodyHeight = options.walkGait.stationary.bodyHeight;
    const loc = entity.location;
    let yaw = 0;
    try { yaw = Math.round(entity.getRotation().y / 45) * 45; } catch (e) {}
    this.body = SpiderBody.fromSpawn(this.level, new Vec3(loc.x, loc.y + bodyHeight, loc.z), yaw, 0,
      options.bodyPlan, options.gallopGait, options.walkGait);
    this.body.legTex = null;    // per-leg texture index for the renderer (camo); null = all netherite
    this.ai = new SpiderAI(this.body);
    this.renderer = new SpiderRenderer(this.dimension);
    this.installSounds();

    // Health per variant (attributes are per instance in Java too, since variants share one type).
    const health = component(entity, "minecraft:health");
    const max = variant === Variant.CAMO ? CONFIG.camoMaxHealth : CONFIG.netheriteMaxHealth;
    try { health?.setCurrentValue(max); } catch (e) {}
    try { entity.setProperty("arachno:variant", variant === Variant.CAMO ? 1 : 0); } catch (e) {}
  }

  installSounds() {
    const level = this.level, body = this.body;
    if (this.variant === Variant.CAMO) {
      // The walked-on block's OWN step/landing sound, like player footsteps (Java v1.1.1).
      body.onLegStep = (leg) => {
        const id = blockUnder(level.blocks, leg.endEffector);
        level.playSound(id ? camoFor(id).step : "step.moss", leg.endEffector, CONFIG.variantStepVolume, 1.0);
      };
      body.onHitGround = () => {
        const id = blockUnder(level.blocks, body.position, 6);
        level.playSound(id ? camoFor(id).fall : "fall.moss", body.position, CONFIG.variantLandVolume, 1.0);
      };
    } else {
      body.onLegStep = (leg) => level.playSound("step.netherite", leg.endEffector, 0.3, 1.0);
      body.onHitGround = () => level.playSound("fall.netherite", body.position, 1.0, 0.8);
    }
  }

  get alive() { return !this.dead && isValid(this.entity); }

  /** The player riding us, or null. */
  currentRider() {
    const ride = component(this.entity, "minecraft:rideable");
    let riders = [];
    try { riders = ride ? ride.getRiders() : []; } catch (e) {}
    return riders.find((r) => r.typeId === "minecraft:player") || null;
  }

  /** One tick, in Java's order: size, body physics, behaviour, AI, then the bite and the visuals.
   *  `players` = every player in our dimension. */
  tick(players, isDay) {
    const body = this.body;
    this.level.blocks.newTick();

    // Tamed through the entity's own interaction (entities/spider.json) rather than the script's
    // interact event: pick it up here so the hearts, the sound and the docility all follow.
    if (!this.tamed) {
      let flag = false;
      try { flag = this.entity.getProperty("arachno:tamed") === true; } catch (e) {}
      if (flag) this.tame();
    }

    const rider = this.currentRider() || this.reseat();
    const nearest = nearestPlayer(body.position, players);

    // ---- size (SpiderMob.tick) ----
    const previous = this.scale;
    if (rider) {
      this.scale = approachScale(this.scale, CONFIG.riddenSize);
      body.setSizeScale(this.scale);
      body.setSpeedScale(scaleToSpeedFactor(CONFIG.riddenSize));
    } else {
      const distance = nearest ? body.position.horizontalDistance(nearest.location) : CONFIG.sizeFarDistance;
      this.scale = approachScale(this.scale, targetScale(this.level.blocks, body, this.ai, this.scale, nearest, distance));
      body.setSizeScale(this.scale);
    }
    body.sizeChanging = Math.abs(this.scale / previous - 1) > SIZE_CHANGING;
    body.idleLife = !CONFIG.enableWandering;
    body.groomingChance = CONFIG.groomingChance;

    // ---- body + behaviour + AI (AppState's ECS systems) ----
    body.update();
    if (rider) {
      applyBehaviour(body, this.riddenBehaviour(rider));
    } else {
      applyBehaviour(body, this.ai.behaviour);
      this.ai.update(players, CONFIG.chaseDistance, CONFIG.hostileOnlyAtNight ? isDay : false);
    }

    // ---- the bite: the nearest player, in (scaled) reach, while HUNTING, never when tamed ----
    if (this.attackCooldown > 0) this.attackCooldown--;
    if (!this.tamed && !rider && this.ai.isChasing() && nearest && this.attackCooldown === 0) {
      const reach = ATTACK_REACH * Math.min(this.scale, REACH_SCALE_CAP);
      if (body.position.distanceSquared(nearest.location) <= reach * reach) {
        const hearts = this.variant === Variant.CAMO ? CONFIG.camoAttackDamageHearts : CONFIG.netheriteAttackDamageHearts;
        try { nearest.applyDamage(hearts * 2, { cause: "entityAttack", damagingEntity: this.entity }); } catch (e) {}
        this.attackCooldown = CONFIG.attackCooldownTicks;
      }
    }

    if (this.variant === Variant.CAMO) this.updateCamo();
    this.followBody();
    this.burnVisibly();
    this.renderer.render(body);
  }

  /** Rider steering (Java v1.8.4's omnidirectional riding): face where the rider looks, travel
   *  where their movement keys point - forward/back and strafe, analog sticks included. */
  riddenBehaviour(rider) {
    let move = { x: 0, y: 0 };
    try { move = rider.inputInfo.getMovementVector(); } catch (e) {}
    const forward = move.y || 0, strafe = move.x || 0;
    let look = { x: 0, z: 1 };
    try { look = rider.getViewDirection(); } catch (e) {}
    const fwd = new Vec3(look.x, 0, look.z);
    if (fwd.lengthSquared() < 1e-6) return new StayStillBehaviour();
    fwd.normalize();
    if (forward * forward + strafe * strafe < 1e-4) return new StayStillBehaviour();
    const left = new Vec3(fwd.z, 0, -fwd.x);
    const dir = fwd.copy().mul(forward).add(left.mul(strafe));
    if (dir.lengthSquared() < 1e-6) return new StayStillBehaviour();
    return new DirectionBehaviour(fwd, dir.normalize());
  }

  /** Every tick we move the mob to the body; if that ever unseats the rider without them asking
   *  (sneaking is how you get off), put them straight back. */
  reseat() {
    const p = this.rider;
    if (!p) return null;
    if (!isValid(p) || p.isSneaking || p.dimension.id !== this.dimension.id) { this.rider = null; return null; }
    try { if (component(p, "minecraft:riding")) return null; } catch (e) {}
    try {
      const ride = component(this.entity, "minecraft:rideable");
      if (ride && ride.addRider(p)) return p;
    } catch (e) {}
    this.rider = null;
    return null;
  }

  /** Spider Tamer: docile for good - never bites, keeps everything else (chase, growth, speed). */
  tame() {
    if (this.tamed) return false;
    this.tamed = true;
    try { this.entity.setProperty("arachno:tamed", true); } catch (e) {}
    try { this.entity.triggerEvent("arachno:tamed"); } catch (e) {}   // adds the saddle (rideable)
    const p = this.body.position;
    for (let i = 0; i < 9; i++) {
      const s = this.scale;
      this.level.spawnParticle("minecraft:heart_particle",
        new Vec3(p.x + (Math.random() - 0.5) * 2.4 * s, p.y + 0.5 + Math.random() * 1.2, p.z + (Math.random() - 0.5) * 2.4 * s));
    }
    this.level.playSound("random.levelup", p, 1.0, 1.2);
    return true;
  }

  /** Tamed + empty hand: climb on (Java's mobInteract). */
  mount(player) {
    if (!this.tamed) return false;
    try {
      const ride = component(this.entity, "minecraft:rideable");
      if (ride && ride.getRiders().length === 0 && ride.addRider(player)) { this.rider = player; return true; }
    } catch (e) {}
    return false;
  }

  /** ACTIVE CAMOUFLAGE: each leg wears the block under its foot; a foot in the air keeps its last
   *  block, so the legs change as they LAND. */
  updateCamo() {
    const legs = this.body.legs;
    if (!this.body.legTex || this.body.legTex.length !== legs.length) this.body.legTex = legs.map(() => CAMO_SPAWN_TEX);
    legs.forEach((leg, i) => {
      const id = blockUnder(this.level.blocks, leg.endEffector);
      if (id) this.body.legTex[i] = camoFor(id).tex;
    });
  }

  /** The invisible mob (its hitbox) sits on the simulated body, as Java's setPos(body.position). */
  followBody() {
    const p = this.body.position;
    const at = this.entity.location;
    if (Math.abs(at.x - p.x) + Math.abs(at.y - p.y) + Math.abs(at.z - p.z) < 1e-3) return;
    try { this.entity.teleport({ x: p.x, y: p.y, z: p.z }); } catch (e) {}
  }

  /** NETHERITE never burns (Java's fireImmune); CAMO burns, and you SEE it: the mob is invisible
   *  and the legs can't catch fire, so flame and smoke dress the body and the feet. */
  burnVisibly() {
    const fire = component(this.entity, "minecraft:onfire");
    if (!fire) return;
    if (this.variant !== Variant.CAMO) { try { this.entity.extinguishFire(false); } catch (e) {} return; }
    if (this.body.age % 3 !== 0) return;
    const p = this.body.position, s = this.scale;
    const jitter = (r) => (Math.random() - 0.5) * 2 * r;
    this.level.spawnParticle("minecraft:basic_flame_particle", new Vec3(p.x + jitter(0.3 * s), p.y + jitter(0.2 * s), p.z + jitter(0.3 * s)));
    this.level.spawnParticle("minecraft:basic_smoke_particle", new Vec3(p.x, p.y + 0.3 * s, p.z));
    for (const leg of this.body.legs) {
      if (Math.random() < 0.35) this.level.spawnParticle("minecraft:basic_flame_particle", leg.endEffector.copy().add(new Vec3(0, 0.1, 0)));
    }
  }

  /** world.beforeEvents.entityHurt, for this mob: the netherite suit (Java's armour formula) and
   *  the netherite fire immunity. Returns { cancel, damage }. */
  filterDamage(cause, damage) {
    if (this.variant === Variant.CAMO) return { cancel: false, damage };
    if (FIRE_CAUSES.has(cause)) return { cancel: true, damage: 0 };
    if (!ARMOURED_CAUSES.has(cause)) return { cancel: false, damage };
    // CombatRules.getDamageAfterAbsorb: armour soaks up to 80%, less against heavy blows.
    const { armour, toughness } = NETHERITE_SUIT;
    const soak = Math.max(armour * 0.2, Math.min(20, armour - damage / (2 + toughness / 4)));
    return { cancel: false, damage: damage * (1 - soak / 25) };
  }

  /** The trophy: a configurable chance of one netherite ingot, on the FLOOR straight below the
   *  body (Java v1.1.2: never dropped from the giant's height, never the surface above a cave). */
  dropTrophy(spawnItem) {
    if (Math.random() >= CONFIG.netheriteDropChance) return;
    const p = this.body.position;
    const floorY = findFloorBelow(this.level.blocks, p.x, p.y, p.z);
    spawnItem(this.dimension, "minecraft:netherite_ingot", { x: p.x, y: (floorY ?? p.y) + 0.25, z: p.z });
  }

  /** Drop the legs and the simulation. The mob entity itself is the caller's business. */
  dispose() {
    this.renderer.removeAll();
  }
}

export function nearestPlayer(position, players) {
  let best = null, bestD = Infinity;
  for (const p of players) {
    let alive = true;
    try { const h = p.getComponent("minecraft:health"); alive = !h || h.currentValue > 0; } catch (e) {}
    if (!alive) continue;
    const d = position.distanceSquared(p.location);
    if (d < bestD) { bestD = d; best = p; }
  }
  return best;
}
