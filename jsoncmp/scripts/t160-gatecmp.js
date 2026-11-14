// t160-gatecmp：R4-A0（D-DEEP-COPY 深拷指令 notes 化）台账四段
// ② 68 门 vs t122n（死冻锚分类——三件深拷文件落「预期差」authorized 格，断言与 t157 台账零漂移）
// ③ vs t157gate 精确差分：74 件逐字同 + 3 件 authorized（深拷 notes 加性 + deepCopy 布尔真化）
//   ——authorized 件做结构校验：差分恰为「节点 notes 尾追加 D-DEEP-COPY supplement note
//   （键集 {kind,code,message,pos,from,to[,deepElements]}）+ deepCopy false→true 翻转」两类，
//   envelope/diagnostics 零触;note 消息/pos 与同件 D-DEEP-COPY 诊断逐字同（单源纪律）;站点全覆盖
// ④ stdlib vs t143stdlib（口径延续 t157）+ ④b t160stdlib vs t157stdlib 十件逐字同（stdlib 零深拷站点）
// 用法：node t160-gatecmp.js（读 jsoncmp/out-subs 各锚，输出台账;未登记差 → stderr + rc=1x）
const fs = require('fs');
const base = 'F:\\Sublimation\\jsoncmp\\out-subs';
const t122n = 'F:\\Sublimation\\jsoncmp\\out\\t122n';
const prev = base + '\\t157gate', curr = base + '\\t160gate';   // ③ 前后门
const t143 = base + '\\t143stdlib', std = base + '\\t160stdlib', stdPrev = base + '\\t157stdlib';
const W5 = ['W-REF-ESCAPE','W-DEEP-COPY-LOOP','W-RESULT-IGNORED','W-REC-NON-TAIL','W-BRANCH-UNREACHABLE'];
const H7 = ['H-CONS-BUILD','H-UNUSED','H-EXPLICIT-TYPE-ARG','H-IMPURE-REDUNDANT','D-SHADOW','D-VARIANCE','D-TRAIT-LOCAL'];
const D2 = ['D-EFFECT-LOCAL','D-EFFECT-ESCAPE'];
const ALL = W5.concat(H7).concat(D2);
// R4-A0 authorized 深拷 notes 件（t157gate 即含 D-DEEP-COPY 诊断站点的三件）
const dcOld = ['examples__副作用与unchecked.subl.json','examples__特性巡礼.subl.json','examples__类型系统.subl.json'];
const expOld = dcOld.concat(['examples__模块系统__主.subl.json']);
const fullW = ['core__collection__list.subl.json','core__collection__map.subl.json','core__collection__set.subl.json','core__math__math.subl.json','examples__日常-数列与区间.subl.json','core__collection__traits.subl.json','examples__上下文弥散.subl.json','examples__日常-容器范型.subl.json','examples__日常-账单统计.subl.json','examples__日常-通讯录.subl.json','examples__标准库集合.subl.json','examples__高阶函数与lambda.subl.json','examples__高阶类型.subl.json','examples___scratch_bill.subl.json'];
const deep = (n, codes, posmsg) => {
  if (Array.isArray(n)) return n.map(x => deep(x, codes, posmsg)).filter(x => x !== null);
  if (n && typeof n === 'object') {
    if (codes && n.code !== undefined && codes.indexOf(n.code) >= 0) return null;
    const h = {};
    Object.keys(n).forEach(k => { if (posmsg && (k === 'pos' || k === 'message')) return; h[k] = deep(n[k], codes, posmsg); });
    return h;
  }
  return n;
};
const cmp = (a, b, codes, posmsg) =>
  JSON.stringify(deep(JSON.parse(fs.readFileSync(a, 'utf8')), codes, posmsg)) ===
  JSON.stringify(deep(JSON.parse(fs.readFileSync(b, 'utf8')), codes, posmsg));

