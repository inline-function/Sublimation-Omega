// backend-js gate：envelope 守门（schema v3 契约 t158）——输入门只收 ok=true，负例先红
// 拒收即整体弃件（不降级、不静默），拒收理由单列。

export function gate(raw) {
  const reasons = [];
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    return { ok: false, reasons: ['envelope 非对象'] };
  }
  if (raw.schema !== 'sublimation/hir@v3') {
    reasons.push('schema 字段非 sublimation/hir@v3（得 ' + JSON.stringify(raw.schema) + '）');
  }
  if (typeof raw.compiler !== 'object' || raw.compiler === null) {
    reasons.push('compiler 字段缺失/非对象');
  }
  if (typeof raw.unit !== 'object' || raw.unit === null ||
      typeof raw.unit.path !== 'string' || typeof raw.unit.mode !== 'string') {
    reasons.push('unit 字段缺失/非 {path, mode} 形');
  }
  if (raw.ok !== true) {
    reasons.push('ok != true（诊断件：codegen 输入面只收 ok=true，错误恢复产物不进运行时）');
  }
  if (!Array.isArray(raw.diagnostics)) {
    reasons.push('diagnostics 字段非数组');
  }
  if (typeof raw.hir !== 'object' || raw.hir === null || raw.hir.kind !== 'HirFile') {
    reasons.push('hir 字段缺失/根节点非 HirFile');
  }
  if (!Array.isArray((raw.hir && raw.hir.entries) || [])) {
    reasons.push('hir.entries 非数组');
  }
  // 负例门 2：Error 族节点 = 错误恢复产物（提案 §2.8）
  const errorNodes = [];
  walkNodes(raw.hir, function (n) {
    if (n.kind && n.kind.indexOf('HirError') === 0) errorNodes.push(n.kind + '@' + (n.pos || '?'));
  });
  if (errorNodes.length) {
    reasons.push('含 Error 族节点（错误恢复产物）' + errorNodes.length + ' 处：' + errorNodes.slice(0, 5).join(' '));
  }
  if (reasons.length) return { ok: false, reasons: reasons };
  return { ok: true, unit: raw.unit, hir: raw.hir, diagnostics: raw.diagnostics };
}

function walkNodes(n, f) {
  if (Array.isArray(n)) { for (const e of n) walkNodes(e, f); return; }
  if (!n || typeof n !== 'object') return;
  f(n);
  for (const k in n) {
    if (k === 'kind' || k === 'symbol' || k === 'field' || k === 'ctor') continue; // 符号引用辅助记录非节点
    const v = n[k];
    if (v && typeof v === 'object') walkNodes(v, f);
  }
}
