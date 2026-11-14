// t165-gate-run：P-L1（公共挂载入口）门产品再生成（口径逐字延续 t160-gate-run：t165gate 77 件 + t165stdlib 10 件）
// 与 t160 的唯一差异：使用 t165 构建（compileFrontendWithStdlib 通道）导出——预期与 t160gate 逐字零差
const {spawnSync} = require('child_process');
const fs = require('fs'); const path = require('path');
const root = 'F:\\Sublimation';
const cp = root + '\\frontend\\build\\install\\frontend\\lib\\*';
const java = 'F:\\jdk-17.0.13.11-hotspot\\bin\\java.exe';
const manifest = fs.readFileSync(root + '\\jsoncmp\\manifest\\export-t131mod-list.txt','utf8').split(/\r?\n/).filter(x=>x.trim());
const side = root + '\\jsoncmp\\out-subs\\t165gate';
fs.rmSync(side, {recursive:true, force:true}); fs.mkdirSync(side, {recursive:true});
for (const line of manifest) {
  const parts = line.split('|');
  const src = root + '\\' + parts[0].replace(/\//g,'\\');
  let base = parts[1].replace(/^jsoncmp\/out\/t131mod\//,'');
  if (base === 'examples__模块系统.json') base = 'examples__模块系统__主.subl.json';
  const dst = side + '\\' + base;
  fs.mkdirSync(path.dirname(dst), {recursive:true});
  try { const r = spawnSync(java, ['-cp',cp,'sublimation.frontend.cli.MainKt','compile',src,'-o',dst],{stdio:['ignore','pipe','pipe'],timeout:240000});
    console.log(base + ' rc=' + r.status); } catch(e) { console.log(base + ' ERROR ' + e.message); }
}
const mod = root + '\\frontend\\src\\test\\resources\\e2e\\examples\\模块系统';
const std = root + '\\jsoncmp\\out-subs\\t165stdlib';
fs.rmSync(std,{recursive:true,force:true}); fs.mkdirSync(std,{recursive:true});
try { const r2 = spawnSync(java, ['-cp',cp,'sublimation.frontend.cli.MainKt','compile',mod,'-o',std + '\\examples__模块系统.json'],{stdio:['ignore','pipe','pipe'],timeout:360000});
  console.log('stdlib rc=' + r2.status); } catch(e) { console.log('stdlib ERROR ' + e.message); }
console.log('T165GATE DONE');
