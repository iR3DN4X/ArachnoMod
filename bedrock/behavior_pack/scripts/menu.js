// The SPIDER CONFIG BOOK (Java v1.8.5), for Bedrock: a settings menu with every setting, what it
// does, its default and range, and a reset - so nobody has to learn /spiderconfig. Opened by using
// the book (handed once to everyone who may edit - giveBookOnFirstJoin) or with /spider menu.
//
// Java's book is one screen with a category cycle, a search box and per-row save/reset buttons.
// Script forms can't change while they are open, so here the same thing is a short chain of forms:
//   the menu (ActionFormData)   a button per category saying how many of its settings differ from
//                               the defaults, Find a setting, Reset ALL (asks first), and the outcome
//                               of the last save at the top - Java's status line
//   a page (ModalFormData)      one control per setting: a toggle, or a text box holding the current
//                               value (empty it to reset that setting); the description under every
//                               name, changed settings named in yellow; "reset this page"; Save puts
//                               everything through the same checks as /spiderconfig, then it is
//                               back to the menu
// A page holds ONLY toggles and text boxes: labels and headers leave holes in formValues.
// Who may edit: operators, and the owner of a single-player world (an operator on Bedrock too);
// everyone else gets the same settings read-only.

import { system, ItemStack, PlayerPermissionLevel, CommandPermissionLevel } from "@minecraft/server";
import { ActionFormData, ModalFormData, MessageFormData, FormCancelationReason } from "@minecraft/server-ui";
import { SPEC, CONFIG, CATEGORIES, check, setConfig } from "./config.js";

export const BOOK_ITEM = "arachno:spider_config_book";
const GIFT_TAG = "arachno_config_book";   // on a player who has had their one free book
const BUSY_RETRY_TICKS = 5;                // /spider menu: the chat screen is still closing...
const BUSY_RETRIES = 40;                   // ...so keep trying for up to 10 seconds

const OPERATOR = PlayerPermissionLevel ? PlayerPermissionLevel.Operator : 2;
const GAME_DIRECTORS = CommandPermissionLevel ? CommandPermissionLevel.GameDirectors : 1;

/** May this player change the settings? (Java's canEditSpiderConfig.) */
export function canEdit(player) {
  try { if (player.playerPermissionLevel === OPERATOR) return true; } catch (e) {}
  try { if (player.commandPermissionLevel >= GAME_DIRECTORS) return true; } catch (e) {}
  return false;
}

/** "spawnMinMinutes" -> "Spawn Min Minutes", as Java's book names them. */
export function pretty(key) {
  return key.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/^./, (c) => c.toUpperCase());
}

const isDefault = (key) => CONFIG[key] === SPEC[key][0];
const shown = (v) => (typeof v === "number" ? String(Math.round(v * 1e6) / 1e6) : v ? "on" : "off");
const range = (key) => `${shown(SPEC[key][1])} to ${shown(SPEC[key][2])}`;   // numbers only

/** Show a form. A player still in another screen (the chat that /spider menu was typed in) is
 *  "busy": try again a few ticks later. Resolves with the response, or null if it never got
 *  through (or the player left). */
async function present(player, form) {
  for (let attempt = 0; attempt <= BUSY_RETRIES; attempt++) {
    let response;
    try { response = await form.show(player); } catch (e) { return null; }
    if (!(response.canceled && response.cancelationReason === FormCancelationReason.UserBusy)) return response;
    await new Promise((resolve) => system.runTimeout(resolve, BUSY_RETRY_TICKS));
  }
  return null;
}

/** The menu. `status` is the outcome of the last change, shown at the top. */
export async function openMenu(player, status = "") {
  const editor = canEdit(player);
  const changed = (keys) => keys.filter((k) => !isDefault(k)).length;
  const lines = [];
  if (status) lines.push(status, "");
  lines.push(editor
    ? "Pick a group to see and change its settings. Changes are saved with the world and apply at once."
    : "§6Read only: only operators can change these settings.");
  const total = changed(Object.keys(SPEC));
  lines.push(total === 0 ? "§7Every setting is at its default." : `§7${total} setting(s) differ from the defaults, named in §eyellow§7.`);

  const form = new ActionFormData().title("ArachnoMod Settings").body(lines.join("\n"));
  const actions = [];
  for (const [name, keys] of CATEGORIES) {
    const c = changed(keys);
    form.button(`${name}\n§8${keys.length} settings${c ? `, ${c} changed` : ""}`);
    actions.push(() => openPage(player, name, keys));
  }
  form.button("Find a setting");
  actions.push(() => openSearch(player));
  if (editor) {
    form.button("§cReset ALL");
    actions.push(() => confirmResetAll(player));
  }
  form.button("Done");
  actions.push(() => {});

  const r = await present(player, form);
  if (!r || r.canceled || r.selection === undefined || !actions[r.selection]) return;
  await actions[r.selection]();
}

