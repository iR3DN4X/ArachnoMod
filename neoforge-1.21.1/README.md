# ArachnoMod — NeoForge 1.21.1 port of [TheCymaera/minecraft-spider](https://github.com/TheCymaera/minecraft-spider)

A faithful port of the procedurally-animated spider. Like the original Paper plugin, the spider is
**not a Minecraft entity** — it is a server-side simulation (FABRIK inverse-kinematics legs + a small
physics body) that is *drawn* using vanilla `BlockDisplay` entities. No client mod is required;
anyone connected to the server sees it.

## Build & run

This uses the **ModDevGradle** toolchain with **KotlinForForge** (so the mod is written in Kotlin,
matching the original).

1. Open the folder in IntelliJ IDEA (it will set up the Gradle wrapper automatically), **or** run
   `gradle wrapper` once if you have Gradle installed, then use `./gradlew`.
2. `./gradlew build` → the mod JAR lands in `build/libs/arachnomod-1.0.0.jar`.
3. `./gradlew runClient` → launches a dev client to test in singleplayer.

The JAR depends on **KotlinForForge** at runtime. For a dev client that's automatic. To hand the JAR
to someone else, they must also install KotlinForForge (or embed it via `jarJar`).

> The only things that go stale are the four version strings in `build.gradle` (NeoForge,
> ModDevGradle, the Kotlin plugin, KotlinForForge). If Gradle can't resolve one, bump it.

## How to use it in-game

- Get the **Spider** item: it's in the *Tools & Utilities* creative tab, or `/give @s arachnomod:spider`.
- **Right-click** with it to spawn the spider where you're looking; **right-click again** to remove it.
- The spider walks toward the nearest player (this is the stand-in for the original laser-pointer
  control — see "Deferred" below). Walk around and watch the legs solve.
- `/preset <name> [segment_count] [segment_length]` — swap the body. Names: `biped`, `quadruped`,
  `hexapod`, `octopod`, `quadbot`, `hexbot`, `octobot`. e.g. `/preset hexapod 3 1.2`.
- `/splay` — removes the spider (simplified; see below).

## What's in this foundation (faithful)

- The **ECS** (`ecs/Ecs.kt`) — ported essentially verbatim.
- The full **procedural animation**: `KinematicChain` FABRIK solver (`util/Maths.kt`), per-leg
  stepping / ground-scanning / gait decisions (`spider/Leg.kt`, `spider/Gait.kt`), and the body
  physics — gravity, drag, terrain-adaptive pitch/roll, support-polygon **normal force**, body-height
  correction (`spider/SpiderBody.kt`). These are line-for-line ports with Bukkit `Vector` → JOML
  `Vector3d`.
- **Walk + gallop** gaits, leg-update ordering, diagonal-pair cooldowns.
- All **presets** (`spider/Presets.kt`) with faithful leg geometry, using the procedural **line** leg model.
- Spawn/despawn **item**, `/preset`, terrain ground-raycasting, and **step/land sounds**.
- Server-side **BlockDisplay renderer** with a retained-mode tracker (`platform/Platform.kt`),
  the equivalent of the plugin's `RenderEntity`/`RenderEntityTracker`, plus crash-orphan cleanup.

## What's deferred (ports the same way — happy to do these next)

These were read and understood; they're left out of the first buildable slice to keep it correct:

- **Hand-authored BlockDisplay art** (the `FLAT`/`BOXY`/`STEALTH` torsos and mechanical leg segments).
  In the plugin these are huge `/summon ... {Passengers:[...]}` NBT blobs parsed by
  `parseModelFromCommand`. They map directly onto `BlockDisplayPiece` (block + `Matrix4f` + tags); the
  bot presets currently use a placeholder netherite torso.
- **Cloak** (active camouflage): block-colour matching via `block_colors.json` + Oklab nearest-block
  search, and the glitch animation.
- **Mountable** pig/armour-stand rider, **trident** hit-detection + knock-back, **water** sounds/particles.
- **Kinematic-chain visualiser**, the debug-graphics overlay, and the full `/items` gesture-item menu
  (laser pointer, come-here, toggle-leg shears, switch-gait, switch-renderer, etc.).
- `/options`, `/modify_model`, `/animated_palette`, `/torso_model`, `/leg_model`, `/scale`,
  `/set_sound` (most were already commented-out in the upstream source), and the full `/splay` scatter
  animation.

## Key Bukkit → NeoForge mappings used here

| Original (Bukkit/Paper)                         | This port (NeoForge 1.21.1, Mojang mappings)            |
|---|---|
| `org.bukkit.util.Vector`                        | `org.joml.Vector3d` (+ extension fns in `util/Maths.kt`)|
| `org.bukkit.entity.BlockDisplay`                | `net.minecraft.world.entity.Display.BlockDisplay`       |
| `World.spawn(...)` / `entity.remove()`          | `ServerLevel.addFreshEntity(...)` / `entity.discard()`  |
| `display.transformation = ...`                  | `display.setTransformation(Transformation(matrix4f))` *(AT)* |
| `display.block = blockData`                     | `(BlockDisplay).setBlockState(blockState)` *(AT)*       |
| `World.rayTraceBlocks(...)`                      | `ServerLevel.clip(ClipContext(..., CollisionContext.empty()))` |
| Scoreboard tag on display                       | `entity.addTag(...)` / `entity.getTags()`               |
| `plugin.yml` commands                           | `RegisterCommandsEvent` + Brigadier                     |
| `onEnable` / Bukkit scheduler tick              | `@Mod` + `ServerTickEvent.Post`                         |

The three private `Display` setters are exposed by `src/main/resources/META-INF/accesstransformer.cfg`.
