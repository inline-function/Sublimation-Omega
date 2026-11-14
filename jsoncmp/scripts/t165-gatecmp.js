// t165-gatecmp：P-L1（公共挂载入口，CLI 通道统一）台账三段
// ② 68 门 vs t122n（死冻锚分类）：须与 t160 台账零漂移——38/11/5/3/6/4/1 = 68 闭合
// ③ vs t160gate 精确差分：77 件**全部逐字同**——P-L1 的验收断言=CLI 行为零变化
//   （单文件 check/compile 改走 compileFrontendWithStdlib 公共通道，装配逐字等价）
// ④ t165stdlib vs t160stdlib：十件逐字同（stdlib 编译面零触及）
// 用法：node t165-gatecmp.js（读 jsoncmp/out-subs 各锚，输出台账；任何差 → stderr + rc=1x）
const fs = require('fs');
const base = 'F:\\Sublimation\\jsoncmp\\out-subs';
const t122n = 'F:\\Sublimation\\jsoncmp\\out\\t122n';
const curr = base + '\\t165gate';
const prev = base + '\\t160gate';   // ③ 前后门（P-L1 零差断言）
const std = base + '\\t165stdlib', stdPrev = base + '\\t160stdlib';
const W5 = ['W-REF-ESCAPE','W-DEEP-COPY-LOOP','W-RESULT-IGNORED','W-REC-NON-TAIL','W-BRANCH-UNREACHABLE'];
const H7 = ['H-CONS-BUILD','H-UNUSED','H-EXPLICIT-TYPE-ARG','H-IMPURE-REDUNDANT','D-SHADOW','D-VARIANCE','D-TRAIT-LOCAL'];
const D2 = ['D-EFFECT-LOCAL','D-EFFECT-ESCAPE'];
const ALL = W5.concat(H7).concat(D2);
// t160 预期差四件（三件 R4-A0 深拷 authorized + 模块样本 1）——t165 须延续同分类（零差断言下不变）
const expOld = ['examples__副作用与unchecked.subl.json','examples__特性巡礼.subl.json','examples__类型系统.subl.json'];
const expMod = ['examples__模块系统__主.subl.json'];
const expAll = expOld.concat(expMod);
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
const cmp1 = (a, b, codes, posmsg) =>
  JSON.stringify(deep(JSON.parse(fs.readFileSync(a, 'utf8')), codes, posmsg)) ===
  JSON.stringify(deep(JSON.parse(fs.readFileSync(b, 'utf8')), codes, posmsg));

// ---- ② 68 门分类（口径延续 t160；断言分类数零漂移）----
const files2 = fs.readdirSync(curr).filter(f => f.endsWith('.json')).filter(f => fs.existsSync(t122n + '\\' + f));
const cls = {same: 0, strippos: 0, pureW: [], pureD: [], mix: [], green: 0, exp: [], fail: []};
files2.forEach(f => {
  const pa = t122n + '\\' + f, pb = curr + '\\' + f;
  const a = fs.readFileSync(pa, 'utf8'), b = fs.readFileSync(pb, 'utf8');
  if (a === b) { cls.same++; return; }
  if (cmp1(pa, pb, null, true)) { cls.strippos++; return; }
  if (cmp1(pa, pb, W5, true)) { cls.pureW.push(f); return; }
  if (cmp1(pa, pb, H7, true)) { cls.pureD.push(f); return; }
  if (cmp1(pa, pb, ALL, true)) { cls.mix.push(f); return; }
  if (f === 'examples__手动证明.subl.json') { if (/"ok":\s*true/.test(b)) { cls.green++; return; } }
  if (expAll.indexOf(f) >= 0) { cls.exp.push(f); return; }
  cls.fail.push(f);
});
cls.mix.forEach(f => { if (fullW.indexOf(f) < 0) cls.fail.push(f + '（混合增量含未登记 W 件）'); });
cls.pureW.forEach(f => { if (fullW.indexOf(f) < 0) cls.fail.push(f + '（纯 W 增量未登记）'); });
if (cls.fail.length) { console.error('FAIL-2 未登记差: ' + cls.fail.join(' ; ')); process.exit(12); }
if (cls.exp.length !== 4 || cls.exp.slice().sort().join(',') !== expAll.slice().sort().join(',')) { console.error('FAIL-2 预期差须恰为 t160 同四件: ' + cls.exp.join(',')); process.exit(12); }
const tot2 = cls.same + cls.strippos + cls.pureW.length + cls.pureD.length + cls.mix.length + cls.exp.length + cls.green;
if (tot2 !== 68) { console.error('FAIL-2 总数须 68 实得 ' + tot2); process.exit(12); }
if (cls.same !== 38 || cls.strippos !== 11 || cls.pureW.length !== 5 || cls.pureD.length !== 3 || cls.mix.length !== 6 || cls.green !== 1) {
  console.error('FAIL-2 分类数与 t160 台账漂移: ' + cls.same + '/' + cls.strippos + '/' + cls.pureW.length + '/' + cls.pureD.length + '/' + cls.mix.length + '/' + cls.exp.length + '/' + cls.green); process.exit(12);
}
console.log('[2] 68 门 vs t122n 闭合（零漂移于 t160 台账）: same=' + cls.same + ' strippos=' + cls.strippos +
  ' pureW=' + cls.pureW.length + ' pureD=' + cls.pureD.length + ' mix=' + cls.mix.length +
  ' exp=' + cls.exp.length + ' green=' + cls.green + ' =68');

// ---- ③ vs t160gate 精确差分：77 件全部逐字同（P-L1 零差断言）----
const files3 = fs.readdirSync(prev).filter(f => f.endsWith('.json'));
let same3 = 0; const diffs = [];
files3.forEach(f => {
  const pb = curr + '\\' + f;
  if (!fs.existsSync(pb)) { diffs.push(f + '（缺失）'); return; }
  if (fs.readFileSync(prev + '\\' + f, 'utf8') === fs.readFileSync(pb, 'utf8')) same3++;
  else diffs.push(f);
});
const extra = fs.readdirSync(curr).filter(f => f.endsWith('.json')).filter(f => !fs.existsSync(prev + '\\' + f));
if (diffs.length || extra.length) {
  console.error('FAIL-3 vs t160gate 逐字同=' + same3 + '/' + files3.length + ' 差: ' + diffs.concat(extra).join(' ; '));
  process.exit(13);
}
console.log('[3] vs t160gate 精确差分：逐字同=' + same3 + '/' + files3.length + '（P-L1 零差断言成立）');

// ---- ④ stdlib 基线：t165stdlib vs t160stdlib 十件逐字同 ----
const files4 = fs.readdirSync(stdPrev).filter(f => f.endsWith('.json'));
let same4 = 0; const diffs4 = [];
files4.forEach(f => {
  const pb = std + '\\' + f;
  if (!fs.existsSync(pb)) { diffs4.push(f + '（缺失）'); return; }
  if (fs.readFileSync(stdPrev + '\\' + f, 'utf8') === fs.readFileSync(pb, 'utf8')) same4++;
  else diffs4.push(f);
});
if (diffs4.length || same4 !== 10) { console.error('FAIL-4 stdlib 逐字同=' + same4 + '/10 差: ' + diffs4.join(' ; ')); process.exit(14); }
console.log('[4] t165stdlib vs t160stdlib：十件逐字同（stdlib 编译面零触及）');
