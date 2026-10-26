// Port of entity/SafeGroundFinder.kt (the Java lead, arachnomod-fabric-1.21.1).
//
// Every function keeps its Java name, order and rules; only the block questions go through
// BlockWorld (blocks.js), which answers them from Bedrock's typeIds instead of collision shapes.
// `world` is a BlockWorld. Positions are plain numbers (x, y, z) as in the Java.

import { CONFIG } from "./config.js";   // spawnMaxVerticalSearch, read live

const floor = Math.floor;

// ---- the four block tests (Java's private helpers) ---------------------------------------------

/** Solid enough to stand on and not a liquid: never place a spider on/in water or lava. */
function isSolidDryGround(world, x, y, z) {
  const c = world.cell(x, y, z);
  if (c.fluid) return false;
  if (c.air) return false;
  return c.sturdyTop;
}

/** Free space for the body: no liquid, and either air or something with no collision shape. */
function isPassable(world, x, y, z) {
  const c = world.cell(x, y, z);
  if (c.fluid) return false;
  return c.air || !c.collision;
}

/** Free space for getting through a wall: passable, plus doors/trapdoors/fence gates, shut or open. */
function isDoorway(world, x, y, z) {
  if (isPassable(world, x, y, z)) return true;
  const c = world.cell(x, y, z);
  if (c.fluid) return false;
  return c.door;
}

/** Would this block stop the spider's body? (A doorway's own door does not count.) */
function isBodyBlocking(world, x, y, z) {
  const c = world.cell(x, y, z);
  if (c.air) return false;
  if (c.door) return false;
  return c.collision;
}

// ---- ground ------------------------------------------------------------------------------------

/** The Y to stand a spider on at (x, z) by the heightmap scan, or null. */
export function findSafeY(world, x, z) {
  const blockX = floor(x);
  const blockZ = floor(z);
  const top = world.topY(blockX, blockZ);
  if (top === null || !world.hasChunk(blockX, blockZ, top)) return null;
  // Java's heightmap is the first FREE y above the column; Bedrock's topmost block is the top
  // block itself (leaves included - the scan below skips them: they have no sturdy top).
  const topY = top + 1;
  let y = Math.min(topY, world.maxY - 1);
  const bottomLimit = Math.max(topY - CONFIG.spawnMaxVerticalSearch, world.minY);
  while (y > bottomLimit) {
    if (isSolidDryGround(world, blockX, y, blockZ)) {
      const feetClear = isPassable(world, blockX, y + 1, blockZ);
      const headClear = isPassable(world, blockX, y + 2, blockZ);
      if (feetClear && headClear) return y + 1;
    }
    y--;
  }
  return null;
}

/** DIMENSION-AWARE safe ground at (x, z): heightmap scan, or around refY under a ceiling.
 *  `ceilingMargin`: how far below the build limit a ceiling dimension's scan starts. */
export function groundYAt(world, x, z, refY, ceilingMargin = 2) {
  return world.hasCeiling ? findSafeYNear(world, x, z, refY, ceilingMargin) : findSafeY(world, x, z);
}

/** A spawn spot is never up in the bedrock ceiling band (the Nether's runs y 123-127 under a build
 *  limit of 128): an air pocket in there is no place to put a spider. */
export const SPAWN_CEILING_MARGIN = 9;

/** Ceiling-dimension variant: scan DOWN from refY + 16 to refY - spawnMaxVerticalSearch. */
export function findSafeYNear(world, x, z, refY, ceilingMargin = 2) {
  const blockX = floor(x);
  const blockZ = floor(z);
  if (!world.hasChunk(blockX, blockZ, refY)) return null;
  let y = Math.min(floor(refY) + 16, world.maxY - ceilingMargin);
  const bottomLimit = Math.max(floor(refY) - CONFIG.spawnMaxVerticalSearch, world.minY);
  while (y > bottomLimit) {
    if (isSolidDryGround(world, blockX, y, blockZ)) {
      const feetClear = isPassable(world, blockX, y + 1, blockZ);
      const headClear = isPassable(world, blockX, y + 2, blockZ);
      if (feetClear && headClear) return y + 1;
    }
    y--;
  }
  return null;
}

/** The floor directly below (x, y, z), scanning straight down (works underground). */
export function findFloorBelow(world, x, y, z, maxDepth = 96) {
  const blockX = floor(x);
  const blockZ = floor(z);
  const startY = Math.min(floor(y), world.maxY - 1);
  const bottomLimit = Math.max(startY - maxDepth, world.minY);
  for (let yy = startY; yy >= bottomLimit; yy--) {
    const c = world.cell(blockX, yy, blockZ);
    if (!c.air && c.collision) return yy + 1;
  }
  return null;
}

