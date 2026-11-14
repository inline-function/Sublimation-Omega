// backend-js emit:kind → emitter 注册表（提案 §2:翻译/擦除/拒收三态）
// 94 kind 知名表；未注册 kind=拒收报清单（不静默）。
// V1 子集注释：深拷落点=V2（deepCopy 位/notes 读但零发射）;Task/Flow/异步面=报错桩。

const KIND_TABLE = {
  // 文件/辅助 4
  HirFile: 'translate', HirParam: 'translate', HirWhenArm: 'translate', HirWhenPropArm: 'erase',
  // 声明 6
  HirFunDecl: 'translate', HirTraitDecl: 'erase', HirTraitConst: 'erase', HirStructDecl: 'translate',
  HirTypeAlias: 'erase', HirErrorDecl: 'reject',
  // 理论参数 2
  HirTypeParam: 'erase', HirPropParam: 'erase',
  // 语句 15
  HirVarStmt: 'translate', HirAssign: 'translate', HirAxiom: 'erase', HirByStmt: 'erase', HirUnchecked: 'erase',
  HirExprStmt: 'translate', HirBecause: 'erase', HirWhenProp: 'erase', HirIfProp: 'erase',
  HirExistsIntro: 'erase', HirExistsElim: 'erase', HirLocalFun: 'translate', HirStrategy: 'erase',
  HirReturn: 'translate', HirErrorStmt: 'reject',
  // 表达式 26
  HirIntLit: 'translate', HirFloatLit: 'translate', HirStrLit: 'translate', HirTop: 'translate', HirBot: 'translate',
  HirNameRef: 'translate', HirBinExpr: 'translate', HirUniExpr: 'translate', HirBorrow: 'translate',
  HirCall: 'translate', HirField: 'translate', HirFunRef: 'translate', HirTypeTest: 'translate',
  HirTask: 'stub', HirFlow: 'stub', HirInst: 'stub', HirLambda: 'translate', HirAnonFun: 'translate',
  HirTuple: 'translate', HirStructCtor: 'translate', HirByExpr: 'erase', HirVarExpr: 'translate',
  HirBlock: 'translate', HirIf: 'translate', HirWhen: 'translate', HirErrorExpr: 'reject',
  // 模式 8
  HirPatLit: 'translate', HirPatCtor: 'translate', HirPatTuple: 'translate', HirPatIs: 'translate',
  HirPatBind: 'translate', HirPatElse: 'translate', HirPatCond: 'translate', HirErrorPattern: 'reject',
  // 类型 9（编译期事实=擦除）
  HirNamedType: 'erase', HirFunType: 'erase', HirTupleType: 'erase', HirMutType: 'erase',
  HirAtomicType: 'erase', HirRefType: 'erase', HirUnionType: 'erase', HirQualifiedType: 'erase',
  HirErrorType: 'reject',
  // 命题 11 + 命题项 13（编译期槽位=擦除——用户倾向）
  HirPTop: 'erase', HirPBot: 'erase', HirPNot: 'erase', HirPAnd: 'erase', HirPOr: 'erase', HirPImp: 'erase',
  HirPIff: 'erase', HirPForall: 'erase', HirPExists: 'erase', HirPExists1: 'erase', HirPAtom: 'erase',
  HirTInt: 'erase', HirTStr: 'erase', HirTName: 'erase', HirTDollar: 'erase', HirTParamRef: 'erase',
  HirTTop: 'erase', HirTBot: 'erase', HirTBin: 'erase', HirTUni: 'erase', HirTCall: 'erase',
  HirTInst: 'erase', HirTField: 'erase', HirTSymbolCall: 'erase'
};

const UNKIND_ERROR_KINDS = ['HirUniExpr'];

// 符号-less 前缀运算符调用 → JS 运算符（样本注释实证：& → &&）
const OP_CALL = {
  '==': '===', '!=': '!==', '<': '<', '<=': '<=', '>': '>', '>=': '>=',
  '+': '+', '-': '-', '*': '*', '/': '/', '%': '%', '&': '&&', '|': '||'
};

const BIN_OP = {
  '+': '+', '-': '-', '*': '*', '/': '/', '%': '%',
  '==': '===', '!=': '!==', '<': '<', '<=': '<=', '>': '>', '>=': '>=',
  '&': '&&', '|': '||'
};

const UNI_OP = { '-': '-', '!': '!' };

// prelude 可解析的外部名（语言构造子 + 体空债；模块态 stdlib 挂载不在 V1）
const PRELUDE_NAMES = new Set([
  'Some', 'None', 'Ok', 'Err', 'Nil', 'Cons', 'emptyList', 'listOf', 'mutableListOf', 'add'
]);

const PRELUDE_CTOR_FIELDS = {
  Some: ['v'], None: [], Ok: ['v'], Err: ['v'], Nil: [], Cons: ['head', 'tail']
};

const STUB_NOTE = { HirTask: 'Task', HirFlow: 'Flow', HirInst: 'Inst' };

