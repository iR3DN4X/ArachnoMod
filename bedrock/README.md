# ArachnoMod — Bedrock Edition add-on

The Netherite Octoarachnopod for **Minecraft Bedrock 26.40+**, written on the stable Script API
(`@minecraft/server` 2.9.0 and `@minecraft/server-ui` 2.1.0, no experiments). Since **v1.8.6** the
add-on carries the Java mod's version numbers, and v1.8.6 matches Java v1.8.6: all four variants and
the enraged boss, permadeath, the Warding Idol, the Spider Laser Pointer, the Spider Config Book
(75 settings, saved in the world), Cymaera's debug view (`/spider debug`), the creative-mode pet
fight, and the Nether rescue. Only the advancements are left out: Bedrock add-ons can't add them.

**Download:** the CurseForge page is the only official distribution. See the root
[`README.md`](../README.md) and [`THENETHERITEOCTOARACHNOPODMODNETHERITESPIDERLICENSE.md`](../THENETHERITEOCTOARACHNOPODMODNETHERITESPIDERLICENSE.md): the same source-available license
covers this add-on.

## Layout

| Path | What |
|---|---|
| `behavior_pack/` | The behavior pack: the entities (`arachno:spider`, the invisible mob with the hitbox; `arachno:leg`, one per leg; `arachno:debug`, one per debug-view part), the Warding Idol block and its recipe, the items (spawn egg, Spider Tamer, Spider Laser Pointer, Spider Config Book), and the scripts |
| `behavior_pack/scripts/engine/` | The spider engine: a line-for-line JavaScript port of the Kotlin `spider/` engine (FABRIK chains, gait, legs, body, behaviours). It was verified identical to the Java engine on the same scenarios before any Bedrock-specific gait changes, each of which is switched off by `body.javaWalk` |
| `behavior_pack/scripts/` | `main.js` (tick loop, events), `spider.js` (SpiderMob), `ai.js` (SpiderAI), `ground.js` (SafeGroundFinder), `spawn.js` (SpiderSpawnManager), `mob.js` (sizing), `platform.js` (raycasts with Java semantics), `render.js` (legs), `camo.js` + generated `camo_table.js`, `config.js`, `commands.js`, `wards.js` (WardRegistry), `menu.js` (the config book's menus), `debug.js` (the `/spider debug` parts) |
| `resource_pack/` | The leg model and its smoothing script, the camo render controller, the debug-part and Warding Idol models, the item textures and the language file |
| `devtools/` | Browser test pages that run the real scripts over a stand-in `@minecraft/server`, plus the generators and the packaging script. See [`devtools/README.md`](devtools/README.md) |
| `CURSEFORGE_LISTING.md` | The add-on's CurseForge description |

Three files are **generated**; edit their generators, not the files:

- `behavior_pack/scripts/camo_table.js`: from the installed game's own vanilla resource packs, by
  `devtools/gen-camo.sh` (re-run it after a game update to pick up new blocks).
- `resource_pack/entity/leg.entity.json`, `resource_pack/animations/leg.animation.json` and
  `resource_pack/render_controllers/leg.render_controllers.json`: by `devtools/gen-leg-rp.sh`.

## Building a release

There is nothing to compile. `bash devtools/package.sh <output dir>` zips both packs into
`NetheriteOctoarachnopod-Bedrock-<version>.mcaddon` and checks the result against the source. To try
the packs in game while developing, quit Minecraft and run `bash devtools/deploy.sh`: it installs
both packs as development packs under
`%APPDATA%\Minecraft Bedrock\Users\Shared\games\com.mojang\` and checks the copies against the
source. Then start Minecraft again.
