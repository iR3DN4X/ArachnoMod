# ArachnoMod a.k.a. The Netherite Octoarachnopod — Bedrock Edition

(_THAT_ Netherite Spider — now on Bedrock)

***

A boss-grade fight whichever face it wears: the **netherite juggernaut** packs 350 HP inside the stats of a full netherite armor suit; the mossy **camo stalker** runs 600 unarmored HP and hopes you never spot it — every one of its legs repaints itself as the block it's standing on; the warped **poison striker** carries 500 HP and a venomous tarantula lunge; and the pitch-black **HUNTER** (400 HP, player-sized, dead silent) only moves when you're not looking. Kill it and it _might_ just drop the netherite it's made of.

***

## ⚠️🕷️ ARACHNOPHOBIA WARNING

**This add-on is not kind to anyone with arachnophobia.** **Please read before installing.** The spider is not a blocky vanilla mob: its eight legs are animated by a **live inverse-kinematics simulation**, so it moves, scurries, and plants each foot with unsettlingly **lifelike, realistic spider motion** — the exact kind of movement arachnophobia responds to. It also:

*   **hunts you** — it spots you from far away, stares you down, and charges;
*   is **enormous** — up to ~16 blocks tall, towering over the trees as it closes in;
*   can be **camouflaged** — the CAMO variant blends into the terrain and steps with the sound of the ground beneath it, so it can get _very_ close before you notice it;
*   can **pounce** — the POISON variant rears up with its front legs raised and _lunges_ at you, like a real tarantula strike;
*   can **stalk** — the pitch-black HUNTER follows you in total silence and only moves when you're not looking at it.

There is no "cute mode." If realistic spider movement is a problem for you or the people you play with, consider `hostileOnlyAtNight` = true, lowering `maxSize`, or sitting this one out. You have been warned. 🕸️

***

Somewhere in your world, a spider is SPRINTING toward you at terrifying speed.

