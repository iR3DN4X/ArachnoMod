package com.heledron.spideranimation.platform

import com.heledron.spideranimation.SpiderAnimationMod
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

/**
 * Awarding the mod's own advancements.
 *
 * **This file is VERSION-SPECIFIC (1.21.1).** The advancement API is one of the few that moved
 * between 1.20.1 and 1.21: here `get(id)` returns an `AdvancementHolder`, whereas 1.20.1
 * called it `getAdvancement(id)` and returned a bare `Advancement`. The 1.20.1 projects
 * carry their own copy of this file; the rest of the engine calls [grantAdvancement] and never
 * sees the difference. (The JSON differs too — 1.20.1 reads `data/<ns>/advancements/` with
 * `"icon": {"item": ...}`, 1.21 reads `data/<ns>/advancement/` with `"icon": {"id": ...}`.)
 *
 * Every advancement the mod ships uses the `minecraft:impossible` trigger, so nothing in vanilla
 * can ever grant them — the mod is the sole authority on when each one fires. `award` is
 * idempotent: it returns false and sends no packet when the player already holds the advancement,
 * which is what makes it safe to call from a per-tick proximity check.
 */
fun ServerPlayer.grantAdvancement(path: String) {
    // A datapack is allowed to remove these, so a missing advancement is not an error.
    // 26.1: ServerPlayer.server is private now - reach the server through the level instead.
    val srv = (level() as? net.minecraft.server.level.ServerLevel)?.server ?: return
    val advancement = srv.advancements
        .get(Identifier.fromNamespaceAndPath(SpiderAnimationMod.ID, path)) ?: return
    advancements.award(advancement, CRITERION)
}

/** The single criterion every one of the mod's advancements is keyed on. */
private const val CRITERION = "impossible"

/** Advancement ids — these must match the file names under `data/arachnomod/advancement/`. */
object Advancements {
    const val ENCOUNTER = "encounter"
    const val ENCOUNTER_CAMO = "encounter_camo"
    const val ENCOUNTER_POISON = "encounter_poison"
    const val ENCOUNTER_HUNTER = "encounter_hunter"
    const val SLAY = "slay"
    const val ENRAGE = "enrage"
    const val SLAY_BOSS = "slay_boss"
}
