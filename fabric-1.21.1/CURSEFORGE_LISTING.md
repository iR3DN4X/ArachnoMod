# ArachnoMod — the Netherite Octoarachnopod

**A giant, procedurally-animated spider that hunts you across the world.** One roams at a time, returning two Minecraft days after each defeat. It towers over the trees and charges in at incredible speed the farther away you are, then shrinks down as it closes for a **6-heart bite**. Its eight legs are driven by real **FABRIK inverse-kinematics**, drawn entirely with vanilla **BlockDisplay** entities — so it walks, scurries, and clambers like nothing else in Minecraft.

A boss-grade fight either way: the **netherite hunter packs 350 HP inside the stats of a full netherite armor suit**; the mossy **camo stalker runs 600 HP with no armor at all**. Kill it and it *might* just drop the netherite it's made of.

---

## ⚠️🕷️ ARACHNOPHOBIA WARNING

**This mod is not kind to arachnophobes — please read before installing.** The spider in this mod is not a blocky vanilla mob: its eight legs are animated by a **live inverse-kinematics simulation**, so it moves, scurries, and plants each foot with unsettlingly **lifelike, realistic spider motion** — the exact kind of movement arachnophobia responds to. It also:

- **hunts you** — it spots you from far away, stares you down, and charges;
- is **enormous** — up to ~16 blocks tall, towering over the trees as it closes in;
- can be **camouflaged** — the CAMO variant blends into the terrain and sounds like ordinary footsteps, so it can get *very* close before you notice it.

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
- **Wander / Alert / Chase AI.** When no one's around, the spider calmly **patrols** its territory — and it **pre-scans every patrol route block-by-block** before walking it, planning around lava, water, chasms, and cliff edges instead of tumbling into them. The instant it spots you it **freezes and snaps to face you** — one heart-stopping beat — then **charges**. It keeps chasing until you truly escape (wider give-up radius than its detection radius, so no flickering at the boundary). Optional `hostileOnlyAtNight` mode makes it docile in daylight like a vanilla spider.
- **The hunt has pacing.** The first spider arrives **about a minute into a session** — no long wait for the main event. **Slay it and you've earned two full Minecraft days (40 min) of real peace** — no respawn treadmill. And if you hide in Peaceful? It returns **one minute after peace ends**. Peace has a price. 😈 (All three timers configurable.)
- **Idle life.** Disable wandering and the spider becomes a living statue instead of a frozen one: it **breathes** — a slow body-bob that scales with its size — and occasionally **grooms**, lifting its front legs to its mouth and cleaning them with a smooth sweeping rub while leaning down. Yes, the murder kaiju is, briefly, adorable.
- **The CAMO variant.** A second spider that can naturally spawn (25% by default, still only ONE spider ever) with **ACTIVE CAMOUFLAGE**: every leg continuously repaints itself as **the actual block it's standing on** — grass legs in the meadow, sand legs on the beach, stone legs in the mountains, changing leg-by-leg as it walks. Its footsteps play **the real step sound of the block underfoot**, exactly like player footsteps — it crunches on gravel, thuds on dirt, and goes whisper-quiet on wool. You won't see it coming. You might not hear it either.
- **The POISON variant.** ☠️ A third spider (20% of spawns by default) sheathed in the eerie blue-teal of **warped wart**, its footsteps squishing like the Nether fungus it's made of. It fights like a **real tarantula**: in striking range it **rears up, front legs raised** — that's your only warning — then **LUNGES**, hurling its whole body at you. The bite is gentler than its cousins' (3 hearts) but injects **Poison II for 30 seconds**. 500 HP and no armor… but with the venom ticking, the clock is yours to worry about.
- **Safe spawning.** Spawns (and patrol routes) are validated for solid, dry ground — no more spiders in the void, over oceans, or inside SkyBlock gaps. Keeps the configured spawn distance whenever possible, preferring farther over closer; never pops up in your face.
- **Procedural 8-leg animation.** No canned animations — every step is solved live with FABRIK IK and rendered through vanilla BlockDisplays. Buttery-smooth motion.
- **Grows with distance.** Far away it's a **towering giant** (up to ~16 blocks tall); as it closes in it **shrinks down** to bite you. The feet stay planted at every size.
- **Charges faster the farther it is** — up to **8× speed** — so distance is no safety.
- **Boss-grade fight.** A 6-heart melee hit either way. The **netherite variant**: 350 HP wrapped in the **exact stats of a full netherite armor suit** (20 armor, 12 toughness) — it shrugs off most of every blow. The **camo variant**: 600 HP, no armor — softer, but you have to find it first. Bring friends.
- **Netherite trophy drop.** A 50% chance to drop a netherite ingot on death (it *is* made of the stuff) — the ingot lands **on the ground directly beneath the spider**, at any height or depth, so your prize is always where you'd look.
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
| `/spider config <key> get` | Shows any config value in chat. | Ops (permission level 2) |
| `/spider config <key> set <value>` | **Live-edits ANY of the 45 config values in-game** — typed, range-checked arguments with full tab-completion (sound keys tab-complete against every sound in the game). Applies to the active spider instantly and saves straight into the config file. | Ops (permission level 2) |

