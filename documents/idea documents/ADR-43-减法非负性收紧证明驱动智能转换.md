# ADR-43：减法非负性收紧（ST-10.2：Γ ⊢ 前>=后 ⇒ Nat - Nat : Nat）

> 状态：已落地 · 日期：2026-11-08 · 前置：ADR-40（ST-10.1 减法规则 rank=max(l,r,1) ⇒ Nat-Nat : Int）、ADR-42（类型事实 + 分支事实入 Γ）、ADR-6 §四 4.4（Γ 帧栈）、ADR-33（t51 燃料）
> 来源：**用户裁决（ST-10.2）**——「`Nat - Nat` 应该返回 `Int`，由自动证明器进行智能转换，比方说当前环境可以证明前 `Nat >=` 后 `Nat`，就智能转换返回值为 `Nat`。」
> 范围：Elaborator（期望栈 `expectAt` + 收窄 `narrowMinus` + 信任审计 `trustedBase`）；Semantic/`compileNarrowAudit`（观察入口）；测试 NarrowSubtractionTest 19 例（任务书验收 10 例 + 9 补充）。**判官主体/偏序/减法表/事实注入/燃料/深度哨/轮次上限/E-CANNOT-PROVE 判据一律未动**。

## 一、设计

### 1.1 触发条件（严格性——验收 #5/#7）

收窄尝试仅当**四条件同时成立**才发生（`narrowMinus` 逐条短路返回）：

| 条件 | 实现位 | 松弛后果 |
|---|---|---|
| ① 运算符 `-` | `e.op != "-"` | `+/*//` 不收窄 |
| ② 两操作数（`demoteView` 后）Nat | `nt(leftT)/nt(rightT) != "Nat"` | Int/Rat/混合不收窄（S8/S8-混合 e2e 守门）|
| ③ 结果类型 Int（ST-10.1 的表值）| `rawN != "Int"` | 已是 Nat 的位不重复判定 |
| ④ **直接期望类型是 Nat** | `expectTypes.lastOrNull()` | **期望 Int/Rat 时 0 尝试**（验收 #7：可观察计数器 `audit.attempts == 0`）|

「期望」由**直接期望栈** `expectTypes` 提供（`expectAt(t){...}` push/pop）：入栈站点只可能是「该表达式被直接适配到期望类型」的位置——return 值（块体 `returnStmt` + 表达式体/lambda 体 `funBody`）、直接调用实参（被调形参静态形态）、var 标注值、赋值右值、形参默认值。内层站点恒为直接期望：`return f(a - b)` 时栈顶是 `f` 的形参类型而非外层 retT——`a - b` 按真实期望判定，不会错位尝试。**不做无目标的全量尝试**（验收 #5/#7 锁定）：期望 Int/Rat（升宽通路 Int<:Rat）时连证明器都不调用。

调用实参位期望的**零副作用取得**：只看被调形参声明 AST 是否就是裸 `NamedType("Nat")`（`takeIf { name == "Nat" && args.isEmpty() }`），不做任何类型解析——早期实测 `type(q.type)` 提前解析会给 11 个 e2e 样本补 E-UNBOUND-NAME（理论参数/模块外名），已回退为 AST 静态判定。

### 1.2 证明目标与结果落地

- 目标：`HirPAtom(">=", [termQuiet(e.left), termQuiet(e.right)])`，在 `allFacts()` 上以**既有** `prover.proves(facts, goal)`（ArithChannel ±1 差分图 + consumeNe；R-NO-GUESS 未动）判定——**不新写证明引擎**。
- 成功 ⇒ 该 `HirBinExpr` 类型记 `Nat`，并落留痕 `HirNote(SUPPLEMENT, "proof:arith-narrow", "减法按 ST-10.2 收窄为 Nat：Γ ⊢ <goal>（规则 <rule>；证据：<必要公理或类型/分支事实>）")`（provenance=proof:arith-narrow + 证据事实，任务书要求 3）。HIR 结构字段未改（HirNote 既有字段承载）。
- 失败 ⇒ **逐字保持 Int**（ST-10.1 不变），期望位为 Nat 时落补充提示 `D-NARROW-SCOPE`：「无法证明 <goal>，减法 … 保持 Int（ST-10.2 收窄未生效）。当前 Γ：<逐条事实>。可先断言 <goal>（axiom …，或以 if 守卫引入分支事实）…」——含「无法证明」+ Γ 实况 + 可操作建议（验收 #10）；提示不写承诺：断言成立确实点亮 Γ ⇒ 可证（R-AXIOM/if[p] 既有通道）。失败永不放行（#2/#4 负例守门：E-TYPE-MISMATCH 照落）。

### 1.3 信任审计（trustedBase 与 D-AXIOM-POST 的分家——验收 #8/#9）

