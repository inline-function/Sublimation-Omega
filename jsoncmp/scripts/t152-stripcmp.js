// t152-stripcmp：诊断滤码逐字比对（wstrip=滤五新 W 码;strippos=剥 pos+message;wstrippos=两者;plain=原始——保序最弱归一化）。
// 用法：node t152-stripcmp.js <a.json> <b.json> <plain|wstrip|strippos|wstrippos>
// 输出：SAME | DIFF
const fs = require('fs');
const NEWW = ["W-REF-ESCAPE","W-DEEP-COPY-LOOP","W-RESULT-IGNORED","W-REC-NON-TAIL","W-BRANCH-UNREACHABLE"];
const a = fs.readFileSync(process.argv[2], "utf8");
const b = fs.readFileSync(process.argv[3], "utf8");
const mode = process.argv[4] || "plain";
const deep = (n) => {
  if (Array.isArray(n)) return n.map(deep).filter((x) => x !== null);
  if (n && typeof n === "object") {
    if (mode === "wstrip" || mode === "wstrippos") { if (NEWW.indexOf(n.code) >= 0) return null; }
    const h = {};
    Object.keys(n).forEach((k) => {
      if ((mode === "strippos" || mode === "wstrippos") && (k === "pos" || k === "message")) return;
      h[k] = deep(n[k]);
    });
    return h;
  }
  return n;
};
process.stdout.write(JSON.stringify(deep(JSON.parse(a))) === JSON.stringify(deep(JSON.parse(b))) ? "SAME" : "DIFF");