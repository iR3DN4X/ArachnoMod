# ArachnoMod a.k.a. The Netherite Octoarachnopod — Bedrock Edition

(_THAT_ Netherite Spider — now on Bedrock)

***

A boss-grade fight whichever face it wears: the **netherite juggernaut** packs 350 HP inside the stats of a full netherite armor suit, and the mossy **camo stalker** runs 600 unarmored HP and hopes you never spot it — every one of its legs repaints itself as the block it's standing on. Kill it and it _might_ just drop the netherite it's made of.

***

## ⚠️🕷️ ARACHNOPHOBIA WARNING

**This add-on is not kind to anyone with arachnophobia.** **Please read before installing.** The spider is not a blocky vanilla mob: its eight legs are animated by a **live inverse-kinematics simulation**, so it moves, scurries, and plants each foot with unsettlingly **lifelike, realistic spider motion** — the exact kind of movement arachnophobia responds to. It also:

*   **hunts you** — it spots you from far away, stares you down, and charges;
*   is **enormous** — up to ~16 blocks tall, towering over the trees as it closes in;
*   can be **camouflaged** — the CAMO variant blends into the terrain and steps with the sound of the ground beneath it, so it can get _very_ close before you notice it.

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
*   **Procedural 8-leg animation.** No canned animations — every step is solved live with FABRIK IK, and the walk is tuned for Bedrock so the feet plant cleanly instead of dragging.
*   **Grows with distance.** Far away it's a **towering giant** (up to ~16 blocks tall); as it closes in it **shrinks down** to bite you.
*   **Charges faster the farther it is** — up to **8× speed** — so distance is no safety.
*   **It goes around corners — and lets itself in.** 🚪 The chase **steers around walls** it can't climb, and when a building blocks it, it **hunts for the way in** — a doorway or a crawl-hole — and **shrinks just enough to slip through**.
*   **No hole is safe.** Dig in, and it applies **constant pressure**, re-evaluating every tick — the instant a block breaks or a ramp opens, it pours through. Wall yourself into a 1×1 hole and it **SQUEEZES**: shrinks below its minimum size, slips down the shaft after you, bites, and regrows on the way out.
*   **No lake is safe.** In deep water it **grows just tall enough to ride above the surface** and keeps coming.
*   **It follows you between dimensions.** 🌀 Portal to the Nether or the End and a few seconds later it re-emerges near you. If it's ever lost in an unloaded chunk, a new one finds you straight away. There is still only ever ONE spider.
*   **Lava sorts the variants.** 🔥 The **netherite** spider is fireproof like the gear it's made of — it wades through lava unburnt. The **camo** variant burns, visibly, flames licking up its legs.
*   **Boss-grade fight.** The **netherite variant**: 350 HP wrapped in the **exact stats of a full netherite armor suit** (20 armor, 12 toughness) — it shrugs off most of every blow, and bites for **6 hearts**. The **camo variant**: 600 HP, no armor, a **5-heart** bite — softer, but you have to find it first. One bite every 3 seconds. Bring friends.
*   **Netherite trophy drop.** A 50% chance to drop a netherite ingot on death, landing **on the ground directly beneath the spider** so your prize is always where you'd look.
*   **Taming & riding.** The creative-only **Spider Tamer** makes it docile; then climb on with an empty hand and **ride it like a mount** — all four movement keys steer relative to where you look. Sneak to get off.
*   **Idle life.** Turn wandering off and it becomes a living statue: it **breathes** and occasionally **grooms**, lifting its front legs to its mouth and cleaning them.
*   **Peaceful-safe.** On Peaceful it despawns and natural spawns pause, like any monster.
*   **Configurable in game.** Every gameplay number can be changed live with `/spiderconfig`, and your settings are **saved with the world**.
*   **Spawn egg included** — summon your own Netherite Octoarachnopod from the creative inventory.

***

## 🎮 Commands

