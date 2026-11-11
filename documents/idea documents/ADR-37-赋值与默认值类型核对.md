# ADR-37：P-10 步骤 1 后健全性漏洞补齐（赋值/字段赋值/默认值类型核对）

> 状态：已落地（t60） · 日期：2026-11-08 · 前置：ADR-36（R-34~R-38 健全化收口）、t59 容忍面清单（G1/G2 登记）
> 范围：前端语义层 `Elaborator` + `TypeInfer`；判官 `adaptOk` 严格性与 e2e 样本一律未动。

## 一、背景（为什么现在做）

t59 穷举 R-38 容忍面时顺带登记了两处**与合成位/落点闸无关**的健全性缺口：赋值语句与形参（字段）默认值
**根本不经过任何类型核对**。在赋值不受检查的地基上叠加 P-10 步骤 2 的子类型偏序等于在破地基上盖楼，
故优先级高于步骤 2。队长已逐条独立复现（t60 任务书 BEFORE 证据）。

## 二、BEFORE（2026-11-08 00:09 脚本模式探针，t60 前基线）

| 探针 | BEFORE 输出 |
|---|---|
| `var x: @mut Nat = 1; x = "abc"` | 零诊断（Str 静默赋给 Nat） |
| `var x: @mut Nat = 1; x = true` | 零诊断 |
| `struct A(a: Nat); struct B(b: Nat); var p: @mut A = A(1); p = B(1)` | 零诊断（结构体互赋静默） |
| `var u: Optional[Nat] = Some(1); var m: @mut Nat = 0; m = u` | 零诊断（联合直赋成员静默） |
| `var z: @mut Nothing = boom(); z = 1` | 零诊断 |
| `@mut struct Box(@mut n: Nat); var b: @mut Box = Box(1); b.n = "s"` | 零诊断（字段写类型裸奔） |
| `struct P(x: Nat); var p: @mut P = P(1); p.x = 5` | 零诊断（字段写可变性裸奔） |
| `@mut struct Box(@mut n: Nat); var b: Box = Box(1); b.n = 5` | 零诊断（不可变接收者写字段） |
| `fun f(a: Nat = "abc"): Nat = a; var r = f()` | 零诊断（默认值类型裸奔） |
| 对照（合法）：`@mut var b: Box = Box(1); b.n = 2` / `fun f(a: Nat = 0): Nat = a` 等 | 零诊断 ✓ |

## 三、改动（同一条判据，三处汇聚）

1. **`TypeInfer.reportMismatch` 增 `code` 形参（默认 `E-TYPE-MISMATCH`）**：R-38 落点闸的「合成位容忍」
   与「燃料耗尽抑制」逻辑只此一份，新核对点共用而非另写一套。
2. **`TypeInfer.assignTypes(targetT, valueT, pos)`（t60-1）**：判定同 `varAnnotated`——`demoted（数值塔
   降级）|| (!adaptOk(valueT, targetT) && unify == null)` 失败落 `E-TYPE-MISMATCH`，文案
   「赋值右值 X 与目标 Y 不符，两边类型不一致」。render 输出含 @mut 包装（如「@mut Nat」），与
   varAnnotated 文案口径一致。
3. **`TypeInfer.defaultType(paramName, want, got, pos)`（t60-3）**：同一判据，新码 **`E-DEFAULT-TYPE`**
   （任务要求落此码；与既有 `E-DEFAULT-ORDER` 同族；失效路径经裁决后改动）。
4. **`Elaborator` AssignStmt 分支**：先可写核定（t8 名字位不变；新 `fieldWriteOk` 字段位），后类型核对
   `infer.assignTypes`；字段目标型经 **t60-1b** 对 @mut 接收者按字段声明型核（读路径的 @mut 剥离不在
   本任务范围，见 §五）。**两闸独立、缺一不可（可变性 ≠ 类型）**。
5. **`Elaborator.param()`**：形参/字段默认值在声明位（求值环境=声明处，t45 口径）经
   `infer.defaultType` 核对——结构体字段默认值与函数形参默认值**共用同一入口**。

## 四、旧路径已死（不存在绕过通道）

赋值的**解析层**入口唯一：`Parser.parseAssignOrExprStmt`（Parser.kt:1112-1119）只接受左值为
`NameRef` 或 `FieldExpr`（其余位置的 `=` 是命题等式中缀），两形态在语义层 `AssignStmt` 分支**汇聚到
同一个** `infer.assignTypes` 核对点：

