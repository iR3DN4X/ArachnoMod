package com.heledron.spideranimation.platform

import com.heledron.spideranimation.util.copy
import com.mojang.math.Transformation
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3d
import kotlin.math.floor

// Ported from `utilities/maths.kt` (raycast/collision helpers), `utilities/overloads`,
// and `utilities/rendering/`. The original used Bukkit's World.rayTraceBlocks and
// Bukkit display entities; here we use net.minecraft ClipContext + Display.BlockDisplay.

// ---- World queries -------------------------------------------------------------------------

/**
 * Clip a ray against block COLLISION shapes, ignoring fluids. Null when nothing is hit.
 *
 * This is `Level.clip(ClipContext(..., COLLIDER, NONE, ...))` written out by hand, and it has to
 * be. **On 1.20.1 `ClipContext`'s only constructor takes an Entity, and vanilla passes it
 * straight to `CollisionContext.of(entity)`, which dereferences it immediately** — so the `null`
 * this used to pass threw an NPE inside `EntityCollisionContext.<init>` the moment a leg first
 * looked for the ground, taking the whole server tick loop with it. (Forge patches that path to
 * tolerate null, which is why the Forge 1.20.1 build never showed it and the belief that null was
 * safe here survived.) There is no CollisionContext-taking constructor on 1.20.1 to switch to.
 *
 * Using the two-argument `getCollisionShape(level, pos)` sidesteps the context entirely — it is
 * the same shape lookup `SafeGroundFinder` already relies on — and `traverseBlocks` walks exactly
 * the same voxel sequence vanilla's own clip does, so the behaviour is unchanged.
 */
private fun ServerLevel.clipCollider(from: Vec3, to: Vec3): BlockHitResult? =
    BlockGetter.traverseBlocks(from, to, Unit, { _, pos ->
        val state = getBlockState(pos)
        if (state.isAir) null else state.getCollisionShape(this, pos).clip(from, to, pos)
    }, { null })

/** Ray-cast against block collision shapes (fluids ignored), returning the hit point or null. */
fun ServerLevel.raycastGround(start: Vector3d, direction: Vector3d, maxDistance: Double): Vector3d? {
    if (direction.lengthSquared() == 0.0) return null
    val dir = Vector3d(direction).normalize().mul(maxDistance)
    val from = Vec3(start.x, start.y, start.z)
    val to = Vec3(start.x + dir.x, start.y + dir.y, start.z + dir.z)
    val result = clipCollider(from, to) ?: return null
    if (result.type == HitResult.Type.MISS) return null
    val loc = result.location
    return Vector3d(loc.x, loc.y, loc.z)
}

fun ServerLevel.isOnGround(position: Vector3d, downVector: Vector3d): Boolean =
    raycastGround(position, downVector, 0.001) != null

data class CollisionResult(val position: Vector3d, val offset: Vector3d)

/** Mirrors the original World.resolveCollision: cast from (position - direction) to position. */
fun ServerLevel.resolveCollision(position: Vector3d, direction: Vector3d): CollisionResult? {
    val start = position.copy().sub(direction)
    val from = Vec3(start.x, start.y, start.z)
    val to = Vec3(position.x, position.y, position.z)
    val result = clipCollider(from, to) ?: return null
    if (result.type == HitResult.Type.MISS) return null
    val hit = Vector3d(result.location.x, result.location.y, result.location.z)
    return CollisionResult(hit, Vector3d(hit).sub(position))
}

/** True when the block at this position has no collision (Bukkit's Block.isPassable). */
fun ServerLevel.isPassableAt(position: Vector3d): Boolean {
    val bp = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
    return this.getBlockState(bp).getCollisionShape(this, bp).isEmpty
}

fun ServerLevel.playSoundAt(position: Vector3d, sound: SoundEvent, volume: Float, pitch: Float) {
    val bp = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
    this.playSound(null, bp, sound, SoundSource.HOSTILE, volume, pitch)
}

// ---- Retained-mode BlockDisplay renderer ---------------------------------------------------
// Equivalent of the plugin's RenderEntity / RenderGroup / RenderEntityTracker. Each render pass
// "submits" items keyed by an arbitrary handle; the tracker spawns/reuses/culls real
// Display.BlockDisplay entities so the set of live displays always matches what was submitted.

const val DISPLAY_TAG = "arachnomod_segment"

interface RenderItem {
    fun submit(handle: Any)
}

object EmptyRenderItem : RenderItem {
    override fun submit(handle: Any) {}
}

