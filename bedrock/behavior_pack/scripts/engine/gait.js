// Port of spider/Gait.kt: the gait parameters, the pivot modes, and the walk/gallop rules that
// decide WHEN each leg may take a step. Faithful to the Kotlin, including the tuning the shipped
// mod uses for its walk gait.

import { Quat, lerp, toRadians } from "./vec.js";

export class SplitDistance {
  constructor(horizontal, vertical) { this.horizontal = horizontal; this.vertical = vertical; }
  clone() { return new SplitDistance(this.horizontal, this.vertical); }
  scale(f) { return new SplitDistance(this.horizontal * f, this.vertical * f); }
  lerp(target, f) {
    return new SplitDistance(lerp(this.horizontal, target.horizontal, f), lerp(this.vertical, target.vertical, f));
  }
}

export class SplitDistanceZone {
  constructor(center, size) { this.center = center; this.size = size; }
  contains(point) {
    return this.center.horizontalDistance(point) <= this.size.horizontal &&
      this.center.verticalDistance(point) <= this.size.vertical;
  }
  get horizontal() { return this.size.horizontal; }
  get vertical() { return this.size.vertical; }
}

export class LerpGait {
  constructor(bodyHeight, triggerZone) { this.bodyHeight = bodyHeight; this.triggerZone = triggerZone; }
  scale(s) { this.bodyHeight *= s; this.triggerZone = this.triggerZone.scale(s); return this; }
  clone() { return new LerpGait(this.bodyHeight, this.triggerZone); }
  lerp(target, f) {
    this.bodyHeight = lerp(this.bodyHeight, target.bodyHeight, f);
    this.triggerZone = this.triggerZone.lerp(target.triggerZone, f);
    return this;
  }
}

export const PivotMode = {
  YAxis: (spider) => Quat.rotY(spider.orientation.getEulerAnglesYXZ().y),
  SpiderOrientation: (spider) => spider.orientation,
  GroundOrientation: (spider) => spider.preferredOrientation,
};

export class Gait {
  constructor(walkSpeed, type) {
    this.type = type;

    this.stationary = new LerpGait(1.1, new SplitDistance(0.25, 1.5));
    this.moving = new LerpGait(1.1, new SplitDistance(0.8, 1.5));

    this.maxBodyDistanceFromGround = 0.25;
    this.maxSpeed = walkSpeed;
    this.moveAcceleration = 0.15 / 4;

    this.rotateAcceleration = 0.15 / 4;
    this.rotationalDragCoefficient = 0.2;

    this.legMoveSpeed = walkSpeed * 2.5;
    this.legLiftHeight = 0.35;
    this.legDropDistance = this.legLiftHeight;

    this.comfortZone = new SplitDistance(1.2, 1.6);

    this.gravityAcceleration = 0.08;
    this.airDragCoefficient = 0.02;
    this.bounceFactor = 0.5;

    this.bodyHeightCorrectionAcceleration = this.gravityAcceleration * 4;
    this.bodyHeightCorrectionFactor = 0.25;

    this.legScanAlternativeGround = true;
    this.legScanHeightBias = 0.5;

    this.legLookAheadFraction = 0.6;
    this.groundDragCoefficient = 0.2;

    this.samePairCooldown = 1;
    this.crossPairCooldown = 1;

    this.polygonLeeway = 0;
    this.stabilizationFactor = 0;

    this.uncomfortableSpeedMultiplier = 0;

    this.disableAdvancedRotation = false;
    this.preferredPitchLeeway = toRadians(10);

    this.straightenLegs = true;
    this.legStraightenRotation = toRadians(-80);

    this.scanPivotMode = PivotMode.YAxis;
    this.legChainPivotMode = PivotMode.SpiderOrientation;

    this.preferLevelBreakpoint = toRadians(45);
    this.preferLevelBias = 0;
    this.preferredRotationLerpFraction = 0.3;

    this.rotationLerp = 0.3;
  }

  /** The shipped walk gait (Gait.defaultWalk in the Kotlin). */
  static defaultWalk() {
    const g = new Gait(0.15, WALK);
    g.maxSpeed = 0.4;
    g.moveAcceleration = 0.15;
    g.legMoveSpeed = 1.1;
    g.moving.triggerZone = new SplitDistance(0.6, 1.5);
    g.uncomfortableSpeedMultiplier = 0.6;
    g.comfortZone = new SplitDistance(1.4, 1.8);
    return g;
  }

