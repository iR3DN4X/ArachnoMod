package com.heledron.spideranimation.entity

import com.heledron.spideranimation.Config
import com.heledron.spideranimation.platform.dimensionId
import com.heledron.spideranimation.platform.playSoundAt
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.storage.LevelResource
import org.joml.Vector3d
import java.nio.file.Files
import java.nio.file.Path

/**
 * WARDING IDOLS — the placed blocks that make a base a place the hunt does not start.
 *
 * A player standing inside a ward's radius stops being a valid SPAWN ANCHOR: the spawn manager
 * will not pick them, and never drops a spider inside a ward even when anchoring on someone
 * else. That is the whole mechanism, and it matters that it works per PLACE rather than by
 * freezing the global timer — there is only ever ONE spider and ONE countdown, so a global
 * freeze would mean one player idling at base makes the entire server safe, including the
 * friend out exploring. Protecting a place instead leaves the hunt running for everyone else,
 * and when every player happens to be warded the countdown pauses on its own.
 *
 * It is also a SAFE ZONE you can run to. The spider's body can never get more than
 * [BITE_BAND] (one block) past the edge — SpiderBody enforces that as a one-way barrier, so a
 * chase stops dead at the line. Anyone deeper inside than that is invisible to it: it will not
 * start a hunt on them, keep one going, bite them or blind them. Within that last block of the
 * edge it can still reach you. A spider that ends up inside anyway (the idol armed around it,
 * say) runs straight away from the idol until it is out (SpiderAI's REPEL mode).
 *
 * The ENRAGED boss is the exception to all of it: no fence, no REPEL, nobody hidden from it - and
 * any idol it gets within [ENRAGED_SHATTER_RANGE] of is blown apart ([shatterNear]). A player who
 * chose to enrage it does not get to hide from the fight.
 *
 * Positions live in their own file in the world folder rather than in NBT, so protection still
 * holds while the base's chunks are unloaded (an offline or far-away base is exactly when it
 * matters). A plain text file, like the permadeath record, because SavedData's API differs
 * between the versions this mod ships on — but a SEPARATE file, so nothing here can corrupt
 * the kill record that permadeath depends on.
 */
object WardRegistry {
    data class Ward(
        val dimension: String,
        val x: Int, val y: Int, val z: Int,
        /** Level gameTime when it was placed — the arming delay is play time, and survives restarts. */
        val placedGameTime: Long,
    )

    private const val FILE = "arachnomod_wards.txt"
    private const val HEADER = "# ArachnoMod warding idols. One per line: ward=<dimension>,<x>,<y>,<z>,<placedGameTime>"

    private val wards = mutableListOf<Ward>()
    private var loaded = false
    private var sweepTimer = 0

    /**
     * Is this block state one of ours? Supplied by each loader at init, because the block handle
     * is a plain Block on Fabric and a deferred holder needing .get() on NeoForge/Forge — keeping
     * that difference out of here is what stops a copy between loaders silently breaking it.
     */
    var isWardBlock: (net.minecraft.world.level.block.state.BlockState) -> Boolean = { false }

    /** Reset between worlds (the mod object is reused across single-player worlds in one session). */
    fun reset() {
        wards.clear()
        loaded = false
        sweepTimer = 0
    }