class RenderGroup : RenderItem {
    private val children = LinkedHashMap<Any, RenderItem>()
    operator fun set(handle: Any, item: RenderItem) { children[handle] = item }
    operator fun get(handle: Any): RenderItem? = children[handle]
    override fun submit(handle: Any) {
        for ((childHandle, item) in children) item.submit(handle to childHandle)
    }
}

class RenderBlockDisplay(
    private val level: ServerLevel,
    private val position: Vector3d,
    private val velocity: Vector3d,
    private val block: BlockState,
    private val matrix: Matrix4f,
) : RenderItem {
    override fun submit(handle: Any) {
        var display = DisplayTracker.get(handle)
        var fresh = false

        if (display == null) {
            display = spawnDisplayAtVisual()
            DisplayTracker.put(handle, display)
            fresh = true
        } else {
            // Displays interpolate their TRANSFORM but not their POSITION, so the entity stays
            // PARKED and all motion rides the interpolated transform. The park point is the
            // spider's own position at park time — TRAILING, never ahead: the client samples the
            // display's LIGHTING and does frustum CULLING at the ENTITY position, so an anchor
            // projected ahead can land inside a hill (whole spider renders pitch black) or swing
            // off-screen (spider blinks out). Ground the spider just walked is always lit, never
            // solid, and in view whenever the spider is.
            val ox = position.x - display.x
            val oy = position.y - display.y
            val oz = position.z - display.z
            val offsetSqr = ox * ox + oy * oy + oz * oz
            val quiet = velocity.length() < QUIET_SPEED
            val mustRePark = offsetSqr > FORCE_RE_ANCHOR * FORCE_RE_ANCHOR
            val quietRePark = quiet && offsetSqr > QUIET_RE_ANCHOR * QUIET_RE_ANCHOR
            if (mustRePark || quietRePark) {
                // ZERO-FLASH re-park: NEVER setPos a live display — its position (teleport packet)
                // and transform (data packet) sync separately, and a frame rendered between the
                // two shows one frame of doubled offset: a hard teleport flash. Instead spawn a
                // REPLACEMENT at the current position: a Display renders nothing until its first
                // data arrives, so it appears atomically with the correct transform; the old one
                // is retired after a short overlap so there is never a hole.
                val replacement = spawnDisplayAtVisual()
                DisplayTracker.replace(handle, replacement)
                display = replacement
                fresh = true
            }
        }

        applyState(display, fresh)

        // Keep retirees tracking the LIVE visual during their overlap: if the server coalesces
        // several ticks into one packet burst, the replacement's render state may not exist for a
        // few frames — the retiree covers them showing the CURRENT visual (perfectly superimposed
        // with the replacement, so no ghost either). Without this, a hiccup during a re-park
        // removed the old display before the new one rendered: the whole spider blinked out.
        for (retiree in DisplayTracker.retiring(handle)) applyState(retiree, false)
    }

    private fun applyState(display: Display.BlockDisplay, fresh: Boolean) {
        val dx = (position.x - display.x).toFloat()
        val dy = (position.y - display.y).toFloat()
        val dz = (position.z - display.z).toFloat()

        display.setBlockState(block)
        display.setTransformation(Transformation(Matrix4f().translate(dx, dy, dz).mul(matrix)))
        // Duration 1: each per-tick update lerps over exactly one tick, chaining seamlessly. Do NOT
        // raise this — a longer window low-pass-filters the fast leg oscillation, visibly damping
        // the scurry (tried 2; the legs went mushy).
        display.setInterpolationDuration(if (fresh) 0 else 1)
        // CRITICAL 1.20.1 QUIRK (verified in the decompiled client Display.java): the client only
        // restarts its interpolation window when the start-delay DATA VALUE IS RECEIVED, and the
        // sync layer only resends CHANGED values — a plain setInterpolationDelay(0) every tick is
        // sent once, the window goes stale, progress clamps to 1, and every update SNAPS (visible
        // as a 1-frame forward teleport whenever the server coalesces two ticks). Double-setting
        // forces the value dirty each tick so the restart is resent — parity with 1.21.1, whose
        // rewritten engine (1.20.2+) doesn't need this.
        display.setInterpolationDelay(1)
        display.setInterpolationDelay(0)
    }

    private fun spawnDisplayAtVisual(): Display.BlockDisplay {
        // NEVER saved to disk: displays are pure per-tick render output. Without this override,
        // a dimension change (player portals away, spider chunks unload mid-render) baked the
        // displays into the chunk save — reloading the area then showed frozen "corpse pile"
        // spiders stacked at the old spot. The mob + body respawn fresh; so must the visuals.
        val display = object : Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level) {
            override fun shouldBeSaved() = false
        }
        display.moveTo(position.x, position.y, position.z, 0f, 0f)
        // The visual can trail up to FORCE_RE_ANCHOR blocks from the parked entity; widen the view
        // range so the client never distance-culls the display while its visual is on screen.
        display.setViewRange(4f)
        display.addTag(DISPLAY_TAG)
        level.addFreshEntity(display)
        return display
    }

    companion object {
        private const val FORCE_RE_ANCHOR = 32.0   // re-park after this much travel (lighting stays fresh)
        private const val QUIET_RE_ANCHOR = 12.0   // tidy up the anchor whenever we're stationary
        private const val QUIET_SPEED = 0.03       // blocks/tick; below this a re-park is invisible
    }
}