export function compileUnit(envelope) {
  const hir = envelope.hir;
  const ctx = {
    structs: new Map(),   // 名 → 字段名数组
    nullary: new Set(),    // 无参 struct/ctor 名（值位=调用）
    defaults: new Map(),    // 'Struct.field' → 默认值表达式
    top: new Set(),       // 顶层声明名（fun/struct/alias）
    warnings: [],
    errors: [],
    whenCounter: 0,
    slotById: new Map(),      // symbol.id → 槽位信息（可变/引用/标量）——闭包捕获快照判据
    slotByName: new Map(),    // 名 → 槽位信息（symbol 缺失回落）
    renames: [],              // 闭包快照重命名帧（栈，内层优先）
    snapCounter: 0
  };
  collectSlots(hir, ctx);
  for (const e of hir.entries) {
    if (e.kind === 'HirStructDecl') {
      ctx.structs.set(e.name, e.fields || []);
      ctx.top.add(e.name);
      if (e.nullary || (e.fields || []).length === 0) ctx.nullary.add(e.name);
      for (const f of (e.fields || [])) { if (f.default) ctx.defaults.set(e.name + '.' + f.name, f.default); }
    } else if (e.kind === 'HirFunDecl' || e.kind === 'HirTypeAlias' || e.kind === 'HirTraitDecl') {
      ctx.top.add(e.name);
    }
  }
  const outs = [];
  for (const e of hir.entries) {
    const r = emitEntry(e, ctx);
    if (r) outs.push(r);
  }
  // 未注册 kind 清单（守门负例的补充：kind 知名表=94；未知即报）
  walkAll(hir, function (n) {
    if (n && n.kind && n.kind.indexOf('Hir') === 0 && !(n.kind in KIND_TABLE)) {
      ctx.errors.push('未知 kind（未注册）：' + n.kind + '@' + (n.pos || '?'));
    }
  });
  const name = unitJsName(envelope.unit.path);
  const hasMain = hir.entries.some(function (e) { return e.kind === 'HirFunDecl' && e.name === 'main'; });
  const lines = [];
  lines.push('// backend-js V1 生成件（HIR JSON v3 → JS）；unit: ' + envelope.unit.path + ' mode: ' + envelope.unit.mode);
  lines.push("import { P } from './prelude.mjs';");
  lines.push('const $S = P;');
  lines.push.apply(lines, outs);
  if (hasMain) lines.push('if (typeof main === ' + String.fromCharCode(39) + 'function' + String.fromCharCode(39) + ') main();');
  return {
    name: name,
    code: lines.join('\n') + '\n',
    warnings: ctx.warnings,
    errors: ctx.errors
  };
}

function nl() { return '\n'; }

function walkAll(n, f) {
  if (Array.isArray(n)) { for (const e of n) walkAll(e, f); return; }
  if (!n || typeof n !== 'object') return;
  f(n);
  for (const k in n) {
    if (k === 'symbol' || k === 'field' || k === 'ctor') continue;
    const v = n[k];
    if (v && typeof v === 'object') walkAll(v, f);
  }
}