| Command |What it does |Who |
| ------- |------------ |--- |
| <code>/spider newinstance [size]</code> |Spawns <strong>your personal follower spider</strong>, which trails you around at the chosen size (0.3–20). A creative-mode toy. |Creative only |
| <code>/spider size &amp;lt;size&amp;gt;</code> |Resizes <strong>your</strong> personal spider (0.3–20). |Anyone (own spider) |
| <code>/spider release</code> |Dismisses your personal spider. |Anyone |
| <code>/spiderconfig list</code> |Shows every setting and its current value. |Operators |
| <code>/spiderconfig &amp;lt;setting&amp;gt;</code> |Shows one setting: its value, default, range and what it does. |Operators |
| <code>/spiderconfig &amp;lt;setting&amp;gt; &amp;lt;value&amp;gt;</code> |<strong>Changes any of the 42 settings in game</strong>, range-checked. It applies to the spider at once and is saved with the world. |Operators |

In single-player you are the operator. If another add-on also adds a `/spider` command, type `/arachno:spider` and `/arachno:spiderconfig` instead.

***

## ⚙️ Configuration

A Bedrock add-on has no config file, so the settings live **in the world** and are changed with `/spiderconfig &lt;setting&gt; &lt;value&gt;`. Setting names are not case-sensitive; the command's autocomplete lists them all in lowercase. Every change applies at once, except `chaseSpeedBlocksPerSecond` and `legStepSpeed`, which a spider reads when it spawns.

**Spawning**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>spawnMinMinutes</code> |1.0 |Earliest the FIRST spider of a session spawns, in minutes |
| <code>spawnMaxMinutes</code> |1.0 |Latest the first spider spawns (random between the two) |
| <code>respawnAfterKillMinutes</code> |40.0 |Cooldown after the spider is KILLED (40 = two Minecraft days of earned peace) |
| <code>peacefulExitSpawnMinutes</code> |1.0 |It returns this many minutes after Peaceful is switched off 😈 |
| <code>spawnDistanceMin</code> |30.0 |Closest it spawns to a player, in blocks |
| <code>spawnDistanceMax</code> |34.0 |Farthest it spawns from a player, in blocks |
| <code>spawnAngleAttempts</code> |24 |Directions tested for safe ground per spawn distance |
| <code>spawnMaxVerticalSearch</code> |48 |How far down from the surface it looks for solid ground |
| <code>camoVariantChance</code> |0.25 |Chance a spider is the CAMO variant (natural spawns and spawn eggs) |

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

