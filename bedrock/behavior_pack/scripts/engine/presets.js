// Port of spider/Models.kt (body plans) and spider/Presets.kt (the leg layouts + the line leg
// model). On Java each segment is a BlockDisplay of a netherite block scaled to
// (thickness, thickness, length); on Bedrock the same box is a bone of the leg entity, so a
// segment plan carries the thickness the Java line model would have used.

import { Vec3, FORWARD } from "./vec.js";
import { Gait } from "./gait.js";

export class SegmentPlan {
  constructor(length, initDirection, thickness = 0.1) {
    this.length = length; this.initDirection = initDirection; this.thickness = thickness;
  }
  clone() { return new SegmentPlan(this.length, this.initDirection.copy(), this.thickness); }
}

export class LegPlan {
  constructor(attachmentPosition, restPosition, segments) {
    this.attachmentPosition = attachmentPosition; this.restPosition = restPosition; this.segments = segments;
  }
}

export class BodyPlan {
  constructor() { this.scale = 1; this.legs = []; this.block = "netherite"; }

  /** Kotlin BodyPlan.scale(): leg spread, segment lengths and the leg model (thickness) all scale.
   *  Renamed scaleBy because JS cannot have a `scale` field and a `scale()` method. */
  scaleBy(s) {
    this.scale *= s;
    for (const leg of this.legs) {
      leg.attachmentPosition.mul(s);
      leg.restPosition.mul(s);
      for (const segment of leg.segments) {
        segment.length *= s;
        segment.thickness *= s;
      }
    }
  }
}

export class SpiderOptions {
  constructor() {
    this.walkGait = Gait.defaultWalk();
    this.gallopGait = Gait.defaultGallop();
    this.bodyPlan = new BodyPlan();
  }
}

const equalLength = (segmentCount, length) =>
  Array.from({ length: segmentCount }, () => new SegmentPlan(length, FORWARD()));

function addLegPair(bodyPlan, root, rest, segments) {
  bodyPlan.legs.push(new LegPlan(new Vec3(root.x, root.y, root.z), new Vec3(rest.x, rest.y, rest.z), segments));
  bodyPlan.legs.push(new LegPlan(new Vec3(-root.x, root.y, root.z), new Vec3(-rest.x, rest.y, rest.z), segments.map((s) => s.clone())));
}

/** applyLineLegModel: thickness tapers from 4.5/16 at the hip to 1.5/16 at the tip. */
function applyLineLegModel(bodyPlan, block) {
  const rootThickness = 4.5 / 16;
  const tipThickness = 1.5 / 16;
  bodyPlan.block = block;
  for (const leg of bodyPlan.legs) {
    leg.segments.forEach((segment, index) => {
      const fraction = leg.segments.length <= 1 ? 0 : index / (leg.segments.length - 1);
      segment.thickness = rootThickness + (tipThickness - rootThickness) * fraction;
    });
  }
}

export const MIN_LEGS = 1;
export const MAX_LEGS = 16;

/** Any number of legs on the octopod's proportions (Presets.legged). */
export function legged(legCount, segmentCount, segmentLength) {
  const o = new SpiderOptions();
  const count = Math.max(MIN_LEGS, Math.min(MAX_LEGS, legCount));
  const pairs = Math.floor(count / 2);

  for (let i = 0; i < pairs; i++) {
    const t = pairs === 1 ? 0.5 : i / (pairs - 1);
    const rootZ = 0.1 + (-0.2 - 0.1) * t;
    const restZ = 1.6 + (-2.5 - 1.6) * t;
    const restX = 1.0 + 0.3 * Math.sin(Math.PI * t);
    const length = (1.05 + 0.55 * t * t) * segmentLength;
    addLegPair(o.bodyPlan, new Vec3(0, 0, rootZ), new Vec3(restX, 0, restZ), equalLength(segmentCount, length));
  }

  if (count % 2 === 1) {
    o.bodyPlan.legs.push(new LegPlan(new Vec3(0, 0, 0.15), new Vec3(0, 0, 1.8), equalLength(segmentCount, 1.1 * segmentLength)));
  }

  applyLineLegModel(o.bodyPlan, "netherite");
  return o;
}

/** The hand-tuned octopod (Presets.octopod) - the body every variant but netherite is built on. */
export function octopod(segmentCount, segmentLength) {
  const o = new SpiderOptions();
  addLegPair(o.bodyPlan, new Vec3(0, 0, 0.1), new Vec3(1.0, 0, 1.6), equalLength(segmentCount, 1.1 * segmentLength));
  addLegPair(o.bodyPlan, new Vec3(0, 0, 0.0), new Vec3(1.3, 0, 0.4), equalLength(segmentCount, 1.0 * segmentLength));
  addLegPair(o.bodyPlan, new Vec3(0, 0, -0.1), new Vec3(1.3, 0, -0.9), equalLength(segmentCount, 1.1 * segmentLength));
  addLegPair(o.bodyPlan, new Vec3(0, 0, -0.2), new Vec3(1.1, 0, -2.5), equalLength(segmentCount, 1.6 * segmentLength));
  applyLineLegModel(o.bodyPlan, "netherite");
  return o;
}

/** The config-driven speed tuning (chase speed 8 blocks/s, leg step speed 1.1 - the defaults). */
function applyConfiguredGait(o, chaseSpeedBlocksPerSecond = 8, legStepSpeed = 1.1) {
  const maxSpeed = chaseSpeedBlocksPerSecond / 20;
  o.walkGait.maxSpeed = maxSpeed;
  o.walkGait.moveAcceleration = maxSpeed * 0.375;
  o.walkGait.legMoveSpeed = legStepSpeed;
  return o;
}

/** THE NETHERITE variant: legged(netheriteLegs = 8, 3 segments, length 1). */
export function netheriteSpider(legs = 8, chaseSpeed = 8, legStepSpeed = 1.1) {
  return applyConfiguredGait(legged(legs, 3, 1.0), chaseSpeed, legStepSpeed);
}

/** Presets.defaultSpider: the octopod at the configured speeds (also /spider newinstance's body). */
export function defaultSpider(chaseSpeed = 8, legStepSpeed = 1.1) {
  return applyConfiguredGait(octopod(3, 1.0), chaseSpeed, legStepSpeed);
}

/** THE CAMO variant (Java v1.1.1): the octopod re-skinned - moss until each leg first lands, then
 *  ACTIVE CAMOUFLAGE paints every leg as the block under its foot (see camo.js). */
export function camoSpider(chaseSpeed = 8, legStepSpeed = 1.1) {
  const o = defaultSpider(chaseSpeed, legStepSpeed);
  applyLineLegModel(o.bodyPlan, "moss");
  return o;
}
