# ADR-35 `class` 关键字更名为 `trait`（代码层，schema v3）

> **编号更正（2026-11-07 17:10，队长）**：本文件原编号 `ADR-33` 与 t51 的 `ADR-33-双向类型检查燃料与不可停机拒绝.md` **撞号**。燃料 ADR 先落盘，故本文件改编号为 **ADR-35**，内容不变。
> 教训：ADR 是**全局唯一编号的档案**，落盘前必须 `ls` 查重；两名作者并行写 ADR 时尤其容易撞。

**日期**：2026-11-07（P-10 步骤 0.2-A，t56）
**状态**：代码层已落（关键字/AST/HIR/SymbolKind/JSON)；文案/样例/文档归 t57

## 一、决策

1. **关键字更名**：语言关键字 `class` → `trait`（ST-v1 §2 裁决，语义不变）。词法层 `Lexer` 关键字表与
   `Kind` 枚举同步（`Kind.CLASS` → `Kind.TRAIT`)。
2. **节点更名（AST/HIR）**：`ClassDecl` → `TraitDecl`(21 处引用）、`HirClassDecl` → `HirTraitDecl`(12 处）、
   `HirClassConst` → `HirTraitConst`;`SymbolKind.CLASS`/`CLASS_CONST` → `TRAIT`/`TRAIT_CONST`;
   `parseClass` → `parseTrait` 及同类标识符（`TRAIT_BODY_SYNC`、`traitEntry`、`traits` 表、`traitCycleCheck`、
   `inTraitBody`、`traitDecls`、`traitName`)。
3. **schema v3**：`HirJson.SCHEMA` 由 `sublimation/hir@v2` 升为 `sublimation/hir@v3`。v3 与 v2 的差异：
   ①声明层节点类型名 `HirClassDecl` → `HirTraitDecl`、`HirClassConst` → `HirTraitConst`（同义更名，字段结构不变）。
   **不做旧名别名**——判据：D-6/D-7「后端与解释器直接消费 HIR；解释器、LSP、注解处理器**暂缓**」 => V1 无外部
   JSON 消费者；别名是对零买家买的保险，并使发射器永久双名。若日后出现外部消费者，以其版本为冻结点，
   由其时的新 ADR 定义别名。
4. **零残留清扫**：全仓 `ClassDecl`/`HirClass*`/`Kind.CLASS`/`parseClass` 等旧名 **0 残留**（grep 佐证，t56 验证）。
   `E-SELF-OUTSIDE-CLASS` 等诊断**码名**按 `码名迁移台账.md` 缓慢改纪律**保留**（已补登记），仅文案术语归 t57。
5. **旧 ADR 与日志保留原文**：历史决策记录是档案，改了即篡改（与 ADR-24 §三 同纪律）；本 ADR 为改名之
   兑现与读法声明——旧 ADR/log 中的 `class` 应读作 `trait`。

## 二、旧路径已死（验收一节）

更名后 **新路径完全接管**，双重证明：

1. **`class` 在语言层不可识别**：`Lexer` 关键字表不再含 `class`——它落为 `IDENT` 文本。工程模式顶层探针
   `class Functor[F[_]] { … }` 落 `E-SYNTAX: 顶层只能写声明，执行代码请放进 \`fun main()\``（typeclass.subl
   实测，t56:16:51 采样），**而非被静默默认为 trait**。ParserTest 新测 `t56 class 关键字已废除——class Foo 不被
   静默当 trait 解析` 断言「AST 中不存在名为 Foo 的 TraitDecl」从测试层钉死该不变量。
2. **旧节点名全仓 0 残留**：`grep '\bClassDecl\b|HirClass|Kind\.CLASS|SymbolKind\.CLASS\b|parseClass|
   'CLASS_BODY_SYNC|hir@v2'` on `frontend/src` → **0 命中**；编译期 Kotlin 类型系统保证所有 AST/HIR 引用
   已切到新名（未切即编译失败）。

## 三、后果

- 用户面：写 `class` 的旧源不再编译（落 E-SYNTAX），须改为 `trait`。**无兼容期**（用户方向：一次改干净）。
- e2e：基线 65/68 → **59/68**——6 个样本文件因自身仍写 `class` 转红（9 处声明，与 t54 盘点表 §1.9 逐条吻合），
  预期行为，t57 改样例后回升。3 个**既有**红（list.subl/手动证明/模块系统-主）诊断码集与基线完全相同，
  无新因混入。
- 测试：529 → **531/0 失败**（+2 个 t56 新测，见 log）。
- 诊断**文案**（含「类型类」的 39 行）与 `E-*` 码名**未动**：归 t57 与台账缓慢改。

## 四、对账

- t54 盘点表 `documents/class-改名盘点.md` §1.1-1.9 的代码层点位与本 ADR 一一对应。
- 验证记录见 `documents/log/2026-11-07.md` t56 段（双快照零警告 / 531-0 / e2e 59-68 归因）。

## 五、t57 续接（文案 / 样例 / 文档层，2026-11-07）

代码层（§一-三）落盘后，文案/样例/文档层以独立任务执行：

1. **诊断文案**:main 源所有字符串字面量与注释中的「类型类」→「特质」、语言层 class → trait。
   码名 E-SELF-OUTSIDE-CLASS、E-TC-* **一字未动**（台账缓慢改纪律）；core.classic / E-NEED-CLASSIC
   中的 classic 保留。机械证据：全扫三引号原始串与单行串字面量，类型类/语言层 class 命中 **0**。
2. **e2e 样例**:9 处 class 声明 → trait(tuple/traits×3/num/特性巡礼/特质×2/高阶类型）；注释术语同步；
   **例外保留** async 4 处「运行时 class 注入」（OOP 类概念非本语言类型类）；**文件更名 2 个**（分母不变
   仍 68):examples/类型类.subl → examples/特质.subl、core/collection/typeclass.subl → core/collection/traits.subl
   (e2e 驱动为目录遍历，无硬编码路径）。
3. **文档**:文法卷正文（L-9/S-9/A.2/D-23/入口分派 + 5 处正文术语）改新形态；**附录 B(P-9 修订记录）保留
   原文 + 段首追加历史口径注记**；TS 卷 29 处与 ST 卷 5 处残留同步；specification/README.md:67 的「逐字替换
   尚未执行」更新为「已执行」（读法声明 65 行本已存在，核实无需新增）。
4. **验收**:e2e 由 t56 的 59/68 **回到 65/68**（6 个新红全部转绿，分母 68 不变；3 个既有红码集逐字不变）;
   :frontend:test **531/0**(t56 基线持平，无新增测试——纯文本替换轮）；双快照零警告。
5. **测试同步**:ParserTest 两处 class-不得携带状态 断言串 → trait-不得携带状态、ExtensionSemanticsTest 3
   处说明串术语；测试类名 ClassInheritanceAndRecursionTest → TraitInheritanceAndRecursionTest（含文件名）。

> 历史保留清单（不改）：全部历史 ADR(19 篇命中）、documents/log/*、P-9-收口报告.md、P-10-执行计划.md /
> P-10-验收方案.md（计划成文于改名前，作决策档案）、t54 盘点表 class-改名盘点.md（历史快照）。
