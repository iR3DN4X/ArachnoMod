package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
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
}