  static defaultGallop() {
    const g = new Gait(0.4, GALLOP);
    g.moving.bodyHeight = 1.6;
    g.legMoveSpeed = 0.5;
    g.rotateAcceleration = 0.25 / 4;
    g.uncomfortableSpeedMultiplier = 0.6;
    g.samePairCooldown = 2;
    g.crossPairCooldown = 4;
    g.polygonLeeway = 0.5;
    return g;
  }

  /** Scale stance height + step/zone distances, never speeds (SpiderBody.setSizeScale). */
  scaleSize(f) {
    this.stationary.scale(f);
    this.moving.scale(f);
    this.maxBodyDistanceFromGround *= f;
    this.legLiftHeight *= f;
    this.legDropDistance *= f;
    this.legMoveSpeed *= f;
    this.comfortZone = this.comfortZone.scale(f);
  }
}

// ---- Leg index arithmetic (LegLookUp) -------------------------------------------------------

export const LegLookUp = {
  diagonalPairs: (legs) => legs.map((l) => [...LegLookUp.diagonal(l), l]),
  isLeftLeg: (leg) => leg % 2 === 0,
  isRightLeg: (leg) => leg % 2 !== 0,
  getPairIndex: (leg) => Math.floor(leg / 2),
  isDiagonal1: (leg) => (LegLookUp.getPairIndex(leg) % 2 === 0 ? LegLookUp.isLeftLeg(leg) : LegLookUp.isRightLeg(leg)),
  isDiagonal2: (leg) => !LegLookUp.isDiagonal1(leg),
  diagonalFront: (leg) => (LegLookUp.isLeftLeg(leg) ? leg - 1 : leg - 3),
  diagonalBack: (leg) => (LegLookUp.isLeftLeg(leg) ? leg + 3 : leg + 1),
  front: (leg) => leg - 2,
  back: (leg) => leg + 2,
  horizontal: (leg) => (LegLookUp.isLeftLeg(leg) ? leg + 1 : leg - 1),
  diagonal: (leg) => [LegLookUp.diagonalFront(leg), LegLookUp.diagonalBack(leg)],
  adjacent: (leg) => [LegLookUp.front(leg), LegLookUp.back(leg), LegLookUp.horizontal(leg)],
};

export const unIndexLeg = (spider, indices) => indices.map((i) => spider.legs[i]).filter((l) => l !== undefined);

// ---- Gait types -----------------------------------------------------------------------------

function walkLegsInUpdateOrder(spider) {
  const idx = spider.legs.map((_, i) => i);
  const d1 = idx.filter((i) => LegLookUp.isDiagonal1(i));
  const d2 = idx.filter((i) => LegLookUp.isDiagonal2(i));
  return [...d1, ...d2].map((i) => spider.legs[i]);
}

// DELIBERATE DEVIATION FROM JAVA (Bedrock only; spider.javaWalk restores Java's rule): THE
// EMERGENCY STEP. The walk rule makes a leg wait while a neighbour is mid-step; on bumpy ground, or
// whenever the body outpaces that wave, a leg that is already strained keeps waiting until it hits
// its reach limit and is then DRAGGED along the ground by the pull-back (the "dragging feet" in the
// user's videos - the Java engine does exactly the same, measured in devtools/simtest/chase.html).
// A strained leg may step at once instead, as long as more than half the feet stay planted.
// `forced` is the leg.js half: a foot that does reach its limit steps instead of sliding.
// `reachable`: a step is only started toward a target within this fraction of the leg's reach (the
// pull-back fires at 0.95; round 14 used 0.9, which left targets at 0.9-0.95 to Java's slide).
// `comfort`: how far into its comfort zone a leg (outside its trigger zone) counts as strained - 1
// = only once it is uncomfortable (red in /spider debug); round 32's 0.7 lets it step before it
// gets there (chase.html, 8 scenarios: red legs 15.5% -> 13% of the time, flat walk 14% -> 6%).
export const EMERGENCY = { stretch: 0.85, uncomfortable: true, comfort: 0.7, minPlantedFraction: 0.5, forced: true, ignoreCareful: false, reachable: 0.94 };

// DELIBERATE DEVIATION FROM JAVA (round 32, Bedrock only; spider.javaWalk restores Java's step):
// `stayLifted`. Java lowers a stepping foot once it is within legDropDistance of its target - but
// the target is a look-ahead point re-scanned every tick, moving with the body, so the last ticks of
// a step chase it ALONG THE GROUND: the foot skates (devtools/simtest/chase.html "skating"). Here
// the foot stays up until the tick it actually lands. (The other skate - a step begun and finished
// inside one tick - is left to the renderer: render.js stepHop. Making those steps last two ticks
// in the engine flipped the AI's tunnel route from through to over the hill.)
export const STEP = { stayLifted: true };

