// t160-gate-run：R4-A0 D-DEEP-COPY notes 化门产品再生成（口径逐字延续 t157-gate-run：t160gate 77 件 + t160stdlib 10 件）
const {spawnSync} = require('child_process');
const fs = require('fs'); const path = require('path');
const root = 'F:\\Sublimation';
const cp = root + '\\frontend\\build\\install\\frontend\\lib\\*';
const java = 'F:\\jdk-17.0.13.11-hotspot\\bin\\java.exe';
const manifest = fs.readFileSync(root + '\\jsoncmp\\manifest\\export-t131mod-list.txt','utf8').split(/\r?\n/).filter(x=>x.trim());
const side = root + '\\jsoncmp\\out-subs\\t160gate';
fs.rmSync(side, {recursive:true, force:true}); fs.mkdirSync(side, {recursive:true});
for (const line of manifest) {
  const parts = line.split('|');
  const src = root + '\\' + parts[0].replace(/\//g,'\\');
  let base = parts[1].replace(/^jsoncmp\/out\/t131mod\//,'');
  if (base === 'examples__\u6a21\u5757\u7cfb\u7edf.json') base = 'examples__\u6a21\u5757\u7cfb\u7edf__\u4e3b.subl.json';
  const dst = side + '\\' + base;
  fs.mkdirSync(path.dirname(dst), {recursive:true});
  try { const r = spawnSync(java, ['-cp',cp,'sublimation.frontend.cli.MainKt','compile',src,'-o',dst],{stdio:['ignore','pipe','pipe'],timeout:240000});
    console.log(base + ' rc=' + r.status); } catch(e) { console.log(base + ' ERROR ' + e.message); }
}
const mod = root + '\\frontend\\src\\test\\resources\\e2e\\examples\\\u6a21\u5757\u7cfb\u7edf';
const std = root + '\\jsoncmp\\out-subs\\t160stdlib';
fs.rmSync(std,{recursive:true,force:true}); fs.mkdirSync(std,{recursive:true});
try { const r2 = spawnSync(java, ['-cp',cp,'sublimation.frontend.cli.MainKt','compile',mod,'-o',std + '\\examples__\u6a21\u5757\u7cfb\u7edf.json'],{stdio:['ignore','pipe','pipe'],timeout:360000});
  console.log('stdlib rc=' + r2.status); } catch(e) { console.log('stdlib ERROR ' + e.message); }
console.log('T160GATE DONE');