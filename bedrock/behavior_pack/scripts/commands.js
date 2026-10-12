// The Java mod's /spider command tree, as Bedrock custom commands (registered at startup):
//
//   /spider newinstance [size]      your personal follower spider (creative only), size 0.3-20
//   /spider size <size>             resize your personal spider
//   /spider release                 dismiss it
//   /spiderconfig <setting> [value] ops: show (no value) or change any setting; "list" shows all.
//                                   Java's /spider config <key> get|set and /spider chasedistance.
//
// Bedrock requires a namespace, so the registered names are arachno:spider and
// arachno:spiderconfig; the game also accepts them without the "arachno:" when no other pack
// uses the same name. A command callback may not change the world, so anything that does is
// deferred with system.run.

import * as mc from "@minecraft/server";
import { SPEC, setConfig, describe, CONFIG } from "./config.js";
import { isCreative } from "./enums.js";

const PERMISSION = mc.CommandPermissionLevel || mc.CustomCommandPermissionLevel || {};
const PARAM = mc.CustomCommandParamType || {};
const STATUS = mc.CustomCommandStatus || { Success: 0, Failure: 1 };

const ok = (message) => ({ status: STATUS.Success, message });
const fail = (message) => ({ status: STATUS.Failure, message });

/** handlers: { newInstance(player, size), resize(player, size), release(player) } -> string|null error */
export function registerCommands(handlers) {
  if (!mc.system.beforeEvents || !mc.system.beforeEvents.startup) return;   // the browser test stub
  mc.system.beforeEvents.startup.subscribe((init) => {
    const registry = init.customCommandRegistry;
    try {
      registry.registerEnum("arachno:spider_action", ["newinstance", "release", "size"]);
      registry.registerCommand({
        name: "arachno:spider",
        description: "Your personal spider: newinstance [size] (creative), size <size>, release.",
        permissionLevel: PERMISSION.Any,
        cheatsRequired: false,
        mandatoryParameters: [{ type: PARAM.Enum, name: "arachno:spider_action" }],
        optionalParameters: [{ type: PARAM.Float, name: "size" }],
      }, (origin, action, size) => spiderCommand(handlers, origin, action, size));

      registry.registerEnum("arachno:spider_setting", ["list", ...Object.keys(SPEC).map((k) => k.toLowerCase())]);
      registry.registerCommand({
        name: "arachno:spiderconfig",
        description: "Show or change a spider setting (no value = show it; 'list' = all of them).",
        permissionLevel: PERMISSION.GameDirectors,
        cheatsRequired: false,
        mandatoryParameters: [{ type: PARAM.Enum, name: "arachno:spider_setting" }],
        optionalParameters: [{ type: PARAM.String, name: "value" }],
      }, (origin, setting, value) => configCommand(setting, value));
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
  return fail("Usage: /spider newinstance [size] | size <size> | release");
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
    return ok(`${info.key} = ${info.value} (default ${info.def}, ${info.range}) - ${info.text}`);
  }
  // The value is applied in a deferred tick: saving it touches the world.
  const preview = setPreview(info.key, value);
  if (!preview.ok) return fail(preview.error);
  mc.system.run(() => setConfig(info.key, value));
  return ok(`Spider setting '${info.key}' set to ${preview.value} (saved with the world).`);
}

/** Validate without saving (the callback may not write to the world). */
function setPreview(key, value) {
  const [def, min, max] = SPEC[key];
  if (typeof def === "boolean") {
    const s = String(value).toLowerCase();
    if (["true", "1", "on"].includes(s)) return { ok: true, value: true };
    if (["false", "0", "off"].includes(s)) return { ok: true, value: false };
    return { ok: false, error: `${key} is true or false` };
  }
  const n = Number(String(value).trim());
  if (!Number.isFinite(n)) return { ok: false, error: `${key} needs a number` };
  if (n < min || n > max) return { ok: false, error: `${key} must be between ${min} and ${max}` };
  return { ok: true, value: n };
}