---

## ⚙️ Configuration

All tunables live in **`config/arachnomod-common.toml`**, created on first launch. It's **fully commented** and **hot-reloads** — edit the file and most changes apply to a running world within moments, no restart needed. (Spawn/gait values are read when a spider spawns, so let the current one respawn to apply those.) Every key is also live-editable in-game with `/spider config <key> set <value>`.

**Spawning**

| Key | Default | Meaning |
|---|---|---|
| `spawnMinMinutes` | 1.0 | Min minutes before the FIRST spider of a session spawns |
| `spawnMaxMinutes` | 1.0 | Max minutes before the FIRST spawn (random between the two) |
| `respawnAfterKillMinutes` | 40.0 | Cooldown after the spider is KILLED (40 = 2 Minecraft days of earned peace) |
| `peacefulExitSpawnMinutes` | 1.0 | The spider returns this many minutes after Peaceful is switched off 😈 |
| `spawnDistanceMin` | 30.0 | Closest a spider naturally spawns from a player (blocks) |
| `spawnDistanceMax` | 34.0 | Farthest a spider naturally spawns from a player (blocks) |
| `spawnAngleAttempts` | 24 | Directions tested per candidate distance when hunting safe spawn ground |
| `spawnMaxVerticalSearch` | 48 | How deep below the heightmap to look for solid ground (SkyBlock-friendly) |
| `camoVariantChance` | 0.25 | Chance a spawned spider is the CAMO variant (natural spawns AND spawn eggs) |
| `poisonVariantChance` | 0.2 | Chance a spawned spider is the POISON variant (rolled before the camo chance) |

**Chase & speed**

| Key | Default | Meaning |
|---|---|---|
| `chaseDistance` | 64.0 | How far the spider spots & chases players (blocks) |
| `chaseExitDistanceMultiplier` | 1.25 | Give-up radius = chaseDistance × this (prevents boundary flicker) |
| `alertReactionTicks` | 10 | The freeze-and-stare beat before it charges (0 = instant charge) |
| `hostileOnlyAtNight` | false | Only hunts at night, like a vanilla spider |
| `chaseSpeedBlocksPerSecond` | 8.0 | Top chase speed at normal size |
| `speedGrowthFactor` | 8.0 | Speed multiplier at maximum size |
| `legStepSpeed` | 1.1 | How fast the legs swing — the "scurry" |

**Wandering & idle**

| Key | Default | Meaning |
|---|---|---|
| `enableWandering` | true | Calm patrol when no player is in range (false = stands still) |
| `wanderSpeedFactor` | 0.35 | Patrol speed as a fraction of chase speed |
| `wanderRadius` | 24.0 | How far it patrols from its anchor point |
| `wanderMinIntervalSeconds` | 3.0 | Shortest commitment to one patrol heading |
| `wanderMaxIntervalSeconds` | 9.0 | Longest commitment to one patrol heading |
| `wanderPauseChance` | 0.25 | Chance it pauses instead of picking a new heading |
| `groomingChance` | 0.03 | Chance per second that the idle spider grooms (wandering disabled; 0 = off) |

**Size & growth**

| Key | Default | Meaning |
|---|---|---|
| `minSize` | 0.6 | Size right next to a player |
| `squeezeSize` | 0.25 | The size it SQUEEZES down to over a player hiding in a hole — 0.25 just fits a 1×1×1 |
| `maxSize` | 15.0 | Size when far away (15 towers over the trees) |
| `sizeNearDistance` | 4.0 | At/below this distance the spider is at minSize |
| `sizeFarDistance` | 32.0 | At/above this distance the spider is at maxSize |
| `growPercentPerTick` | 12.0 | Fastest it can grow (%/tick) |
| `shrinkPercentPerTick` | 25.0 | Fastest it can shrink (%/tick) |
| `riddenSize` | 2.0 | Stable size while a player is riding it |
| `growInWater` | true | In water, grow just big enough to ride above the surface instead of drowning (false = deep water stays a drowning trap) |

