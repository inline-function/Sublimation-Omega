# ADR-40：数字减法的类型规则（ST-10.1）与 Nat 任意精度化（ST-10 修正）

> 状态：已落地（队长裁决 A 批准 / A′ 否决，2026-11-08） · 日期：2026-11-08 · 前置：ADR-39（t62 子类型偏序，数值塔单条件句）
> 来源：队长实测 `fun give(a: Nat, b: Nat): Nat = wantNat(a - b)` 通过——`Nat - Nat` 判为 `Nat`。
> 范围：TypeInfer（builtinOp 减法规则 / uniExpr 一元取负规则）、测试（MinusTypeRuleTest 4 组 22 例 + 受影响旧测试 5 例翻新）。判官主体/偏序/燃料/深度哨/轮次一律未动。

## 一、缺陷与根因

`Nat` 承载「非负」命题（从 `x : Nat` 免费得到 `x >= 0`）。若 `Nat - Nat : Nat`，则 `3 - 5` 被判为非负类型，而其值可为负——
类型声明与真实值脱节，证明器据类型的线性推理**可为假**。同族缺口：一元取负 `-a : Nat`。

根因：`TypeInfer.kt:572`（builtinOp）`"+", "-", "*", "%"` **共用**一条上确界规则 `ts.reduce { x, y -> unify(x, y) ?: x }`（取两操作数塔上确界）。
`+`/`*`/`%` 对 Nat 封包正确；`-` 天然破坏非负闭包。`uniExpr` T769（`"-" -> operandType`）为同族第二处。

## 二、规则（ST-10.1 + ST-10 修正）

- **二元减法**：`rank(结果) = max(rank(l), rank(r), 1(=Int))`。即 `Nat-Nat / Nat-Int / Int-Int → Int`，`Rat-Rat / Rat-Nat → Rat`（Rat rank=2 ≥ 1 不受影响）；
  **`+`/`*`/`%` 保持上确界**（Nat 闭包，未动）。
- **一元取负**：`-/¬` 数值操作数 rank<1 提为 Int（`-a : Int`）；非数值保持原样（bool 联结词分支不变）。
- **ST-10 修正（同批生效）**：`Nat` 为任意精度（无界）。原「具界无符号」措辞作废——证明器已对 Nat/Int 做线性算术推理，
  若 Nat 有界则 `a + b ≥ a` 这类已证命题运行时可假（溢出），与「证过的命题运行时不得为假」冲突。**本文未涉及任何界相关代码**（V1 无界语义本来如此，仅文档/语义口径对齐）。

## 三、改动（2 处，判定同池）

1. `builtinOp`（T571~）：`-` 拆出单独子句，结果经新私有件 `minusSup(ts)`——先取旧上确界 sup，
   若 `(sup as? HirNamedType).let { rank(it.name) } < 1` 则提为 `namedT("Int")`；`+ * %` 原样。
2. `uniExpr`（T769）：单行改三行——`op == "-"` 且数值 rank<1 → `namedT("Int")`，否则原样；其余算符 → Bool 不变。

机械要求 ② 的 grep 证据（全仓 `-` 结果产出点）:

| 位置 | 性质 | 处置 |
|---|---|---|
| TypeInfer.builtinOp `"-", ... -> ts.reduce` | 唯一算术结果表（二元） | **改**为 minusSup |
| TypeInfer.uniExpr `op == "-" -> operandType` | 一元结果 | **改**为 rank≥Int |
| Elaborator:830 binExpr / :898 算符前缀调用 | **调用方**（都经 infer.builtinOp） | 不动（无第二处规则） |
| PropChecker 命题求值线（linOf/evalConst 的 `-`） | 命题常量求值，不产类型 | 不动 |
| Parser:1290 `UniExpr("-", ...)` | 语法造点 | 不动 |

即：**算术结果唯一在 builtinOp**；`-` 的旧（上确界/不变）规则已无第二处。

## 四、e2e 影响（先报告再决定）

ST-10.1 是收紧（Int→Nat 降级位须拒绝）。实测 e2e：65 → **63/68**，受损 2 样本（3 既有红码集未变：list.subl 15项/同 5 码 = 基线）：

| 样本 | 位 | BEFORE | AFTER（规则） |
|---|---|---|---|
| core/time/time.subl:10 | `fun diffMs(a: Nat, b: Nat): Nat = b - a` | 通过 | E-TYPE-MISMATCH（b - a : Int → Nat 降级） |
| examples/日常-数列与区间.subl:42-46 | `fun fact(n: Nat): Nat { ... fact(n - 1); return n * r }` | 通过 | 4项（n - 1 : Int → Nat 形参位 + 其余既有 D-TYPE-INFERRED） |
| core/collection/list.subl | take/drop/slice 的 `n - 1` : Int 传入 Nat 形参 | 15项（既有红之一） | 15项同 5 码集（count 亦不变；List[T] 模式处的既有失败先于实参位） |