/** One page of settings: editable for an editor, a read-only list for everyone else. */
async function openPage(player, title, keys) {
  if (!canEdit(player)) return showReadOnly(player, title, keys);
  const form = new ModalFormData().title(title);
  for (const key of keys) {
    const [def, , , text] = SPEC[key];
    const label = `${isDefault(key) ? "" : "§e"}${pretty(key)}§r\n§7${text}`;
    if (typeof def === "boolean") {
      form.toggle(label, { defaultValue: CONFIG[key], tooltip: `Default: ${shown(def)}` });
    } else {
      form.textField(label, `default ${shown(def)}`, {
        defaultValue: shown(CONFIG[key]),
        tooltip: `Default ${shown(def)}, ${range(key)}. Empty the box to reset it.`,
      });
    }
  }
  form.toggle("§cReset every setting on this page to its default", { defaultValue: false });
  form.submitButton("Save");
  const r = await present(player, form);
  if (!r) return;
  if (r.canceled || !r.formValues) return openMenu(player);   // closed: back to the menu
  return openMenu(player, applyPage(player, keys, r.formValues));
}

/** Save a page's answers (`values` in page order, then the reset-page toggle). Returns the status
 *  line: what was saved, reset and refused - the same checks and messages as /spiderconfig. */
export function applyPage(player, keys, values) {
  if (!canEdit(player)) return "§cOnly operators can change these settings.";
  const resetPage = values[keys.length] === true;
  const saved = [], reset = [], refused = [];
  keys.forEach((key, i) => {
    const def = SPEC[key][0];
    let want, isReset = resetPage;
    if (resetPage) {
      want = def;
    } else if (typeof def === "boolean") {
      if (typeof values[i] !== "boolean") return;
      want = values[i];
    } else {
      const typed = String(values[i] ?? "").trim();
      // Untouched (the page shows numbers rounded): leave a value set more precisely by command alone.
      if (typed === shown(CONFIG[key])) return;
      if (typed === "") {
        want = def;
        isReset = true;
      } else {
        const checked = check(key, typed);
        if (!checked.ok) { refused.push(checked.error); return; }
        want = checked.value;
      }
    }
    if (want === CONFIG[key]) return;
    const result = setConfig(key, want);
    if (!result.ok) { refused.push(result.error); return; }
    if (isReset) reset.push(key);
    else saved.push(`${key} = ${shown(result.value)}`);
  });
  const lines = [];
  if (saved.length) lines.push(`§aSaved: ${saved.join(", ")}`);
  if (reset.length) lines.push(`§aReset to default: ${reset.join(", ")}`);
  if (refused.length) lines.push(`§cNot changed: ${refused.join("; ")}`);
  const status = lines.length ? lines.join("\n") : "§7Nothing changed.";
  if (saved.length || reset.length || refused.length) try { player.sendMessage(status); } catch (e) {}
  return status;
}

async function showReadOnly(player, title, keys) {
  const entries = keys.map((key) => {
    const [def, , , text] = SPEC[key];
    const limits = typeof def === "boolean" ? "" : `, ${range(key)}`;
    return `${isDefault(key) ? "" : "§e"}${pretty(key)}: §f${shown(CONFIG[key])}  §7(default ${shown(def)}${limits})\n§7${text}`;
  });
  const form = new ActionFormData().title(title).body(entries.join("\n\n")).button("Back");
  if (await present(player, form)) return openMenu(player);
}

/** Java's search box: every setting whose name or description mentions the words. */
export function findSettings(query) {
  const q = String(query).trim().toLowerCase();
  if (!q) return [];
  return Object.keys(SPEC).filter((k) =>
    k.toLowerCase().includes(q) || pretty(k).toLowerCase().includes(q) || SPEC[k][3].toLowerCase().includes(q));
}

async function openSearch(player) {
  const form = new ModalFormData().title("Find a setting")
    .textField("Search settings...", "e.g. chase, size, ward, bite")
    .submitButton("Search");
  const r = await present(player, form);
  if (!r) return;
  const query = r.canceled || !r.formValues ? "" : String(r.formValues[0] ?? "").trim();
  if (!query) return openMenu(player);
  const keys = findSettings(query);
  if (!keys.length) return openMenu(player, `§7No settings match "${query}".`);
  return openPage(player, `Settings matching "${query}"`, keys);
}

async function confirmResetAll(player) {
  const form = new MessageFormData().title("Reset ALL spider settings?")
    .body("Every setting goes back to its default. This cannot be undone.")
    .button1("§cReset ALL")
    .button2("Cancel");
  const r = await present(player, form);
  if (!r) return;
  if (r.canceled || r.selection !== 0) return openMenu(player);
  return openMenu(player, resetAll(player));
}

/** Every setting back to its default. */
export function resetAll(player) {
  if (!canEdit(player)) return "§cOnly operators can change these settings.";
  let count = 0;
  for (const key of Object.keys(SPEC)) {
    if (isDefault(key)) continue;
    if (setConfig(key, SPEC[key][0]).ok) count++;
  }
  const status = count === 0 ? "§7Everything was already at its default." : `§aReset ${count} setting(s) to their defaults.`;
  if (count) try { player.sendMessage(status); } catch (e) {}
  return status;
}

/** First join: one book for everyone who may edit, once ever (a tag on the player remembers). */
export function giveBookOnFirstJoin(player) {
  if (!CONFIG.giveConfigBook || !canEdit(player)) return false;
  try {
    if (player.hasTag(GIFT_TAG) || !player.addTag(GIFT_TAG)) return false;
    const left = player.getComponent("minecraft:inventory").container.addItem(new ItemStack(BOOK_ITEM, 1));
    if (left) player.dimension.spawnItem(left, player.location);
    player.sendMessage("§7You got a §fSpider Config Book§7: use it to see and change every spider setting (or type /spider menu).");
    return true;
  } catch (e) {
    return false;
  }
}
