# ArachnoMod a.k.a. The Netherite Octoarachnopod

(_THAT_ Netherite Spider)

***

A boss-grade fight whichever face it wears: the **netherite juggernaut** packs 350 HP inside the stats of a full netherite armor suit; the mossy **camo stalker** runs 600 unarmored HP and hopes you never spot it; the warped **poison striker** carries 500 unarmored HP, and a venomous tarantula lunge; and the pitch-black **HUNTER** (400 HP, player-sized, dead silent) only moves when you're not looking. Kill it and it _might_ just drop the netherite it's made of.

***

## ⚠️🕷️ ARACHNOPHOBIA WARNING

**This mod is not kind to anyone with arachnophobia.** **Please read before installing.** The spider in this mod is not a blocky vanilla mob: its eight legs are animated by a **live inverse-kinematics simulation**, so it moves, scurries, and plants each foot with unsettlingly **lifelike, realistic spider motion** — the exact kind of movement arachnophobia responds to. It also:

*   **hunts you** — it spots you from far away, stares you down, and charges;
*   is **enormous** — up to ~16 blocks tall, towering over the trees as it closes in;
*   can be **camouflaged** (v1.1.1) — the CAMO variant blends into the terrain and sounds like ordinary footsteps, so it can get _very_ close before you notice it;
*   can **pounce** — the POISON variant rears up with its front legs raised and _lunges_ at you, exactly like a real tarantula strike;
*   can **stalk** — the pitch-black HUNTER variant follows you in total silence and only moves when you're not looking at it.

There is no "cute mode." If realistic spider movement is a problem for you or the people on your server, this may genuinely be an uncomfortable experience… consider `hostileOnlyAtNight = true`, lowering `maxSize`, or sitting this one out. You have been warned. 🕸️

***

Somewhere in your world, a spider is SPRINTING toward you at terrifying speed.

ArachnoMod adds a single, relentless, procedurally animated spider that stalks players across the world. Its eight legs are solved in real time with FABRIK inverse kinematics and drawn entirely with vanilla **BlockDisplay** entities. Every step is calculated, not pre-baked, so it scuttles believably over any terrain. **A giant, procedurally-animated spider that hunts you across the world.** One roams at a time, respawning every 40 minutes. It towers over the trees and charges in at incredible speed the farther away you are, then shrinks down as it closes for a **6-heart bite**. Its eight legs are driven by real **FABRIK inverse-kinematics**, drawn entirely with vanilla **BlockDisplay** entities — so it walks, scurries, and clambers like nothing else in Minecraft.

But this isn't a tech demo for movement physics.

It's a hunter.

***

ArachnoMod runs on **Fabric, Forge and NeoForge — on both 1.20.1 and 1.21.1**:

| Loader   |Minecraft |Requires                                         |
| -------- |--------- |------------------------------------------------ |
| <strong>Fabric</strong> |<strong>1.21.1</strong> |Fabric API <strong>+</strong> Fabric Language Kotlin |
| <strong>NeoForge</strong> |1.21.1    |Kotlin for Forge                                 |
| <strong>Forge</strong> |1.20.1    |Kotlin for Forge <em>(also loads on NeoForge 1.20.1)</em> |
| <strong>Fabric</strong> |<strong>1.20.1</strong> |Fabric API <strong>+</strong> Fabric Language Kotlin |

***

## 🕷️ Features

*   **One spider, always hunting.** Exactly one exists at a time. It wanders the world looking for the nearest player within its chase distance.
*   **Wander / Alert / Chase AI.** When no one's around, the spider calmly **patrols** its territory — and it **pre-scans every patrol route block-by-block** before walking it, planning around lava, water, chasms, and cliff edges instead of tumbling into them. The instant it spots you it **freezes and snaps to face you** — one heart-stopping beat — then **charges**. It keeps chasing until you truly escape (wider give-up radius than its detection radius, so no flickering at the boundary). Optional `hostileOnlyAtNight` mode makes it docile in daylight like a vanilla spider.
*   **The hunt has pacing.** The first spider arrives **about a minute into a session** — no long wait for the main event. **Slay it and you've earned two full Minecraft days (40 min) of real peace** — no respawn treadmill. And if you hide in Peaceful? It returns **one minute after peace ends**. Peace has a price. 😈 (All three timers configurable.)
*   **Idle life.** Disable wandering and the spider becomes a living statue instead of a frozen one: it **breathes** — a slow body-bob that scales with its size — and occasionally **grooms**, lifting its front legs to its mouth and cleaning them with a smooth sweeping rub while leaning down. Yes, the murder kaiju is, briefly, adorable.
*   **The CAMO variant.** A second spider that can naturally spawn (25% by default, still only ONE spider ever) with **ACTIVE CAMOUFLAGE**: every leg continuously repaints itself as **the actual block it's standing on** — grass legs in the meadow, sand legs on the beach, stone legs in the mountains, changing leg-by-leg as it walks. Its footsteps play **the real step sound of the block underfoot**, exactly like player footsteps — it crunches on gravel, thuds on dirt, and goes whisper-quiet on wool. You won't see it coming. You might not hear it either.
*   **The POISON variant.** ☠️ A third spider (20% of spawns by default) sheathed in the eerie blue-teal of **warped wart**, its footsteps squishing like the Nether fungus it's made of. It fights like a **real tarantula**: in striking range it **rears up, front legs raised** — that's your only warning — then **LUNGES**, hurling its whole body at you. The bite is gentler than its cousins' (3 hearts) but injects **Poison II for 30 seconds**. 500 HP and no armor… but with the venom ticking, the clock is yours to worry about.
*   **The HUNTER variant.** 🖤 A fourth spider (15% of spawns by default), **pitch black**, **player-sized and always exactly that size**, and **completely silent** — no footsteps, ever. **And it takes your eyes**: get within ~16 blocks of it while it's stalking and it **blinds you**, keeping the blindness topped up for as long as you stay near it — the only cure is to actually get away. A thing that only moves when you aren't looking, in a world where you can't look. It doesn't charge. It **stalks**: it closes in FAST (1.6× chase speed), but **only while nobody is looking at it** — the moment your view swings toward it, it **freezes dead-still**, mid-stride, legs in the air. Every glance away brings it closer. Within a few blocks it stops caring about your eyes. Hide in a hole and it doesn't dig or squeeze — it **camps outside, motionless, waiting**. 400 HP. Walks through doorways at its size. Check behind you.
*   **Safe spawning.** Spawns (and patrol routes) are validated for solid, dry ground — no more spiders in the void, over oceans, or inside SkyBlock gaps. Keeps the configured spawn distance whenever possible, preferring farther over closer; never pops up in your face.
*   **Procedural 8-leg animation.** No canned animations — every step is solved live with FABRIK IK and rendered through vanilla BlockDisplays. Buttery-smooth motion.
*   **Grows with distance.** Far away it's a **towering giant** (up to ~16 blocks tall); as it closes in it **shrinks down** to bite you. The feet stay planted at every size.
*   **Charges faster the farther it is** — up to **8× speed** — so distance is no safety.
*   **It follows you between dimensions.** 🌀 Portal away and a few seconds later it re-emerges near you — the Nether, the End, **or any modded dimension**: the follow keys on where the players are, not which portal was used. There is still only ever ONE spider.
*   **No hole is safe.** Dig in and it applies **constant pressure**, re-evaluating every tick — the instant a block breaks or a ramp opens, it pours through. And a 1×1×1 hidey-hole? It **SQUEEZES**: shrinks below its minimum size, slips down the shaft after you, bites, and regrows on the way out (`squeezeSize`).
*   **It goes around corners — and lets itself in.** 🚪 A wall it can't climb no longer stops (or confuses) it: the chase **steers around obstacles**, hugging walls to the corner and turning in after you. And when a building blocks it, it **hunts for the way in** — scanning for a **1×2 doorway** or even a **1×1 crawl-hole**, walking to it, and **shrinking just enough to slip through**. Closed doors and trapdoors don't count as walls: it comes through them anyway. Your house is not the safe side of the door. (`chasePathfinding`, on by default.)
*   **No lake is safe.** Deep water used to drown it while you watched from a boat. Now it **grows just tall enough to ride above the surface** — pond, river, or open ocean — and keeps coming. (`growInWater`, on by default; turn it off if you want water to stay a weakness.)
*   **Lava sorts the variants.** 🔥 The **netherite** spider is fireproof like the gear it's made of — it _wades through lava lakes_ unburnt and unbothered. The **camo and poison** variants burn — visibly, flames licking up their legs.
*   **Boss-grade fight.** The **netherite variant**: 350 HP wrapped in the **exact stats of a full netherite armor suit** (20 armor, 12 toughness) — it shrugs off most of every blow, and lands a **6-heart bite**. The **camo variant**: 600 HP, no armor, same bite — softer, but you have to find it first. The **poison variant**: 500 HP, and the venom does the talking. Bring friends.
*   **Netherite trophy drop.** A 50% chance to drop a netherite ingot on death (it _is_ made of the stuff) — the ingot lands **on the ground directly beneath the spider**, at any height or depth, so your prize is always where you'd look.
*   **Taming & riding.** A creative-only **Spider Tamer** makes it docile, then you can **ride it like a mount** — full omni-directional steering on all four movement keys, always facing where you look (see below).
*   **ENRAGE it — if you dare.** ⚡ Right-click the netherite spider with a **netherite ingot** — feed it the very metal it's made of — and it becomes the **Enraged Netherite Spider**: a true boss with a **red boss bar**, more health (700), 1.5× speed, a **10-heart bite**, and a crackling aura of red rage-dust and blue lightning sparks. Kill it and it **always drops a FULL netherite block** — nine ingots back for the one you fed it. Risk it for the biscuit.
*   **Peaceful-safe.** On Peaceful difficulty it despawns and natural spawns pause, like any monster.
*   **Optional PERMADEATH.** 🏆 Prefer it to be a boss you can actually *finish*? Set `permadeath = true` and killing it once ends the hunt in that world — permanently, saved to the world, surviving restarts. Off by default.
*   **Fully configurable & hot-reloading.** Every gameplay number lives in a commented config file you can edit live (see Configuration).
*   **Spawn egg included** — spawn your own Netherite Octoarachnopod from the creative menu.