- `NameRef` → 目标型 `etype(target)`；可写按 t8 既有口径（`E-IMMUT-ASSIGN`）。
- `FieldExpr` → 可写经 `fieldWriteOk`（字段 @mut + 接收者可写），型经 t60-1b 字段声明型；上游
  `fieldMissing`/`E-ASSIGN-TARGET` 覆盖未登记形态（结构体未知/字段未找到时类型闸继续，不静默放过）。
- 下标（`a[i] = v`）等形态**文法不存在**（S-30 注：仅名字/字段），故无第三通道；将来若扩文法，须在
  本分支显式接入同一 `assignTypes` 并落诊断，不允许静默。

默认值：形参与字段默认值**都**经 `param()`（SymbolKind.PARAM/FIELD 共用），无第二入口；调用位
填补（`defaultExprAtParam`）只负责按位供给，类型核对已前移到声明位。

grep 佐证：`AssignStmt` 分支内 `assignTypes` 调用为本分支唯一类型核对点；`param()` 内 `defaultType`
为默认值唯一核对点。

## 五、AFTER（2026-11-08 00:50 复测）

| 探针 | AFTER 输出 |
|---|---|
| `x = "abc"` / `x = true` / `p = B(1)` / `m = u` / `z = 1` | `E-TYPE-MISMATCH: 赋值右值 X 与目标 Y 不符，两边类型不一致` |
| `x = 3.5`（Int 目标） | `E-TYPE-MISMATCH`（数值塔降级，同 varAnnotated） |
| `b.n = "s"`（@mut 接收者） | `E-TYPE-MISMATCH`（t60-1b 声明型核对） |
| `p.x = 5`（不可变字段） | `E-IMMUT-ASSIGN: 字段 x 不可变，不能写。要改它？在结构体声明里给字段加 @mut` |
| `b.n = 5`（不可变接收者） | `E-IMMUT-ASSIGN: 不能通过不可变接收者写字段 n…` |
| `fun f(a: Nat = "abc")` | `E-DEFAULT-TYPE: 默认值 Str 与形参 a 的类型 Nat 不符…` |
| `@mut struct Box(@mut n: Nat = "s")` | `E-DEFAULT-TYPE`（字段默认值同入口） |
| 对照（全部通过）：`x = 5`、`x = x + 0`（@mut var 绑定形态）、`p = A(2)`、`@mut var x: Int; x = 0`（塔升级）、
  `x = boom()`（Nothing→任意，R-35）、`@mut var y: Any = 5; y = "abc"`（Any 通配）、`when (u) { is Some(n) -> m = n }`
  （is 收窄后赋值）、合法字段写三形态（@mut var 绑定 / @mut 类型位 / @mut 形参）、合法默认值（形参/字段） | 零错误 ✓ |

**e2e 影响：无转红**——68 样例仍 65/68，3 条既有红（list/手动证明/模块系统-主）诊断码集逐字不变；
 原因：全部既有字段写样本（7 处）均为 `@mut struct X(@mut n: Nat)` 形态且经 @mut 绑定或 @mut 类型位写入
 （特性巡礼/类型系统/副作用与unchecked），全部既有名字赋值（5 处）均为同型算术自增。R-38 闸与燃料逻辑
 未改，合成位容忍面不变。

## 六、已知的 t60 暴露（不在本任务范围，登记给后续）

`var x: @mut Nat = 1` **类型位** @mut 的算术（`x = x + 1`）在 binExpr 旧路径里被推出 `Bool` 型，
此前被「赋值零核对」静默吞掉，现被新核对报为 `E-TYPE-MISMATCH`（右值 Bool vs 目标 @mut Nat）。
e2e 与全部单测不受影响（均用 `@mut var x = 1` 绑定形态或合成位路径）。同族的「@mut 接收者字段**读**
不剥离 @mut」（走合成位 → R-38 闸容忍）是 t59 M2 家族既有形态，随 P-10 步骤 4 字典/签名字典消解。

## 七、验收

- `:frontend:test --rerun-tasks` = **573/0**（553 基线 + 20 新 AssignDefaultsCheckTest；其中 t59 的
  R38ToleranceSurveyTest g1/g2 已按封堵翻成 assertTrue）。新增用例：a1~a11 负例（赋值 6 + 字段写 3 +
  默认值 2）与 c1~c9 对照例（数值塔升级、Nothing、Any、联合 is 收窄、合法字段写三形态、合法默认值）。
- e2e **65/68**（3 既有红逐字不变）；结构性违规 **0**；双快照（compileKotlin/compileTestKotlin）
  BUILD SUCCESSFUL 且 `w:`/`e:` 行 **0**。
- 判官 `adaptOk` 主体未改（仅 `reportMismatch` 增形参）；FUEL_DEFAULT 与深度哨未动；e2e 样本未动。