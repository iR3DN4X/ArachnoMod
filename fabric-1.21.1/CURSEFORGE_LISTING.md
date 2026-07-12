# ArachnoMod — the Netherite Octoarachnopod

**A giant, procedurally-animated spider that hunts you across the world.** One roams at a time, respawning every 5–30 minutes. It towers over the trees and charges in at incredible speed the farther away you are, then shrinks down as it closes for a **6-heart bite**. Its eight legs are driven by real **FABRIK inverse-kinematics**, drawn entirely with vanilla **BlockDisplay** entities — so it walks, scurries, and clambers like nothing else in Minecraft.

Built on **1000 HP** of boss-grade menace. Kill it and it *might* just drop the netherite it's made of.

---

## ⚠️🕷️ ARACHNOPHOBIA WARNING

**This mod is not kind to arachnophobes — please read before installing.** The spider in this mod is not a blocky vanilla mob: its eight legs are animated by a **live inverse-kinematics simulation**, so it moves, scurries, and plants each foot with unsettlingly **lifelike, realistic spider motion** — the exact kind of movement arachnophobia responds to. It also:

- **hunts you** — it spots you from far away, stares you down, and charges;
- is **enormous** — up to ~16 blocks tall, towering over the trees as it closes in;
- can be **camouflaged** (v1.1.1) — the CAMO variant blends into the terrain and sounds like ordinary footsteps, so it can get *very* close before you notice it.

There is no "cute mode." If realistic spider movement is a problem for you or the people on your server, this may genuinely be an uncomfortable experience — consider `hostileOnlyAtNight = true`, lowering `maxSize`, or sitting this one out. You have been warned. 🕸️

---

## ✅ Now available on Fabric!

ArachnoMod now runs on **three loaders**:

| Loader | Minecraft | Requires |
|---|---|---|
| **Fabric** | **1.21.1** | Fabric API **+** Fabric Language Kotlin |
| **NeoForge** | 1.21.1 | Kotlin for Forge |
| **Forge** | 1.20.1 | Kotlin for Forge *(also loads on NeoForge 1.20.1)* |

> The Fabric build is a full, native port — same spider, same simulation, same features — verified in-world. **You must install Fabric API and Fabric Language Kotlin** alongside it, or the game won't load the mod.

---

## 🕷️ Features

- **One spider, always hunting.** Exactly one exists at a time. It wanders the world looking for the nearest player within its chase distance.
- **🆕 Wander / Alert / Chase AI (v1.1.1).** When no one's around, the spider calmly **patrols** its territory (never strolling into water or off cliffs). The instant it spots you it **freezes and snaps to face you** — one heart-stopping beat — then **charges**. It keeps chasing until you truly escape (wider give-up radius than its detection radius, so no flickering at the boundary). Optional `hostileOnlyAtNight` mode makes it docile in daylight like a vanilla spider.
- **🆕 The CAMO variant (v1.1.1).** A second spider that can naturally spawn (25% by default, still only ONE spider ever) with **ACTIVE CAMOUFLAGE**: every leg continuously repaints itself as **the actual block it's standing on** — grass legs in the meadow, sand legs on the beach, stone legs in the mountains, changing leg-by-leg as it walks. Its footsteps play **the real step sound of the block underfoot**, exactly like player footsteps — it crunches on gravel, thuds on dirt, and goes whisper-quiet on wool. You won't see it coming. You might not hear it either.
- **🆕 Safe spawning (v1.1.1).** Spawns (and patrol routes) are validated for solid, dry ground — no more spiders in the void, over oceans, or inside SkyBlock gaps. Keeps the configured spawn distance whenever possible, preferring farther over closer; never pops up in your face.
- **Procedural 8-leg animation.** No canned animations — every step is solved live with FABRIK IK and rendered through vanilla BlockDisplays. Buttery-smooth motion.
- **Grows with distance.** Far away it's a **towering giant** (up to ~16 blocks tall); as it closes in it **shrinks down** to bite you. The feet stay planted at every size.
- **Charges faster the farther it is** — up to **8× speed** — so distance is no safety.
- **Boss-grade fight.** 1000 HP and a 6-heart melee hit. Bring friends.
- **Netherite trophy drop.** A 50% chance to drop a netherite ingot on death (it *is* made of the stuff).
- **Taming & riding.** A creative-only **Spider Tamer** makes it docile, then you can **ride it like a mount** (see below).
- **Peaceful-safe.** On Peaceful difficulty it despawns and natural spawns pause, like any monster.
- **Fully configurable & hot-reloading.** Every gameplay number lives in a commented config file you can edit live (see Configuration).
- **Spawn egg included** — spawn your own Netherite Octoarachnopod from the creative menu.

