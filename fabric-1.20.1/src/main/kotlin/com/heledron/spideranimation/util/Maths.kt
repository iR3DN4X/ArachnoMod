package com.heledron.spideranimation.util

import org.joml.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt

/*
 * Ported from the original `utilities/maths` + `utilities/polygons` + `utilities/KinematicChain`.
 *
 * The original used org.bukkit.util.Vector. We use org.joml.Vector3d (bundled with Minecraft)
 * and provide extension functions that mirror the Bukkit Vector API (clone/subtract/multiply/
 * setY/...), so the ported physics & IK code below reads almost line-for-line like the original.
 */

// Direction constants. Like the original, these are getters that return a FRESH vector each
// access, so callers can mutate the result freely without aliasing a shared constant.
val DOWN_VECTOR get() = Vector3d(0.0, -1.0, 0.0)
val UP_VECTOR get() = Vector3d(0.0, 1.0, 0.0)
val FORWARD_VECTOR get() = Vector3d(0.0, 0.0, 1.0)
val BACKWARD_VECTOR get() = Vector3d(0.0, 0.0, -1.0)
val LEFT_VECTOR get() = Vector3d(-1.0, 0.0, 0.0)
val RIGHT_VECTOR get() = Vector3d(1.0, 0.0, 0.0)

fun Vector3d.copy() = Vector3d(this)
fun Vector3d.toV3f() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
val Vector3d.isZero get() = x == 0.0 && y == 0.0 && z == 0.0

// Bukkit-style aliases (JOML names differ)
fun Vector3d.subtract(o: Vector3dc): Vector3d = this.sub(o)
fun Vector3d.multiply(s: Double): Vector3d = this.mul(s)
fun Vector3d.setY(v: Double): Vector3d { this.y = v; return this }
fun Vector3d.crossProduct(o: Vector3dc): Vector3d = Vector3d(this).cross(o)

// JOML provides rotate(Quaterniondc) natively; add an overload for Quaternionf.
fun Vector3d.rotate(q: Quaternionf): Vector3d = this.rotate(Quaterniond(q))

fun Vector3d.rotateAroundY(angle: Double, origin: Vector3d): Vector3d {
    this.sub(origin).rotateY(angle).add(origin)
    return this
}

fun Vector3d.rotateAroundAxis(axis: Vector3d, angle: Double): Vector3d =
    this.rotateAxis(angle, axis.x, axis.y, axis.z)

fun Vector3d.moveTowards(target: Vector3dc, speed: Double): Vector3d {
    val diff = Vector3d(target).sub(this)
    val distance = diff.length()
    if (distance <= speed) this.set(target) else this.add(diff.mul(speed / distance))
    return this
}

fun Vector3d.pitch(): Float = (-atan2(y, sqrt(x * x + z * z))).toFloat()
fun Vector3d.yaw(): Float = (-atan2(-x, z)).toFloat()

fun Vector3d.horizontalDistance(o: Vector3dc): Double {
    val dx = x - o.x(); val dz = z - o.z(); return sqrt(dx * dx + dz * dz)
}
fun Vector3d.verticalDistance(o: Vector3dc): Double = abs(y - o.y())
fun Vector3d.horizontalLength(): Double = sqrt(x * x + z * z)

fun List<Vector3d>.average(): Vector3d {
    val out = Vector3d()
    for (v in this) out.add(v)
    if (isNotEmpty()) out.mul(1.0 / size)
    return out
}

fun Vector3f.moveTowards(target: Vector3fc, speed: Float): Vector3f {
    val diff = Vector3f(target).sub(this)
    val d = diff.length()
    if (d <= speed) this.set(target) else this.add(diff.mul(speed / d))
    return this
}

fun Double.lerp(o: Double, t: Double) = this * (1 - t) + o * t
fun Float.lerp(o: Float, t: Float) = this * (1 - t) + o * t
fun Double.moveTowards(target: Double, speed: Double): Double {
    val d = target - this; return if (abs(d) < speed) target else this + speed * sign(d)
}
fun Float.moveTowards(target: Float, speed: Float): Float {
    val d = target - this; return if (abs(d) < speed) target else this + speed * sign(d)
}
fun Int.moveTowards(target: Int, speed: Int): Int {
    val d = target - this; return if (abs(d) < speed) target else this + speed * d.sign
}
fun Double.toRadians(): Double = Math.toRadians(this)
fun Float.toRadians(): Float = Math.toRadians(this.toDouble()).toFloat()
fun Float.eased(): Float = this * this * (3 - 2 * this)

fun Quaternionf.getYXZRelative(pivot: Quaternionf): Vector3f =
    Quaternionf(pivot).difference(this).getEulerAnglesYXZ(Vector3f())

fun Vector3d.getRotationAroundAxis(pivot: Quaternionf): Vector3f {
    val orientation = Quaternionf().rotationTo(FORWARD_VECTOR.toV3f(), this.toV3f())
    return orientation.getYXZRelative(pivot)
}

/** Build a transform that places the unit cube [0,1]^3 centred on the origin at the given size. */
fun centredMatrix(x: Float, y: Float, z: Float): Matrix4f =
    Matrix4f().scale(x, y, z).translate(-0.5f, -0.5f, -0.5f)

