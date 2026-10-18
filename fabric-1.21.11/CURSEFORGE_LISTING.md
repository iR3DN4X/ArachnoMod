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

ArachnoMod runs on **Fabric, Forge and NeoForge — across 26.1.2, 1.21.11, 1.21.1 and 1.20.1** — and on **Bedrock Edition** as an add-on:

| Loader   |Minecraft |Requires                                         |
| -------- |--------- |------------------------------------------------ |
| <strong>Fabric</strong> |<strong>26.1.2</strong> |Fabric API <strong>+</strong> Fabric Language Kotlin <em>(Java 25; Quilt Loader runs this jar too)</em> |
| <strong>NeoForge</strong> |<strong>26.1.2</strong> |Kotlin for Forge <em>(Java 25; NeoForge 26.1.2.94+)</em> |
| <strong>Fabric</strong> |<strong>1.21.11</strong> |Fabric API <strong>+</strong> Fabric Language Kotlin 1.13.13+ <em>(new in v1.8.5; Quilt Loader runs this jar too)</em> |
| <strong>Fabric</strong> |<strong>1.21.1</strong> |Fabric API <strong>+</strong> Fabric Language Kotlin |
| <strong>NeoForge</strong> |1.21.1    |Kotlin for Forge                                 |
| <strong>Forge</strong> |1.20.1    |Kotlin for Forge <em>(also loads on NeoForge 1.20.1)</em> |
| <strong>Fabric</strong> |<strong>1.20.1</strong> |Fabric API <strong>+</strong> Fabric Language Kotlin |
| <strong>Bedrock</strong> |<strong>26.40+</strong> |Nothing — it's an add-on with its own page: <strong>[Netherite Octoarachnopod for Bedrock](https://www.curseforge.com/minecraft-bedrock/addons/netherite-octoarachnopod-the-netherite-spider)</strong> <em>(currently matches Java v1.2.7)</em> |

***

## 🕷️ Features

*   **One spider, always hunting.** Exactly one exists at a time. It wanders the world looking for the nearest player within its chase distance.
*   **Wander / Alert / Chase AI.** When no one's around, the spider calmly **patrols** its territory — and it **pre-scans every patrol route block-by-block** before walking it, planning around lava, water, chasms, and cliff edges instead of tumbling into them. The instant it spots you, it **freezes and snaps to face you** — one heart-stopping beat — then **charges**. It keeps chasing until you truly escape (wider give-up radius than its detection radius, so no flickering at the boundary). Optional `hostileOnlyAtNight` mode makes it docile in daylight like a vanilla spider.
*   **The hunt has pacing.** The first spider arrives **about a minute into a session** — no long wait for the main event. **Slay it, and you've earned two full Minecraft days (40 min) of real peace** — no respawn treadmill. And if you hide in Peaceful? It returns **one minute after peace ends**. Peace has a price. 😈 (All three timers configurable.)
*   **Idle life.** Disable wandering and the spider becomes a living statue instead of a frozen one: it **breathes** — a slow body-bob that scales with its size — and occasionally **grooms**, lifting its front legs to its mouth and cleaning them with a smooth sweeping rub while leaning down. Yes, the murder kaiju is, briefly, adorable.
*   **The CAMO variant.** A second spider that can naturally spawn (25% by default, still only ONE spider ever) with **ACTIVE CAMOUFLAGE**: every leg continuously repaints itself as **the actual block it's standing on** — grass legs in the meadow, sand legs on the beach, stone legs in the mountains, changing leg-by-leg as it walks. Its footsteps play **the real step sound of the block underfoot**, exactly like player footsteps — it crunches on gravel, thuds on dirt, and goes whisper-quiet on wool. You won't see it coming. You might not hear it either.
*   **The POISON variant.** ☠️ A third spider (20% of spawns by default) sheathed in the eerie blue-teal of **warped wart**, its footsteps squishing like the Nether fungus it's made of. It fights like a **real tarantula**: in striking range it **rears up, front legs raised** — that's your only warning — then **LUNGES**, hurling its whole body at you. The bite is gentler than its cousins' (3 hearts) but injects **Poison II for 30 seconds**. 500 HP and no armor… but with the venom ticking, the clock is yours to worry about.
*   **The HUNTER variant.** 🖤 A fourth spider (15% of spawns by default), **pitch black**, **player-sized and always exactly that size**, and **completely silent** — no footsteps, ever. **And it takes your eyes**: get within ~16 blocks of it while it's stalking and it **blinds you**, keeping the blindness topped up for as long as you stay near it — the only cure is to actually get away. A thing that only moves when you aren't looking, in a world where you can't look. It doesn't charge. It **stalks**: it closes in FAST (1.6× chase speed), but **only while nobody is looking at it** — the moment your view swings toward it, it **freezes dead-still**, mid-stride, legs in the air. Every glance away brings it closer. Within a few blocks it stops caring about your eyes. Hide in a hole, and it doesn't dig or squeeze — it **camps outside, motionless, waiting**. 400 HP. Walks through doorways at its size. Check behind you.
*   **Safe spawning.** Spawns (and patrol routes) are validated for solid, dry ground — no more spiders in the void, over oceans, or inside SkyBlock gaps. Keeps the configured spawn distance whenever possible, preferring farther over closer; never pops up in your face.
*   **Procedural 8-leg animation.** No canned animations — every step is solved live with FABRIK IK and rendered through vanilla BlockDisplays. Buttery-smooth motion.
*   **Grows with distance.** Far away it's a **towering giant** (up to ~16 blocks tall); as it closes in it **shrinks down** to bite you. The feet stay planted at every size. (Rather it stayed one size? `enableGrowth = false`.)
*   **Charges faster the farther it is** — up to **8× speed** — so distance is no safety.
*   **It follows you between dimensions.** 🌀 Portal away and a few seconds later it re-emerges near you — the Nether, the End, **or any modded dimension**: the follow keys on where the players are, not which portal was used. There is still only ever ONE spider. (Want it kept out of some? `spawnDimensions` / `blockedDimensions`.)
*   **No hole is safe.** Dig in, and it applies **constant pressure**, re-evaluating every tick — the instant a block breaks or a ramp opens, it pours through. And a 1×1×1 hidey-hole? It **SQUEEZES**: shrinks below its minimum size, slips down the shaft after you, bites, and regrows on the way out (`squeezeSize`).
*   **It goes around corners — and lets itself in.** 🚪 A wall it can't climb no longer stops (or confuses) it: the chase **steers around obstacles**, hugging walls to the corner and turning in after you. And when a building blocks it, it **hunts for the way in** — scanning for a **1×2 doorway** or even a **1×1 crawl-hole**, walking to it, and **shrinking just enough to slip through**. Closed doors and trapdoors don't count as walls: it comes through them anyway. Your house is not the safe side of the door. (`chasePathfinding`, on by default.)
*   **No lake is safe.** Deep water used to drown it while you watched from a boat. Now it **grows just tall enough to ride above the surface** — pond, river, or open ocean — and keeps coming. (`growInWater`, on by default; turn it off if you want water to stay a weakness.)
*   **Lava sorts the variants.** 🔥 The **netherite** spider is fireproof like the gear it's made of — it _wades through lava lakes_ unburnt and unbothered. The **camo and poison** variants burn — visibly, flames licking up their legs.
*   **Boss-grade fight.** The **netherite variant**: 350 HP wrapped in the **exact stats of a full netherite armor suit** (20 armor, 12 toughness) — it shrugs off most of every blow, and lands a **6-heart bite**. The **camo variant**: 600 HP, no armor, same bite — softer, but you have to find it first. The **poison variant**: 500 HP, and the venom does the talking. Bring friends.
*   **Netherite trophy drop.** A 50% chance to drop a netherite ingot on death (it _is_ made of the stuff) — the ingot lands **on the ground directly beneath the spider**, at any height or depth, so your prize is always where you'd look.
*   **Taming & riding.** A creative-only **Spider Tamer** makes it docile, then you can **ride it like a mount** — full omnidirectional steering on all four movement keys, always facing where you look.
*   **ENRAGE it — if you dare.** ⚡ Right-click the netherite spider with a **netherite ingot** — feed it the very metal it's made of — and it becomes the **Enraged Netherite Spider**: a true boss with a **red boss bar**, more health (700), 1.5× speed, a **10-heart bite**, and a crackling aura of red rage-dust and blue lightning sparks. Kill it and it **always drops a FULL netherite block** — nine ingots back for the one you fed it. Risk it for the biscuit.
*   **Peaceful-safe.** On Peaceful difficulty, it despawns, and natural spawns pause, like any monster.
*   **Optional PERMADEATH.** 🏆 Prefer it to be a boss you can actually _finish_? Set `permadeath = true` and killing it once ends the hunt in that world — permanently, saved to the world, surviving restarts. Off by default.
*   **Make ONE place safe — the Warding Idol.** 🛡️ Craft it (4 diamonds, 4 deepslate, 1 netherite ingot — the spider drops the ingot itself) and place it at your base: everything within **128 blocks** becomes a **safe zone**. Nothing spawns inside it, and the spider **cannot follow you in** — run for home mid-chase and it stops at the edge, backs off, and waits outside for you. Linger within one block of the edge and it can still reach you; any deeper and it can't see you at all. Idle AFK at home without being jumped. **Except from the enraged boss:** it walks straight in and blows apart any idol it gets within 16 blocks of.
*   **Play with it: the SPIDER LASER POINTER.** 🔴 A creative-only pointer — right-click and the spider bolts for the red dot like a cat. It will **break off a chase** to go for it, and while it is chasing the dot it is not hunting, so it **cannot bite you while distracted**. This is the mod's answer to the carrot-on-a-stick following from the original videos.
*   **See how it thinks.** 🔍 `/spider debug` switches on **Cymaera's debug view from the original videos**, rebuilt from his code: scan bars, trigger zones, colour-coded feet, step targets and the support polygon, live on the walking spider.
*   **Fully configurable & hot-reloading.** Every gameplay number lives in a commented config file you can edit live (see Configuration) — or **in-game, from the Spider Config Book**: one screen with every setting, what it does, its default and a reset button.
*   **Spawn egg included** — spawn your own Netherite Octoarachnopod from the creative menu. Everything the mod adds sits together in its own **ArachnoMod** creative tab.

