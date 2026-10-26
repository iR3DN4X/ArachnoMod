// Port of spider/SpiderBody.kt: gravity, drag, rotation, the support polygon and normal force,
// the symmetric height correction, collision, size scaling, the per-tick leg update, and the
// chase's tight-space machinery (THE SQUEEZE descent, doorway/corridor floor and lane pins, and
// the body's own corridor detection), idle grooming, the poison variant's lunge and the Warding
// Idol's barriers.

import { Vec3, Quat, UP, DOWN, FORWARD, lerp, toRadians } from "./vec.js";
import { Leg } from "./leg.js";
import { LegLookUp } from "./gait.js";
import { pointInPolygon, nearestPointInPolygon } from "./chain.js";

const MAX_DESCENT_SPEED = 1.2;

// The poison variant's tarantula strike (see beginLunge/updateLunge): telegraph ticks with the
// front legs raised, then the forward-burst ticks that form the bite window, and the impulse
// speed (blocks/tick at size 1, scaled by the spider's size).
const LUNGE_REAR_TICKS = 8;
const LUNGE_STRIKE_TICKS = 6;
const LUNGE_SPEED = 0.9;

// Round 15 knob (setSizeScale): size changes up to this fraction per tick leave planted feet put.
export const RESCALE = { slideFeetAbove: 0.08 };

export class NormalInfo {
  constructor(normal, origin = null, contactPolygon = null, centreOfMass = null) {
    this.normal = normal; this.origin = origin; this.contactPolygon = contactPolygon; this.centreOfMass = centreOfMass;
  }
}

export class SpiderBody {
  constructor(level, position, orientation, bodyPlan, gallopGait, walkGait) {
    this.level = level;
    this.position = position;
    this.orientation = orientation;
    this.bodyPlan = bodyPlan;
    this.gallopGait = gallopGait;
    this.walkGait = walkGait;

    this.onGround = false;
    this.legs = [];
    this.normal = null;
    this.normalAcceleration = new Vec3();

    this.gallop = false;
    this.isWalking = false;
    this.isRotatingYaw = false;
    this.sizeScale = 1;
    this.speedScale = 1;

    const e = orientation.getEulerAnglesYXZ();
    this.preferredPitch = e.x;
    this.preferredRoll = e.z;
    this.preferredOrientation = orientation.copy();

    this.velocity = new Vec3();
    this.rotationalVelocity = new Vec3();

    this.onLegStep = null;       // (leg) => void
    this.onHitGround = null;     // () => void

    // THE SQUEEZE descent: SpiderAI sets this to the dug-in player's Y every tick it applies.
    this.squeezeTargetY = null;
    // THREADING A DOORWAY: the opening's floor, and the passage's centre line (one of the two).
    this.passageFloorY = null;
    this.passageLaneX = null;
    this.passageLaneZ = null;
    // What is ACTUALLY in force this tick: the pathfinder's intent merged with what the body
    // sees around itself (updateConfinement).
    this.activePinY = null;
    this.activeLaneX = null;
    this.activeLaneZ = null;
    this.activeHeadroom = null;

    // IDLE LIFE (Java v1.1.3, NetherySiloX's breathing + grooming): only while wandering is
    // disabled - the owner sets these two every tick from the config.
    this.idleLife = false;
    this.groomingChance = 0.03;       // per second, once it has stood still for 3 s
    this.age = 0;                     // ticks lived (Java breathes on level.gameTime)
    this.stationaryTimer = 0;
    this.groomingTimer = 0;           // 0 = not grooming, else ticks remaining
    this.groomingOriginalPos0 = new Vec3();
    this.groomingOriginalPos1 = new Vec3();

    // Lunge state (the poison variant's tarantula strike, see updateLunge): 0 = idle, else ticks
    // remaining across both phases (rear-up telegraph, then the forward strike burst).
    this.lungeTimer = 0;
    this.lungeDirection = new Vec3();

    // WARDING IDOL fences ({ x, z, radius }): set each tick by SpiderAI for a wild spider and
    // consumed (then cleared) by the physics step, so a ridden body is never fenced. One-way: a
    // body ALREADY inside a circle is free to walk out (REPEL steers that); one outside can't get in.
    this.barriers = [];
  }

