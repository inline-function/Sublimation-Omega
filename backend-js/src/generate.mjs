// backend-js 生成驱动：envelope(s) → JS 件（守门先行，守门负例=拒收报清单）
// 用法：node src/generate.mjs <envelope.json|envelope目录> <outdir>
import { readFileSync, readdirSync, mkdirSync, existsSync, statSync, writeFileSync } from 'node:fs';
import { gate } from './gate.mjs';
import { compileUnit } from './emit.mjs';

const args = process.argv.slice(2);
const src = args[0];
const outdir = args[1] || 'out';
mkdirSync(outdir, { recursive: true });

const files = [];
if (statSync(src).isDirectory()) {
  for (const f of readdirSync(src)) if (f.endsWith('.json')) files.push(src + '/' + f);
} else {
  files.push(src);
}

writeFileSync(outdir + '/prelude.mjs', readFileSync(root_prelude(), 'utf8'), 'utf8');
const report = [];
let rejected = 0;
for (const f of files) {
  let raw;
  try { raw = JSON.parse(readFileSync(f, 'utf8')); }
  catch (e) { report.push([f, 'GATE-FAIL', ['JSON 解析失败: ' + e.message]]); rejected++; continue; }
  const g = gate(raw);
  if (!g.ok) { report.push([f, 'GATE-REJECT', g.reasons]); rejected++; continue; }
  const r = compileUnit(raw);
  if (r.errors && r.errors.length) { report.push([f, 'EMIT-REJECT', r.errors]); rejected++; continue; }
  const jsPath = outdir + '/' + r.name;
  writeFileSync(jsPath, r.code, 'utf8');
  report.push([f, 'OK', r.name, r.warnings || []]);
}
for (const row of report) {
  if (row[1] === 'OK') console.log(row[2] + ' <- ' + row[0] + (row[3] && row[3].length ? '  警告: ' + row[3].slice(0, 3).join(' ; ') : ''));
  else console.log(row[1] + ' <- ' + row[0] + ' :: ' + row[2].join(' | '));
}
function root_prelude() { return process.cwd() + '/prelude/prelude.mjs'; }
console.log('GEN DONE: ' + report.length + ' 件，拒收 ' + rejected + ' 件');
