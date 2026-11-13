// t156-gatecmp：B3 axioms 公理库台账三段（② 68 门 vs t122n 死冻锚分类;③ vs t155gate 精确差分;④ stdlib vs t143stdlib）。
// 用法：node t156-gatecmp.js   （读 jsoncmp/out-subs 各锚，输出台账;未登记差 → stderr + rc=1x）
const fs = require('fs');
const base = 'F:\\Sublimation\\jsoncmp\\out-subs';
const t122n = 'F:\\Sublimation\\jsoncmp\\out\\t122n';
const prev = base + '\\t155gate', curr = base + '\\t156gate';   // ③ 前后门
const t143 = base + '\\t143stdlib', std = base + '\\t156stdlib';
const W5 = ['W-REF-ESCAPE','W-DEEP-COPY-LOOP','W-RESULT-IGNORED','W-REC-NON-TAIL','W-BRANCH-UNREACHABLE'];
const H7 = ['H-CONS-BUILD','H-UNUSED','H-EXPLICIT-TYPE-ARG','H-IMPURE-REDUNDANT','D-SHADOW','D-VARIANCE','D-TRAIT-LOCAL'];
const D2 = ['D-EFFECT-LOCAL','D-EFFECT-ESCAPE'];
const ALL = W5.concat(H7).concat(D2);
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

// t152 既有登记集（authorized W 批一台账 + 预期差件 + 转绿件——口径逐字延续）
const fullW = ['core__collection__list.subl.json','core__collection__map.subl.json','core__collection__set.subl.json','core__math__math.subl.json','examples___scratch_bill.subl.json','examples__日常-容器范型.subl.json','examples__日常-数列与区间.subl.json','examples__日常-账单统计.subl.json','examples__日常-通讯录.subl.json','examples__标准库集合.subl.json','examples__高阶函数与lambda.subl.json'];
const expOld = ['examples__副作用与unchecked.subl.json','examples__特性巡礼.subl.json','examples__类型系统.subl.json','examples__模块系统__主.subl.json'];
const GREEN = 'examples__手动证明.subl.json';

// ---- ② 68 门分类 ----
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
  if (f === GREEN) { if (/"ok":\s*true/.test(b)) { cls.green++; return; } }
  if (expOld.indexOf(f) >= 0) { cls.exp.push(f); return; }
  cls.fail.push(f);
});
cls.mix.forEach(f => { if (fullW.indexOf(f) < 0) cls.fail.push(f + '（混合增量含未登记 W 件）'); });
cls.pureW.forEach(f => { if (fullW.indexOf(f) < 0) cls.fail.push(f + '（纯 W 增量未登记）'); });
if (cls.fail.length) { console.error('FAIL-2 未登记差: ' + cls.fail.join(' ; ')); process.exit(12); }
console.log('[2] 68门 vs t122n: SAME=' + cls.same + ' 剥差同=' + cls.strippos +
  ' 纯W批一=' + cls.pureW.length + ' 纯批二=' + cls.pureD.length + ' 混合=' + cls.mix.length +
  ' 预期差=' + cls.exp.length + ' 转绿=' + cls.green + ' 总=' +
  (cls.same + cls.strippos + cls.pureW.length + cls.pureD.length + cls.mix.length + cls.exp.length + cls.green));
console.log('[2] 纯批二件: ' + (cls.pureD.join(', ') || '（无）'));
console.log('[2] 混合件（批一W+批二H/D）: ' + (cls.mix.join(', ') || '（无）'));
console.log('[2] 纯W件（批一延续）: ' + (cls.pureW.join(', ') || '（无）'));