// ---------------------------------------------------------------------------------------------
// SplitDistance (a horizontal + vertical radius), used by trigger/comfort zones.
// ---------------------------------------------------------------------------------------------
class SplitDistance(val horizontal: Double, val vertical: Double) {
    fun clone() = SplitDistance(horizontal, vertical)
    fun scale(factor: Double) = SplitDistance(horizontal * factor, vertical * factor)
    fun lerp(target: SplitDistance, factor: Double) =
        SplitDistance(horizontal.lerp(target.horizontal, factor), vertical.lerp(target.vertical, factor))
}

class SplitDistanceZone(val center: Vector3d, val size: SplitDistance) {
    fun contains(point: Vector3d): Boolean =
        center.horizontalDistance(point) <= size.horizontal && center.verticalDistance(point) <= size.vertical
    val horizontal get() = size.horizontal
    val vertical get() = size.vertical
}

// ---------------------------------------------------------------------------------------------
// Polygon helpers (used for the support-polygon / normal-force calculation).
// ---------------------------------------------------------------------------------------------
fun pointInPolygon(point: Vector2d, polygon: List<Vector2d>): Boolean {
    var count = 0
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        if (a.y <= point.y && b.y > point.y || b.y <= point.y && a.y > point.y) {
            val slope = (b.x - a.x) / (b.y - a.y)
            val intersect = a.x + (point.y - a.y) * slope
            if (intersect < point.x) count++
        }
    }
    return count % 2 == 1
}

fun nearestPointInPolygon(point: Vector2d, polygon: List<Vector2d>): Vector2d {
    var closest = polygon[0]
    var closestDistance = point.distance(closest)
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        val onLine = nearestPointOnClampedLine(point, a, b)
        val distance = point.distance(onLine)
        if (distance < closestDistance) {
            closest = onLine
            closestDistance = distance
        }
    }
    return closest
}

fun nearestPointOnClampedLine(point: Vector2d, a: Vector2d, b: Vector2d): Vector2d {
    val ap = Vector2d(point.x - a.x, point.y - a.y)
    val ab = Vector2d(b.x - a.x, b.y - a.y)
    val dot = ap.dot(ab)
    val lengthAB = a.distance(b)
    val t = if (lengthAB == 0.0) 0.0 else dot / (lengthAB * lengthAB)
    val tc = t.coerceIn(0.0, 1.0)
    return Vector2d(a.x + tc * ab.x, a.y + tc * ab.y)
}

// ---------------------------------------------------------------------------------------------
// KinematicChain (FABRIK solver) - ported verbatim, Bukkit Vector -> Vector3d.
// ---------------------------------------------------------------------------------------------
class ChainSegment(var position: Vector3d, var length: Double, var initDirection: Vector3d) {
    fun clone() = ChainSegment(position.copy(), length, initDirection.copy())
}

class KinematicChain(val root: Vector3d, val segments: List<ChainSegment>) {
    var maxIterations = 20
    var tolerance = 0.01

    fun fabrik(target: Vector3d) {
        for (i in 0 until maxIterations) {
            fabrikForward(target)
            fabrikBackward()
            if (getEndEffector().distanceSquared(target) < tolerance) break
        }
    }

    fun straightenDirection(rotation: Quaternionf) {
        val position = root.copy()
        for (segment in segments) {
            val initDirection = segment.initDirection.copy().rotate(rotation)
            position.add(initDirection.multiply(segment.length))
            segment.position.set(position)
        }
    }

    fun fabrikForward(newPosition: Vector3d) {
        segments.last().position.set(newPosition)
        for (i in segments.size - 1 downTo 1) {
            val previous = segments[i]
            val segment = segments[i - 1]
            moveSegment(segment.position, previous.position, previous.length)
        }
    }

    fun fabrikBackward() {
        moveSegment(segments.first().position, root, segments.first().length)
        for (i in 1 until segments.size) {
            val previous = segments[i - 1]
            val segment = segments[i]
            moveSegment(segment.position, previous.position, segment.length)
        }
    }

    fun moveSegment(point: Vector3d, pullTowards: Vector3d, segment: Double) {
        val direction = Vector3d(pullTowards).sub(point).normalize()
        point.set(pullTowards).sub(direction.mul(segment))
    }

    fun getEndEffector(): Vector3d = segments.last().position

    fun getVectors(): List<Vector3d> = segments.mapIndexed { i, segment ->
        val previous = segments.getOrNull(i - 1)?.position ?: root
        Vector3d(segment.position).sub(previous)
    }

    fun getRelativeRotations(pivot: Quaternionf): List<Quaternionf> {
        val vectors = getVectors()
        val firstEuler = vectors.first().getRotationAroundAxis(pivot)
        val firstRotation = Quaternionf(pivot).rotateYXZ(firstEuler.y, firstEuler.x, 0f)
        return vectors.mapIndexed { i, current ->
            val previous = vectors.getOrNull(i - 1) ?: return@mapIndexed firstRotation
            Quaternionf().rotationTo(previous.toV3f(), current.toV3f())
        }
    }

    fun getRotations(pivot: Quaternionf): List<Quaternionf> =
        getRelativeRotations(pivot).also { cumulateRotations(it) }

    private fun cumulateRotations(rotations: List<Quaternionf>) {
        for (i in 1 until rotations.size) rotations[i].mul(rotations[i - 1])
    }
}
