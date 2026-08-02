package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.floor

/**
 * Shared "is this a safe place to put a spider" check.
 *
 * Used by both natural spawning (SpiderSpawnManager) and wander-target picking (SpiderAI), so the
 * same rule that stops spawning over air/water/lava/void also stops the spider from wandering into
 * a hazard.
 *
 * Works for normal survival, custom maps, SkyBlock and OneBlock: instead of trusting a single
 * heightmap lookup (which can be wrong on floating islands, over caves, or in a column with no
 * blocks at all), it scans downward from the heightmap hit until it finds an actual solid,
 * non-liquid surface with clear space above it - or gives up if it runs out of search depth.
 *
 * (Contributed by the community Fabric patch; translated from Yarn to Mojang mappings.)
 */
object SafeGroundFinder {

    /**
     * Returns the Y coordinate to stand a spider on at (x, z), or null if no safe surface could
     * be found within the configured search depth (e.g. the column is entirely air - a gap
     * between SkyBlock islands, or open sky with nothing below).
     */
    fun findSafeY(level: ServerLevel, x: Double, z: Double): Double? {
        val blockX = floor(x).toInt()
        val blockZ = floor(z).toInt()

        // Only ever consider spots in generated/loaded terrain - never force-generate chunks just
        // to look for a spawn point, and never trust a chunk that hasn't generated its surface.
        if (!level.hasChunk(blockX shr 4, blockZ shr 4)) return null

        val topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ)
        val maxSearch = Config.SPAWN_MAX_VERTICAL_SEARCH.get()

        var y = topY.coerceAtMost(level.maxBuildHeight - 1)
        val bottomLimit = (topY - maxSearch).coerceAtLeast(level.minBuildHeight)

        val pos = BlockPos.MutableBlockPos()
        while (y > bottomLimit) {
            pos.set(blockX, y, blockZ)
            if (isSolidDryGround(level, pos)) {
                pos.set(blockX, y + 1, blockZ)
                val feetClear = isPassable(level, pos)
                pos.set(blockX, y + 2, blockZ)
                val headClear = isPassable(level, pos)
                if (feetClear && headClear) return (y + 1).toDouble()
            }
            y--
        }

