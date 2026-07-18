package com.heledron.spideranimation.entity

import com.heledron.spideranimation.AppState
import com.heledron.spideranimation.Config
import com.heledron.spideranimation.SpiderAnimationMod
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.platform.playSoundAt
import com.heledron.spideranimation.spider.DirectionBehaviour
import com.heledron.spideranimation.spider.SpiderBehaviour
import com.heledron.spideranimation.spider.SpiderBody
import com.heledron.spideranimation.spider.StayStillBehaviour
import com.heledron.spideranimation.spider.camoSpider
import com.heledron.spideranimation.spider.defaultSpider
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.SpawnGroupData
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import kotlin.math.sqrt

/**
 * A real hostile Mob that *owns* a [SpiderBody] simulation. The mob itself is invisible (a
 * NoopRenderer is registered on the client); the visible spider is the BlockDisplay legs driven by
 * the simulation. The mob exists to provide:
 *   - a 1000 HP health pool,
 *   - a hittable hitbox synced to the simulated body position,
 *   - melee damage (6 hearts) to nearby players,
 *   - taming (Spider Tamer item) -> docile, and riding with horse-like steering,
 *   - a 50% netherite ingot death drop,
 *   - a spawn egg + vanilla entity lifecycle.
 *
 * Movement/chasing is still done by the ECS simulation (see [AppState] "chase nearest player"), so
 * the mob runs no vanilla movement goals and is [noPhysics] — its position is overwritten from the
 * simulation every tick. While RIDDEN, the mob takes manual control of the body instead: the rider
 * steers by looking + pressing forward (W), and the size pins to a mount-friendly scale.
 */
/** Visual variants of the spider. NETHERITE is the classic; CAMO is the mossy one (v1.1). */
enum class SpiderVariant(val key: String) { NETHERITE("netherite"), CAMO("camo") }

class SpiderMob(type: EntityType<out SpiderMob>, level: Level) : Monster(type, level) {
    private var body: SpiderBody? = null
    private var ecsEntity: EcsEntity? = null
    private var attackCooldown = 0
    private var currentScale = 1.0

    /** Set by the spawn manager BEFORE the first tick (the body is built lazily on tick 1). */
    var variant = SpiderVariant.NETHERITE

    /** Tamed = docile: never attacks, but keeps every other behaviour (chase, growth, speed). */
    var tamed = false
        private set

    init {
        noPhysics = true       // position is driven by the simulation, not vanilla physics
        isNoGravity = true
    }

    companion object {
        // Nearly every gameplay number lives in config/arachnomod-common.toml (see Config) and is
        // read LIVE each tick. Only the melee reach geometry stays hardcoded:
        const val ATTACK_REACH = 3.5
        const val REACH_SCALE_CAP = 2.0   // don't let the giant form melee from absurd distances

        // Registration-time defaults; the real max health from the config is applied per-instance
        // in ensureBody (attributes are built before configs are guaranteed loaded).
        fun createAttributes(): AttributeSupplier.Builder =
            createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)

