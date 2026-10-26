// /spider debug - the original mod's debug graphics (TheCymaera's "Toggle Debug Graphics"), the
// Java mod's SpiderDebugRenderer.kt part for part: same parts, same blocks, same sizes, same
// placements, every part on. The only thing missing (on Java too) is the point detector's
// "selected leg" highlight, a slightly bigger foot cube.
//
// Java draws each part as a block display. Here each part is one `arachno:debug` box entity: a
// centred cube the client scales (sx, sy, sz) in its own frame, turns by YXZ Euler angles (a, p, r
// - the legs' convention) and moves to (x, y, z), all relative to the anchor the legs are parked
// at. SpiderRenderer (render.js) parks, re-parks and removes them along with the legs; this file
// only works out where every part goes.
//
// They are ordinary entities, so EVERYONE nearby sees them - for recording or showing someone
// what the legs are doing, that is what you want. Off by default, and not saved: like Java's, the
// switch lasts until the world is closed.

import { FORWARD, RIGHT, UP, toDegrees, clamp } from "./engine/vec.js";

export const DEBUG_TYPE = "arachno:debug";

/** Java's SpiderDebug.enabled. */
export const DEBUG = { enabled: false };

/** What each part means, sent to whoever switches the view on (Java's LEGEND, word for word). */
export const LEGEND = "Spider debug view ON. Gold bars: where each leg looks for ground. " +
  "Cyan: each leg's trigger zone, where a foot steps once it leaves (red = leg stretched too far). " +
  "Feet: diamond planted, lapis touching, redstone in the air. Glass cubes: step targets " +
  "(lime = on ground). Green outline: the feet holding it up, with the centre of mass and " +
  "normal force inside. Emerald nub: facing. Diamond axes: preferred orientation.";

// The debug entity's own palette (resource_pack/entity/debug.entity.json lists the same files in
// the same order); the stained glass comes last and draws translucent.
export const DEBUG_TEXTURES = [
  "textures/blocks/gold_block", "textures/blocks/iron_block", "textures/blocks/diamond_block",
  "textures/blocks/lapis_block", "textures/blocks/redstone_block", "textures/blocks/emerald_block",
  "textures/blocks/concrete_black", "textures/blocks/concrete_white",
  "textures/blocks/glass_cyan", "textures/blocks/glass_red", "textures/blocks/glass_lime",
];
const GOLD = 0, IRON = 1, DIAMOND = 2, LAPIS = 3, REDSTONE = 4, EMERALD = 5;
const BLACK_CONCRETE = 6, WHITE_CONCRETE = 7, CYAN_GLASS = 8, RED_GLASS = 9, LIME_GLASS = 10;
export const FIRST_GLASS = CYAN_GLASS;

/** One part: its centre (world), its turn (degrees, YXZ), its size in its own frame, its block,
 *  whether it is full-bright (Java's Brightness(15, 15)) and whether it snaps instead of gliding. */
const box = (centre, a, p, r, sx, sy, sz, tex, bright = true, snap = false) =>
  ({ x: centre.x, y: centre.y, z: centre.z, a, p, r, sx, sy, sz, tex, bright, snap });

/** A part turned by quaternion `q`. */
function turned(centre, q, sx, sy, sz, tex, bright = true) {
  const e = q.getEulerAnglesYXZ();
  return box(centre, toDegrees(e.y), toDegrees(e.x), toDegrees(e.z), sx, sy, sz, tex, bright);
}

/** An unturned cube of side `size` centred on `centre` (Java's centredMatrix). */
const cube = (centre, size, tex) => box(centre, 0, 0, 0, size, size, size, tex);

/** Java's line(): a square bar from `start` along `vector`. Its +Z is turned onto the vector with
 *  no roll (rotateTowards with UP), so it is centred halfway along. */
function line(start, vector, thickness, tex, snap = false) {
  // A zero-length line has no direction to face.
  if (vector.lengthSquared() < 1e-12) return null;
  const length = vector.length();
  const a = toDegrees(Math.atan2(vector.x, vector.z));
  const p = toDegrees(Math.atan2(-vector.y, vector.horizontalLength()));
  const centre = start.copy().add(vector.copy().mul(0.5));
  return box(centre, a, p, 0, thickness, thickness, length, tex, true, snap);
}

