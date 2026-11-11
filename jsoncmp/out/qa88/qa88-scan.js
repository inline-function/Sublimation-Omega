const fs = require("fs");
const e2e = "F:\\Sublimation\\frontend\\src\\test\\resources\\e2e";
const list = fs.readFileSync("F:\\Sublimation\\jsoncmp\\e2e-list.txt", "utf8").trim().split("\n");
const NAME_EXTRA = "¬∧∨→↔∀∃⊤⊥⊗⊕⊆⊂∉≤≥≠∙·×∀∃";
function isNameChar(ch) { return /[A-Za-z0-9_]/.test(ch) || NAME_EXTRA.indexOf(ch) >= 0; }
const SYM_OK = "=&|<>!:.+-*/%^∈≠≤≥∉⊆⊂·×"; // single syms that may form ops
const MULTI = ["->", "<->", "<=", ">=", "!=", "<:", "..", "->>"]; // merge order: longest first
function tokenize(text) {
  const raw = []; let i = 0;
  while (i < text.length) {
    const ch = text[i];
    if (ch === " " || ch === "\t") { i++; continue; }
    if (isNameChar(ch)) { const s = i; while (i < text.length && isNameChar(text[i])) i++; raw.push({ t: text.slice(s, i), kind: "name", start: s, end: i }); }
    else { raw.push({ t: ch, kind: "sym", start: i, end: i + 1 }); i++; }
  }
  // merge multi-char ops
  const toks = [];
  for (let j = 0; j < raw.length; j++) {
    if (raw[j].kind === "sym") {
      let merged = false;
      for (const mu of MULTI) {
        if (raw[j].t === mu[0] && j + mu.length - 1 < raw.length) {
          let ok = true;
          for (let k = 1; k < mu.length; k++) if (raw[j + k].kind !== "sym" || raw[j + k].t !== mu[k]) ok = false;
          if (ok) { toks.push({ t: mu, kind: "sym", start: raw[j].start, end: raw[j + mu.length - 1].end }); j += mu.length - 1; merged = true; break; }
        }
      }
      if (merged) continue;
    }
    toks.push(raw[j]);
  }
  return toks;
}
const LEVEL = { "&": 8, "∧": 8, "|": 7, "∨": 7, "->": 6, "→": 6, "<->": 5, "↔": 5, "=": 9, "≠": 9, "!=": 9, "∈": 9, "<:": 9, ">=": 9, "<=": 9, "≥": 9, "≤": 9, "in": 9, "+": 10, "-": 10, "*": 10, "/": 10, "%": 10, "^": 10, "..": 10, "is": 10 };
const STRUCT = ["(",")","[","]","{","}","<",">",",",";",":",".","`",",",";"];
function levelOf(tk) {
  if (LEVEL[tk.t] !== undefined) return LEVEL[tk.t];
  if (tk.kind === "sym" && STRUCT.indexOf(tk.t) < 0 && tk.t.length === 1) return 9; // unknown unicode/math sym = user infix (level 9)
  return null;
}
function stripLine(line) {
  // remove // comments, string and backtick-ident contents
  let out = ""; let i = 0;
  while (i < line.length) {
    if (line[i] === "/" && line[i+1] === "/") break;
    if (line[i] === "\"") { i++; while (i < line.length && line[i] !== "\"") i++; i++; out += "  "; continue; }
    if (line[i] === "`") { i++; while (i < line.length && line[i] !== "`") i++; i++; out += "    "; continue; }
    out += line[i]; i++;
  }
  return out;
}
function opsOfRun(toks, lo, hi) {
  const ops = []; const subs = [];
  let depth = 0; let openAt = -1; let openKind = null;
  for (let i = lo; i < hi; i++) {
    const tk = toks[i];
    if (depth === 0) {
      if (tk.t === "(" || tk.t === "[" || tk.t === "{") { depth++; openAt = i; openKind = tk.t; continue; }
      if (tk.t === ")" || tk.t === "]" || tk.t === "}") continue;
      if (tk.t === "<" || tk.t === ">") {
        const prev = i > lo ? toks[i-1] : null;
        const noSpaceBefore = prev && prev.end === tk.start;
        if (tk.t === "<" && prev && prev.kind === "name" && noSpaceBefore) { depth++; openAt = i; openKind = "<"; continue; }
        if (tk.t === ">") continue;  // treat stray > as boundary (prop end handled outside)
        ops.push({ tok: tk, level: 9 }); continue;
      }
      const lv = levelOf(tk);
      if (lv !== null) ops.push({ tok: tk, level: lv });
    } else {
      if (tk.t === "(" || tk.t === "[" || tk.t === "{" || tk.t === "<") depth++;
      else if (tk.t === ")" || tk.t === "]" || tk.t === "}" || tk.t === ">") { depth--; if (depth === 0) { subs.push([openAt+1, i]); openKind = null; } }
    }
  }
  return { ops: ops, subs: subs };
}
function walkRuns(toks, lo, hi, onRun) {
  const r = opsOfRun(toks, lo, hi);
  onRun(lo, hi, r);
  for (const s of r.subs) walkRuns(toks, s[0], s[1], onRun);
}
function verdict(ops) {
  const why = [];
  for (let i = 0; i + 1 < ops.length; i++) if (ops[i].level < ops[i+1].level) why.push(ops[i].tok.t + "(" + ops[i].level + ")<" + ops[i+1].tok.t + "(" + ops[i+1].level + ")");
  const imp = ops.filter(o => o.level === 6).length;
  if (imp >= 2) why.push("RASSOC->x" + imp);
  return why;
}
const results = []; const allSyms = new Set(); const allRuns = [];
for (const name of list) {
  const src = fs.readFileSync(e2e + "\\" + name.split("__").join("\\"), "utf8");
  src.split("\n").forEach(function(raw, ln) {
    const line = stripLine(raw);
    if (!line.trim()) return;
    const toks = tokenize(line);
    if (toks.length === 0) return;
    // prop regions: find token sequence [":","Null","<"] or axiom <...> or by [ ... ]
    const regions = [];
    for (let i = 0; i + 2 < toks.length; i++) {
      if (toks[i].t === ":" && toks[i+1].t === "Null" && toks[i+2].t === "<") regions.push({ lo: i + 3, kind: "funprop" });
      if (toks[i].t === "axiom" && toks[i+1].kind === "name" && toks[i+2].t === "<") regions.push({ lo: i + 3, kind: "axiom" });
      if (toks[i].t === "by" && toks[i+1].t === "[") regions.push({ lo: i + 2, kind: "by" });
    }
    const isBecause = /^[ \t]*[∵∴]/.test(line);
    function closeOf(loTok) {
      let d = 1;
      for (let j = loTok; j < toks.length; j++) {
        const t = toks[j].t;
        if (t === "(" || t === "[" || t === "{") d++;
        else if (t === ")") { d--; if (d === 0) return j; }
        else if (t === "]") { d--; if (d === 0) return j; }
        else if (t === "}") { d--; if (d === 0) return j; }
      }
      return toks.length;
    }
    for (const reg of regions) {
      const hi = closeOf(reg.lo);
      walkRuns(toks, reg.lo, hi, function(a, b, r) {
        r.ops.forEach(o => allSyms.add(o.tok.t));
        const why = verdict(r.ops);
        if (r.ops.length >= 2) allRuns.push({ name: name, line: ln + 1, ctx: reg.kind, ops: r.ops.map(o => o.tok.t + o.level).join(" "), n: r.ops.length, why: why.join(";") });
        if (why.length) results.push({ name: name, line: ln + 1, ctx: reg.kind, ops: r.ops.map(o => o.tok.t).join(" "), lv: r.ops.map(o => o.level).join(","), why: why.join("; "), text: line.trim().slice(0, 120) });
      });
    }
    if (regions.length === 0 && isBecause) {
      walkRuns(toks, 0, toks.length, function(a, b, r) {
        r.ops.forEach(o => allSyms.add(o.tok.t));
        const why = verdict(r.ops);
        if (why.length) results.push({ name: name, line: ln + 1, ctx: "because", ops: r.ops.map(o => o.tok.t).join(" "), lv: r.ops.map(o => o.level).join(","), why: why.join("; "), text: line.trim().slice(0, 120) });
      });
    }
    // VALUE position & / | ruling C
    if (regions.length === 0 && !isBecause && toks.some(t => t.t === "&" || t.t === "|")) {
      walkRuns(toks, 0, toks.length, function(a, b, r) {
        const all = r.ops; const why = [];
        for (let i2 = 0; i2 + 1 < all.length; i2++) {
          const a1 = all[i2], a2 = all[i2+1];
          if ((a1.tok.t === "&" || a1.tok.t === "|") && a2.level > a1.level) why.push("VAL " + a1.tok.t + "(" + a1.level + ")<" + a2.tok.t + "(" + a2.level + ")");
        }
        if (why.length) results.push({ name: name, line: ln + 1, ctx: "value", ops: all.map(o => o.tok.t).join(" "), lv: all.map(o => o.level).join(","), why: why.join("; "), text: line.trim().slice(0, 120) });
      });
    }
  });
}
console.log("=== CHANGED CANDIDATES: " + results.length);
results.forEach(function(r, i) { console.log("#" + (i+1) + " " + r.name + ":" + r.line + " [" + r.ctx + "] lv=[" + r.lv + "] ops=" + r.ops + " | why: " + r.why + " | " + r.text); });
console.log("\n=== multi-op runs total: " + allRuns.length);
console.log("=== symbols seen in prop runs: " + JSON.stringify([...allSyms].sort()));
fs.writeFileSync("F:\\Sublimation\\jsoncmp\\qa88-scan.json", JSON.stringify(results, null, 1));
fs.writeFileSync("F:\\Sublimation\\jsoncmp\\qa88-runs.json", JSON.stringify(allRuns, null, 1));