        /** Map a size scale to a travel-speed factor: 1.0 (base chase speed) at the smallest,
         *  up to speedGrowthFactor at the largest. Shared with the /spider possession feature. */
        fun scaleToSpeedFactor(scale: Double): Double {
            val minScale = Config.MIN_SIZE.get()
            val maxScale = Config.MAX_SIZE.get()
            val t = if (maxScale > minScale)
                ((scale - minScale) / (maxScale - minScale)).coerceIn(0.0, 1.0) else 1.0
            return 1.0 + (Config.SPEED_GROWTH_FACTOR.get() - 1.0) * t
        }
    }

    // The spider is a "boss"-style mob: never auto-despawn from distance. It DOES despawn in
    // Peaceful like any other monster (Monster's default shouldDespawnInPeaceful() = true), and
    // the spawn manager pauses natural spawns while the difficulty is Peaceful.
    override fun removeWhenFarAway(distanceToClosestPlayer: Double) = false

    // Ephemeral: never written to the world save. If its chunk unloads it's gone for good (the spawn
    // manager then drops a fresh one near a player). This keeps "only ever one" airtight — a saved
    // copy can never reload and become a second spider.
    override fun shouldBeSaved() = false

    // Lava is variant-flavoured: the NETHERITE spider is forged of the stuff, so like netherite
    // gear it neither burns nor takes ANY fire-type damage (lava, magma blocks, campfires) —
    // fireImmune also makes isOnFire() report false, so no flame overlay ever shows. The CAMO
    // variant is living moss and undergrowth: it burns like it (vanilla lava/burn damage).
    override fun fireImmune() = variant == SpiderVariant.NETHERITE

    // Movement is handled by the ECS simulation, not vanilla goals.
    override fun registerGoals() {}

    // Spawn eggs roll the camo chance too (natural spawns roll it in SpiderSpawnManager, which
    // sets `variant` directly). This makes `/spider config camoVariantChance set 1.0` + one egg
    // click a guaranteed camo — no waiting on the natural respawn timer to see the variant.
    // 1.20.1 API: finalizeSpawn still takes the trailing CompoundTag (dropped in 1.20.5+).
    override fun finalizeSpawn(
        level: ServerLevelAccessor,
        difficulty: DifficultyInstance,
        spawnType: MobSpawnType,
        spawnGroupData: SpawnGroupData?,
        spawnTag: CompoundTag?,
    ): SpawnGroupData? {
        if (spawnType == MobSpawnType.SPAWN_EGG && random.nextDouble() < Config.CAMO_VARIANT_CHANCE.get()) {
            variant = SpiderVariant.CAMO
        }
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, spawnTag)
    }

    // Seat the rider on top of the body (entity pos = body centre; body model is ~0.5 blocks tall
    // per scale unit). 1.20.1 API: getPassengersRidingOffset (renamed getPassengerAttachmentPoint in 1.21).
    override fun getPassengersRidingOffset(): Double = 0.25 * currentScale + 0.2

    /** Spider Tamer item -> tame (docile). Tamed + empty hand -> ride. */
    public override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        val level = level()

        if (stack.item === SpiderAnimationMod.SPIDER_TAMER.get()) {
            if (level is ServerLevel && !tamed) {
                tamed = true
                level.sendParticles(ParticleTypes.HEART, x, y + 1.5, z, 9, 1.2, 1.2, 1.2, 0.02)
                level.playSoundAt(Vector3d(x, y, z), SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f)
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        if (tamed && stack.isEmpty && passengers.isEmpty()) {
            if (!level.isClientSide) player.startRiding(this)
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        return super.mobInteract(player, hand)
    }

    private fun ensureBody(level: ServerLevel): SpiderBody {
        body?.let { return it }

        val options = if (variant == SpiderVariant.CAMO) camoSpider() else defaultSpider()
        val bodyHeight = options.walkGait.stationary.bodyHeight
        val spawn = Vector3d(x, y + bodyHeight, z)
        val yaw = Math.round(yRot / 45f) * 45f

        val (entity, newBody) = AppState.spawnBody(level, spawn, yaw, options)
        newBody.variantKey = variant.key   // routes step/land sounds (netherite = classic clank)
        ecsEntity = entity
        body = newBody
        SpiderSpawnManager.notifyAlive(this)   // register as THE spider (enforces only-one)

        // Apply the configured max health per-instance (attributes register before configs load;
        // health is per-VARIANT: the armored netherite runs leaner than the bare camo).
        // Safe to top up here: this runs once per mob, and spiders are never save/reloaded.
        getAttribute(Attributes.MAX_HEALTH)?.baseValue =
            if (variant == SpiderVariant.NETHERITE) Config.NETHERITE_MAX_HEALTH.get()
            else Config.CAMO_MAX_HEALTH.get()
        health = maxHealth

        // The NETHERITE variant wears what it's made of: the exact stats of a FULL suit of
        // netherite armor — 20 armor (3+8+6+3), 12 toughness (3x4), 0.4 knockback resistance
        // (0.1x4). The camo variant is bare moss: health alone. (Knockback resistance is moot
        // while the simulation pins the position, but it keeps the suit honest.)
        if (variant == SpiderVariant.NETHERITE) {
            getAttribute(Attributes.ARMOR)?.baseValue = 20.0
            getAttribute(Attributes.ARMOR_TOUGHNESS)?.baseValue = 12.0
            getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue = 0.4
        }

        return newBody
    }

    override fun tick() {
        super.tick()

        val level = level()
        if (level !is ServerLevel) return

        // Once dying/dead, let the vanilla death animation (the "poof") play out — do NOT recreate
        // the body, or we'd resurrect ourselves to full health every tick (the "refuses to die" bug).
        if (!isAlive) return

        val body = ensureBody(level)

        // Sync our hitbox to the simulated body so players can hit the (invisible) mob, and keep
        // vanilla from moving us.
        setPos(body.position.x, body.position.y, body.position.z)
        deltaMovement = Vec3.ZERO

        val rider = firstPassenger as? Player
        if (rider != null) {
            tickRidden(body, rider)
            return
        }
        body.manualControl = false

        // The CAMO variant burns VISIBLY: the mob itself is an invisible hitbox and BlockDisplays
        // can't catch fire, so vanilla burning alone would be a silent, invisible death. While it
        // is on fire, dress the body and the feet in flame + smoke so the player sees the moss
        // ablaze. (Positions come from the simulation, never the entity — the hitbox lags a tick.)
        if (variant == SpiderVariant.CAMO && isOnFire && tickCount % 3 == 0) {
            val p = body.position
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2,
                0.3 * currentScale, 0.2 * currentScale, 0.3 * currentScale, 0.01)
            level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.3 * currentScale, p.z, 1,
                0.2 * currentScale, 0.1, 0.2 * currentScale, 0.02)
            for (leg in body.legs) {
                if (random.nextFloat() < 0.35f) {
                    val foot = leg.endEffector
                    level.sendParticles(ParticleTypes.FLAME, foot.x, foot.y + 0.1, foot.z, 1, 0.05, 0.05, 0.05, 0.005)
                }
            }
        }

        // Movement, facing and SPEED are driven by SpiderAI's wander/alert/chase state machine
        // (ticked once per tick from AppState.driveWildSpiders — not here, so mode timers don't
        // run double-speed). This mob keeps what's physical about itself: size and the bite.

        // Nearest player (server-side ServerPlayer list).
        val nearest = level.players().minByOrNull {
            it.distanceToSqr(body.position.x, body.position.y, body.position.z)
        }

        // Distance-based grow/shrink — physical, so the IK feet stay planted at every size. Size
        // reacts to distance regardless of AI mode: a wandering spider far away is still huge.
        // THE SQUEEZE overrides it: pressing over a dug-in player, the spider shrinks below
        // minSize to squeezeSize — just small enough to fit a 1x1x1 hole — and comes in after
        // them; the moment the squeeze ends, distance-based sizing regrows it automatically.
        val horizontalDistance = nearest?.let {
            val dx = it.x - body.position.x; val dz = it.z - body.position.z
            sqrt(dx * dx + dz * dz)
        } ?: Config.SIZE_FAR_DISTANCE.get()
        val squeezing = ecsEntity?.let { SpiderAI.isSqueezing(it) } ?: false
        // GROW IN WATER (growInWater, default true): distance-based sizing keeps the spider SMALL
        // near a player, so a swimmer (or someone perched over a lake) used to watch it drown on
        // the lake floor. When the floor it stands on is submerged, grow it just big enough for
        // the body to ride above the surface, whatever the depth. Keyed on the ENVIRONMENT (water
        // column over its floor), not on "is the mob in water" — a grown body above the surface
        // is no longer in water, which would shrink, dunk and regrow it in an endless bob. The
        // squeeze still outranks it: it's the active kill move, and brief.
        val waterScale = if (!squeezing && Config.GROW_IN_WATER.get()) waterGrowthScale(level, body) else null
        val targetScale =
            if (squeezing) Config.SQUEEZE_SIZE.get()
            else maxOf(distanceToScale(horizontalDistance), waterScale ?: 0.0)
        currentScale = approachScale(currentScale, targetScale)
        body.setSizeScale(currentScale)

        // Melee: hit the nearest player if within (scaled) reach, on a cooldown. Tamed = docile.
        // Only bites while actually HUNTING (chase mode) — so a peacefully wandering spider (or
        // any spider during the day with hostileOnlyAtNight) never sucker-punches a bystander.
        if (attackCooldown > 0) attackCooldown--
        val hunting = ecsEntity?.let { SpiderAI.isChasing(it) } ?: false
        if (!tamed && hunting && nearest != null && attackCooldown == 0 && nearest.isAlive) {
            val reach = ATTACK_REACH * currentScale.coerceAtMost(REACH_SCALE_CAP)
            val distSqr = nearest.distanceToSqr(body.position.x, body.position.y, body.position.z)
            if (distSqr <= reach * reach) {
                nearest.hurt(damageSources().mobAttack(this), (Config.ATTACK_DAMAGE_HEARTS.get() * 2.0).toFloat())
                attackCooldown = Config.ATTACK_COOLDOWN_TICKS.get()
            }
        }
    }

    /**
     * Horse-like steering: the rider looks where they want to go and holds forward (W). The rider's
     * forward impulse (zza) is synced to the server by the vanilla ride-input packet. We drive the
     * ECS behaviour directly and flag the body as manually controlled so the "chase the nearest
     * player" system leaves it alone (the nearest player is, after all, sitting on it).
     */
    private fun tickRidden(body: SpiderBody, rider: Player) {
        body.manualControl = true

        val riddenSize = Config.RIDDEN_SIZE.get()
        currentScale = approachScale(currentScale, riddenSize)
        body.setSizeScale(currentScale)
        body.setSpeedScale(scaleToSpeedFactor(riddenSize))

        val entity = ecsEntity ?: return
        if (rider.zza > 0f) {
            val look = rider.lookAngle
            val dir = Vector3d(look.x, 0.0, look.z)
            if (dir.lengthSquared() > 1.0e-6) {
                dir.normalize()
                entity.replaceComponent<SpiderBehaviour>(DirectionBehaviour(dir, Vector3d(dir)))
                return
            }
        }
        entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
    }

    private fun distanceToScale(distance: Double): Double {
        val near = Config.SIZE_NEAR_DISTANCE.get()
        val far = Config.SIZE_FAR_DISTANCE.get()
        val t = if (far > near) ((distance - near) / (far - near)).coerceIn(0.0, 1.0) else 1.0
        return Config.MIN_SIZE.get() + (Config.MAX_SIZE.get() - Config.MIN_SIZE.get()) * t
    }

    /**
     * The size needed for the body centre to ride ~half a block above the water the spider is
     * standing in, or null when it isn't standing in water worth reacting to. The body stands at
     * floor + bodyHeight (which scales linearly with size), so the needed scale is simply
     * (depth + clearance) / per-unit bodyHeight. May exceed maxSize on purpose: "any body of
     * water" includes deep oceans. Depths of a block or less are ignored — the spider doesn't
     * drown in a creek, and inflating at every stream crossing would look jumpy.
     */
    private fun waterGrowthScale(level: ServerLevel, body: SpiderBody): Double? {
        val p = body.position
        val floorY = SafeGroundFinder.findFloorBelow(level, p.x, p.y, p.z) ?: return null
        val depth = SafeGroundFinder.waterDepthAbove(level, p.x, floorY, p.z)
        if (depth <= 1.0) return null
        val bodyHeightPerScale = body.walkGait.stationary.bodyHeight / body.sizeScale
        return (depth + 0.5) / bodyHeightPerScale
    }

    /**
     * Smooth toward the target scale, rate-capped by the configured grow/shrink %/tick. Fast on
     * purpose: short decisive morphs beat long grinding ones, because transitions are when growth
     * artifacts can show and a stable size is proven smooth.
     */
    private fun approachScale(current: Double, target: Double): Double {
        val lerped = current + (target - current) * 0.3
        val growCap = 1.0 + Config.GROW_PERCENT_PER_TICK.get() / 100.0
        val shrinkCap = 1.0 + Config.SHRINK_PERCENT_PER_TICK.get() / 100.0
        return lerped.coerceIn(current / shrinkCap, current * growCap)
    }

    private fun cleanup() {
        ecsEntity?.let {
            AppState.removeBody(it)
            SpiderAI.forget(it)   // drop the AI side-table entry with the body
        }
        ecsEntity = null
        body = null
    }

    override fun die(damageSource: DamageSource) {
        // Trophy drop: configurable chance of a single netherite ingot (it IS made of the stuff).
        // Spawn it on the FLOOR directly beneath the body centre, not at the mob position: the
        // giant form's body rides 10-25 blocks up, and an ingot dropped from the sky lands
        // somewhere the player will never spot under a collapsing kaiju. findFloorBelow scans
        // straight DOWN from the body (never the heightmap!), so cave and negative-Y kills drop
        // on the cave floor — not teleported to the surface above. Void below = drop at the body.
        val level = level()
        if (level is ServerLevel) rollTrophy(level)
        super.die(damageSource)
        cleanup()
    }

    // Set once the trophy roll has happened, so die() and remove(KILLED) can never both drop.
    private var trophyRolled = false

    /**
     * Roll the netherite trophy. Called from [die] (normal kills) AND from [remove] with
     * KILLED — some modded "kill anything" weapons (e.g. Avaritia-style endgame swords) slay by
     * ZEROING HEALTH directly, which skips hurt()/die() entirely: the death animation then goes
     * straight to remove(KILLED). Without this second hook the trophy silently never dropped
     * for such kills. Discards (peaceful despawn, only-one replacement) still never drop.
     */
    private fun rollTrophy(level: ServerLevel) {
        if (trophyRolled) return
        trophyRolled = true
        if (random.nextFloat() >= Config.NETHERITE_DROP_CHANCE.get()) return
        // Drop at the SIMULATION body position, not the invisible hitbox position. The hitbox is
        // synced to the body only during entity-ticking, one ECS update BEHIND body.position — for
        // a fast or giant spider (several blocks/tick) that lag dropped the trophy several blocks
        // behind the visible spider. body.position is exactly where the spider is drawn.
        val b = body
        val dropX = b?.position?.x ?: x
        val dropY = b?.position?.y ?: y
        val dropZ = b?.position?.z ?: z
        val floorY = SafeGroundFinder.findFloorBelow(level, dropX, dropY, dropZ) ?: dropY
        val trophy = ItemEntity(level, dropX, floorY + 0.25, dropZ, ItemStack(Items.NETHERITE_INGOT))
        trophy.setDefaultPickUpDelay()
        level.addFreshEntity(trophy)
    }

    override fun remove(reason: Entity.RemovalReason) {
        // Belt-and-suspenders trophy hook. A KILL always leaves the mob DEAD (health <= 0),
        // whatever weapon or mechanism did it — including modded "kill anything" swords (e.g.
        // Avaritia's) that zero health and bypass hurt()/die() entirely, so KILLED is not a
        // reliable signal. Rolling on `isDeadOrDying` catches every real death; rollTrophy's
        // trophyRolled guard means die() + this can never double-drop. Peaceful despawn,
        // chunk-unload, and the only-one replacement all remove the mob while it is still ALIVE
        // (health > 0), so they never drop — exactly as intended.
        val level = level()
        if (level is ServerLevel && (reason == Entity.RemovalReason.KILLED || health <= 0.0f)) rollTrophy(level)
        cleanup()
        super.remove(reason)
    }
}