***

## 🎮 Commands

All under `/spider`:

| Command                    |What it does                                                                                                  |Who                      |
| -------------------------- |------------------------------------------------------------------------------------------------------------- |------------------------ |
| <code>/spider newinstance [size]</code> |Spawns <strong>your personal follower spider</strong> that trails you around at the chosen size (0.3–20). A creative-mode toy. |Creative only            |
| <code>/spider size &lt;n&gt;</code> |Live-resizes <strong>your</strong> personal spider (0.3–20).                                                  |Anyone (own spider)      |
| <code>/spider release</code> |Dismisses your personal spider.                                                                               |Anyone                   |
| <code>/spider chasedistance &lt;blocks&gt;</code> |Sets how far (8–256) the <strong>wild</strong> spider spots and chases players. <strong>Saves into the config file.</strong> |Ops (permission level 2) |
| <code>/spider config &lt;key&gt; get</code> |Shows any config value in chat.                                                                               |Ops (permission level 2) |
| <code>/spider config &lt;key&gt; set &lt;value&gt;</code> |<strong>Live-edits ANY of the 58 config values in-game</strong> — typed, range-checked arguments with full tab-completion (sound keys tab-complete against every sound in the game). Applies to the active spider instantly and saves straight into the config file. |Ops (permission level 2) |

***

## ⚙️ Configuration

All tunables live in **`config/arachnomod-common.toml`**, created on first launch. It's **fully commented** and **hot-reloads** — edit the file and most changes apply to a running world within moments, no restart needed. (Spawn/gait values are read when a spider spawns, so let the current one respawn to apply those.) Every key is also live-editable in-game with `/spider config <key> set <value>`.

**Spawning**

| Key                      |Default |Meaning                                                                     |
| ------------------------ |------- |--------------------------------------------------------------------------- |
| <code>spawnMinMinutes</code> |1.0     |Min minutes before the FIRST spider of a session spawns                     |
| <code>spawnMaxMinutes</code> |1.0     |Max minutes before the FIRST spawn (random between the two)                 |
| <code>respawnAfterKillMinutes</code> |40.0    |Cooldown after the spider is KILLED (40 = 2 Minecraft days of earned peace) |
| <code>permadeath</code> |false   |BOSS MODE: kill it once and it NEVER returns in that world (saved to the world, survives restarts) |
| <code>peacefulExitSpawnMinutes</code> |1.0     |The spider returns this many minutes after Peaceful is switched off 😈      |
| <code>spawnDistanceMin</code> |30.0    |Closest a spider naturally spawns from a player (blocks)                    |
| <code>spawnDistanceMax</code> |34.0    |Farthest a spider naturally spawns from a player (blocks)                   |
| <code>spawnAngleAttempts</code> |24      |Directions tested per candidate distance when hunting safe spawn ground     |
| <code>spawnMaxVerticalSearch</code> |48      |How deep below the heightmap to look for solid ground (SkyBlock-friendly)   |
| <code>camoVariantChance</code> |0.25    |Chance a spawned spider is the CAMO variant (natural spawns AND spawn eggs) |
| <code>poisonVariantChance</code> |0.2     |Chance a spawned spider is the POISON variant (rolled before the camo chance) |
| <code>hunterVariantChance</code> |0.15    |Chance a spawned spider is the pitch-black HUNTER variant (rolled first)    |

**Chase & speed**

| Key                         |Default |Meaning                                                           |
| --------------------------- |------- |----------------------------------------------------------------- |
| <code>chaseDistance</code>  |64.0    |How far the spider spots &amp; chases players (blocks)            |
| <code>chaseExitDistanceMultiplier</code> |1.25    |Give-up radius = chaseDistance × this (prevents boundary flicker) |
| <code>alertReactionTicks</code> |10      |The freeze-and-stare beat before it charges (0 = instant charge)  |
| <code>hostileOnlyAtNight</code> |false   |Only hunts at night, like a vanilla spider                        |
| <code>chasePathfinding</code> |true    |Steer around unclimbable walls and shrink through doorways/crawl-holes while chasing (false = old straight-line charge) |
| <code>chaseSpeedBlocksPerSecond</code> |8.0     |Top chase speed at normal size                                    |
| <code>hunterSpeedMultiplier</code> |1.6     |The HUNTER's speed (× base chase speed) while it moves — it only moves when unwatched |
| <code>hunterBlindnessRange</code> |16.0    |How close the stalking HUNTER must be to blind you; it re-blinds while you stay in range (0 = off) |
| <code>hunterBlindnessSeconds</code> |30.0    |How long the HUNTER's blindness lasts once you are out of its range |
| <code>speedGrowthFactor</code> |8.0     |Speed multiplier at maximum size                                  |
| <code>legStepSpeed</code>   |1.1     |How fast the legs swing — the "scurry"                            |