/** Are the round-14 emergency steps on for this body? Off for javaWalk, and while the AI lines it
 *  up for a doorway/tunnel or squeezes it (body.carefulGait, set by ai.js): Java's routing there
 *  hangs on centimetres of arrival position, and Java's own gait is what it was tuned on. */
export function emergencyStepsOn(spider) {
  return !spider.javaWalk && (EMERGENCY.ignoreCareful || (!spider.carefulGait && !spider.sizeChanging));
}

function emergencyStep(leg) {
  const spider = leg.spider;
  if (!emergencyStepsOn(spider) || !leg.touchingGround) return false;
  const reach = leg.legPlan.segments.reduce((s, seg) => s + seg.length, 0);
  // Over-stretched counts even inside the trigger zone: a foot left on a lower step is stretched
  // VERTICALLY as the body climbs, and the trigger zone (mostly horizontal) never asks it to move.
  const overStretched = reach > 0 && leg.endEffector.distance(leg.attachmentPosition) > reach * EMERGENCY.stretch;
  const strained = overStretched || (EMERGENCY.uncomfortable && leg.isOutsideTriggerZone && leg.comfortUsed() > EMERGENCY.comfort);
  if (!strained) return false;
  if (leg.endEffector.distanceSquared(leg.target.position) < 0.01) return false;
  if (leg.target.position.distance(leg.attachmentPosition) >= reach * EMERGENCY.reachable) return false;   // a step it can't finish
  const planted = spider.legs.filter((l) => l !== leg && l.isGrounded()).length;
  return planted > spider.legs.length * EMERGENCY.minPlantedFraction;
}

function walkCanMoveLeg(leg) {
  const spider = leg.spider;
  const index = spider.legs.indexOf(leg);

  if (!leg.target.isGrounded) return true;

  leg.isPrimary = true;
  if (emergencyStep(leg)) return true;

  const crossPair = unIndexLeg(spider, LegLookUp.adjacent(index));
  if (crossPair.some((l) => !l.isGrounded() && !l.isDisabled && l.target.isGrounded)) return false;
  if (crossPair.some((l) => l.target.isGrounded && l.timeSinceStopMove < spider.gait.crossPairCooldown)) return false;

  const samePair = unIndexLeg(spider, LegLookUp.diagonal(index));
  if (samePair.some((l) => l.target.isGrounded && l.timeSinceBeginMove < spider.gait.samePairCooldown)) return false;

  const wantsToMove = leg.isOutsideTriggerZone || !leg.touchingGround;
  // A foot that "arrived" at a mid-air target must still be allowed to step (deadlock fix).
  const alreadyAtTarget = leg.target.isGrounded && leg.endEffector.distanceSquared(leg.target.position) < 0.01;
  const onGround = spider.legs.some((l) => l.isGrounded()) || spider.onGround;

  return wantsToMove && !alreadyAtTarget && onGround;
}

function gallopCanMoveLeg(leg) {
  const spider = leg.spider;
  const index = spider.legs.indexOf(leg);

  if (!spider.isWalking) return walkCanMoveLeg(leg);
  if (!leg.target.isGrounded) return true;

  const onGround = spider.legs.some((l) => l.isGrounded()) || spider.onGround;
  if (!onGround) return false;

  const pair = spider.legs[LegLookUp.horizontal(index)] ?? leg;
  leg.isPrimary = LegLookUp.isDiagonal1(index) || pair.isDisabled || !pair.target.isGrounded;

  if (leg.isPrimary) {
    const front = spider.legs[LegLookUp.diagonalFront(index)];
    if (front && leg.target.isGrounded && leg.timeSinceBeginMove < spider.gait.crossPairCooldown) return false;
    return leg.isOutsideTriggerZone || !leg.touchingGround;
  }
  const hasCooldown = pair.target.isGrounded && pair.timeSinceBeginMove < spider.gait.samePairCooldown;
  return pair.isMoving && !hasCooldown;
}

export const WALK = { canMoveLeg: walkCanMoveLeg, getLegsInUpdateOrder: walkLegsInUpdateOrder };
export const GALLOP = { canMoveLeg: gallopCanMoveLeg, getLegsInUpdateOrder: walkLegsInUpdateOrder };
