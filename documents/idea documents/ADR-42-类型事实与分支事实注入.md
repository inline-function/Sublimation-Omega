# ADR-42：证明器事实注入（类型事实 + 分支事实，ST-13/P-12 使能前置）

> 状态：已落地 · 日期：2026-11-08 · 前置：ADR-13（命题 if[p] 的事实帧与分支注入）、ADR-6 §四 4.4（Γ 帧栈 factFrames/injectFact/allFacts）、t61（demoteView）
> 来源：**用户裁决（ST-10.2 前置）**——「Nat - Nat : Int；若 Γ ⊢ 前 >= 后，由证明器智能转换为 Nat」。队长 jar 探针（2026-11-08 09:4x）证实使能条件两个都缺：
> #1 \`n : Nat + axiom n != 0 ⇒ ∵ n != 0 ∴ n >= 1\` 落 \`E-CANNOT-PROVE\`（Γ：!=<n,0>，缺类型事实 \`n >= 0\`）；#2 \`if (n == 0) {} else { ∴ n >= 0 }\` 的 else 支 \`Γ：\`（空，缺分支事实）；#3 \`∵ a >= b ∴ a - b >= 0\` 通过——算术通道（ArithChannel：±1 差分图 Bellman-Ford + consumeNe 不等式通道）能力在线，缺口纯粹在**事实未入帧**。
> 范围：Elaborator（事实注入 + 块级/臂级事实帧 + IfExpr 分支注入）；测试 ProveFactInjectionTest 15 例（任务书验收表 8 例 + 字段/@mut/上界/条件不可命题化/信任审计 7 补充）。**本任务不做类型收窄本身（t66）；判官主体/偏序/减法/燃料/深度哨/轮次上限一律未动**。

## 一、设计

### 1.1 事实来源分类与信任审计