/** Depth of the contiguous WATER column on the floor at (x, z), counting up from floorY. */
export function waterDepthAbove(world, x, floorY, z, maxDepth = 64) {
  const blockX = floor(x);
  const blockZ = floor(z);
  let y = floor(floorY);
  const top = Math.min(y + maxDepth, world.maxY - 1);
  let depth = 0;
  while (y <= top) {
    if (!world.cell(blockX, y, blockZ).water) break;
    depth++;
    y++;
  }
  return depth;
}

// ---- openings (doorways and crawl-holes) -------------------------------------------------------

/** Height (2 doorway, 1 crawl-hole, 0 none) of a passable ground-level gap at (x, z), floor at groundY. */
export function openingHeight(world, x, groundY, z) {
  const blockX = floor(x);
  const blockZ = floor(z);
  const feetY = floor(groundY);
  if (!world.cell(blockX, feetY - 1, blockZ).sturdyTop) return 0;   // no floor
  if (!isDoorway(world, blockX, feetY, blockZ)) return 0;
  return isDoorway(world, blockX, feetY + 1, blockZ) ? 2 : 1;
}

/** A ground-level gap the spider can fit through; alongX = the axis you travel THROUGH it on. */
export class Opening {
  constructor(x, y, z, height, alongX) { this.x = x; this.y = y; this.z = z; this.height = height; this.alongX = alongX; }
}

/** HUNT FOR THE DOOR: every way IN around the hit point, nearest to the player first. */
export function collectOpenings(world, hitX, hitZ, groundY, targetX, targetZ, radius, minHeight, limit = 12) {
  const baseX = floor(hitX);
  const baseZ = floor(hitZ);
  const found = [];
  for (let dx = -radius; dx <= radius; dx++) {
    for (let dz = -radius; dz <= radius; dz++) {
      const bx = baseX + dx;
      const bz = baseZ + dz;
      if (!world.hasChunk(bx, bz, groundY)) continue;
      const x = bx + 0.5;
      const z = bz + 0.5;
      for (let dy = -1; dy <= 1; dy++) {
        const y = groundY + dy;
        const height = openingHeight(world, x, y, z);
        if (height < minHeight) continue;
        const pinch = pinchAxis(world, bx, floor(y), bz);
        if (pinch === 0) continue;
        found.push([(targetX - x) * (targetX - x) + (targetZ - z) * (targetZ - z), new Opening(x, y, z, height, pinch === 2)]);
        break;
      }
    }
  }
  return found.sort((a, b) => a[0] - b[0]).slice(0, limit).map((f) => f[1]);
}

/** How far the passage at this opening runs along its axis: [lo, hi] block-centre coordinates. */
export function passageExtent(world, opening, minHeight, maxLength = 32) {
  const alongX = opening.alongX;
  const wantAxis = alongX ? 2 : 1;
  let lo = alongX ? opening.x : opening.z;
  let hi = lo;
  for (const dir of [-1, 1]) {
    for (let step = 1; step <= maxLength; step++) {
      const cx = alongX ? opening.x + dir * step : opening.x;
      const cz = alongX ? opening.z : opening.z + dir * step;
      if (openingHeight(world, cx, opening.y, cz) < minHeight) break;
      if (pinchAxis(world, floor(cx), floor(opening.y), floor(cz)) !== wantAxis) break;
      const along = alongX ? cx : cz;
      if (dir < 0) lo = along; else hi = along;
    }
  }
  return [lo, hi];
}

/** 0 = not a gap; 1 = walls east+west (travel along Z); 2 = walls north+south (travel along X). */
function pinchAxis(world, x, feetY, z) {
  const blocked = (ox, oz) => !isPassable(world, x + ox, feetY, z + oz);
  if (blocked(1, 0) && blocked(-1, 0)) return 1;
  if (blocked(0, 1) && blocked(0, -1)) return 2;
  return 0;
}

// ---- confinement (corridors, tunnels, rooms) ----------------------------------------------------

/** What a tight space is doing to a body at a point: floor to stand on, lane to hold, headroom. */
export class Confinement {
  constructor(floorY, laneX, laneZ, headroom = null) { this.floorY = floorY; this.laneX = laneX; this.laneZ = laneZ; this.headroom = headroom; }
  get any() { return this.floorY !== null || this.laneX !== null || this.laneZ !== null; }
}
const NOT_CONFINED = new Confinement(null, null, null);

const INTERIOR_WALL_SEARCH = 8;   // how far sideways to look for the walls that make an interior
const CEILING_SEARCH = 4;         // how far up to look for a ceiling