  /** Ceiling height above the floor while the body is in a tight space, else null (SpiderMob caps the size with it). */
  get confinedHeadroom() { return this.activeHeadroom; }

  /** Ask the world whether the body is in a tight space right now, and merge that with whatever
   *  the chase asked for. Runs every tick, in every AI mode. */
  updateConfinement() {
    this.activePinY = this.passageFloorY;
    this.activeLaneX = this.passageLaneX;
    this.activeLaneZ = this.passageLaneZ;
    // Ask about the GROUND PLANE the legs stand on, not the body centre.
    const bodyHeight = this.lerpedGait().bodyHeight;
    const confinement = this.level.confinementAt
      ? this.level.confinementAt(this.position.x, this.position.y - bodyHeight, this.position.z, bodyHeight)
      : null;
    if (!confinement) { this.activeHeadroom = null; return; }
    if (this.activePinY === null) this.activePinY = confinement.floorY;
    if (this.activeLaneX === null) this.activeLaneX = confinement.laneX;
    if (this.activeLaneZ === null) this.activeLaneZ = confinement.laneZ;
    this.activeHeadroom = confinement.headroom;
  }

  get gait() { return this.gallop ? this.gallopGait : this.walkGait; }

  /** yaw/pitch in Minecraft degrees, as Location.yaw/pitch. */
  static fromSpawn(level, position, yaw, pitch, bodyPlan, gallopGait, walkGait) {
    const orientation = Quat.rotationYXZ(toRadians(-yaw), toRadians(pitch), 0);
    return new SpiderBody(level, position, orientation, bodyPlan, gallopGait, walkGait);
  }

  lerpedGait() {
    if (this.isRotatingYaw) return this.gait.moving.clone();
    const speedFraction = this.velocity.length() / this.gait.maxSpeed;
    return this.gait.stationary.clone().lerp(this.gait.moving, speedFraction);
  }

  forwardDirection() { return FORWARD().rotate(this.orientation); }

  teleport(newPosition) {
    const diff = newPosition.copy().sub(this.position);
    this.position.set(newPosition);
    for (const leg of this.legs) leg.endEffector.add(diff);
  }

  setSizeScale(target) {
    const ratio = target / this.sizeScale;
    if (ratio >= 0.999 && ratio <= 1.001) return;
    this.bodyPlan.scaleBy(ratio);
    this.walkGait.scaleSize(ratio);
    this.gallopGait.scaleSize(ratio);
    for (const leg of this.legs) {
      leg.chain.segments.forEach((segment, i) => {
        const plan = leg.legPlan.segments[i];
        if (plan) segment.length = plan.length;
      });
    }
    // Java slides every foot in/out with the size so the posture holds. DELIBERATE DEVIATION FROM
    // JAVA (Bedrock only, round 15): for small changes the planted feet stay put and the legs
    // re-step to the new size (round 14's emergency steps) - a spider following at 4-6 blocks
    // wobbles between sizes 0.6 and 0.9, and every wobble slid all eight feet ("dragging").
    // Fast changes (the charge's +12% / -25% per tick) still slide, as in Java.
    if (this.javaWalk || Math.abs(ratio - 1) > RESCALE.slideFeetAbove) {
      for (const leg of this.legs) {
        leg.endEffector.x = this.position.x + (leg.endEffector.x - this.position.x) * ratio;
        leg.endEffector.z = this.position.z + (leg.endEffector.z - this.position.z) * ratio;
      }
    }
    this.sizeScale = target;
  }