| 来源 | 通道 | 信任 | 审计 |
|---|---|---|---|
| \`user-axiom\` | \`axiom P\` 语句（R-AXIOM 免证直接点亮 Γ）| 免证信任 | \`D-AXIOM-POST\`（@axiom 函数签名即公理，ProofChecker 唯一信任审计码）|
| \`branch\` | \`if[p]\`（ADR-13 既有）+ **if (cmp) 表达式**（本次复用同机制）| 假设驱动（支内有效，出支即弃）| 不进信任审计（由 allFacts 的 occ 参与证明）|
| \`type\` | **本次新增**：声明位按数值类型注入（Nat ⇒ v >= 0）；provenance 留痕为 HirNote(kind=SUPPLEMENT, code="provenance=type") | 类型论断（与 ST-10.1 的 Nat 承载 v>=0 同源，t63 ADR-40）| **不进用户公理信任审计**（与 R-AXIOM/D-AXIOM-POST 通道完全隔离——a14 测试断言无 D-AXIOM-POST 副产）|
| \`because\` | ∵∴ 结论证出后注入（既有，未动）| 已证 | — |

类型事实**不是**公理（不免证）：它只是把「Nat ⇒ v >= 0」这个类型承载命题（ADR-40 §二，Nat 承载非负命题）写进 Γ，仍由证明器逐次核对，不点亮任何免证结论。

### 1.2 类型事实注入点（生命周期 = 作用域）

- \`param()\`：形参（SymbolKind.PARAM；字段 FIELD 意图跳过——结构体声明位无事实帧）；
- \`funDecl\`：扩展函数接收者 self（TS-10 的 self 绑定位同帧注入）；
- \`VarStmt\`/\`VarExpr\`：局部 var 声明位（declared 声明类型或推导 finalT；@mut Nat 经 demoteView 按值注入）；
- 模式槽位（when 臂 \`is Cons(x, t)\` 等）：typed slot 两位点。
- **帧机制升级**：`block()` 与 `arm()` 原先只 push 符号帧（ScopeKind.BLOCK），现各包一层 factFrame——块级/臂级事实帧使块内 var/模式槽位的类型事实**随块/臂退出失效**。这是验收 #8（出作用域不得串味）的机制前提：命题项按**名字**结构相等（HirTName.equals 只认 name，ADR P-STRUCT-EQ），若无块级帧，内层 \`var n: Nat\` 的 \`n >= 0\` 会在函数帧滞留并被外层同名 \`n: Int\` 沾用（假事实 = 静默放行，R-NO-GUESS 红线）。
- 不可注入的形态：无事实帧（结构体声明位等 \`factFrames.isEmpty()\`）跳过；Int/Rat/别名/联合/容器/泛型 → 无事实（Int 可正可负，Rat 同）。

### 1.3 结构体字段形参

`c: C`（用户 struct C(v: Nat) 无类型实参）⇒ 逐 Nat 字段注入 \`c.v >= 0\`（HirTField(HirTName("c"), "v")，与 PropNormalizer.termQuiet 的字段项形态同构——命题项名字/字段名等价）。@mut 字段同注入（值仍是 Nat）。

### 1.4 上界截断（不随机丢弃）

- 每帧 \`TYPE_FACT_CAP = 64\`（`typeFactCounts` 与 factFrames 同层 push/pop）。
- 超界：按**声明顺序**后续事实不再注入（注入侧计数，不是事后剔除——保序），同帧落 \`H-TYPEFACT-CAP\`（hint 族）**一次**（同帧同理由不刷屏）。
- 全 e2e 套件实测零触发（无任何样本单帧类型事实 > 64）——H-TYPEFACT-CAP 仅在 a11/a12 仓内测试（120 形参）构造触发。

### 1.5 分支事实（与 ADR-13 命题 if[p] 的关系）

- \`if[p] {...} else {...}\`（IfPropStmt，ADR-13 既有）：支种子 \`p\` / \`¬p\`，取两支新事实交——**本次完全未动**。
- \`if (cmp) {...} else {...}\`（IfExpr 值位，S-34）：新增 \`branchProp(cond)\` 判定——条件为比较/等值二元（\`== != < <= > >= ∈ in\`，两侧均可 termQuiet 命题化）或其 ¬ 组合时，then 支 \`factFrame(listOf(condProp))\`、else 支 \`factFrame(listOf(¬condProp))\`——**与 if[p] 同一 factFrame(prop)/factFrame(¬prop) 注入口径，不另起通道**。
- 条件不可命题化（调用 Bool 函数、字面量、非比较算符）→ 不注入（b13 测试断言）。注：\`in\` 保留入表（prover consume 支持），但区间端点项不可 quiet 化时自然落 null——保守。

## 二、先红后绿（验收 8 例 + 补充）

BEFORE（t65b 槽位，未实现时）：a1/a6/a9/b3/b4/b7/a8 全红（事实未注入；a2/a5 正确拒绝）；AFTER（t65a5）15/15 全绿：

| # | 源 | BEFORE | AFTER |
|---|---|---|---|
| 1 | \`n: Nat\` + \`axiom n != 0\` ⇒ \`∵ n != 0 ∴ n >= 1\` | E-CANNOT-PROVE | **通过**（类型事实 + 不等式通道）|
| 2 | \`n: Nat\` 无公理 ⇒ \`∴ n >= 1\` | 拒绝 | 拒绝（n 可为 0——判据未放宽）|
| 3 | \`if (n == 0) {...} else {...}\` else 支 ⇒ \`∴ n != 0\` | E-CANNOT-PROVE（Γ 空）| **通过**（¬(n==0) 注入）|
| 4 | then 支 ⇒ \`∴ n == 0\` | 同上 | **通过** |
| 5 | \`n: Int\` ⇒ \`∴ n >= 0\` | 拒绝 | 拒绝（Int 无事实）|
| 6 | \`n: Nat\` ⇒ \`∴ n >= 0\` | E-CANNOT-PROVE | **通过** |
| 7 | then 支（\`n: Nat\`）⇒ \`∴ n >= 0\` | E-CANNOT-PROVE | **通过**（分支事实 + 类型事实叠加）|
| 8 | 内层 \`var n: Nat\` 出块后 \`n: Int\` 复用 | —（间隙未测）| 串味**未发生**（块级帧：出块即失效；8a 正向 + 8 负向双断言）|
| 补 | \`c: C(v: Nat)\` ⇒ \`∴ c.v >= 0\` | 红 | **通过** |
| 补 | \`n: @mut Nat\` ⇒ \`∴ n >= 0\` | 红 | **通过**（demoteView 按值）|
| 补 | 120 形参上界截断 | — | 截断保序 + H-TYPEFACT-CAP 留痕（a11/a12）|
| 补 | \`if (gate(n == 0))\`（Bool 调用）| — | 不注入（b13）|
| 补 | 信任审计隔离 | — | 无 D-AXIOM-POST 副产（a14）|

注：验收表原文的 \`∵ ∴ P\` 形在语法上是 E-SYNTAX（P900 S-24：裸前提反糖为 \`∴ P\`）——测试按真实语法 \`∴ P\`（premises=[⊤]，S-8）落形，语义同。

## 三、回归门槛（实测，2026-11-08）

| 门槛 | 结果 |
|---|---|
| \`:frontend:test\` | **647/0 失败错误**（632 基线 + ProveFactInjectionTest 15 例）|
| e2e | **65/68**；3 既有红码集与项数逐字不变（list 15项 D-TYPE-INFERRED,E-TYPE-MISMATCH,E-UNBOUND-NAME,H-SHADOW,W-NON-EXHAUSTIVE；手动证明 2项 D-BTS,E-CANNOT-PROVE；模块主 4项 E-UNBOUND-NAME）。仅 2 个 true 样本节点数 +11/+16（事实命题入 HirBecause.context，新增 HIR 节点；**码集与项数完全不变**）。结构性违规 0。|
| 双快照 | 零警告零错误（HIR 结构未改——HirProp/HirNote 既有字段承载新内容）|
| 性能哨（任务书硬要求）| BEFORE \`2.717s\`（JUnit 时间，t65t0 槽位，--rerun-tasks）；AFTER 三次实测 \`4.583 / 2.417 / 2.178s\`（首跑为冷启噪声；稳态 \`≈2.42s\`）——**退化 −11%，未超 30%**；无需说明例外。事实注入放大 prover 输入的影响经 normalizeFacts 去重吸收（同帧同事实只计一次）。|
| 偏序/减法/燃料/深度哨/轮次上限 | 未动（仅新增注入与帧包装；E-CANNOT-PROVE 判据 R-NO-GUESS 一字未动——a2/a5/b13 负例守门）|
| H-TYPEFACT-CAP 触发 | e2e 全套零触发（单帧类型事实上界内）|

## 四、与 ADR-13 的关系声明（不重复造轮子）

分支事实的机制层（factFrame(prop) / factFrame(¬prop) / common 交集）是 ADR-13 的既有实现，本次**仅在 IfExpr 表达式位接入同一机制**（branchProp 判定 + 复用 factFrame）；类型事实是**第三种事实来源**（1.1 表），与 ADR-13 的命题位事实共用 allFacts 池，但生命周期由其声明位帧决定（1.2）。Axiom/by/when-prop 的注入口径一律未动。

## 五、已知限制与步骤衔接

1. 类型事实只覆盖 \`Nat\`（v >= 0）；Int/Rat 无事实（用户裁决语义）；`Nat 无界` 已由 ADR-40 口径化。
2. 别名 / 联合 / 容器型参（如 \`List[Nat]\`）不展开注入元素事实（V1 界；t66 类型收窄若需要再议）。
3. \`H-SHADOW\` 一次回归插曲（实现期 G2 模式槽位双重 declareLocal → 同域遮蔽假阳性），已修复且由 e2e 逐样本 diff 佐证（25 样本 H-SHADOW 全消）。
4. **t66（类型收窄）输入就位**：Γ 现在同时携带类型事实与分支事实——「Γ ⊢ 前 >= 后 ⇒ Nat 智能转换」可直接基于 allFacts() 实现收窄规则，无需再补事实通道。
