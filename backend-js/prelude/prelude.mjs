// backend-js V1 prelude：值模型 + 内建面 + 渲染（渲染规格=golden 锁定）
// List/MutableList = JS Array（OQ-9 Array 值模型：Nil=[]、Cons(h,t)=[h,...t]）
// struct/enum/union 值 = tagged object { __tag: ctor名， …字段（保声明序） }
// Optional/Result 语言构造子同样为 tagged object；@ref 视图=透传（零包装）

// ---------- 内建面（kernel 内建面规格 §2，V1 非异步子集；异步面=报错桩） ----------

export function print(v) { console.log(render(v)); }

export function render(v) {
  if (typeof v === 'string') return v;
  if (typeof v === 'number') return String(v);
  if (typeof v === 'bigint') return v.toString();
  if (typeof v === 'boolean') return v ? 'true' : 'false';
  if (v === null || v === undefined) return 'null';
  if (Array.isArray(v)) return '[' + v.map(render).join(', ') + ']';
  if (v instanceof Map) return '{' + [...v].map(function (e) { return render(e[0]) + ': ' + render(e[1]); }).join(', ') + '}';
  if (v instanceof Set) return '{' + [...v].map(render).join(', ') + '}';
  if (typeof v === 'object' && typeof v.__tag === 'string') return ctorStr(v);
  if (typeof v === 'function') return '<fn>';
  return String(v);
}

function ctorStr(v) {
  const keys = Object.keys(v).filter(function (k) { return k !== '__tag'; });
  return v.__tag + (keys.length === 0 ? '()' : '(' + keys.map(function (k) { return render(v[k]); }).join(', ') + ')');
}

export const toStr = render;

// ---------- V2：深拷引擎（R4 §2.3） ----------
// DFS 对象图遍历 + visited 表：同一次拷贝内已访问节点复用同一副本——保共享、保环。
// 标量/字符串/函数=透传（不可变对象零成本;函数值透传=e2e lambda 样本 golden 实证）。
// R-14 元素层计数仅编译期成本提示——运行时一律全图递归（不做部分拷）。
// R-29 口径：deepCopyCount 只作审计观测，不参与正确性路径。
export let deepCopyCount = 0;
const DEEP_MAX = 4096;   // DFS 栈深上限（成本面与 W-DEEP-COPY-LOOP 同源;部分/惰性拷为 V3 CoW 域）
export function deepcopy(v, seen, depth) {
  if (seen === undefined) { seen = new Map(); deepCopyCount += 1; }
  if (depth === undefined) depth = 0;
  if (v === null || v === undefined) return v;
  const tv = typeof v;
  if (tv === 'function') return v;
  if (tv !== 'object') return v;
  if (depth > DEEP_MAX) {
    throw new Error('$S.deepcopy: 深度超上限(' + DEEP_MAX + ')——深图/循环数据需 V3 CoW 处置');
  }
  const hit = seen.get(v);
  if (hit !== undefined) return hit;
  if (Array.isArray(v)) {
    const c = [];
    seen.set(v, c);
    for (const x of v) c.push(deepcopy(x, seen, depth + 1));
    const proto = Object.getPrototypeOf(v);
    if (proto && proto !== Array.prototype) Object.setPrototypeOf(c, proto);  // 保 MList 等子类形态
    return c;
  }
  if (v instanceof Map) {
    const c = new Map();
    seen.set(v, c);
    for (const e of v) c.set(deepcopy(e[0], seen, depth + 1), deepcopy(e[1], seen, depth + 1));
    return c;
  }
  if (v instanceof Set) {
    const c = new Set();
    seen.set(v, c);
    for (const x of v) c.add(deepcopy(x, seen, depth + 1));
    return c;
  }
  const c = {};
  seen.set(v, c);
  for (const k in v) c[k] = deepcopy(v[k], seen, depth + 1);
  return c;
}

export function concat(a, b) { return String(a) + String(b); }

export function length(s) { return [...String(s)].length; }

export function charAt(s, i) { return [...String(s)][i]; }

export function substring(s, from, to) {
  const chars = [...String(s)];
  if (to === undefined || to === null) return chars.slice(from).join('');
  return chars.slice(from, to).join('');
}

export function strCmp(a, b) { a = String(a); b = String(b); return a < b ? -1 : (a > b ? 1 : 0); }
export function strUpper(s) { return String(s).toUpperCase(); }
export function strLower(s) { return String(s).toLowerCase(); }
export function strTrim(s) { return String(s).trim(); }
export function startsWith(s, p) { return String(s).startsWith(String(p)); }
export function endsWith(s, p) { return String(s).endsWith(String(p)); }
export function strReplace(s, old_, nw) { return String(s).split(String(old_)).join(String(nw)); }
export function strSplit(s, sep) { return String(s).split(String(sep)); }
export function strIndexOf(s, sub) { const ix = String(s).indexOf(String(sub)); return ix < 0 ? null : ix; }

