package com.heledron.spideranimation.entity

import com.heledron.spideranimation.AppState
import com.heledron.spideranimation.Config
import com.heledron.spideranimation.SpiderAnimationMod
import com.heledron.spideranimation.ecs.EcsEntity
import com.heledron.spideranimation.platform.Advancements
import com.heledron.spideranimation.platform.grantAdvancement
import com.heledron.spideranimation.platform.playSoundAt
import com.heledron.spideranimation.spider.DirectionBehaviour
import com.heledron.spideranimation.spider.SpiderBehaviour
import com.heledron.spideranimation.spider.SpiderBody
import com.heledron.spideranimation.spider.StayStillBehaviour
import com.heledron.spideranimation.spider.camoSpider
import com.heledron.spideranimation.spider.defaultSpider
import com.heledron.spideranimation.spider.hunterSpider
import com.heledron.spideranimation.spider.netheriteSpider
import com.heledron.spideranimation.spider.poisonSpider
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
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
/** Variants of the spider. NETHERITE is the armored classic; CAMO is the mossy chameleon;
 *  POISON is the warped-teal tarantula that lunges and injects Poison II; HUNTER is the
 *  pitch-black, player-sized, silent stalker that only moves while unwatched. */
enum class SpiderVariant(val key: String) {
    NETHERITE("netherite"), CAMO("camo"), POISON("poison"), HUNTER("hunter")
}

class SpiderMob(type: EntityType<out SpiderMob>, level: Level) : Monster(type, level) {
    private var body: SpiderBody? = null
    private var ecsEntity: EcsEntity? = null
    private var attackCooldown = 0
    private var blindnessCooldown = 0
    private var currentScale = 1.0

    /** Set by the spawn manager BEFORE the first tick (the body is built lazily on tick 1). */
    var variant = SpiderVariant.NETHERITE

    /** Tamed = docile: never attacks, but keeps every other behaviour (chase, growth, speed). */
    var tamed = false
        private set

    // ENRAGED boss mode (netherite only): fed a netherite ingot, it gains health/speed/damage,
    // a red boss bar, a name, a particle aura — and always drops a FULL netherite block.
    private var enraged = false
    private val bossEvent = ServerBossEvent(
        Component.literal("Enraged Netherite Spider"),
        BossEvent.BossBarColor.RED,
        BossEvent.BossBarOverlay.PROGRESS,
    ).apply { isVisible = false }

    init {
        noPhysics = true       // position is driven by the simulation, not vanilla physics
        isNoGravity = true
    }