    private fun file(server: MinecraftServer): Path =
        server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE)

    fun ensureLoaded(server: MinecraftServer) {
        if (loaded) return
        loaded = true
        wards.clear()
        try {
            val f = file(server)
            if (!Files.exists(f)) return
            for (line in Files.readAllLines(f)) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("ward=")) continue      // comments and blanks
                val p = trimmed.removePrefix("ward=").split(',')
                if (p.size < 5) continue
                val x = p[1].trim().toIntOrNull() ?: continue
                val y = p[2].trim().toIntOrNull() ?: continue
                val z = p[3].trim().toIntOrNull() ?: continue
                val placed = p[4].trim().toLongOrNull() ?: continue
                wards += Ward(p[0].trim(), x, y, z, placed)
            }
        } catch (e: Exception) {
            wards.clear()   // unreadable record: no wards rather than a broken world
        }
    }

    private fun save(server: MinecraftServer) {
        try {
            val f = file(server)
            Files.createDirectories(f.parent)
            Files.write(f, listOf(HEADER) + wards.map {
                "ward=${it.dimension},${it.x},${it.y},${it.z},${it.placedGameTime}"
            })
        } catch (e: Exception) {
            // Can't write (read-only world dir?): the wards just won't survive this restart.
        }
    }

    // ---- placement ---------------------------------------------------------------------------

    /** All wards in this dimension. */
    private fun inDimension(level: ServerLevel): List<Ward> {
        val id = level.dimensionId()
        return wards.filter { it.dimension == id }
    }

    private fun horizontalDistance(w: Ward, x: Double, z: Double): Double {
        val dx = (w.x + 0.5) - x
        val dz = (w.z + 0.5) - z
        return Math.sqrt(dx * dx + dz * dz)
    }

    /**
     * The ward that blocks building here, or null. Spacing is the real anti-spam lever: cost
     * alone never stops a group from carpeting a map, but a minimum separation does.
     */
    fun blockingNeighbour(level: ServerLevel, pos: BlockPos): Ward? {
        val server = level.server
        ensureLoaded(server)
        val spacing = Config.WARD_MIN_SPACING.get()
        if (spacing <= 0.0) return null
        return inDimension(level).firstOrNull {
            (it.x != pos.x || it.y != pos.y || it.z != pos.z) &&
                horizontalDistance(it, pos.x + 0.5, pos.z + 0.5) < spacing
        }
    }

    fun add(level: ServerLevel, pos: BlockPos) {
        val server = level.server
        ensureLoaded(server)
        val id = level.dimensionId()
        if (wards.any { it.dimension == id && it.x == pos.x && it.y == pos.y && it.z == pos.z }) return
        wards += Ward(id, pos.x, pos.y, pos.z, level.gameTime)
        save(server)
    }

    fun remove(level: ServerLevel, pos: BlockPos) {
        val server = level.server
        ensureLoaded(server)
        val id = level.dimensionId()
        if (wards.removeAll { it.dimension == id && it.x == pos.x && it.y == pos.y && it.z == pos.z }) {
            save(server)
        }
    }

    // ---- protection --------------------------------------------------------------------------

    private fun armTicks(): Long = (Config.WARD_ARMING_SECONDS.get() * 20.0).toLong()

    /** A freshly placed ward is inert for wardArmingSeconds (default 5) before it protects anything. */
    fun isArmed(level: ServerLevel, ward: Ward): Boolean =
        level.gameTime - ward.placedGameTime >= armTicks()

    /**
     * Is this spot inside an armed ward? A CYLINDER, not a sphere: horizontal distance only, so
     * a base is protected from bedrock to sky and the rule stays predictable ("it spawned in my
     * basement" is exactly the surprise a sphere produces).
     */
    fun isProtected(level: ServerLevel, x: Double, z: Double): Boolean {
        if (!Config.WARD_ENABLED.get()) return false
        ensureLoaded(level.server)
        if (wards.isEmpty()) return false                  // the common case, before any are built
        val radius = Config.WARD_RADIUS.get()
        val id = level.dimensionId()
        for (w in wards) {
            if (w.dimension != id) continue
            if (horizontalDistance(w, x, z) > radius) continue
            if (isArmed(level, w)) return true
        }
        return false
    }

    // ---- the safe zone -----------------------------------------------------------------------

    /** How far past the edge the spider may still reach: its body stops here, and a player
     *  standing within this last stretch of the zone can still be hunted and bitten. */
    const val BITE_BAND = 1.0

    /** An armed ward's circle, in block-centre coordinates. */
    class Zone(val x: Double, val z: Double, val radius: Double) {
        fun distance(px: Double, pz: Double): Double {
            val dx = px - x
            val dz = pz - z
            return Math.sqrt(dx * dx + dz * dz)
        }
    }

    /** Every armed zone in this level whose edge lies within [margin] of (x, z), or that holds it. */
    fun zonesNear(level: ServerLevel, x: Double, z: Double, margin: Double): List<Zone> {
        if (!Config.WARD_ENABLED.get()) return emptyList()
        ensureLoaded(level.server)
        if (wards.isEmpty()) return emptyList()
        val radius = Config.WARD_RADIUS.get()
        val id = level.dimensionId()
        var out: MutableList<Zone>? = null
        for (w in wards) {
            if (w.dimension != id) continue
            if (horizontalDistance(w, x, z) > radius + margin) continue
            if (!isArmed(level, w)) continue
            if (out == null) out = mutableListOf()
            out += Zone(w.x + 0.5, w.z + 0.5, radius)
        }
        return out ?: emptyList()
    }

    /** The zone holding (x, z) that it sits DEEPEST inside, or null when it is outside them all. */
    fun zoneAt(level: ServerLevel, x: Double, z: Double): Zone? =
        zonesNear(level, x, z, 0.0).minByOrNull { it.distance(x, z) - it.radius }

    /**
     * Is a player standing here out of the spider's reach? True once they are more than
     * [BITE_BAND] inside an armed zone. Such a player is invisible to it: no hunt starts on
     * them, a running one breaks off, and it can neither bite nor blind them.
     */
    fun isSafe(level: ServerLevel, x: Double, z: Double): Boolean {
        val zone = zoneAt(level, x, z) ?: return false
        return zone.distance(x, z) < zone.radius - BITE_BAND
    }

    /** How long until this spot's ward arms, in seconds; null when it is already protected. */
    fun secondsUntilArmed(level: ServerLevel, pos: BlockPos): Long? {
        ensureLoaded(level.server)
        val id = level.dimensionId()
        val w = wards.firstOrNull { it.dimension == id && it.x == pos.x && it.y == pos.y && it.z == pos.z }
            ?: return null
        if (isArmed(level, w)) return null
        // Rounded UP: with a fraction of a second left it is still asleep, and "0 seconds" would lie.
        return ((w.placedGameTime + armTicks()) - level.gameTime + 19) / 20
    }

    fun count(): Int = wards.size

    // ---- the enraged boss ----------------------------------------------------------------------

    /** How close (blocks, from its feet) the ENRAGED boss has to get to an idol to blow it apart. */
    const val ENRAGED_SHATTER_RANGE = 16.0

    /**
     * Blow apart every idol within [range] of (x, y, z) - the enraged boss's answer to them. It looks
     * and sounds like an explosion but leaves no crater, so a base never loses more than the idol
     * itself, and nothing drops: the idol is gone. Everyone nearby is told. Returns how many went.
     */
    fun shatterNear(level: ServerLevel, x: Double, y: Double, z: Double, range: Double): Int {
        ensureLoaded(level.server)
        if (wards.isEmpty()) return 0
        val id = level.dimensionId()
        val hit = wards.filter { w ->
            val dx = w.x + 0.5 - x
            val dy = w.y + 0.5 - y
            val dz = w.z + 0.5 - z
            w.dimension == id && dx * dx + dy * dy + dz * dz <= range * range
        }
        if (hit.isEmpty()) return 0
        for (w in hit) {
            val pos = BlockPos(w.x, w.y, w.z)
            val cx = w.x + 0.5
            val cy = w.y + 0.5
            val cz = w.z + 0.5
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 24, 0.6, 0.6, 0.6, 0.03)
            level.playSoundAt(Vector3d(cx, cy, cz), SoundEvents.DRAGON_FIREBALL_EXPLODE, 4.0f, 0.8f)
            level.playSoundAt(Vector3d(cx, cy, cz), SoundEvents.WITHER_BREAK_BLOCK, 2.0f, 0.7f)
            // The removal hook drops the registry entry too; this covers a block that is already gone.
            if (level.isLoaded(pos) && isWardBlock(level.getBlockState(pos))) level.destroyBlock(pos, false)
            wards.remove(w)
            for (player in level.players()) {
                val px = player.x - cx
                val pz = player.z - cz
                if (px * px + pz * pz > SHATTER_NEWS_RANGE * SHATTER_NEWS_RANGE) continue
                player.sendSystemMessage(Component.literal(
                    "The enraged spider blew apart the Warding Idol at (${w.x}, ${w.y}, ${w.z}). Nowhere is safe from it."))
            }
        }
        save(level.server)
        return hit.size
    }

    /** Who hears that an idol went: anyone within this many blocks, horizontally. */
    private const val SHATTER_NEWS_RANGE = 256.0

    // ---- upkeep ------------------------------------------------------------------------------

    /**
     * Periodic validation. A ward can vanish in ways no block hook covers — another mod, an
     * edited world, a restored backup — so anything whose chunk is loaded and no longer holds a
     * ward block is dropped. Chunks that are NOT loaded are left alone on purpose: that is an
     * offline base, and forgetting it there would quietly un-protect it.
     */
    fun sweep(server: MinecraftServer) {
        ensureLoaded(server)
        if (wards.isEmpty()) return
        if (++sweepTimer < 200) return                     // every 10s; the scan is tiny
        sweepTimer = 0
        var changed = false
        for (level in server.allLevels) {
            val id = level.dimensionId()
            val gone = wards.filter { w ->
                w.dimension == id &&
                    level.isLoaded(BlockPos(w.x, w.y, w.z)) &&
                    !isWardBlock(level.getBlockState(BlockPos(w.x, w.y, w.z)))
            }
            if (gone.isNotEmpty()) { wards.removeAll(gone); changed = true }
        }
        if (changed) save(server)
    }
}