***

## 🎮 Commands

All under `/spider`:

| Command                                      |What it does                                                                                                                                                                                                                                        |Who                      |
| -------------------------------------------- |--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |------------------------ |
| <code>/spider newinstance [size]</code>      |Spawns <strong>your personal follower spider</strong> that trails you around at the chosen size (0.3–20). A creative-mode toy.                                                                                                                      |Creative only            |
| <code>/spider size &amp;lt;n&amp;gt;</code>  |Live-resizes <strong>your</strong> personal spider (0.3–20).                                                                                                                                                                                        |Anyone (own spider)      |
| <code>/spider release</code>                 |Dismisses your personal spider.                                                                                                                                                                                                                     |Anyone                   |
| <code>/spider chasedistance &amp;lt;blocks&amp;gt;</code> |Sets how far (8–256) the <strong>wild</strong> spider spots and chases players. <strong>Saves into the config file.</strong>                                                                                                                        |Ops (permission level 2) |
| <code>/spider config &amp;lt;key&amp;gt; get</code> |Shows any config value in chat.                                                                                                                                                                                                                     |Ops (permission level 2) |
| <code>/spider config &amp;lt;key&amp;gt; set &amp;lt;value&amp;gt;</code> |<strong>Live-edits ANY of the 76 config values in-game</strong> — typed, range-checked arguments with full tab-completion (sound keys tab-complete against every sound in the game). Applies to the active spider instantly and saves straight into the config file. |Ops (permission level 2) |
| <code>/spider menu</code>                    |Opens the <strong>settings screen</strong> (the same one as the Spider Config Book): every setting with its description, default and a reset button. |Anyone (only ops can change things) |
| <code>/spider debug</code>                   |Toggles <strong>Cymaera's debug view from the original videos</strong>, rebuilt from his code: gold scan bars, cyan trigger zones, diamond/lapis/redstone feet, lime step targets and the green support polygon. Run it again to switch it off. |Ops (permission level 2) |

***

## ⚙️ Configuration

All tunables live in **`config/arachnomod-common.toml`**, created on first launch. It's **fully commented** and **hot-reloads** — edit the file, and most changes apply to a running world within moments, no restart needed. (Spawn/gait values are read when a spider spawns, so let the current one respawn to apply those.) Every key is also live-editable in-game, either from the **Spider Config Book** screen (`/spider menu` opens it too) or with `/spider config &lt;key&gt; set &lt;value&gt;`.

**Spawning**