  setSpeedScale(target) {
    const ratio = target / this.speedScale;
    if (ratio >= 0.999 && ratio <= 1.001) return;
    for (const gait of [this.walkGait, this.gallopGait]) {
      gait.maxSpeed *= ratio;
      gait.moveAcceleration *= ratio;
    }
    this.speedScale = target;
  }

  updatePreferredAngles() {
    const currentEuler = this.orientation.getEulerAnglesYXZ();
    const gait = this.gait;

    if (gait.disableAdvancedRotation) {
      this.preferredPitch = 0;
      this.preferredRoll = 0;
      this.preferredOrientation = Quat.rotationYXZ(currentEuler.y, 0, 0);
      return;
    }

    const legs = this.legs;
    if (legs.length < 2) return;
    const getPos = (leg) => leg.groundPosition ?? leg.restPosition;

    const frontLeft = getPos(legs[0]);
    const frontRight = getPos(legs[1]);
    const backLeft = getPos(legs[legs.length - 2]);
    const backRight = getPos(legs[legs.length - 1]);

    const forwardLeft = frontLeft.copy().sub(backLeft);
    const forwardRight = frontRight.copy().sub(backRight);
    const forward = forwardLeft.add(forwardRight).mul(0.5);

    const sideways = new Vec3();
    for (let i = 0; i < legs.length; i += 2) {
      const left = legs[i];
      const right = legs[i + 1];
      if (!left || !right) continue;
      sideways.add(getPos(right).copy().sub(getPos(left)));
    }

    this.preferredPitch = lerp(forward.pitch(), this.preferredPitch, gait.preferredRotationLerpFraction);
    this.preferredRoll = lerp(sideways.pitch(), this.preferredRoll, gait.preferredRotationLerpFraction);

    if (this.preferredPitch < gait.preferLevelBreakpoint) this.preferredPitch *= 1 - gait.preferLevelBias;
    if (this.preferredRoll < gait.preferLevelBreakpoint) this.preferredRoll *= 1 - gait.preferLevelBias;

    this.preferredOrientation = Quat.rotationYXZ(currentEuler.y, this.preferredPitch, this.preferredRoll);
  }

  init() {
    this.legs = this.bodyPlan.legs.map((plan) => new Leg(this, plan));
  }

