// Port of the SIZE and SPEED rules from entity/SpiderMob.kt and entity/SpiderAI.kt.
//
// The Java spider shrinks to minSize when it is near you and grows toward maxSize the farther
// away you are, and its travel speed scales with its size (up to speedGrowthFactor) - a giant at
// range is also a sprinter. On top of that (targetScale): THE SQUEEZE, the doorway/crawl-hole
// fit, the hard headroom cap inside a tight space, sizing down for the room the PLAYER is in,
// and growing in deep water. Values are the Java mod's shipped config defaults.

import { roomAt, findFloorBelow, waterDepthAbove } from "./ground.js";

// The settings (Java's config file): config.js. Re-exported here for the AI, which reads them via mob.js.
export { CONFIG } from "./config.js";
import { CONFIG } from "./config.js";

/** SpiderMob.distanceToScale: minSize within sizeNearDistance, maxSize from sizeFarDistance out. */
export function distanceToScale(distance) {
  const near = CONFIG.sizeNearDistance, far = CONFIG.sizeFarDistance;
  const t = far > near ? Math.max(0, Math.min(1, (distance - near) / (far - near))) : 1;
  return CONFIG.minSize + (CONFIG.maxSize - CONFIG.minSize) * t;
}

/** SpiderMob.fixedSizeScale: the size at which the spider stands fixedSizeBlocks tall. Standing
 *  height is exactly linear in size - Java measured the top of the highest leg 2.64 blocks up per
 *  unit of size (the body CENTRE is only 1.1, so a headroom-style estimate would be far too tall). */
export function fixedSizeScale() {
  return CONFIG.fixedSizeBlocks / 2.64;
}

/** The size a ridden spider holds: riddenSize, or the one fixed size when growth is off. */
export function riddenScale() {
  return CONFIG.enableGrowth ? CONFIG.riddenSize : fixedSizeScale();
}

/** SpiderMob.approachScale: 30% of the way per tick, capped at +12% / -25% per tick. */
export function approachScale(current, target) {
  const lerped = current + (target - current) * 0.3;
  const growCap = 1 + CONFIG.growPercentPerTick / 100;
  const shrinkCap = 1 + CONFIG.shrinkPercentPerTick / 100;
  return Math.max(current / shrinkCap, Math.min(current * growCap, lerped));
}

/** SpiderMob.scaleToSpeedFactor: 1x at minSize up to speedGrowthFactor at maxSize. */
export function scaleToSpeedFactor(scale) {
  const min = CONFIG.minSize, max = CONFIG.maxSize;
  const t = max > min ? Math.max(0, Math.min(1, (scale - min) / (max - min))) : 1;
  return 1 + (CONFIG.speedGrowthFactor - 1) * t;
}

/** SpiderAI.tickChase stop distance: twice the standing height (max 4), or a tight 0.25 when the
 *  player is more than 2 blocks above/below the spider's ground plane (dug-in pressure). */
export function chaseArriveDistance(body, player) {
  const standing = body.walkGait.stationary.bodyHeight;
  const groundLevelY = body.position.y - standing;
  const pressureMode = Math.abs(groundLevelY - player.location.y) > 2;
  return pressureMode ? 0.25 : Math.min(standing * 2, 4);
}

/** SpiderMob.waterGrowthScale: grow just enough to ride above the water over the floor it stands on. */
export function waterGrowthScale(world, body) {
  const p = body.position;
  const floorY = findFloorBelow(world, p.x, p.y, p.z);
  if (floorY === null) return null;
  const depth = waterDepthAbove(world, p.x, floorY, p.z);
  if (depth <= 1.0) return null;
  const bodyHeightPerScale = body.walkGait.stationary.bodyHeight / body.sizeScale;
  return (depth + 0.5) / bodyHeightPerScale;
}

/**
 * SpiderMob.tick's size decision for this tick: distance (or water) size, THE SQUEEZE, the passage
 * fit, the headroom cap, the prey's-room cap. `nearest` is the nearest player at any range (or
 * null), `horizontalDistance` to them; `variant` the spider's ("netherite", "camo", "poison",
 * "hunter").
 */
export function targetScale(world, body, ai, currentScale, nearest, horizontalDistance, variant = "netherite") {
  const squeezing = ai ? ai.isSqueezing() : false;
  const hunter = variant === "hunter";
  // NO-GROW MODE (enableGrowth false, Java v1.8.5): every variant holds ONE size, fixedSizeBlocks tall.
  const growth = CONFIG.enableGrowth;
  const waterScale = growth && CONFIG.growInWater && !squeezing && !hunter ? waterGrowthScale(world, body) : null;
  let target;
  if (hunter) {
    // THE HUNTER never changes size: fixed at hunterSize whatever the distance, water depth or
    // opening ahead (it fits doorways as it is; holes are walls to it - deep water is its one
    // weakness, since it will not grow above the surface).
    target = growth ? CONFIG.hunterSize : fixedSizeScale();
  } else if (squeezing) {
    // The squeeze may only ever SHRINK a fixed-size spider, never enlarge a small one.
    target = growth ? CONFIG.squeezeSize : Math.min(CONFIG.squeezeSize, fixedSizeScale());
  } else if (!growth) {
    // No distance growth, no water growth, and poisonSize gives way to fixedSizeBlocks too. The
    // doorway, crawl-hole and headroom caps below still apply - they only shrink.
    target = fixedSizeScale();
  } else if (variant === "poison") {
    // THE POISON variant holds ONE size too (Java v1.8.1: distance sizing read as bobbing). Placed
    // AFTER the squeeze so it can still pour itself down a hole, and the caps below still apply,
    // so it still threads doorways and crawl-holes.
    target = CONFIG.poisonSize;
  } else {
    target = Math.max(distanceToScale(horizontalDistance), waterScale !== null ? waterScale : 0);
  }
  // Doorway/crawl-hole fit (chase pathfinding). The hunter walks through openings at its own size.
  if (!squeezing && !hunter && ai) {
    const fit = ai.passageFitScale();
    if (fit !== null) target = Math.min(target, fit);
  }
  // HARD CEILING CAP: a body in a tight space may never be taller than that space.
  const perScale = body.walkGait.stationary.bodyHeight / Math.max(body.sizeScale, 0.01);
  const fitFor = (room) => Math.max((room - 0.2) / (perScale + 0.3), 0.12);
  if (body.confinedHeadroom !== null) target = Math.min(target, fitFor(body.confinedHeadroom));
  // ...and size down for the space the PLAYER is in, once close enough (lead scales with size).
  const fitLeadDistance = 14.0 + 6.0 * currentScale;
  if (!squeezing && nearest && horizontalDistance < fitLeadDistance) {
    const room = roomAt(world, nearest.location.x, nearest.location.y, nearest.location.z);
    if (room !== null) target = Math.min(target, fitFor(room));
  }
  return target;
}
