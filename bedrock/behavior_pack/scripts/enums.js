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

export function isPeacefulDifficulty() {
  try { return same(mc.world.getDifficulty(), mc.Difficulty, "Peaceful"); } catch (e) { return false; }
}
