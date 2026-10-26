// Port of entity/SpiderMob.kt: the spider as a real mob.
//
// As in Java, the mob is an INVISIBLE hittable entity (`arachno:spider`) that owns one simulated
// body; what you see is the legs (render.js). The mob provides what the Java SpiderMob did:
//   - a health pool per variant (netherite 350 inside a full netherite suit's armour, camo 600),
//   - a hitbox that follows the simulated body,
//   - the bite (per-variant hearts, every attackCooldownTicks, only while CHASING, never tamed),
//   - taming with the Spider Tamer, then riding it (all four movement keys, facing your view),
//   - a netherite-ingot trophy on death, dropped on the floor directly beneath the body,
//   - the variants: NETHERITE (fireproof, clanks), CAMO (active camouflage, burns visibly),
//     POISON (warped wart; rears up, LUNGES and injects Poison II; fixed size) and HUNTER (pitch
//     black, silent, fixed size, moves only while unwatched, blinds you within 16 blocks),
//   - the ENRAGED boss: feed the netherite variant a netherite ingot (boss bar, more health, speed
//     and bite, a red storm of an aura, a guaranteed netherite BLOCK),
//   - Java v1.8.6: nobody in creative or spectator is hunted, bitten or blinded, and a TAMED
//     spider goes for the mob its owner punches while in creative (ownerPunched).
// Movement, facing and speed come from SpiderAI (ai.js); this mob owns size and the bite.

import { Vec3 } from "./engine/vec.js";
import { SpiderBody } from "./engine/body.js";
import { netheriteSpider, camoSpider, poisonSpider, hunterSpider } from "./engine/presets.js";
import { applyBehaviour, DirectionBehaviour, StayStillBehaviour } from "./engine/behaviour.js";
import { BedrockLevel } from "./platform.js";
import { SpiderRenderer, isValid } from "./render.js";
import { CONFIG } from "./config.js";
import { approachScale, targetScale, scaleToSpeedFactor, riddenScale } from "./mob.js";
import { SpiderAI, fightable } from "./ai.js";
import { findFloorBelow } from "./ground.js";
import { camoFor, blockUnder, NETHERITE_TEX, CAMO_SPAWN_TEX } from "./camo.js";
import { isSafe } from "./wards.js";
import { isCreative, isSpectator } from "./enums.js";

export const SPIDER_TYPE = "arachno:spider";
export const TAMER_ITEM = "arachno:spider_tamer";
export const Variant = { NETHERITE: "netherite", CAMO: "camo", POISON: "poison", HUNTER: "hunter" };
const VARIANT_PROPERTY = { netherite: 0, camo: 1, poison: 2, hunter: 3 };

/** Roll a spawn's variant, in Java's order: the pitch-black HUNTER first, then the venomous POISON,
 *  then the mossy CAMO, else the classic NETHERITE. Natural spawns and spawn eggs alike. */
export function rollVariant() {
  if (Math.random() < CONFIG.hunterVariantChance) return Variant.HUNTER;
  if (Math.random() < CONFIG.poisonVariantChance) return Variant.POISON;
  if (Math.random() < CONFIG.camoVariantChance) return Variant.CAMO;
  return Variant.NETHERITE;
}

const ATTACK_REACH = 3.5;
const REACH_SCALE_CAP = 2.0;       // the giant form doesn't melee from absurd distances
// The poison variant starts its rear-up + lunge when the player is within this many bite-reaches:
// far enough for the leap to read as a leap, close enough to connect.
const LUNGE_RANGE_FACTOR = 2.2;
// How often the hunter tops up its blindness while you stay in range - well under the effect's own
// duration, so it never lapses until you actually escape.
const BLINDNESS_REFRESH_TICKS = 40;
const WART = camoFor("warped_wart_block");
const BLACK_CONCRETE = camoFor("black_concrete");
const SIZE_CHANGING = 0.08;        // size change per tick that counts as fast (see engine/gait.js)
const FIRE_CAUSES = new Set(["fire", "fireTick", "lava", "magma", "campfire", "soulCampfire"]);
// Damage a worn armour suit reduces in Java (everything not tagged BYPASSES_ARMOR).
const ARMOURED_CAUSES = new Set(["entityAttack", "projectile", "entityExplosion", "blockExplosion",
  "contact", "thorns", "fallingBlock", "anvil", "ramAttack", "maceSmash", "stalactite", "piston"]);