    companion object {
        // Nearly every gameplay number lives in config/arachnomod-common.toml (see Config) and is
        // read LIVE each tick. Only the melee reach geometry stays hardcoded:
        const val ATTACK_REACH = 3.5
        const val REACH_SCALE_CAP = 2.0   // don't let the giant form melee from absurd distances
        // The poison variant starts its rear-up + lunge when the player is within this many
        // bite-reaches: far enough for the leap to read as a leap, close enough to connect.
        const val LUNGE_RANGE_FACTOR = 2.2
        // How often the hunter tops up its blindness while you stay in range. Well under the
        // effect's own duration, so it never lapses until you actually escape.
        const val BLINDNESS_REFRESH_TICKS = 40
        // How close counts as having MET the spider, and how often that check runs.
        const val ENCOUNTER_RANGE = 24.0
        const val ENCOUNTER_CHECK_TICKS = 20

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

    // The boss bar follows whoever can see the spider — the same tracker hooks the Wither uses.
    // The bar itself stays invisible until the spider is actually enraged.
    override fun startSeenByPlayer(player: ServerPlayer) {
        super.startSeenByPlayer(player)
        bossEvent.addPlayer(player)
    }

    override fun stopSeenByPlayer(player: ServerPlayer) {
        super.stopSeenByPlayer(player)
        bossEvent.removePlayer(player)
    }

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
        if (spawnType == MobSpawnType.SPAWN_EGG) {
            if (random.nextDouble() < Config.HUNTER_VARIANT_CHANCE.get()) variant = SpiderVariant.HUNTER
            else if (random.nextDouble() < Config.POISON_VARIANT_CHANCE.get()) variant = SpiderVariant.POISON
            else if (random.nextDouble() < Config.CAMO_VARIANT_CHANCE.get()) variant = SpiderVariant.CAMO
        }
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, spawnTag)
    }

    // Seat the rider on top of the body (entity pos = body centre; body model is ~0.5 blocks tall
    // per scale unit). 1.20.1 API: getPassengersRidingOffset (renamed getPassengerAttachmentPoint in 1.21).
    override fun getPassengersRidingOffset(): Double = 0.25 * currentScale + 0.2

    /** Spider Tamer item -> tame (docile). Tamed + empty hand -> ride.
     *  Netherite ingot on the (untamed) NETHERITE variant -> ENRAGE: feed it the metal it is
     *  made of and it becomes a boss. Risk it for the full-block payout. */
    public override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        val level = level()

        if (stack.item === Items.NETHERITE_INGOT && variant == SpiderVariant.NETHERITE && !tamed) {
            if (level is ServerLevel && !enraged) {
                enrage(level)
                if (!player.abilities.instabuild) stack.shrink(1)
                (player as? ServerPlayer)?.grantAdvancement(Advancements.ENRAGE)
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

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

    /** Flip into boss mode: name + red bar, health topped up by the enraged bonus (damage
     *  already taken is NOT forgiven), and an ignition worth the price of the ingot. */
    private fun enrage(level: ServerLevel) {
        enraged = true
        body?.enraged = true
        // NO CUSTOM NAME AT ALL. Setting one and merely hiding it (isCustomNameVisible = false)
        // was not enough: the name still surfaced as a label on screen, because anything holding a
        // custom name is fair game for vanilla's name rendering AND for HUD mods like Jade / WTHIT
        // / TheOneProbe, which happily draw it whatever the visibility flag says. The only way to
        // be rid of it on every version and every setup is to never set one.
        //
        // Nothing is lost: the boss bar carries its own literal name (see bossEvent above), and
        // death messages fall back to the entity type name, "Netherite Octoarachnopod".
        val attr = getAttribute(Attributes.MAX_HEALTH)
        val oldMax = attr?.baseValue ?: maxHealth.toDouble()
        val newMax = Config.ENRAGED_MAX_HEALTH.get().coerceAtLeast(oldMax)
        attr?.baseValue = newMax
        health = (health + (newMax - oldMax).toFloat()).coerceAtMost(maxHealth)
        bossEvent.isVisible = true

        val p = body?.position
        val px = p?.x ?: x
        val py = p?.y ?: y
        val pz = p?.z ?: z
        level.playSoundAt(Vector3d(px, py, pz), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 1.4f)
        level.sendParticles(DustParticleOptions.REDSTONE, px, py, pz, 60,
            1.2 * currentScale, 0.8 * currentScale, 1.2 * currentScale, 0.1)
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 40,
            1.2 * currentScale, 0.8 * currentScale, 1.2 * currentScale, 0.3)
    }

    private fun ensureBody(level: ServerLevel): SpiderBody {
        body?.let { return it }

        val options = when (variant) {
            SpiderVariant.CAMO -> camoSpider()
            SpiderVariant.POISON -> poisonSpider()
            SpiderVariant.HUNTER -> hunterSpider()
            else -> netheriteSpider()
        }
        val bodyHeight = options.walkGait.stationary.bodyHeight
        val spawn = Vector3d(x, y + bodyHeight, z)
        val yaw = Math.round(yRot / 45f) * 45f

        val (entity, newBody) = AppState.spawnBody(level, spawn, yaw, options)
        newBody.variantKey = variant.key   // routes step/land sounds (netherite = classic clank)
        newBody.enraged = enraged          // survives body rebuilds (SpiderAI reads it for speed)
        ecsEntity = entity
        body = newBody
        SpiderSpawnManager.notifyAlive(this)   // register as THE spider (enforces only-one)

        // Apply the configured max health per-instance (attributes register before configs load;
        // health is per-VARIANT: the armored netherite runs leaner than the bare camo).
        // Safe to top up here: this runs once per mob, and spiders are never save/reloaded.
        getAttribute(Attributes.MAX_HEALTH)?.baseValue = when (variant) {
            SpiderVariant.NETHERITE -> Config.NETHERITE_MAX_HEALTH.get()
            SpiderVariant.CAMO -> Config.CAMO_MAX_HEALTH.get()
            SpiderVariant.POISON -> Config.POISON_MAX_HEALTH.get()
            SpiderVariant.HUNTER -> Config.HUNTER_MAX_HEALTH.get()
        }
        health = maxHealth

        // The NETHERITE variant wears what it's made of: the exact stats of a FULL suit of
        // netherite armor — 20 armor (3+8+6+3), 12 toughness (3x4), 0.4 knockback resistance
        // (0.1x4). The camo variant is bare moss: health alone. (Knockback resistance is moot
        // while the simulation pins the position, but it keeps the suit honest.)
        if (variant == SpiderVariant.NETHERITE) {
            // All three are config-backed: set netheriteArmor (and toughness) to 0 for a spider
            // with NO armor at all, which fights as a bare health pool. Read at spawn, like the
            // max-health above - attributes are per-instance and the variants share an EntityType.
            getAttribute(Attributes.ARMOR)?.baseValue = Config.NETHERITE_ARMOR.get()
            getAttribute(Attributes.ARMOR_TOUGHNESS)?.baseValue = Config.NETHERITE_ARMOR_TOUGHNESS.get()
            getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.baseValue = Config.NETHERITE_KNOCKBACK_RESISTANCE.get()
        }

        return newBody
    }

    /**
     * Everyone within [ENCOUNTER_RANGE] has met this spider: the shared "Along Came a Spider",
     * plus the variant's own. NETHERITE has no separate one - it IS the root advancement.
     */
    private fun grantEncounterAdvancements(level: ServerLevel, body: SpiderBody) {
        val rangeSq = ENCOUNTER_RANGE * ENCOUNTER_RANGE
        for (player in level.players()) {
            if (player.distanceToSqr(body.position.x, body.position.y, body.position.z) > rangeSq) continue
            player.grantAdvancement(Advancements.ENCOUNTER)
            when (variant) {
                SpiderVariant.CAMO -> player.grantAdvancement(Advancements.ENCOUNTER_CAMO)
                SpiderVariant.POISON -> player.grantAdvancement(Advancements.ENCOUNTER_POISON)
                SpiderVariant.HUNTER -> player.grantAdvancement(Advancements.ENCOUNTER_HUNTER)
                SpiderVariant.NETHERITE -> {}
            }
        }
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

        // ENRAGED boss dressing: keep the bar honest, and wear the storm — red rage-dust laced
        // with blue-white sparks and soul flame (our BlockDisplay body can't wear the charged-
        // creeper shader, so it wears the weather instead). body.position per the effects rule.
        if (enraged) {
            bossEvent.progress = health / maxHealth
            if (tickCount % 2 == 0) {
                val p = body.position
                level.sendParticles(DustParticleOptions.REDSTONE, p.x, p.y, p.z, 3,
                    0.6 * currentScale, 0.4 * currentScale, 0.6 * currentScale, 0.02)
                if (tickCount % 6 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2,
                        0.7 * currentScale, 0.5 * currentScale, 0.7 * currentScale, 0.15)
                    val leg = body.legs.randomOrNull()
                    if (leg != null) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        leg.endEffector.x, leg.endEffector.y + 0.1, leg.endEffector.z,
                        1, 0.05, 0.05, 0.05, 0.01)
                }
            }
        }

        // Non-NETHERITE variants burn VISIBLY: the mob itself is an invisible hitbox and
        // BlockDisplays can't catch fire, so vanilla burning alone would be a silent, invisible
        // death. While burning, dress the body and the feet in flame + smoke so the player sees
        // the moss (or warped fungus) ablaze. (Positions come from the simulation, never the
        // entity — the hitbox lags a tick.)
        if (variant != SpiderVariant.NETHERITE && isOnFire && tickCount % 3 == 0) {
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

        // ADVANCEMENTS: meeting it. Everyone close enough to be in real danger has met it, not
        // just whoever it happens to be hunting. Cheap to repeat - award() is a no-op once held.
        if (tickCount % ENCOUNTER_CHECK_TICKS == 0) grantEncounterAdvancements(level, body)

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
        val waterScale =
            if (!squeezing && variant != SpiderVariant.HUNTER && Config.GROW_IN_WATER.get())
                waterGrowthScale(level, body)
            else null
        var targetScale = when {
            // THE HUNTER never changes size: fixed at hunterSize whatever the distance, water
            // depth, or opening ahead (it fits doorways at that size; holes are walls to it —
            // deep water is its one weakness, since it will not grow above the surface).
            variant == SpiderVariant.HUNTER -> Config.HUNTER_SIZE.get()
            squeezing -> Config.SQUEEZE_SIZE.get()
            // THE POISON variant holds ONE size too. Distance-based sizing made it swell and
            // shrink as the player moved around, and because body height scales with size that
            // showed up in-game as the spider BOBBING UP AND DOWN on the spot - worst near
            // water, where growInWater and the distance scale fight over maxOf(). A tarantula
            // is an ambusher, not a siege engine: it stays player-sized whatever the range.
            // Placed AFTER the squeeze so it can still pour itself down a hole, and the
            // situational caps below still apply, so it can still thread a doorway or a
            // crawl-hole - it simply has no distance-driven size of its own to oscillate.
            variant == SpiderVariant.POISON -> Config.POISON_SIZE.get()
            else -> maxOf(distanceToScale(horizontalDistance), waterScale ?: 0.0)
        }
        // Doorway/crawl-hole fit (chase pathfinding): cap the size so the body slips through
        // the opening on its path to the player. The squeeze still owns its own smaller size.
        if (!squeezing && variant != SpiderVariant.HUNTER) {
            ecsEntity?.let { SpiderAI.passageFitScale(it) }?.let { targetScale = targetScale.coerceAtMost(it) }
        }
        // HARD CEILING CAP: whatever the distance, the water or the variant wants, a body in a
        // tight space may never be taller than that space. This is the fix for "it inflates
        // inside the tunnel and shoots up through the roof": the shrink used to be requested by
        // the pathfinder, which stops asking the moment the line to the player is clear — which
        // inside a corridor is exactly when it's deepest in. The body's own headroom reading
        // doesn't care what the AI is doing.
        val perScale = body.walkGait.stationary.bodyHeight / body.sizeScale.coerceAtLeast(0.01)
        fun fitFor(room: Double) = ((room - 0.2) / (perScale + 0.3)).coerceAtLeast(0.12)
        body.confinedHeadroom?.let { targetScale = targetScale.coerceAtMost(fitFor(it)) }
        // ...and size down for the space the PLAYER is in, once close enough to be coming for
        // them. Standing at the mouth of a corridor, the straight line to the player reads as
        // walkable, so the pathfinder never engages its threading — the spider just walked up
        // at full size and climbed the hillside instead of coming in. Sizing to fit where the
        // prey is standing makes it able to follow them in regardless of what the AI decided.
        // THE LEAD DISTANCE MUST SCALE WITH SIZE. This used to be a flat 14 blocks, and that is
        // why a spider coming from range walked straight over the top of a building instead of
        // going in: the shrink is not instant, and a big spider is also a FAST spider, so by the
        // time it was allowed to start it was already standing on the roof. Measured on a real
        // sealed room: approaching at scale ~14 the shrink takes tens of ticks, during which the
        // body covers far more than 14 blocks.
        //
        // Both terms of that problem are proportional to the body's size (how much shrinking is
        // needed, and how fast it closes), so the warning distance is too. Small spiders keep
        // essentially the old behaviour; only the giants - the ones that actually climb - get the
        // early notice. Outdoors this changes nothing at all: roomAt returns null unless the prey
        // is genuinely in an enclosed, roofed space, so the towering approach is untouched.
        val fitLeadDistance = 14.0 + 6.0 * currentScale
        if (!squeezing && nearest != null && horizontalDistance < fitLeadDistance) {
            // roomAt, not confinementAt: a fact about where the prey stands, not about this
            // body. Gating it on the spider's own height would mean a giant never learns it has
            // to shrink to follow you in - which is exactly when it needs to.
            SafeGroundFinder.roomAt(level, nearest.x, nearest.y, nearest.z)?.let { room ->
                targetScale = targetScale.coerceAtMost(fitFor(room))
            }
        }
        currentScale = approachScale(currentScale, targetScale)
        body.setSizeScale(currentScale)

        // Melee: hit the nearest player if within (scaled) reach, on a cooldown. Tamed = docile.
        // Only bites while actually HUNTING (chase mode) — so a peacefully wandering spider (or
        // any spider during the day with hostileOnlyAtNight) never sucker-punches a bystander.
        // The POISON variant fights like a real tarantula instead of trading standing hits: in
        // range it REARS UP with its front legs raised (the telegraph), LUNGES, and only a bite
        // landed out of that lunge connects — for less damage, but with Poison II in it.
        if (attackCooldown > 0) attackCooldown--
        if (blindnessCooldown > 0) blindnessCooldown--
        val hunting = ecsEntity?.let { SpiderAI.isChasing(it) } ?: false

        // THE HUNTER'S DARK: while it is stalking, anyone inside hunterBlindnessRange goes
        // blind — and it keeps the blindness topped up for as long as they stay in range, so
        // the only cure is to actually put distance between you and it. A thing that only
        // moves when you aren't looking, in a world where you can't look. Blinding everyone in
        // range rather than just its current quarry matters on servers: standing next to the
        // victim is not a safe seat.
        val blindRange = Config.HUNTER_BLINDNESS_RANGE.get()
        if (variant == SpiderVariant.HUNTER && !tamed && hunting &&
            blindRange > 0.0 && blindnessCooldown <= 0) {
            val blindTicks = (Config.HUNTER_BLINDNESS_SECONDS.get() * 20.0).toInt().coerceAtLeast(1)
            var blinded = false
            for (player in level.players()) {
                if (!player.isAlive) continue
                val distSqr = player.distanceToSqr(body.position.x, body.position.y, body.position.z)
                if (distSqr > blindRange * blindRange) continue
                player.addEffect(MobEffectInstance(MobEffects.BLINDNESS, blindTicks, 0), this)
                blinded = true
            }
            if (blinded) blindnessCooldown = BLINDNESS_REFRESH_TICKS
        }
        if (!tamed && hunting && nearest != null && nearest.isAlive) {
            val reach = ATTACK_REACH * currentScale.coerceAtMost(REACH_SCALE_CAP)
            val distSqr = nearest.distanceToSqr(body.position.x, body.position.y, body.position.z)
            if (variant == SpiderVariant.POISON) {
                val lungeRange = reach * LUNGE_RANGE_FACTOR
                if (attackCooldown == 0 && !body.isLunging && distSqr <= lungeRange * lungeRange) {
                    body.beginLunge(Vector3d(nearest.x - body.position.x, 0.0, nearest.z - body.position.z))
                    attackCooldown = Config.ATTACK_COOLDOWN_TICKS.get()   // recovery starts at the leap
                }
                if (body.isLungeStriking && distSqr <= reach * reach) {
                    nearest.hurt(damageSources().mobAttack(this), (biteHearts() * 2.0).toFloat())
                    nearest.addEffect(MobEffectInstance(MobEffects.POISON,
                        (Config.POISON_EFFECT_SECONDS.get() * 20.0).toInt(), 1), this)
                    body.endLunge()   // bite landed — end the strike, so one lunge = one bite
                }
            } else if (attackCooldown == 0 && distSqr <= reach * reach) {
                nearest.hurt(damageSources().mobAttack(this), (biteHearts() * 2.0).toFloat())
                attackCooldown = Config.ATTACK_COOLDOWN_TICKS.get()
            }
        }
    }

    /**
     * Horse-like steering: the rider looks where they want to go and presses the movement keys.
     * BOTH axes are honoured — W/S drive forward and back along the look direction, A/D strafe
     * across it — so the mount moves omni-directionally while always facing wherever the rider is
     * looking. The impulses are synced to the server by the vanilla ride-input packet. We drive
     * the ECS behaviour directly and flag the body as manually controlled so the "chase the
     * nearest player" system leaves it alone (the nearest player is, after all, sitting on it).
     */
    private fun tickRidden(body: SpiderBody, rider: Player) {
        body.manualControl = true

        val riddenSize = Config.RIDDEN_SIZE.get()
        currentScale = approachScale(currentScale, riddenSize)
        body.setSizeScale(currentScale)
        body.setSpeedScale(scaleToSpeedFactor(riddenSize))

        val entity = ecsEntity ?: return

        // Signed impulses straight off the ride-input packet: zza > 0 is forward, xxa > 0 is
        // LEFT. That sign convention is vanilla's own - getInputVector maps xxa onto +X at yaw 0
        // (facing south), where east is the rider's left. Reading them as floats rather than
        // booleans also means a controller's analog stick works at partial deflection for free.
        val forward = rider.zza.toDouble()
        val strafe = rider.xxa.toDouble()

        if (forward * forward + strafe * strafe > 1.0e-8) {
            val look = rider.lookAngle
            val fwd = Vector3d(look.x, 0.0, look.z)
            if (fwd.lengthSquared() > 1.0e-6) {
                fwd.normalize()
                val left = Vector3d(fwd.z, 0.0, -fwd.x)
                val dir = Vector3d(fwd).mul(forward).add(left.mul(strafe))
                if (dir.lengthSquared() > 1.0e-6) {
                    dir.normalize()
                    // Face where the rider looks; travel where they asked.
                    entity.replaceComponent<SpiderBehaviour>(DirectionBehaviour(Vector3d(fwd), dir))
                    return
                }
            }
        }
        entity.replaceComponent<SpiderBehaviour>(StayStillBehaviour())
    }

    /**
     * Raw bite damage in hearts for this spider — each variant hits differently: the armoured
     * netherite hardest, the mossy camo a little less, the hunter softest of all (it hunts by
     * taking your sight, not by force), and the enraged boss hardest of anything.
     *
     * These are PRE-ARMOUR numbers on purpose. The bite is an ordinary mob attack, so vanilla
     * armour reduction already applies on top — a player in full diamond takes roughly a
     * quarter of the number below. Scaling these down for armour ourselves would reduce it
     * twice over and make an armoured player practically immune.
     */
    private fun biteHearts(): Double = when {
        enraged -> Config.ENRAGED_ATTACK_DAMAGE_HEARTS.get()
        variant == SpiderVariant.POISON -> Config.POISON_ATTACK_DAMAGE_HEARTS.get()
        variant == SpiderVariant.CAMO -> Config.CAMO_ATTACK_DAMAGE_HEARTS.get()
        variant == SpiderVariant.HUNTER -> Config.HUNTER_ATTACK_DAMAGE_HEARTS.get()
        else -> Config.NETHERITE_ATTACK_DAMAGE_HEARTS.get()
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

        // ADVANCEMENTS: the kill, and the boss kill. lastHurtByPlayer is vanilla's own kill
        // attribution (it holds the reference for 100 ticks), so this credits exactly who the
        // game would credit. It lives in rollTrophy deliberately: that is the one funnel proven
        // to fire on every real death and never on a despawn, chunk unload or dimension follow.
        (lastHurtByPlayer as? ServerPlayer)?.let { killer ->
            killer.grantAdvancement(Advancements.SLAY)
            if (enraged) killer.grantAdvancement(Advancements.SLAY_BOSS)
        }
        // Same funnel = same guarantees: this fires on every real death and never on a peaceful
        // despawn, chunk unload or dimension follow, which is exactly what permadeath needs.
        SpiderSpawnManager.notifyKilled(level.server)
        // The ENRAGED boss always pays out: a FULL netherite block where one ingot went in.
        val trophyItem = if (enraged) Items.NETHERITE_BLOCK else Items.NETHERITE_INGOT
        if (!enraged && random.nextFloat() >= Config.NETHERITE_DROP_CHANCE.get()) return
        // Drop at the SIMULATION body position, not the invisible hitbox position. The hitbox is
        // synced to the body only during entity-ticking, one ECS update BEHIND body.position — for
        // a fast or giant spider (several blocks/tick) that lag dropped the trophy several blocks
        // behind the visible spider. body.position is exactly where the spider is drawn.
        val b = body
        val dropX = b?.position?.x ?: x
        val dropY = b?.position?.y ?: y
        val dropZ = b?.position?.z ?: z
        val floorY = SafeGroundFinder.findFloorBelow(level, dropX, dropY, dropZ) ?: dropY
        val trophy = ItemEntity(level, dropX, floorY + 0.25, dropZ, ItemStack(trophyItem))
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
        bossEvent.removeAllPlayers()
        cleanup()
        super.remove(reason)
    }
}