**Combat & drops**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>netheriteMaxHealth</code> |350.0 |Health of the netherite variant (it also wears a full netherite suit's armor) |
| <code>camoMaxHealth</code> |600.0 |Health of the camo variant (no armor) |
| <code>netheriteAttackDamageHearts</code> |6.0 |The netherite bite, in hearts before your armor |
| <code>camoAttackDamageHearts</code> |5.0 |The camo bite, in hearts before your armor |
| <code>attackCooldownTicks</code> |60 |Ticks between bites (60 = one bite every 3 seconds) |
| <code>netheriteDropChance</code> |0.5 |Chance it drops a netherite ingot when slain |

**Camo sounds & troubleshooting**

| Setting |Default |Meaning |
| ------- |------- |------- |
| <code>variantStepVolume</code> |0.3 |Volume of the camo spider's footsteps |
| <code>variantLandVolume</code> |1.0 |Volume of its landings |
| <code>debugHud</code> |false |Shows a status line (tick rate, cost, AI mode, size, speed) above the hotbar — handy for bug reports |

***

## 🥚 Encountering it

You don't have to do anything. One spawns on its own and comes looking about a minute after you load into the world. To summon one yourself, use the **Netherite Octoarachnopod Spawn Egg** from the creative inventory — the spider already in the world is replaced, because there is only ever one.

It dies like any other mob. Beat it down, and it may drop a netherite ingot (50% chance).

***

## 📦 How to install

**You need Minecraft Bedrock Edition 26.40 or newer** on Windows, Android or iOS/iPadOS. **No experiments needed.** Xbox, PlayStation and Switch can't import add-on files.

1.  **Download** the `.mcaddon` file from the **Files** tab.
2.  **Open it with Minecraft.** On Windows, double-click it. On a phone or tablet, open it from your downloads and choose Minecraft. Minecraft opens and imports two packs: the behavior pack and the resource pack.
3.  **Turn it on for a world.** Create a new world, or open an existing world's settings. Under **Behavior Packs**, find **Netherite Octoarachnopod (Behavior Pack)** and press **Activate**. Its resource pack is added automatically.
4.  **Play.** The spider comes looking for you about a minute after you load in. In Creative, the **Netherite Octoarachnopod Spawn Egg** summons one straight away.

**Good to know**

*   **Import says the pack is already installed?** Then it is. Go straight to step 3.
*   **No spider and no spawn egg?** The behavior pack isn't active in that world. Check the world's **Behavior Packs** tab.
*   Works in new and existing worlds. Back up an existing world first, as with any add-on.
*   Like any behavior pack, it may make Minecraft turn achievements off for that world.
*   Tested in single-player on Windows. Realms and dedicated servers are untested so far. Reports are welcome.

***

## 🧭 Bedrock vs. Java

This first Bedrock release matches the Java mod's **v1.2.7**: both variants, the hunt's pacing, the squeeze, water growth, dimension following, the netherite armor, taming and riding. It also includes two later Java changes — the corner-steering, doorway-hunting chase AI and the 3-second bite.

The Java mod's later additions — the **poison** and **hunter** variants, the **enraged boss**, **permadeath**, the **Warding Idol**, the **laser pointer**, the **config book** and the **debug view** — are planned for later Bedrock updates. Java's advancements can't come across, because Bedrock add-ons can't add advancements.

***

## ❓ FAQ

**How do I ride it?**
Take the **Spider Tamer** from the creative inventory and use it on the spider to make it **docile** (it stops biting, but still follows you and changes size). Then use it again with an empty hand to climb on. **All four movement keys work**, relative to where you look. Sneak to get off. While ridden it settles to a stable size (`riddenSize`).

**I killed it and got no netherite — is the drop broken?**
It's a **coin flip by default** (`netheriteDropChance`, 0.5), so one kill isn't a sample. Set it to 1 for a guaranteed drop. The ingot lands **on the ground directly beneath the body**, not where its giant body was in the air.

**It never spawns, or it comes too soon.**
The first spawn of a session comes after `spawnMinMinutes`–`spawnMaxMinutes` (1 minute). After a kill it stays away for `respawnAfterKillMinutes` (40). It doesn't exist on Peaceful. On very rough terrain, raise `spawnAngleAttempts`.

**The camo spider's legs show the wrong block, or a purple-and-black texture.**
Please report it with the block's name. Blocks added in a newer game version than this release fall back to a related block until the add-on is updated.

**Can I make it less terrifying?**
There is no cute mode, but `hostileOnlyAtNight` = true makes it ignore you in daylight, `maxSize` caps how large it grows, and `chaseDistance` controls how far away it notices you.

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

**You may NOT:** re-use its assets or code commercially; port or re-release it for other versions, platforms or games (official ports are made by me — if you want one, ask); or **rehost the add-on files** anywhere else. If you downloaded this `.mcaddon` from another site, it may be modified or malicious — the only official sources are my CurseForge pages.

For anything not covered here, ask: **@iR3DN4X**.

### 🎆 Supporters 🎆

*   **[NetherySiloX](https://www.curseforge.com/members/netherysilox/projects)** — designed and prototyped the **safe spawning system**, the **wander → alert → chase AI**, and the in-game settings system that became `/spiderconfig` on Bedrock. Thank you! 🕷️
*   **ggfelle** aka @ vayne on Discord, for bug tests, the Nether and Overworld fixes, and getting the spider through 2×1 holes and corridors. Thank you! 🕷️
*   **Pronk** aka @ pronk.1 on Discord, for helping move the mod's release forward. Thank you! 🕷️
