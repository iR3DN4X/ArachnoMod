// The Java mod's /spider command tree, as Bedrock custom commands (registered at startup):
//
//   /spider newinstance [size]      your personal follower spider (creative only), size 0.3-20
//   /spider size <size>             resize your personal spider
//   /spider release                 dismiss it
//   /spider debug                   ops: the original's debug graphics on every spider, on/off
//   /spider menu                    the settings menu (the Spider Config Book's); ops may edit
//   /spiderconfig <setting> [value] ops: show (no value) or change any setting; "list" shows all.
//                                   Java's /spider config <key> get|set and /spider chasedistance.
//   /spidertoggle <setting> <true|false>  ops: switch an on/off setting (the value slot above is a
//                                   number, so on/off settings take 1/0 there too).
//
// Bedrock requires a namespace, so the registered names are arachno:spider and
// arachno:spiderconfig; the game also accepts them without the "arachno:" when no other pack
// uses the same name. A command callback may not change the world, so anything that does is
// deferred with system.run.

import * as mc from "@minecraft/server";
import { SPEC, setConfig, describe, check, CONFIG } from "./config.js";
import { isCreative } from "./enums.js";
import { DEBUG, LEGEND } from "./debug.js";

const PERMISSION = mc.CommandPermissionLevel || mc.CustomCommandPermissionLevel || {};
const PARAM = mc.CustomCommandParamType || {};
const STATUS = mc.CustomCommandStatus || { Success: 0, Failure: 1 };

// The on/off settings (/spidertoggle's list).
const SWITCHES = Object.keys(SPEC).filter((k) => typeof SPEC[k][0] === "boolean");

const ok = (message) => ({ status: STATUS.Success, message });
const fail = (message) => ({ status: STATUS.Failure, message });

/** handlers: { openMenu(player), hasInstance(player), newInstance(player, size), resize(player, size),
 *  release(player) } */
export function registerCommands(handlers) {
  if (!mc.system.beforeEvents || !mc.system.beforeEvents.startup) return;   // the browser test stub
  mc.system.beforeEvents.startup.subscribe((init) => {
    const registry = init.customCommandRegistry;
    try {
      registry.registerEnum("arachno:spider_action", ["newinstance", "release", "size", "menu", "debug"]);
      registry.registerCommand({
        name: "arachno:spider",
        description: "menu = every spider setting. Your personal spider: newinstance [size] (creative), size <size>, release. Ops: debug.",
        permissionLevel: PERMISSION.Any,
        cheatsRequired: false,
        mandatoryParameters: [{ type: PARAM.Enum, name: "arachno:spider_action" }],
        optionalParameters: [{ type: PARAM.Float, name: "size" }],
      }, (origin, action, size) => spiderCommand(handlers, origin, action, size));

      // The value is a NUMBER slot: a String slot rejects a bare number ("Syntax error: Unexpected
      // "3"" - every numeric setting was unsettable in 1.2.7), and one slot can't take both. On/off
      // settings get their own command with a true/false slot (and take 1/0 here too).
      registry.registerEnum("arachno:spider_setting", ["list", ...Object.keys(SPEC).map((k) => k.toLowerCase())]);
      registry.registerCommand({
        name: "arachno:spiderconfig",
        description: "Show or change a spider setting (no value = show it; 'list' = all of them).",
        permissionLevel: PERMISSION.GameDirectors,
        cheatsRequired: false,
        mandatoryParameters: [{ type: PARAM.Enum, name: "arachno:spider_setting" }],
        optionalParameters: [{ type: PARAM.Float, name: "value" }],
      }, (origin, setting, value) => configCommand(setting, value));

      registry.registerEnum("arachno:spider_switch", SWITCHES.map((k) => k.toLowerCase()));
      registry.registerCommand({
        name: "arachno:spidertoggle",
        description: "Turn an on/off spider setting on or off.",
        permissionLevel: PERMISSION.GameDirectors,
        cheatsRequired: false,
        mandatoryParameters: [{ type: PARAM.Enum, name: "arachno:spider_switch" }, { type: PARAM.Boolean, name: "on" }],
      }, (origin, setting, on) => configCommand(setting, on));
    } catch (e) {
      console.warn(`[arachno] command registration failed: ${e}`);
    }
  });
}