| 事实来源 | trustedBase | 说明 |
|---|---|---|
| 用户 `axiom`（AxiomStmt 注入位登记 `axiomFacts` 结构集合）| **计入**（留一法：`proves(facts - ax, goal) == null` ⇒ 必要公理）| 与 t65 `provenance=type` 事实完全隔离 |
| 类型事实（Nat ⇒ v>=0）/分支事实（¬(n==0)）| **不计入** | 证明消费它们不构成「用户免证输入扩张」 |

- 既有信任审计码 `D-AXIOM-POST`（@axiom 函数签名信任，ProofChecker 唯一信任审计通道）**未动**；`trustedBase` 是本任务新增的**收窄专用**观察量（经 `compileNarrowAudit` → `NarrowReport(audit)` 暴露，生产路径 `compile` 无变化）。
- 留一法按命题结构相等（HirProp P-STRUCT-EQ）逐条剔除重证：`Γ = {a>=b(axiom)}` 收窄 `a - b` ⇒ 去 `a>=b` 后证不出 ⇒ 计入；`Γ = {n>=0, ¬(n==0)}` 收窄 `n - 1` ⇒ 无公理 ⇒ trustedBase 空（#9）。

### 1.4 预算（任务书要求 5）

- 每函数帧 `NARROW_TRY_CAP = 8`（`FunFrame.narrowTries`，声明/源序递增——确定性，不随机），超限不再尝试；全局 `narrowAttempts` 计数器供观察。
- 不开新燃料池：尝试本身只走 `prover.proves` 的**既有**搜索预算（ArithChannel/neLimit、substBfs 的 steps/nodes 双上限——与 by/strategy 共用的同一机器），t51 燃料/深度哨/轮次上限未动（证明调用不经过 TypeInfer 的 fuel 池，预算由 prover 既有界与帧级尝试上界双重控制）。

### 1.5 与 ST-10.2 / P-12 第 ③ 级的关系

- ST-10.1（ADR-40）回答「`Nat - Nat` 的**类型**是什么」：`Int`——Nat 无符号，差可负，**本 ADR 不改这个表值**。
- ST-10.2（本 ADR）回答「差**何时可再视作 Nat**」：当 Γ 可证 `前 >= 后`（差非负）时，**期望位为 Nat** 的适配按智能转换通过。这是**证明驱动的值域收紧**，不是类型规则替换：类型层仍是 Int，收窄发生在「Int → Nat 适配需求 + 证明在手」的位点。
- P-12 第 ③ 级（证明器交付正确性）与 R-NO-GUESS：收窄结论的强度完全等于 `proves` 的结论强度（同一 Γ、同一通道、同一判据）；**证明失败不是错误**，落 E-TYPE-MISMATCH（只是多了可操作提示），E-CANNOT-PROVE 判据未动、不得把失败变放行（负例 #2/#4 守门）。

## 二、先红后绿（验收 10 例 + 补充 9 例）

BEFORE（`narrowMinus` 禁用探针，`Elaborator.kt.t66bak` 口径逐字等价于 t65 末态；e2e 65/68 复现）：

| # | 源 | BEFORE | AFTER |
|---|---|---|---|
| 1 | `fun f(a: Nat, b: Nat): Nat { axiom a >= b; return a - b }` | E-RETURN-TYPE | **通过**（收窄为 Nat）|
| 2 | 同上无 axiom（表达式体 `= a - b`）| E-TYPE-MISMATCH | 拒绝（逐字回退；+D-NARROW-SCOPE 提示）|
| 3 | `fun f(n: Nat): Nat { if (n == 0) { return 0 } else { return n - 1 } }` | E-RETURN-TYPE | **通过**（else 支 Γ：n>=0, ¬(n==0) ⊢ n>=1）|
| 4 | `fun f(n: Nat): Nat = n - 1` | E-TYPE-MISMATCH | 拒绝（无守卫）|
| 5 | `: Int` | 通过 | 通过，**0 尝试** |
| 6 | `: Rat` | 通过 | 通过（Int<:Rat 升宽，不收窄）|
| 7 | 尝试计数（期望 Int）| — | **`audit.attempts == 0`** |
| 8 | `axiom a >= b` 参与收窄 | E-RETURN-TYPE | trustedBase = [`a >= b`]（留一法必要公理）|
| 9 | 仅类型/分支事实收窄（#3 形）| — | trustedBase = [] |
| 10 | 失败诊断文案 | 「Int 与 Nat 不符」 | D-NARROW-SCOPE：无法证明 + Γ 实况 + 可先断言 |

补充（S1~S9）：块体 return 双道、**调用实参位**（`take(t, n - 1)`——本规则存在的理由，验收表外的核心场景）、var 标注位、赋值右值位（@mut Nat）、Int/混合操作数不触发、帧级尝试上界 8、HIR 留痕 proof:arith-narrow、默认值位。19/19 全绿。

