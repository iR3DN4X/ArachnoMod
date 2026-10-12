// Port of spider/Leg.kt - ground scanning, trigger/comfort zones, stepping, fall + stuck recovery,
// and the IK chain (straightened toward the knee-up pose, then solved with FABRIK). Faithful.

import { Vec3, UP, DOWN, lerp } from "./vec.js";
import { SplitDistance, SplitDistanceZone, EMERGENCY, emergencyStepsOn } from "./gait.js";
import { KinematicChain, ChainSegment } from "./chain.js";

export class LegTarget {
  constructor(position, isGrounded, id) { this.position = position; this.isGrounded = isGrounded; this.id = id; }
}

export class Leg {
  constructor(spider, legPlan) {
    this.spider = spider;
    this.legPlan = legPlan;

    // memo, recomputed every tick
    this.triggerZone = null;
    this.comfortZone = null;
    this.groundPosition = null;
    this.restPosition = null;
    this.lookAheadPosition = null;
    this.scanStartPosition = null;
    this.scanVector = null;
    this.attachmentPosition = null;

    this.updateMemo();

    // state
    this.target = this.locateGround() ?? this.strandedTarget();
    this.endEffector = this.target.position.copy();
    this.previousEndEffector = this.endEffector.copy();
    this.chain = new KinematicChain(new Vec3(), []);

    this.touchingGround = true;
    this.isMoving = false;
    this.timeSinceBeginMove = 0;
    this.timeSinceStopMove = 0;
    this.ticksOffGround = 0;

    this.isDisabled = false;
    this.isPrimary = false;
    this.canMove = false;
  }

  get isOutsideTriggerZone() { return !this.triggerZone.contains(this.endEffector); }
  get isUncomfortable() { return !this.comfortZone.contains(this.endEffector); }
  isGrounded() { return this.touchingGround && !this.isMoving && !this.isDisabled; }

  updateMemo() {
    const spider = this.spider;
    const lerpedGait = spider.lerpedGait();
    const orientation = spider.gait.scanPivotMode(spider);

    const upVector = UP().rotate(orientation);
    const scanStartAxis = upVector.copy().mul(lerpedGait.bodyHeight * 1.6);
    const scanAxis = upVector.copy().mul(-lerpedGait.bodyHeight * 3.5);

    this.restPosition = this.legPlan.restPosition.copy();
    this.restPosition.add(upVector.copy().mul(-lerpedGait.bodyHeight));
    this.restPosition.rotate(orientation).add(spider.position);

    this.triggerZone = new SplitDistanceZone(this.restPosition, lerpedGait.triggerZone);

    const comfortZoneCenter = this.restPosition.copy();
    comfortZoneCenter.y = lerp(this.restPosition.y, spider.position.y, 0.5);
    const comfortZoneSize = new SplitDistance(
      spider.gait.comfortZone.horizontal,
      spider.gait.comfortZone.vertical + Math.max(0, spider.position.y - this.restPosition.y),
    );
    this.comfortZone = new SplitDistanceZone(comfortZoneCenter, comfortZoneSize);

    this.lookAheadPosition = this.computeLookAhead(this.restPosition, this.triggerZone.size.horizontal);

    this.scanStartPosition = this.lookAheadPosition.copy().add(scanStartAxis);
    this.scanVector = scanAxis;

    this.attachmentPosition = this.legPlan.attachmentPosition.copy().rotate(spider.orientation).add(spider.position);
  }

  update() {
    const index = this.spider.legs.indexOf(this);
    this.legPlan = this.spider.bodyPlan.legs[index] ?? this.legPlan;
    this.updateMovement();
    this.chain = this.buildChain();
  }

