// backend-js V1 验收器：守门负例先红 + golden 快照对拍 + 差分台账
// 用法：node src/verify.mjs [products目录] [samples目录]
//   默认 products=../../jsoncmp/out-subs/t162probe samples=../../frontend/src/test/resources/e2e/examples
import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { gate } from './gate.mjs';
import { compileUnit } from './emit.mjs';

const root = process.cwd();
const productsDir = process.argv[2] || root + '/../jsoncmp/out-subs/t162probe';
const samplesDir = process.argv[3] || root + '/../frontend/src/test/resources/e2e/examples';
const genDir = root + '/out';
const preludeSrc = root + '/prelude/prelude.mjs';
const snapDir = root + '/snapshots';
mkdirSync(genDir, { recursive: true });
mkdirSync(snapDir, { recursive: true });

// ---------- 1 负例先红：守门拒收四例 ----------
const neg = [];
function addNeg(name, raw) {
  const g = gate(raw);
  neg.push({ name: name, want: 'REJECT', got: g.ok ? 'PASS-错误放行' : 'REJECT', ok: !g.ok, reasons: g.reasons || [] });
}
addNeg('schema-v4', { schema: 'sublimation/hir@v4', compiler: {}, unit: { path: 'x', mode: 'project' }, ok: true, diagnostics: [], hir: { kind: 'HirFile', entries: [] } });
addNeg('ok-false', { schema: 'sublimation/hir@v3', compiler: {}, unit: { path: 'x', mode: 'project' }, ok: false, diagnostics: [{ severity: 'error', code: 'E-X' }], hir: { kind: 'HirFile', entries: [] } });
addNeg('error-node', { schema: 'sublimation/hir@v3', compiler: {}, unit: { path: 'x', mode: 'project' }, ok: true, diagnostics: [], hir: { kind: 'HirFile', entries: [{ kind: 'HirErrorExpr', pos: '1:1' }] } });
addNeg('no-hir-root', { schema: 'sublimation/hir@v3', compiler: {}, unit: { path: 'x', mode: 'project' }, ok: true, diagnostics: [], hir: { kind: 'HirBlock', entries: [] } });
addNeg('not-object', JSON.parse('"just-a-string"'));
let negFail = 0;
for (const n of neg) if (!n.ok) negFail++;
console.log('=== 守门负例：' + neg.length + ' 例，' + (negFail === 0 ? '全拒 ✓（先红成立）' : negFail + ' 例放行 ✗'));
for (const n of neg) console.log('  ' + n.name + ': ' + n.got + (n.reasons.length ? ' :: ' + n.reasons[0] : ''));