## 三、回归门槛（实测，2026-11-08）

| 门槛 | 结果 |
|---|---|
| `:frontend:test` | **666/0**（647 基线 + NarrowSubtractionTest 19 例）；先红后绿探针单独执行后删除 |
| e2e | ok 计数 **65/68 不变**；手动证明 2 项/模块主 4 项**逐字不变**；list.subl 由冻结 **15 项 → 13 项**（队长裁决 A 案：重冻结，规格直接强制，见 §四 4.1）：消失 4 项（elementAt/take/drop/slice 的 `n-1`/`from-1` 实参位收窄）+ 新增 2 项 D-NARROW-SCOPE（slice 的 `to-1` 位失败提示），逐条对应见 `documents/冻结红重冻结-t66-list.md`。结构性违规 0；68 行样本快照 diff 仅 list.subl 一行 |
| 双快照 | 零警告零错误（HIR 结构字段未改——HirBinExpr 既有 notes 字段承载 provenance 留痕）|
| 性能哨 | BEFORE 2.624/2.674s（均值 2.649s，--rerun-tasks ×2）→ AFTER 2.786/2.660s（均值 2.723s）：**+2.8% < 10%**；收窄只在「期望 Nat 且减两 Nat」触发，逐样本快照 68 行 diff 1 行 |
| 偏序/变构性/事实注入/燃料/深度哨/轮次上限 | 未动（E-CANNOT-PROVE / R-NO-GUESS 一字未动——#2/#4 负例守门）|

### 4.1 为什么这次基线可以动（判据登记）

判据：**基线（e2e 冻结红）改动必须由规格变更直接强制，不得由「顺手整齐」驱动。**

- 成立性：ST-10.2 落地后，`elementAt/take/drop/slice` 的 `n - 1`/`from - 1` 在 else 支的非负性**真的可证**——4 条 E-TYPE-MISMATCH **本该消失**；为维持「15 项」而保留它们 = 让已修好的证据维持错误状态 = 伪造基线。
- 反向对照：t63 讨论过的「改 take/drop/slice 签名 `Nat → Int`」不满足本判据（签名改型不是收窄强制的），维持驳回——同一判据两个方向都成立，区别在**因果**：A′ 是顺手整齐，A 是规格落地。
- 留档目的：将来有人质疑「你们改过基线」时，答案在文档里（本节 + `documents/冻结红重冻结-t66-list.md` 的 15→13 逐条对应），不在 anyone 的记忆里。

## 四、与既有机制的关系（不重复造轮子）

- 收窄的**证明侧**完全复用 ADR-42 的 Γ（allFacts）与 ProofChecker.proves/AarithChannel——本 ADR 只加「何时期望 Nat」的判定与结果落地。
- 期望栈是**新的薄层**（Elaborator 私有 ArrayDeque + inline expectAt），不侵入 TypeInfer 判官：TypeInfer 的 adaptOk/assignTypes/funBody/returnStmt/callFun 签名一律未改；收窄发生在表达式求值侧（HirBinExpr 的 type 字段），判官随后按既有偏序核对。
- 信任审计与 D-AXIOM-POST 分家：既有信任审计（@axiom 函数签名）通道未动，trustedBase 是收窄专用的第二观察量（compileNarrowAudit，工具/测试入口）。

## 五、已知限制与上报

1. **e2e 冻结红重冻结（队长已裁决 A 案）**：任务书同时要求典型场景 `take(t, n - 1)` 通过（实参位收窄）与「3 既有红项数 15/2/4 逐字不变」——list.subl 的冻结红恰由 6 个此形态 E-TYPE-MISMATCH 构成，二者不可兼得。裁决：重冻结（规格直接强制 ⇒ 基线可以动，见 §四 4.1）；逐条对应（15→13）登记于 `documents/冻结红重冻结-t66-list.md`。
2. `to - 1`（Γ：¬(to<=from)）：差分通道不处理 ≤ 的否定，证不出——按设计落 E-TYPE-MISMATCH + 提示；用户可用 `axiom to >= 1` 或改用 `>=` 守卫点亮。
3. 收窄不跨函数边界（被调函数的 Γ 不影响调用位），不处理复合项 `a-b-c` 的分段证明（goal 项由 termQuiet 生成，递归项进入差分图即由通道判定）。
4. 观察入口 `compileNarrowAudit` 仅供工具/测试（任务书验收 #7/#8/#9 的可观测计数器）；生产路径 `compile` 无任何签名变化。

## 六、交付清单

Elaborator.kt（期望栈 + narrowMinus + trustedBase + 7 处 expectAt 站点）、Semantic.kt（compileNarrowAudit/NarrowReport/NarrowObserver）、NarrowSubtractionTest.kt（19 例）、本 ADR、台账（表二追加 t66 行）、log（2026-11-08 追加 t66 节）。