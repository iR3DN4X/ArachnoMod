# ArachnoMod — the Netherite Octoarachnopod 🕷️

**A giant, procedurally-animated spider that hunts you across the world.** One roams at a time.
It patrols, spots you, freezes to stare you down — then charges, towering over the trees and
shrinking as it closes in for the bite. Its eight legs are driven by real **FABRIK
inverse-kinematics**, drawn entirely with vanilla **BlockDisplay** entities, so it moves like
nothing else in Minecraft. Since v1.1.1 it may spawn as the **CAMO variant**: active camouflage
that repaints each leg as the block it walks on, with block-true footsteps.

> ⚠️ **Arachnophobia warning** — the movement is realistic on purpose. See the CurseForge page.

**Download:** the only official distribution is the **CurseForge page** — jars found anywhere
else may be modified or malicious.

## Repository layout

This is a monorepo: one folder per loader, each a self-contained Gradle project sharing the same
Kotlin engine sources.

| Folder | Loader | Minecraft | Java | Toolchain |
|---|---|---|---|---|
| [`fabric-1.21.1/`](fabric-1.21.1/) | Fabric | 1.21.1 | 21 | Fabric Loom, Mojang mappings |
| [`neoforge-1.21.1/`](neoforge-1.21.1/) | NeoForge | 1.21.1 | 21 | ModDevGradle |
| [`forge-1.20.1/`](forge-1.20.1/) | Forge (loads on NeoForge 1.20.1 too) | 1.20.1 | 17 | ForgeGradle 6, official mappings |

## Building

Each project builds independently with Gradle (no wrapper script is committed; use Gradle
8.10.2 for the 1.21.1 projects, 8.8 for 1.20.1):

```
# from the repo root — pick the project you want
gradle -p fabric-1.21.1   build   # requires JDK 21
gradle -p neoforge-1.21.1 build   # requires JDK 21
gradle -p forge-1.20.1    build   # requires JDK 17
```

Jars land in each project's `build/libs/`. `runClient` in any project starts a dev client.

**Runtime dependencies:** Fabric build needs Fabric API + Fabric Language Kotlin; the
NeoForge/Forge builds need Kotlin for Forge.

## License — source-available, NOT open-source

This code is published for transparency, learning, and add-on development. It remains under a
**source-available custom license**: free to play, free to read — but **no commercial reuse, no
ports/re-releases, and no rehosting of the mod files**. Add-on mods, resource/language packs,
modpacks (with acknowledgment), and monetized videos are all welcome. Read
[`LICENSE.md`](LICENSE.md) for the exact terms before reusing anything.

## Credits

- **[Heledron](https://github.com/TheCymaera)** (TheCymaera) — the original
  [minecraft-spider](https://github.com/TheCymaera/minecraft-spider) plugin this mod derives
  from; the FABRIK spider engine concept is his work, used with attribution.
- **[NetherySiloX](https://www.curseforge.com/members/netherysilox/projects)** — community
  contributor: safe-spawning design, the wander/alert/chase AI, and the in-game command/config
  system that became `/spider config`.
- **iR3DN4X** — mod author: the ports (NeoForge/Forge/Fabric), taming & riding, the size-shifting
  hunt behaviour, the camo variant's active camouflage, smooth-animation work, and everything
  else.
