# Bedrock port dev tools (not shipped)

Nothing here can run Bedrock itself; these let the pack's real code run in the Claude desktop app's
browser pane so a change can be checked before the user tests it in game.

- `serve.ps1` - tiny static server, started via the `bedrock-engine-sim` entry in
  `..\..\.claude\launch.json` (preview_start name `bedrock-engine-sim`, port 8765). Routes:
  `/` = `behavior_pack/scripts`, `/test/` = `devtools/simtest`, `/bp/` = `behavior_pack`,
  `/rp/` = `resource_pack`.
- `simtest/mcstub.js` - stand-in for `@minecraft/server` (mapped by an importmap): a flat world
  (top surface y = -60), entities that reject property writes outside the ranges in the pack's own
  `entities/leg.json`, script events, one scripted player. Its raycast reproduces what the real game
  was seen doing: a hit on a block's TOP face reports `faceLocation.y = 0`, not 1.
- `simtest/molang.js` - a small Molang interpreter (the subset the leg entity uses). It is STRICT like the
  game: reading a never-assigned variable stops the expression (counted in the replay's
  `molangErrors`, which must be 0). It used to read unset variables as 0, which hid the bug that
  made rounds 5-7 draw no legs in game.
- `simtest/client.html` - THE main check: runs the real `main.js`, then replays the real
  `pre_animation` + bone expressions from `resource_pack/` at 60 fps and reports segment gaps
  (standing and running), drawn legs vs the server's numbers once settled, per-frame motion steps
  incl. across re-parks, and a 54-block charge (peak size/speed). `?nosmooth=1` skips the smoothing
  script (= what the game draws if it fails: must still be gap-free), `?ox=N` runs N blocks from the
  origin, `?debug=1` dumps the leg state at the worst standing gap.
  The replay models Bedrock's NETWORK: an entity's properties and its position arrive in separate
  packets and a new entity first appears with default properties. `?net=props` (default) / `pos` /
  `atomic` picks the order; `blink.framesWithALegMissing` must be 0 in every order (teleporting a
  live leg entity fails this - it is the whole-spider blink seen in game in rounds 4 and 8).
  `visualLag.lagInTicks` (drawn legs behind the server, Java's 1-tick glide ~0.33) and
  `frozenLegFramesWhileRunning` (must be 0) measure how SNAPPY the legs look.
  `?burst=1` delivers every other tick late, together with the next one (uneven delivery, as an
  in-game video showed): the adaptive glide length must keep frozen frames near 0 there too.
  `blink.framesWithALegMissingBy12` must stay 0 everywhere (the 5-block one can trip on lag).
- `gen-camo.sh` (+ `camo-table.pl`) - GENERATES `behavior_pack/scripts/camo_table.js`, the camo
  variant's block table, from the INSTALLED GAME's own vanilla resource packs (every
  `data/resource_packs/vanilla*/blocks.json` + `textures/terrain_texture.json`, merged in version
  order): each block's top-face texture (biome-tinted grass/leaves -> their pre-coloured "carried"
  textures; see-through ones - glass, ice, slime - listed last from FIRST_TRANSLUCENT so the legs
  draw them blended) and its step/landing sound events (only events confirmed in Mojang's
  sound_definitions). 2026-10-11: 1344 blocks, 705 textures. Then runs gen-leg-rp.sh. Re-run after
  a game update. camo.js resolves renamed ids (ALIASES) and unknown slabs/stairs/walls/waxed blocks
  to their base block, else stone.
- `gen-leg-rp.sh` - GENERATES `resource_pack/render_controllers/leg.render_controllers.json` (the
  per-leg texture + material pick from camo_table.js), `resource_pack/entity/leg.entity.json` (the smoothing script) and
  `resource_pack/animations/leg.animation.json`. Edit the generator, never those two files.
- `simtest/ai.html` - THE AI check: runs the real `main.js` (engine + `ai.js` + sizing) in one
  obstacle scene - `?scene=open|wall|tree|pit|room|pillar|tunnel` - and reports whether the chase
  reaches the player (`reachedTick`), the longest stall, ticks with the body inside a block, the
  size range, where it crossed the obstacle, squeeze depth, passage/pin ticks, and native calls per
  tick. `?trace=a-b` dumps the AI state tick by tick. The stand-in world (`mcstub.js`) is now a
  voxel world: `__test.set/fill` place blocks, rays are real voxel traversals with Bedrock's
  far-face quirk, and `getBlock`/`getTopmostBlock` answer from the same blocks.
  Last results (2026-10-10, round 14): every scene reached the player (open 39, wall 60 - through
  the door, tree 24, pit 81 - squeezed 3.65 deep, room 63, pillar 52, tunnel 84 - through it,
  tunnel2 81 and tunnel3 79 - over the hill). Knobs: `?java=1`, `?ramp=`, `?emerg= ?eunc=
  ?eplant= ?forced=`, `?careful=0` (emergency steps even while lining up for an opening).