This is the **Bedrock Edition of ArachnoMod**, the Java mod published as [Netherite Octoarachnopod](https://www.curseforge.com/minecraft/mc-mods/netherite-octoarachnopod). It adds a single, relentless, procedurally animated spider that stalks players across the world. One roams at a time. It towers over the trees and charges in faster the farther away you are, then shrinks down as it closes for a **6-heart bite**. Its eight legs are solved in real time with **FABRIK inverse kinematics**, running the same spider engine as the Java mod, so it walks, scurries, and clambers over any terrain like nothing else in Minecraft.

But this isn't a tech demo for movement physics.

It's a hunter.

***

## 🕷️ Features

*   **One spider, always hunting.** Exactly one exists at a time. It wanders the world looking for the nearest player within its chase distance.
*   **Wander / Alert / Chase AI.** When no one's around, it calmly **patrols** its territory, **pre-scanning every patrol route** block by block so it plans around lava, water, chasms and cliff edges instead of tumbling into them. The instant it spots you, it **freezes and snaps to face you** — one heart-stopping beat — then **charges**, and keeps chasing until you truly escape (its give-up radius is wider than its detection radius, so it doesn't flicker at the edge). `hostileOnlyAtNight` makes it docile in daylight like a vanilla spider.
*   **The hunt has pacing.** The first spider arrives **about a minute into a session**. **Slay it, and you've earned two full Minecraft days (40 min) of peace.** Hide in Peaceful, and it returns **one minute after peace ends** — but Peaceful can never cut a kill's cooldown short.
*   **The CAMO variant.** 🌿 25% of spiders are born with **ACTIVE CAMOUFLAGE**: every leg continuously repaints itself as **the actual block it's standing on** — grass legs in the meadow, sand legs on the beach, see-through legs on glass — changing leg by leg as it walks. It knows **every vanilla block** (over 1,300 of them). Its footsteps and landings play **that block's own sound**: it crunches on gravel, thuds on dirt, and goes whisper-quiet on wool.
*   **The POISON variant.** ☠️ 20% of spawns, sheathed in the blue-teal of **warped wart**, its footsteps squishing like Nether fungus. In striking range it **rears up, front legs raised** — your only warning — then **LUNGES**. The bite is gentler (3 hearts) but injects **Poison II for 30 seconds**. 500 HP, no armor.
*   **The HUNTER variant.** 🖤 15% of spawns: **pitch black**, **player-sized and always exactly that size**, and **completely silent**. It doesn't charge — it **stalks**, closing in fast (1.6× chase speed) but **only while nobody is looking at it**; the moment your view swings toward it, it **freezes dead-still**, mid-stride. Get within 16 blocks and it **blinds you**, topping the blindness up for as long as you stay near. Hide in a hole and it camps outside, waiting. 400 HP.
*   **Procedural 8-leg animation.** No canned animations — every step is solved live with FABRIK IK, and the walk is tuned for Bedrock so each foot **lifts clear, lands where it was aimed and stays planted** instead of skating along the ground.
*   **Grows with distance.** Far away it's a **towering giant** (up to ~16 blocks tall); as it closes in it **shrinks down** to bite you. (Rather it stayed one size? `enableGrowth` = false.)
*   **Charges faster the farther it is** — up to **8× speed** — so distance is no safety.
*   **It goes around corners — and lets itself in.** 🚪 The chase **steers around walls** it can't climb, and when a building blocks it, it **hunts for the way in** — a doorway or a crawl-hole — and **shrinks just enough to slip through**.
*   **No hole is safe.** Dig in, and it applies **constant pressure**, re-evaluating every tick — the instant a block breaks or a ramp opens, it pours through. Wall yourself into a 1×1 hole and it **SQUEEZES**: shrinks below its minimum size, slips down the shaft after you, bites, and regrows on the way out.
*   **No lake is safe.** In deep water it **grows just tall enough to ride above the surface** and keeps coming.
*   **It follows you between dimensions.** 🌀 Portal to the Nether or the End and a few seconds later it re-emerges near you. If it's ever lost in an unloaded chunk, a new one finds you straight away, and if it wanders far off with its chunk still loaded, it moves back near someone. There is still only ever ONE spider. (Want it kept out of a dimension? `spawnInOverworld`, `spawnInNether`, `spawnInEnd`.) **And it can't get lost in the Nether:** out of range there, or stranded up on the bedrock roof, the very same spider teleports to you a second later.
*   **Lava sorts the variants.** 🔥 The **netherite** spider is fireproof like the gear it's made of — it wades through lava unburnt. The **camo and poison** variants burn, visibly, flames licking up their legs.
*   **Boss-grade fight.** The **netherite variant**: 350 HP wrapped in the **exact stats of a full netherite armor suit** (20 armor, 12 toughness) — it shrugs off most of every blow, and bites for **6 hearts**. The **camo variant**: 600 HP, no armor, a **5-heart** bite — softer, but you have to find it first. The **poison** and **hunter** variants: 500 and 400 HP. One bite every 3 seconds. Bring friends.
*   **ENRAGE it — if you dare.** ⚡ Use a **netherite ingot** on the netherite spider — feed it the metal it's made of — and it becomes the **Enraged Netherite Spider**: a true boss with a **boss bar**, 700 HP, 1.5× speed, a **10-heart bite** and a crackling aura of red dust and blue sparks. Kill it and it **always drops a FULL netherite block**.
*   **Netherite trophy drop.** A 50% chance to drop a netherite ingot on death, landing **on the ground directly beneath the spider** so your prize is always where you'd look.
*   **Optional PERMADEATH.** 🏆 Set `permadeath` = true and killing it once ends the hunt in that world, for good — saved with the world. Off by default.
*   **Taming & riding.** The creative-only **Spider Tamer** makes it docile; then climb on with an empty hand and **ride it like a mount** — all four movement keys steer relative to where you look. Sneak to get off. **In creative it fights for you, too:** punch any mob and your tamed spider goes for it like a wolf would.
*   **Creative-friendly.** Like vanilla mobs, it doesn't hunt anyone in **creative or spectator** mode — it just wanders — so you can build, film or test around it in peace.
*   **Make ONE place safe — the Warding Idol.** 🛡️ Craft it (4 diamonds, 4 deepslate, 1 netherite ingot) and place it at your base: everything within **128 blocks** becomes a **safe zone**. Nothing spawns inside it, and the spider **can't follow you in** — run home mid-chase and it stops at the edge and waits outside. Linger within one block of the edge and it can still reach you. **Except the enraged boss:** it walks straight in and blows apart any idol it gets within 16 blocks of.
*   **Play with it: the SPIDER LASER POINTER.** 🔴 A creative-only pointer — use it and the spider bolts for the red dot like a cat, **breaking off a chase** to do it. While it chases the dot it **can't bite you**.
*   **See how it thinks.** 🔍 `/spider debug` switches on **Cymaera's debug view from the original videos**, rebuilt part for part: scan bars, trigger zones, colour-coded feet, step targets and the support polygon, live on the walking spider.
*   **Idle life.** Turn wandering off and it becomes a living statue: it **breathes** and occasionally **grooms**, lifting its front legs to its mouth and cleaning them.
*   **Peaceful-safe.** On Peaceful it despawns and natural spawns pause, like any monster.
*   **Configurable in game.** Every gameplay number can be changed live — from the **Spider Config Book** (a menu with every setting, what it does, its default and a reset), or with `/spiderconfig` — and your settings are **saved with the world**.
*   **Spawn egg included** — summon your own Netherite Octoarachnopod from the creative inventory.