/** Convenience: a block display at a world position with a local transform matrix. */
fun renderBlock(level: ServerLevel, position: Vector3d, velocity: Vector3d, block: BlockState, matrix: Matrix4f): RenderBlockDisplay =
    RenderBlockDisplay(level, position, velocity, block, matrix)

object DisplayTracker {
    // Retired displays overlap their replacement for this many render passes before being
    // discarded. They keep receiving the LIVE transform during the overlap (see
    // RenderBlockDisplay.submit), so however client frames and packet bursts line up, at least one
    // display always renders the current visual — re-parks can neither blink nor ghost.
    private const val RETIRE_OVERLAP_TICKS = 4

    private class Retiree(val entity: Display.BlockDisplay, var ticksLeft: Int)

    private val rendered = HashMap<Any, Display.BlockDisplay>()
    private val used = HashSet<Any>()
    private val retiring = HashMap<Any, ArrayList<Retiree>>()

    fun get(handle: Any): Display.BlockDisplay? {
        val existing = rendered[handle]
        if (existing == null || !existing.isAlive) {
            rendered.remove(handle)
            return null
        }
        used.add(handle)
        return existing
    }

    fun put(handle: Any, entity: Display.BlockDisplay) {
        rendered[handle] = entity
        used.add(handle)
    }

    /** Swap in a re-parked replacement; the old display retires after a short overlap. */
    fun replace(handle: Any, replacement: Display.BlockDisplay) {
        rendered[handle]?.let { retiring.getOrPut(handle) { ArrayList() }.add(Retiree(it, RETIRE_OVERLAP_TICKS)) }
        rendered[handle] = replacement
        used.add(handle)
    }

    /** The still-overlapping previous displays for this handle (kept updated with the live visual). */
    fun retiring(handle: Any): List<Display.BlockDisplay> =
        retiring[handle]?.mapNotNull { if (it.entity.isAlive) it.entity else null } ?: emptyList()

    /** True if this entity is one of OUR live displays (rendered or retiring). Anything carrying
     *  the mod tag but NOT tracked is an orphan left by an old version — the janitor sweep in
     *  SpiderSpawnManager discards those as their chunks load. */
    fun isTracked(entity: net.minecraft.world.entity.Entity): Boolean {
        for (display in rendered.values) if (display === entity) return true
        for (list in retiring.values) for (retiree in list) if (retiree.entity === entity) return true
        return false
    }

    /** Called at the end of every render pass: discard any display not submitted this pass. */
    fun endRender() {
        val toRemove = rendered.keys - used
        for (key in toRemove) {
            rendered[key]?.discard()
            rendered.remove(key)
            retiring.remove(key)?.forEach { it.entity.discard() }
        }
        used.clear()

        // Count down and discard retirees from zero-flash re-parks.
        val handleIterator = retiring.entries.iterator()
        while (handleIterator.hasNext()) {
            val entry = handleIterator.next()
            entry.value.removeIf { retiree ->
                retiree.ticksLeft -= 1
                if (retiree.ticksLeft <= 0) { retiree.entity.discard(); true } else false
            }
            if (entry.value.isEmpty()) handleIterator.remove()
        }
    }

    fun removeAll() {
        for (entity in rendered.values) entity.discard()
        rendered.clear()
        used.clear()
        for (list in retiring.values) for (retiree in list) retiree.entity.discard()
        retiring.clear()
    }
}

/** Sweep up display entities left behind by a crash / failed shutdown (matches the plugin's tag sweep). */
fun ServerLevel.removeOrphanDisplays(near: Vector3d) {
    val box = AABB(near.x - 256, near.y - 256, near.z - 256, near.x + 256, near.y + 256, near.z + 256)
    val orphans = this.getEntitiesOfClass(Display.BlockDisplay::class.java, box) { it.tags.contains(DISPLAY_TAG) }
    for (e in orphans) e.discard()
}