function unitJsName(p) {
  // 名需单射：CJK/特殊符 ASCII 化 + 路径哈希防撞
  let h = 5381;
  for (const ch of p) { h = ((h * 33) ^ ch.charCodeAt(0)) >>> 0; }
  const base = p.replace(/\.[^.]+$/, '').replace(/[^A-Za-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '');
  return 'unit_' + base.slice(0, 20) + '_' + h.toString(36) + '.mjs';
}

function emitEntry(e, ctx) {
  const cls = KIND_TABLE[e.kind] || 'unknown';
  if (e.kind === 'HirFunDecl') return emitFun(e, ctx, true);
  if (e.kind === 'HirStructDecl') return emitStruct(e, ctx);
  if (cls === 'erase') return null;
  if (cls === 'reject') { ctx.errors.push('Error 族节点：' + e.kind + '@' + (e.pos || '?')); return null; }
  if (cls === 'unknown') { ctx.errors.push('未注册 kind（拒收）：' + e.kind + '@' + (e.pos || '?')); return null; }
  // 顶层语句（脚本模式 var/print）
  return emitStmt(e, ctx).join('\n');
}

function emitFun(d, ctx, top) {
  // 扩展方法：receiver 首参 self（方法糖 D-METHOD-SUGAR 调用方= 首参传）
  const hasReceiver = !!(d.receiver);
  const params = (hasReceiver ? [{ name: 'self', default: null }] : []).concat(d.params || []).map(function (p) {
    // 站点 3 默认值：拷指令后入槽
    if (p.default) {
      const dv = emitExpr(p.default, ctx);
      return p.name + ' = ' + (hasDeepCopyNote(p) ? deepWrap(dv) : dv);
    }
    return p.name;
  }).join(', ');
  const head = (top ? 'export function ' : 'function ') + safeName(d.name) + '(' + params + ') ';
  const body = d.body;
  if (!body) return head + '{}'; // 体空（模块态分派面声明——V1 不应出现）
  if (body.kind === 'HirBlock') {
    const bt = blockTail(body.stmts);
    const lines = [];
    for (const s of bt.stmts) { const ls = emitStmt(s, ctx); for (const l of ls) lines.push(l); }
    if (bt.tail !== null) {
      // 站点 7 返回值/函数表达式体：拷指令后返
      const tv = emitExpr(bt.tail, ctx);
      lines.push('  return ' + (hasDeepCopyNote(bt.tail, { skipArgNotes: true }) ? deepWrap(tv) : tv) + ';');
    }
    return head + '{\n' + lines.join('\n') + '\n}';
  }
  return head + '{\n  return ' + (hasDeepCopyNote(body, { skipArgNotes: true }) ? deepWrap(emitExpr(body, ctx)) : emitExpr(body, ctx)) + ';\n}';
}

function emitStruct(d, ctx) {
  const fields = (d.fields || []).map(function (f) { return f.name; });
  const args = fields.join(', ');
  const body = fields.length === 0
    ? "  return { __tag: " + JSON.stringify(d.name) + " };"
    : "  return { __tag: " + JSON.stringify(d.name) + ", " + fields.map(function (f) { return f + ': ' + f; }).join(', ') + " };";
  return 'export function ' + safeName(d.name) + '(' + args + ') {\n' + body + '\n}';
}

function stmtForm(b, ctx) {
  if (!b) return '';
  if (b.kind === 'HirBlock') {
    const ls = [];
    for (const st of b.stmts || []) { const l2 = emitStmt(st, ctx); for (const x of l2) ls.push(x); }
    return '{' + nl() + '  ' + ls.join(nl()) + nl() + '}';
  }
  return '{' + nl() + '  ' + emitStmt(b, ctx).join(nl()) + nl() + '}';
}

function emitWhenStmt(n, ctx) {
  ctx.whenCounter++;
  const wv = '_w' + ctx.whenCounter;
  const subject = emitExpr(n.subject, ctx);
  const lines = ['(function (' + wv + ') {'];
  if (n.subjectBinding && n.subjectBinding.name) lines.push('  const ' + safeName(n.subjectBinding.name) + ' = ' + wv + ';');
  let opened = false;
  for (const arm of n.arms || []) {
    const binds = [];
    const cond = emitPattern(arm.pattern, wv, binds, ctx);
    let full = cond;
    if (arm.guard) {
      const gi = '(function () {' + nl() + binds.map(function (b) { return '  ' + b + ';'; }).join(nl()) + nl() + '  return ' + emitExpr(arm.guard, ctx) + ';' + nl() + '})()';
      full = '(' + cond + ' && ' + gi + ')';
    }
    const body = armBody(arm, binds, ctx);
    const isElse = arm.pattern && arm.pattern.kind === 'HirPatElse';
    if (isElse) lines.push('  else {');
    else if (opened) lines.push('  else if (' + full + ') {');
    else lines.push('  if (' + full + ') {');
    opened = true;
    for (const b of binds) lines.push('    ' + b + ';');
    for (const b of body.split(nl())) if (b) lines.push('    ' + b);
    lines.push('  }');
  }
  lines.push('})(' + subject + ');');
  return [lines.join(nl())];
}

function blockTail(stmts) {
  // 尾值归一：去末尾无值 HirReturn（前端 Null 归一）；末尾 ExprStmt / unchecked 包裹的 ExprStmt / 有值 Return 升为块值
  let arr = (stmts || []).slice();
  let last = arr[arr.length - 1];
  if (last && last.kind === 'HirReturn' && !last.expr && !last.value) arr = arr.slice(0, -1);
  const last2 = arr[arr.length - 1];
  if (last2 && last2.kind === 'HirExprStmt') {
    return { stmts: arr.slice(0, -1), tail: last2.expr };
  }
  if (last2 && last2.kind === 'HirUnchecked' && last2.inner && last2.inner.kind === 'HirExprStmt') {
    return { stmts: arr.slice(0, -1), tail: last2.inner.expr };
  }
  if (last2 && last2.kind === 'HirReturn' && (last2.expr || last2.value)) {
    return { stmts: arr.slice(0, -1), tail: last2.expr || last2.value };
  }
  return { stmts: arr, tail: null };
}

function safeName(n) {
  // JS 保留/运算符名（用户自定 +、* 等）：转义绑定名
  if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(n)) return n;
  return '$_' + [...n].map(function (c) { return '$' + c.charCodeAt(0).toString(16); }).join('') + '$';
}

