# ADR-52：@ref 语法层（词法/语法/AST/HIR/JSON）——零语义中间态

**状态**：已落地（t95）
**日期**：2026-11-10
**前提裁决**：用户裁定 N2 = 要做 @ref（推翻「倾向不做」的建议）
**上层计划**：`documents/R2落地计划-纯度闸与@ref.md` §二（分层 1-3）；语义四规则由 t97 落地

## 一、背景

`@ref` 是 N2 形态矩阵（根文档 :89-163）的不可变视图引用类型注解：
```subl
var a : @mut T = T(0)     // 可变视图
var b : @ref T = a        // 不可变视图的引用（不拷贝）
var b : @ref @mut T = a   // 可变引用（写 a 可见）
```
t95 只做**分层 1-3**（词法/语法/AST/HIR/JSON），语义（可写性/子类型/拷贝分派/命题待遇）是 t97 的域。

## 二、决策

1. **词法**：`@ref` 走既有注解通道（AT + IDENT("ref")，"ref" 非关键字）——近零成本，不新增 Kind。
2. **语法**（`Parser.parseType`，同一通道扩展，不另开）：
   - 类型位注解表加 `"ref" -> RefType(parseType(), t.range())`；
   - **叠加序 = 书写序，不规范化**：`@ref @mut T` → `RefType(MutType(T))`（与 `@mut` 既有通道同构；反序 `@mut @ref T` → `MutType(RefType(T))`，两者不同构）；
   - 值注解位（形参名前，`parseParams`）**拒绝** `@ref`：`@ref` 是类型位注解，混用落语法诊断（"@ref 是类型位注解：写在类型上（a: @ref T）"）；
   - 函数体语句位 `@ref x`（非 var）沿用既有 `errStmt("期望 var 变量声明")`——本任务不引入新码，只确认既有通道落诊断。
3. **AST**：`RefType(inner, pos)`（`ast/Type.kt`，紧邻 `MutType`/`AtomicType` 的 S-15 类型注解族）。
4. **HIR**：`HirRefType(inner, nid, origin, notes)`（`hir/Type.kt`）。sealed 穷尽 when 的机械分支同步补齐（Elaborator type()/subTpNames/specTypeQuiet/substSelf/containsSelf、TypeInfer render/renameTps/substTps/cleanTpVars、ExtFunHeads ofAst/ofHir、Collector sigSkeleton、AstPrinter、VarianceInfer walk/substitute）。
5. **JSON**：新增头 `"HirRefType"`（`fieldNode("inner")`，与 HirMutType 同形）。**schema 保持 v3**——`sublimation/hir@v3` 的版本纪律是「breaking 变更追加新 ADR」，新增节点为**附加项**（既有节点形态不动，旧消费者未见过的头按未知节点处理），不破坏既有 schema。
6. **零语义降级口径（中间态）**：`TypeInfer.demoteView` 把 `HirRefType` 与 `HirMutType` 同作可拆卸的视图包装（声明位 `@ref T` vs 初值 `T` 不误报类型不一致）。**这是有意的「可解析、无效果」中间态**——t97 以真正的视图子类型规则（`Box <: @ref Box` 等）替换本透明口径。

## 三、不做（t97 的域）

- 可写性规则（`@ref T` 只读、`@ref @mut T` 可写透）、子类型分派、拷贝/共享判定、命题待遇（不进上下文、不承认相等命题）；
- `@ref var`（语句位值注解通道，样本已有用例）的语义——t95 保持既有「注解收集、无效果」状态，不新增诊断（否则 68 样本将出现未归因 diff）。

## 四、验收要点（t95）

- 正例 `var b: @ref T = a` / `@ref @mut T` 解析通过、HIR 产出 `HirRefType`；
- JSON 往返（发射一致性 + schema v3 不变）；非法位置落语法诊断（体语句位 / 形参注解位）；
- 全量只增不减 0 失败；e2e 65/68；68 样本**零 diff**（样本仅在值注解位用 `@ref var …`，不触类型位 `@ref T`）。

## 五、风险

- `@ref var view : Box = o`（样本形态）在 t95 仍无语义——若用户期望其行为，属 t77/t97 的 `@ref` 语义层范围；
- demoteView 透明口径若被 t97 误留，会把视图子类型问题静默吞掉——t97 须显式替换该分支。


## 六、过程记录：导出缺陷复现（台账 D-2）

**事实**：t95 验证期的第一次 68 样本导出落在 `jsoncmp/out/t95/`——**55 份 + 非规范名**（如 `logic.subl.json`，正规名应为 `core__logic__logic.subl.json`），且未逐个校验产物存在；当时消息里报了「emit 缺 13」却**没在当批作废重跑**，而是另建 `out/t95n/`（68 份、名对齐）重新导出并据此得出「vs 旧基线 13 处 diff」的结论。旧目录残留在盘上，成为口径混乱源。

**为什么错**：
1. 批量 spawn 不限流（68 个 java 进程并行），一部分落盘竞态/失败未被察觉——违反台账 D-2「≤20 个/次 或 顺序跑+逐个校验存在」；
2. 导出命名未与 `base-t74/` 的名集合对齐（path→`__` 扁平化），diff 无从直接对齐；
3. 发现缺件后**应整批作废重跑**，而非另开目录、留残缺导出在原处。

**结论是否成立**：队长在稳定树上以规范导出 `jsoncmp/out/t95cap/`（68 份、顺序跑、逐个校验）复核——SAME=68/68（相对其基线零差异），「13 处 diff 全归因早前任务」的结论本身正确，但**推导过程不成立**：本次是结论对、过程错，属运气，不可复用。

**处置**：
- `out/t95/`（55 份残缺）**保留作缺陷证据**，不作任何引用；
- 规范导出以队长重导的 `out/t95cap/` 为准；
- 本 ADR 留此节作为过程记录。

**下次规范（自我约束）**：批量导出先按 D-2 分批（≤20）或顺序跑 + 逐个 `Test-Path` 校验；缺任何一个 ⇒ 当批作废重跑，不允许「另开干净目录、留旧残件」；导出名先与基线名集合比对再开跑。