// ---- ② 68 门分类（口径延续 t157;断言分类数零漂移）----
const files = fs.readdirSync(curr).filter(f => f.endsWith('.json')).filter(f => fs.existsSync(t122n + '\\' + f));
const cls = {same: 0, strippos: 0, pureW: [], pureD: [], mix: [], green: 0, exp: [], fail: []};
files.forEach(f => {
  const pa = t122n + '\\' + f, pb = curr + '\\' + f;
  const a = fs.readFileSync(pa, 'utf8'), b = fs.readFileSync(pb, 'utf8');
  if (a === b) { cls.same++; return; }
  if (cmp(pa, pb, null, true)) { cls.strippos++; return; }
  if (cmp(pa, pb, W5, true)) { cls.pureW.push(f); return; }
  if (cmp(pa, pb, H7, true)) { cls.pureD.push(f); return; }
  if (cmp(pa, pb, ALL, true)) { cls.mix.push(f); return; }
  if (f === 'examples__手动证明.subl.json') { if (/"ok":\s*true/.test(b)) { cls.green++; return; } }
  if (expOld.indexOf(f) >= 0) { cls.exp.push(f); return; }
  cls.fail.push(f);
});
cls.mix.forEach(f => { if (fullW.indexOf(f) < 0) cls.fail.push(f + '（混合增量含未登记 W 件）'); });
cls.pureW.forEach(f => { if (fullW.indexOf(f) < 0) cls.fail.push(f + '（纯 W 增量未登记）'); });
if (cls.fail.length) { console.error('FAIL-2 未登记差: ' + cls.fail.join(' ; ')); process.exit(12); }
if (cls.exp.length !== 4 || cls.exp.slice().sort().join(',') !== expOld.slice().sort().join(',')) { console.error('FAIL-2 预期差须恰为四件（含三件 R4-A0 深拷 authorized）: ' + cls.exp.join(',')); process.exit(12); }
const tot2 = cls.same + cls.strippos + cls.pureW.length + cls.pureD.length + cls.mix.length + cls.exp.length + cls.green;
if (tot2 !== 68) { console.error('FAIL-2 总数须 68 实得 ' + tot2); process.exit(12); }
if (cls.same !== 38 || cls.strippos !== 11 || cls.pureW.length !== 5 || cls.pureD.length !== 3 || cls.mix.length !== 6 || cls.green !== 1) { console.error('FAIL-2 分类数与 t157 台账漂移（38/11/5/3/6/1）: ' + cls.same + '/' + cls.strippos + '/' + cls.pureW.length + '/' + cls.pureD.length + '/' + cls.mix.length + '/' + cls.green); process.exit(12); }
console.log('[2] 68门 vs t122n: SAME=' + cls.same + ' 剥差同=' + cls.strippos + ' 纯W批一=' + cls.pureW.length + ' 纯批二=' + cls.pureD.length + ' 混合=' + cls.mix.length + ' 预期差=' + cls.exp.length + ' 转绿=' + cls.green + ' 总=' + tot2);
console.log('[2] 预期差件（authorized）: ' + cls.exp.join(', '));

// ---- ③ vs t157gate：精确结构差分 ----
const isDcNote = n => n && typeof n === 'object' && !Array.isArray(n) &&
  n.kind === 'supplement' && n.code === 'D-DEEP-COPY' && typeof n.message === 'string' && typeof n.pos === 'string' &&
  typeof n.from === 'string' && typeof n.to === 'string' &&
  Object.keys(n).every(k => ['kind','code','message','pos','from','to','deepElements'].indexOf(k) >= 0) &&
  (n.deepElements === undefined || (typeof n.deepElements === 'number' && n.deepElements > 0));
// 并行走两树：仅允许 notes 数组尾追加合法 D-DEEP-COPY note + deepCopy false→true 翻转;其余须深同
const hirCmp = (a, b, path, rep) => {
  if (a === b) return true;
  if (/\.deepCopy$/.test(path) && a === false && b === true) { rep.flips.push(path); return true; }
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length > b.length) { rep.bad.push(path + ' 数组缩短'); return false; }
    let ok = true;
    for (let i = 0; i < a.length; i++) if (!hirCmp(a[i], b[i], path + '[' + i + ']', rep)) ok = false;
    if (b.length > a.length) {
      if (!/\.notes$/.test(path)) { rep.bad.push(path + ' 非尾追加'); return false; }
      for (let i = a.length; i < b.length; i++) {
        if (!isDcNote(b[i])) { rep.bad.push(path + '[' + i + '] 追加对象非合法 D-DEEP-COPY note'); ok = false; continue; }
        rep.notes.push({node: path.replace(/^\.hir/, ''), note: b[i]});
      }
    }
    return ok;
  }
  if (a && b && typeof a === 'object' && typeof b === 'object') {
    const ka = Object.keys(a), kb = Object.keys(b);
    if (ka.length !== kb.length || ka.join(',') !== kb.join(',')) { rep.bad.push(path + ' 键集异'); return false; }
    let ok = true;
    for (const k of ka) if (!hirCmp(a[k], b[k], path + '.' + k, rep)) ok = false;
    return ok;
  }
  rep.bad.push(path + ' 值异 ' + JSON.stringify(a) + ' → ' + JSON.stringify(b));
  return false;
};
let same3 = 0, auth3 = [], broken3 = [], totNotes = 0, totFlips = 0;
fs.readdirSync(curr).filter(f => f.endsWith('.json')).forEach(f => {
  const pa = prev + '\\' + f;
  if (!fs.existsSync(pa)) { broken3.push(f + ' t157gate 缺件'); return; }
  const ta = fs.readFileSync(pa, 'utf8'), tb = fs.readFileSync(curr + '\\' + f, 'utf8');
  if (ta === tb) { same3++; return; }
  if (dcOld.indexOf(f) < 0) { broken3.push(f + ' 非登记件差'); return; }
  const ja = JSON.parse(ta), jb = JSON.parse(tb);
  // envelope（除 hir）须逐字同：compiler/unit/ok/diagnostics/schema
  const ea = JSON.parse(JSON.stringify(ja)), eb = JSON.parse(JSON.stringify(jb));
  delete ea.hir; delete eb.hir;
  if (JSON.stringify(ea) !== JSON.stringify(eb)) { broken3.push(f + ' envelope/diagnostics 零触违反'); return; }
  const rep = {bad: [], notes: [], flips: []};
  hirCmp(ja.hir, jb.hir, '.hir', rep);
  if (rep.bad.length) { broken3.push(f + ' 结构差分越界: ' + rep.bad.slice(0, 3).join(' ; ')); return; }
  // 单源纪律：每条追加 note 的 message/pos 须与同件 D-DEEP-COPY 诊断逐字同（诊断镜像核验）
  const msgs = ja.diagnostics.filter(d => d.code === 'D-DEEP-COPY').map(d => d.message + '@' + d.pos);
  rep.notes.forEach(x => {
    if (msgs.indexOf(x.note.message + '@' + x.note.pos) < 0) rep.bad.push(x.node + ' note 与 D-DEEP-COPY 诊断不同源（message/pos）');
  });
  if (rep.bad.length) { broken3.push(f + ' 单源校验失败: ' + rep.bad.slice(0, 3).join(' ; ')); return; }
  // 站点全覆盖：本件 D-DEEP-COPY 诊断数须 == 追加 note 数
  if (rep.notes.length !== msgs.length) { broken3.push(f + ' 站点覆盖不全（诊断 ' + msgs.length + ' → notes ' + rep.notes.length + '）'); return; }
  totNotes += rep.notes.length; totFlips += rep.flips.length;
  auth3.push(f + '（notes +' + rep.notes.length + '，deepCopy 真化 ' + rep.flips.length + ' 位）');
});