// ---------- V2 深拷（R4 §2.3 八站点发射 + 闭包快照 §2.4） ----------
const SCALAR_TYPE_NAMES = ['Nat', 'Int', 'Rat', 'Real', 'Str', 'Bool', 'Null', 'Nothing'];
// D-DEEP-COPY 补注（R4-A0 结构化）或 var 位 deepCopy 布尔——单源信任
function hasDeepCopyNote(n, opts) {
  if (!n || typeof n !== 'object') return false;
  if (n.deepCopy === true) return true;
  const ns = n.notes;
  if (!ns) return false;
  const skipArg = opts && opts.skipArgNotes;
  for (let i = 0; i < ns.length; i++) {
    if (ns[i].code !== 'D-DEEP-COPY') continue;
    if (skipArg && /实参\s*\d+/.test(ns[i].message || '')) continue;
    return true;
  }
  return false;
}
function hasMethodSugar(n) {
  const ns = n && n.notes;
  if (!ns) return false;
  for (let i = 0; i < ns.length; i++) if (ns[i].code === 'D-METHOD-SUGAR') return true;
  return false;
}
// call 实参位拷指令：message 内"实参 N"（ receiver 首参含在 N 内）
function deepArgIndexes(n) {
  const out = new Set();
  const ns = n.notes;
  if (!ns) return out;
  for (let i = 0; i < ns.length; i++) {
    const nt = ns[i];
    if (nt.code !== 'D-DEEP-COPY') continue;
    const m = /实参\s*(\d+)/.exec(nt.message || '');
    if (m) out.add(parseInt(m[1], 10) - 1);
  }
  return out;
}
function stripViews(t) {
  let cur = t;
  while (cur && (cur.kind === 'HirMutType' || cur.kind === 'HirRefType')) cur = cur.inner;
  return cur;
}
function isScalarType(t) {
  const n = stripViews(t);
  if (!n) return true;
  if (n.kind !== 'HirNamedType') return false;
  return SCALAR_TYPE_NAMES.indexOf(n.name) >= 0;
}
function slotInfoOf(annotations, type) {
  const ann = annotations || [];
  const ty = type || null;
  return {
    mut: ann.indexOf('mut') >= 0 || (ty !== null && ty.kind === 'HirMutType'),
    ref: ann.indexOf('ref') >= 0 || (ty !== null && ty.kind === 'HirRefType'),
    scalar: isScalarType(ty)
  };
}
function collectSlots(n, ctx) {
  if (Array.isArray(n)) { for (const x of n) collectSlots(x, ctx); return; }
  if (!n || typeof n !== 'object') return;
  if (n.kind === 'HirVarStmt' && n.name) {
    const info = slotInfoOf(n.annotations, n.type);
    ctx.slotByName.set(n.name, info);
    if (n.symbol && n.symbol.id !== undefined && n.symbol.id !== null) ctx.slotById.set(String(n.symbol.id), info);
  }
  if (n.kind === 'HirParam' && n.name) {
    const info = slotInfoOf(n.annotations, n.type);
    ctx.slotByName.set(n.name, info);
    if (n.symbol && n.symbol.id !== undefined && n.symbol.id !== null) ctx.slotById.set(String(n.symbol.id), info);
  }
  for (const k in n) if (k !== 'notes') collectSlots(n[k], ctx);
}
// 判据 A：节点带拷指令;判据 B:@mut 槽位 ← 值迁移（NameRef/Field/Borrow——非自造值）
function isValueTransfer(v) {
  if (!v) return false;
  return v.kind === 'HirNameRef' || v.kind === 'HirField' || v.kind === 'HirBorrow';
}
function deepWrap(code) { return '$S.deepcopy(' + code + ')'; }
function refViewVar(s) {
  const ann = s.annotations || [];
  return ann.indexOf('ref') >= 0 || (s.type && s.type.kind === 'HirRefType');
}
function mutVar(s) {
  const ann = s.annotations || [];
  return ann.indexOf('mut') >= 0 || (s.type && s.type.kind === 'HirMutType');
}

// ---------- 语句发射 ----------