  updateMovement() {
    const spider = this.spider;
    const level = spider.level;
    this.previousEndEffector = this.endEffector.copy();

    const gait = spider.gait;
    let didStep = false;

    this.timeSinceBeginMove += 1;
    this.timeSinceStopMove += 1;

    const ground = this.locateGround();
    this.groundPosition = ground ? ground.position : null;

    if (this.isDisabled) {
      this.target = this.disabledTarget();
    } else {
      if (ground) this.target = ground;
      if (!this.target.isGrounded || !this.comfortZone.contains(this.target.position)) this.target = this.strandedTarget();
    }

    // Out of reach: pull the foot back inside the leg's reach so it never renders as a spike.
    const reach = this.legPlan.segments.reduce((s, seg) => s + seg.length, 0);
    let forcedStepNow = false;
    if (reach > 0 && this.endEffector.distance(this.attachmentPosition) > reach * 0.95) {
      this.touchingGround = false;
      const pullBack = this.endEffector.copy().sub(this.attachmentPosition);
      if (pullBack.lengthSquared() > 1e-6) {
        pullBack.normalize().mul(reach * 0.9);
        this.endEffector.set(this.attachmentPosition.copy().add(pullBack));
      }
      // DELIBERATE DEVIATION FROM JAVA (Bedrock only): Java lets the pulled-back foot drop
      // straight back onto the ground where it is - the leg is SLID along the floor (the "dragging
      // feet"). Here it lifts and steps to its target instead, whatever its neighbours are doing.
      // Only toward a target the leg can actually reach - else it would step out, be pulled back
      // and step again every tick. The foot holds still this tick and the step begins NEXT tick,
      // like every step the gait starts (round 15: starting it at once - and carrying the foot
      // with the body - swept it up to 0.9 blocks along the ground in one tick, with no lift).
      if (emergencyStepsOn(spider) && EMERGENCY.forced && !this.isMoving && !this.isDisabled && this.target.isGrounded &&
          this.target.position.distance(this.attachmentPosition) < reach * EMERGENCY.reachable) {
        this.isMoving = true;
        this.timeSinceBeginMove = 0;
        forcedStepNow = true;
      }
    }

    // Free-fall: release feet the body has outrun so the legs fall with it.
    if (this.touchingGround && !spider.onGround &&
        this.endEffector.y - this.restPosition.y > this.comfortZone.size.vertical) {
      this.touchingGround = false;
    }

    // inherit parent velocity while airborne
    if (!this.isGrounded() && !forcedStepNow) {
      this.endEffector.add(spider.velocity);
      this.endEffector.rotateAroundY(spider.rotationalVelocity.y, spider.position);
    }

    // resolve ground collision
    if (!this.touchingGround) {
      const collision = level.resolveCollision(this.endEffector, DOWN());
      if (collision) {
        didStep = true;
        this.touchingGround = true;
        this.endEffector.y = collision.position.y;
      }
    }

    if (forcedStepNow) {
      // (the forced step's first move is next tick)
    } else if (this.isMoving) {
      const legMoveSpeed = gait.legMoveSpeed;
      this.endEffector.moveTowards(this.target.position, legMoveSpeed);

      const targetY = this.target.position.y + gait.legLiftHeight;
      const hDistance = this.endEffector.horizontalDistance(this.target.position);
      if (hDistance > gait.legDropDistance) {
        const d = targetY - this.endEffector.y;
        this.endEffector.y = Math.abs(d) < legMoveSpeed ? targetY : this.endEffector.y + legMoveSpeed * Math.sign(d);
      }

      if (this.endEffector.distance(this.target.position) < 0.0001) {
        this.isMoving = false;
        this.touchingGround = this.checkTouchingGround();
        didStep = this.touchingGround;
      }
    } else {
      this.canMove = spider.gait.type.canMoveLeg(this);
      if (this.canMove) {
        this.isMoving = true;
        this.timeSinceBeginMove = 0;
      }
    }

    // Stuck recovery: no leg hangs in the air while the spider is standing.
    const supported = spider.onGround || spider.legs.some((l) => l !== this && l.isGrounded());
    if (!this.touchingGround && supported) this.ticksOffGround++; else this.ticksOffGround = 0;
    if (this.ticksOffGround > 8) {
      this.ticksOffGround = 0;
      const bodyHeight = spider.lerpedGait().bodyHeight;
      const lift = new Vec3(0, bodyHeight * 1.5, 0);
      let hit = level.raycastGround(this.restPosition.copy().add(lift), DOWN(), bodyHeight * 4);
      if (!hit) {
        const inward = spider.position.copy().sub(this.restPosition);
        inward.y = 0;
        if (inward.lengthSquared() > 1e-6) {
          inward.normalize();
          for (let step = 1; step <= 4 && !hit; step++) {
            const probe = this.restPosition.copy().add(inward.copy().mul(step * 0.5 * bodyHeight)).add(lift);
            hit = level.raycastGround(probe, DOWN(), bodyHeight * 4);
          }
        }
      }
      if (hit) {
        this.endEffector.set(hit);
        this.target = new LegTarget(hit.copy(), true, -2);
        this.touchingGround = true;
        this.isMoving = false;
        didStep = true;
      }
    }

    // NOTE: the Java mod never resets timeSinceStopMove (Cymaera's original reset it on every
    // step), so its cross-pair cooldown is inert. Matched here on purpose: identical gait.
    if (didStep) spider.onLegStep?.(this);
  }