// ---- ③ vs t155gate 精确差分（全 envelope——68 用户门 + 模块系统件 + stdlib 子件;B3 只许新增 axioms 件）----
let same3 = 0, inc3 = 0, broken3 = [], newAx = [];
fs.readdirSync(curr).filter(f => f.endsWith('.json')).forEach(f => {
  const pa = prev + '\\' + f;
  if (!fs.existsSync(pa)) {
    if (f.indexOf('axioms') >= 0) { newAx.push(f); return; }   // t156 新增件（axioms 挂载并入默认表）
    broken3.push(f + ' (t155gate 缺——非 axioms 新件)'); return; }
  const a = fs.readFileSync(pa, 'utf8'), b = fs.readFileSync(curr + '\\' + f, 'utf8');
  if (a === b) { same3++; return; }
  if (cmp(pa, curr + '\\' + f, ALL, false)) { inc3++; return; }
  broken3.push(f + ' (未登记差)');
});
if (broken3.length) { console.error('FAIL-3 未登记差（vs t155gate）: ' + broken3.join(' ; ')); process.exit(13); }
if (newAx.length === 0) { console.error('FAIL-3 须至少一枚 axioms 新件'); process.exit(16); }
console.log('[3] vs t155gate: 逐字同=' + same3 + ' 滤14码同=' + inc3 + ' axioms新增件=' + newAx.length +
  ' 零未登记差（既有件逐字不动——B3 只加模块，不改既件）');

// ---- ④ stdlib vs t143stdlib（t156：原 9 件闭合 + axioms 新增件 1 枚）----
const sf = fs.readdirSync(std).filter(f => f.endsWith('.json'));
let ssame = 0, swinc = [], sdinc = [], smix = [], snew = [], sbroken = [];
sf.forEach(f => {
  const pa = t143 + '\\' + f;
  if (!fs.existsSync(pa)) { snew.push(f + ' (t156 新增件——B3 axioms)'); return; }
  const pb = std + '\\' + f;
  const a = fs.readFileSync(pa, 'utf8'), b = fs.readFileSync(pb, 'utf8');
  if (a === b) { ssame++; return; }
  if (cmp(pa, pb, W5, false)) { swinc.push(f); return; }
  if (cmp(pa, pb, H7, false)) { sdinc.push(f); return; }
  if (cmp(pa, pb, ALL, false)) { smix.push(f); return; }
  if (f === 'examples__模块系统__list.json') return;   // t146 既有登记差（构造族迁源面）
  sbroken.push(f);
});
if (sbroken.length) { console.error('FAIL-4 未登记差（stdlib）: ' + sbroken.join(' ; ')); process.exit(14); }
if (snew.length !== 1 || snew[0].indexOf('axioms') < 0) { console.error('FAIL-4 新增件须恰为 axioms 一枚: ' + snew.join(' ; ')); process.exit(15); }
console.log('[4] stdlib vs t143stdlib: 逐字同=' + ssame + ' 纯W批一=' + swinc.length + ' 纯批二=' + sdinc.length +
  ' 混合=' + smix.length + ' list件登记差=1 axioms新增件=1 总=' + sf.length);
console.log('[4] 纯批二件: ' + (sdinc.join(', ') || '（无）') + ' | 混合件: ' + (smix.join(', ') || '（无）') + ' | 纯W件: ' + (swinc.join(', ') || '（无）'));

// ---- 新码归因台账 ----
const cnt = {}; H7.concat(D2).forEach(c => cnt[c] = 0);
const perFile = {};
files.concat(sf.map(f => 'STD/' + f)).forEach(f => {
  const path = (f.startsWith('STD/') ? std : curr) + '\\' + f.replace('STD/', '');
  const t = fs.readFileSync(path, 'utf8');
  const hits = [];
  H7.concat(D2).forEach(c => { const n = t.split('"code": "' + c + '"').length - 1; if (n > 0) { cnt[c] += n; hits.push(c + 'x' + n); } });
  if (hits.length) perFile[f] = hits.join(' ');
});
console.log('[ledger] 新码总量: ' + JSON.stringify(cnt));
Object.keys(perFile).sort().forEach(f => console.log('[ledger] ' + f + ' :: ' + perFile[f]));
console.log('T156GATECMP DONE');
