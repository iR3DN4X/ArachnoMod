// Port of util/Maths.kt (vectors) plus the JOML calls the Kotlin engine makes.
//
// The Kotlin engine runs on JOML (Vector3d / Quaternionf). These two classes reproduce exactly
// the JOML semantics it relies on - the mutating, chainable style, the right-handed rotations, and
// the YXZ Euler convention - so the ported engine reads line-for-line like the original.

export class Vec3 {
  constructor(x = 0, y = 0, z = 0) { this.x = x; this.y = y; this.z = z; }

  static of(v) { return new Vec3(v.x, v.y, v.z); }

  copy() { return new Vec3(this.x, this.y, this.z); }

  set(x, y, z) {
    if (typeof x === "object") { this.x = x.x; this.y = x.y; this.z = x.z; }
    else { this.x = x; this.y = y; this.z = z; }
    return this;
  }

  add(v) { this.x += v.x; this.y += v.y; this.z += v.z; return this; }
  sub(v) { this.x -= v.x; this.y -= v.y; this.z -= v.z; return this; }
  mul(s) { this.x *= s; this.y *= s; this.z *= s; return this; }
  dot(v) { return this.x * v.x + this.y * v.y + this.z * v.z; }

  lengthSquared() { return this.x * this.x + this.y * this.y + this.z * this.z; }
  length() { return Math.sqrt(this.lengthSquared()); }

  // JOML would produce NaN for a zero vector; leaving it at zero keeps one degenerate frame from
  // poisoning the whole simulation.
  normalize() {
    const l = this.length();
    if (l > 0) { this.x /= l; this.y /= l; this.z /= l; }
    return this;
  }

  distanceSquared(v) { const dx = this.x - v.x, dy = this.y - v.y, dz = this.z - v.z; return dx * dx + dy * dy + dz * dz; }
  distance(v) { return Math.sqrt(this.distanceSquared(v)); }
  horizontalDistance(v) { const dx = this.x - v.x, dz = this.z - v.z; return Math.sqrt(dx * dx + dz * dz); }
  verticalDistance(v) { return Math.abs(this.y - v.y); }
  horizontalLength() { return Math.sqrt(this.x * this.x + this.z * this.z); }
  get isZero() { return this.x === 0 && this.y === 0 && this.z === 0; }

  lerp(v, t) {
    this.x += (v.x - this.x) * t; this.y += (v.y - this.y) * t; this.z += (v.z - this.z) * t;
    return this;
  }

  /** A new vector: this x v. */
  crossNew(v) {
    return new Vec3(this.y * v.z - this.z * v.y, this.z * v.x - this.x * v.z, this.x * v.y - this.y * v.x);
  }

  /** Rotate by a unit quaternion (JOML Vector3d.rotate). */
  rotate(q) {
    const vx = this.x, vy = this.y, vz = this.z;
    const tx = 2 * (q.y * vz - q.z * vy);
    const ty = 2 * (q.z * vx - q.x * vz);
    const tz = 2 * (q.x * vy - q.y * vx);
    this.x = vx + q.w * tx + (q.y * tz - q.z * ty);
    this.y = vy + q.w * ty + (q.z * tx - q.x * tz);
    this.z = vz + q.w * tz + (q.x * ty - q.y * tx);
    return this;
  }

  /** JOML Vector3d.rotateY: right-handed rotation about +Y. */
  rotateY(angle) {
    const s = Math.sin(angle), c = Math.cos(angle);
    const x = this.x * c + this.z * s;
    const z = -this.x * s + this.z * c;
    this.x = x; this.z = z;
    return this;
  }

  rotateAroundY(angle, origin) { return this.sub(origin).rotateY(angle).add(origin); }

  moveTowards(target, speed) {
    const diff = target.copy().sub(this);
    const distance = diff.length();
    if (distance <= speed) this.set(target); else this.add(diff.mul(speed / distance));
    return this;
  }

  pitch() { return -Math.atan2(this.y, Math.sqrt(this.x * this.x + this.z * this.z)); }

  /** Rotation (YXZ Euler, radians) that turns +Z toward this vector, relative to a pivot. */
  getRotationAroundAxis(pivot) {
    const orientation = Quat.rotationTo(FORWARD(), this);
    return pivot.difference(orientation).getEulerAnglesYXZ();
  }
}