export function abs(n) { return Math.abs(n); }
export function floor(n) { return Math.floor(n); }
export function ceil(n) { return Math.ceil(n); }
export function pow(a, b) { return Math.pow(a, b); }
export function mathMin(a, b) { return Math.min(a, b); }
export function mathMax(a, b) { return Math.max(a, b); }
export function mathClamp(n, lo, hi) { return Math.min(Math.max(n, lo), hi); }
export function mathRound(n) { return Math.round(n); }
export function mathSin(n) { return Math.sin(n); }
export function mathCos(n) { return Math.cos(n); }
export function mathTan(n) { return Math.tan(n); }

export function parseNat(s) { const n = Number(String(s)); if (!Number.isInteger(n) || n < 0) return null; return n; }
export function parseRat(s) { const n = Number(String(s)); return Number.isFinite(n) ? n : null; }
export function toNat(v) { return Math.trunc(Number(v)); }
export function toRat(v) { return Number(v); }
export function toReal(v) { return Number(v); }

export function in_(v, coll) {
  if (Array.isArray(coll)) return coll.some(function (e) { return e === v; });
  if (coll instanceof Set || coll instanceof Map) return coll.has(v);
  if (typeof coll === 'object' && coll !== null) return Object.prototype.hasOwnProperty.call(coll, v);
  if (typeof coll === 'string') return String(coll).includes(v);
  return false;
}

export function dynamicStub(what) { throw new Error('backend-js V1: 未解析外部引用 ' + String(what) + '——该样本超出 V1 覆盖面（非静默：遇即抛）'); }
export function panic(msg) { throw new Error('panic: ' + String(msg)); }

// ---------- JSON 面（jsonParse 的解析产物=原生 JS 值，JSON 内建=访问器） ----------

export function jsonParse(s) { try { const v = JSON.parse(String(s)); return v === undefined ? None() : Some(v); } catch (e) { return None(); } }
export function jsonGet(o, k) { if (o === null || o === undefined || !(String(k) in Object(o))) return None(); return Some(o[k]); }
export function jsonAt(a, i) { if (!Array.isArray(a) || i < 0 || i >= a.length) return None(); return Some(a[i]); }
export function jsonLen(a) { if (!Array.isArray(a)) return None(); return Some(a.length); }
export function jsonStr(v) { return typeof v === 'string' ? Some(v) : None(); }
export function jsonNum(v) { return typeof v === 'number' ? Some(v) : None(); }
export function jsonBool(v) { return typeof v === 'boolean' ? Some(v) : None(); }
export function jsonIsNull(v) { return v === null; }
export function jsonIsArr(v) { return Array.isArray(v); }
export function jsonToStr(v) { return JSON.stringify(v); }
export function jsonToList(a) { return a === null || a === undefined ? [] : [...a]; }

// ---------- 数值/元组常量 ----------

export const emptyTuple = [];
export function singleTuple(v) { return [v]; }

// ---------- 语言构造子（Optional/Result；体空债：stdlib List 构造族） ----------
// tagged object 形（与用户 struct 一致：__tag + 字段）

export function Some(v) { return { __tag: 'Some', v: v }; }
export function None() { return { __tag: 'None' }; }
export function Ok(v) { return { __tag: 'Ok', v: v }; }
export function Err(v) { return { __tag: 'Err', v: v }; }

// List 构造族（OQ-9 Array 值模型；Cons(h, t) = [h, ...t]）
export function Nil() { return []; }
export function Cons(h, t) { return [h, ...t]; }
export function emptyList() { return []; }
export function listOf(...items) { return [...items]; }

// Mutable 容器：Array 值模型子类（OQ-9）。`.n` = 当前持有值（末元素）——
// golden 派生语义（e2e 特性巡礼 深拷站点注释实证：a=mutableListOf(0)，b 深拷后 a.n 仍 0）。
export class MList extends Array {
  get n() { return this.length === 0 ? undefined : this[this.length - 1]; }
}
export function mutableListOf(...items) {
  const ml = new MList();
  for (const x of items) ml.push(x);
  return ml;
}
export function add(xs, v) { xs.push(v); return null; }

// ---------- 通用构造 / 分派工具 ----------

// 类型名 → 运行时判定（HirPatIs / HirTypeTest）
const SCALAR_TYPES = { Nat: 'num', Int: 'num', Rat: 'num', Real: 'num', Str: 'str', Bool: 'bool', Null: 'null', Nothing: 'null', Any: 'any' };

export function typeIs(v, name) {
  const kind = SCALAR_TYPES[name];
  if (kind === 'num') return typeof v === 'number';
  if (kind === 'str') return typeof v === 'string';
  if (kind === 'bool') return typeof v === 'boolean';
  if (kind === 'null') return v === null || v === undefined;
  if (kind === 'any') return true;
  if (typeof v === 'object' && v !== null && v.__tag === name) return true;
  return false;
}