function playerOf(origin) {
  const e = origin && (origin.sourceEntity || origin.initiator);
  return e && e.typeId === "minecraft:player" ? e : null;
}

function spiderCommand(handlers, origin, action, size) {
  const player = playerOf(origin);
  // The debug view is a world-wide switch, so ops only (Java: permission level 2). Flipping it
  // changes nothing in the world by itself - the renderers pick it up on their next tick.
  if (String(action).toLowerCase() === "debug") {
    if (player && !(player.commandPermissionLevel >= PERMISSION.GameDirectors)) {
      return fail("/spider debug is for operators.");
    }
    DEBUG.enabled = !DEBUG.enabled;
    return ok(DEBUG.enabled ? LEGEND : "Spider debug view OFF.");
  }
  // Anyone may LOOK (Java: no permission needed); the menu itself decides who may change things.
  // Forms can't open from a command callback, and the chat screen is still up: menu.js retries.
  if (String(action).toLowerCase() === "menu") {
    if (!player) return fail("Only a player can open the settings menu.");
    mc.system.run(() => handlers.openMenu(player));
    return ok("Opening the spider settings...");
  }
  if (!player) return fail("Only a player can have a personal spider.");
  switch (String(action).toLowerCase()) {
    case "newinstance": {
      let creative = false;
      creative = isCreative(player);
      if (!creative) return fail("/spider newinstance is a creative-mode-only feature.");
      const s = size === undefined ? 1.0 : size;
      if (!(s >= 0.3 && s <= 20)) return fail("Size must be between 0.3 and 20.");
      mc.system.run(() => handlers.newInstance(player, s));
      return ok(`Spawned your personal spider${size === undefined ? "" : ` (size ${s})`} - it follows you. /spider size <n> to resize, /spider release to remove.`);
    }
    case "size": {
      if (size === undefined || !(size >= 0.3 && size <= 20)) return fail("Usage: /spider size <0.3-20>");
      if (!handlers.hasInstance(player)) return fail("You have no spider. Use /spider newinstance first.");
      mc.system.run(() => handlers.resize(player, size));
      return ok(`Spider size set to ${size}.`);
    }
    case "release": {
      if (!handlers.hasInstance(player)) return fail("You have no spider. Use /spider newinstance first.");
      mc.system.run(() => handlers.release(player));
      return ok("Spider released.");
    }
  }
  return fail("Usage: /spider menu | newinstance [size] | size <size> | release | debug (ops)");
}

function configCommand(setting, value) {
  const name = String(setting);
  if (name.toLowerCase() === "list") {
    const lines = Object.keys(SPEC).map((k) => `${k} = ${CONFIG[k]}`);
    return ok(`Spider settings:\n${lines.join("\n")}`);
  }
  const info = describe(name);
  if (!info) return fail(`Unknown setting '${name}'. /spiderconfig list shows them all.`);
  if (value === undefined || value === "") {
    const how = typeof info.def === "boolean" ? ` Change it with /spidertoggle ${info.key} true|false.` : "";
    return ok(`${info.key} = ${info.value} (default ${info.def}, ${info.range}) - ${info.text}${how}`);
  }
  // The value is applied in a deferred tick: saving it touches the world.
  const preview = setPreview(info.key, value);
  if (!preview.ok) return fail(preview.error);
  mc.system.run(() => setConfig(info.key, preview.value));
  return ok(`Spider setting '${info.key}' set to ${preview.value} (saved with the world).`);
}

/** Validate without saving (the callback may not write to the world). */
function setPreview(key, value) {
  const [def] = SPEC[key];
  if (typeof def === "boolean") {
    const s = String(value).toLowerCase();
    if (["true", "1", "on"].includes(s)) return { ok: true, value: true };
    if (["false", "0", "off"].includes(s)) return { ok: true, value: false };
    return { ok: false, error: `${key} is on or off: /spidertoggle ${key} true|false (or 1/0 here)` };
  }
  if (typeof value === "boolean") return { ok: false, error: `${key} needs a number: /spiderconfig ${key} <number>` };
  return check(key, value);   // range-checked, whole-number settings rounded - exactly what gets saved
}