function emitStmt(s, ctx) {
  switch (s.kind) {
    case 'HirVarStmt': {
      // 站点 1 var 初始化：指令拷（deepCopy 位/note）+ @mut 槽位值迁移拷；@ref 视图=零操作同指
      let value = s.value ? emitExpr(s.value, ctx) : 'null';
      if (!refViewVar(s) && (hasDeepCopyNote(s, { skipArgNotes: true }) || (mutVar(s) && isValueTransfer(s.value)))) {
        value = deepWrap(value);
      }
      if (s.destruct) {
        return ['let [' + s.destruct.map(function (n) { return n || '_'; }).join(', ') + '] = ' + value + ';'];
      }
      return ['let ' + safeName(s.name) + ' = ' + value + ';'];
    }
    case 'HirAssign': {
      // 站点 2 赋值：右值拷指令后写槽
      const target = emitTarget(s.target, ctx);
      let rhs = emitExpr(s.value, ctx);
      if (hasDeepCopyNote(s, { skipArgNotes: true }) || s.deepCopy === true) rhs = deepWrap(rhs);
      return [target + ' = ' + rhs + ';'];
    }
    case 'HirExprStmt': {
      const e = s.expr;
      if (e.kind === 'HirIf') {
        const cond = emitExpr(e.cond, ctx);
        const th = stmtForm(e.thenBlock, ctx);
        const el = e.elseBlock ? stmtForm(e.elseBlock, ctx) : null;
        return el ? ['if (' + cond + ') ' + th + ' else ' + el] : ['if (' + cond + ') ' + th];
      }
      if (e.kind === 'HirWhen') return emitWhenStmt(e, ctx);
      return [emitExpr(e, ctx) + ';'];
    }
    case 'HirReturn': {
      // 站点 7 return 值：拷指令后返
      if (!(s.expr || s.value)) return ['return;'];
      let rv = emitExpr(s.expr || s.value, ctx);
      if (hasDeepCopyNote(s, { skipArgNotes: true })) rv = deepWrap(rv);
      return ['return ' + rv + ';'];
    }
    case 'HirBlock': {
      const ls = [];
      for (const st of s.stmts || []) { const l2 = emitStmt(st, ctx); for (const x of l2) ls.push(x); }
      return ['{\n' + ls.join('\n') + '\n}'];
    }
    case 'HirIf': {
      const cond = emitExpr(s.cond, ctx);
      const th = emitStmt(s.thenBlock, ctx);
      const el = s.elseBlock ? emitStmt(s.elseBlock, ctx) : null;
      if (el) return ['if (' + cond + ') ' + th[0] + ' else ' + el[0]];
      return ['if (' + cond + ') ' + th[0]];
    }
    case 'HirLocalFun':
      return [emitFun({ name: s.name, params: s.params, body: s.body }, ctx, false).replace(/^function/, 'function ') + ';'];
    case 'HirUnchecked':
      return emitStmt(s.inner, ctx); // 信任闸编译期已闭：发射内层语句
    case 'HirAxiom': case 'HirByStmt': case 'HirBecause': case 'HirWhenProp':
    case 'HirIfProp': case 'HirExistsIntro': case 'HirExistsElim': case 'HirStrategy':
      return [];
    case 'HirTask': case 'HirFlow': case 'HirInst':
      ctx.warnings.push('stub:' + s.kind + '@' + (s.pos || '?'));
      return ['$S.asyncStub(' + JSON.stringify(s.kind) + ');'];
    case 'HirErrorStmt':
      ctx.errors.push('Error 族语句：' + (s.pos || '?'));
      return [];
  }
  ctx.errors.push('未注册语句 kind（拒收）：' + s.kind + '@' + (s.pos || '?'));
  return [];
}

function emitTarget(t, ctx) {
  if (t.kind === 'HirNameRef') return safeName(t.name);
  if (t.kind === 'HirField') return emitExpr(t, ctx);
  ctx.errors.push('未支持的赋值目标：' + t.kind + '@' + (t.pos || '?'));
  return '/*?*/';
}

// ---------- 表达式发射 ----------

function emitExpr(n, ctx) {
  switch (n.kind) {
    case 'HirIntLit': return n.value; // 数值塔→JS number（V1:安全整数域内精确）
    case 'HirFloatLit': return n.value;
    case 'HirStrLit': return JSON.stringify(n.value);
    case 'HirTop': return 'true';
    case 'HirBot': return 'false';
    case 'HirNameRef': return emitName(n, ctx);
    case 'HirField': return '(' + emitExpr(n.target, ctx) + ').' + safeField(n.name);
    case 'HirBinExpr':
      return '(' + emitExpr(n.left, ctx) + ' ' + (BIN_OP[n.op] || opErr(n.op, ctx)) + ' ' + emitExpr(n.right, ctx) + ')';
    case 'HirUniExpr':
      return '(' + (UNI_OP[n.op] || opErr(n.op, ctx)) + emitExpr(n.operand || n.value, ctx) + ')';
    case 'HirCall': return emitCall(n, ctx);
    case 'HirTuple':
      return '[' + (n.items || []).map(function (i) { return emitExpr(i, ctx); }).join(', ') + ']';
    case 'HirStructCtor': return emitStructCtor(n, ctx);
    case 'HirLambda': case 'HirAnonFun':
      return emitLambda(n, ctx);
    case 'HirFunRef': return emitName({ name: n.name, symbol: n.symbol }, ctx);
    case 'HirBlock': {
      const bt = blockTail(n.stmts);
      const ls = [];
      for (const st of bt.stmts) { const l2 = emitStmt(st, ctx); for (const x of l2) ls.push(x); }
      if (bt.tail !== null) {
        const tv = emitExpr(bt.tail, ctx);
        ls.push('  return ' + (hasDeepCopyNote(bt.tail, { skipArgNotes: true }) ? deepWrap(tv) : tv) + ';');
      }
      return '(function () {' + nl() + ls.join(nl()) + nl() + '})()';
    }
    case 'HirIf': {
      const cond = emitExpr(n.cond, ctx);
      const th = emitExpr(n.thenBlock, ctx);
      const el = n.elseBlock ? emitExpr(n.elseBlock, ctx) : 'null';
      return '(' + cond + ' ? ' + th + ' : ' + el + ')';
    }
    case 'HirWhen': return emitWhen(n, ctx);
    case 'HirVarExpr': {
      // var 表达式（脚本模式 var m = var k = 0）：内层绑定入 IIFE，外层见 VarStmt 特判
      const inner = 'let ' + safeName(n.name) + ' = ' + emitExpr(n.value, ctx) + ';';
      return '(function () {\n' + inner + '\n  return ' + safeName(n.name) + ';\n})()';
    }
    case 'HirTypeTest':
      return '$S.typeIs(' + emitExpr(n.value || n.target, ctx) + ', ' + JSON.stringify(typeNameOf(n)) + ')';
    case 'HirBorrow':
      return emitExpr(n.value || n.inner || n.target, ctx); // @ref 视图=透传（R4 §2.3/OQ-4）
    case 'HirTask': case 'HirFlow': case 'HirInst':
      ctx.warnings.push('stub:' + n.kind + '@' + (n.pos || '?'));
      return '$S.asyncStub(' + JSON.stringify(n.kind) + ')';
    case 'HirByExpr':
      return 'null'; // 证明计量=擦除
  }
  ctx.errors.push('未注册表达式 kind（拒收）：' + n.kind + '@' + (n.pos || '?'));
  return '/*' + n.kind + '*/';
}

