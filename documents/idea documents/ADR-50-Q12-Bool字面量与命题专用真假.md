# ADR-50 Q12/Q20：Bool 字面量 True/False 与 ⊤/⊥ 命题专用

**日期**：2026-11-09（t79）
**状态**：已落（**并存期**——True/False 与 true/false 同时合法；小写拒绝与样本迁移由 t80 承接）
**性质**：用户裁决 Q12 + Q20 的语义层落地；值位新增拒绝（E-PROP-POS 复用），无新增诊断码

## 一、裁决口径（用户原文 2026-11-09）

> 「⊤ ⊥ 是两个关键词，表示恒真命题和恒假命题…… Bool 使用 `True` 和 `False`，类型则使用 `Any` 和 `Nothing`，不与命题层共用。」

四条落地约束：

| # | 约束 | 本批次 |
|---|---|---|
| 1 | Bool 字面量写 `True` / `False` | ✅ 与 true/false **并存**（先支持后收紧） |
| 2 | ⊤ / ⊥ 是**关键词**，只表恒真/恒假**命题** | ✅ 值位拒绝 |
| 3 | 类型顶/底 = `Any` / `Nothing` | 本批次无改动（Any 已在、Nothing 已定义；零样本使用） |
| 4 | 不与命题层共用 | ✅ 值位 E-PROP-POS |

## 二、实现（三处落点，全部复用既有机制）

| 位置 | 改动 |
|---|---|
| `SymbolTable.kt` `BUILTIN_VALUES` | 加 `True` / `False`——与 true/false 同走内建值解析（值位 `HirNameRef` + 内建符号 + Bool 类型，**唯名差异**，节点形态与旧小写字面量同构） |
| `Elaborator.expr()` TopExpr/BotExpr 分支 | 值位 `⊤` → `propOnlyExpr(pos, "⊤", "值位布尔真请写 True")`；⊥ 同理提示 False。复用 E-PROP-POS（「命题出现在值位」家族），**无新增诊断码**；半成品回落 `HirErrorExpr` |
| `Elaborator` 模式/穷尽性 | 臂顶层与槽位模式识别 True/False 为 Bool 字面量模式（D-PAT-BOOL 口径）；`boolLitName` 归一大小写计 when 穷尽覆盖 |

提示文本遵守 ADR-48 §八（推荐写法本身可编译）：`True` / `False` 均为本批次登记的内建值。

## 三、为什么值位拒绝码复用 E-PROP-POS

任务书允许新码但要求先报队长。E-PROP-POS 既有语义即「命题专属语法出现在程序面」（`$`、量词同族），⊤/⊥ 作为命题关键词落在值位**语义完全吻合**：
- 复用 ⇒ 诊断码面零增长（S-3 §诊断码登记纪律）；
- 无需新增测试夹具；既有 E-PROP-POS 断言自动覆盖族内新形态。
若后续文案需分化（例如区分关键词与衍生形态），再单议分码。

## 四、补真内部路径（S-8）为何不受影响

`∴ q` / `∵ p` 裸形由 Parser 反糖为 `BTSStmt(TopExpr(∴), …)`（Parser:875/905）——**内部构造的 TopExpr**，BTS 语义处理（Elaborator:885-887）把 form/premises/conclusion 全部经 `PropNormalizer.prop()`，**不经 `expr()`**：命题层 → `HirPTop`，与值位守卫正交。测 `bts补真零变化` / `bts因为裸形补真不走值位` 锁定该不变量。

## 五、验收

| 项 | 结果 |
|---|---|
| True/False 通过 + HIR 同构 | ✅ `Q12BoolLiteralsTest`（16 例）|
| ⊤/⊥ 值位拒绝 + 提示 | ✅ 赋值/实参/返回/模式四守卫 |
| ⊤/⊥ 命题位零变化 | ✅ HirPTop/HirPBot 逐字（合取命题位同验）|
| ∵∴ 补真路径 | ✅ 零 E-PROP-POS、零 E-SYNTAX |
| 全量 | **775 / 0**（753 + Q12BoolLiteralsTest 16 例；两处旧 C1/S-34 双身份断言随裁决改向——SemanticTest::propOnlySyntaxInProgramPosition、StructCtorTest「C1 程序位」——BEFORE/AFTER 已记本 ADR §二，红→绿属规格强制，非放松）|
| e2e | ok 65→**64**：`值与字面量.subl` 变红（5项→7项，+E-PROP-POS×2，即 33/34 行 `var _top/_bot: Bool = ⊤/⊥`），三冻结红逐字不变（t79-e2e.txt vs t73-e2e.txt）|
| 68 样本 HIR JSON 逐字比对 | **SAME=66 / DIFF=2**：`值与字面量.subl` 仅 +E-PROP-POS（值位 HirTop×1 → HirErrorExpr×2 回落，ok true→false）；另一处 `core/collection/list.subl` 的 diff 是**并发 t78** 的 `W-OP-RETIRED×2`（节点数 2881 不变、零 E-PROP-POS），与 t79 无关，已定界上报 |