// ---------- 2 golden 对拍 ----------
function extractGolden(srcText) {
  const lines = srcText.replace(/\r\n/g, '\n').split('\n');
  let idx = -1;
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (l.match(/预期输出（每行一个）[：:]/) || l.match(/预期 stdout（compile \+ node，逐行）[：:]/) || l.match(/预期输出（逐行）[：:]/)) { idx = i; break; }
  }
  if (idx < 0) return inlineGolden(lines);
  // header 形：显式逐行预期=完整 golden（声明位=执行位的作者承诺）
  const head = headerItems(lines[idx]);
  // 续行注释（同行 ' / ' 亦入），直到非注释行
  let items = head;
  for (let i = idx + 1; i < lines.length; i++) {
    const tl = lines[i];
    if (tl.trim().indexOf('//') !== 0) break;
    const extra = headerItems(tl);
    if (!extra.length) break;
    items = items.concat(extra);
  }
  return { items: items.map(function (s) { return { v: s, line: null }; }), commentBased: true };
}
function headerItems(l) {
  const m = l.match(/预期[^：:]*[：:]\s*(.*)$/);
  if (!m && l.indexOf(' / ') < 0) return [];
  let tail = m ? m[1] : l.trim().replace(/^\/\/\s*/, '');
  const items = tail.split(' / ').map(function (s) { return s.trim().replace(/\s*\/$/, ''); }).map(function (s) { return s.replace(/（[^（）]*）$/, ''); }).filter(function (s) { return s.length; });
  if (items.length && items[0].indexOf('//') === 0) return [];
  return items;
}
// V2:值样判据——行尾注释剥离（…）后若为值样（数/布/列表渲染/ctor 形/无空白分号 CJK 单 token）
// 取为 golden;非值样注释（说明文字）与无注释 print 行回落到字符串字面量值（print("done") 之属）。
function inlineGolden(lines) {
  const out = [];
  let commentBased = false;
  for (let li = 0; li < lines.length; li++) {
    const l = lines[li];
    if (l.indexOf('print(') < 0) continue;
    const index = l.lastIndexOf('//');
    if (index < 0) { pushLiteralGolden(out, l, li); continue; }
    const value = l.slice(index + 2).trim().replace(/（[^（）]*）$/, '').replace(/\s*\/$/, '');
    if (value.length && isValueLike(value)) { out.push({ v: value, line: li + 1 }); commentBased = true; }
    else pushLiteralGolden(out, l, li);
  }
  return out.length ? { items: out, commentBased: commentBased } : null;
}
function isValueLike(v) {
  if (/^-?\d+(\.\d+)?$/.test(v)) return true;
  if (v === 'true' || v === 'false') return true;
  if (/^\[.*\]$/.test(v) && v.indexOf('（') < 0) return true;
  if (/^[A-Za-z_$][A-Za-z0-9_$]*\(/.test(v) && v.indexOf('（') < 0) return true;
  return !/[\s；:：]/.test(v) && !/[一-龥]/.test(v);
}
function pushLiteralGolden(out, l, li) {
  // 行回落：仅 print("字面量") 取值——非字面（print(expr)）不可静态知，跳过
  const m = /print\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)/.exec(l);
  if (m) out.push({ v: m[1], line: li + 1, fallback: true });
}
function unusedExtractAnchor(lines) {
  for (const l of lines) {
    const m = l.match(/预期输出（每行一个）[：:](.*)$/);
    if (m) return m[1].split(' / ').map(function (s) { return s.trim(); }).filter(function (s) { return s.length; });
    const m2 = l.match(/预期 stdout（compile \+ node，逐行）[：:](.*)$/);
    if (m2) return m2[1].split(' / ').map(function (s) { return s.trim(); }).filter(function (s) { return s.length; });
    const m3 = l.match(/预期输出（逐行）[：:](.*)$/);
    if (m3) return m3[1].split(' / ').map(function (s) { return s.trim(); }).filter(function (s) { return s.length; });
  }
  // 行内注释形态（// 期望 贴 print 行尾）
  const out = [];
  for (const l of lines) {
    if (l.indexOf('print(') < 0) continue;
    const idx = l.lastIndexOf('//');
    if (idx < 0) continue;
    const v = l.slice(idx + 2).trim();
    if (v.length) out.push(v);
  }
  return out.length ? out : null;
}

