# ArachnoMod — MinecraftForge 1.20.1

A giant, procedurally-animated spider that stalks players. This is the **Forge 1.20.1** build of
ArachnoMod (the NeoForge 1.21.1 version is a separate project). Based on
[TheCymaera/minecraft-spider](https://github.com/TheCymaera/minecraft-spider) by Heledron; created by **iR3DN4X**.

The spider's eight legs are solved in real time with FABRIK inverse kinematics and drawn with vanilla
`BlockDisplay` entities — but it's a real hostile **mob** (the "Netherite Octoarachnopod"):

- One spawns naturally at a time, respawning every **5–30 minutes**, ~32 blocks from a player.
- It **hunts the nearest player within 64 blocks**, **growing to tower over the trees** (up to ~10×)
  and **charging in at huge speed** the farther away it is, then shrinking to normal as it closes in.
- **1000 HP**, deals **6 hearts** of melee, and dies like any mob (poof). Only ever one at a time.
- Also available as a **spawn egg** (Spawn Eggs creative tab) for instant testing.

## Requirements

- **Minecraft 1.20.1** + **MinecraftForge 47.x**
- **Kotlin for Forge** (4.x) — install alongside this mod. Needed on **client and server**.

## Build

Built with **ForgeGradle** + **Kotlin for Forge 4.12.0**. **Minecraft 1.20.1 requires Java 17**, so
the build uses a Java 17 toolchain (Gradle auto-provisions one if you don't have it; in IntelliJ set
the Gradle JVM to a JDK 17).

- `gradlew build` → JAR in `build/libs/arachnomod-1.0.0.jar`
- `gradlew runClient` → dev client

> Note: the Access Transformer (`src/main/resources/META-INF/accesstransformer.cfg`) widens two
> private `Display` setters. Forge 1.20.1 applies ATs at the **SRG layer**, so they use SRG method
> names (`m_269214_`, `m_269329_`) rather than the official names the 1.21.1 version uses.

## License

This mod is a derivative of TheCymaera/minecraft-spider, distributed under the original project's
terms: free use (commercial or not), attribution appreciated but not required, no reselling without
substantial changes. See https://github.com/TheCymaera/minecraft-spider.