**未受损的含 `-` 样本（实测）**：特性巡礼 `abs`（其注释「返回 Int 而非 Nat：0 - n 与 n 在此处都是 Int……类型层不表达非负，故诚实声明 Int」
**恰为 ST-10.1 的既有先例，反而变正确**）；series.subl 命题（3项 H-ARITH-PREC 与基线同）；高阶函数与lambda `{ _ - 1 }`（泛型 U 解为 Int）；值与字面量 `var i: Int = -7`（一元取负提为 Int 后仍适配）。

### 裁决选项（队长示下）

- **A（队长批准，已落盘）**：2 处样本诚实化——time.subl `diffMs(a: Nat, b: Nat): Int = b - a`
  **+ 注释改陈述事实**（差值为正表示 b 在 a 之后——原注释是无前件支撑的前提断言，队长批注「留着自相矛盾的注释比类型错更坏」）；
  日常-数列与区间 `fun fact(n: Int): Int`。实测：e2e **65/68**（3 既有红码集与项数逐字不变）、单测 **601/0**、结构违规 0。
  已登记 documents/样本语义修订台账.md（**新档，与码名迁移台账分立**——语义修订非码名生死）。
  裁定判据（队长）：**改动必须由「规格变更」直接强制，不得由「顺手整齐」驱动**。
- A′（**队长否决，已驳回**）：list.subl take/drop/slice 的 `n/from/to: Nat → Int`——该红（模块系统边界）的**项数与码集是冻结证据**，ST-10.1 下收 Nat 仍合法、无规格强制。该观察已由队长登记为开放问题。
- B：不动样本 ⇒ e2e 停在 63/68（本任务验收不可过）。
- C：撤销 ST-10.1 ⇒ 任务失败（规格已写入 ST 卷）。

**不涉及**：别处放宽（Int→Nat 仍判非法，判据在 ADR-39 偏序子句 8，未动）；`/` 仍为 Rat；比较族不变。

## 五、负向对照（硬条件：规则仍然咬人，且不依赖样本）

MinusTypeRuleTest.minusBinaryRule 的 `wantNat-arg`（`fun give(a: Nat, b: Nat): Nat = wantNat(a - b)` → **E-TYPE-MISMATCH**）
即「Nat - Nat : Int、Int → Nat 降级位被拒」的仓内负向对照——独立于任何 e2e 样本（同一组另含 var 声明位/返回位/一元取负等 6 个负例）。

**规格收紧的正面证据（队长批注）**：旧样本里「早就诚实写了 Int」的处所成为新规格的正面证据——
examples/特性巡礼.subl 的 `fun abs(n: Int): Int { if (n < 0) { return 0 - n } … }` 及其注释「`0 - n` 与 n 在此处都是 Int，而 Int → Nat 属向下、须显式收窄（数值塔只做小到大提升）。
非负性由两个分支保证，但类型层不表达它，故诚实声明 Int」——**正是 ST-10.1 的先例口径**，收紧后由「恰巧写对」升格为「规格正确」。

## 六、受影响单测翻新（规则收紧的必然，非放宽）

- ViewDemotionTest.v10：`fun f(a: @mut Nat): Nat = -a` → `: Int`（测的是 @mut 操作数的视图降级分类，结果类型随规则翻为 Int）。
- TraitInheritanceAndRecursionTest TS-16 三例：扩展接收者 `Nat → Int`（`self - 1 : Int`），调用点加 `var n: Int = 9` 中介（
  扩展函数方法糖/直接调用的接收者按**名义名精确匹配**——Nat 字面量 9 不落到 Int 接收者；此为既有解析行为，非本任务引入）。
- MinusTypeRuleTest（新，4 组 22 例）：二元规则 10 例（负 3 正 7）、+ * % 闭包 4 正、一元 5 例（负 2 正 3）、floor 既有行为 2 例（负 1 正 1）。

## 六、验证（2026-11-08 实测，样本未动状态）

- BEFORE（先红后绿，留证）：`wantNat(a - b)` PASS（缺陷）；`wantInt/wantRat` PASS；`wantNat(a+b/*/%b)` PASS；floor(r)→Nat REJECT（既有）。
  AFTER：前 4 行 + 一元全部落位；`+ * %` 4 例零误伤；floor 行为不回退。
- **:frontend:test --rerun-tasks = 601/0**（597 基线 + MinusTypeRuleTest 4 方法 22 例；另 5 处旧测试翻新）。
- **e2e：65/68**（3 既有红——list.subl 15项/同 5 码集、手动证明 2项 D-BTS,E-CANNOT-PROVE、模块系统主 4项 E-UNBOUND-NAME，逐字不变）；结构违规 0；HirJson 快照 23/0 零警告。
- 判官主体/偏序/燃料/深度哨/轮次上限一律未动（TypeInfer 仅 2 处规则 + 1 新私有件）；grep 证据见 §三表。