const samples = readdirSync(samplesDir).filter(function (f) { return f.endsWith('.subl'); });
const ledger = [];
const rows = [];
writeFileSync(genDir + '/prelude.mjs', readFileSync(preludeSrc, 'utf8'), 'utf8');
for (const f of samples) {
  const prod = productsDir + '/' + f + '.json';
  if (!existsSync(prod)) { ledger.push([f, 'SKIP', '无编译产物']); continue; }
  const srcText = readFileSync(samplesDir + '/' + f, 'utf8');
  const goldenRaw = extractGolden(srcText);
  let g;
  try { g = gate(JSON.parse(readFileSync(prod, 'utf8'))); }
  catch (e) { ledger.push([f, 'SKIP', '产物 JSON 解析失败']); continue; }
  if (!g.ok) { ledger.push([f, 'GATE-REJECT', g.reasons.join('|')]); continue; }
  const r = compileUnit(JSON.parse(readFileSync(prod, 'utf8')));
  if (r.errors && r.errors.length) { ledger.push([f, 'EMIT-REJECT', r.errors.slice(0, 2).join('|')]); continue; }
  const jsPath = genDir + '/' + r.name;
  writeFileSync(jsPath, r.code, 'utf8');
  const run = spawnSync(process.execPath, ['--no-warnings', jsPath], { cwd: root, stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8', timeout: 15000 });
  const stdout = run.stdout || '';
  writeFileSync(snapDir + '/' + r.name + '.stdout', stdout, 'utf8');
  const rc = run.status;
  const lane = laneOf(f);
  // 纯字面回落=作者未写预期注释 → NO-GOLDEN（不误判为 FAIL）
  if (!goldenRaw || (!goldenRaw.commentBased && goldenRaw.items.every(function (it) { return it.fallback; }))) {
    ledger.push([f, 'NO-GOLDEN', lane + ' 无预期注释（仅字面回落）']);
    continue;
  }
  const golden = goldenRaw.items;
  const exp = golden.map(function (it) { return it.v; });
  const act = stdout.split('\n').filter(function (s) { return s.length; });
  // 多体样本：print 分布于多个函数声明体——声明位序≠执行位序，按多重集对拍（单体保序）
  const multiBody = isMultiBody(JSON.parse(readFileSync(prod, 'utf8')).hir, golden);
  let hit;
  if (multiBody) {
    const sortJoin = function (a) { return a.slice().sort().join('\u0001'); };
    hit = sortJoin(exp) === sortJoin(act);
  } else {
    hit = exp.length === act.length && exp.every(function (x, i) { return x === act[i]; });
  }
  if (hit && rc === 0) ledger.push([f, 'PASS', lane]);
  else if (lane !== 'V1') ledger.push([f, 'FAIL(范围外:' + lane + ')', 'rc=' + rc + ' 差异: ' + diffBrief(exp, act)]);
  else ledger.push([f, 'FAIL', 'rc=' + rc + ' 差异: ' + diffBrief(exp, act)]);
}

// golden 各值由 HIR print 调用位归属到声明的函数——跨多个声明体=多体样本
function isMultiBody(hir, golden) {
  const ownerOf = {};   // print 调用首行 → 声明名
  let any = false;
  hir.entries.forEach(function (e) {
    if (e.kind !== 'HirFunDecl') return;
    const nm = e.name || '?';
    walkCallPrint(e.body, function (pos) {
      const m = /^(\d+):/.exec(pos || '');
      if (m) { ownerOf[parseInt(m[1], 10)] = nm; any = true; }
    });
  });
  if (!any) return false;
  const owners = new Set();
  golden.forEach(function (it) { if (ownerOf[it.line]) owners.add(ownerOf[it.line]); });
  return owners.size > 1;
}
function walkCallPrint(n, f) {
  if (Array.isArray(n)) { for (const x of n) walkCallPrint(x, f); return; }
  if (!n || typeof n !== 'object') return;
  if (n.kind === 'HirCall' && n.callee && n.callee.kind === 'HirNameRef' && n.callee.name === 'print') f(n.pos);
  for (const k in n) if (k !== 'notes') walkCallPrint(n[k], f);
}
function diffBrief(exp, act) {
  const n = Math.min(exp.length, act.length);
  for (let i = 0; i < n; i++) if (exp[i] !== act[i]) return '#' + (i + 1) + ' 期「' + exp[i] + '」得「' + act[i] + '」';
  if (exp.length !== act.length) return '行数 期' + exp.length + ' 得' + act.length + (act.length ? '（首差后: ' + act[n] + '）' : '');
  return 'rc 差';
}

function laneOf(f) {
  if (f.indexOf('异步') >= 0) return 'V3-async';
  if (f.indexOf('字符串与插值') >= 0) return '非确定(timeNow/rand)';
  if (f.indexOf('副作用与unchecked') >= 0 || f.indexOf('可变性控制') >= 0 || f.indexOf('特性巡礼') >= 0 || f.indexOf('lambda与闭包') >= 0 || f.indexOf('类型系统') >= 0 || f.indexOf('高阶函数与lambda') >= 0) return 'V2-deepcopy';
  if (f.indexOf('脚本求值桥') >= 0) return 'V1.5-sublEval';
  return 'V1';
}

console.log('');
console.log('=== golden 对拍（' + samples.length + ' 样本）===');
for (const row of ledger) console.log('  ' + row[0] + ' :: ' + row[1] + (row[2] ? ' :: ' + String(row[2]).slice(0, 150) : ''));
const cnt = {};
for (const row of ledger) cnt[row[1].split('(')[0]] = (cnt[row[1].split('(')[0]] || 0) + 1;
console.log('汇总: ' + JSON.stringify(cnt));
writeFileSync(root + '/ledger.txt', ledger.map(function (r) { return r.join('\t'); }).join('\n') + '\n', 'utf8');
console.log('台账: backend-js/ledger.txt');
