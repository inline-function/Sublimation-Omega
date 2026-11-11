# ADR-3 AST 节点体系：零语义、位置模型与容错节点

> 状态：🟢 已决策（2026-11-04，t3 AST 节点体系实现）
> 相关任务：t3；相关规范：S-1 §2、S-2 §2、D-9、D-10、ADR-1-HIR设计
> 编号承接：ADR-2（syntax，词法错误恢复模型）；本文自 ADR-3 追加（documents/README 写作规范）

## 背景

t3 按《形式化文法》§2 重建 sublimation.frontend.ast（旧实现 Ast.kt 仅作字典点查）。
旧实现与本期契约束存在多处张力，需逐项裁决：

- 旧 Node 以 `pos: String`（"line:col"）携带位置；D-10（P-4 决议）已将诊断位置定为
  `SourceRange(start, end?)` 结构（ADR-1-HIR设计 §3.6）；D-9 要求源位置一律保留。
- 旧 AST 内嵌语义设施：Type 层的 render()/substT() 类型代数、InferMarker 推导占位、
  exprHasStmtReturn/diverges 控制流判据——与 S-1 §2「AST 零语义：节点不认识任何名字、
  类型与证明」直接冲突；HIR 层以结论字段承载这些判据（ADR-1 §3.3）。
- 旧 WhenArm.pattern 为 `var`（checker 原地改写归一化）；HIR 设计已将原地突变列为病根，
  要求复制构造（ADR-1 §3.1）。
- 旧实现无任何容错节点——解析失败即抛异常；S-1 §2 要求容错解析（语法错误处产出局部子树）。
- 旧 ReturnSym/TopExpr/BotExpr/PatCond 为 data object，无逐用点位置。

## 候选方案

- **A 同形零语义重建**：保留旧节点名与字段骨架，剔除全部语义设施；位置改 SourceRange；
  位置盲的单例节点改为携带位置的 data class；新增容错节点；全 val 不可变。
- **B 保留旧 API 直接移植**：pos:String、render/substT、InferMarker 等原样搬移。
  违反 S-1 §2 与 D-10/D-9，且把语义逻辑提前到 AST 层（S-2 §2 明令禁止）。
- **C 重量级 AST**：每节点附带 nid 与 notes 通道。nid/notes 属 HIR 五件套（ADR-1 §3.2），
  AST 层引入即重复 D-9 的留痕宿主，且 parser 的 notes 旁挂通道已有定案（ADR-1 §3.5）。

## 决策

采用 **A**：

1. **位置模型**：`SourcePos(line, col)` + `SourceRange(start, end?)`，与 D-10 结构同构；
   宿于 ast 包（diag 横切包 t6 尚未建立，AST 本期即需位置）。t6 落地后由队长裁定：
   diag 复用本定义，或反向依赖（S-2 §3.2 横切规则的例外裁定）——两侧结构相同，迁移机械。
2. **Type 节点从无位置 → 携带源位置**（D-9：类型级诊断/LSP 定位需要）。
3. **层级结构**：sealed interface AstNode（pos 必填、无默认——位置是契约非装饰），
   五个 sealed 子层级 Decl / Stmt / Expr / Pattern / Type；Entry(FileAst 层级容器)与
   Param / Ctor / TheoryParam / WhenArm / WhenPropArm 等辅助记录同样自携 pos。
4. **零语义剔除**：render()/substT()/InferMarker/namedT/isInferMarker 与
   exprHasStmtReturn/diverges 系列一律移除——类型代数与控制流判据归属 semantic 层，
   结论入 HIR 字段（ADR-1 §3.3）。BlockExpr.tailExpr 保留（纯结构性投影，无解释）。
5. **单例节点 data object → data class(pos)**（ReturnSym / TopExpr / BotExpr / PatCond）：
   每个使用点都有独立源位置，S-1 §2 方落实（旧 object 恒为同一无位置实例）。
6. **全 val 不可变**：WhenArm.pattern 改 val；模式归一化由 HIR 构造期拷贝完成（ADR-1 §3.1）。
7. **容错节点**（S-1 §2，旧实现无）：ErrorDecl / ErrorStmt / ErrorExpr / ErrorPattern /
   ErrorType（message + pos）。恢复契约：错误点占位记因，可挽救子树保留在相邻节点字段。
8. **范围**：声明 S-5~S-14 + 理论参数/形参/构造子（S-8/S-11）、类型 S-15（声明/表达式/模式
   的类型位必需）、语句 S-16~S-30、表达式 S-31~S-37 + S-40 量词表达式、模式 S-42。
   旧 StructDecl 的 @compose 块体（body）剔除——非 §2 产生式且属 SfA 非本期范围（D-6/D-7）。
9. **齐备性双重判据**（测试落实）：各层级穷尽 when（无 else，sealed 穷尽性=编译期证明）
   + 样本名集逐层级对账（运行期）。

## 后果

- 节点计数：Decl 8 / Stmt 15 / Expr 30 / Pattern 8 / Type 8 = 69 个 AstNode；
  与旧 Ast.kt 节点名一一对应（HIR 映射表仍然成立，ADR-1 §3.2），新增 5 个容错节点。
- t4（parser）构造节点时必须填写 pos；容错节点是其错误恢复的落点。
- t6（diag）需与本文第 1 条的位置宿主裁定对接；t7（semantic）不得在 AST 层做类型代数。
- 旧 pos:String 渲染兼容由 D-10 的渲染层承担（「[错误][1:2] CODE: msg」格式），AST 不参与。