/** Every part of `spider`'s debug view, by name (a Map, so the renderer keeps one entity each). */
export function debugParts(spider) {
  const parts = new Map();
  const put = (key, part) => { if (part) parts.set(key, part); };
  const scale = spider.bodyPlan.scale;
  const scanPivot = spider.gait.scanPivotMode(spider);

  spider.legs.forEach((leg, i) => {
    // Scan bars
    if (leg.scanStartPosition && leg.scanVector) {
      put(`scanBar${i}`, line(leg.scanStartPosition, leg.scanVector, 0.05 * scale, leg.isPrimary ? GOLD : IRON));
    }

    // Trigger zone: a post its full height, and a flat square at the step target's height in it
    const zone = leg.triggerZone;
    if (zone && leg.comfortZone) {
      const zoneTex = leg.isUncomfortable ? RED_GLASS : CYAN_GLASS;
      put(`triggerZoneVertical${i}`, turned(zone.center, scanPivot, 0.07 * scale, 2 * zone.vertical, 0.07 * scale, zoneTex));
      const level = zone.center.copy();
      level.y = clamp(leg.target.position.y, level.y - zone.vertical, level.y + zone.vertical);
      const size = 2 * zone.horizontal;
      put(`triggerZoneHorizontal${i}`, turned(level, scanPivot, size, 0.02, size, zoneTex));
    }

    // End effector
    put(`endEffector${i}`, cube(leg.endEffector, 0.15 * scale,
      leg.isDisabled ? BLACK_CONCRETE : leg.isGrounded() ? DIAMOND : leg.touchingGround ? LAPIS : REDSTONE));

    // Target position
    put(`targetPosition${i}`, cube(leg.target.position, 0.2 * scale, leg.target.isGrounded ? LIME_GLASS : RED_GLASS));
  });

  // Spider direction: a nub one scale ahead
  {
    const size = 0.1 * scale;
    const ahead = FORWARD().rotate(spider.orientation).mul(scale).add(spider.position);
    put("direction", turned(ahead, spider.orientation, size, size, size, spider.gallop ? REDSTONE : EMERALD));
  }

  // Preferred orientation (the original leaves these at world lighting): three bars from the body
  // along the preferred forward (2 scale), right and up (1 scale each)
  {
    const thickness = 0.025 * scale;
    const q = spider.preferredOrientation;
    const axis = (key, direction, length) => {
      const centre = direction.copy().mul((thickness + length) / 2).rotate(q).add(spider.position);
      put(key, turned(centre, q, thickness + direction.x * length, thickness + direction.y * length,
        thickness + direction.z * length, DIAMOND, false));
    };
    axis("preferredForwards", FORWARD(), 2 * scale);
    axis("preferredRight", RIGHT(), scale);
    axis("preferredUp", UP(), scale);
  }

  const normal = spider.normal;
  if (!normal) return parts;

  // Support polygon (snaps instead of gliding, as in the original)
  const points = normal.contactPolygon;
  if (points) {
    for (let i = 0; i < points.length; i++) {
      const a = points[i], b = points[(i + 1) % points.length];
      put(`polygon${i}`, line(a, b.copy().sub(a), 0.05 * scale, EMERALD, true));
    }
  }

  // Centre of mass
  const centreOfMass = normal.centreOfMass;
  if (centreOfMass) {
    put("centreOfMass", cube(centreOfMass, 0.1 * scale, normal.normal.horizontalLength() === 0 ? LAPIS : REDSTONE));
  }

  // Normal force
  if (centreOfMass && normal.origin) {
    put("acceleration", line(normal.origin, centreOfMass.copy().sub(normal.origin), 0.02 * scale,
      spider.normalAcceleration.isZero ? BLACK_CONCRETE : WHITE_CONCRETE));
  }

  return parts;
}