---

## 🎮 Commands

All under `/spider`:

| Command | What it does | Who |
|---|---|---|
| `/spider newinstance [size]` | Spawns **your personal follower spider** that trails you around at the chosen size (0.3–20). A creative-mode toy. | Creative only |
| `/spider size <n>` | Live-resizes **your** personal spider (0.3–20). | Anyone (own spider) |
| `/spider release` | Dismisses your personal spider. | Anyone |
| `/spider chasedistance <blocks>` | Sets how far (8–256) the **wild** spider spots and chases players. **Saves into the config file.** | Ops (permission level 2) |
| 🆕 `/spider config <key> get` | Shows any config value in chat. | Ops (permission level 2) |
| 🆕 `/spider config <key> set <value>` | **Live-edits ANY of the 35 config values in-game** — typed, range-checked arguments with full tab-completion (sound keys tab-complete against every sound in the game). Applies to the active spider instantly and saves straight into the config file. | Ops (permission level 2) |

---

## ⚙️ Configuration

All tunables live in **`config/arachnomod-common.toml`**, created on first launch. It's **fully commented** and **hot-reloads** — edit the file and most changes apply to a running world within moments, no restart needed. (Spawn/gait values are read when a spider spawns, so let the current one respawn to apply those.)

| Key | Default | Meaning |
|---|---|---|
| `spawnMinMinutes` | 5.0 | Min minutes before a spider spawns |
| `spawnMaxMinutes` | 30.0 | Max minutes before a spider spawns (delay is random between the two) |
| `spawnDistanceMin` | 30.0 | Closest a spider naturally spawns from a player (blocks) |
| `spawnDistanceMax` | 34.0 | Farthest a spider naturally spawns from a player (blocks) |
| `chaseDistance` | 64.0 | How far the spider spots & chases players (blocks) |
| `chaseSpeedBlocksPerSecond` | 8.0 | Top chase speed at normal size |
| `speedGrowthFactor` | 8.0 | Speed multiplier at maximum size |
| `legStepSpeed` | 1.1 | How fast the legs swing — the "scurry" |
| `minSize` | 0.6 | Size right next to a player |
| `maxSize` | 15.0 | Size when far away (15 towers over the trees) |
| `sizeNearDistance` | 4.0 | At/below this distance the spider is at minSize |
| `sizeFarDistance` | 32.0 | At/above this distance the spider is at maxSize |
| `growPercentPerTick` | 12.0 | Fastest it can grow (%/tick) |
| `shrinkPercentPerTick` | 25.0 | Fastest it can shrink (%/tick) |
| `riddenSize` | 2.0 | Stable size while a player is riding it |
| `maxHealth` | 1000.0 | The spider's max health |
| `attackDamageHearts` | 6.0 | Melee damage in hearts per hit |
| `attackCooldownTicks` | 20 | Ticks between melee hits (20 = 1/sec) |
| `netheriteDropChance` | 0.5 | Chance to drop a netherite ingot on death |

**🆕 New in v1.1.1** (16 more keys — every one editable in-game via `/spider config`):