// Direction constants: like the Kotlin getters, every access returns a FRESH vector.
export const DOWN = () => new Vec3(0, -1, 0);
export const UP = () => new Vec3(0, 1, 0);
export const FORWARD = () => new Vec3(0, 0, 1);
export const RIGHT = () => new Vec3(1, 0, 0);

export const lerp = (a, b, t) => a * (1 - t) + b * t;
export const moveTowardsScalar = (value, target, speed) => {
  const d = target - value;
  return Math.abs(d) < speed ? target : value + speed * Math.sign(d);
};
export const toRadians = (deg) => deg * Math.PI / 180;
export const toDegrees = (rad) => rad * 180 / Math.PI;
export const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

export class Quat {
  constructor(x = 0, y = 0, z = 0, w = 1) { this.x = x; this.y = y; this.z = z; this.w = w; }

  copy() { return new Quat(this.x, this.y, this.z, this.w); }
  set(q) { this.x = q.x; this.y = q.y; this.z = q.z; this.w = q.w; return this; }

  /** this = this * q (JOML Quaternionf.mul). */
  mul(q) {
    const x = this.w * q.x + this.x * q.w + this.y * q.z - this.z * q.y;
    const y = this.w * q.y - this.x * q.z + this.y * q.w + this.z * q.x;
    const z = this.w * q.z + this.x * q.y - this.y * q.x + this.z * q.w;
    const w = this.w * q.w - this.x * q.x - this.y * q.y - this.z * q.z;
    this.x = x; this.y = y; this.z = z; this.w = w;
    return this;
  }

  static rotX(a) { return new Quat(Math.sin(a / 2), 0, 0, Math.cos(a / 2)); }
  static rotY(a) { return new Quat(0, Math.sin(a / 2), 0, Math.cos(a / 2)); }
  static rotZ(a) { return new Quat(0, 0, Math.sin(a / 2), Math.cos(a / 2)); }

  /** JOML rotationYXZ: R = Ry(y) * Rx(x) * Rz(z). */
  static rotationYXZ(y, x, z) { return Quat.rotY(y).mul(Quat.rotX(x)).mul(Quat.rotZ(z)); }

  rotateY(a) { return this.mul(Quat.rotY(a)); }
  rotateYXZ(y, x, z) { return this.mul(Quat.rotationYXZ(y, x, z)); }

  invert() {
    const n = this.x * this.x + this.y * this.y + this.z * this.z + this.w * this.w || 1;
    this.x = -this.x / n; this.y = -this.y / n; this.z = -this.z / n; this.w = this.w / n;
    return this;
  }

  /** A new quaternion D with this * D = other (JOML difference). */
  difference(other) { return this.copy().invert().mul(other); }

  /** JOML getEulerAnglesYXZ: (x = pitch, y = yaw, z = roll) for R = Ry * Rx * Rz. */
  getEulerAnglesYXZ() {
    const { x, y, z, w } = this;
    return new Vec3(
      Math.asin(clamp(-2 * (y * z - w * x), -1, 1)),
      Math.atan2(x * z + y * w, 0.5 - y * y - x * x),
      Math.atan2(y * x + w * z, 0.5 - x * x - z * z),
    );
  }

  /** JOML rotationTo: the shortest-arc rotation turning `from` onto `to`. */
  static rotationTo(from, to) {
    const f = from.copy().normalize(), t = to.copy().normalize();
    const dot = f.dot(t);
    if (dot < -1 + 1e-6) {
      let x = f.y, y = -f.x, z = 0;
      if (x * x + y * y === 0) { x = 0; y = f.z; z = -f.y; }
      const n = Math.sqrt(x * x + y * y + z * z) || 1;
      return new Quat(x / n, y / n, z / n, 0);
    }
    const sd2 = Math.sqrt((1 + dot) * 2), isd2 = 1 / sd2;
    const c = f.crossNew(t);
    const q = new Quat(c.x * isd2, c.y * isd2, c.z * isd2, sd2 * 0.5);
    const n = Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w) || 1;
    q.x /= n; q.y /= n; q.z /= n; q.w /= n;
    return q;
  }
}