if (broken3.length) { console.error('FAIL-3 未登记差/越界（vs t157gate）: ' + broken3.join(' ; ')); process.exit(13); }
if (same3 !== 74 || auth3.length !== 3 || totNotes !== 15 || totFlips !== 7) { console.error('FAIL-3 期望 74 逐字同 + 3 authorized（15 notes / 7 真化位）实得 ' + same3 + '/' + auth3.length + '/' + totNotes + '/' + totFlips); process.exit(13); }
console.log('[3] vs t157gate: 逐字同=' + same3 + ' authorized深拷notes件=' + auth3.length + '（notes +' + totNotes + '，deepCopy 真化 ' + totFlips + ' 位）零未登记差——R4-A0 加性通道（envelope/diagnostics 零触）');
auth3.forEach(x => console.log('[3]   ' + x));

// ---- ④ stdlib vs t143stdlib（口径延续 t157）----
const sf = fs.readdirSync(std).filter(f => f.endsWith('.json'));
let ssame = 0, swinc = [], sdinc = [], smix = [], snew = [], sbroken = [];
sf.forEach(f => {
  const pa = t143 + '\\' + f;
  if (!fs.existsSync(pa)) { snew.push(f); return; }
  const pb = std + '\\' + f;
  const a = fs.readFileSync(pa, 'utf8'), b = fs.readFileSync(pb, 'utf8');
  if (a === b) { ssame++; return; }
  if (cmp(pa, pb, W5, false)) { swinc.push(f); return; }
  if (cmp(pa, pb, H7, false)) { sdinc.push(f); return; }
  if (cmp(pa, pb, ALL, false)) { smix.push(f); return; }
  if (f === 'examples__模块系统__list.json') return;   // t146 既有登记差（构造族迁源面）
  sbroken.push(f);
});
if (sbroken.length) { console.error('FAIL-4 未登记差（stdlib vs t143）: ' + sbroken.join(' ; ')); process.exit(14); }
if (ssame !== 4 || swinc.length !== 3 || sdinc.length !== 1 || smix.length !== 0 || snew.length !== 1 || snew[0].indexOf('axioms') < 0) { console.error('FAIL-4 stdlib 漂移（期望 4/3/1/0 + axioms 新增 1）实得 ' + ssame + '/' + swinc.length + '/' + sdinc.length + '/' + smix.length + '/' + snew.join(',')); process.exit(14); }
console.log('[4] stdlib vs t143stdlib: 逐字同=' + ssame + ' 纯W批一=' + swinc.length + ' 纯批二=' + sdinc.length + ' 混合=' + smix.length + ' list件登记差=1 axioms新增件=1 总=' + sf.length + '（口径延续 t157）');

// ---- ④b t160stdlib vs t157stdlib：十件逐字同（stdlib 零深拷站点 ⇒ 默认态零产）----
let sb10 = [];
fs.readdirSync(std).forEach(f => {
  const pa = stdPrev + '\\' + f;
  if (!fs.existsSync(pa) || fs.readFileSync(pa, 'utf8') !== fs.readFileSync(std + '\\' + f, 'utf8')) sb10.push(f);
});
if (sb10.length > 0 || fs.readdirSync(std).length !== 10) { console.error('FAIL-4b stdlib 须十件逐字同（零深拷站点 ⇒ 默认态零产）: ' + sb10.join(' ; ')); process.exit(17); }
console.log('[4b] vs t157stdlib: 十件逐字同（R4-A0 默认态零产——stdlib 零深拷站点）');

console.log('[ledger] R4-A0 D-DEEP-COPY notes 化: 3 件 / +' + totNotes + ' notes / ' + totFlips + ' 位 deepCopy 真化；其余 74 件 + stdlib 10 件逐字同（默认态零产）');
console.log('T160GATECMP DONE');