function opErr(op, ctx) { ctx.errors.push('未支持运算符：' + op); return op; }

function typeNameOf(n) {
  const ty = n.type || n.test;
  return (ty && ty.name) || '?';
}

function safeField(name) {
  if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) return name;
  return JSON.stringify(name);
}

function emitName(n, ctx) {
  const sym = n.symbol;
  const k = sym && sym.kind;
  if (!sym) {
    // True/False 字面量 builtin 无 symbol 形（PatLit 内）——NameRef 无 symbol=外部未解析
    if (n.name === 'True') return 'true';
    if (n.name === 'False') return 'false';
    ctx.warnings.push('unresolved:' + n.name + '@' + (n.pos || '?'));
    return '$S.dynamicStub(' + JSON.stringify(n.name) + ')';
  }
  if (k === 'param' || k === 'local') {
    // V2 闭包快照：捕获快照帧内层优先（内层 lambda 形参帧先查）
    for (let i = ctx.renames.length - 1; i >= 0; i--) {
      const frame = ctx.renames[i];
      if (frame.has(n.name)) return frame.get(n.name);
    }
    return safeName(n.name);
  }
  if (k === 'builtin') {
    if (n.name === 'True') return 'true';
    if (n.name === 'False') return 'false';
    return '$S.' + (n.name === 'in' ? 'in_' : n.name);
  }
  if (k === 'fun' || k === 'ctor' || k === 'struct' || k === 'alias' || k === 'enum' || k === 'trait' || k === 'trait_const') {
    if (ctx.top.has(n.name)) {
      if (ctx.nullary.has(n.name)) return safeName(n.name) + '()';
      return safeName(n.name);
    }
    if (PRELUDE_NAMES.has(n.name)) {
      if (n.name === 'None' || n.name === 'Nil') return '$S.' + n.name + '()';
      return '$S.' + n.name;
    }
    ctx.warnings.push('unresolved:' + n.name + '@' + (n.pos || '?'));
    return '$S.dynamicStub(' + JSON.stringify(n.name) + ')';
  }
  if (k === 'field' || k === 'theorem' || k === 'propdef') return safeName(n.name);
  ctx.warnings.push('unresolved:' + n.name + '(' + k + ')@' + (n.pos || '?'));
  return '$S.dynamicStub(' + JSON.stringify(n.name) + ')';
}

function emitCall(n, ctx) {
  const callee = n.callee;
  if (callee.kind === 'HirNameRef' && !callee.symbol) {
    const op = OP_CALL[callee.name];
    if (op) {
      const a = (n.args || []).map(function (x) { return emitExpr(x, ctx); });
      return '(' + a[0] + ' ' + op + ' ' + a[1] + ')';
    }
    // 外部未解析方法（Channel.start 之属——V1 运行时桩，非静默）
    ctx.warnings.push('unresolved-call:' + callee.name + '@' + (n.pos || '?'));
    const ra = (n.args || []).map(function (x) { return emitExpr(x, ctx); });
    return '$S.dynamicStub(' + JSON.stringify(callee.name) + '(' + ra.length + ' args))';
  }
  if (callee.kind === 'HirNameRef' && callee.symbol && callee.symbol.kind === 'builtin') {
    const nm = callee.name;
    const a = callArgs(n, ctx);
    if (nm === 'in') return '$S.in_(' + a.join(', ') + ')';
    return '$S.' + nm + '(' + a.join(', ') + ')';
  }
  if (callee.kind === 'HirField') {
    // 限定调用（模块别名；V1 无模块态 → 未解析报清单）
    ctx.warnings.push('qualified-call:' + (callee.name || '?') + '@' + (n.pos || '?'));
    const ra = (n.args || []).map(function (x) { return emitExpr(x, ctx); });
    return '$S.dynamicStub(' + JSON.stringify('module:' + (callee.target && callee.target.name) + '.' + callee.name) + '(' + ra.length + ' args))';
  }
  const f = emitExpr(callee, ctx);
  const args = callArgs(n, ctx);
  return f + '(' + args.join(', ') + ')';
}

