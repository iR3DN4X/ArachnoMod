// Port of spider/Behaviour.kt: how a behaviour turns into body rotation + walking velocity.

import { Vec3, Quat, FORWARD, clamp } from "./vec.js";
import { emergencyStepsOn } from "./gait.js";

export class StayStillBehaviour {}
export class TargetBehaviour { constructor(target, distance) { this.target = target; this.distance = distance; } }
export class DirectionBehaviour { constructor(targetDirection, walkDirection) { this.targetDirection = targetDirection; this.walkDirection = walkDirection; } }

/** One tick of a behaviour (setupBehaviours' three onTick systems). */
export function applyBehaviour(spider, behaviour) {
  if (behaviour instanceof StayStillBehaviour) {
    walkAt(spider, new Vec3());
    const f = spider.forwardDirection();
    f.y = 0;
    rotateTowards(spider, f);
  } else if (behaviour instanceof TargetBehaviour) {
    const direction = behaviour.target.copy().sub(spider.position).normalize();
    rotateTowards(spider, direction);

    const currentSpeed = spider.velocity.length();
    const decelerateDistance = (currentSpeed * currentSpeed) / (2 * spider.gait.moveAcceleration);
    const currentDistance = spider.position.horizontalDistance(behaviour.target);

    if (currentDistance > behaviour.distance + decelerateDistance) {
      walkAt(spider, direction.copy().mul(spider.gait.maxSpeed));
    } else {
      walkAt(spider, new Vec3());
    }
  } else if (behaviour instanceof DirectionBehaviour) {
    rotateTowards(spider, behaviour.targetDirection);
    walkAt(spider, behaviour.walkDirection.copy().mul(spider.gait.maxSpeed));
  }
}

function rotateTowards(spider, targetVector) {
  const gait = spider.gait;
  const currentEuler = spider.orientation.getEulerAnglesYXZ();
  const targetEuler = Quat.rotationTo(FORWARD(), targetVector).getEulerAnglesYXZ();

  targetEuler.x = clamp(targetEuler.x, spider.preferredPitch - gait.preferredPitchLeeway, spider.preferredPitch + gait.preferredPitchLeeway);
  targetEuler.z = spider.preferredRoll;
  if (spider.legs.some((l) => l.isUncomfortable && !l.isMoving)) targetEuler.y = currentEuler.y;

  const diffEuler = targetEuler.copy().sub(currentEuler);
  if (diffEuler.y > Math.PI) diffEuler.y -= 2 * Math.PI;
  if (diffEuler.y < -Math.PI) diffEuler.y += 2 * Math.PI;

  spider.isRotatingYaw = (diffEuler.x + diffEuler.y + diffEuler.z) > 0.001;
  diffEuler.lerp(new Vec3(), gait.rotationLerp);

  const diff = Quat.rotationYXZ(diffEuler.y, diffEuler.x, diffEuler.z);
  const conjugated = spider.orientation.copy().mul(diff).mul(spider.orientation.copy().invert());
  const conjugatedEuler = conjugated.getEulerAnglesYXZ();

  const grounded = spider.legs.filter((l) => l.isGrounded()).length;
  const maxAcceleration = gait.rotateAcceleration * grounded / spider.legs.length;
  spider.rotationalVelocity.moveTowards(conjugatedEuler, maxAcceleration);
}

// DELIBERATE DEVIATION FROM JAVA (the Java mod has the same flaw - measured 2026-10-10 with a real
// SpiderMob chasing a FakePlayer): the walk speed is gated on leg comfort - 60% while any planted
// leg is strained, FULL the moment none is. Tailing a walking player, that gate flips every few
// ticks, and each flip to full is a one-tick kick (4.5 -> 7.5 b/s) that throws the body past its
// planted feet. The centre of mass leaves the support polygon, the normal force switches off, the
// body sags for several ticks (in the worst case onto the floor), and the legs left behind hit
// their reach limit and are DRAGGED by the pull-back. Here the slow-down is still instant, but the
// way back to full speed is a ramp (COMFORT_RECOVERY_PER_TICK) instead of a snap.
// spider.javaWalk = true restores the Java rule exactly (the engine-vs-Java trace tools use it).
export let COMFORT_RECOVERY_PER_TICK = 0.25;   // 0.6 -> full in 2 ticks: kills the one-tick kick, keeps Java's routing (tunnel tests)
export function setComfortRecovery(v) { COMFORT_RECOVERY_PER_TICK = v; }   // for the browser tools' sweeps
// DELIBERATE DEVIATION FROM JAVA (round 15): while a planted leg is strained Java holds the spider
// to 60% speed (the gait's uncomfortableSpeedMultiplier). Since round 14 a strained leg steps at
// once instead of being dragged, so that brake mostly just made it trail you; 90% keeps up
// (sim: follows 1.8 blocks behind instead of 2.4, zero drags; Java 2.2 and 26% of status
// readouts red). null = the gait's own value.
export const COMFORT = { floor: 0.9 };

function walkAt(spider, targetVelocity) {
  const gait = spider.gait;
  const acceleration = gait.moveAcceleration;
  const target = targetVelocity.copy();

  if (!spider.javaWalk) {
    const uncomfortable = spider.legs.some((l) => l.isUncomfortable && !l.isMoving);
    // (Java's 60% wherever the emergency steps are off: lining up for an opening, the charge's morph)
    const floor = emergencyStepsOn(spider) ? (COMFORT.floor ?? gait.uncomfortableSpeedMultiplier) : gait.uncomfortableSpeedMultiplier;
    const previous = spider.walkFactor === undefined ? 1 : spider.walkFactor;
    spider.walkFactor = uncomfortable ? Math.min(previous, floor) : Math.min(1, previous + COMFORT_RECOVERY_PER_TICK);
    // Horizontal target scaled by the ramped factor; the vertical exactly as Java's two branches.
    const scaled = new Vec3(target.x * spider.walkFactor, spider.velocity.y * (uncomfortable ? floor : 1), target.z * spider.walkFactor);
    spider.velocity.moveTowards(scaled, acceleration);
    spider.isWalking = uncomfortable
      ? targetVelocity.x !== 0 && targetVelocity.z !== 0
      : spider.velocity.x !== 0 && spider.velocity.z !== 0;
    return;
  }

  if (spider.legs.some((l) => l.isUncomfortable && !l.isMoving)) {
    target.y = spider.velocity.y;
    const scaled = target.mul(gait.uncomfortableSpeedMultiplier);
    spider.velocity.moveTowards(scaled, acceleration);
    spider.isWalking = targetVelocity.x !== 0 && targetVelocity.z !== 0;
  } else {
    target.y = spider.velocity.y;
    spider.velocity.moveTowards(target, acceleration);
    spider.isWalking = spider.velocity.x !== 0 && spider.velocity.z !== 0;
  }
}