export function isCtor(v, name) {
  if (Array.isArray(v)) return name === 'Nil' ? v.length === 0 : (name === 'Cons' ? v.length > 0 : false);
  if (v && typeof v === 'object' && typeof v.__tag === 'string') return v.__tag === name;
  return false;
}

// ---------- 异步面：V1 报错桩（OQ-5；遇即抛，不静默放行） ----------

export function asyncStub(what) { throw new Error('backend-js V1: 异步面未实现（' + what + '）——V3 依裁决落'); }
export function Task() { asyncStub('Task'); }
export function Flow() { asyncStub('Flow'); }
export function Channel() { asyncStub('Channel'); }
export function runBoth() { asyncStub('runBoth'); }
export function runSeq() { asyncStub('runSeq'); }
export function withMutex() { asyncStub('withMutex'); }
export function bumpCounter() { asyncStub('bumpCounter'); }
export function bumpPair() { asyncStub('bumpPair'); }
export function collectToList() { asyncStub('collectToList'); }
export function collectCount() { asyncStub('collectCount'); }
export function collectAll() { asyncStub('collectAll'); }
export function channelFromList() { asyncStub('channelFromList'); }
export function sendAll() { asyncStub('sendAll'); }
export function channelDrain() { asyncStub('channelDrain'); }
export function channelPipe() { asyncStub('channelPipe'); }
export function readLine() { asyncStub('readLine'); }
export function readFile() { asyncStub('readFile'); }
export function writeFile() { asyncStub('writeFile'); }
export function appendFile() { asyncStub('appendFile'); }
export function fileExists() { asyncStub('fileExists'); }
export function readDir() { asyncStub('readDir'); }
export function getArgs() { asyncStub('getArgs'); }
export function timeNow() { return Date.now(); }
export function sleep() { asyncStub('sleep'); }
export function delay() { asyncStub('delay'); }
export function rand() { asyncStub('rand'); }
export function sublEval() { asyncStub('sublEval（脚本求值桥：需运行时嵌入编译器，V1 不覆盖）'); }

export const P = {
  print: print,
  render: render,
  toStr: toStr,
  concat: concat,
  length: length,
  charAt: charAt,
  substring: substring,
  strCmp: strCmp,
  strUpper: strUpper,
  strLower: strLower,
  strTrim: strTrim,
  startsWith: startsWith,
  endsWith: endsWith,
  strReplace: strReplace,
  strSplit: strSplit,
  strIndexOf: strIndexOf,
  abs: abs,
  floor: floor,
  ceil: ceil,
  pow: pow,
  mathMin: mathMin,
  mathMax: mathMax,
  mathClamp: mathClamp,
  mathRound: mathRound,
  mathSin: mathSin,
  mathCos: mathCos,
  mathTan: mathTan,
  parseNat: parseNat,
  parseRat: parseRat,
  toNat: toNat,
  toRat: toRat,
  toReal: toReal,
  in_: in_,
  dynamicStub: dynamicStub,
  panic: panic,
  jsonParse: jsonParse,
  jsonGet: jsonGet,
  jsonAt: jsonAt,
  jsonLen: jsonLen,
  jsonStr: jsonStr,
  jsonNum: jsonNum,
  jsonBool: jsonBool,
  jsonIsNull: jsonIsNull,
  jsonIsArr: jsonIsArr,
  jsonToStr: jsonToStr,
  jsonToList: jsonToList,
  emptyTuple: emptyTuple,
  singleTuple: singleTuple,
  Some: Some,
  None: None,
  Ok: Ok,
  Err: Err,
  Nil: Nil,
  Cons: Cons,
  emptyList: emptyList,
  listOf: listOf,
  mutableListOf: mutableListOf,
  add: add,
  deepcopy: deepcopy,
  MList: MList,
  typeIs: typeIs,
  isCtor: isCtor,
  asyncStub: asyncStub,
  Task: Task,
  Flow: Flow,
  Channel: Channel,
  runBoth: runBoth,
  runSeq: runSeq,
  withMutex: withMutex,
  bumpCounter: bumpCounter,
  bumpPair: bumpPair,
  collectToList: collectToList,
  collectCount: collectCount,
  collectAll: collectAll,
  channelFromList: channelFromList,
  sendAll: sendAll,
  channelDrain: channelDrain,
  channelPipe: channelPipe,
  readLine: readLine,
  readFile: readFile,
  writeFile: writeFile,
  appendFile: appendFile,
  fileExists: fileExists,
  readDir: readDir,
  getArgs: getArgs,
  timeNow: timeNow,
  sleep: sleep,
  delay: delay,
  rand: rand,
  sublEval: sublEval,
};


Object.defineProperty(P, 'deepCopyCount', { get: function () { return deepCopyCount; } });