// 站点 4 call 实参：caller 侧拷后入帧（callee 无感）——"实参 N"拷指令;
// 方法糖（D-METHOD-SUGAR）receiver 首参=同一对象写口，透传不拷（别名共享/就地写可见性，
// e2e 特性巡礼与类型系统 golden 实证）
function callArgs(n, ctx) {
  const deepIdx = deepArgIndexes(n);
  const sugared = hasMethodSugar(n);
  return (n.args || []).map(function (x, i) {
    const code = emitExpr(x, ctx);
    if (deepIdx.has(i) && !(sugared && i === 0)) return deepWrap(code);
    return code;
  });
}

function emitStructCtor(n, ctx) {
  const name = n.struct || (n.symbol && n.symbol.name);
  if (ctx.structs.has(name)) {
    const fields = ctx.structs.get(name);
    const assigns = n.assigns || [];
    const values = fields.map(function (f) {
      const a = assigns.filter(function (x) { return x.field === f.name; })[0];
      // 站点 5 构造子/struct 字段：拷指令后装字段
      if (a) return hasDeepCopyNote(a) ? deepWrap(emitExpr(a.value, ctx)) : emitExpr(a.value, ctx);
      const dflt = ctx.defaults.get(name + '.' + f.name);
      return dflt ? emitExpr(dflt, ctx) : 'null';
    });
    return safeName(name) + '(' + values.join(', ') + ')';
  }
  if (PRELUDE_NAMES.has(name)) return '$S.' + name + '(' + (n.args || []).map(function (x) { return emitExpr(x, ctx); }).join(', ') + ')';
  ctx.warnings.push('unresolved-ctor:' + name + '@' + (n.pos || '?'));
  return '$S.dynamicStub(' + JSON.stringify(name) + ')';
}

function emitLambda(n, ctx) {
  // 站点 8 闭包捕获：创建时对可变复合值深拷快照（R4 §2.4;e2e 特性巡礼 golden 实证
  // ——snapshot() 持创建时值）。标量槽=直播（JS 词法域重绑定可见;e2e lambda 样本实证）;
  // @ref 捕获=共享透传（§2.3 同源）。纯编译期槽位信息（无前端 @mut 衣依赖）。
  const ownParams = new Set((n.params || []).map(function (p) { return p.name; }));
  const seen = new Set();
  const snaps = [];
  walkRefs(n.body, function (ref) {
    const nm = ref.name;
    if (ownParams.has(nm) || seen.has(nm)) return;
    const sym = ref.symbol;
    if (!sym || (sym.kind !== 'local' && sym.kind !== 'param')) return;
    seen.add(nm);
    const info = (sym.id !== undefined && sym.id !== null) ? ctx.slotById.get(String(sym.id)) : null;
    const slot = info || ctx.slotByName.get(nm);
    if (slot && slot.mut && !slot.ref && !slot.scalar) snaps.push(nm);
  });
  const defaults = (n.params || []).map(function (p) {
    if (p.default) {
      const dv = emitExpr(p.default, ctx);
      return p.name + ' = ' + (hasDeepCopyNote(p) ? deepWrap(dv) : dv);
    }
    return p.name;
  }).join(', ');
  const body = n.body;
  let inner;
  if (snaps.length === 0) {
    if (body.kind === 'HirBlock') {
      const ls = [];
      for (const st of body.stmts || []) { const l2 = emitStmt(st, ctx); for (const x of l2) ls.push(x); }
      inner = '{\n' + ls.join('\n') + '\n}';
    } else inner = emitExpr(body, ctx);
    return '((' + defaults + ') => ' + inner + ')';
  }
  ctx.snapCounter += 1;
  const snapVar = '__snap' + ctx.snapCounter;
  const frame = new Map();
  for (const nm of snaps) frame.set(nm, snapVar + '.' + safeField(nm));
  ctx.renames.push(frame);
  if (body.kind === 'HirBlock') {
    const ls = [];
    for (const st of body.stmts || []) { const l2 = emitStmt(st, ctx); for (const x of l2) ls.push(x); }
    inner = '{\n' + ls.join('\n') + '\n}';
  } else inner = emitExpr(body, ctx);
  ctx.renames.pop();
  const envObj = '{' + snaps.map(function (nm) {
    return safeField(nm) + ': ' + deepWrap(safeName(nm));
  }).join(', ') + '}';
  return '((' + snapVar + ') => ((' + defaults + ') => ' + inner + '))(' + envObj + ')';
}

function walkRefs(n, f) {
  if (Array.isArray(n)) { for (const x of n) walkRefs(x, f); return; }
  if (!n || typeof n !== 'object') return;
  if (n.kind === 'HirNameRef') f(n);
  for (const k in n) if (k !== 'notes') walkRefs(n[k], f);
}

// ---------- when/模式发射 ----------