  update() {
    if (this.legs.length === 0) {
      this.init();
      if (this.legs.length === 0) return;
    }
    const gait = this.gait;

    this.updatePreferredAngles();
    this.updateConfinement();   // must precede calcPreferredY and the collision step below

    const groundedLegs = this.legs.filter((l) => l.isGrounded());
    const fractionOfLegsGrounded = groundedLegs.length / this.legs.length;

    // gravity + air resistance
    this.velocity.y -= gait.gravityAcceleration;
    this.velocity.y *= (1 - gait.airDragCoefficient);

    // apply rotational velocity
    const rv = this.rotationalVelocity;
    const rotVelocity = Quat.rotationYXZ(rv.y, rv.x, rv.z);
    this.orientation.set(rotVelocity.mul(this.orientation));

    // drag while leg on ground
    if (!this.isWalking) {
      const legDrag = 1 - gait.groundDragCoefficient * fractionOfLegsGrounded;
      this.velocity.x *= legDrag;
      this.velocity.z *= legDrag;
    }

    // rotational drag
    rv.mul(1 - gait.rotationalDragCoefficient * fractionOfLegsGrounded);

    // drag while body on ground
    if (this.onGround) {
      this.velocity.x *= 0.5;
      this.velocity.z *= 0.5;
      rv.mul(0.5);
    }

    const normal = this.calcNormal();
    this.normal = normal;

    this.normalAcceleration = new Vec3();
    if (normal) {
      const preferredY = this.calcPreferredY();
      const preferredYAcceleration = Math.max(0, preferredY - this.position.y - this.velocity.y);
      const capableAcceleration = gait.bodyHeightCorrectionAcceleration * fractionOfLegsGrounded;
      const accelerationMagnitude = Math.min(preferredYAcceleration, capableAcceleration);

      this.normalAcceleration = normal.normal.copy().mul(accelerationMagnitude);
      if (this.normalAcceleration.horizontalLength() > this.normalAcceleration.y) {
        // Java switches the support OFF here (the centre of mass overhangs the planted feet), and
        // the body sags under gravity - in a close chase all the way to the floor, then springs
        // back up: the "bouncing" where the legs meet, with the stranded legs dragged behind (the
        // Java mod does it too). DELIBERATE DEVIATION (javaWalk restores Java): keep holding the
        // body up, straight up only - no sideways push, exactly like Java's zeroed case - while
        // legs are planted. With no planted legs, capableAcceleration is 0 and it falls as before.
        // Only ever up to its STANDING height over the ground right beneath it: legs planted up a
        // wall raise the preferred height, and lifting toward that would carry the body up into
        // the wall (a tunnel mouth turned into a climb over the hill). Java's zeroing never
        // lifted; this never lifts past standing either - it only stops the sag.
        // One or two legs can't hold a body up at all: those keep Java's rule and DRAG it along
        // the floor - the "1 leg crawls, 2 legs flail" netheriteLegs is meant to give.
        if (this.javaWalk || this.javaSupport || this.legs.length < 3) {
          this.normalAcceleration.mul(0);
        } else {
          const standing = this.lerpedGait().bodyHeight;
          const floor = this.level.raycastGround(this.position, DOWN(), standing * 1.5);
          // A floor genuinely BELOW the body only - not the "inside a block" hit at the ray's start
          // (a body pushed into a pillar must be left to the collision step, as in Java).
          const below = floor && floor.y < this.position.y - 0.05;
          const lift = below ? Math.max(0, Math.min(accelerationMagnitude, floor.y + standing - this.position.y - this.velocity.y)) : 0;
          this.normalAcceleration.set(0, lift, 0);
        }
      }
      this.velocity.add(this.normalAcceleration);
    }

    // Symmetric height correction (pull DOWN toward the preferred height, terminal-speed capped).
    {
      const preferredY = this.calcPreferredY();
      const excess = this.position.y + this.velocity.y - preferredY;
      if (excess > 0) {
        this.velocity.y -= Math.min(excess, gait.bodyHeightCorrectionAcceleration);
        if (this.velocity.y < -MAX_DESCENT_SPEED) this.velocity.y = -MAX_DESCENT_SPEED;
      }
    }

    // apply velocity
    const beforeX = this.position.x;
    const beforeZ = this.position.z;
    this.position.add(this.velocity);

    // Hold the centre line of a passage being threaded, and kill the lateral velocity pushing off it.
    if (this.activeLaneX !== null) { this.position.x += (this.activeLaneX - this.position.x) * 0.6; this.velocity.x *= 0.2; }
    if (this.activeLaneZ !== null) { this.position.z += (this.activeLaneZ - this.position.z) * 0.6; this.velocity.z *= 0.2; }

    this.applyBarriers(beforeX, beforeZ);

    // resolve collision
    const heightPin = this.activePinY !== null ? this.activePinY : this.squeezeTargetY;
    if (heightPin !== null) {
      // Threading a doorway, corridor or squeeze-hole: the collision resolve casts from a block
      // ABOVE the body - inside the ceiling in any tight space - so it would shove the body a
      // block upward every tick. The height pin owns the body height in here; just keep it off
      // the floor.
      const minY = heightPin + 0.05;
      if (this.position.y < minY) {
        this.position.y = minY;
        if (this.velocity.y < 0) this.velocity.y = 0;
      }
      this.onGround = true;
    } else {
      const collision = this.level.resolveCollision(this.position, new Vec3(0, Math.min(-1, -Math.abs(this.velocity.y)), 0));
      if (collision) {
        this.onGround = true;
        const didHit = collision.offset.length() > (gait.gravityAcceleration * 2) * (1 - gait.airDragCoefficient);
        if (didHit) this.onHitGround?.();
        this.position.y = collision.position.y;
        if (this.velocity.y < 0) this.velocity.y *= -gait.bounceFactor;
        if (this.velocity.y < gait.gravityAcceleration) this.velocity.y = 0;
      } else {
        this.onGround = this.level.isOnGround(this.position, DOWN().rotate(this.orientation));
      }
    }

    const updateOrder = gait.type.getLegsInUpdateOrder(this);
    for (const leg of updateOrder) leg.updateMemo();
    for (const leg of updateOrder) leg.update();

    // Idle grooming and the lunge both override the front legs' pose, so they must run AFTER the
    // leg updates (they never overlap - beginLunge waits out a grooming).
    this.updateGrooming();
    this.updateLunge();
    this.age++;

    this.updatePreferredAngles();
  }

