// Port of the KinematicChain (FABRIK solver) and polygon helpers in util/Maths.kt - verbatim logic.

import { Vec3, Quat } from "./vec.js";

export class ChainSegment {
  constructor(position, length, initDirection) {
    this.position = position; this.length = length; this.initDirection = initDirection;
  }
}

export class KinematicChain {
  constructor(root, segments) {
    this.root = root; this.segments = segments;
    this.maxIterations = 20;
    this.tolerance = 0.01;
  }

  fabrik(target) {
    for (let i = 0; i < this.maxIterations; i++) {
      this.fabrikForward(target);
      this.fabrikBackward();
      if (this.getEndEffector().distanceSquared(target) < this.tolerance) break;
    }
  }

  straightenDirection(rotation) {
    const position = this.root.copy();
    for (const segment of this.segments) {
      const initDirection = segment.initDirection.copy().rotate(rotation);
      position.add(initDirection.mul(segment.length));
      segment.position.set(position);
    }
  }

  fabrikForward(newPosition) {
    this.segments[this.segments.length - 1].position.set(newPosition);
    for (let i = this.segments.length - 1; i >= 1; i--) {
      const previous = this.segments[i];
      const segment = this.segments[i - 1];
      this.moveSegment(segment.position, previous.position, previous.length);
    }
  }

  fabrikBackward() {
    this.moveSegment(this.segments[0].position, this.root, this.segments[0].length);
    for (let i = 1; i < this.segments.length; i++) {
      const previous = this.segments[i - 1];
      const segment = this.segments[i];
      this.moveSegment(segment.position, previous.position, segment.length);
    }
  }

  moveSegment(point, pullTowards, length) {
    const direction = pullTowards.copy().sub(point).normalize();
    point.set(pullTowards).sub(direction.mul(length));
  }

  getEndEffector() { return this.segments[this.segments.length - 1].position; }

  getVectors() {
    return this.segments.map((segment, i) => {
      const previous = i > 0 ? this.segments[i - 1].position : this.root;
      return segment.position.copy().sub(previous);
    });
  }

  getRelativeRotations(pivot) {
    const vectors = this.getVectors();
    const firstEuler = vectors[0].getRotationAroundAxis(pivot);
    const firstRotation = pivot.copy().rotateYXZ(firstEuler.y, firstEuler.x, 0);
    return vectors.map((current, i) => {
      if (i === 0) return firstRotation;
      return Quat.rotationTo(vectors[i - 1], current);
    });
  }

  /** Each segment's WORLD rotation (the +Z axis of a segment model points along the segment). */
  getRotations(pivot) {
    const rotations = this.getRelativeRotations(pivot);
    for (let i = 1; i < rotations.length; i++) rotations[i].mul(rotations[i - 1]);
    return rotations;
  }
}

// ---- Polygon helpers (support polygon / normal force). Points are {x, y} 2-D. ----------------

export function pointInPolygon(point, polygon) {
  let count = 0;
  for (let i = 0; i < polygon.length; i++) {
    const a = polygon[i];
    const b = polygon[(i + 1) % polygon.length];
    if ((a.y <= point.y && b.y > point.y) || (b.y <= point.y && a.y > point.y)) {
      const slope = (b.x - a.x) / (b.y - a.y);
      const intersect = a.x + (point.y - a.y) * slope;
      if (intersect < point.x) count++;
    }
  }
  return count % 2 === 1;
}

const dist2 = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

export function nearestPointInPolygon(point, polygon) {
  let closest = polygon[0];
  let closestDistance = dist2(point, closest);
  for (let i = 0; i < polygon.length; i++) {
    const a = polygon[i];
    const b = polygon[(i + 1) % polygon.length];
    const onLine = nearestPointOnClampedLine(point, a, b);
    const distance = dist2(point, onLine);
    if (distance < closestDistance) { closest = onLine; closestDistance = distance; }
  }
  return closest;
}

export function nearestPointOnClampedLine(point, a, b) {
  const apx = point.x - a.x, apy = point.y - a.y;
  const abx = b.x - a.x, aby = b.y - a.y;
  const dot = apx * abx + apy * aby;
  const lengthAB = dist2(a, b);
  const t = lengthAB === 0 ? 0 : dot / (lengthAB * lengthAB);
  const tc = Math.max(0, Math.min(1, t));
  return { x: a.x + tc * abx, y: a.y + tc * aby };
}

export { Vec3 };