/** groundY is the body's GROUND PLANE (position.y - bodyHeight), never its centre. */
export function confinementAt(world, x, groundY, z, bodyHeight) {
  const r = readSpace(world, x, groundY, z);
  if (!r) return NOT_CONFINED;
  // A ceiling only constrains a body actually UNDER it (the bouncing-giant fix).
  if (r.roofed && bodyHeight > r.headroom + 1.0) return NOT_CONFINED;
  // Big bodies are not lane-locked by scenery they stride over: an unroofed pinch only confines
  // if its walls rise into the body.
  if (!r.roofed && sideWallHeight(world, r.bx, r.bz, r.feetY, r.walledX) < bodyHeight * 0.6) return NOT_CONFINED;
  return new Confinement(
    r.headroom < bodyHeight + 1.5 ? r.floorTop : null,
    r.walledX ? r.bx + 0.5 : null,
    r.walledZ && !r.walledX ? r.bz + 0.5 : null,
    r.roofed ? r.headroom : null,
  );
}

/** Headroom of an ENCLOSED, roofed space at a location (asked without reference to any body). */
/** Bedrock only (not in Java): is the spot walled in on at least one axis at feet level - a hole or
 *  a trench - rather than open ground? The squeeze's check that the player is actually IN something. */
export function walledIn(world, x, feetY, z) {
  const bx = floor(x);
  const bz = floor(z);
  const y = floor(feetY + 0.01);
  const walledX = isBodyBlocking(world, bx + 1, y, bz) && isBodyBlocking(world, bx - 1, y, bz);
  const walledZ = isBodyBlocking(world, bx, y, bz + 1) && isBodyBlocking(world, bx, y, bz - 1);
  return walledX || walledZ;
}

export function roomAt(world, x, groundY, z) {
  const bx = floor(x);
  const bz = floor(z);
  if (!world.hasChunk(bx, bz, groundY)) return null;
  const startY = floor(groundY + 0.5);
  let floorTop = NaN;
  for (let dy = 0; dy >= -4; dy--) {
    if (isBodyBlocking(world, bx, startY + dy, bz)) { floorTop = startY + dy + 1; break; }
  }
  if (Number.isNaN(floorTop)) return null;
  const offGround = groundY - floorTop;
  if (offGround > 1.5 || offGround < -1.5) return null;

  const feetY = floorTop;
  let headroom = Infinity;
  for (let dy = 0; dy <= CEILING_SEARCH; dy++) {
    if (isBodyBlocking(world, bx, feetY + dy, bz)) { headroom = dy; break; }
  }
  if (headroom === Infinity || headroom < 1) return null;
  // A ceiling alone is not shelter (a leaf canopy is a ceiling too): require an enclosure.
  if (!boundedOnSomeAxis(world, bx, feetY, bz)) return null;
  return headroom;
}

function boundedOnSomeAxis(world, bx, feetY, bz) {
  const bounded = (dx, dz) => {
    for (let d = 1; d <= INTERIOR_WALL_SEARCH; d++) if (isBodyBlocking(world, bx + dx * d, feetY, bz + dz * d)) return true;
    return false;
  };
  return (bounded(1, 0) && bounded(-1, 0)) || (bounded(0, 1) && bounded(0, -1));
}

/** The raw geometry of the space at a point, or null when it is not a tight space at all. */
function readSpace(world, x, groundY, z) {
  const bx = floor(x);
  const bz = floor(z);
  // Anchored at the GROUND PLANE (the oversized-body deadlock fix).
  const startY = floor(groundY + 0.5);
  let floorTop = NaN;
  for (let dy = 0; dy >= -4; dy--) {
    if (isBodyBlocking(world, bx, startY + dy, bz)) { floorTop = startY + dy + 1; break; }
  }
  if (Number.isNaN(floorTop)) return null;
  const offGround = groundY - floorTop;
  if (offGround > 1.5 || offGround < -1.5) return null;

  const feetY = floorTop;
  const walledX = isBodyBlocking(world, bx + 1, feetY, bz) && isBodyBlocking(world, bx - 1, feetY, bz);
  const walledZ = isBodyBlocking(world, bx, feetY, bz + 1) && isBodyBlocking(world, bx, feetY, bz - 1);
  if (!walledX && !walledZ) return null;

  let headroom = Infinity;
  for (let dy = 0; dy <= CEILING_SEARCH; dy++) {
    if (isBodyBlocking(world, bx, feetY + dy, bz)) { headroom = dy; break; }
  }
  // A "ceiling" of zero is a body straddling terrain, not a tunnel.
  const roofed = headroom !== Infinity && headroom >= 1;
  return { bx, bz, feetY, floorTop, walledX, walledZ, headroom, roofed };
}

/** How far the confining side walls rise above the passage floor, in blocks (capped at 7). */
function sideWallHeight(world, bx, bz, feetY, walledX) {
  let height = 0;
  for (let dy = 0; dy <= 6; dy++) {
    const both = walledX
      ? isBodyBlocking(world, bx + 1, feetY + dy, bz) && isBodyBlocking(world, bx - 1, feetY + dy, bz)
      : isBodyBlocking(world, bx, feetY + dy, bz + 1) && isBodyBlocking(world, bx, feetY + dy, bz - 1);
    if (!both) break;
    height = dy + 1;
  }
  return height;
}