  // ---- Warding idol barriers (Java v1.8.5) ----------------------------------------------------

  /** A body that just crossed INTO a ward's circle is put back on its rim and the part of its
   *  velocity driving inward is removed, so anything that would carry it in (a chase, a lunge,
   *  plain momentum) slides it along the edge instead. Physics rather than AI on purpose: it holds
   *  whatever the AI or a lunge asks for. A body that started the tick inside is left alone: it is
   *  on its way out, and snapping it to the rim would be a teleport. */
  applyBarriers(beforeX, beforeZ) {
    const fence = this.barriers;
    this.barriers = [];
    for (const b of fence) {
      const bx = beforeX - b.x;
      const bz = beforeZ - b.z;
      const before = Math.sqrt(bx * bx + bz * bz);
      if (before < b.radius - 1e-3) continue;
      const nx = this.position.x - b.x;
      const nz = this.position.z - b.z;
      const now = Math.sqrt(nx * nx + nz * nz);
      if (now >= b.radius) continue;
      const ux = now > 1e-6 ? nx / now : bx / before;
      const uz = now > 1e-6 ? nz / now : bz / before;
      this.position.x = b.x + ux * b.radius;
      this.position.z = b.z + uz * b.radius;
      const inward = this.velocity.x * ux + this.velocity.z * uz;
      if (inward < 0) {
        this.velocity.x -= inward * ux;
        this.velocity.z -= inward * uz;
      }
    }
  }

  // ---- The lunge (the poison variant's tarantula strike, Java v1.3.0) ------------------------
  // Real tarantulas rear up with their front legs raised, THEN strike. beginLunge starts the
  // sequence: LUNGE_REAR_TICKS of telegraph (the front legs sweep up and forward while
  // calcPreferredY lifts the body - the threat pose), then one hard velocity impulse hurls the
  // body at the target for LUNGE_STRIKE_TICKS - the only window in which the mob lets the poison
  // bite land. Same trick as grooming: front legs isDisabled + endEffector steering.

  get isLunging() { return this.lungeTimer > 0; }

  /** The forward-burst phase - the bite window. */
  get isLungeStriking() { return this.lungeTimer >= 1 && this.lungeTimer <= LUNGE_STRIKE_TICKS; }

  beginLunge(direction) {
    if (this.lungeTimer > 0 || this.groomingTimer > 0) return;
    if (this.legs.length < 2) return;
    this.lungeDirection.set(direction.x, 0, direction.z);
    if (this.lungeDirection.lengthSquared() < 1e-6) return;
    this.lungeDirection.normalize();
    this.lungeTimer = LUNGE_REAR_TICKS + LUNGE_STRIKE_TICKS;
    this.legs[0].isDisabled = true;
    this.legs[1].isDisabled = true;
  }

  /** End the sequence (bite landed, or it played out): give the front legs back to the gait,
   *  which replants them with normal steps - no snap. */
  endLunge() {
    this.lungeTimer = 0;
    if (this.legs[0]) this.legs[0].isDisabled = false;
    if (this.legs[1]) this.legs[1].isDisabled = false;
  }