        return null
    }

    /**
     * DIMENSION-AWARE safe ground at (x, z): in normal open-sky dimensions this is the heightmap
     * scan of [findSafeY]; in CEILING'D dimensions (the Nether, and any modded roofed dimension)
     * the heightmap returns the bedrock ROOF, so instead we scan for safe ground around [refY] —
     * the altitude of whatever we care about (the player being spawned near, the wander anchor).
     * Every caller that used to use findSafeY directly should use this with a sensible refY.
     */
    fun groundYAt(level: ServerLevel, x: Double, z: Double, refY: Double): Double? =
        if (level.dimensionType().hasCeiling()) findSafeYNear(level, x, z, refY) else findSafeY(level, x, z)

    /**
     * Ceiling-dimension variant of [findSafeY]: scans DOWN from refY + 16 (catching ledges a bit
     * above) to refY - spawnMaxVerticalSearch, using the same solid + dry + 2-blocks-clear rules.
     * Never touches the heightmap, so the Nether's bedrock roof is irrelevant.
     */
    fun findSafeYNear(level: ServerLevel, x: Double, z: Double, refY: Double): Double? {
        val blockX = floor(x).toInt()
        val blockZ = floor(z).toInt()
        if (!level.hasChunk(blockX shr 4, blockZ shr 4)) return null

        var y = (floor(refY).toInt() + 16).coerceAtMost(level.maxBuildHeight - 2)
        val bottomLimit = (floor(refY).toInt() - Config.SPAWN_MAX_VERTICAL_SEARCH.get())
            .coerceAtLeast(level.minBuildHeight)

        val pos = BlockPos.MutableBlockPos()
        while (y > bottomLimit) {
            pos.set(blockX, y, blockZ)
            if (isSolidDryGround(level, pos)) {
                pos.set(blockX, y + 1, blockZ)
                val feetClear = isPassable(level, pos)
                pos.set(blockX, y + 2, blockZ)
                val headClear = isPassable(level, pos)
                if (feetClear && headClear) return (y + 1).toDouble()
            }
            y--
        }
        return null
    }

    /**
     * The Y to place something on the FLOOR directly below (x, y, z) — scanning straight down
     * from the given Y, NOT from the heightmap. Unlike [findSafeY] (a surface-spawning helper),
     * this works underground: a cave kill finds the cave floor, not the mountaintop above it.
     * Returns null only when there's nothing below within [maxDepth] blocks (open void).
     */
    fun findFloorBelow(level: ServerLevel, x: Double, y: Double, z: Double, maxDepth: Int = 96): Double? {
        val blockX = floor(x).toInt()
        val blockZ = floor(z).toInt()
        val startY = floor(y).toInt().coerceAtMost(level.maxBuildHeight - 1)
        val bottomLimit = (startY - maxDepth).coerceAtLeast(level.minBuildHeight)

        val pos = BlockPos.MutableBlockPos()
        for (yy in startY downTo bottomLimit) {
            pos.set(blockX, yy, blockZ)
            val state = level.getBlockState(pos)
            if (!state.isAir && !state.getCollisionShape(level, pos).isEmpty) return (yy + 1).toDouble()
        }
        return null
    }

    /**
     * Depth (in blocks) of the contiguous WATER column sitting on the floor at (x, z), counting
     * upward from [floorY] (a floor-top Y as returned by [findFloorBelow]). 0.0 when the floor
     * is dry. Waterlogged plants (kelp, seagrass) count as water — their fluid state IS water —
     * so kelp forests measure as the deep water they are.
     */
    fun waterDepthAbove(level: ServerLevel, x: Double, floorY: Double, z: Double, maxDepth: Int = 64): Double {
        val blockX = floor(x).toInt()
        val blockZ = floor(z).toInt()
        var y = floor(floorY).toInt()
        val top = (y + maxDepth).coerceAtMost(level.maxBuildHeight - 1)
        val pos = BlockPos.MutableBlockPos()
        var depth = 0
        while (y <= top) {
            pos.set(blockX, y, blockZ)
            if (!level.getFluidState(pos).`is`(FluidTags.WATER)) break
            depth++
            y++
        }
        return depth.toDouble()
    }

    /**
     * Height (2 = doorway, 1 = crawl-hole, 0 = none) of a passable ground-level opening at
     * (x, z) whose floor sits at feet level [groundY] — used by the chase pathfinding to slip
     * THROUGH a wall instead of steering around it. The ground scanners can't see these
     * (they scan top-down and find the wall top first), so blocked path columns get this
     * explicit check. Dry passables only: a water-filled gap is not a doorway.
     */
    fun openingHeight(level: ServerLevel, x: Double, groundY: Double, z: Double): Int {
        val blockX = floor(x).toInt()
        val blockZ = floor(z).toInt()
        val feetY = floor(groundY).toInt()
        val pos = BlockPos.MutableBlockPos()
        pos.set(blockX, feetY - 1, blockZ)
        if (!level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) return 0   // no floor
        pos.set(blockX, feetY, blockZ)
        if (!isDoorway(level, pos)) return 0
        pos.set(blockX, feetY + 1, blockZ)
        return if (isDoorway(level, pos)) 2 else 1
    }

    /** A ground-level gap the spider can fit through: a doorway (2 high) or a crawl-hole (1).
     *  [alongX] is the axis you travel THROUGH it on (walls stand on the other axis), which is
     *  what lets the chase line itself up square with the gap instead of clipping the frame. */
    class Opening(val x: Double, val y: Double, val z: Double, val height: Int, val alongX: Boolean)

    /**
     * HUNT FOR THE DOOR. A doorway is one block wide, so the straight line from the spider to
     * the player almost never passes through it — checking only the column where the line hits
     * the wall (which is what the chase used to do) finds a doorway just about never, and the
     * spider circles the building instead. This scans the obstacle around the hit point and
     * returns every way IN, ordered by how close each one gets to the player, for the caller to
     * pick the nearest one it can actually reach.
     *
     * A candidate must be a genuine GAP IN A BARRIER, not just open floor: it needs solid
     * blocks on BOTH sides of one axis (north+south, or east+west) at feet level. That is what
     * a doorway, a gate or a mouse-hole looks like, and it rejects the open floor inside the
     * room (which is otherwise "floor with air above" and would otherwise win for being nearest
     * the player), room corners, and the ground out in the open.
     */
    fun collectOpenings(
        level: ServerLevel,
        hitX: Double, hitZ: Double, groundY: Double,
        targetX: Double, targetZ: Double,
        radius: Int, minHeight: Int,
        limit: Int = 12,
    ): List<Opening> {
        val baseX = floor(hitX).toInt()
        val baseZ = floor(hitZ).toInt()
        val found = ArrayList<Pair<Double, Opening>>()

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val bx = baseX + dx
                val bz = baseZ + dz
                if (!level.hasChunk(bx shr 4, bz shr 4)) continue
                val x = bx + 0.5
                val z = bz + 0.5

                for (dy in -1..1) {
                    val y = groundY + dy
                    val height = openingHeight(level, x, y, z)
                    if (height < minHeight) continue
                    val pinch = pinchAxis(level, bx, floor(y).toInt(), bz)
                    if (pinch == 0) continue
                    found.add(((targetX - x) * (targetX - x) + (targetZ - z) * (targetZ - z))
                        to Opening(x, y, z, height, alongX = pinch == 2))
                    break
                }
            }
        }
        return found.sortedBy { it.first }.take(limit).map { it.second }
    }

    /**
     * What a tight space is doing to a body sitting at (x, y, z): the floor it should stand on,
     * and the centre line it must not stray off. Null fields mean "not confined on that count".
     *
     * This is deliberately a property of WHERE THE BODY IS, not of what the AI is currently
     * planning. The chase drops its waypoint the moment the straight line to the player clears
     * — which, inside a corridor, is exactly when the spider is deepest in the tunnel — so any
     * protection hung off the pathfinder evaporates at the worst moment and the body drifts
     * into a wall and gets launched through the roof. Asking the world instead means the spider
     * is protected in a corridor whether it is chasing, wandering, or standing still.
     */
    class Confinement(
        val floorY: Double?,
        val laneX: Double?,
        val laneZ: Double?,
        /** Ceiling height above the floor, when there is a ceiling worth caring about. This is
         *  what stops the spider INFLATING back to its distance-based size inside a tunnel and
         *  ramming its way out through the roof. */
        val headroom: Double? = null,
    ) {
        val any get() = floorY != null || laneX != null || laneZ != null
    }

    private val NOT_CONFINED = Confinement(null, null, null)

    /**
     * [groundY] is the body's GROUND PLANE — the level its legs stand on (`position.y -
     * bodyHeight`), not its centre. That distinction is the whole fix for the oversized-body
     * deadlock; see the floor hunt below.
     */
    fun confinementAt(level: ServerLevel, x: Double, groundY: Double, z: Double, bodyHeight: Double): Confinement {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()

        // THE DEADLOCK THIS FIXES. The floor hunt used to start at the body's CENTRE and scan
        // down. A body too big for the passage carries its centre at or above the passage
        // CEILING, so that scan hit the ceiling first and recorded it as the floor — the reading
        // came back "not confined", no headroom was reported, SpiderMob never capped the size,
        // and the body therefore stayed too big to ever be detected. Too big to see the corridor,
        // and never told to shrink because it couldn't see it: the spider parks outside forever.
        // Chase size is distance-based, so a spider that comes at you from range arrives LARGE —
        // exactly the case the detector was blind to. Anchoring at the ground plane instead makes
        // the reading independent of how big the body currently is.
        val startY = floor(groundY + 0.5).toInt()

        var floorTop = Double.NaN
        for (dy in 0 downTo -4) {
            if (isBodyBlocking(level, bx, startY + dy, bz)) { floorTop = (startY + dy + 1).toDouble(); break }
        }
        if (floorTop.isNaN()) return NOT_CONFINED
        // Are the legs actually standing on THIS floor? Size-independent, unlike the old test,
        // which measured the body CENTRE against a fixed 2.5-block tolerance and so was failed by
        // any spider taller than that simply for being tall.
        val offGround = groundY - floorTop
        if (offGround > 1.5 || offGround < -1.5) return NOT_CONFINED

        // Walls on both sides of one axis = a corridor, and its centre line is this column.
        val feetY = floorTop.toInt()
        val walledX = isBodyBlocking(level, bx + 1, feetY, bz) && isBodyBlocking(level, bx - 1, feetY, bz)
        val walledZ = isBodyBlocking(level, bx, feetY, bz + 1) && isBodyBlocking(level, bx, feetY, bz - 1)
        if (!walledX && !walledZ) return NOT_CONFINED

        // Headroom decides whether the height has to be pinned: the collision step casts from a
        // block ABOVE the body, so a ceiling that close would "hit" and teleport it upward.
        var headroom = Double.MAX_VALUE
        for (dy in 0..4) {
            if (isBodyBlocking(level, bx, feetY + dy, bz)) { headroom = dy.toDouble(); break }
        }
        // A ceiling at all means the size has to be capped; a LOW one also pins the height.
        val roofed = headroom != Double.MAX_VALUE

        // Now that big bodies are visible to this scan at all, they must not be lane-locked by
        // scenery they simply STRIDE OVER. A tall spider crossing a one-block ditch, or stepping
        // between two low blocks, has walls beside its FEET and nothing beside its body — pinning
        // it to a centre line there would be a brand new way to get stuck. So an unroofed pinch
        // only counts as confining if its walls actually rise into the body.
        if (!roofed && sideWallHeight(level, bx, bz, feetY, walledX) < bodyHeight * 0.6) return NOT_CONFINED

        val pinFloor = if (headroom < bodyHeight + 1.5) floorTop else null

        return Confinement(
            floorY = pinFloor,
            laneX = if (walledX) bx + 0.5 else null,
            laneZ = if (walledZ && !walledX) bz + 0.5 else null,
            headroom = if (roofed) headroom else null,
        )
    }

    /** How far the confining side walls rise above the passage floor, in blocks (capped at 7). */
    private fun sideWallHeight(level: ServerLevel, bx: Int, bz: Int, feetY: Int, walledX: Boolean): Double {
        var height = 0
        for (dy in 0..6) {
            val both = if (walledX)
                isBodyBlocking(level, bx + 1, feetY + dy, bz) && isBodyBlocking(level, bx - 1, feetY + dy, bz)
            else
                isBodyBlocking(level, bx, feetY + dy, bz + 1) && isBodyBlocking(level, bx, feetY + dy, bz - 1)
            if (!both) break
            height = dy + 1
        }
        return height.toDouble()
    }

    /** Would this block stop the spider's body? (Collision-shaped; a doorway's own door does
     *  not count — the spider goes through those.) */
    private fun isBodyBlocking(level: ServerLevel, x: Int, y: Int, z: Int): Boolean {
        val pos = BlockPos(x, y, z)
        val state = level.getBlockState(pos)
        if (state.isAir) return false
        if (state.`is`(BlockTags.DOORS) || state.`is`(BlockTags.TRAPDOORS) || state.`is`(BlockTags.FENCE_GATES)) return false
        return !state.getCollisionShape(level, pos).isEmpty
    }

    /**
     * How far the passage at this opening runs along its axis: the block-centre coordinates of
     * its first and last column. A doorway in a one-block wall gives the same value twice; the
     * tunnel bored through a hillside gives its two mouths. The chase needs this because a
     * corridor is not "one step through" — the body has to be steered and held on the centre
     * line for the WHOLE length, or it clips a wall and gets shoved up through the roof.
     */
    fun passageExtent(level: ServerLevel, opening: Opening, minHeight: Int, maxLength: Int = 32): Pair<Double, Double> {
        val alongX = opening.alongX
        val wantAxis = if (alongX) 2 else 1
        var lo = if (alongX) opening.x else opening.z
        var hi = lo

        for (dir in intArrayOf(-1, 1)) {
            for (step in 1..maxLength) {
                val cx = if (alongX) opening.x + dir * step else opening.x
                val cz = if (alongX) opening.z else opening.z + dir * step
                if (openingHeight(level, cx, opening.y, cz) < minHeight) break
                if (pinchAxis(level, floor(cx).toInt(), floor(opening.y).toInt(), floor(cz).toInt()) != wantAxis) break
                val along = if (alongX) cx else cz
                if (dir < 0) lo = along else hi = along
            }
        }
        return lo to hi
    }

    /**
     * Is (x, feetY, z) a gap THROUGH something rather than just open floor? Returns 0 for no,
     * 1 when walls stand east+west (so you pass along Z), 2 when they stand north+south (pass
     * along X). Walls on both sides of one axis is the signature of a doorway, gate or
     * mouse-hole; it rejects room interiors, corners and open ground.
     */
    private fun pinchAxis(level: ServerLevel, x: Int, feetY: Int, z: Int): Int {
        val pos = BlockPos.MutableBlockPos()
        fun blocked(ox: Int, oz: Int): Boolean {
            pos.set(x + ox, feetY, z + oz)
            return !isPassable(level, pos)
        }
        if (blocked(1, 0) && blocked(-1, 0)) return 1    // walls on X -> travel along Z
        if (blocked(0, 1) && blocked(0, -1)) return 2    // walls on Z -> travel along X
        return 0
    }

    /** Solid enough to stand on and not a liquid: never place a spider on/in water or lava. */
    private fun isSolidDryGround(level: ServerLevel, pos: BlockPos): Boolean {
        if (!level.getFluidState(pos).isEmpty) return false
        val state = level.getBlockState(pos)
        if (state.isAir) return false
        return state.isFaceSturdy(level, pos, Direction.UP)
    }

    /** Free space for the body: no liquid, and either air or something with no collision shape. */
    private fun isPassable(level: ServerLevel, pos: BlockPos): Boolean {
        if (!level.getFluidState(pos).isEmpty) return false
        val state = level.getBlockState(pos)
        return state.isAir || state.getCollisionShape(level, pos).isEmpty
    }

    /**
     * Free space *for getting through a wall*: [isPassable], plus doors, trapdoors and fence
     * gates whether they are open or SHUT. A closed door has a collision shape, so the plain
     * passable test reads an ordinary front door as solid wall — which is exactly why the
     * spider used to ignore doorways. Shut doors are no obstacle to something that can flow
     * through the frame; tagged so modded doors count too.
     */
    private fun isDoorway(level: ServerLevel, pos: BlockPos): Boolean {
        if (isPassable(level, pos)) return true
        if (!level.getFluidState(pos).isEmpty) return false
        val state = level.getBlockState(pos)
        return state.`is`(BlockTags.DOORS) || state.`is`(BlockTags.TRAPDOORS) || state.`is`(BlockTags.FENCE_GATES)
    }
}