***

## 🎮 Commands

| Command |What it does |Who |
| ------- |------------ |--- |
| <code>/spider newinstance [size]</code> |Spawns <strong>your personal follower spider</strong>, which trails you around at the chosen size (0.3–20). A creative-mode toy. |Creative only |
| <code>/spider size &amp;lt;size&amp;gt;</code> |Resizes <strong>your</strong> personal spider (0.3–20). |Anyone (own spider) |
| <code>/spider release</code> |Dismisses your personal spider. |Anyone |
| <code>/spider menu</code> |Opens the <strong>settings menu</strong> (the same one as the Spider Config Book): every setting with its description, default and a reset. |Anyone (only operators can change things) |
| <code>/spider debug</code> |Toggles <strong>Cymaera's debug view</strong> on every spider: gold scan bars, cyan trigger zones, diamond/lapis/redstone feet, lime step targets and the green support polygon. Run it again to switch it off. |Operators |
| <code>/spiderconfig list</code> |Shows every setting and its current value. |Operators |
| <code>/spiderconfig &amp;lt;setting&amp;gt;</code> |Shows one setting: its value, default, range and what it does. |Operators |
| <code>/spiderconfig &amp;lt;setting&amp;gt; &amp;lt;value&amp;gt;</code> |<strong>Changes any of the 75 settings in game</strong>, range-checked. It applies to the spider at once and is saved with the world. On/off settings take 1 or 0 here. |Operators |
| <code>/spidertoggle &amp;lt;setting&amp;gt; &amp;lt;true/false&amp;gt;</code> |Switches an on/off setting, with autocomplete for just those. |Operators |

In single-player you are the operator. If another add-on also adds one of these commands, put `arachno:` in front: `/arachno:spider`, `/arachno:spiderconfig`, `/arachno:spidertoggle`.

***

## ⚙️ Configuration

A Bedrock add-on has no config file, so the settings live **in the world**. The easy way to change them is the **Spider Config Book**: use it for a menu with every setting, grouped like the tables below, with a search, what each one does, its default and a reset. Operators (and the owner of a single-player world) are handed one the first time they join, it's in the creative inventory, and `/spider menu` opens the same menu. Players who aren't operators can look but not change anything. Or type `/spiderconfig &lt;setting&gt; &lt;value&gt;`: setting names are not case-sensitive, and the command's autocomplete lists them all in lowercase. Every change applies at once, except `chaseSpeedBlocksPerSecond`, `legStepSpeed`, `netheriteArmor`, `netheriteArmorToughness` and `netheriteLegs`, which a spider reads when it spawns.

**Spawning**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>spawnMinMinutes</code> |1.0 |Earliest the FIRST spider of a session spawns, in minutes |
| <code>spawnMaxMinutes</code> |1.0 |Latest the first spider spawns (random between the two) |
| <code>respawnAfterKillMinutes</code> |40.0 |Cooldown after the spider is KILLED (40 = two Minecraft days of earned peace) |
| <code>permadeath</code> |false |BOSS MODE: once a spider is killed in this world it never comes back (saved with the world; spawn eggs still work) |
| <code>peacefulExitSpawnMinutes</code> |1.0 |It returns this many minutes after Peaceful is switched off 😈 |
| <code>spawnInOverworld</code> |true |May it spawn in (and follow players into) the Overworld? Spawn eggs work anywhere |
| <code>spawnInNether</code> |true |The same for the Nether |
| <code>spawnInEnd</code> |true |The same for the End |
| <code>spawnDistanceMin</code> |30.0 |Closest it spawns to a player, in blocks |
| <code>spawnDistanceMax</code> |34.0 |Farthest it spawns from a player, in blocks |
| <code>relocateDistanceBlocks</code> |192.0 |A living spider this far from every player, with its chunk still loaded, moves near someone (never counts as a kill; 0 disables) |
| <code>spawnAngleAttempts</code> |24 |Directions tested for safe ground per spawn distance (raise it for SkyBlock) |
| <code>spawnMaxVerticalSearch</code> |48 |How far down from the surface it looks for solid ground |
| <code>spawnCloseFallbackDistance</code> |6.0 |SkyBlock last resort: when every ring from spawnDistanceMin out is void, search inward this close (0 disables) |
| <code>hunterVariantChance</code> |0.15 |Chance a spawn is the HUNTER variant (rolled first) |
| <code>poisonVariantChance</code> |0.2 |Chance a spawn is the POISON variant (rolled next) |
| <code>camoVariantChance</code> |0.25 |Chance a spider is the CAMO variant (natural spawns and spawn eggs) |