  updateLunge() {
    if (this.lungeTimer <= 0) return;
    const leg0 = this.legs[0], leg1 = this.legs[1];
    if (!leg0 || !leg1) { this.endLunge(); return; }

    // Telegraph over -> one hard impulse toward the target, with a slight hop so the strike
    // leaves the ground. Ground drag bleeds it off across the strike window.
    if (this.lungeTimer === LUNGE_STRIKE_TICKS + 1) {
      const burst = LUNGE_SPEED * Math.max(0.6, Math.min(2.5, this.sizeScale));
      this.velocity.add(this.lungeDirection.copy().mul(burst));
      this.velocity.y += 0.3 * burst;
    }

    // The front legs are held up and forward of the body for the whole sequence - the threat
    // pose - swept smoothly toward the raised target during the telegraph and riding along with
    // the body through the strike. legs[0] = front-right, legs[1] = front-left.
    const up = UP();
    const right = this.lungeDirection.crossNew(up).normalize();
    for (let i = 0; i <= 1; i++) {
      const leg = i === 0 ? leg0 : leg1;
      const sideDir = i === 0 ? 1 : -1;
      const target = this.position.copy()
        .add(this.lungeDirection.copy().mul(0.9 * this.sizeScale))
        .add(up.copy().mul(0.8 * this.sizeScale))
        .add(right.copy().mul(0.35 * sideDir * this.sizeScale));
      leg.endEffector.lerp(target, 0.35);
    }

    this.lungeTimer--;
    if (this.lungeTimer === 0) this.endLunge();
  }

  // ---- Idle grooming (Java v1.1.3, contributed by NetherySiloX) ----------------------------
  // Only while wandering is DISABLED: once the spider has stood still for 3 seconds, a
  // per-second chance (groomingChance) starts a 5-second sequence - the front leg pair lifts to
  // the mouth and "cleans" with a smooth sweeping rub (opposite phases per leg, eased in/out),
  // while calcPreferredY leans the body down. The legs are flagged isDisabled so the gait leaves
  // them alone, and the ease returns them to their stored ground positions before the flag is
  // lifted - no snap at either end.
  updateGrooming() {
    const speedSq = this.velocity.lengthSquared();
    if (this.idleLife) {
      if (speedSq < 0.001) {
        this.stationaryTimer++;
        if (this.groomingTimer <= 0 && this.stationaryTimer > 60) {
          const chancePerTick = this.groomingChance / 20;
          if (chancePerTick > 0 && Math.random() < chancePerTick) this.beginGrooming();
        }
      } else {
        this.stationaryTimer = 0;
        // Real movement interrupts grooming; tiny physics jitter (< 0.01 sq) does not.
        if (speedSq > 0.01 && this.groomingTimer > 0) this.cancelGrooming();
      }
    } else {
      this.stationaryTimer = 0;
      if (this.groomingTimer > 0) this.cancelGrooming();
    }

    if (this.groomingTimer <= 0) return;
    const leg0 = this.legs[0];
    const leg1 = this.legs[1];
    if (!leg0 || !leg1) { this.cancelGrooming(); return; }

    const progress = 1 - this.groomingTimer / 100;
    const smoothT = this.groomingSmoothT();
    const forward = FORWARD().rotate(this.orientation);
    const up = UP().rotate(this.orientation);
    const right = forward.crossNew(up).normalize();

    // The "mouth": just in front of and slightly below the body centre.
    const mouthPos = this.position.copy()
      .add(forward.copy().mul(0.5 * this.sizeScale))
      .add(up.copy().mul(-0.05 * this.sizeScale));

    // Rub only through the middle of the sequence (fades in after the lift, out before the return).
    const rubIntensity = progress < 0.2 || progress > 0.8 ? 0
      : progress < 0.3 ? (progress - 0.2) / 0.1
      : progress > 0.7 ? (0.8 - progress) / 0.1
      : 1;

    for (let i = 0; i <= 1; i++) {
      const leg = i === 0 ? leg0 : leg1;
      const originalPos = i === 0 ? this.groomingOriginalPos0 : this.groomingOriginalPos1;
      const sideDir = i === 0 ? 1 : -1;   // legs[0] = front-right, legs[1] = front-left
      const targetPos = mouthPos.copy().add(right.copy().mul(0.15 * sideDir * this.sizeScale));
      if (rubIntensity > 0) {
        // Smooth, deliberate sweeping motion: 3 full cycles, opposite phase per leg.
        const rubPhase = progress * Math.PI * 6 + (i === 0 ? 0 : Math.PI);
        targetPos.add(up.copy().mul(Math.sin(rubPhase) * 0.08 * this.sizeScale * rubIntensity));
        targetPos.add(forward.copy().mul(Math.cos(rubPhase) * 0.04 * this.sizeScale * rubIntensity));
      }
      // Arc from the stored ground position up to the mouth; smoothT eases back to 0 at the end,
      // so the same lerp carries the leg home.
      const arcHeight = Math.sin(smoothT * Math.PI) * 0.4 * this.sizeScale;
      leg.endEffector.set(originalPos.copy().lerp(targetPos, smoothT).add(up.copy().mul(arcHeight)));
    }

    this.groomingTimer--;
    if (this.groomingTimer === 0) this.cancelGrooming();
  }