- `simtest/gait.html` - the full pack (main.js) chasing a player walking away at `?speed=` b/s
  (default 4.8, the user's clip): per-leg steps, foot SLIP while planted (dragging), stretch,
  body/hip bob, low-body ticks (`drops`), speed kicks. `?java=1` = Java's walk+support rules.
- `simtest/gaittrace.html` - engine-only twin of a temporary Java harness (GaitTrace.kt, removed):
  prints GAITTRACE lines that matched the real Java engine line for line (2026-10-10). Uses
  Java's rules (`javaWalk`) unless `?bedrock=1`.
- `simtest/chase.html` - the full pack chasing a player who walks away over `?terrain=dunes|flat`
  (dunes = rolling sand in 1-block steps, like the user's 4th clip) at `?speed=` b/s (4.3): the
  size the distance wants vs what it got and which cap held it, speed, how often the comfort gate
  slowed it, steps/s, planted-foot SLIP with a log of what caused each big one, fewest planted
  feet. `?java=1` = Java's rules; emergency-step knobs `?emerg= ?eunc= ?eplant= ?forced=`.
  Round 14 (2026-10-10), dunes at 4.3 b/s: worst slip 1.09 (Java) -> 0.07, steps/s 2.2 -> 3.1,
  comfort-slowed ticks 91% -> 62%, fewest planted feet 1 -> 3; at 5.6 b/s worst slip 1.53 -> 0.37.
  Round 32 added `?pattern=circle` (`?r=`) and `cross`, `?lifted=0` / `?comfort=` (the round-32
  knobs, also on ai.html), and the measures the debug view's colours stand for: `skating` (a foot
  moving sideways while ON the ground at both ends of a tick - planted, stepping, or a one-tick
  step the renderer draws as a hop) and `debugViewRed` (share of leg-ticks with a red zone, a red
  target, a foot in the air). Round 32 (flat walk / sprint / cross / stop-go / charge, dunes walk /
  zigzag / stop-go / sprint): visible sliding on flat 2.9-13.5 -> 0 b/s, dunes 5-7.6 -> 0.5-1.1;
  red legs flat walk 14% -> 7%, sprint unchanged ~21%. `client.html` reports the drawn `hop`.
- Bedrock DEVIATES from Java on purpose, every one behind `body.javaWalk` (the identity tools set
  it): behaviour.js walkAt ramp + body.js overhang support (round 13, `javaSupport` for the
  second); engine/gait.js EMERGENCY STEP + engine/leg.js forced step at the reach limit (round 14,
  off while the AI lines up for an opening or squeezes - `body.carefulGait` - and while the size
  changes >8%/tick - `body.sizeChanging`); ai.js squeeze needs the player walled in (round 14).
  See the comments there. The tunnel scenes' "through or over the hill" flips on ANY gait detail
  (Java's AI decides it on centimetres of arrival height); all of them must still REACH.
- `simtest/mobsuite.html` - THE MOB CHECK (round 16): runs every `mob.html?case=` in its own
  iframe and prints PASS/FAIL - spawn (first spawn ~1 min, 30-34 blocks, legs, variant health,
  hitbox on the body), chasebite (6-heart bite every 60 ticks, only in reach), armour (netherite
  suit soaks 7 -> 1.79, no lava/fall damage; camo full damage, burns, flames), kill (trophy on the
  floor under the body, legs gone, 40-min cooldown that Peaceful can't shorten), peaceful (removed,
  paused, back 1 min after), tame (Spider Tamer, no bites, ride: riddenSize, forward/strafe,
  re-seat, sneak off, the data-driven tame path), camo (legs take the block underfoot, its own
  step sounds), onlyone (a second replaces the first; a leftover from an old save is removed),
  lost (an unloaded spider is replaced near you at once, no cooldown), commands (/spider and
  /spiderconfig through the registered callbacks), groom (idle grooming + breathing).
  Round 30 (v1.8.5 catch-up D) added: ward (Warding Idol: arming countdown, spacing refusal +
  hand-back, no spawns while warded, the 1-block bite band and the fence, REPEL out of a zone,
  the enraged boss shattering it, wardEnabled), laser (creative-only, LURE pulls it off a chase,
  no bites meanwhile, a dot inside a ward stops at the fence), debug (`/spider debug`: ops only,
  one box per Java part at the engine's own values across re-parks, Java's placements checked
  directly, then a 60 fps replay of the REAL `debug.entity.json`/`debug.animation.json` - every
  corner exact once the glide lands, motion glides, no Molang errors - giant and personal spiders,
  orphan sweep, pack files agree with `debug.js`).
  Round 33 (Java v1.8.6 mirrored) added: creative (creative/spectator players not hunted, bitten
  or blinded, size still follows them, a tamed spider follows its creative owner but not a
  spectator) and pet (the owner's creative punch -> FIGHT -> the mob dies; no orders in survival,
  from another player, while ridden, against players or its own legs, or to an untamed spider; the
  poison pet lunges); netherroof gained the spectator/creative rescue checks. The stub gained
  `__test.punch(target, attacker)` (entityHitEntity) and vanilla mob health (pig, cow, zombie).
  Round 31 (catch-up E) added: menu (the Spider Config Book's settings menu, `menu.js`: Java's 8
  categories cover every setting once, first-join book once ever and only for editors, every page
  and button, save/refuse/empty-box reset/reset page/untouched page, spawn timer re-arm, rounding,
  Find a setting, Reset ALL with its confirm, `/spider menu` retried while the chat screen closes,
  a guest's read-only view, giveConfigBook). Forms come from `simtest/mcuistub.js`, the stand-in for
  `@minecraft/server-ui` (every test page's import map points there): the open form is
  `ui.open`; answer it with `ui.click(i | "button text")`, `ui.submit({ "Label start": value })`,
  `ui.close()`, then `await ui.settle()`; `ui.busy = N` makes the next N shows come back
  UserBusy; misuse lands in `ui.errors`.
  2026-10-11 (round 17, camo): 82 passed, 0 failed. The stub (`mcstub.js`) now models: entity health + damage with
  the entityHurt before-event, deferred after-events (spawn/load/die/remove; a slain mob stays
  valid 20 ticks), system.run, rideable/riding, onfire, items, difficulty, input, game mode, and
  captures custom-command registrations with Bedrock's naming rules (`__test.command(...)`).
- `simtest/sweep.html?configs=a|b&runs=x|y` - runs chase.html for every config x scenario, each
  in its own iframe (fresh pack), and totals red drag readouts / worst slip / follow distance /
  comfort-slowed share / steps / fewest planted feet. Default runs: 5 dune/flat walk, stop-go and
  zig-zag scenarios. Round 15 (2026-10-11): default 6/270 red, worst 0.31, follows 1.94 blocks
  behind; Java (`java=1`) 116/270, worst 1.53, 3.0 behind. chase.html also takes `?pattern=
  stopgo|zigzag`, `?floor=` (speed while a leg is strained), `?slide=` (0 = Java's foot slide on
  every size change), `?reach=` (emergency-step reach limit).
- Round 32 deviations: `engine/gait.js STEP.stayLifted` (a stepping foot stays up until it lands
  instead of chasing its moving look-ahead target along the ground; off under `javaWalk` and
  `carefulGait`), `EMERGENCY.comfort = 0.7` (a strained leg may emergency-step at 70% of its
  comfort zone, before it turns red), and render.js `stepHop` + the leg client's `v.hy`: a step the
  engine begins and lands inside one tick is DRAWN as a hop (the engine's own path is untouched -
  making such steps last two ticks flipped the tunnel scene from through to over the hill).
- Round 15 deviations (all off under `javaWalk`): COMFORT.floor 0.9 (behaviour.js; Java 0.6 - only
  where emergency steps are on), RESCALE.slideFeetAbove 0.08 (body.js; feet stay put on small size
  changes), the leg.js forced step starts NEXT tick and does not ride along with the body that
  tick (it used to sweep a foot 0.9 blocks along the ground in one tick), EMERGENCY.reachable 0.94.
- The in-game developer status line (OFF since round 16; `/spiderconfig debughud true` shows it) ends with `steps N/s  drag D`: D = the furthest a PLANTED foot moved
  in one tick over the last half second, not counting fast size changes (red above 0.25 x size,
  0.15 at the smallest) - the user's "dragging feet" in one
  screenshot. Java's rules on the dune scene go red in 24 of 60 updates; round 14 reads 0.00.
- `simtest/quirks.html` - runs two scenarios that were verified IDENTICAL to the Java engine (a
  straight walk and a 180-degree turn) through the real `platform.js` over fake raycasts with each
  Bedrock edge quirk; every mode must print `MATCHES JAVA`.

- `version.sh` - prints the version, or stops if the two manifests disagree anywhere: header, every
  module, the BP's dependency on the RP, and the `vX.Y.Z` in both pack NAMES (the name is the only
  place the game's pack list shows which copy is which). `package.sh` and `deploy.sh` both run it.
- `package.sh [output dir]` - builds the release `NetheriteOctoarachnopod-Bedrock-<version>.mcaddon`
  (both packs) and diffs the unpacked file against the source. With no argument, every build goes
  into a NEW folder `BEDROCK_ADDON_STAGED/v<version>_<date>_<time>` next to the project, with a
  `BUILD_INFO.txt` (size, SHA-1). It refuses to overwrite a staged `.mcaddon`. v1.2.7 (2026-10-11)
  was the first public one; it had no version in its pack names (added in 1.3.0).
- `deploy.sh` - installs both packs as the game's DEVELOPMENT packs in versioned folders
  (`development_behavior_packs\arachnomod_bp_v<version>`, `development_resource_packs\arachnomod_rp_v<version>`),
  after removing every older `arachnomod_bp*` / `arachnomod_rp*` dev folder, then diffs the copies
  against the source. Stops if the game is running. Fully quit Minecraft first and relaunch it
  after. A world applies packs by UUID + version, so after a version bump re-add the pack to the
  test world (or start a new one).