**Combat & drops**

| Key | Default | Meaning |
|---|---|---|
| `netheriteMaxHealth` | 350.0 | Max health of the netherite variant (it also wears full netherite-suit armor stats) |
| `camoMaxHealth` | 600.0 | Max health of the camo variant (no armor) |
| `poisonMaxHealth` | 500.0 | Max health of the poison variant (no armor — its danger is the bite) |
| `attackDamageHearts` | 6.0 | Melee damage in hearts per hit |
| `poisonAttackDamageHearts` | 3.0 | The poison variant's bite damage in hearts (every bite also injects Poison II) |
| `poisonEffectSeconds` | 30.0 | How long the Poison II from the poison variant's bite lasts |
| `attackCooldownTicks` | 20 | Ticks between melee hits (20 = 1/sec) |
| `netheriteDropChance` | 0.5 | Chance to drop a netherite ingot on death |

**Variant sounds** *(the netherite spider always keeps its iconic clank; camo plays the real sound of the block underfoot)*

| Key | Default | Meaning |
|---|---|---|
| `variantStepSound` | `block.moss.step` | Fallback step sound for non-netherite variants |
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

### v1.3.1 — "The Venom Update" — all three loaders
- **NEW: the POISON variant.** ☠️ A third spider — sheathed in the eerie blue-teal of **warped wart block**, footsteps squishing like the Nether fungus it's made of, 20% of spawns by default (`poisonVariantChance`; still only ever ONE spider). And it fights like a **real tarantula**: in striking range it **rears up with its front legs lifted high** — exactly the way a real tarantula telegraphs — then **LUNGES**, hurling itself at you, front legs still raised. Only a bite landed out of that lunge connects: **3 hearts** (`poisonAttackDamageHearts`) plus **Poison II for 30 seconds** (`poisonEffectSeconds`). 500 HP, no armor (`poisonMaxHealth`). Everything else — the netherite trophy, the scurry, distance sizing, the squeeze, water growth — works exactly like its cousins.

### v1.2.7 — all three loaders
- **The great health rebalance: no more 1000 flat HP.** The two variants now fight very differently. The **Netherite variant drops to 350 HP but wears what it's made of — the exact stats of a FULL suit of netherite armor** (20 armor, 12 toughness, knockback resistance): it shrugs off most of every sword blow, but armor-piercing weapons finally have a real target. The **Camo variant keeps 600 HP with no armor** — much easier to put down… if you can find it. 🌿 Old `maxHealth` configs migrate automatically into the new per-variant `netheriteMaxHealth` / `camoMaxHealth` keys (a customized value carries over; defaults upgrade).

### v1.2.5 — all three loaders
- **The spider no longer drowns while you laugh at it from a boat.** 🌊 Distance-based sizing kept it *small* right when it waded in after you — so it sank to the lake floor and drowned while you floated above. Now, standing in water of any depth, it **grows just big enough for its body to ride above the surface** and keeps coming. Works in ponds, rivers, lakes and deep oceans (it will exceed `maxSize` if the ocean demands it). New config toggle **`growInWater`** (default **true**); set it to `false` if you *want* deep water to stay a drowning trap.

### v1.2.4 — all three loaders
- **Lava now treats the two variants very differently.** 🔥 The **Netherite** spider is forged of the stuff — it now takes **no fire damage of any kind** (lava, magma blocks, campfires) and never catches fire: luring it into a lava lake just means it keeps coming, wading through the melt. The **Camo** variant is living moss and undergrowth — it **burns, no matter what**, and you'll see it: the body and legs catch visible flame and smoke as it cooks.

### v1.2.3 — all three loaders
- **THE SQUEEZE now actually descends into the hole.** In v1.2.2 the spider shrank correctly but hovered at the lip — its legs stood on the rim around your hole and held the body up there forever. Now, once it has shrunk small enough to fit, the body **sinks down the shaft after you** (pressing flat against the ground while it lines itself up, then pouring in over the lip) until the bite is on top of you. Also, the squeeze now only triggers when you're genuinely *below* the spider — shrinking at the base of your pillar never helped it reach you anyway.

