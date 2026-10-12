// A small Molang interpreter covering what the pack's leg entity uses: numbers, 'strings',
// v.* variables, q.property / q.position / q.delta_time / q.body_y_rotation,
// math.clamp / abs / sqrt / round / atan2 (DEGREES, like Molang), + - * / unary -, comparisons,
// && || !, the ternary, `??`, parentheses, and `v.x = expr;` statements.
//
// STRICT like the real game: reading a variable that was never assigned STOPS the expression
// ("your code will stop with a content error" - Molang syntax guide), so its assignment never
// happens; `a ?? b` gives b instead. Rounds 5-7 of the Bedrock port drew no legs for exactly this
// reason while an older version of this file quietly read unset variables as 0. Stopped
// expressions are counted in ctx.errors (a Map: message -> count).

export class MolangUnset extends Error {}

function tokenize(src) {
  const out = [];
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (/\s/.test(c)) { i++; continue; }
    if (/[0-9]/.test(c) || (c === "." && /[0-9]/.test(src[i + 1] || ""))) {
      let j = i; while (j < src.length && /[0-9.]/.test(src[j])) j++;
      if (src[j] === "f") j++;
      out.push({ t: "num", v: parseFloat(src.slice(i, j)) }); i = j; continue;
    }
    if (/[A-Za-z_]/.test(c)) {
      let j = i; while (j < src.length && /[A-Za-z0-9_.]/.test(src[j])) j++;
      out.push({ t: "id", v: src.slice(i, j).toLowerCase() }); i = j; continue;
    }
    if (c === "'") { const j = src.indexOf("'", i + 1); out.push({ t: "str", v: src.slice(i + 1, j) }); i = j + 1; continue; }
    const two = src.slice(i, i + 2);
    if ([">=", "<=", "==", "!=", "&&", "||", "??"].includes(two)) { out.push({ t: "op", v: two }); i += 2; continue; }
    if ("+-*/()<>!?:,=;".includes(c)) { out.push({ t: "op", v: c }); i++; continue; }
    throw new Error(`Molang: unexpected '${c}' in: ${src}`);
  }
  return out;
}

const ALIAS = (name) => name.replace(/^variable\./, "v.").replace(/^query\./, "q.");

export function compile(src) {
  const toks = tokenize(src);
  let p = 0;
  const peek = () => toks[p];
  const next = () => toks[p++];
  const expect = (v) => { const t = next(); if (!t || t.v !== v) throw new Error(`Molang: expected '${v}' in: ${src}`); };

  function primary() {
    const t = next();
    if (!t) throw new Error(`Molang: unexpected end in: ${src}`);
    if (t.t === "num") return () => t.v;
    if (t.t === "str") return () => t.v;
    if (t.t === "op" && t.v === "(") { const e = ternary(); expect(")"); return e; }
    if (t.t === "op" && t.v === "-") { const e = unary(); return (c) => -e(c); }
    if (t.t === "op" && t.v === "!") { const e = unary(); return (c) => (e(c) ? 0 : 1); }
    if (t.t === "id") {
      const name = ALIAS(t.v);
      if (peek() && peek().v === "(") {
        next();
        const args = [];
        if (peek().v !== ")") { args.push(ternary()); while (peek().v === ",") { next(); args.push(ternary()); } }
        expect(")");
        return (c) => call(name, args.map((a) => a(c)), c);
      }
      if (name.startsWith("v.")) return (c) => { if (!(name in c.v)) throw new MolangUnset(`unset ${name}`); return c.v[name]; };
      return (c) => call(name, [], c);
    }
    throw new Error(`Molang: unexpected '${t.v}' in: ${src}`);
  }
  const unary = primary;
  function binary(sub, ops) {
    return () => {
      let left = sub();
      while (peek() && peek().t === "op" && ops.includes(peek().v)) {
        const op = next().v; const l = left, r = sub();
        left = (c) => {
          const a = l(c), b = r(c);
          switch (op) {
            case "*": return a * b; case "/": return a / b; case "+": return a + b; case "-": return a - b;
            case ">": return a > b ? 1 : 0; case "<": return a < b ? 1 : 0; case ">=": return a >= b ? 1 : 0; case "<=": return a <= b ? 1 : 0;
            case "==": return a === b ? 1 : 0; case "!=": return a !== b ? 1 : 0;
            case "&&": return a && b ? 1 : 0; case "||": return a || b ? 1 : 0;
          }
        };
      }
      return left;
    };
  }
  const mul = binary(unary, ["*", "/"]);
  const add = binary(mul, ["+", "-"]);
  const cmp = binary(add, [">", "<", ">=", "<="]);
  const eq = binary(cmp, ["==", "!="]);
  const and = binary(eq, ["&&"]);
  const or = binary(and, ["||"]);
  function coalesce() {
    const left = or();
    if (peek() && peek().v === "??") {
      next(); const right = coalesce();
      return (c) => { try { return left(c); } catch (e) { if (e instanceof MolangUnset) return right(c); throw e; } };
    }
    return left;
  }
  function ternary() {
    const cond = coalesce();
    if (peek() && peek().v === "?") {
      next(); const a = ternary(); expect(":"); const b = ternary();
      return (c) => (cond(c) ? a(c) : b(c));
    }
    return cond;
  }
  function statement() {
    if (peek() && peek().t === "id" && ALIAS(peek().v).startsWith("v.") && toks[p + 1] && toks[p + 1].v === "=") {
      const name = ALIAS(next().v); next();
      const e = ternary();
      return (c) => { c.v[name] = e(c); return c.v[name]; };
    }
    return ternary();
  }
  const stmts = [];
  while (p < toks.length) {
    stmts.push(statement());
    if (peek() && peek().v === ";") next();
    else if (p < toks.length) throw new Error(`Molang: expected ';' in: ${src}`);
  }
  return (c) => {
    try { let r = 0; for (const s of stmts) r = s(c); return r; }
    catch (e) {
      if (!(e instanceof MolangUnset)) throw e;
      if (!c.errors) c.errors = new Map();
      const k = `${e.message} in: ${src}`; c.errors.set(k, (c.errors.get(k) || 0) + 1);
      return 0;   // an errored expression evaluates to 0.0
    }
  };
}

const DEG = 180 / Math.PI;
function call(name, args, c) {
  switch (name) {
    case "q.property": return c.props[args[0]] ?? 0;
    case "q.position": return [c.position.x, c.position.y, c.position.z][args[0] ?? 0];
    case "q.delta_time": return c.deltaTime;
    case "q.body_y_rotation": return c.bodyYaw;
    case "math.clamp": return Math.max(args[1], Math.min(args[2], args[0]));
    case "math.abs": return Math.abs(args[0]);
    case "math.sqrt": return Math.sqrt(args[0]);
    case "math.round": return Math.round(args[0]);
    case "math.atan2": return Math.atan2(args[0], args[1]) * DEG;
    default: throw new Error(`Molang: unknown function ${name}`);
  }
}

/** Evaluate a [x, y, z] channel whose entries are numbers or Molang strings. */
export function channel(arr, ctx) {
  return arr.map((e) => (typeof e === "number" ? e : compile(e)(ctx)));
}
