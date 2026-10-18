# ArachnoMod — the Netherite Octoarachnopod 🕷️

**A giant, procedurally-animated spider that hunts you across the world.** One roams at a time.
It patrols, spots you, freezes to stare you down — then charges, towering over the trees and
shrinking as it closes in for the bite. Its legs are driven by real **FABRIK inverse-kinematics**,
drawn entirely with vanilla **BlockDisplay** entities, so it moves like nothing else in Minecraft.

It hunts in **four variants**: the armoured **netherite** classic; the **camo** chameleon, which
repaints each leg as the block it stands on and steps with that block's own sound; the **poison**
tarantula, which rears up and lunges to inject Poison II; and the pitch-black **hunter**, which
moves only while nobody is looking at it and blinds anyone who gets close. Feed the netherite one a
**netherite ingot** and it enrages into a boss with a real boss bar — and a guaranteed netherite
block if you can put it down.

It threads your doorways and corridors, squeezes into a 1×1 hole to reach a player who dug in,
grows in deep water, follows you between dimensions, and — with `permadeath` on — never returns
once you finally kill it. **Seven advancements** track the hunt. Every number is tunable from
`config/arachnomod-common.toml` or live in-game via `/spider config`, including the netherite
spider's armour and its leg count (1–16, default 8).

**Bedrock Edition:** the spider is also a Bedrock add-on (Minecraft Bedrock 26.40+, no experiments),
in [`bedrock/`](bedrock/). Bedrock v1.2.7 matches the Java mod's v1.2.7: the netherite and camo
variants, taming and riding, and `/spider` + `/spiderconfig`.

> ⚠️ **Arachnophobia warning** — the movement is realistic on purpose. See the CurseForge page.

**Download:** the only official distribution is the **CurseForge page** — jars found anywhere
else may be modified or malicious.

## Repository layout

This is a monorepo: one folder per loader, each a self-contained Gradle project sharing the same
Kotlin engine sources, plus the Bedrock add-on.

| Folder | Loader | Minecraft | Java | Toolchain |
|---|---|---|---|---|
| [`fabric-26.1.2/`](fabric-26.1.2/) | Fabric (Quilt Loader runs this jar too) | 26.1.2 | 25 | Loom 1.16.3, Gradle 9.4 |
| [`neoforge-26.1.2/`](neoforge-26.1.2/) | NeoForge 26.1.2.94 | 26.1.2 | 25 | ModDevGradle 2.0.143, Gradle 9.4 |
| [`fabric-1.21.11/`](fabric-1.21.11/) | Fabric (Quilt Loader runs this jar too) | 1.21.11 | 21 | Loom 1.16.3 (remapping), Mojang mappings, Gradle 9.4 |
| [`fabric-1.21.1/`](fabric-1.21.1/) | Fabric | 1.21.1 | 21 | Fabric Loom, Mojang mappings |
| [`neoforge-1.21.1/`](neoforge-1.21.1/) | NeoForge | 1.21.1 | 21 | ModDevGradle |
| [`forge-1.20.1/`](forge-1.20.1/) | Forge (loads on NeoForge 1.20.1 too) | 1.20.1 | 17 | ForgeGradle 6, official mappings |
| [`fabric-1.20.1/`](fabric-1.20.1/) | Fabric | 1.20.1 | 17 | Fabric Loom, Mojang mappings |
| [`bedrock/`](bedrock/) | Bedrock add-on (behavior + resource pack) | Bedrock 26.40+ | — | Script API `@minecraft/server` 2.9.0 (JavaScript); nothing to compile |

**On the 26.x builds:** Minecraft 26.1 is the first *unobfuscated* release, so those two projects carry no mappings declaration at all, use the `net.fabricmc.fabric-loom` / ModDevGradle plugins without remapping, and require **Java 25 — including for the Gradle JVM itself**. There is no Forge build for 26.x: MinecraftForge 26.1.2 exists, but Kotlin for Forge, which this mod needs, does not support it; Quilt needs no separate build because Quilt Loader runs the Fabric jar (Quilt's own standard libraries were discontinued in December 2025).

**On the 1.21.11 build:** 1.21.11 is the last *obfuscated* release, so `fabric-1.21.11/` shares the 26.1.2 Fabric sources almost line for line (five small API differences) but goes back to the remapping toolchain: the `fabric-loom` plugin with `loom.officialMojangMappings()`, `modImplementation` dependencies, and Java 21.

## Building

Each project builds independently with Gradle (no wrapper script is committed). **The Gradle and
JDK versions differ per target and are not interchangeable** — Gradle 9.4 for the 26.x and 1.21.11
projects, 8.10.2 for 1.21.1, 8.8 for 1.20.1:

```
# from the repo root — pick the project you want
gradle -p fabric-26.1.2   build   # requires JDK 25 + Gradle 9.4
gradle -p neoforge-26.1.2 build   # requires JDK 25 + Gradle 9.4
gradle -p fabric-1.21.11  build   # requires JDK 21 + Gradle 9.4
gradle -p fabric-1.21.1   build   # requires JDK 21 + Gradle 8.10.2
gradle -p neoforge-1.21.1 build   # requires JDK 21 + Gradle 8.10.2
gradle -p forge-1.20.1    build   # requires JDK 17 + Gradle 8.8
gradle -p fabric-1.20.1   build   # requires JDK 17 + Gradle 8.8
```

For the 26.x projects Gradle itself must be *launched* on JDK 25 — a toolchain declaration alone
is not enough. Using the wrong JDK on the 1.20.1 projects fails confusingly, with ForgeGradle
reporting `Start.java cannot find Main` rather than anything about Java versions.

Jars land in each project's `build/libs/`. `runClient` in any project starts a dev client.

**Runtime dependencies:** Fabric build needs Fabric API + Fabric Language Kotlin; the
NeoForge/Forge builds need Kotlin for Forge.

**Bedrock:** nothing to compile. `bash bedrock/devtools/package.sh <output dir>` zips both packs into
the release `.mcaddon`; [`bedrock/README.md`](bedrock/README.md) has the details.

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
- **iR3DN4X** — mod author: the ports (NeoForge/Forge/Fabric, and the Bedrock add-on), taming & riding, the size-shifting
  hunt behaviour, the camo variant's active camouflage, smooth-animation work, and everything
  else.
