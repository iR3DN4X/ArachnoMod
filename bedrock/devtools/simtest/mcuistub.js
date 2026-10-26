// Stand-in for @minecraft/server-ui (2.1.0): every form a script shows is held in `ui.open` - what
// the player sees: its kind, title, body, buttons or controls (with their defaults and tooltips) -
// until the test answers it as the player would: ui.click(i), ui.submit({ "Label start": value }),
// ui.close(). `ui.busy = N` makes the next N shows come back UserBusy (the chat screen /spider menu
// was typed in is still closing), as the game does. Misuse - a form shown over another, the
// pre-2.0 positional arguments, a page with labels in it - goes into ui.errors.
export const FormCancelationReason = { UserBusy: "UserBusy", UserClosed: "UserClosed" };
export const FormRejectReason = { MalformedResponse: "MalformedResponse", PlayerQuit: "PlayerQuit", ServerShutdown: "ServerShutdown" };

export const ui = { open: null, shown: [], busy: 0, errors: [] };

/** Colour codes off: what a label reads as. */
export const plain = (s) => String(s ?? "").replace(/§./g, "");

function show(kind, form, player) {
  if (!player || player.typeId !== "minecraft:player") ui.errors.push(`${kind} shown to ${player && player.typeId}`);
  if (form._title === undefined) ui.errors.push(`${kind} without a title`);
  if (ui.busy > 0) { ui.busy--; return Promise.resolve({ canceled: true, cancelationReason: "UserBusy" }); }
  if (ui.open) ui.errors.push(`"${plain(form._title)}" shown while "${plain(ui.open.title)}" is still open`);
  return new Promise((resolve) => {
    const rec = { kind, title: form._title, body: form._body, buttons: form._buttons, controls: form._controls,
      submit: form._submit, resolve: (r) => { if (ui.open === rec) ui.open = null; resolve(r); } };
    ui.open = rec;
    ui.shown.push(rec);
  });
}

const options = (kind, o) => {
  if (o !== undefined && (typeof o !== "object" || o === null)) ui.errors.push(`${kind}: options must be an object (2.x), got ${JSON.stringify(o)}`);
  return o || {};
};

export class ActionFormData {
  constructor() { this._buttons = []; }
  title(t) { this._title = t; return this; }
  body(t) { this._body = t; return this; }
  button(text, icon) { this._buttons.push({ text, icon }); return this; }
  divider() { return this; }
  header() { return this; }
  label() { return this; }
  show(player) {
    if (!this._buttons.length) ui.errors.push("action form without buttons");
    return show("action", this, player);
  }
}

export class MessageFormData {
  title(t) { this._title = t; return this; }
  body(t) { this._body = t; return this; }
  button1(t) { this._b1 = t; return this; }
  button2(t) { this._b2 = t; return this; }
  show(player) {
    this._buttons = [{ text: this._b1 }, { text: this._b2 }];
    return show("message", this, player);
  }
}

export class ModalFormData {
  constructor() { this._controls = []; }
  title(t) { this._title = t; return this; }
  toggle(label, o) { const op = options("toggle", o); this._controls.push({ type: "toggle", label, value: op.defaultValue ?? false, tooltip: op.tooltip }); return this; }
  textField(label, placeholder, o) {
    const op = options("textField", o);
    if (op.defaultValue !== undefined && typeof op.defaultValue !== "string") ui.errors.push(`textField default must be a string, got ${typeof op.defaultValue}`);
    this._controls.push({ type: "textField", label, placeholder, value: op.defaultValue ?? "", tooltip: op.tooltip });
    return this;
  }
  slider(label, min, max, o) { const op = options("slider", o); this._controls.push({ type: "slider", label, value: op.defaultValue ?? min }); return this; }
  dropdown(label, items, o) { const op = options("dropdown", o); this._controls.push({ type: "dropdown", label, value: op.defaultValueIndex ?? 0 }); return this; }
  label() { ui.errors.push("a label on a modal page (it leaves a hole in formValues)"); this._controls.push({ type: "label" }); return this; }
  header() { ui.errors.push("a header on a modal page (it leaves a hole in formValues)"); this._controls.push({ type: "header" }); return this; }
  divider() { ui.errors.push("a divider on a modal page (it leaves a hole in formValues)"); this._controls.push({ type: "divider" }); return this; }
  submitButton(t) { this._submit = t; return this; }
  show(player) { return show("modal", this, player); }
}

function need(kind) {
  if (!ui.open) throw new Error(`no form is open (wanted a ${kind} form)`);
  if (ui.open.kind !== kind) throw new Error(`the open form "${plain(ui.open.title)}" is a ${ui.open.kind} form, not ${kind}`);
  return ui.open;
}

/** Press button `i` of the open action or message form (or the first button whose text starts with `i`). */
ui.click = (i) => {
  const f = ui.open;
  if (!f || (f.kind !== "action" && f.kind !== "message")) throw new Error("no action/message form is open");
  const index = typeof i === "number" ? i : f.buttons.findIndex((b) => plain(b.text).startsWith(i));
  if (index < 0 || index >= f.buttons.length) throw new Error(`no button ${i} on "${plain(f.title)}": ${f.buttons.map((b) => plain(b.text).split("\n")[0]).join(", ")}`);
  f.resolve({ canceled: false, selection: index });
};
/** Fill the open page: `changes` maps the start of a control's label (colour codes off) to its new
 *  value; every other control keeps what the form showed. */
ui.submit = (changes = {}) => {
  const f = need("modal");
  const values = f.controls.map((c) => (c.type === "label" || c.type === "header" || c.type === "divider" ? undefined : c.value));
  for (const [start, value] of Object.entries(changes)) {
    const i = f.controls.findIndex((c) => plain(c.label).startsWith(start));
    if (i < 0) throw new Error(`no control "${start}" on "${plain(f.title)}"`);
    const c = f.controls[i];
    if (c.type === "toggle" && typeof value !== "boolean") throw new Error(`${start} is a toggle`);
    if (c.type === "textField" && typeof value !== "string") throw new Error(`${start} is a text box`);
    values[i] = value;
  }
  f.resolve({ canceled: false, formValues: values });
};
/** Close the open form with its X. */
ui.close = () => {
  if (!ui.open) throw new Error("no form is open");
  ui.open.resolve({ canceled: true, cancelationReason: "UserClosed" });
};
/** Let the script react (forms answer through promises). */
ui.settle = async () => { for (let i = 0; i < 5; i++) await new Promise((r) => setTimeout(r, 0)); };