**Wandering & idle**

| Key                      |Default |Meaning                                                                     |
| ------------------------ |------- |--------------------------------------------------------------------------- |
| <code>enableWandering</code> |true    |Calm patrol when no player is in range (false = stands still)               |
| <code>wanderSpeedFactor</code> |0.35    |Patrol speed as a fraction of chase speed                                   |
| <code>wanderRadius</code> |24.0    |How far it patrols from its anchor point                                    |
| <code>wanderMinIntervalSeconds</code> |3.0     |Shortest commitment to one patrol heading                                   |
| <code>wanderMaxIntervalSeconds</code> |9.0     |Longest commitment to one patrol heading                                    |
| <code>wanderPauseChance</code> |0.25    |Chance it pauses instead of picking a new heading                           |
| <code>groomingChance</code> |0.03    |Chance per second that the idle spider grooms (wandering disabled; 0 = off) |

**Size & growth**

| Key                  |Default |Meaning                                                                                                                 |
| -------------------- |------- |----------------------------------------------------------------------------------------------------------------------- |
| <code>minSize</code> |0.6     |Size right next to a player                                                                                             |
| <code>squeezeSize</code> |0.25    |The size it SQUEEZES down to over a player hiding in a hole — 0.25 just fits a 1×1×1                                    |
| <code>maxSize</code> |15.0    |Size when far away (15 towers over the trees)                                                                           |
| <code>sizeNearDistance</code> |4.0     |At/below this distance the spider is at minSize                                                                         |
| <code>sizeFarDistance</code> |32.0    |At/above this distance the spider is at maxSize                                                                         |
| <code>growPercentPerTick</code> |12.0    |Fastest it can grow (%/tick)                                                                                            |
| <code>shrinkPercentPerTick</code> |25.0    |Fastest it can shrink (%/tick)                                                                                          |
| <code>riddenSize</code> |2.0     |Stable size while a player is riding it                                                                                 |
| <code>hunterSize</code> |1.1     |The HUNTER's FIXED size — it never grows or shrinks (1.1 = a head taller than a player; fits doorways as-is)            |
| <code>growInWater</code> |true    |In water, grow just big enough to ride above the surface instead of drowning (false = deep water stays a drowning trap) |

**Combat & drops**

| Key                 |Default |Meaning                                   |
| ------------------- |------- |----------------------------------------- |
| <code>netheriteMaxHealth</code> |350.0   |Max health of the netherite variant (it also wears full netherite-suit armor stats) |
| <code>camoMaxHealth</code> |600.0   |Max health of the camo variant (no armor) |
| <code>poisonMaxHealth</code> |500.0   |Max health of the poison variant (no armor — its danger is the bite) |
| <code>hunterMaxHealth</code> |400.0   |Max health of the hunter variant (no armor — it was never supposed to be seen) |
| <code>netheriteAttackDamageHearts</code> |6.0     |The netherite variant's bite, in hearts <em>before</em> your armour |
| <code>camoAttackDamageHearts</code> |5.0     |The camo variant's bite, in hearts before armour |
| <code>hunterAttackDamageHearts</code> |4.0     |The hunter variant's bite, in hearts before armour |
| <code>poisonAttackDamageHearts</code> |3.0     |The poison variant's bite damage in hearts (every bite also injects Poison II) |
| <code>poisonEffectSeconds</code> |30.0    |How long the Poison II from the poison variant's bite lasts |
| <code>attackCooldownTicks</code> |20      |Ticks between melee hits (20 = 1/sec)     |
| <code>netheriteDropChance</code> |0.5     |Chance to drop a netherite ingot on death |
| <code>enragedMaxHealth</code> |700.0   |Max health of an ENRAGED netherite spider (right-click it with a netherite ingot; it keeps its armor suit) |
| <code>enragedSpeedMultiplier</code> |1.5     |Chase-speed multiplier while enraged |
| <code>enragedAttackDamageHearts</code> |10.0    |The enraged boss's bite damage in hearts (it always drops a FULL netherite block) |

**Variant sounds** _(the netherite spider always keeps its iconic clank; camo plays the real sound of the block underfoot)_