  buildChain() {
    const spider = this.spider;
    if (this.chain.segments.length !== this.legPlan.segments.length) {
      let stride = 0;
      this.chain = new KinematicChain(this.attachmentPosition.copy(), this.legPlan.segments.map((s) => {
        stride += s.length;
        const position = spider.position.copy().add(this.legPlan.restPosition.copy().normalize().mul(stride));
        return new ChainSegment(position, s.length, s.initDirection.copy());
      }));
    }

    this.chain.root.set(this.attachmentPosition);

    if (spider.gait.straightenLegs) {
      const pivot = spider.gait.legChainPivotMode(spider).copy();
      const direction = this.endEffector.copy().sub(this.attachmentPosition);
      const rotation = direction.getRotationAroundAxis(pivot);
      rotation.x += spider.gait.legStraightenRotation;
      const orientation = pivot.rotateYXZ(rotation.y, rotation.x, 0);
      this.chain.straightenDirection(orientation);
    }

    this.chain.fabrik(this.endEffector);
    return this.chain;
  }

  checkTouchingGround() {
    return this.spider.level.isOnGround(this.endEffector, DOWN().rotate(this.spider.orientation));
  }

  computeLookAhead(restPosition, triggerZoneRadius) {
    const spider = this.spider;
    if (!spider.isWalking) return restPosition;
    const direction = spider.velocity.isZero ? spider.forwardDirection() : spider.velocity.copy().normalize();
    const lookAhead = direction.mul(triggerZoneRadius * spider.gait.legLookAheadFraction).add(restPosition);
    lookAhead.rotateAroundY(spider.rotationalVelocity.y, spider.position);
    return lookAhead;
  }

  locateGround() {
    const spider = this.spider;
    const lookAhead = this.lookAheadPosition;
    const scanLength = this.scanVector.length();
    const level = spider.level;

    let id = 0;
    const rayCast = (x, z) => {
      id += 1;
      const start = new Vec3(x, this.scanStartPosition.y, z);
      const hit = level.raycastGround(start, this.scanVector, scanLength);
      return hit ? new LegTarget(hit, true, id) : null;
    };

    const x = this.scanStartPosition.x;
    const z = this.scanStartPosition.z;
    const mainCandidate = rayCast(x, z);

    if (!spider.gait.legScanAlternativeGround) return mainCandidate;

    if (mainCandidate && mainCandidate.position.y >= lookAhead.y - 0.24 && mainCandidate.position.y <= lookAhead.y + 1.5) {
      return mainCandidate;
    }

    const margin = 2 / 16;
    const nx = Math.floor(x) - margin;
    const nz = Math.floor(z) - margin;
    const pz = Math.ceil(z) + margin;
    const px = Math.ceil(x) + margin;

    const candidates = [
      rayCast(nx, nz), rayCast(nx, z), rayCast(nx, pz),
      rayCast(x, nz), mainCandidate, rayCast(x, pz),
      rayCast(px, nz), rayCast(px, z), rayCast(px, pz),
    ].filter((c) => c);

    const preferredPosition = lookAhead.copy();
    const front = lookAhead.copy().add(spider.forwardDirection().mul(1));
    if (!level.isPassableAt(front)) preferredPosition.y += spider.gait.legScanHeightBias;

    let best = null, bestD = Infinity;
    for (const c of candidates) {
      const d = c.position.distanceSquared(preferredPosition);
      if (d < bestD) { bestD = d; best = c; }
    }
    if (best && !this.comfortZone.contains(best.position)) return null;
    return best;
  }

  strandedTarget() { return new LegTarget(this.lookAheadPosition.copy(), false, -1); }

  disabledTarget() {
    const spider = this.spider;
    const lerpedGait = spider.lerpedGait();
    const upVector = UP().rotate(spider.orientation);
    const target = this.strandedTarget();
    target.position.add(upVector.copy().mul(lerpedGait.bodyHeight * 0.5));
    const minY = (this.groundPosition ? this.groundPosition.y : -Number.MAX_VALUE) + lerpedGait.bodyHeight * 0.1;
    target.position.y = Math.max(target.position.y, minY);
    return target;
  }
}
