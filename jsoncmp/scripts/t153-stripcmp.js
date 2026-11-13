// t153-stripcmp：诊断滤码逐字比对（t152 wstrip 系保留;新增 d*=滤批二 7 新码,b*=滤 12 码,strippos=剥 pos+message）。
// 用法：node t153-stripcmp.js <a.json> <b.json> <plain|wstrip|strippos|wstrippos|dstrip|dstrippos|bstrip|bstrippos>
// 输出：SAME | DIFF
const fs = require('fs');
const NEWW = ["W-REF-ESCAPE","W-DEEP-COPY-LOOP","W-RESULT-IGNORED","W-REC-NON-TAIL","W-BRANCH-UNREACHABLE"];
const NEWH = ["H-CONS-BUILD","H-UNUSED","H-EXPLICIT-TYPE-ARG","H-IMPURE-REDUNDANT","D-SHADOW","D-VARIANCE","D-TRAIT-LOCAL"];
const a = fs.readFileSync(process.argv[2], "utf8");
const b = fs.readFileSync(process.argv[3], "utf8");
const mode = process.argv[4] || "plain";
const deep = (n) => {
  if (Array.isArray(n)) return n.map(deep).filter((x) => x !== null);
  if (n && typeof n === "object") {
    if (/^(w|b)/.test(mode) && NEWW.indexOf(n.code) >= 0) return null;
    if (/^(d|b)/.test(mode) && NEWH.indexOf(n.code) >= 0) return null;
    const h = {};
    Object.keys(n).forEach((k) => {
      if (/pos$/.test(mode) && (k === "pos" || k === "message")) return;
      h[k] = deep(n[k]);
    });
    return h;
  }
  return n;
};
process.stdout.write(JSON.stringify(deep(JSON.parse(a))) === JSON.stringify(deep(JSON.parse(b))) ? "SAME" : "DIFF");