## 六、交付与未动

改：`semantic/SymbolTable.kt`（BUILTIN_VALUES）、`semantic/Elaborator.kt`（expr 守卫 + propOnlyExpr hint 形参 + 模式两处 + boolLitName 归一）、新增 `Q12BoolLiteralsTest.kt`、本 ADR、日志 `2026-11-09.md`。
未动：词法（⊤/⊥ 仍是 L-8 逻辑单例 token——它们本来就是固定 token，Q20 的"关键词"在词法层已成立）、Any/Nothing、样本（迁移归 t80）、小写 true/false 的合法地位（并存期）。

## 七、风险与后续

- **并存期纪律**：当前 true/false 仍合法；**收紧（拒绝小写 + 提示 True/False）必须等 t80 完成样本迁移后单独一批**，否则样本会大面积落 E-UNBOUND-NAME。
- 同批的裁决 #4（表达式位 && / || 退役、命题位 & / | 退役）与 #5（原生命题优先级表）**不在本批次**——分别由后续任务承接，与本 ADR 同批次落档但实现分离。

## 八、t84 收口：小写 true/false 拒绝（终态）

**日期**：2026-11-09（t84，t80 完成 45 处样本迁移后）——**Q12 裁决的终态落定**。

- `SymbolTable.BUILTIN_VALUES` 移除 `true`/`false`；未声明 `true`/`false` 走既有 `E-UNBOUND-NAME`，消息附加可编译提示「——Bool 字面量写作 `True` / `False`」（ADR-48 §八：推荐写法本身可编译）。
- **模式位一致性**：`when (b) { true -> … }` 的小写臂经 D-PAT-BOOL 分支调 `resolveValue` ⇒ 同码同提示；半成品 `HirPatLit` 保留计入覆盖（文件已因错失败，覆盖判定不影响）。
- **既有机制的内部规范名保留**（非源码语义用法）：`sealedCtorNames("Bool")`、`whenCovered` 的 `"true" in lits`、`boolLitName` 归一——它们消费的是 HIR 内部规范名（经 D-PAT-BOOL 归一），不依赖源码写法。
- **否定提示连带修正**：`¬true` 的值位提示原写 `!true`——t84 后该提示不可编译，`boolLitCap` 把字面量尾改大写（提示仍是「推荐写法本身可编译」的纪律）。

### 落点
| 文件 | 改动 |
|---|---|
| `semantic/SymbolTable.kt` | `BUILTIN_VALUES` 去小写 |
| `semantic/NameResolver.kt` | `boolHint`（大写写法提示）+ `boolLitCap`（否定提示的字面量尾）|
| `semantic/Elaborator.kt` | 三处注释口径更新（分支与归一逻辑零改动——小写臂改由 `resolveValue` 落诊）|
| 测试 | 10 个语义测试内嵌源码片段迁 `True/False`（队长 t80 只迁了样本，测试片段是漏网）；`ParserTest` 3 处、`R38` 2 处、`P5Batch1` 否定提示用例改 `!True`；Q12 两个并存期用例改为终态负例（断言 E-UNBOUND-NAME + 提示）|

### 验证
- CLI 探针（`frontend/probe-mut/cases-t84/`）：`True/False` 通过；`true` 值位/模式位均落 `E-UNBOUND-NAME` + 「Bool 字面量写作 `True`」；`when(b){True->1;False->0}` 通过且穷尽。
- 68 样本 JSON（`jsoncmp/out/t84` vs 权威基线 `jsoncmp/base-t74`）：SAME=55 / DIFF=13，13 处**逐处归因**——10 处纯 t80 字面量迁移（`name:"true"/"false"` 计数归零、节点数/码集/ok 不变）、`list` 另含 t80 `&&→&`、`logic` 为 t82 优先级（`&` 2→0）、`值与字面量` 为 t80 `⊤⊥→True/False`（`HirTop/HirBot` 各 1 → `HirNameRef`+内建符号）。**t84 自身零新增 diff**（样本已无小写）。
- e2e 等效快照：ok=true **65/68**（三冻结红不变；`值与字面量` 自 t79 的红恢复）。