function emitWhen(n, ctx) {
  ctx.whenCounter++;
  const wv = '_w' + ctx.whenCounter;
  const subject = emitExpr(n.subject, ctx);
  const lines = [];
  lines.push('(function (' + wv + ') {');
  if (n.subjectBinding && n.subjectBinding.name) {
    lines.push('  const ' + safeName(n.subjectBinding.name) + ' = ' + wv + ';');
  }
  let opened = false;
  for (const arm of n.arms || []) {
    const binds = [];
    const cond = emitPattern(arm.pattern, wv, binds, ctx);
    let full = cond;
    if (arm.guard) {
      // 卫条件的绑定作用域：入 IIFE 闭包（const m=… 在 cond 处不可见）
      const gi = '(function () {' + nl() + binds.map(function (b) { return '  ' + b + ';'; }).join(nl()) + nl() + '  return ' + emitExpr(arm.guard, ctx) + ';' + nl() + '})()';
      full = '(' + cond + ' && ' + gi + ')';
    }
    const body = armBody(arm, binds, ctx);
    const isElse = arm.pattern && arm.pattern.kind === 'HirPatElse';
    if (isElse) lines.push('  else {');
    else if (opened) lines.push('  else if (' + full + ') {');
    else lines.push('  if (' + full + ') {');
    opened = true;
    for (const b of binds) lines.push('    ' + b + ';');
    for (const b of body.split(nl())) if (b) lines.push('    ' + b);
    lines.push('  }');
  }
  lines.push('  return null;'); // 非穷尽→Null（样本实证口径）
  lines.push('})(' + subject + ')');
  return lines.join(nl());
}

function armBody(arm, binds, ctx) {
  const b = arm.body;
  if (b && b.kind === 'HirBlock') {
    const ls = [];
    for (const st of b.stmts || []) { const l2 = emitStmt(st, ctx); for (const x of l2) ls.push(x); }
    return ls.join('\n');
  }
  return 'return ' + emitExpr(b, ctx) + ';';
}

function emitPattern(p, v, binds, ctx) {
  if (!p) return 'true';
  switch (p.kind) {
    case 'HirPatElse': return 'true';
    case 'HirPatBind':
      if (p.name && p.name !== '_' && p.symbol) binds.push('const ' + safeName(p.name) + ' = ' + v);
      return 'true';
    case 'HirPatLit': {
      const lit = p.value;
      if (lit.kind === 'HirNameRef' && lit.symbol && lit.symbol.kind === 'builtin') {
        return v + ' === ' + (lit.name === 'True' ? 'true' : 'false');
      }
      if (lit.kind === 'HirNameRef') {
        // ctor 值模式=结构比较（非同一对象）
        return '$S.isCtor(' + v + ', ' + JSON.stringify(lit.name) + ')';
      }
      return v + ' === ' + emitExpr(lit, ctx);
    }
    case 'HirPatIs': {
      const ty = p.type || p.inner;
      const name = ty && ty.name;
      return '$S.typeIs(' + v + ', ' + JSON.stringify(name) + ')';
    }
    case 'HirPatCtor': {
      const name = p.name;
      // 桥接：prelude 形 Option（Some=原值 / None=null；未在本单元声明时）
      if (!ctx.structs.has(name) && name === 'None') return '(' + v + ' == null || $S.isCtor(' + v + ', "None"))';
      if (!ctx.structs.has(name) && name === 'Some') {
        // 双形：prelude Some=tagged；原值裸形（防内建直接返原值）
        let cond2 = '(' + v + ' != null && (' + v + '.__tag ? $S.isCtor(' + v + ', "Some") : true))';
        const argsb = p.args || [];
        const access = '(' + v + '.__tag ? ' + v + '.v : ' + v + ')';
        for (let i = 0; i < argsb.length; i++) {
          cond2 = '(' + cond2 + ' && ' + emitPattern(argsb[i], access, binds, ctx) + ')';
        }
        return cond2;
      }
      let fields = ctx.structs.has(name) ? (ctx.structs.get(name) || []).map(function (f) { return f.name; }) : PRELUDE_CTOR_FIELDS[name];
      if (!fields) { ctx.warnings.push('unresolved-pat-ctor:' + name); fields = []; }
      let cond = '$S.isCtor(' + v + ', ' + JSON.stringify(name) + ')';
      const args = p.args || [];
      for (let i = 0; i < args.length; i++) {
        const sub = args[i];
        const access = (fields[i] !== undefined) ? v + '.' + safeField(fields[i]) : v;
        cond = '(' + cond + ' && ' + emitPattern(sub, access, binds, ctx) + ')';
      }
      return cond;
    }
    case 'HirPatTuple': {
      const items = p.items || [];
      let cond = 'Array.isArray(' + v + ') && ' + v + '.length === ' + items.length;
      for (let i = 0; i < items.length; i++) {
        cond = '(' + cond + ' && ' + emitPattern(items[i], v + '[' + i + ']', binds, ctx) + ')';
      }
      return cond;
    }
    case 'HirPatCond':
      return '(' + emitExpr(p.cond, ctx) + ')';
    case 'HirErrorPattern':
      ctx.errors.push('Error 模式（拒收）：' + (p.pos || '?'));
      return 'false';
  }
  ctx.errors.push('未注册模式 kind（拒收）：' + p.kind + '@' + (p.pos || '?'));
  return 'false';
}
