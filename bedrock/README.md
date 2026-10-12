# ArachnoMod — Bedrock Edition add-on

The Netherite Octoarachnopod for **Minecraft Bedrock 26.40+**, written on the stable Script API
(`@minecraft/server` 2.9.0, no experiments). **v1.2.7** matches the Java mod's v1.2.7: the netherite
and camo variants, health and the netherite-suit armour, the bite, the spawn manager and its pacing,
dimension following, the squeeze, water growth, the Spider Tamer and riding, idle grooming and
breathing, and `/spider` + `/spiderconfig`. It also has the Java mod's later chase AI (corner
steering, doorway hunting).

**Download:** the CurseForge page is the only official distribution. See the root
[`README.md`](../README.md) and [`LICENSE.md`](../LICENSE.md): the same source-available license
covers this add-on.

## Layout

| Path | What |
|---|---|
| `behavior_pack/` | The behavior pack: the entities (`arachno:spider`, the invisible mob with the hitbox; `arachno:leg`, one per leg), the Spider Tamer item, and the scripts |
| `behavior_pack/scripts/engine/` | The spider engine: a line-for-line JavaScript port of the Kotlin `spider/` engine (FABRIK chains, gait, legs, body, behaviours). It was verified identical to the Java engine on the same scenarios before any Bedrock-specific gait changes, each of which is switched off by `body.javaWalk` |
| `behavior_pack/scripts/` | `main.js` (tick loop, events), `spider.js` (SpiderMob), `ai.js` (SpiderAI), `ground.js` (SafeGroundFinder), `spawn.js` (SpiderSpawnManager), `mob.js` (sizing), `platform.js` (raycasts with Java semantics), `render.js` (legs), `camo.js` + generated `camo_table.js`, `config.js`, `commands.js` |
| `resource_pack/` | The leg model and its smoothing script, the camo render controller, the spawn egg, the Tamer's texture and the language file |
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
the packs in game while developing, copy `behavior_pack` and `resource_pack` into
`development_behavior_packs` and `development_resource_packs` under
`%APPDATA%\Minecraft Bedrock\Users\Shared\games\com.mojang\`, then fully restart Minecraft.