// THE ENRAGED BOSS's health pool, in the entity's own points (entities/spider.json, group
// arachno:enraged). Bedrock's boss bar shows health / MAX health and no API can change a mob's max,
// so the boss carries a fixed 1000-point pool and incoming damage is scaled to it: the bar reads
// exactly right for any enragedMaxHealth.
// The Nether's bedrock ceiling: an irregular band from y 123 up to the roof surface at 128.
const NETHER_CEILING_BAND = 118;
const NETHER_ROOF_FLOOR = 126;
const BOSS_POOL = 1000;
const RAGE_DUST = "minecraft:redstone_wire_dust_particle";
const RAGE_SPARK = "minecraft:electric_spark_particle";
const SOUL_FLAME = "minecraft:blue_flame_particle";

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
    // THE PET'S FIGHT (Java v1.8.6): whoever tamed it is its owner while it lives, and the mob they
    // last punched in creative is what it goes after. Never saved - neither is the spider.
    this.ownerId = null;
    this.fightTarget = null;
    this.dead = false;          // entityDie seen
    this.detached = false;      // removed by the mod itself (dimension follow, only-one, peaceful)
    this.attackCooldown = 0;
    this.blindnessCooldown = 0;
    this.scale = 1.0;
    // ENRAGED boss mode (netherite only, Java v1.5.0): fed a netherite ingot, it gains health,
    // speed and bite, a boss bar and a storm of an aura - and always drops a FULL netherite block.
    this.enraged = false;
    this.healthScale = 1;          // entity health points per real HP (BOSS_POOL / max once enraged)
    this.pendingBossHealth = null; // real HP to write once the boss pool is in place

    // The NETHERITE variant wears what it's made of - a full netherite suit's armour, from the
    // config at spawn (netheriteArmor 0 = a bare health pool) - and has netheriteLegs legs.
    this.suit = { armour: CONFIG.netheriteArmor, toughness: CONFIG.netheriteArmorToughness };
    const speed = CONFIG.chaseSpeedBlocksPerSecond, step = CONFIG.legStepSpeed;
    const options = variant === Variant.CAMO ? camoSpider(speed, step)
      : variant === Variant.POISON ? poisonSpider(speed, step)
      : variant === Variant.HUNTER ? hunterSpider(speed, step)
      : netheriteSpider(CONFIG.netheriteLegs, speed, step);
    const bodyHeight = options.walkGait.stationary.bodyHeight;
    const loc = entity.location;
    let yaw = 0;
    try { yaw = Math.round(entity.getRotation().y / 45) * 45; } catch (e) {}
    this.body = SpiderBody.fromSpawn(this.level, new Vec3(loc.x, loc.y + bodyHeight, loc.z), yaw, 0,
      options.bodyPlan, options.gallopGait, options.walkGait);
    this.body.variantKey = variant;   // the AI reads it (the hunter stalks instead of charging)
    this.body.legTex = null;    // per-leg texture index for the renderer (camo); null = all netherite
    // One skin for every leg: warped wart for the poison variant, black concrete for the hunter.
    this.fixedTex = variant === Variant.POISON ? WART.tex : variant === Variant.HUNTER ? BLACK_CONCRETE.tex : null;
    this.ai = new SpiderAI(this.body);
    this.renderer = new SpiderRenderer(this.dimension);
    this.installSounds();

    // Health per variant (attributes are per instance in Java too, since variants share one type).
    const health = component(entity, "minecraft:health");
    const max = variant === Variant.CAMO ? CONFIG.camoMaxHealth
      : variant === Variant.POISON ? CONFIG.poisonMaxHealth
      : variant === Variant.HUNTER ? CONFIG.hunterMaxHealth
      : CONFIG.netheriteMaxHealth;
    this.maxHealth = max;
    try { health?.setCurrentValue(max); } catch (e) {}
    try { entity.setProperty("arachno:variant", VARIANT_PROPERTY[variant] ?? 0); } catch (e) {}
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
    } else if (this.variant === Variant.POISON) {
      // Locked to the warped wart squish (it IS eerie Nether fungus).
      body.onLegStep = (leg) => level.playSound(WART.step, leg.endEffector, CONFIG.variantStepVolume, 0.9);
      body.onHitGround = () => level.playSound(WART.fall, body.position, CONFIG.variantLandVolume, 0.8);
    } else if (this.variant === Variant.HUNTER) {
      // Dead silent. You will not hear it coming - that is its job.
      body.onLegStep = null;
      body.onHitGround = null;
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
      // That path never says who used the Tamer: the nearest player within reach did.
      if (flag) this.tame(nearestPlayer(body.position, players.filter((p) => body.position.distanceSquared(p.location) < 64)));
    }

    // A rider missing here got off by themselves: getting off is always the rider's call.
    const rider = this.currentRider();
    // SIZE follows the nearest player but a spectator: it still shrinks near you in creative.
    const nearest = nearestPlayer(body.position, players.filter((p) => !isSpectator(p)));

    // THE PET'S FIGHT: drop a target that died, left, or is no longer an order (owner gone or out
    // of creative, or the spider is being ridden), then tell the AI.
    const owner = this.ownerId ? players.find((p) => p.id === this.ownerId) : null;
    if (this.fightTarget && (!this.tamed || rider || !owner || !isCreative(owner) ||
        !fightable(this.fightTarget, this.dimension.id))) {
      this.fightTarget = null;
    }
    this.ai.setPet(this.tamed, this.fightTarget);

    // ---- size (SpiderMob.tick) ----
    const previous = this.scale;
    if (rider) {
      // With enableGrowth off the mount keeps the one fixed size too; riddenSize is ignored.
      const ridden = riddenScale();
      this.scale = approachScale(this.scale, ridden);
      body.setSizeScale(this.scale);
      body.setSpeedScale(scaleToSpeedFactor(ridden));
    } else {
      const distance = nearest ? body.position.horizontalDistance(nearest.location) : CONFIG.sizeFarDistance;
      this.scale = approachScale(this.scale, targetScale(this.level.blocks, body, this.ai, this.scale, nearest, distance, this.variant));
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
    // The POISON variant fights like a real tarantula instead of trading standing hits: in range
    // it REARS UP with its front legs raised (the telegraph), LUNGES, and only a bite landed out of
    // that lunge connects - for less damage, but with Poison II in it.
    if (this.attackCooldown > 0) this.attackCooldown--;
    if (this.blindnessCooldown > 0) this.blindnessCooldown--;
    const hunting = !rider && this.ai.isChasing();
    if (this.variant === Variant.HUNTER && !this.tamed && hunting) this.blindNearby(players);
    // Its quarry is the nearest player it can actually REACH: anyone sheltering more than the bite
    // band inside a Warding Idol is out of bounds, however close. The enraged boss ignores idols.
    // Nobody in creative or spectator, ever.
    const dim = this.dimension.id;
    const prey = nearestPlayer(body.position, players.filter((p) => !isCreative(p) && !isSpectator(p) &&
      (this.enraged || !isSafe(dim, p.location.x, p.location.z))));
    if (this.tamed && !rider && this.fightTarget && this.ai.isFighting()) {
      this.attack(this.fightTarget);           // the mob its owner punched
    } else if (!this.tamed && hunting && prey) {
      this.attack(prey);
    }

    if (this.enraged) {
      this.applyBossHealth();
      if (!rider) this.rageAura();
    }
    if (this.fixedTex !== null && (!body.legTex || body.legTex.length !== body.legs.length)) {
      body.legTex = body.legs.map(() => this.fixedTex);
    }
    if (this.variant === Variant.CAMO) this.updateCamo();
    this.followBody();
    this.burnVisibly();
    // Nobody left in its dimension (everyone went through a portal): take the legs down NOW, while
    // their chunks are still loaded. Legs still standing when a chunk unloads are SAVED with it and
    // come back frozen when you return - the "afterimage". Redrawn the moment someone is back.
    if (players.length === 0) this.renderer.removeAll();
    else this.renderer.render(body);
  }

  /** Out of range of every player in `players`: past the distance its hunt gives up at. */
  outOfRangeOf(players) {
    if (!players.length) return false;
    const limit = CONFIG.chaseDistance * CONFIG.chaseExitDistanceMultiplier;
    const p = this.body.position;
    return players.every((pl) => p.distanceSquared(pl.location) > limit * limit);
  }

  /** Down at (or through) the bottom of the world: falling out of it is how you kill it - never
   *  rescued. */
  fallingOutOfWorld() {
    return this.body.position.y < this.level.blocks.minY + 2;
  }

  /** TELEPORT this very spider - same variant, health, rage and taming - so its feet stand at
   *  `feet`. Body, feet and step targets move together, the legs are redrawn fresh at the new spot
   *  (no handover across the jump) and the AI starts over from there. */
  teleportTo(feet) {
    const body = this.body;
    const delta = new Vec3(feet.x - body.position.x, feet.y + body.walkGait.stationary.bodyHeight - body.position.y, feet.z - body.position.z);
    body.position.add(delta);
    body.velocity.set(0, 0, 0);
    for (const leg of body.legs) {
      leg.endEffector.add(delta);
      leg.previousEndEffector.add(delta);
      if (leg.target && leg.target.position) leg.target.position.add(delta);
    }
    if (body.lungeTimer > 0) body.endLunge();
    this.renderer.removeAll();
    this.ai.anchor = body.position.copy();
    this.ai.clearOpening();
    this.followBody();
  }

  /** In the Nether, has it been pushed up through the bedrock ceiling - standing on the roof, or
   *  wedged in the bedrock band - with nobody up there? (Falling OUT of the world is a different
   *  thing and is left alone: that is how you kill it.) */
  stuckInNetherRoof(players) {
    if (this.dimension.id !== "minecraft:nether") return false;
    if (!players.some((p) => p.location.y < NETHER_ROOF_FLOOR)) return false;   // someone's down below
    if (players.some((p) => p.location.y >= NETHER_ROOF_FLOOR)) return false;   // ...but someone's up there too
    const p = this.body.position;
    const ground = p.y - this.body.walkGait.stationary.bodyHeight;
    if (ground >= NETHER_ROOF_FLOOR) return true;                                 // on the roof
    if (p.y < NETHER_CEILING_BAND) return false;
    const c = this.level.blocks.cell(Math.floor(p.x), Math.floor(p.y), Math.floor(p.z));
    return c.loaded && c.id === "bedrock";                                        // in the roof
  }

  /** One go at `target` if it is in (scaled) reach and the bite is off cooldown - a hunted player,
   *  or the mob a tamed spider's owner punched. The POISON variant fights like a real tarantula:
   *  in range it REARS UP (the telegraph), LUNGES, and only a bite landed out of that lunge
   *  connects - for less damage, but with Poison II in it. */
  attack(target) {
    const body = this.body;
    const reach = ATTACK_REACH * Math.min(this.scale, REACH_SCALE_CAP);
    const distSqr = body.position.distanceSquared(target.location);
    if (this.variant === Variant.POISON) {
      const lungeRange = reach * LUNGE_RANGE_FACTOR;
      if (this.attackCooldown === 0 && !body.isLunging && distSqr <= lungeRange * lungeRange) {
        body.beginLunge(new Vec3(target.location.x - body.position.x, 0, target.location.z - body.position.z));
        this.attackCooldown = CONFIG.attackCooldownTicks;   // recovery starts at the leap
      }
      if (body.isLungeStriking && distSqr <= reach * reach) {
        this.bite(target);
        try { target.addEffect("poison", Math.round(CONFIG.poisonEffectSeconds * 20), { amplifier: 1 }); } catch (e) {}
        body.endLunge();   // bite landed - end the strike, so one lunge = one bite
      }
    } else if (this.attackCooldown === 0 && distSqr <= reach * reach) {
      this.bite(target);
      this.attackCooldown = CONFIG.attackCooldownTicks;
    }
  }

  /** One bite: the variant's hearts as an ordinary mob attack, so the victim's armour still applies. */
  bite(target) {
    try { target.applyDamage(this.biteHearts() * 2, { cause: "entityAttack", damagingEntity: this.entity }); } catch (e) {}
  }

  /** The owner punched `target` (world.afterEvents.entityHitEntity). An order only while it is
   *  tamed and NOT ridden, the puncher is its owner and in CREATIVE, and what they hit is a mob -
   *  never a player or anything of ours. A punch thrown while riding is dropped, not saved. */
  ownerPunched(player, target) {
    if (!this.tamed || !player || player.id !== this.ownerId || !isCreative(player) || this.currentRider()) return false;
    if (!target || target.typeId === "minecraft:player" || target.typeId === "minecraft:armor_stand") return false;
    if (String(target.typeId).startsWith("arachno:")) return false;
    if (!fightable(target, this.dimension.id)) return false;
    this.fightTarget = target;
    return true;
  }

  /** Raw bite damage in hearts, before armour: the enraged boss hardest of anything, then netherite,
   *  camo a little less, the hunter softer still (it takes your sight, not your health), the
   *  poison weakest but venomous. */
  biteHearts() {
    if (this.enraged) return CONFIG.enragedAttackDamageHearts;
    switch (this.variant) {
      case Variant.POISON: return CONFIG.poisonAttackDamageHearts;
      case Variant.CAMO: return CONFIG.camoAttackDamageHearts;
      case Variant.HUNTER: return CONFIG.hunterAttackDamageHearts;
      default: return CONFIG.netheriteAttackDamageHearts;
    }
  }

  /** THE HUNTER'S DARK (Java v1.7.0): while it stalks, everyone inside hunterBlindnessRange goes
   *  blind, topped up while they stay in range - the only cure is to get away from it. Everyone,
   *  not just its quarry: standing next to the victim is not a safe seat. */
  blindNearby(players) {
    const range = CONFIG.hunterBlindnessRange;
    if (range <= 0 || this.blindnessCooldown > 0) return;
    const ticks = Math.max(1, Math.round(CONFIG.hunterBlindnessSeconds * 20));
    let blinded = false;
    for (const p of players) {
      let alive = true;
      try { const h = p.getComponent("minecraft:health"); alive = !h || h.currentValue > 0; } catch (e) {}
      if (!alive || this.body.position.distanceSquared(p.location) > range * range) continue;
      if (isCreative(p) || isSpectator(p)) continue;                          // it does not see them
      if (isSafe(this.dimension.id, p.location.x, p.location.z)) continue;   // sheltering past a Warding Idol
      try { p.addEffect("blindness", ticks, { amplifier: 0 }); blinded = true; } catch (e) {}
    }
    if (blinded) this.blindnessCooldown = BLINDNESS_REFRESH_TICKS;
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

  /** Spider Tamer: docile for good - never bites a player, keeps everything else (it follows people,
   *  grows, keeps its speed). `player` (whoever used the Tamer) becomes its owner. */
  tame(player = null) {
    if (this.tamed) return false;
    this.tamed = true;
    this.ownerId = player ? player.id : null;
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

  /** Netherite ingot on the untamed NETHERITE variant -> ENRAGED boss (Java v1.5.0): the boss bar,
   *  health topped up by the enraged bonus (damage already taken is NOT forgiven), more speed and
   *  bite, and an ignition worth the price of the ingot. No name tag (Java v1.8.3): the bar carries
   *  the name. True when it enraged - the caller then takes the ingot. */
  enrage() {
    if (this.enraged || this.tamed || this.variant !== Variant.NETHERITE || !this.alive) return false;
    const oldMax = this.maxHealth;
    const newMax = Math.max(CONFIG.enragedMaxHealth, oldMax);
    this.pendingBossHealth = Math.min(this.realHealth() + (newMax - oldMax), newMax);
    this.enraged = true;
    this.body.enraged = true;      // the AI reads it for speed
    this.maxHealth = newMax;
    this.healthScale = BOSS_POOL / newMax;
    try { this.entity.triggerEvent("arachno:enrage"); } catch (e) {}
    this.applyBossHealth();

    const p = this.body.position, s = this.scale;
    this.level.playSound("ambient.weather.thunder", p, 1.0, 1.4);
    this.scatter(RAGE_DUST, 60, 1.2 * s, 0.8 * s);
    this.scatter(RAGE_SPARK, 40, 1.2 * s, 0.8 * s);
    return true;
  }

  /** Once the boss pool is in place (the component group lands within a tick), write the carried
   *  health into it. */
  applyBossHealth() {
    if (this.pendingBossHealth === null) return;
    const health = component(this.entity, "minecraft:health");
    let max = 0;
    try { max = health ? health.effectiveMax : 0; } catch (e) {}
    if (Math.abs(max - BOSS_POOL) > 1e-6) return;
    try { health.setCurrentValue(this.pendingBossHealth * this.healthScale); } catch (e) {}
    this.pendingBossHealth = null;
  }

  /** Health in real hit points (the boss counts in its 1000-point pool). */
  realHealth() {
    if (this.pendingBossHealth !== null) return this.pendingBossHealth;
    let v = 0;
    try { v = component(this.entity, "minecraft:health").currentValue; } catch (e) {}
    return v / this.healthScale;
  }

  /** The ENRAGED boss wears the storm: red rage dust laced with sparks and soul flame. */
  rageAura() {
    const body = this.body, s = this.scale;
    if (body.age % 2 !== 0) return;
    this.scatter(RAGE_DUST, 3, 0.6 * s, 0.4 * s);
    if (body.age % 6 !== 0) return;
    this.scatter(RAGE_SPARK, 2, 0.7 * s, 0.5 * s);
    const leg = body.legs[Math.floor(Math.random() * body.legs.length)];
    if (leg) this.level.spawnParticle(SOUL_FLAME, leg.endEffector.copy().add(new Vec3(0, 0.1, 0)));
  }

  /** `count` particles scattered round the body, `spread` blocks out sideways and `rise` up/down. */
  scatter(id, count, spread, rise) {
    const p = this.body.position;
    const r = (d) => (Math.random() - 0.5) * 2 * d;
    for (let i = 0; i < count; i++) this.level.spawnParticle(id, new Vec3(p.x + r(spread), p.y + r(rise), p.z + r(spread)));
  }

  /** Tamed + empty hand: climb on (Java's mobInteract). */
  mount(player) {
    if (!this.tamed) return false;
    try {
      const ride = component(this.entity, "minecraft:rideable");
      if (ride && ride.getRiders().length === 0 && ride.addRider(player)) return true;
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

  /** The invisible mob (its hitbox) sits on the simulated body, as Java's setPos(body.position).
   *  If that move ever knocks the rider off, they go straight back on. This is the ONLY place a
   *  rider is put back: Bedrock never reports the dismount key (it is not a Sneak press), so any
   *  other way of losing the rider has to be them getting off. */
  followBody() {
    const p = this.body.position;
    const at = this.entity.location;
    if (Math.abs(at.x - p.x) + Math.abs(at.y - p.y) + Math.abs(at.z - p.z) < 1e-3) return;
    const rider = this.currentRider();
    try { this.entity.teleport({ x: p.x, y: p.y, z: p.z }); } catch (e) {}
    if (rider && !this.currentRider()) {
      try { component(this.entity, "minecraft:rideable")?.addRider(rider); } catch (e) {}
    }
  }

  /** NETHERITE never burns (Java's fireImmune); the others burn, and you SEE it: the mob is
   *  invisible and the legs can't catch fire, so flame and smoke dress the body and the feet. */
  burnVisibly() {
    const fire = component(this.entity, "minecraft:onfire");
    if (!fire) return;
    if (this.variant === Variant.NETHERITE) { try { this.entity.extinguishFire(false); } catch (e) {} return; }
    if (this.body.age % 3 !== 0) return;
    const p = this.body.position, s = this.scale;
    const jitter = (r) => (Math.random() - 0.5) * 2 * r;
    this.level.spawnParticle("minecraft:basic_flame_particle", new Vec3(p.x + jitter(0.3 * s), p.y + jitter(0.2 * s), p.z + jitter(0.3 * s)));
    this.level.spawnParticle("minecraft:basic_smoke_particle", new Vec3(p.x, p.y + 0.3 * s, p.z));
    for (const leg of this.body.legs) {
      if (Math.random() < 0.35) this.level.spawnParticle("minecraft:basic_flame_particle", leg.endEffector.copy().add(new Vec3(0, 0.1, 0)));
    }
  }

  /** world.beforeEvents.entityHurt, for this mob: the netherite suit (Java's armour formula), the
   *  netherite fire immunity, and the enraged boss's pool scaling. Returns { cancel, damage }. */
  filterDamage(cause, damage) {
    if (this.variant !== Variant.NETHERITE) return { cancel: false, damage };
    if (FIRE_CAUSES.has(cause)) return { cancel: true, damage: 0 };
    let real = damage;
    if (ARMOURED_CAUSES.has(cause)) {
      // CombatRules.getDamageAfterAbsorb: armour soaks up to 80%, less against heavy blows.
      const { armour, toughness } = this.suit;
      const soak = Math.max(armour * 0.2, Math.min(20, armour - damage / (2 + toughness / 4)));
      real = damage * (1 - soak / 25);
    }
    if (!this.enraged) return { cancel: false, damage: real };
    // The boss counts in its pool's points. In the tick before the pool lands, count it off the
    // carried health instead.
    if (this.pendingBossHealth !== null) {
      this.pendingBossHealth = Math.max(1, this.pendingBossHealth - real);
      return { cancel: true, damage: 0 };
    }
    return { cancel: false, damage: real * this.healthScale };
  }

  /** The trophy: a configurable chance of one netherite ingot, on the FLOOR straight below the
   *  body (Java v1.1.2: never dropped from the giant's height, never the surface above a cave). */
  dropTrophy(spawnItem) {
    // The ENRAGED boss always pays out: a FULL netherite block where one ingot went in.
    if (!this.enraged && Math.random() >= CONFIG.netheriteDropChance) return;
    const item = this.enraged ? "minecraft:netherite_block" : "minecraft:netherite_ingot";
    const p = this.body.position;
    const floorY = findFloorBelow(this.level.blocks, p.x, p.y, p.z);
    spawnItem(this.dimension, item, { x: p.x, y: (floorY ?? p.y) + 0.25, z: p.z });
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