### v1.2.2 — all three loaders
- **NEW: THE SQUEEZE.** 🕷️ Hiding in a 1×1×1 hole is no longer an escape. When the spider is pressing directly over a dug-in player, it now **shrinks below its normal minimum size — just small enough to fit into your hole** — slips in after you, and bites. It regrows the instant the squeeze is over. New config key `squeezeSize` (default 0.25; raise it toward 1.0 if you want hidey-holes to stay viable).

### v1.2.1 — all three loaders
- **Digging in no longer makes the spider give up.** Hiding in a pit (or pillaring up) used to leave it standing at the rim, watching passively — even after you opened an escape path. Now, whenever you're vertically out of its reach, it applies **constant pressure toward your exact position, re-evaluating every tick** — the instant a block breaks or a ramp opens, it pours through the new opening. Dig wide and it climbs down in after you. Your first mistake is its opportunity. 🕷️

### v1.2.0 — "The Dimension Update" — all three loaders
- **Fixed (gamebreaking): dimension travel no longer leaves piles of frozen spider "corpses" behind.** The spider's leg displays could get saved into chunks when you portalled away; returning showed dozens of frozen spiders stacked at one spot. Displays are now never saved — **and a janitor sweep automatically deletes the leftover frozen piles from worlds affected by older versions** as you revisit those areas. Your infested world heals itself.
- **NEW: the spider follows you across dimensions.** Leave it behind in the overworld and a few seconds later it re-emerges near you in the Nether, the End — **or any modded dimension** (Twilight Forest, Dimensional Doors, anything): the follow triggers on where the players are, not on which portal was used. There is still only ever ONE spider.
- **Fixed: spawning and wandering now work correctly under the Nether roof** (and any ceiling'd modded dimension) — the ground search works around the player's altitude instead of hitting the bedrock ceiling.

### v1.1.9 — all three loaders
- **Fixed: the netherite trophy now drops right AT the spider, not several blocks behind it.** The drop was placed at the spider's (invisible) hitbox, which trails the animated body by one tick — negligible normally, but for a fast or giant spider (e.g. one one-shot mid-charge) that could put the ingot several blocks behind where you saw it die. It now drops at the exact simulation body position, always right under the spider.

### v1.1.8 — all three loaders
- **Fixed: the netherite trophy now drops no matter HOW the spider dies.** Some modded "kill anything" weapons (Avaritia-style endgame swords and similar) slay mobs by zeroing their health directly, bypassing the normal death event — which silently skipped the trophy. The drop now fires on **any death that leaves the spider at 0 HP**, regardless of the weapon or mechanism, with a guard so it can never drop twice. Verified against a direct health-zeroing kill that skips the normal death path entirely. Despawns (Peaceful, chunk-unload, replacing the spider with a new egg) still intentionally drop nothing — the spider is still alive when those remove it. *(This is why the drop looked "broken" only in modpacks with instant-kill weapons; vanilla kills were never affected.)*

### v1.1.6 — all three loaders
- **Fixed: updating from an older version now actually delivers the new spawn pacing.** Config defaults only apply to freshly generated files, so existing installs silently kept the old 5–30 minute first spawn ("the spider never shows up"). The config now **migrates itself once**: any value still at its old default is upgraded (`spawnMinMinutes` 5→1, `spawnMaxMinutes` 30→1, `spawnAngleAttempts` 12→24) — **values you customized yourself are never touched.** No action needed; just update and play.

### v1.1.5 — "The Hunt Has Pacing" — all three loaders
*This release bundles v1.1.2–v1.1.4 (listed below) — if you're updating from v1.1.1, everything from here down to the v1.1.1 entry is new for you.*
- **The hunt begins one minute in.** The first spider of a session now spawns after ~1 minute (was 5–30) — new players meet the mod immediately. (`spawnMinMinutes`/`spawnMaxMinutes`, both default 1, raise them if you prefer slow-burn suspense.)
- **Slaying the spider buys you real peace: the next one comes 40 minutes later — two full Minecraft days** (new `respawnAfterKillMinutes`, default 40). No respawn treadmill; killing it means something.
- **Turning off Peaceful is punished promptly 😈** — the spider returns 1 minute after peace ends (`peacefulExitSpawnMinutes`). But toggling Peaceful can **not** shortcut the post-kill cooldown — a slain spider stays gone for its full two days.

### v1.1.4 — all three loaders
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
