// Port of platform/Platform.kt's world queries onto the Bedrock Script API.
//
// The Java engine leans on EXACT ray semantics: a hit counts if the surface is anywhere on
// [0, maxDistance] along the ray, including a ray that starts exactly on the surface - its
// "is this foot on the ground?" test is a 0.001-block ray from the foot. Bedrock's native
// getBlockFromRay is not guaranteed to behave like that at the edges (a ray that starts on a face,
// ends exactly on one, or is a sliver long), and when those probes miss, no foot ever counts as
// planted: nothing holds the body up and it collapses onto the ground. So every query here casts
// a GENEROUS native ray (backed up a little, extended a little) and decides the hit itself by its
// distance along the ray - Java's semantics, whatever the native ray does at its ends.

import { Vec3 } from "./engine/vec.js";
import { BlockWorld } from "./blocks.js";
import { confinementAt } from "./ground.js";

const PAD = 0.25;        // how far behind the start / beyond the end the native ray reaches
const EPS = 1e-4;        // float slack for "exactly on the surface"

export class BedrockLevel {
  constructor(dimension) {
    this.dimension = dimension;
    this.blocks = new BlockWorld(dimension);   // cached block reads for the AI and confinement
    this.rays = 0;                             // native raycasts since last read (perf HUD)
  }

  /** SafeGroundFinder.confinementAt for the body (corridors, tunnels, rooms). */
  confinementAt(x, groundY, z, bodyHeight) {
    return confinementAt(this.blocks, x, groundY, z, bodyHeight);
  }

  /**
   * World hit point of a native raycast that started at `origin` heading `dir` (unit).
   *
   * faceLocation is relative to the block's north-west-bottom corner, BUT Bedrock wraps the
   * coordinate of a FAR face back to 0: a hit on the TOP of a sand block at y=61 came back in game
   * as faceLocation.y = 0.00, not 1.00 - so feet were planted a whole block down, inside the ground.
   * So: take the face Bedrock names, put its plane at the block edge (restoring the wrapped 1.0;
   * partial blocks like slabs keep their own fraction), and intersect the ray with that plane.
   */
  static hitPoint(hit, origin, dir) {
    const b = hit.block.location, f = hit.faceLocation;
    const far = (v) => (v <= 1e-6 ? 1 : v);
    let axis, plane;
    switch (hit.face) {
      case "Up": axis = "y"; plane = b.y + far(f.y); break;
      case "Down": axis = "y"; plane = b.y + f.y; break;
      case "East": axis = "x"; plane = b.x + far(f.x); break;
      case "West": axis = "x"; plane = b.x + f.x; break;
      case "South": axis = "z"; plane = b.z + far(f.z); break;
      case "North": axis = "z"; plane = b.z + f.z; break;
      default: return new Vec3(b.x + f.x, b.y + f.y, b.z + f.z);
    }
    if (!origin || !dir || Math.abs(dir[axis]) < 1e-9) {
      const p = new Vec3(b.x + f.x, b.y + f.y, b.z + f.z);
      p[axis] = plane;
      return p;
    }
    const t = (plane - origin[axis]) / dir[axis];
    return new Vec3(origin.x + dir.x * t, origin.y + dir.y * t, origin.z + dir.z * t);
  }

  /** First solid surface on [0, maxDistance] along the ray (Java ClipContext semantics), or null. */
  raycastGround(start, direction, maxDistance) {
    if (direction.lengthSquared() === 0 || !(maxDistance >= 0)) return null;
    const d = direction.copy().normalize();
    // A ray that STARTS inside a solid block: Java's VoxelShape.clip reports a hit right at the
    // start (from + 0.001 of the way), and the mod relies on it - a body pushed into a wall or a
    // pillar gets lifted out of the top by the collision step, which is how the dug-in pressure
    // climbs to a player on a pillar. Bedrock's native ray promises nothing there, so decide it
    // here: full solid blocks and leaves (full-cube collision) contain the start point.
    const c = this.blocks.cell(Math.floor(start.x), Math.floor(start.y), Math.floor(start.z));
    if (c.collision && (c.sturdyTop || c.leaves)) return start.copy().add(d.copy().mul(0.001 * maxDistance));
    let from = start.copy().sub(d.copy().mul(PAD));
    let fromT = -PAD;
    for (let attempt = 0; attempt < 4; attempt++) {
      let hit;
      try {
        this.rays++;
        hit = this.dimension.getBlockFromRay(
          { x: from.x, y: from.y, z: from.z },
          { x: d.x, y: d.y, z: d.z },
          { maxDistance: maxDistance - fromT + PAD, includeLiquidBlocks: false, includePassableBlocks: false },
        );
      } catch (e) {
        return null;   // unloaded chunk or outside the world
      }
      if (!hit) return null;
      const p = BedrockLevel.hitPoint(hit, from, d);
      const t = p.copy().sub(start).dot(d);   // the hit's distance along the ray from `start`
      if (t > maxDistance + EPS) return null;
      if (t >= -EPS) return p;
      // A surface just BEHIND the start (the backed-up ray met it): look again from past it.
      from = p.copy().add(d.copy().mul(1e-3));
      fromT = t + 1e-3;
    }
    return null;
  }

  /** Mirrors the Java resolveCollision: cast from (position - direction) to position. */
  resolveCollision(position, direction) {
    const start = position.copy().sub(direction);
    const hit = this.raycastGround(start, direction, direction.length());
    if (!hit) return null;
    return { position: hit, offset: hit.copy().sub(position) };
  }

  /** Exactly the Java test: a 0.001-block ray from the point. */
  isOnGround(position, downVector) {
    return this.raycastGround(position, downVector, 0.001) !== null;
  }

  isPassableAt(position) {
    try {
      const block = this.dimension.getBlock({ x: Math.floor(position.x), y: Math.floor(position.y), z: Math.floor(position.z) });
      if (!block) return true;
      return block.isAir || block.isLiquid;
    } catch (e) {
      return true;
    }
  }

  playSound(id, position, volume, pitch) {
    try { this.dimension.playSound(id, { x: position.x, y: position.y, z: position.z }, { volume, pitch }); }
    catch (e) { /* unknown sound id or unloaded */ }
  }

  spawnParticle(id, position) {
    try { this.dimension.spawnParticle(id, { x: position.x, y: position.y, z: position.z }); }
    catch (e) { /* unknown particle or unloaded */ }
  }
}