**Warding Idol**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>wardEnabled</code> |true |Are Warding Idols active? Each makes a safe zone wardRadius blocks round it |
| <code>wardRadius</code> |128.0 |How far an idol reaches, in blocks, horizontally (a cylinder from bedrock to sky) |
| <code>wardMinSpacing</code> |256.0 |Minimum distance between two idols, in blocks (one placed too close is handed back; 0 = no limit) |
| <code>wardArmingSeconds</code> |5.0 |Seconds a freshly placed idol takes to wake up (0 = at once) |

**Tools & items**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>laserPointerRange</code> |64.0 |How far the Spider Laser Pointer throws its dot, in blocks |
| <code>laserPointerLureSeconds</code> |4.0 |How long the spider chases the dot per use, in seconds (it can't bite meanwhile) |
| <code>giveConfigBook</code> |true |Hand operators a Spider Config Book the first time they join |
| <code>debugHud</code> |false |Shows a status line (tick rate, cost, AI mode, size, speed, steps) above the hotbar — handy for bug reports |

**Chase & speed**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>chaseDistance</code> |64.0 |How far it spots and chases players, in blocks |
| <code>chaseExitDistanceMultiplier</code> |1.25 |Give-up radius = chaseDistance × this (no flicker at the edge) |
| <code>alertReactionTicks</code> |10 |The freeze-and-stare beat before it charges (0 = instant charge) |
| <code>hostileOnlyAtNight</code> |false |Only hunts at night, like a vanilla spider |
| <code>chasePathfinding</code> |true |Steer around walls and shrink through doorways and crawl-holes while chasing (false = the straight-line charge) |
| <code>chaseSpeedBlocksPerSecond</code> |8.0 |Chase speed at its smallest size, in blocks per second |
| <code>speedGrowthFactor</code> |8.0 |How much faster it is at its largest size |
| <code>legStepSpeed</code> |1.1 |How fast the legs swing — the "scurry" |

**Wandering & idle**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>enableWandering</code> |true |Calm patrol when no player is in range (false = stands still, breathes and grooms) |
| <code>wanderSpeedFactor</code> |0.35 |Patrol speed as a fraction of chase speed |
| <code>wanderRadius</code> |24.0 |How far it patrols from its anchor point, in blocks |
| <code>wanderMinIntervalSeconds</code> |3.0 |Shortest patrol leg or pause, in seconds |
| <code>wanderMaxIntervalSeconds</code> |9.0 |Longest patrol leg or pause, in seconds |
| <code>wanderPauseChance</code> |0.25 |Chance each patrol leg is a pause instead |
| <code>groomingChance</code> |0.03 |Chance per second that the idle spider grooms (wandering off; 0 = never) |

**Size & growth**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>minSize</code> |0.6 |Its size right next to a player |
| <code>maxSize</code> |15.0 |Its size far away (15 towers over the trees) |
| <code>sizeNearDistance</code> |4.0 |At or inside this distance it is at minSize |
| <code>sizeFarDistance</code> |32.0 |At or beyond this distance it is at maxSize |
| <code>growPercentPerTick</code> |12.0 |Fastest it grows, in percent per tick |
| <code>shrinkPercentPerTick</code> |25.0 |Fastest it shrinks, in percent per tick |
| <code>squeezeSize</code> |0.25 |How small it SQUEEZES to follow you into a 1×1 hole |
| <code>riddenSize</code> |2.0 |Its size while you ride it |
| <code>growInWater</code> |true |In deep water, grow just tall enough to ride above the surface (false = deep water stays a drowning trap) |
| <code>enableGrowth</code> |true |false = EVERY spider holds one size, fixedSizeBlocks tall — near or far, in water, ridden (it still shrinks to fit doorways and low ceilings) |
| <code>fixedSizeBlocks</code> |2.0 |How tall every spider stands, in blocks, when enableGrowth is false |

**Combat & drops**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>netheriteMaxHealth</code> |350.0 |Health of the netherite variant (it also wears a full netherite suit's armor) |
| <code>netheriteArmor</code> |20.0 |Netherite variant armor points (20 = a full netherite suit; 0 = none) |
| <code>netheriteArmorToughness</code> |12.0 |Netherite variant armor toughness (12 = a full netherite suit) |
| <code>netheriteLegs</code> |8 |How many legs the netherite spider has (8 = the classic; 1–16 all walk) |
| <code>camoMaxHealth</code> |600.0 |Health of the camo variant (no armor) |
| <code>poisonMaxHealth</code> |500.0 |Health of the poison variant (no armor) |
| <code>poisonSize</code> |1.0 |The poison variant's fixed size (it never grows with distance) |
| <code>hunterMaxHealth</code> |400.0 |Health of the hunter variant (no armor) |
| <code>hunterSize</code> |1.1 |The hunter's fixed size (fits 1×2 doorways as it is) |
| <code>hunterSpeedMultiplier</code> |1.6 |The hunter's speed (× chase speed) while it moves — and it only moves while nobody looks at it |
| <code>hunterBlindnessRange</code> |16.0 |How close the stalking hunter blinds you, in blocks (0 disables) |
| <code>hunterBlindnessSeconds</code> |30.0 |How long the blindness lasts once you're out of its range, in seconds |
| <code>netheriteAttackDamageHearts</code> |6.0 |The netherite bite, in hearts before your armor |
| <code>camoAttackDamageHearts</code> |5.0 |The camo bite, in hearts before your armor |
| <code>hunterAttackDamageHearts</code> |4.0 |The hunter bite — it takes your sight, not your health |
| <code>poisonAttackDamageHearts</code> |3.0 |The poison bite — every bite also injects Poison II |
| <code>poisonEffectSeconds</code> |30.0 |How long the poison bite's Poison II lasts, in seconds |
| <code>attackCooldownTicks</code> |60 |Ticks between bites (60 = one bite every 3 seconds) |
| <code>netheriteDropChance</code> |0.5 |Chance it drops a netherite ingot when slain |
| <code>enragedMaxHealth</code> |700.0 |Health of the ENRAGED netherite spider |
| <code>enragedSpeedMultiplier</code> |1.5 |Chase-speed multiplier while enraged |
| <code>enragedAttackDamageHearts</code> |10.0 |The enraged boss's bite, in hearts before your armor. It always drops a full netherite block |

**Variant sounds**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>variantStepVolume</code> |0.3 |Volume of the camo and poison spiders' footsteps |
| <code>variantLandVolume</code> |1.0 |Volume of their landings |

***

## 🥚 Encountering it

You don't have to do anything. One spawns on its own and comes looking about a minute after you load into the world. To summon one yourself, use the **Netherite Octoarachnopod Spawn Egg** from the creative inventory — the spider already in the world is replaced, because there is only ever one.

It dies like any other mob. Beat it down, and it may drop a netherite ingot (50% chance). Everything else the add-on brings is in the creative inventory too: the **Spider Tamer** and **Spider Laser Pointer** under Equipment, the **Spider Config Book** under Items, and the **Warding Idol** under Construction (it's also craftable).

***

## 📦 How to install

**You need Minecraft Bedrock Edition 26.40 or newer** on Windows, Android or iOS/iPadOS. **No experiments needed.** Xbox, PlayStation and Switch can't import add-on files.

1.  **Download** the `.mcaddon` file from the **Files** tab.
2.  **Open it with Minecraft.** On Windows, double-click it. On a phone or tablet, open it from your downloads and choose Minecraft. Minecraft opens and imports two packs: the behavior pack and the resource pack.
3.  **Turn it on for a world.** Create a new world, or open an existing world's settings. Under **Behavior Packs**, find **Netherite Octoarachnopod v1.8.6 (Behavior Pack)** and press **Activate**. Its resource pack is added automatically.
4.  **Play.** The spider comes looking for you about a minute after you load in. In Creative, the **Netherite Octoarachnopod Spawn Egg** summons one straight away.

**Good to know**

*   **Import says the pack is already installed?** Then it is. Go straight to step 3.
*   **Updating from 1.2.7?** Import the new file. Your worlds keep their settings, because they're saved in the world. If a world still runs the old version, open its **Behavior Packs**, remove the pack and activate it again.
*   **No spider and no spawn egg?** The behavior pack isn't active in that world. Check the world's **Behavior Packs** tab.
*   Works in new and existing worlds. Back up an existing world first, as with any add-on.
*   Like any behavior pack, it may make Minecraft turn achievements off for that world.
*   Tested in single-player on Windows. Realms and dedicated servers are untested so far. Reports are welcome.

***

## 🧭 Bedrock vs. Java

Since **v1.8.6** the add-on uses the Java mod's version numbers, and it matches **Java v1.8.6**: all four variants, the enraged boss, permadeath, the Warding Idol, the laser pointer, the config book, the debug view, the creative-mode changes and the Nether rescue (which came to Bedrock first). The differences are what Bedrock itself decides:

*   **No advancements.** Bedrock add-ons can't add them.
*   **Three dimensions, three switches.** Bedrock has no modded dimensions, so Java's dimension lists are three on/off settings here (`spawnInOverworld`, `spawnInNether`, `spawnInEnd`).
*   **Settings live in the world**, not in a config file, and the config book is a Bedrock menu rather than Java's settings screen.
*   **No creative tab of its own.** Bedrock add-ons can't add one, so the items sit in the vanilla tabs (see Encountering it).

***

## 📜 Changelog

### v1.8.6 (latest) — caught up with Java

The version jumps from 1.2.7 to 1.8.6 because the add-on now carries the Java mod's version numbers: this release has everything Java v1.8.6 has.

*   **Two new variants.** ☠️ The **poison** tarantula (rears up and lunges; the bite injects Poison II) and 🖤 the **hunter** (pitch black, silent, player-sized; only moves while nobody looks at it, and blinds you up close).
*   **The enraged boss.** ⚡ Feed the netherite spider a netherite ingot: a boss bar, 700 HP, 1.5× speed, a 10-heart bite, and a full netherite block when it dies.
*   **Permadeath**, the **netherite legs and armor settings**, and **one-size mode** (`enableGrowth`).
*   **The Warding Idol** 🛡️ — a craftable block that makes a 128-block safe zone round your base.
*   **The Spider Laser Pointer** 🔴 and **the debug view** 🔍 (`/spider debug`).
*   **The Spider Config Book** 📖 — every setting in a menu, with a search and a reset; also `/spider menu`.
*   **Creative-friendly**: it ignores players in creative and spectator mode and just wanders. A tamed spider follows you in creative and **fights the mobs you punch**.
*   **It can't get lost in the Nether**: out of range, or stuck on the bedrock roof, it teleports to you.
*   **Dimension switches**, **SkyBlock spawning** (`spawnCloseFallbackDistance`) and **relocation** when it wanders too far.
*   **Fixed: numeric settings couldn't be changed in 1.2.7.** `/spiderconfig` turned away every number ("Syntax error"); it takes numbers now, and the new `/spidertoggle` switches the on/off settings.
*   **Feet that lift.** Steps that used to skate along the ground now lift clear and land where they were aimed, so it scurries like the Java spider.
*   75 settings (was 42). Your 1.2.7 settings are kept.

### v1.2.7 — first Bedrock release

Matched the Java mod's v1.2.7: the netherite and camo variants, the chase AI, the hunt's pacing, the squeeze, water growth, dimension following, taming and riding, and 42 settings saved with the world.

***

## ❓ FAQ

**How do I ride it?**
Take the **Spider Tamer** from the creative inventory and use it on the spider to make it **docile** (it stops biting, but still follows you and changes size). Then use it again with an empty hand to climb on. **All four movement keys work**, relative to where you look. Sneak to get off. While ridden it settles to a stable size (`riddenSize`).

**Can my tamed spider fight for me?**
Yes, in creative: punch any mob and the spider you tamed goes after it and bites until it's dead, then comes back to you, like a wolf. It only takes orders from whoever tamed it, only while that player is in creative, never against players, and not while you're riding it.

**Can I make my base safe?**
Craft a **Warding Idol** (4 diamonds, 4 deepslate, 1 netherite ingot) and place it. Everything within `wardRadius` (128 blocks) is a **safe zone**: nothing spawns inside it, and the spider can't get more than a block past its edge, so you can run home mid-chase. Step properly inside, because within that last block it can still reach you. A fresh idol takes 5 seconds to wake up, and idols must be 256 blocks apart. The **enraged boss** ignores idols and blows them apart.

**I beat it. Can it stay dead?**
Set `permadeath` = true and it never returns in that world. It's saved with the world, so it survives quitting and restarts, and a new world is a fresh hunt. Only real kills count: a Peaceful despawn never robs you of the fight.

**I killed it and got no netherite — is the drop broken?**
It's a **coin flip by default** (`netheriteDropChance`, 0.5), so one kill isn't a sample. Set it to 1 for a guaranteed drop. The ingot lands **on the ground directly beneath the body**, not where its giant body was in the air.

**It never spawns, or it comes too soon.**
The first spawn of a session comes after `spawnMinMinutes`–`spawnMaxMinutes` (1 minute). After a kill it stays away for `respawnAfterKillMinutes` (40). It doesn't exist on Peaceful, and it never spawns inside a Warding Idol's zone. In creative it does spawn, but it only wanders and won't come for you. On very rough terrain, raise `spawnAngleAttempts`. On a SkyBlock or void world, `spawnCloseFallbackDistance` lets it search closer in.

**The camo spider's legs show the wrong block, or a purple-and-black texture.**
Please report it with the block's name. Blocks added in a newer game version than this release fall back to a related block until the add-on is updated.

**Can I make it less terrifying?**
There is no cute mode, but `hostileOnlyAtNight` = true makes it ignore you in daylight, `maxSize` caps how large it grows (or `enableGrowth` = false keeps every spider 2 blocks tall), and `chaseDistance` controls how far away it notices you.

**Why is there only ever one spider?**
By design. It's a thing that hunts you, not a mob you farm.

***

## 🙏 Credits & license

Created by **iR3DN4X**. Based on **[TheCymaera/minecraft-spider](https://github.com/TheCymaera/minecraft-spider)** by **Heledron** (YouTube [@heledron](https://www.youtube.com/@heledron)), whose original procedural-spider plugin is the heart of everything you see here: the inverse-kinematics legs and the gait are his work, used with attribution. I built a hostile-mob experience on top of it, then brought it to Bedrock.

*   🔧 **Original plugin (GitHub):** [https://github.com/TheCymaera/minecraft-spider](https://github.com/TheCymaera/minecraft-spider)
*   🎬 **Demo video:** [https://www.youtube.com/watch?v=Hc9x1e85L0w](https://www.youtube.com/watch?v=Hc9x1e85L0w)
*   ☕ **The Java Edition mod:** [Netherite Octoarachnopod on CurseForge](https://www.curseforge.com/minecraft/mc-mods/netherite-octoarachnopod)

### 📄 License

**Copyright © iR3DN4X.** ArachnoMod is **source-available freeware**: free to download and play, forever — but not open-source, and not free to re-use commercially. The license is the same for the Java mod and this add-on; the copy bundled with the latest release (`LICENSE.md` inside each pack) is the license in force. Source: [github.com/iR3DN4X/ArachnoMod](https://github.com/iR3DN4X/ArachnoMod).

**You MAY:** play it anywhere, including servers and Realms; feature it in videos and streams, monetized or not; make resource packs, language packs and add-ons for it; and make non-commercial derivative works with credit — except ports.

**You may NOT:** re-use its assets or code commercially; port or re-release it for other versions, platforms or games (official ports are made by me — if you want one, ask); or **rehost the add-on files** anywhere other than my official pages. If you downloaded this `.mcaddon` from another site, it may be modified or malicious — the only official sources are my CurseForge pages and this add-on's page on **MCPEDL** (_Netherite Octoarachnopod — The Netherite Spider_, uploaded by me).

For anything not covered here, ask: **@iR3DN4X**.

### 🎆 Supporters 🎆

*   **[NetherySiloX](https://www.curseforge.com/members/netherysilox/projects)** — designed and prototyped the **safe spawning system**, the **wander → alert → chase AI**, and the in-game settings system that became `/spiderconfig` on Bedrock. Thank you! 🕷️
*   **ggfelle** aka @ vayne on Discord, for bug tests, the Nether and Overworld fixes, and getting the spider through 2×1 holes and corridors. Thank you! 🕷️
*   **Pronk** aka @ pronk.1 on Discord, for helping move the mod's release forward. Thank you! 🕷️
