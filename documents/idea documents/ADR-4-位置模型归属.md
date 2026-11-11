# ADR-4 位置模型归属：SourcePos/SourceRange 下沉 diag 横切包

> 状态：🟢 已决策（2026-11-04，队长裁定；取代 ADR-3 决策 1 的位置宿主条款）
> 相关任务：t3/t4/t6；相关规范：S-2 §3.2、D-10、S-1 层间总契约 2

## 背景

ADR-3 决策 1 将 SourcePos/SourceRange 暂宿 ast 包，把最终归属裁定留给队长（diag 横切包 t6 未建）。
现 t4（parser，syntax）与 t6（HIR+diag，semantics）已并行启动，归属必须即刻裁清，否则两侧
import 会随后续迁移反复返工。

## 候选方案

- **A 下沉 diag**：SourcePos/SourceRange 与 NodeId 一并宿于 sublimation.frontend.diag；diag 零依赖，
  lexer/ast/parser/hir/semantic/json 任层单向依赖 diag。
- **B 留 ast**：diag 依赖 ast 取用位置类型。
- **C 独立 source 包**：位置模型单立 sublimation.frontend.source。

## 决策

采用 **A**：

1. sublimation.frontend.diag 宿：Severity / SourcePos / SourceRange / NodeId / Diag / DiagBag，
   与 D-10 及 ADR-1 §3.6 一致；diag 只依赖 Kotlin 标准库（S-2 §3.3 零第三方依赖）。
2. ** diag 落地动作（t6 首步，机械迁移）**：把 ast/SourceRange.kt 的 SourcePos/SourceRange 迁入
   diag 包，同步改 ast 各节点 import；迁移前后构建+测试须绿。完成后 t4/t6/t7 均按
   `sublimation.frontend.diag.SourceRange` 引用。
3. 为什么不留 ast：词法错误的诊断（lexer → diag）在 ast 之前，diag 依赖 ast 会把第 2 层的类型
   拖进第 1 层的依赖图（S-1 层间总契约 1 的「禁止跨层引用」精神）；NodeId 同理（Diag.nid 反引
   HIR 节点，若 NodeId 宿 hir 则 diag→hir 与 hir→diag 成环）。
4. 为什么不单立 source 包：新增包即修订 S-2 §4 包结构并惊动全部下游 import；diag 已是横切基座
   （S-2 §3.2「diag 可被任层依赖」），位置模型本就是诊断的最小载量，不需第三种基座。

## 后果

- t4（parser）：FileAst/节点已建，位置类型 import 指向 ast 包 —— t6 迁移完成后须机械改指 diag
  （sed 级改动，t6 一并处理后 t4 落地代码需复审 import 行）。
- t6（diag）：首步即迁移，随后按 ADR-1 §3.6 落地 Diag/DiagBag（四族 E/W/D/H、Severity 判阻断、
  report 渲染兼容旧格式）。
- ADR-3 决策 1 的「宿 ast 包」表述自本文生效起作废，ADR-3 原文保留不改动（documents/README
   「决策被推翻时保留原文，标注取代」）。