| Key                        |Default |Meaning                                                                                                                                |
| -------------------------- |------- |-------------------------------------------------------------------------------------------------------------------------------------- |
| <code>spawnMinMinutes</code> |1.0     |Min minutes before the FIRST spider of a session spawns                                                                                |
| <code>spawnMaxMinutes</code> |1.0     |Max minutes before the FIRST spawn (random between the two)                                                                            |
| <code>respawnAfterKillMinutes</code> |40.0    |Cooldown after the spider is KILLED (40 = 2 Minecraft days of earned peace)                                                            |
| <code>permadeath</code>    |false   |BOSS MODE: kill it once, and it NEVER returns in that world (saved to the world, survives restarts)                                    |
| <code>peacefulExitSpawnMinutes</code> |1.0     |The spider returns this many minutes after Peaceful is switched off 😈                                                                 |
| <code>spawnDimensions</code> |<em>(empty)</em> |WHICH DIMENSIONS it may spawn in, comma-separated (e.g. <code>minecraft:overworld</code>). Empty = every dimension. Also stops it FOLLOWING you into one it may not spawn in |
| <code>blockedDimensions</code> |<em>(empty)</em> |Dimensions it may NEVER spawn in; beats <code>spawnDimensions</code>, so leaving that empty and setting this to <code>minecraft:the_end</code> means "everywhere except the End" |
| <code>wardEnabled</code> |true    |Are WARDING IDOLS active? A placed idol makes a SAFE ZONE: nothing spawns in it, the spider can't get more than 1 block past its edge, and anyone deeper inside can't be hunted, bitten or blinded. The enraged boss ignores all of it and blows idols apart. false disables them server-wide |
| <code>wardRadius</code> |128.0   |How far an idol's safe zone reaches, in blocks — a cylinder, so a base is covered bedrock to sky. Keep it well above <code>spawnDistanceMax</code> |
| <code>wardMinSpacing</code> |256.0   |Minimum distance between two idols. This, not the recipe, is what stops a group carpeting the map in safety (0 = no limit) |
| <code>wardArmingSeconds</code> |5.0     |Seconds a freshly placed idol takes to wake up; placing one says how long is left (0 = instant) |
| <code>laserPointerRange</code> |64.0    |How far the Spider Laser Pointer throws its dot, in blocks                                                             |
| <code>laserPointerLureSeconds</code> |4.0     |How long the spider stays fixed on the dot per click. While chasing the dot it is not hunting, so it will not bite      |
| <code>giveConfigBook</code> |true    |Hand operators (and a single-player world's owner) a Spider Config Book the first time they join |
| <code>spawnDistanceMin</code> |30.0    |Closest a spider naturally spawns from a player (blocks)                                                                               |
| <code>spawnDistanceMax</code> |34.0    |Farthest a spider naturally spawns from a player (blocks)                                                                              |
| <code>spawnAngleAttempts</code> |24      |Directions tested per candidate distance when hunting safe spawn ground                                                                |
| <code>spawnMaxVerticalSearch</code> |48      |How deep below the heightmap to look for solid ground (SkyBlock-friendly)                                                              |
| <code>spawnCloseFallbackDistance</code> |6.0     |SKYBLOCK LAST RESORT: if nothing at/beyond <code>spawnDistanceMin</code> is solid, sweep inward to this distance (0 = off, keep the strict minimum) |
| <code>relocateDistanceBlocks</code> |192.0   |If the spider ends up this far from every player while its chunk is still loaded, it relocates back near one (0 = off)                 |
| <code>camoVariantChance</code> |0.25    |Chance a spawned spider is the CAMO variant (natural spawns AND spawn eggs)                                                            |
| <code>poisonVariantChance</code> |0.2     |Chance a spawned spider is the POISON variant (rolled before the camo chance)                                                          |
| <code>hunterVariantChance</code> |0.15    |Chance a spawned spider is the pitch-black HUNTER variant (rolled first)                                                               |

**Chase & speed**

| Key                         |Default |Meaning                                                                                                                 |
| --------------------------- |------- |----------------------------------------------------------------------------------------------------------------------- |
| <code>chaseDistance</code>  |64.0    |How far the spider spots &amp; chases players (blocks)                                                                  |
| <code>chaseExitDistanceMultiplier</code> |1.25    |Give-up radius = chaseDistance × this (prevents boundary flicker)                                                       |
| <code>alertReactionTicks</code> |10      |The freeze-and-stare beat before it charges (0 = instant charge)                                                        |
| <code>hostileOnlyAtNight</code> |false   |Only hunts at night, like a vanilla spider                                                                              |
| <code>chasePathfinding</code> |true    |Steer around unclimbable walls and shrink through doorways/crawl-holes while chasing (false = old straight-line charge) |
| <code>chaseSpeedBlocksPerSecond</code> |8.0     |Top chase speed at normal size                                                                                          |
| <code>hunterSpeedMultiplier</code> |1.6     |The HUNTER's speed (× base chase speed) while it moves — it only moves when unwatched                                   |
| <code>hunterBlindnessRange</code> |16.0    |How close the stalking HUNTER must be to blind you; it re-blinds while you stay in range (0 = off)                      |
| <code>hunterBlindnessSeconds</code> |30.0    |How long the HUNTER's blindness lasts once you are out of its range                                                     |
| <code>speedGrowthFactor</code> |8.0     |Speed multiplier at maximum size                                                                                        |
| <code>legStepSpeed</code>   |1.1     |How fast the legs swing — the "scurry"                                                                                  |

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
| <code>poisonSize</code> |1.0     |The POISON variant's FIXED size — roughly player height. It never grows or shrinks with distance (0.3–5.0)              |
| <code>growInWater</code> |true    |In water, grow just big enough to ride above the surface instead of drowning (false = deep water stays a drowning trap) |
| <code>enableGrowth</code> |true    |false = EVERY variant holds ONE fixed size, <code>fixedSizeBlocks</code> tall: no growing with distance or in water, same size while ridden. Still shrinks to fit doorways, low ceilings and 1×1 holes |
| <code>fixedSizeBlocks</code> |2.0     |How tall every spider stands, in blocks (floor to the top of its legs), when <code>enableGrowth = false</code>                  |

**Combat & drops**

| Key                          |Default |Meaning                                                                                                            |
| ---------------------------- |------- |------------------------------------------------------------------------------------------------------------------ |
| <code>netheriteMaxHealth</code> |350.0   |Max health of the netherite variant (it also wears full netherite-suit armor stats)                                |
| <code>netheriteArmor</code>  |20.0    |The netherite variant's armor points (20 = a full netherite suit). <strong>Set to 0 for an unarmoured 350 HP target</strong> (0–30) |
| <code>netheriteArmorToughness</code> |12.0    |Its armor toughness (12 = a full netherite suit) — this is what blunts heavy hits (0–20)                           |
| <code>netheriteKnockbackResistance</code> |0.4     |Its knockback resistance, 0–1 (a netherite suit is 0.1 per piece)                                                  |
| <code>netheriteLegs</code>   |8       |How many legs the netherite spider has (1–16; 8 is the classic octopod). 1–2 drag and flail; 4+ stride properly    |
| <code>camoMaxHealth</code>   |600.0   |Max health of the camo variant (no armor)                                                                          |
| <code>poisonMaxHealth</code> |500.0   |Max health of the poison variant (no armor — its danger is the bite)                                               |
| <code>hunterMaxHealth</code> |400.0   |Max health of the hunter variant (no armor — it was never supposed to be seen)                                     |
| <code>netheriteAttackDamageHearts</code> |6.0     |The netherite variant's bite, in hearts <em>before</em> your armour                                                |
| <code>camoAttackDamageHearts</code> |5.0     |The camo variant's bite, in hearts before armour                                                                   |
| <code>hunterAttackDamageHearts</code> |4.0     |The hunter variant's bite, in hearts before armour                                                                 |
| <code>poisonAttackDamageHearts</code> |3.0     |The poison variant's bite damage in hearts (every bite also injects Poison II)                                     |
| <code>poisonEffectSeconds</code> |30.0    |How long the Poison II from the poison variant's bite lasts                                                        |
| <code>attackCooldownTicks</code> |60      |Ticks between melee hits, for <strong>every</strong> variant (60 = one bite every 3 seconds). Range 10–120 = half a second to six seconds |
| <code>netheriteDropChance</code> |0.5     |Chance to drop a netherite ingot on death                                                                          |
| <code>enragedMaxHealth</code> |700.0   |Max health of an ENRAGED netherite spider (right-click it with a netherite ingot; it keeps its armor suit)         |
| <code>enragedSpeedMultiplier</code> |1.5     |Chase-speed multiplier while enraged                                                                               |
| <code>enragedAttackDamageHearts</code> |10.0    |The enraged boss's bite damage in hearts (it always drops a FULL netherite block)                                  |

**Variant sounds** _(the netherite spider always keeps its iconic clank; camo plays the real sound of the block underfoot)_

| Key               |Default         |Meaning                                                          |
| ----------------- |--------------- |---------------------------------------------------------------- |
| <code>variantStepSound</code> |<code>block.moss.step</code> |Fallback step sound for non-netherite variants                   |
| <code>variantStepVolume</code> |0.3             |Volume of variant steps (also scales camo's block-matched steps) |
| <code>variantLandSound</code> |<code>block.moss.fall</code> |Fallback landing sound for non-netherite variants                |
| <code>variantLandVolume</code> |1.0             |Volume of variant landings                                       |

***

## 🥚 Encountering it

You don't have to do anything. One spawns on its own and comes looking after a minute of loading into the world. But if you want to summon one yourself, grab the **Netherite Octoarachnopod** spawn egg from the **ArachnoMod** (or **Spawn Eggs**) creative tab.

It dies like any other mob. Beat it down, and it goes _poof_, dropping exactly 1 Netherite ingot (50% chance).

***

## 📦 Requirements

Minecraft 26.1.2 (Fabric or NeoForge) — **needs Java 25**, which is Minecraft's own requirement for 26.1, not the mod's:

*   **Fabric:** [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) + [Fabric Language Kotlin](https://www.curseforge.com/minecraft/mc-mods/fabric-language-kotlin). **Quilt Loader runs this same jar** — no separate build needed.
*   **NeoForge:** 26.1.2.94 or newer, plus [Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge).
*   There is **no Forge build for 26.x** — Kotlin for Forge, which this mod needs, doesn't support Forge 26 yet. Use NeoForge.

Neoforge:

*   **Minecraft 1.21.1**
*   **NeoForge 21.1.x**
*   **[Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge)** (REQUIRED. Install it alongside this mod - FOR NEOFORGE ONLY.)

Forge:

*   **Minecraft 1.20.1**
*   **[Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge)** (REQUIRED. Install it alongside this mod - FOR FORGE ONLY.)

Fabric:

*   **Minecraft 1.21.11, 1.21.1 or 1.20.1** (on 1.21.11, Fabric Language Kotlin **1.13.13 or newer**)
*   **[Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)**
*   **[Fabric Language Kotlin](https://www.curseforge.com/minecraft/mc-mods/fabric-language-kotlin)** (REQUIRED. Install it alongside this mod - FOR FABRIC ONLY.)

Bedrock:

*   **Minecraft Bedrock 26.40 or newer** — get the `.mcaddon` from the **[Bedrock page](https://www.curseforge.com/minecraft-bedrock/addons/netherite-octoarachnopod-the-netherite-spider)**. Nothing else is needed.

## 📜 Full changelog

### v1.8.5 (latest) — all seven builds
- **NEW BUILD: FABRIC 1.21.11.** 🧵 You asked for it in the comments: the complete v1.8.5 now runs on **Minecraft 1.21.11** (Fabric, and Quilt through the same jar) — every variant, the Warding Idol, the laser pointer, the config book and all 76 settings. Needs Fabric API and Fabric Language Kotlin 1.13.13+.
- **THE BITE HAS A REAL COOLDOWN NOW.** 🕰️ Every variant used to be able to hit you **once per second**, which turned any moment you couldn't break away into a shredder. It's now **one bite every 3 seconds** — the same for the netherite juggernaut, the camo stalker, the poison tarantula's lunge, the hunter, and the enraged boss. You still lose the fight if you stand there, but you now get a real window to drink, block, or run. Tune it with `attackCooldownTicks` (default **60** ticks; anything from **10** = twice a second to **120** = once every six seconds).
  - **Existing config files update themselves.** If you never touched the old value it moves to the new default automatically; **if you customised it, your number is kept exactly as-is.**
- **KEEP IT OUT OF THE DIMENSIONS YOU DON'T WANT IT IN.** 🌍 Two new keys decide **where** the hunt happens. `spawnDimensions` is the list of the only dimensions it may spawn in — `spawnDimensions = "minecraft:overworld"` makes it an Overworld-only mod — and `blockedDimensions` is the opposite, banning it from the ones you name (`"minecraft:the_end"` = everywhere except the End). Both accept comma-separated lists, work with **modded** dimensions, and are case-insensitive, so a bare `overworld`, `nether` or `end` is understood. Both are **empty by default, so nothing changes unless you set them.** This also governs **following**: it will no longer chase you through a portal into a dimension it isn't allowed in. Spawn eggs and `/spider newinstance` stay unrestricted, so you can still summon one anywhere on purpose. _(Community request.)_
- **ONE SIZE, IF YOU WANT IT.** 📏 New `enableGrowth` (default `true`, so nothing changes). Set it to `false` and **every** spider — all four variants and the enraged boss — holds one fixed size, **`fixedSizeBlocks` tall (default 2 blocks, floor to the top of its legs)**: no towering at range, no growing in water, the same size while you ride it. It can still shrink *below* that to get through a doorway, under a low ceiling or into a 1×1 hole after you, because that is what keeps it out of your walls — it just never grows. Speed follows size, so a fixed-size spider loses its long-range sprint; raise `chaseSpeedBlocksPerSecond` if you miss it. _(Community request.)_
- **NEW BLOCK: THE WARDING IDOL — a safe zone you can run to.** 🛡️ The mod's first craftable block (4 diamonds, 4 deepslate and a netherite ingot, the metal the spider is made of): chiseled deepslate with red eyes watching from every face, glowing as bright as a redstone torch (light level 7). Place it and everything within **`wardRadius`** (default **128 blocks**, a cylinder from bedrock to sky) is off-limits. No spider spawns inside it, even when it is anchoring on someone else — and **the spider physically cannot come in**: its body stops one block past the edge, whatever it is doing. **Being chased? Run home.** Get more than a block inside and it loses you: the chase breaks off at the line, it backs out, and it waits outside to hunt whoever comes back out (or strays into that last block of the edge, where it can still bite). Anyone deeper inside can't be hunted, bitten or blinded. A spider caught inside — the idol woke up around it — runs straight away from the idol until it is out. **The enraged boss is the one exception:** no idol stops it — it walks right in, and blows apart any idol it gets within 16 blocks of (no crater, but the idol is gone). You chose that fight. A fresh idol takes **`wardArmingSeconds`** (default 5 seconds) to wake up, and two idols must be **`wardMinSpacing`** apart (default 256 blocks), which is what stops a group carpeting the map. Positions are saved in the world folder, so your base stays protected while its chunks are unloaded — an offline base is exactly when it matters. Switch the whole feature off with `wardEnabled = false`. _(Community request: "a protective pole… so our time at base is mostly peaceful, while exploring the rest of the world is scary.")_
- **THE SPIDER LASER POINTER, AND THE DEBUG VIEW FROM THE VIDEOS.** 🔴 Two things people kept asking after watching Cymaera's originals. The **Spider Laser Pointer** (creative only, no recipe — a black barrel with a red emitter) replaces the old carrot-on-a-stick following: right-click and the spider scurries to wherever the dot lands, up to `laserPointerRange` (64 blocks) away, for `laserPointerLureSeconds` (4) per click. It **outranks a chase**, so the pointer genuinely pulls it off a player — and because chasing the dot is not hunting, **it will not bite while distracted**. And **`/spider debug`** switches on **Cymaera's debug view from those videos**, rebuilt from his own code with the same parts in the same places: **gold bars** where each leg looks for ground, **cyan trigger zones** (red when a leg is stretched too far), feet in **diamond** (planted), **lapis** (touching) or **redstone** (in the air), **lime glass** step targets, and the **green outline** of the feet holding it up, with its centre of mass and normal force inside. Run it again to switch it off. _(It can't be bound to F3: F3 is client-side state, and everything this mod draws is server-side so that it works for vanilla clients.)_
- **ITS OWN CREATIVE TAB.** 🗂️ Everything the mod adds — the spawn egg, the Warding Idol, the Spider Tamer, the laser pointer and the config book — now sits together in an **ArachnoMod** tab, so it is easy to find in creative. They still appear in their usual vanilla tabs too.
- **IN-GAME SETTINGS: THE SPIDER CONFIG BOOK.** 📖 No more hunting through commands. Right-click the book for a screen with **every** setting, grouped by category with a search box: hover one for what it does, its default and its range; type or toggle a new value; ↺ puts that one back to its default, and **Reset ALL** puts everything back (it asks first). Edits go through the same checks as `/spider config` and save to the config file instantly. It works on servers too: operators (and the owner of a single-player world) can change things, everyone else gets a read-only view. Operators are handed one on their first join (`giveConfigBook`, default on); it is in the creative tab, and `/spider menu` opens the same screen. _(Community request: "a UI config... with a desc and a reset settings.")_

### v1.8.4 — now on SIX builds

*   **RIDE IT IN ANY DIRECTION.** 🕷️ Steering a tamed spider used to be one key: hold W and it walked wherever you were looking. Now **all four movement keys work** — W and S drive forward and back along your line of sight, A and D strafe across it, and the spider keeps facing wherever you look while it moves. Sidestep along a ledge, back out of a tunnel, circle a target without ever turning away from it. On the 1.20.1 and 1.21.1 builds the movement impulses are read at their full precision rather than as simple on/off, so partial input carries through at its actual strength.
*   **IT COMES IN THROUGH THE DOOR NOW, INSTEAD OF SITTING ON YOUR ROOF.** 🏠 If you sheltered inside a building, the spider would walk up at full size, step onto the roof — the only surface a body that big can stand on — and camp there directly above you, forever. Two things were wrong:
    *   **The spider couldn't tell that a room was a room.** Its "how much space is where my prey is standing" check demanded walls immediately either side of you, which is true in a corridor and false in the middle of any ordinary room. So every building read as open ground and it never learned it had to shrink to get in.
    *   **It was told to shrink far too late.** The warning distance was a flat 14 blocks, but a big spider is also a _fast_ spider and covers far more than that while it's shrinking — it was already standing on your roof before it started. The distance now scales with how big it currently is.
    *   Out in the open nothing changes: a ceiling alone isn't shelter, so standing under a tree still gets you the full towering horror.
*   **NEW: MINECRAFT 26.1.2, on Fabric and NeoForge.** 🕸️ The spider makes the jump to the new version scheme — the same mod, same feature set, same config, rebuilt against 26.1.2. This was a real port, not a recompile: 26.1 is the first **unobfuscated** Minecraft, and eleven releases of API changes sit between it and 1.21.1. Notable for anyone curious: `ResourceLocation` became `Identifier`, entity registration and spawn eggs were overhauled, and command permissions stopped being numbers.
    *   **Requires Java 25** — that's Minecraft's own requirement for 26.1, not the mod's.
    *   **Quilt players are covered by the Fabric jar.** Quilt Loader supports 26.1.2 and runs Fabric mods; a separate Quilt build isn't needed (and isn't possible — Quilt's own standard libraries were discontinued in December 2025).
    *   **No Forge build for 26.x.** MinecraftForge has no release for any 26 version, so there is nothing to build against. The 1.20.1 Forge build is unaffected and still supported.
    *   **A brand-new spawn egg.** 🥚 26.1 retired the old tinted spawn-egg template that every mod used, so the Octoarachnopod's egg is now **hand-drawn pixel art** in the style of the modern per-mob eggs — a dark netherite shell with a red-eyed spider face. (The 1.20.1 and 1.21.1 builds keep their classic tinted egg, which still works fine there.)
    *   **Everything the first 26.1.2 play session turned up is fixed.** These were caught and fixed _before_ release, so they never reached anyone — listed for the curious: the spawn egg and Spider Tamer drew as missing-texture blocks (26.1 requires a new per-item definition file that didn't exist before), the advancement tab's background was a missing texture (backgrounds became sprite IDs), the two kill advancements — **Eight Legs Later** and **Rage Against the Machine** — could never be earned (the kill-credit field changed type, so the check silently never matched), and riding was completely dead — you could mount the spider but not move, because the server stopped reporting rider input the old way. All six builds were then re-verified against the same test suite and report identical results.
*   **SKYBLOCK / SKYBASE SUPPORT.** 🏝️ On a floating-island world the spider often never spawned at all, and cranking the spawn settings couldn't save it. Two things were wrong, and both are fixed:
    *   **`spawnAngleAttempts` was capped at 64.** A small island is only a thin slice of the circle around you, so most directions find nothing but void and you need far more samples. The ceiling is now **1024**.
    *   **The search could never look CLOSER than `spawnDistanceMin`.** It only ever swept outward — and on a skybase everything outward is void, because the only land in the world is the island under your feet, _inside_ that minimum. So no number of angles could ever help. Once every normal option is exhausted, the search now sweeps **inward** in 1-block steps down to the new **`spawnCloseFallbackDistance`** (default 6 blocks; set 0 to disable and keep the strict minimum). It still takes the farthest spot the island offers and only ends up close if that's genuinely all there is.
    *   Verified in a real void world: a 13×13 island had **no spawnable ring at all** at or beyond the minimum distance even with 1024 angles — and now spawns reliably.
    *   Thanks to **NetherySiloX** for reporting it and pushing for the cap to go. 🕷️

### v1.8.3 — all four builds

*   **The Enraged boss's name label is properly gone now.** 🏷️ v1.8.2 hid it, but hiding wasn't enough — anything carrying a custom name is still fair game for vanilla's name rendering and for HUD mods like Jade, WTHIT and TheOneProbe, which draw it regardless. The boss simply doesn't get a custom name anymore. **The big red boss bar is untouched** — it carries its own name and is now the only place the title appears. Death messages read "Netherite Octoarachnopod".

### v1.8.2 — all four builds

*   **Fixed: the giant no longer strobes between huge and tiny.** 🔁 Backing away from it made it flicker wildly instead of growing to its full towering size. The tight-space sense that lets it fit through your doorways was reading ordinary scenery as a ceiling — a tree trunk under its feet counted as a zero-height roof, and a leaf canopy three blocks over its feet was "low ceiling" to a body ten blocks tall — so it shrank, the ground beneath it changed, the cap lifted, it regrew, forever. A ceiling now only counts if the spider is actually **under** it. It grows to full size and _stays_ there. 🕷️
*   **It comes to you instead of getting lost.** 🧭 If the living spider ends up more than **192 blocks** from every player while something is still keeping its chunk loaded (spawn chunks, a chunk loader, another player on a server), it now relocates to a fresh spot near someone rather than idling out of play. New `relocateDistanceBlocks` (0 disables). **This never counts as a kill** — no trophy, no 40-minute cooldown, and permadeath is not triggered. Note this was already handled for the ordinary case: walking far enough away unloads its chunk, and it has always respawned near you immediately when that happens.
*   **The Enraged boss lost its floating nametag.** The red boss bar at the top of the screen says everything; a second label hanging in the air was just clutter. The name still appears in death messages.

### v1.8.1 — all four builds

*   **Fixed: it gets through your doorways and down your corridors again.** 🚪 A spider that came at you from a distance arrived **large** — and a large spider was invisible to its own tight-space sense, so it never knew to shrink. It would park outside the door, or wedge itself in a corridor ceiling, and stay there. The cause was a ratchet: the check that measures the space around the body looked _downward from the body's middle_, and on a body too big for the passage that middle sits **above the ceiling** — so the check found the ceiling, mistook it for the floor, and reported "plenty of room". Having grown too big to notice the corridor, it could never be told to shrink back down. It now measures from **where its legs are standing**, which works at any size: it sizes down to fit a passage no matter how big it arrives, and holds that size for the whole length instead of swelling up into the roof halfway through. 🕷️
*   Big spiders still stride over low walls and one-block ditches exactly as before — only genuinely enclosing spaces hold them.
*   **NEW: SEVEN ADVANCEMENTS.** 🏆 The hunt now keeps score. They live in their own tab, and none of them are the kind you get by accident:
    *   **Along Came a Spider** — _Meet the Netherite Octoarachnopod. It met you first, and as Coinmonke once put it, "ITS SO BIG!!!"_
    *   **If It Bleeds, We Can Kill It** — _Find the Camo variant — or rather, notice that a piece of the scenery has been following you._
    *   **We Are Venom** — _Meet the warped tarantula. The Poison II is not a suggestion._
    *   **Don't Blink** — _Meet the Hunter. Don't turn your back, don't look away, and whatever you do, don't blink._
    *   **Eight Legs Later** — _Kill an Octoarachnopod. That buys you two Minecraft days of quiet. Spend them well._
    *   **I Have a Bad Feeling About This** — _Feed a netherite ingot to the spider that is made of netherite. What is the worst that could happen?_
    *   **Rage Against the Machine** _(challenge)_ — _Put down the Enraged one. It cost you an ingot and most of your health. It paid you back in blocks._
*   **CRITICAL, Fabric 1.20.1 only: the spider could not spawn at all.** 💥 On that build the very first thing a leg does — look down for the ground — threw an error that took the whole server tick loop with it, so a spider appearing meant a crash. The cause was a Minecraft 1.20.1 API that quietly refuses the "no entity" ray-cast the mod was asking for; the Forge build papers over it, which is why only Fabric 1.20.1 was affected. The ground ray-cast is now done directly against block collision shapes and needs no such context on any version. **If you tried the Fabric 1.20.1 build and it crashed, this was why.**
*   **NEW: the netherite spider's LEG COUNT is configurable.** 🦿 `netheriteLegs`, default 8 — the classic octopod, unchanged. The original mod shipped 2/4/6/8-legged bodies, and that idea is back as a live setting: **anything from 1 to 16**, with the body proportions interpolated to match, so 12 or 16 reads as a centipede and 4 as a crab. Only the netherite variant — camo, poison and hunter always have 8. Below 4 is deliberately experimental and behaves about how you'd expect: 3 limps, and **1 or 2 legs can't walk at all — the body just drags itself along the ground after you**, which is horrible in the best way. Odd numbers add a leg on the centre line.
*   **NEW: the netherite spider's ARMOR is configurable.** 🛡️ `netheriteArmor` (20), `netheriteArmorToughness` (12) and `netheriteKnockbackResistance` (0.4) were previously hardcoded to a full netherite suit. **Set netheriteArmor and netheriteArmorToughness to 0 for a spider with no armor at all** — it then takes weapon damage straight off its 350 HP and dies far faster. Bite damage was already per-variant and is unchanged (`netheriteAttackDamageHearts` and friends). Both leg count and armor apply when a spider SPAWNS, so they take effect on the next one.
*   **The POISON tarantula is now a fixed size.** 🕸️ It used to swell and shrink with your distance like the others, and since the body rides higher as it grows, that read in-game as the spider **bobbing up and down on the spot** whenever you backed away — especially near water. It now holds **one size, roughly player height** (new `poisonSize`, default 1.0), which suits it far better anyway: a tarantula is an ambusher, not a siege engine. It still sizes down when it needs to — threading a doorway, or pouring itself into a squeeze hole — it just no longer has a distance-driven size to oscillate.

### v1.8.0 — now on FOUR builds

*   **NEW BUILD: Fabric 1.20.1.** 🧵 Fabric players on 1.20.1 finally get it — same spider, same simulation, the complete v1.8.0 feature set, verified booting on a real 1.20.1 server. That fills the last gap: **Fabric and Forge/NeoForge, on both 1.20.1 and 1.21.1.** Requires Fabric API + Fabric Language Kotlin, like the 1.21.1 Fabric build.

### v1.8.0 — all builds

*   **Every variant now bites differently.** ⚔️ The one shared damage number is gone — each spider hits with its own weight: **Netherite 6 hearts**, **Camo 5**, **Hunter 4** (it hunts by taking your sight, not by force), **Poison 3** plus its venom, and the **Enraged boss 10**. New keys `netheriteAttackDamageHearts` / `camoAttackDamageHearts` / `hunterAttackDamageHearts` (a customized `attackDamageHearts` carries over automatically).
*   **On armour:** those are _raw_ numbers — **your armour has always reduced them**, because the bite is an ordinary mob attack running vanilla's armour maths. For reference, what actually reaches you: the netherite bite lands **6 hearts unarmoured → ~3.8 in iron → ~1.9 in diamond → ~1.8 in netherite**; the poison bite lands **3 → ~0.75 through full netherite**; even the Enraged boss drops from 10 to ~3.6. Full kit is genuinely worth wearing, and the numbers above were left un-nerfed _because_ armour already does that work — pre-reducing them would have made armoured players nearly immune. 🛡️

### v1.7.0 — all three loaders

*   **NEW: the HUNTER takes your eyes.** 🖤 The pitch-black stalker now **blinds you** when it gets close — within **16 blocks** by default (`hunterBlindnessRange`) — for **30 seconds** (`hunterBlindnessSeconds`). And it **keeps re-applying it** for as long as you stay in range: the blindness doesn't tick down while it's near you, so the only way to clear it is to genuinely get away from the thing. Everyone in range is blinded, not just its current quarry — standing next to the victim is not a safe seat. A creature that only moves when you aren't looking, in a world where you can't look. Set the range to 0 to turn it off. ☠️

### v1.6.4 — all three loaders

*   **Fixed: legs no longer get stuck hanging in the air.** 🕷️ A leg that couldn't find footing — over a ledge, a cliff edge, a gap — was given a target in _mid-air_, and then deadlocked: it couldn't step (it had "arrived") and it couldn't land (there was nothing there), so it hung stranded until the spider wandered away. And because the gait makes neighbouring legs wait on an ungrounded one, a single stuck leg could freeze half the spider. Two fixes: arriving at thin air no longer counts as arriving, and **any leg left off the ground for more than a moment while the spider is standing hunts straight down and re-plants itself** — tucking in toward the body if it's dangling over a drop. Verified with a real spider perched on a cliff lip: previously two legs hung permanently, now none.
*   **Falls settle almost instantly.** Combined with the reach fix, a spider dropped 20 blocks now has every foot planted within about a second of landing, and never draws the stretched-out upright legs at any point during the fall.

### v1.6.3 — all three loaders

*   **It comes in even when you're standing right inside the entrance.** Corridors worked if you ran deep inside, but if you waited at the mouth the spider stayed out and climbed the hillside instead — from there the straight line to you looks perfectly walkable, so it never realised it had to squeeze in at all. It now checks whether **it actually fits where you are standing**, and if it doesn't, it hunts for the way in and sizes down to follow you — no matter how inviting the line to you looks. 🚪
*   **Fixed properly: no more legs standing straight up after a fall.** The previous attempt only released a foot while the spider was still airborne, and only after a drop that scaled with its size — so a big spider landed with its feet still up on the ledge, legs stretched into spikes. The test is now simply **whether the leg can still reach its foot**: if it can't, it lets go immediately and the foot is pulled back within reach, so the spike never renders at all. Works at any size, mid-fall or landed. 🕷️

### v1.6.2 — all three loaders

*   **Corridors: the missing half.** v1.6.1 stopped the spider drifting into tunnel walls, but it could still **inflate** once inside — the shrink was requested by the pathfinder, which stops asking the moment the straight line to you is clear (i.e. exactly when it's deepest in the tunnel), so it swelled back toward its distance-based size, decided it wanted to stand taller than the roof, and pushed itself up through it. Now the body caps its own size to the space it's standing in: **in a tight place it simply cannot be taller than the ceiling**, no matter what the distance sizing, the water rule or the variant wants. Out in the open, nothing is capped — it's still a kaiju. 🕳️
*   **Fixed: legs no longer point skyward after a big fall.** Walking off a cliff left each foot standing up on the ledge, still flagged as planted, while the body plummeted away — stretching the legs into upward spikes that only sorted themselves out several seconds after landing. The legs now let go when the body outruns them, fall _with_ it in a splayed pose, and plant together on impact. 🕷️

### v1.6.1 — all three loaders

*   **Corridors: fixed at the root.** The previous attempts hung the anti-climb protection off the _pathfinder_ — so the moment the straight line to you cleared (which, inside a tunnel, is exactly when the spider is deepest in it) the protection switched off, the body drifted into a wall, and up through the roof it went. That was the wrong place for it. The body now **checks its own surroundings every tick** — in a corridor it holds the floor and the centre line **whether it's chasing, wandering or standing still**, and lets go the instant it's back in the open. 1×N crawl tunnels and 2×N corridors of any length, in any AI state. 🕳️🕷️

### v1.6.0 — all three loaders

*   **NEW: PERMADEATH — beat it once and it's over.** 🏆 Set **`permadeath = true`** and the spider **never comes back after it is killed in that world**. No 40-minute cooldown, no respawn: you fought the thing, you won, the hunt is done. The victory is written into the **world save**, so it survives quitting, relogging and server restarts — and it's per-world, so a new world is a fresh hunt. **Default is `false`** (it still returns after `respawnAfterKillMinutes`, as always), so nothing changes unless you want it to. Spawn eggs and `/spider newinstance` still work afterwards if you ever want one back. Only genuine kills count — a Peaceful despawn or a chunk unload never robs you of the fight. _(Requested by the community: "make it feel like a boss that needs to be killed so it stops showing up." Now it is one.)_

### v1.5.3 — all three loaders

*   **Tunnels work now, not just doorways.** 🕳️ A one-block-thick doorway is one step through; a **corridor** means holding a one-block-wide line for its whole length, and the spider couldn't — it drifted a few centimetres into a wall and got launched up through the roof. Now it **measures the passage end to end**, walks in on the centre line, and is **held on that line until it comes out the far side** — through a 6-block tunnel, a mineshaft, a hallway, whatever you dug. The upward "teleport" is gone at the source: while inside anything tight, the collision step that was shoving it up a block per tick is disabled outright (that also quietly fixes the same lurch in 1×1 squeeze-holes). Long crawl tunnels work too — though the fixed-size hunter still won't fit a 1-high one, and has to find another way in. 🕷️

### v1.5.2 — all three loaders

*   **Fixed for real this time: it comes through the door instead of climbing over your house.** v1.5.1 got the spider to _find_ your door; it then rode up the wall anyway. Three causes, all fixed: its legs were grabbing the wall top and hauling the body up after them (the body is now **pinned to the doorway's own floor** while it goes through), it approached the gap at an angle so the body clipped the frame and got shoved a block upward every tick (it now **lines up square in front of the opening and walks straight through**), and the "fit" size was far too big — the leg spread was three blocks wide against a one-block door (**doorway fit 1.15 → 0.6, crawl-hole 0.45 → 0.3**). It now threads the gap with its legs splayed around the frame, exactly as it should. 🚪🕷️

### v1.5.1 — all three loaders

*   **Fixed: doorways actually work now.** 🚪 v1.3.3's "it fits through doorways" only triggered if a doorway happened to sit exactly on the straight line between the spider and you — which basically never happens, so the spider just circled your house instead. Two fixes: **it now goes looking for the way in** (when a wall blocks it, it scans the building for real openings, picks the nearest one it can actually walk to, and commits to it), and **closed doors and trapdoors count as openings** — a shut door has collision, so the old check read your front door as solid wall. Netherite, camo and poison shrink to fit and let themselves in; the hunter is already door-sized and simply walks through. Alcoves, windows-to-nowhere and the open floor inside the room are all correctly ignored — it's specifically hunting _gaps in barriers_. Lock your door all you like. 🕷️

### v1.5.0 — "The Enrage Update" — all three loaders

*   **NEW: feed it a netherite ingot. See what happens.** ⚡ Right-click the **netherite** spider with a **netherite ingot** and it consumes it — a thunderclap, a burst of red dust and blue sparks — and becomes the **ENRAGED NETHERITE SPIDER**: a named boss with a **red boss bar**, **700 HP** behind its full armor suit (`enragedMaxHealth`), **1.5× chase speed** (`enragedSpeedMultiplier`), and a **10-heart bite** (`enragedAttackDamageHearts`), wreathed in a permanent aura of red rage-dust laced with blue lightning sparks and soul flame. Slay it and it **always drops a FULL netherite block** — nine ingots back for the one you fed it. Netherite variant only; the others just stare at you. Risk it for the biscuit. 🕷️

### v1.4.0 — "The Stalker Update" — all three loaders

*   **NEW: the HUNTER variant.** 🖤 A fourth spider — **pitch black**, **fixed at player size** (never grows, never shrinks — `hunterSize`), and **completely silent**: no footsteps, no landing thuds, nothing. It doesn't charge; it **stalks**. It closes in at **1.6× chase speed** (`hunterSpeedMultiplier`) — but **only while nobody is looking at it**. The moment your view swings toward it, it **freezes dead-still, mid-stride**. Every glance away brings it closer. Inside a few blocks, your eyes stop helping. Hide in a hole and it doesn't press or squeeze like the others — it **camps outside, motionless, and waits**. 400 HP (`hunterMaxHealth`), no armor, 15% of spawns (`hunterVariantChance`). It walks through your doorway at its own size. Check behind you. Then check again.

### v1.3.3 — all three loaders

*   **Fixed: no more getting stuck in walls and "teleporting" up them.** The chase used to walk the straight line into wall faces, slide inside, and ride up out the top. Now it **plans its route**: walls and cliffs it can't climb are **steered around** — it hugs the wall to the corner and turns in after you, without flip-flopping. New config `chasePathfinding` (default **true**; set false for the old straight-line charge).
*   **NEW: it fits through doorways.** 🚪 If the wall on its path has a ground-level opening — a **1×2 doorway** or even a **1×1 crawl-hole** — it doesn't go around: it **shrinks just enough to slip through** and regrows on the other side. Closing the door behind you buys exactly as much time as the door is tall.

### v1.3.2 — all three loaders

*   **Spawn-timer changes now apply immediately.** ⏱️ Changing `spawnMinMinutes` / `spawnMaxMinutes` / `respawnAfterKillMinutes` / `peacefulExitSpawnMinutes` with `/spider config` used to leave the already-running countdown on its old value until you relogged — which made custom spawn timers _look_ broken (they never were: values set before world load have always worked, verified). Now the live countdown **re-arms instantly from the new values, crediting the time already waited** — raise a half-elapsed cooldown and it extends by the difference; lower it below the time served and the spider comes promptly. 😈

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

## ❓ FAQ

The questions that come up again and again in the comments. If yours isn't here, ask — it may end up on this list.

**Can I become the spider with Identity, Morph, or another transformation mod?**
No — and this is the one thing here that genuinely cannot be patched. Morph mods work by swapping in a mob's **rendered model**: a fixed rig of cubes and bones Minecraft already knows how to draw. This spider has no such model. The entity itself is **invisible**; what you see is a **live inverse-kinematics simulation**, solved every tick and drawn with **dozens of independent vanilla BlockDisplay entities**. There is no single "spider model" anywhere for a morph mod to find, capture or copy — its appearance exists only as the moment-to-moment output of a running simulation. Making it morphable would mean rebuilding it as an ordinary pre-rigged model, which throws away the procedural, ground-adapting movement that is the entire point of the mod. That's not a fix, it's a worse mod. **This is architectural and permanent.** The intended answer is to *pilot* it instead of wearing it — see below.

**How do I ride it?**
Grab the creative-only **Spider Tamer**, right-click the spider to make it **docile** (it stops attacking, but still chases and grows), then right-click it empty-handed to climb on. **All four movement keys work:** W and S drive forward and back along your line of sight, A and D strafe across it, and the spider keeps facing wherever you look the whole time. Sneak to dismount. While ridden it settles to a stable, mount-friendly size. There's also `/spider newinstance` for a personal follower spider that just trails you around.

**I killed it and got no netherite — is the drop broken?**
Almost certainly not: it's a **coin flip by default** (`netheriteDropChance`, 0.5), so one kill is not a sample. Set it to 1.0 if you want a guaranteed drop. The trophy lands **on the ground directly beneath the body** rather than at the spider's centre — a giant's body rides 10–25 blocks up, so it used to fall from the sky and land far away. Two genuine bugs here were fixed long ago: kills deep underground now drop on the cave floor instead of the surface above, and "kill anything" weapons that zero your health directly (Avaritia's, for example) no longer skip the drop.

**It spawns too fast / too slow, and my config looks ignored.**
`spawnMinMinutes` and `spawnMaxMinutes` govern the **first spawn of a session only**. After that, killing it earns you `respawnAfterKillMinutes` (40 = two Minecraft days), and switching Peaceful back off brings it in after `peacefulExitSpawnMinutes` (1 minute). Changes made with `/spider config` re-arm the running countdown immediately, crediting time already served. Also worth knowing: it charges at up to **8× speed**, so it is usually *seen* far sooner than it spawned.

**It never spawns at all.**
Two usual causes. On rough terrain the safe-ground search can starve — raise `spawnAngleAttempts`. On a **skyblock or void world**, everything at the normal spawn distance is empty air; `spawnCloseFallbackDistance` (default 6) lets the search sweep inward as a last resort. And note that an **existing config file keeps its old values** when defaults change, so a long-running install may still be on old numbers — delete the file to regenerate it, or update the keys by hand.

**The carrot on a stick from the videos doesn't work, and how do I turn on the debug rendering?**
Both were features of the original plugin rather than this mod — until **v1.8.5**. The carrot is replaced by the **Spider Laser Pointer**, a creative-only item: right-click and the spider chases the red dot wherever you put it, breaking off a chase to do it (and it can't bite you while it's distracted). The debug view is **`/spider debug`** (ops): Cymaera's own debug graphics, rebuilt from his code — gold scan bars, cyan trigger zones, colour-coded feet, step targets and the green support polygon. Run it again to turn it off. It deliberately isn't bound to F3 — F3 is client-side, while everything this mod draws is server-side, which is what lets it work for players on vanilla clients.

**Can I make my base safe?**
Yes, since **v1.8.5**: craft a **Warding Idol** (4 diamonds, 4 deepslate, 1 netherite ingot) and place it. Everything within `wardRadius` (default 128 blocks) is a **safe zone**: nothing spawns inside it, and the spider can't get more than a block past its edge — so you can idle at base, or **run home mid-chase** and watch it stop at the line. Two things worth knowing: within that last block of the edge it can still reach you, so step properly inside, and a fresh idol takes 5 seconds to wake up. The one thing no idol stops is the **enraged boss**: it walks in and blows the idol apart. Idols must be 256 blocks apart, and `wardEnabled = false` turns them off entirely.

**Can I make it spawn in only one dimension?**
Yes, since **v1.8.5**. Set `spawnDimensions = "minecraft:overworld"` for an Overworld-only hunt, or list several: `spawnDimensions = "minecraft:overworld, minecraft:the_nether"`. `blockedDimensions` does the reverse — leave `spawnDimensions` empty and set `blockedDimensions = "minecraft:the_end"` for "everywhere except the End". **Modded dimension ids work too.** Both are empty by default (every dimension, as before), both can be changed live with `/spider config`, and both also stop it **following** you into a dimension it isn't allowed in. A spawn egg still works anywhere.

**It wandered off / it's a thousand blocks away.**
It finds its way back. If its chunk unloads it is replaced near a player immediately; if the chunk stays loaded (spawn chunks, a chunk loader, another player) it relocates once it has been further than `relocateDistanceBlocks` (192) from everyone for about ten seconds. There is still only ever one.

**I beat it. Can it stay dead?**
Yes — set `permadeath = true` and it never returns in that world. The result is written into the **world save**, so it survives quitting, relogging and server restarts, and it's per-world, so a new world is a fresh hunt. Only genuine kills count: a Peaceful despawn or a chunk unload never robs you of the fight.

**It's stuck, or sitting on my roof instead of coming inside.**
Fixed in **v1.8.4** — it now recognises an ordinary room, shrinks early enough to fit through the door, and comes in rather than camping overhead. Earlier versions had a run of wall-climbing, doorway and corridor problems; all are resolved. If you still find it stuck somewhere, please report it with the layout — that's exactly how the last several got fixed.

**Can I make it less terrifying?**
There is no cute mode, but `hostileOnlyAtNight = true` makes it ignore you in daylight like a vanilla spider, `maxSize` caps how large it grows (or `enableGrowth = false` keeps every spider one fixed size, 2 blocks tall by default), and `chaseDistance` controls how far away it notices you. Please read the arachnophobia warning at the top of this page before installing.

**Does it work on a dedicated server? What about Quilt?**
Yes to servers — the entire simulation runs server-side, and only the loader requirements above matter. **Quilt players should use the Fabric jar**: Quilt Loader runs it directly, so no separate build exists or is needed.

**Why is there only ever one spider?**
By design. It's a thing that hunts you, not a mob you farm.

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
*   **Rehost or redistribute the mod files** outside CurseForge. If you downloaded this jar anywhere else, it may be modified or malicious — the only official sources are this CurseForge page and its Bedrock twin.

For anything not covered here, ask: **@iR3DN4X**.

Ported to NeoForge and turned into a hunting mob by **iR3DN4X**.

### 🎆 Supporters 🎆

*   **[NetherySiloX](https://www.curseforge.com/members/netherysilox/projects)** — community contributor behind much of the v1.1.1 "Hunt Update": designed and prototyped the **safe spawning system** (no more void/water/mid-air spawns, SkyBlock-friendly ground scanning), the **wander → alert → chase AI** (the calm patrols, the freeze-and-stare moment, and the chase hysteresis that stopped the gait glitching at the detection edge), and the **server-side command/config system** that became `/spider config` — live in-game editing of every setting, including the every-sound-in-the-game step-sound picker. Thank you! 🕷️
*   **ggfelle** aka @ vayne on Discord for helping with bug tests, and suggesting to bug fix the Nether and Overworld, as well as letting the spider fit through 2x1 block holes/corridors. Thank you! 🕷️

If anyone finds any bugs/glitches/suggestions, please report them back to me in the comments, and thank you for downloading my mod!