| Key | Default | Meaning |
|---|---|---|
| `spawnAngleAttempts` | 12 | Directions tested per candidate distance when hunting safe spawn ground |
| `spawnMaxVerticalSearch` | 48 | How deep below the heightmap to look for solid ground (SkyBlock-friendly) |
| `camoVariantChance` | 0.25 | Chance a spawned spider is the CAMO variant (natural spawns AND spawn eggs) |
| `chaseExitDistanceMultiplier` | 1.25 | Give-up radius = chaseDistance × this (prevents boundary flicker) |
| `alertReactionTicks` | 10 | The freeze-and-stare beat before it charges (0 = instant charge) |
| `hostileOnlyAtNight` | false | Only hunts at night, like a vanilla spider |
| `enableWandering` | true | Calm patrol when no player is in range (false = stands still, pre-1.1 behavior) |
| `wanderSpeedFactor` | 0.35 | Patrol speed as a fraction of chase speed |
| `wanderRadius` | 24 | How far it patrols from its anchor point |
| `wanderMinIntervalSeconds` | 3.0 | Shortest commitment to one patrol heading |
| `wanderMaxIntervalSeconds` | 9.0 | Longest commitment to one patrol heading |
| `wanderPauseChance` | 0.25 | Chance it pauses instead of picking a new heading |
| `variantStepSound` | `block.moss.step` | Fallback step sound for non-netherite variants (camo normally uses the walked-on block's own sound) |
| `variantStepVolume` | 0.3 | Volume of variant steps (also scales camo's block-matched steps) |
| `variantLandSound` | `block.moss.fall` | Fallback landing sound for non-netherite variants |
| `variantLandVolume` | 1.0 | Volume of variant landings |

---

## 🐴 Why riding? (and why you can't morph into the spider)

The #1 request is always *"can I become the spider / turn into it with Identity or Morph?"* — and the honest answer is **no, and it's not something a patch can add.** Instead we gave you the next best thing: **tame it and ride it.**

Grab the creative-only **Spider Tamer** item, right-click the spider to make it **docile** (it stops attacking but still chases and grows), then right-click it empty-handed to **climb on and ride it** — horse-style steering: **look where you want to go and hold W**, sneak to dismount. While ridden it settles to a stable, mount-friendly size. You *pilot* the spider instead of wearing it.

The **disclaimer** below explains exactly why morphing is impossible — it's worth a read if you were hoping for Identity/Morph support.

---

## ⚠️ Disclaimer — ArachnoMod is NOT compatible with Identity, Morph, or other transformation mods (and cannot be made so)

This comes up a lot, so here's the full, honest explanation.

**Morph mods work by swapping a model.** When you "become" a mob with Identity, Morph, iDisguise, etc., the mod takes that mob's **rendered model** — a fixed rig of cubes and bones that Minecraft already knows how to draw — and renders *that* in place of your player. It's copying an existing, static model.

**ArachnoMod's spider has no such model.** This is the whole trick behind why it moves the way it does:

- The actual spider **entity is invisible** (it renders nothing).
- What you *see* is a **live, server-side physics and inverse-kinematics simulation** — its legs are solved every single tick with FABRIK and drawn using **dozens of independent vanilla BlockDisplay entities**.
- There is **no single "spider model"** anywhere in the game for a morph mod to find, capture, or reproduce. The spider's appearance **only exists as the moment-to-moment output of a running simulation.**

So there is simply nothing for a morph mod to grab onto. It can copy a model; it cannot copy a *simulation that is being computed live from dozens of separate entities.*

**Could we make it compatible?** Only by throwing away what makes ArachnoMod special — we'd have to rebuild the spider as an ordinary, pre-rigged animated model, which would kill the procedural, ground-adapting, size-shifting movement that is the entire point of the mod. That's not a fix; it's a different, worse mod.

**This is architectural and permanent — not a missing feature or an incompatibility we can eventually patch.** If you want to *be* the spider, **tame it and ride it.** That's the intended answer, and it's why taming and riding exist.

---

## 📜 Full changelog

### v1.1.4 (latest) — all three loaders
- **Peace has a price: the spider now returns 1 minute after Peaceful difficulty is switched off** (was: a fresh 5–30 minute roll). Configurable via the new `peacefulExitSpawnMinutes` key.
- **More reliable spawning in rough terrain** *(thanks NetherySiloX for the report)*: the default `spawnAngleAttempts` is now **24** (was 12) — the spawner tests twice as many directions for safe ground, fixing "spider never shows up" in dense forests, snowy peaks, and cliffside terrain. **If you have an existing config file, raise `spawnAngleAttempts` to 24 yourself** (or delete the key and let it regenerate) — saved configs keep their old value.

### v1.1.3 — all three loaders
- **Improved wandering: route pre-scanning** *(contributed by NetherySiloX)*. Before committing to a patrol route, the spider now scans the ground along the entire path block-by-block and rejects routes that cross ravines, cliff gaps, or water — it plans its way around hazards instead of tumbling into them.
- **New idle life: breathing.** When wandering is disabled (`enableWandering = false`), the standing spider gently breathes — a slow body bob (~4.5 s per breath) that scales with its size and fades out the moment it moves.
- **New idle life: grooming** *(concept & animation by NetherySiloX)*. While idle (wandering disabled), the spider occasionally (3%/sec after standing still, `groomingChance`) lifts its front leg pair to its mouth and cleans them with a smooth sweeping motion while leaning its body down — 5 seconds of surprisingly endearing behavior from a 1000 HP murder machine.
- New config key: `groomingChance` (0–1 per second, 0 disables).

### v1.1.2 — all three loaders
- **Improved: the netherite trophy now drops on the FLOOR directly beneath the spider — at any height, any depth.** Previously, killing the giant form (especially from range) dropped the ingot from 10–25 blocks up in the air, where it fell and landed somewhere easy to miss — making drops *feel* broken. Now it lands right where the spider died: on the surface, on a cave floor, at negative Y — anywhere, anytime. (Drop chance is unchanged — `netheriteDropChance`, default 0.5, configurable up to guaranteed.)

### v1.1.1 — The Hunt Update — all three loaders
- **NEW: Wander / Alert / Chase AI.** The spider now patrols calmly when alone, freezes and *stares at you* for a beat when it first spots you, then charges — and keeps chasing until you genuinely escape (no more flickering at the detection edge). Optional `hostileOnlyAtNight` vanilla-spider mode.
- **NEW: The CAMO variant with active camouflage.** A mossy second spider (25% of spawns by default — still only ever ONE spider) whose legs **continuously repaint as the actual blocks it walks on**, leg by leg, and whose footsteps play **the real sound of the block underfoot** exactly like player steps. Spawn eggs roll the variant chance too.
- **NEW: Safe spawning.** Spawn positions (and patrol targets) are verified solid, dry, and clear — never in water, lava, the void, or mid-air. Works on SkyBlock/OneBlock-style maps. Prefers the configured distance band, farther over closer.
- **NEW: `/spider config <key> get|set <value>`** — every one of the 35 config values live-editable in-game (OP-only), with typed, range-checked arguments and full tab-completion; sound keys tab-complete against every sound in the game. Changes apply to the active spider instantly and persist to the config file.
- **16 new config keys** (spawn safety, AI tuning, wander behavior, variant sounds). Existing config files upgrade in place — your tuned values are untouched.
- **Fixed:** on NeoForge/Forge, command-made config changes (including the old `/spider chasedistance`) were lost on restart; they now save to disk immediately.
- Requires: Fabric API + Fabric Language Kotlin (Fabric) / Kotlin for Forge (NeoForge & Forge), as before.

### Fabric 1.21.1 — new loader
- **Full native Fabric port for Minecraft 1.21.1.** Same spider, same simulation, same features and config as the NeoForge/Forge builds. Verified in-world.
- Requires **Fabric API** and **Fabric Language Kotlin**.
- Config system rebuilt on a bundled TOML backend so the commented, hot-reloading `arachnomod-common.toml` works identically on Fabric.

### v1.0.2
- **Smooth-animation overhaul.** Reworked how the BlockDisplay legs are rendered so the whole spider moves fluidly at any size and speed — no jitter, no snapping, no black-rendering as it crosses terrain.
- **Taming & riding added.** Creative-only **Spider Tamer** item: right-click to make the spider docile, then ride it with horse-like look-to-steer + W controls. (Our answer to "can I become the spider?" — see the disclaimer.)
- **50% netherite ingot death drop.**
- **Full configuration file** (`config/arachnomod-common.toml`) — every gameplay number, fully commented, hot-reloading.
- **Command suite:** `/spider newinstance`, `/spider size`, `/spider release`, `/spider chasedistance`.
- **Peaceful handling:** the spider despawns on Peaceful difficulty and natural spawns pause there.
- Size scaling raised so the far-away giant truly towers over the trees (max size 15).

### v1.0.0
- Initial release: the one-at-a-time hunting Netherite Octoarachnopod with procedural FABRIK-IK legs drawn via vanilla BlockDisplay entities, distance-based growth and charge speed, a 1000 HP boss fight with a 6-heart bite, natural respawning, and a spawn egg.

---

## 🙏 Credits & license

Created by **iR3DN4X**. Based on **[TheCymaera/minecraft-spider](https://github.com/TheCymaera/minecraft-spider)** by **Heledron** (YouTube @heledron).

### Supporters

- **[NetherySiloX](https://www.curseforge.com/members/netherysilox/projects)** — community contributor behind much of the v1.1.1 "Hunt Update": designed and prototyped the **safe spawning system** (no more void/water/mid-air spawns, SkyBlock-friendly ground scanning), the **wander → alert → chase AI** (the calm patrols, the freeze-and-stare moment, and the chase hysteresis that stopped the gait glitching at the detection edge), and the **server-side command/config system** that became `/spider config` — live in-game editing of every setting, including the every-sound-in-the-game step-sound picker. Thank you! 🕷️

### 📄 License

**Copyright © iR3DN4X.** ArachnoMod is **source-available freeware**: free to download and play, forever — but not open-source, and not free to re-use commercially. This license applies to **all versions and loaders** of the mod; the copy bundled with the latest release (`LICENSE.md` inside the jar) is the license in force.

ArachnoMod is a derivative of **[minecraft-spider](https://github.com/TheCymaera/minecraft-spider)** by **Heledron** (TheCymaera), used with attribution under its original terms; those portions remain his. All remaining content not belonging to Mojang/Microsoft or the above is the property of iR3DN4X.

**You MAY:**
- **Play the mod** in any context, including on servers of any size.
- Feature it in **videos, streams, and other media — monetized or not** (that is not "commercial use" of the mod's assets).
- Include it in **modpacks** with acknowledgment, distributed per CurseForge's terms.
- Create and publish **resource packs and language packs** for it.
- Create **add-on mods** that depend on, extend, or interact with this mod. Prior contact is appreciated, not required — I'd love to see what you make: **@iR3DN4X**.
- Create **non-commercial derivative works** with credit — **except ports** (below).

**You may NOT:**
- Re-use this mod's assets or code **commercially** anywhere else.
- **Port or re-release this mod** for other Minecraft versions, loaders, platforms, games, or media — official ports are made by me and planned ahead. If you want one, ask.
- **Rehost or redistribute the mod files** outside CurseForge. If you downloaded this jar anywhere else, it may be modified or malicious — the only official source is this CurseForge page.

For anything not covered here, ask: **@iR3DN4X**.