  /** Smoothstep envelope for the grooming sequence: eases in over the first 20%, holds, eases out. */
  groomingSmoothT() {
    const progress = 1 - this.groomingTimer / 100;
    const f = progress < 0.2 ? progress / 0.2 : progress > 0.8 ? (1 - progress) / 0.2 : 1;
    return f * f * (3 - 2 * f);
  }

  beginGrooming() {
    const leg0 = this.legs[0], leg1 = this.legs[1];
    if (!leg0 || !leg1 || leg0.isMoving || leg1.isMoving) return;   // wait for both feet planted
    this.groomingTimer = 100;   // 5 seconds
    leg0.isDisabled = true;
    leg1.isDisabled = true;
    this.groomingOriginalPos0.set(leg0.endEffector);
    this.groomingOriginalPos1.set(leg1.endEffector);
  }

  cancelGrooming() {
    if (this.legs[0]) this.legs[0].isDisabled = false;
    if (this.legs[1]) this.legs[1].isDisabled = false;
    this.groomingTimer = 0;
  }

  legsInPolygonalOrder() {
    const idx = this.legs.map((_, i) => i);
    const lefts = idx.filter((i) => LegLookUp.isLeftLeg(i));
    const rights = idx.filter((i) => LegLookUp.isRightLeg(i));
    return [...lefts, ...rights.reverse()];
  }

