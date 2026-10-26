// Enum checks that survive either spelling: the API's own enum constant when it is there, else the
// name (2.x enums are strings like "Creative"; compare case-insensitively as a fallback).

import * as mc from "@minecraft/server";

function same(value, enumObject, name) {
  if (enumObject && enumObject[name] !== undefined && value === enumObject[name]) return true;
  return String(value).toLowerCase() === name.toLowerCase();
}

export function isCreative(player) {
  try { return same(player.getGameMode(), mc.GameMode, "Creative"); } catch (e) { return false; }
}

export function isSpectator(player) {
  try { return same(player.getGameMode(), mc.GameMode, "Spectator"); } catch (e) { return false; }
}

/** Can a spider see this player at all (Java v1.8.6)? Never a spectator; someone in creative only
 *  if the spider is tamed - like vanilla mobs, a wild one ignores you in creative and wanders, while
 *  a tamed one still follows you around, as a pet does. */
export function notices(player, tamed) {
  return !isSpectator(player) && (tamed || !isCreative(player));
}

export function isPeacefulDifficulty() {
  try { return same(mc.world.getDifficulty(), mc.Difficulty, "Peaceful"); } catch (e) { return false; }
}