| Key               |Default         |Meaning                                                          |
| ----------------- |--------------- |---------------------------------------------------------------- |
| <code>variantStepSound</code> |<code>block.moss.step</code> |Fallback step sound for non-netherite variants                   |
| <code>variantStepVolume</code> |0.3             |Volume of variant steps (also scales camo's block-matched steps) |
| <code>variantLandSound</code> |<code>block.moss.fall</code> |Fallback landing sound for non-netherite variants                |
| <code>variantLandVolume</code> |1.0             |Volume of variant landings                                       |

***

## 🥚 Encountering it

You don't have to do anything. One spawns on its own and comes looking after a minute of loading into the world. But if you want to summon one yourself, grab the **Netherite Octoarachnopod** spawn egg from the **Spawn Eggs** creative tab.

It dies like any other mob. Beat it down, and it goes _poof_, dropping exactly 1 Netherite ingot (50% chance).

***

## 📦 Requirements

Neoforge:

*   **Minecraft 1.21.1**
*   **NeoForge 21.1.x**
*   **[Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge)** (REQUIRED. Install it alongside this mod - FOR NEOFORGE ONLY.)

Forge:

*   **Minecraft 1.20.1**
*   **[Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge)** (REQUIRED. Install it alongside this mod - FOR FORGE ONLY.)

Fabric:

*   **Minecraft 1.21.1**
*   **[Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)**
*   **[Fabric Language Kotlin](https://www.curseforge.com/minecraft/mc-mods/fabric-language-kotlin)** (REQUIRED. Install it alongside this mod - FOR FABRIC ONLY.)

## 📜 Full changelog

### v1.8.4 (latest) — now on SIX builds
- **RIDE IT IN ANY DIRECTION.** 🕷️ Steering a tamed spider used to be one key: hold W and it walked wherever you were looking. Now **all four movement keys work** — W and S drive forward and back along your line of sight, A and D strafe across it, and the spider keeps facing wherever you look while it moves. Sidestep along a ledge, back out of a tunnel, circle a target without ever turning away from it. On the 1.20.1 and 1.21.1 builds the impulses are read as analog values, so a **controller's stick works at partial deflection** too.
- **IT COMES IN THROUGH THE DOOR NOW, INSTEAD OF SITTING ON YOUR ROOF.** 🏠 If you sheltered inside a building, the spider would walk up at full size, step onto the roof — the only surface a body that big can stand on — and camp there directly above you, forever. Two things were wrong:
  - **The spider couldn't tell that a room was a room.** Its "how much space is where my prey is standing" check demanded walls immediately either side of you, which is true in a corridor and false in the middle of any ordinary room. So every building read as open ground and it never learned it had to shrink to get in.
  - **It was told to shrink far too late.** The warning distance was a flat 14 blocks, but a big spider is also a *fast* spider and covers far more than that while it's shrinking — it was already standing on your roof before it started. The distance now scales with how big it currently is.
  - Out in the open nothing changes: a ceiling alone isn't shelter, so standing under a tree still gets you the full towering horror.
- **NEW: MINECRAFT 26.1.2, on Fabric and NeoForge.** 🕸️ The spider makes the jump to the new version scheme — the same mod, same feature set, same config, rebuilt against 26.1.2. This was a real port, not a recompile: 26.1 is the first **unobfuscated** Minecraft, and eleven releases of API changes sit between it and 1.21.1. Notable for anyone curious: `ResourceLocation` became `Identifier`, entity registration and spawn eggs were overhauled, and command permissions stopped being numbers.
  - **Requires Java 25** — that's Minecraft's own requirement for 26.1, not the mod's.
  - **Quilt players are covered by the Fabric jar.** Quilt Loader supports 26.1.2 and runs Fabric mods; a separate Quilt build isn't needed (and isn't possible — Quilt's own standard libraries were discontinued in December 2025).
  - **No Forge build for 26.x.** MinecraftForge has no release for any 26 version, so there is nothing to build against. The 1.20.1 Forge build is unaffected and still supported.
  - **A brand-new spawn egg.** 🥚 26.1 retired the old tinted spawn-egg template that every mod used, so the Octoarachnopod's egg is now **hand-drawn pixel art** in the style of the modern per-mob eggs — a dark netherite shell with a red-eyed spider face. (The 1.20.1 and 1.21.1 builds keep their classic tinted egg, which still works fine there.)
  - **Everything the first 26.1.2 play session turned up is fixed.** These were caught and fixed *before* release, so they never reached anyone — listed for the curious: the spawn egg and Spider Tamer drew as missing-texture blocks (26.1 requires a new per-item definition file that didn't exist before), the advancement tab's background was a missing texture (backgrounds became sprite IDs), the two kill advancements — **Eight Legs Later** and **Rage Against the Machine** — could never be earned (the kill-credit field changed type, so the check silently never matched), and riding was completely dead — you could mount the spider but not move, because the server stopped reporting rider input the old way. All six builds were then re-verified against the same test suite and report identical results.
- **SKYBLOCK / SKYBASE SUPPORT.** 🏝️ On a floating-island world the spider often never spawned at all, and cranking the spawn settings couldn't save it. Two things were wrong, and both are fixed:
  - **`spawnAngleAttempts` was capped at 64.** A small island is only a thin slice of the circle around you, so most directions find nothing but void and you need far more samples. The ceiling is now **1024**.
  - **The search could never look CLOSER than `spawnDistanceMin`.** It only ever swept outward — and on a skybase everything outward is void, because the only land in the world is the island under your feet, *inside* that minimum. So no number of angles could ever help. Once every normal option is exhausted, the search now sweeps **inward** in 1-block steps down to the new **`spawnCloseFallbackDistance`** (default 6 blocks; set 0 to disable and keep the strict minimum). It still takes the farthest spot the island offers and only ends up close if that's genuinely all there is.
  - Verified in a real void world: a 13×13 island had **no spawnable ring at all** at or beyond the minimum distance even with 1024 angles — and now spawns reliably.
  - Thanks to **NetherySiloX** for reporting it and pushing for the cap to go. 🕷️

### v1.8.3 — all four builds
- **The Enraged boss's name label is properly gone now.** 🏷️ v1.8.2 hid it, but hiding wasn't enough — anything carrying a custom name is still fair game for vanilla's name rendering and for HUD mods like Jade, WTHIT and TheOneProbe, which draw it regardless. The boss simply doesn't get a custom name any more. **The big red boss bar is untouched** — it carries its own name and is now the only place the title appears. Death messages read "Netherite Octoarachnopod".

### v1.8.2 — all four builds
- **Fixed: the giant no longer strobes between huge and tiny.** 🔁 Backing away from it made it flicker wildly instead of growing to its full towering size. The tight-space sense that lets it fit through your doorways was reading ordinary scenery as a ceiling — a tree trunk under its feet counted as a zero-height roof, and a leaf canopy three blocks over its feet was "low ceiling" to a body ten blocks tall — so it shrank, the ground beneath it changed, the cap lifted, it regrew, forever. A ceiling now only counts if the spider is actually **under** it. It grows to full size and *stays* there. 🕷️
- **It comes to you instead of getting lost.** 🧭 If the living spider ends up more than **192 blocks** from every player while something is still keeping its chunk loaded (spawn chunks, a chunk loader, another player on a server), it now relocates to a fresh spot near someone rather than idling out of play. New `relocateDistanceBlocks` (0 disables). **This never counts as a kill** — no trophy, no 40-minute cooldown, and permadeath is not triggered. Note this was already handled for the ordinary case: walking far enough away unloads its chunk, and it has always respawned near you immediately when that happens.
- **The Enraged boss lost its floating nametag.** The red boss bar at the top of the screen says everything; a second label hanging in the air was just clutter. The name still appears in death messages.

### v1.8.1 — all four builds
- **Fixed: it gets through your doorways and down your corridors again.** 🚪 A spider that came at you from a distance arrived **large** — and a large spider was invisible to its own tight-space sense, so it never knew to shrink. It would park outside the door, or wedge itself in a corridor ceiling, and stay there. The cause was a ratchet: the check that measures the space around the body looked *downward from the body's middle*, and on a body too big for the passage that middle sits **above the ceiling** — so the check found the ceiling, mistook it for the floor, and reported "plenty of room". Having grown too big to notice the corridor, it could never be told to shrink back down. It now measures from **where its legs are standing**, which works at any size: it sizes down to fit a passage no matter how big it arrives, and holds that size for the whole length instead of swelling up into the roof halfway through. 🕷️
- Big spiders still stride over low walls and one-block ditches exactly as before — only genuinely enclosing spaces hold them.
- **NEW: SEVEN ADVANCEMENTS.** 🏆 The hunt now keeps score. They live in their own tab, and none of them are the kind you get by accident:
  - **Along Came a Spider** — *Meet the Netherite Octoarachnopod. It met you first, and as Coinmonke once put it, "ITS SO BIG!!!"*
  - **If It Bleeds, We Can Kill It** — *Find the Camo variant — or rather, notice that a piece of the scenery has been following you.*
  - **We Are Venom** — *Meet the warped tarantula. The Poison II is not a suggestion.*
  - **Don't Blink** — *Meet the Hunter. Don't turn your back, don't look away, and whatever you do, don't blink.*
  - **Eight Legs Later** — *Kill an Octoarachnopod. That buys you two Minecraft days of quiet. Spend them well.*
  - **I Have a Bad Feeling About This** — *Feed a netherite ingot to the spider that is made of netherite. What is the worst that could happen?*
  - **Rage Against the Machine** *(challenge)* — *Put down the Enraged one. It cost you an ingot and most of your health. It paid you back in blocks.*
- **CRITICAL, Fabric 1.20.1 only: the spider could not spawn at all.** 💥 On that build the very first thing a leg does — look down for the ground — threw an error that took the whole server tick loop with it, so a spider appearing meant a crash. The cause was a Minecraft 1.20.1 API that quietly refuses the "no entity" ray-cast the mod was asking for; the Forge build papers over it, which is why only Fabric 1.20.1 was affected. The ground ray-cast is now done directly against block collision shapes and needs no such context on any version. **If you tried the Fabric 1.20.1 build and it crashed, this was why.**
- **NEW: the netherite spider's LEG COUNT is configurable.** 🦿 `netheriteLegs`, default 8 — the classic octopod, unchanged. The original mod shipped 2/4/6/8-legged bodies, and that idea is back as a live setting: **anything from 1 to 16**, with the body proportions interpolated to match, so 12 or 16 reads as a centipede and 4 as a crab. Only the netherite variant — camo, poison and hunter always have their 8. Below 4 is deliberately experimental and behaves about how you'd expect: 3 limps, and **1 or 2 legs can't walk at all — the body just drags itself along the ground after you**, which is horrible in the best way. Odd numbers add a leg on the centre line.
- **NEW: the netherite spider's ARMOR is configurable.** 🛡️ `netheriteArmor` (20), `netheriteArmorToughness` (12) and `netheriteKnockbackResistance` (0.4) were previously hardcoded to a full netherite suit. **Set netheriteArmor and netheriteArmorToughness to 0 for a spider with no armor at all** — it then takes weapon damage straight off its 350 HP and dies far faster. Bite damage was already per-variant and is unchanged (`netheriteAttackDamageHearts` and friends). Both leg count and armor apply when a spider SPAWNS, so they take effect on the next one.
- **The POISON tarantula is now a fixed size.** 🕸️ It used to swell and shrink with your distance like the others, and since the body rides higher as it grows, that read in-game as the spider **bobbing up and down on the spot** whenever you backed away — especially near water. It now holds **one size, roughly player height** (new `poisonSize`, default 1.0), which suits it far better anyway: a tarantula is an ambusher, not a siege engine. It still sizes down when it needs to — threading a doorway, or pouring itself into a squeeze hole — it just no longer has a distance-driven size to oscillate.

### v1.8.0 — now on FOUR builds
- **NEW BUILD: Fabric 1.20.1.** 🧵 Fabric players on 1.20.1 finally get it — same spider, same simulation, the complete v1.8.0 feature set, verified booting on a real 1.20.1 server. That fills the last gap: **Fabric and Forge/NeoForge, on both 1.20.1 and 1.21.1.** Requires Fabric API + Fabric Language Kotlin, like the 1.21.1 Fabric build.

### v1.8.0 — all builds
- **Every variant now bites differently.** ⚔️ The one shared damage number is gone — each spider hits with its own weight: **Netherite 6 hearts**, **Camo 5**, **Hunter 4** (it hunts by taking your sight, not by force), **Poison 3** plus its venom, and the **Enraged boss 10**. New keys `netheriteAttackDamageHearts` / `camoAttackDamageHearts` / `hunterAttackDamageHearts` (a customized `attackDamageHearts` carries over automatically).
- **On armour:** those are *raw* numbers — **your armour has always reduced them**, because the bite is an ordinary mob attack running vanilla's armour maths. For reference, what actually reaches you: the netherite bite lands **6 hearts unarmoured → ~3.8 in iron → ~1.9 in diamond → ~1.8 in netherite**; the poison bite lands **3 → ~0.75 through full netherite**; even the Enraged boss drops from 10 to ~3.6. Full kit is genuinely worth wearing, and the numbers above were left un-nerfed *because* armour already does that work — pre-reducing them would have made armoured players nearly immune. 🛡️

### v1.7.0 — all three loaders
- **NEW: the HUNTER takes your eyes.** 🖤 The pitch-black stalker now **blinds you** when it gets close — within **16 blocks** by default (`hunterBlindnessRange`) — for **30 seconds** (`hunterBlindnessSeconds`). And it **keeps re-applying it** for as long as you stay in range: the blindness doesn't tick down while it's near you, so the only way to clear it is to genuinely get away from the thing. Everyone in range is blinded, not just its current quarry — standing next to the victim is not a safe seat. A creature that only moves when you aren't looking, in a world where you can't look. Set the range to 0 to turn it off. ☠️

### v1.6.4 — all three loaders
- **Fixed: legs no longer get stuck hanging in the air.** 🕷️ A leg that couldn't find footing — over a ledge, a cliff edge, a gap — was given a target in *mid-air*, and then deadlocked: it couldn't step (it had "arrived") and it couldn't land (there was nothing there), so it hung stranded until the spider wandered away. And because the gait makes neighbouring legs wait on an ungrounded one, a single stuck leg could freeze half the spider. Two fixes: arriving at thin air no longer counts as arriving, and **any leg left off the ground for more than a moment while the spider is standing hunts straight down and re-plants itself** — tucking in toward the body if it's dangling over a drop. Verified with a real spider perched on a cliff lip: previously two legs hung permanently, now none.
- **Falls settle almost instantly.** Combined with the reach fix, a spider dropped 20 blocks now has every foot planted within about a second of landing, and never draws the stretched-out upright legs at any point during the fall.

### v1.6.3 — all three loaders
- **It comes in even when you're standing right inside the entrance.** Corridors worked if you ran deep inside, but if you waited at the mouth the spider stayed out and climbed the hillside instead — from there the straight line to you looks perfectly walkable, so it never realised it had to squeeze in at all. It now checks whether **it actually fits where you are standing**, and if it doesn't, it hunts for the way in and sizes down to follow you — no matter how inviting the line to you looks. 🚪
- **Fixed properly: no more legs standing straight up after a fall.** The previous attempt only released a foot while the spider was still airborne, and only after a drop that scaled with its size — so a big spider landed with its feet still up on the ledge, legs stretched into spikes. The test is now simply **whether the leg can still reach its foot**: if it can't, it lets go immediately and the foot is pulled back within reach, so the spike never renders at all. Works at any size, mid-fall or landed. 🕷️

### v1.6.2 — all three loaders
- **Corridors: the missing half.** v1.6.1 stopped the spider drifting into tunnel walls, but it could still **inflate** once inside — the shrink was requested by the pathfinder, which stops asking the moment the straight line to you is clear (i.e. exactly when it's deepest in the tunnel), so it swelled back toward its distance-based size, decided it wanted to stand taller than the roof, and pushed itself up through it. Now the body caps its own size to the space it's standing in: **in a tight place it simply cannot be taller than the ceiling**, no matter what the distance sizing, the water rule or the variant wants. Out in the open, nothing is capped — it's still a kaiju. 🕳️
- **Fixed: legs no longer point skyward after a big fall.** Walking off a cliff left each foot standing up on the ledge, still flagged as planted, while the body plummeted away — stretching the legs into upward spikes that only sorted themselves out several seconds after landing. The legs now let go when the body outruns them, fall *with* it in a splayed pose, and plant together on impact. 🕷️

### v1.6.1 — all three loaders
- **Corridors: fixed at the root.** The previous attempts hung the anti-climb protection off the *pathfinder* — so the moment the straight line to you cleared (which, inside a tunnel, is exactly when the spider is deepest in it) the protection switched off, the body drifted into a wall, and up through the roof it went. That was the wrong place for it. The body now **checks its own surroundings every tick** — in a corridor it holds the floor and the centre line **whether it's chasing, wandering or standing still**, and lets go the instant it's back in the open. 1×N crawl tunnels and 2×N corridors of any length, in any AI state. 🕳️🕷️

### v1.6.0 — all three loaders
- **NEW: PERMADEATH — beat it once and it's over.** 🏆 Set **`permadeath = true`** and the spider **never comes back after it is killed in that world**. No 40-minute cooldown, no respawn: you fought the thing, you won, the hunt is done. The victory is written into the **world save**, so it survives quitting, relogging and server restarts — and it's per-world, so a new world is a fresh hunt. **Default is `false`** (it still returns after `respawnAfterKillMinutes`, as always), so nothing changes unless you want it to. Spawn eggs and `/spider newinstance` still work afterwards if you ever want one back. Only genuine kills count — a Peaceful despawn or a chunk unload never robs you of the fight. *(Requested by the community: "make it feel like a boss that needs to be killed so it stops showing up." Now it is one.)*

### v1.5.3 — all three loaders
- **Tunnels work now, not just doorways.** 🕳️ A one-block-thick doorway is one step through; a **corridor** means holding a one-block-wide line for its whole length, and the spider couldn't — it drifted a few centimetres into a wall and got launched up through the roof. Now it **measures the passage end to end**, walks in on the centre line, and is **held on that line until it comes out the far side** — through a 6-block tunnel, a mineshaft, a hallway, whatever you dug. The upward "teleport" is gone at the source: while inside anything tight, the collision step that was shoving it up a block per tick is disabled outright (that also quietly fixes the same lurch in 1×1 squeeze-holes). Long crawl tunnels work too — though the fixed-size hunter still won't fit a 1-high one, and has to find another way in. 🕷️

### v1.5.2 — all three loaders
- **Fixed for real this time: it comes through the door instead of climbing over your house.** v1.5.1 got the spider to *find* your door; it then rode up the wall anyway. Three causes, all fixed: its legs were grabbing the wall top and hauling the body up after them (the body is now **pinned to the doorway's own floor** while it goes through), it approached the gap at an angle so the body clipped the frame and got shoved a block upward every tick (it now **lines up square in front of the opening and walks straight through**), and the "fit" size was far too big — the leg spread was three blocks wide against a one-block door (**doorway fit 1.15 → 0.6, crawl-hole 0.45 → 0.3**). It now threads the gap with its legs splayed around the frame, exactly as it should. 🚪🕷️

### v1.5.1 — all three loaders
- **Fixed: doorways actually work now.** 🚪 v1.3.3's "it fits through doorways" only triggered if a doorway happened to sit exactly on the straight line between the spider and you — which basically never happens, so the spider just circled your house instead. Two fixes: **it now goes looking for the way in** (when a wall blocks it, it scans the building for real openings, picks the nearest one it can actually walk to, and commits to it), and **closed doors and trapdoors count as openings** — a shut door has collision, so the old check read your front door as solid wall. Netherite, camo and poison shrink to fit and let themselves in; the hunter is already door-sized and simply walks through. Alcoves, windows-to-nowhere and the open floor inside the room are all correctly ignored — it's specifically hunting *gaps in barriers*. Lock your door all you like. 🕷️

### v1.5.0 — "The Enrage Update" — all three loaders
- **NEW: feed it a netherite ingot. See what happens.** ⚡ Right-click the **netherite** spider with a **netherite ingot** and it consumes it — a thunderclap, a burst of red dust and blue sparks — and becomes the **ENRAGED NETHERITE SPIDER**: a named boss with a **red boss bar**, **700 HP** behind its full armor suit (`enragedMaxHealth`), **1.5× chase speed** (`enragedSpeedMultiplier`), and a **10-heart bite** (`enragedAttackDamageHearts`), wreathed in a permanent aura of red rage-dust laced with blue lightning sparks and soul flame. Slay it and it **always drops a FULL netherite block** — nine ingots back for the one you fed it. Netherite variant only; the others just stare at you. Risk it for the biscuit. 🕷️

### v1.4.0 — "The Stalker Update" — all three loaders
- **NEW: the HUNTER variant.** 🖤 A fourth spider — **pitch black**, **fixed at player size** (never grows, never shrinks — `hunterSize`), and **completely silent**: no footsteps, no landing thuds, nothing. It doesn't charge; it **stalks**. It closes in at **1.6× chase speed** (`hunterSpeedMultiplier`) — but **only while nobody is looking at it**. The moment your view swings toward it, it **freezes dead-still, mid-stride**. Every glance away brings it closer. Inside a few blocks, your eyes stop helping. Hide in a hole and it doesn't press or squeeze like the others — it **camps outside, motionless, and waits**. 400 HP (`hunterMaxHealth`), no armor, 15% of spawns (`hunterVariantChance`). It walks through your doorway at its own size. Check behind you. Then check again.

### v1.3.3 — all three loaders
- **Fixed: no more getting stuck in walls and "teleporting" up them.** The chase used to walk the straight line into wall faces, slide inside, and ride up out the top. Now it **plans its route**: walls and cliffs it can't climb are **steered around** — it hugs the wall to the corner and turns in after you, without flip-flopping. New config `chasePathfinding` (default **true**; set false for the old straight-line charge).
- **NEW: it fits through doorways.** 🚪 If the wall on its path has a ground-level opening — a **1×2 doorway** or even a **1×1 crawl-hole** — it doesn't go around: it **shrinks just enough to slip through** and regrows on the other side. Closing the door behind you buys exactly as much time as the door is tall.

### v1.3.2 — all three loaders
- **Spawn-timer changes now apply immediately.** ⏱️ Changing `spawnMinMinutes` / `spawnMaxMinutes` / `respawnAfterKillMinutes` / `peacefulExitSpawnMinutes` with `/spider config` used to leave the already-running countdown on its old value until you relogged — which made custom spawn timers *look* broken (they never were: values set before world load have always worked, verified). Now the live countdown **re-arms instantly from the new values, crediting the time already waited** — raise a half-elapsed cooldown and it extends by the difference; lower it below the time served and the spider comes promptly. 😈

### v1.3.1 — "The Venom Update" — all three loaders

*   **NEW: the POISON variant.** ☠️ A third spider — sheathed in the eerie blue-teal of **warped wart block**, footsteps squishing like the Nether fungus it's made of, 20% of spawns by default (`poisonVariantChance`; still only ever ONE spider). And it fights like a **real tarantula**: in striking range it **rears up with its front legs lifted high** — exactly the way a real tarantula telegraphs — then **LUNGES**, hurling itself at you, front legs still raised. Only a bite landed out of that lunge connects: **3 hearts** (`poisonAttackDamageHearts`) plus **Poison II for 30 seconds** (`poisonEffectSeconds`). 500 HP, no armor (`poisonMaxHealth`). Everything else — the netherite trophy, the scurry, distance sizing, the squeeze, water growth — works exactly like its cousins.

### v1.2.7 — all three loaders

*   **The great health rebalance: no more 1000 flat HP.** The two variants now fight very differently. The **Netherite variant drops to 350 HP but wears what it's made of — the exact stats of a FULL suit of netherite armor** (20 armor, 12 toughness, knockback resistance): it shrugs off most of every sword blow, but armor-piercing weapons finally have a real target. The **Camo variant keeps 600 HP with no armor** — much easier to put down… if you can find it. 🌿 Old `maxHealth` configs migrate automatically into the new per-variant `netheriteMaxHealth` / `camoMaxHealth` keys (a customized value carries over; defaults upgrade).

### v1.2.5 — all three loaders

*   **The spider no longer drowns while you laugh at it from a boat.** 🌊 Distance-based sizing kept it _small_ right when it waded in after you — so it sank to the lake floor and drowned while you floated above. Now, standing in water of any depth, it **grows just big enough for its body to ride above the surface** and keeps coming. Works in ponds, rivers, lakes and deep oceans (it will exceed `maxSize` if the ocean demands it). New config toggle **`growInWater`** (default **true**); set it to `false` if you _want_ deep water to stay a drowning trap.

### v1.2.4 — all three loaders

*   **Lava now treats the two variants very differently.** 🔥 The **Netherite** spider is forged of the stuff — it now takes **no fire damage of any kind** (lava, magma blocks, campfires) and never catches fire: luring it into a lava lake just means it keeps coming, wading through the melt. The **Camo** variant is living moss and undergrowth — it **burns, no matter what**, and you'll see it: the body and legs catch visible flame and smoke as it cooks.

### v1.2.3 — all three loaders

*   **THE SQUEEZE now actually descends into the hole.** In v1.2.2 the spider shrank correctly but hovered at the lip — its legs stood on the rim around your hole and held the body up there forever. Now, once it has shrunk small enough to fit, the body **sinks down the shaft after you** (pressing flat against the ground while it lines itself up, then pouring in over the lip) until the bite is on top of you. Also, the squeeze now only triggers when you're genuinely _below_ the spider — shrinking at the base of your pillar never helped it reach you anyway.

### v1.2.2 — all three loaders

*   **NEW: THE SQUEEZE.** 🕷️ Hiding in a 1×1×1 hole is no longer an escape. When the spider is pressing directly over a dug-in player, it now **shrinks below its normal minimum size — just small enough to fit into your hole** — slips in after you, and bites. It regrows the instant the squeeze is over. New config key `squeezeSize` (default 0.25; raise it toward 1.0 if you want hidey-holes to stay viable).

### v1.2.1 — all three loaders

*   **Digging in no longer makes the spider give up.** Hiding in a pit (or pillaring up) used to leave it standing at the rim, watching passively — even after you opened an escape path. Now, whenever you're vertically out of its reach, it applies **constant pressure toward your exact position, re-evaluating every tick** — the instant a block breaks or a ramp opens, it pours through the new opening. Dig wide and it climbs down in after you. Your first mistake is its opportunity. 🕷️

### v1.2.0 — "The Dimension Update" — all three loaders

*   **Fixed (gamebreaking): dimension travel no longer leaves piles of frozen spider "corpses" behind.** The spider's leg displays could get saved into chunks when you portalled away; returning showed dozens of frozen spiders stacked at one spot. Displays are now never saved — **and a janitor sweep automatically deletes the leftover frozen piles from worlds affected by older versions** as you revisit those areas. Your infested world heals itself.
*   **NEW: the spider follows you across dimensions.** Leave it behind in the overworld and a few seconds later it re-emerges near you in the Nether, the End — **or any modded dimension** (Twilight Forest, Dimensional Doors, anything): the follow triggers on where the players are, not on which portal was used. There is still only ever ONE spider.
*   **Fixed: spawning and wandering now work correctly under the Nether roof** (and any ceiling'd modded dimension) — the ground search works around the player's altitude instead of hitting the bedrock ceiling.

### v1.1.9 — "The Hunt Has Pacing" — all three loaders

_This release bundles v1.1.2–v1.1.4 (listed below) — if you're updating from v1.1.1, everything from here down to the v1.1.1 entry is new for you._

*   **The hunt begins one minute in.** The first spider of a session now spawns after ~1 minute (was 5–30) — new players meet the mod immediately. (`spawnMinMinutes`/`spawnMaxMinutes`, both default 1, raise them if you prefer slow-burn suspense.)
*   **Slaying the spider buys you real peace: the next one comes 40 minutes later — two full Minecraft days** (new `respawnAfterKillMinutes`, default 40). No respawn treadmill; killing it means something.
*   **Turning off Peaceful is punished promptly 😈** — the spider returns 1 minute after peace ends (`peacefulExitSpawnMinutes`). But toggling Peaceful can **not** shortcut the post-kill cooldown — a slain spider stays gone for its full two days.

### v1.1.4 — all three loaders

*   **Peace has a price: the spider now returns 1 minute after Peaceful difficulty is switched off** (was: a fresh 5–30 minute roll). Configurable via the new `peacefulExitSpawnMinutes` Command.
*   **More reliable spawning in rough terrain** _(thanks NetherySiloX for the report)_: the default `spawnAngleAttempts` is now **24** (was 12) — the spawner tests twice as many directions for safe ground, fixing "spider never shows up" in dense forests, snowy peaks, and cliffside terrain. **If you have an existing config file, raise `spawnAngleAttempts` to 24 yourself** (or delete the Command and let it regenerate) — saved configs keep their old value.

### v1.1.3 — all three loaders

*   **Improved wandering: route pre-scanning** _(contributed by NetherySiloX)_. Before committing to a patrol route, the spider now scans the ground along the entire path block-by-block and rejects routes that cross ravines, cliff gaps, or water — it plans its way around hazards instead of tumbling into them.
*   **New idle life: breathing.** When wandering is disabled (`enableWandering = false`), the standing spider gently breathes — a slow body bob (~4.5 s per breath) that scales with its size and fades out the moment it moves.
*   **New idle life: grooming** _(concept & animation by NetherySiloX)_. While idle (wandering disabled), the spider occasionally (3%/sec after standing still, `groomingChance`) lifts its front leg pair to its mouth and cleans them with a smooth sweeping motion while leaning its body down — 5 seconds of surprisingly endearing behavior from a 1000 HP murder machine.
*   New config Command: `groomingChance` (0–1 per second, 0 disables).

### v1.1.2 — all three loaders

*   **Improved: the netherite trophy now drops on the FLOOR directly beneath the spider — at any height, any depth.** Previously, killing the giant form (especially from range) dropped the ingot from 10–25 blocks up in the air, where it fell and landed somewhere easy to miss — making drops _feel_ broken. Now it lands right where the spider died: on the surface, on a cave floor, at negative Y — anywhere, anytime. (Drop chance is unchanged — `netheriteDropChance`, default 0.5, configurable up to guaranteed.)

### v1.1.1 — The Hunt Update — all three loaders

*   **NEW: Wander / Alert / Chase AI.** The spider now patrols calmly when alone, freezes and _stares at you_ for a beat when it first spots you, then charges — and keeps chasing until you genuinely escape (no more flickering at the detection edge). Optional `hostileOnlyAtNight` vanilla-spider mode.
*   **NEW: The CAMO variant with active camouflage.** A mossy second spider (25% of spawns by default — still only ever ONE spider) whose legs **continuously repaint as the actual blocks it walks on**, leg by leg, and whose footsteps play **the real sound of the block underfoot** exactly like player steps. Spawn eggs roll the variant chance too.
*   **NEW: Safe spawning.** Spawn positions (and patrol targets) are verified solid, dry, and clear — never in water, lava, the void, or mid-air. Works on SkyBlock/OneBlock-style maps. Prefers the configured distance band, farther over closer.
*   **NEW: `/spider config &lt;key&gt; get|set &lt;value&gt;`** — every one of the 35 config values live-editable in-game (OP-only), with typed, range-checked arguments and full tab-completion; sound keys tab-complete against every sound in the game. Changes apply to the active spider instantly and persist to the config file.
*   **16 new config keys** (spawn safety, AI tuning, wander behavior, variant sounds). Existing config files upgrade in place — your tuned values are untouched.
*   **Fixed:** on NeoForge/Forge, command-made config changes (including the old `/spider chasedistance`) were lost on restart; they now save to disk immediately.
*   Requires: Fabric API + Fabric Language Kotlin (Fabric) / Kotlin for Forge (NeoForge & Forge), as before.

## _📋 Update v1.0.0:_

*   **Added a Forge 1.20.1 port** — ArachnoMod now runs on **Forge 1.20.1** in addition to NeoForge 1.21.1. Same spider, same features, on both loaders. _(Requires Kotlin for Forge.)_
*   **Smoother animation** — fixed the jittery, stuttering leg movement. The spider now walks and climbs fluidly, with its legs and body staying fully in sync.

## _📋 Update v1.0.2:_

### Fabric 1.21.1 — new loader port

*   **Full native Fabric port for Minecraft 1.21.1.** Same spider, same simulation, same features and config as the NeoForge/Forge builds. Verified in-world.
    
*   Requires **Fabric API** and **Fabric Language Kotlin**. PLEASE DO NOT USE KOTLIN FOR FORGE WHEN USING FABRIC API!
    
*   Config system rebuilt on a bundled TOML backend so the commented, hot-reloading `arachnomod-common.toml` works identically on Fabric.
    
*   **Smooth-animation overhaul.** Reworked how the BlockDisplay legs are rendered so the whole spider moves fluidly at any size and speed — no jitter, no snapping, no black-rendering as it crosses terrain.
    
*   **Taming & riding added.** Creative-only **Spider Tamer** item: right-click to make the spider docile, then ride it with horse-like look-to-steer + W controls. (Our answer to "can I become the spider?" — see the disclaimer.)
    
*   **50% netherite ingot death drop.**
    
*   **Full configuration file** (`config/arachnomod-common.toml`) — every gameplay number, fully commented, hot-reloading.
    
*   **Command suite:** `/spider newinstance`, `/spider size`, `/spider release`, `/spider chasedistance`.
    
*   **Peaceful handling:** the spider despawns on Peaceful difficulty and natural spawns pause there.
    
*   Size scaling raised so the far-away giant truly towers over the trees (max size 15).
    

### v1.0.0

*   Initial release: the one-at-a-time hunting Netherite Octoarachnopod with procedural FABRIK-IK legs drawn via vanilla BlockDisplay entities, distance-based growth and charge speed, a 1000 HP boss fight with a 6-heart bite, natural respawning, and a spawn egg.

***

## 🐴 Why riding? (and why you can't morph into the spider)

The #1 request given is _"can I become the spider / turn into it with Identity or Morph?"_ — and the honest answer is **no, and it's not something a patch can add.** Instead, I give you the next best thing: **tame it and ride it.**

Grab the creative-only **Spider Tamer** item, right-click the spider to make it **docile** (it stops attacking but still chases and grows), then right-click it empty-handed to **climb on and ride it** — **look where you want to go and use all four movement keys**: W and S drive forward and back along your line of sight, A and D strafe across it, and the spider keeps facing wherever you're looking the whole time. Sneak to dismount. While ridden, it settles to a stable, mount-friendly size. You _pilot_ the spider instead of wearing it.

The **disclaimer** below explains exactly why morphing is impossible. It's worth a read if you were hoping for Identity/Morph support.

***

## ⚠️ Disclaimer — ArachnoMod is NOT compatible with Identity, Morph, or other transformation mods (and cannot be made so)

This question comes up a lot, so here's the full, honest explanation.

**Morph mods work by swapping a model.** When you "become" a mob with Identity, Morph, iDisguise, etc., the mod takes that mob's **rendered model** — a fixed rig of cubes and bones that Minecraft already knows how to draw — and renders _that_ in place of your player. It's copying an existing, static model.

**ArachnoMod's spider has no such model.** This is the whole trick behind why it moves the way it does:

*   The actual spider **entity is invisible** (it renders nothing).
*   What you _see_ is a **live, server-side physics and inverse-kinematics simulation** — its legs are solved every single tick with FABRIK and drawn using **dozens of independent vanilla BlockDisplay entities**.
*   There is **no single "spider model"** anywhere in the game for a morph mod to find, capture, or reproduce. The spider's appearance **only exists as the moment-to-moment output of a running simulation.**

So there is simply nothing for a morph mod to grab onto. It can copy a model; it cannot copy a _simulation that is being computed live from dozens of separate entities._

**Could we make it compatible?** Only by throwing away what makes ArachnoMod special — we'd have to rebuild the spider as an ordinary, pre-rigged animated model, which would kill the procedural, ground-adapting, size-shifting movement that is the entire point of the mod. That's not a fix; it's a different, worse mod.

**This is architectural and permanent — not a missing feature or an incompatibility we can eventually patch.** If you want to _be_ the spider, **tame it and ride it.** That's the intended answer, and it's why taming and riding exist.

***

## 🙏 Credits & license

Created by **iR3DN4X**. Based on **[TheCymaera/minecraft-spider](https://github.com/TheCymaera/minecraft-spider)** by **Heledron** (YouTube [@heledron](https://www.youtube.com/@heledron)).

This mod stands entirely on the shoulders of **Heledron (TheCymaera/Cymaera)**, whose original procedural-spider plugin is the heart of everything you see here. All of the inverse-kinematics leg animation, gait, and BlockDisplay rendering brilliance is theirs. I just built a hostile-mob experience on top of it.

Please go support the original creator:

*   🔧 **Original plugin (GitHub):** [https://github.com/TheCymaera/minecraft-spider](https://github.com/TheCymaera/minecraft-spider)
*   🎬 **Demo video:** [https://www.youtube.com/watch?v=Hc9x1e85L0w](https://www.youtube.com/watch?v=Hc9x1e85L0w)
*   📺 **Heledron's YouTube channel:** [https://www.youtube.com/@heledron](https://www.youtube.com/@heledron)

Custom license (per the original): free to use for commercial or non-commercial purposes; attribution appreciated but not required; **do not resell without substantial changes.** This is a free derivative with substantial changes.

### 📄 License

**Copyright © iR3DN4X.** ArachnoMod is **source-available freeware**: free to download and play, forever — but not open-source, and not free to re-use commercially. This license applies to **all versions and loaders** of the mod; the copy bundled with the latest release (`LICENSE.md` inside the jar) is the license in force.

ArachnoMod is a derivative of **[minecraft-spider](https://github.com/TheCymaera/minecraft-spider)** by **Heledron** (TheCymaera), used with attribution under its original terms; those portions remain his. All remaining content not belonging to Mojang/Microsoft or the above is the property of iR3DN4X.

**You MAY:**

*   **Play the mod** in any context, including on servers of any size.
*   Feature it in **videos, streams, and other media — monetized or not** (that is not "commercial use" of the mod's assets).
*   Include it in **modpacks** with acknowledgment, distributed per CurseForge's terms.
*   Create and publish **resource packs and language packs** for it.
*   Create **add-on mods** that depend on, extend, or interact with this mod. Prior contact is appreciated, not required — I'd love to see what you make: **Private Message me on CurseForge:** @ iR3DN4X
*   Create **non-commercial derivative works** with credit — **except ports** (below).

**You may NOT:**

*   Re-use this mod's assets or code **commercially** anywhere else.
*   **Port or re-release this mod** for other Minecraft versions, loaders, platforms, games, or media — official ports are made by me and planned ahead. If you want one, ask.
*   **Rehost or redistribute the mod files** outside CurseForge. If you downloaded this jar anywhere else, it may be modified or malicious — the only official source is this CurseForge page.

For anything not covered here, ask: **@iR3DN4X**.

Ported to NeoForge and turned into a hunting mob by **iR3DN4X**.

### 🎆 Supporters 🎆

*   **[NetherySiloX](https://www.curseforge.com/members/netherysilox/projects)** — community contributor behind much of the v1.1.1 "Hunt Update": designed and prototyped the **safe spawning system** (no more void/water/mid-air spawns, SkyBlock-friendly ground scanning), the **wander → alert → chase AI** (the calm patrols, the freeze-and-stare moment, and the chase hysteresis that stopped the gait glitching at the detection edge), and the **server-side command/config system** that became `/spider config` — live in-game editing of every setting, including the every-sound-in-the-game step-sound picker. Thank you! 🕷️
*   **ggfelle** aka @ vayne on Discord for helping with bug tests, and suggesting to bug fix the Nether and Overworld, as well as letting the spider fit through 1x1 block holes. Thank you! 🕷️

If anyone finds any bugs/glitches/suggestions, please report them back to me in the comments, and thank you for downloading my mod!