  calcPreferredY() {
    const gait = this.gait;
    const lerped = this.lerpedGait();
    const lookAhead = this.position.copy().add(this.velocity);
    const ground = this.level.raycastGround(lookAhead, DOWN().rotate(this.preferredOrientation), lerped.bodyHeight);
    const groundY = ground ? ground.y : -Number.MAX_VALUE;

    const averageY = this.legs.reduce((s, l) => s + l.target.position.y, 0) / this.legs.length + lerped.bodyHeight;

    const pivot = gait.legChainPivotMode(this);
    const target = UP().rotate(pivot).mul(gait.maxBodyDistanceFromGround);
    let targetY = Math.max(averageY, groundY + target.y);

    // Idle breathing + grooming lean (Java v1.1.3; only while wandering is disabled, blended out
    // as soon as the spider actually moves).
    if (this.idleLife) {
      const horizontalSpeed = Math.hypot(this.velocity.x, this.velocity.z);
      const breathingFactor = Math.max(0, Math.min(1, 1 - horizontalSpeed / 0.05));
      // ~4.5 s per breath cycle; depth scales with the spider's size.
      if (breathingFactor > 0) targetY += Math.sin(this.age * 0.07) * 0.08 * this.sizeScale * breathingFactor;
      if (this.groomingTimer > 0) targetY -= this.groomingSmoothT() * 0.15 * this.sizeScale;
    }

    // Rear up while a lunge telegraphs: the body lifts as the front legs rise - the tarantula
    // threat pose right before the strike (see updateLunge).
    if (this.lungeTimer > LUNGE_STRIKE_TICKS) targetY += 0.22 * this.sizeScale;

    // Doorway/corridor: stand at the passage's own floor - at normal height, or as close under
    // the ceiling as this space allows, whichever is lower - never at the wall top the legs found.
    const passY = this.activePinY;
    if (passY !== null) {
      const room = this.activeHeadroom !== null ? this.activeHeadroom : Number.MAX_VALUE;
      const stand = Math.min(lerped.bodyHeight, Math.max(room - 0.3, 0.15));
      const standY = passY + stand;
      if (standY < targetY) targetY = standY;
    }

    // THE SQUEEZE descent: sink toward the floor of the hole instead of hovering at rim height.
    const squeezeY = this.squeezeTargetY;
    if (squeezeY !== null) {
      const scanDistance = Math.max(this.position.y - squeezeY, 0) + 2;
      const floor = this.level.raycastGround(this.position, DOWN(), scanDistance);
      const standY = (floor ? floor.y : squeezeY) + lerped.bodyHeight;
      if (standY < targetY) targetY = standY;
    }

    return lerp(this.position.y, targetY, gait.bodyHeightCorrectionFactor);
  }

  applyStabilization(normal) {
    if (!normal.origin || !normal.centreOfMass) return;
    const gait = this.gait;
    if (normal.origin.horizontalDistance(normal.centreOfMass) < gait.polygonLeeway) {
      normal.origin.x = normal.centreOfMass.x;
      normal.origin.z = normal.centreOfMass.z;
    }
    const stabilizationTarget = normal.origin.copy();
    stabilizationTarget.y = normal.centreOfMass.y;
    normal.centreOfMass.lerp(stabilizationTarget, gait.stabilizationFactor);
    normal.normal.set(normal.centreOfMass).sub(normal.origin).normalize();
  }

  calcNormal() {
    const centreOfMass = this.legs.reduce((s, l) => s.add(l.endEffector), new Vec3()).mul(1 / this.legs.length);
    centreOfMass.lerp(this.position, 0.5);
    centreOfMass.y += 0.01;

    const groundedLegs = this.legsInPolygonalOrder().map((i) => this.legs[i]).filter((l) => l.isGrounded());
    if (groundedLegs.length === 0) return null;

    const legsPolygon = groundedLegs.map((l) => l.endEffector.copy());
    const polygonCenterY = legsPolygon.reduce((s, p) => s + p.y, 0) / legsPolygon.length;

    if (legsPolygon.length === 1) {
      const origin = groundedLegs[0].endEffector.copy();
      const n = new NormalInfo(centreOfMass.copy().sub(origin).normalize(), origin, legsPolygon, centreOfMass);
      this.applyStabilization(n);
      return n;
    }

    const polygon2D = legsPolygon.map((p) => ({ x: p.x, y: p.z }));
    if (pointInPolygon({ x: centreOfMass.x, y: centreOfMass.z }, polygon2D)) {
      return new NormalInfo(new Vec3(0, 1, 0), new Vec3(centreOfMass.x, polygonCenterY, centreOfMass.z), legsPolygon, centreOfMass);
    }

    const point = nearestPointInPolygon({ x: centreOfMass.x, y: centreOfMass.z }, polygon2D);
    const origin = new Vec3(point.x, polygonCenterY, point.y);
    const n = new NormalInfo(centreOfMass.copy().sub(origin).normalize(), origin, legsPolygon, centreOfMass);
    this.applyStabilization(n);
    return n;
  }
}
