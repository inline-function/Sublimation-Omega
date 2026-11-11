# ADR-26：扩展函数桶键规范化 + V1 禁止重载（t38）

- **状态**：已落地（t38，main 干净，测试验收待 t40 并行冲突解除）
- **关联**：卷 TS-11/TS-13/TS-14；ADR-25（P-8/D 语义层裁决 R-1~R-4）；t37（文法 S-43 括号化接收者）
- **日期**：2026-11-06

## 背景

t37 让文法支持括号化接收者（`fun (Nat,Nat).f()`、`fun ((Nat)=>Nat).g()`），receiver 以完整 Type 存
入 HIR。但扩展桶（`extFuns`）的键为 (接收者类型头, 名字)，而**元组/函数/联合类型没有名字**——
登记与查找必须用**同一套**规范化，否则非具名接收者的方法糖/直接调用无法解析。同时，原「同
(头, 名字, 形参类型列表) 并存 + 按 arity 初选、无匹配取首项」的重载机制在 Go 派语言定位下没有
样本支撑，且会在同 arity 不同形参时**静默选中错误候选**并报在错误的重载上。

## 裁决 R-8：类型头规范化（单一源）

落地为 `semantic/ExtFunHeads.kt`——登记侧（Collector：AST receiver 的 `ofAst`）与查找侧
（Elaborator：实参 HIR 静态类型的 `ofHir`）**两个投影自同一张规范表**：

| 接收者 | 键 | 说明 |
|---|---|---|
| `Nat` | `Nat` | 具名类型头即名字 |
| `List[A]` | `List` | 类型实参留在签名比对 |
| `core.Point` | `core.Point` | 限定链保留 |
| `(Nat, Nat)` | `Tuple/2` | 形状/元数 |
| `(Nat, Nat, Nat)` | `Tuple/3` | |
| `(Nat) => Nat` | `Fun/1` | |
| `(Nat, Nat) => Nat` | `Fun/2` | |
| `A \| B` | `Union/2` | 变体数 |
| `((Nat, Nat) => Nat)` | `Fun/1` | 嵌套形状进签名比对，不污染外层键 |
| `@mut T` / `@atomic T` | 同内层头 | 透明委托 |
| ErrorType / 合成体 `(推导:x)` / `(未密封)` / Self | null | 不登记不查找 |

一致性由 `ExtFunHeadsTest` 的对偶断言守护（每行键规范 AST/HIR 各断言一次）——杜绝登记与查找两套口径
的静默漂移。

## 裁决 R-9：V1 禁止重载

1. **自由函数**：同名即 `E-DUP-DECL`（不变）。
2. **扩展函数**：同一 **(接收者类型头, 名字)** 只允许一个声明；已存在则 `E-DUP-EXT`（**两处位置
   均报**），不论形参类型列表或 arity 是否不同。
3. `pickExtDecl`（按 arity 初选 + 首项兜底）**删除**；桶内至多 1 项，直接取唯一项
   (`singleOrNull()`)；arity/类型不符由 `callFun` 正常报 `E-ARITY`/类型不符，**不再静默兜底**。
4. `sigSkeleton` 保留作 E-DUP-EXT 诊断文本，**不参与判重**（`declareExtFun` 的 dupKey 从
   `Triple(头, 名, 形参骨架)` 改为 `Pair(头, 名)`）。

### 依据

- 用户方向为 Go 派，Go 无函数重载；
- 样本库实测**零处**依赖同 (接收者, 名字) 不同形参的重载；
- 现状「按 arity 初选、无匹配取首项」会在同 arity 不同形参时静默选中错误候选并报在错误的重载
  上，属真实隐患（本次测试反转实证：`fun Nat.f(x: Nat)` 与 `fun Nat.f(x: Str)` 在旧口径下共存，
  新口径两处均报 `E-DUP-EXT`）。

## 顺带修复（t37 线索）

`Elaborator` 预扫描原以 `(d.receiver as? NamedType)?.name` 取头——元组/函数类型接收者 head 为
null 时**漏入 `funDecls` 自由函数通道**。t38 起统一用 `ExtFunHeads.ofAst`，不可规范化头不入任何
索引；`W-EXT-OBSOLETE` 的提示文本头也改用规范键。

## 与 t39（帧链遮蔽）的衔接

R-8/R-9 的**唯一性按域成立**：t39 把 `extFuns` 从全局单表改为帧链（局部扩展函数声明进帧、出帧即
消失，遮蔽粒度按 (头, 名字) 键）后，`declareExtFun` 的判重与 `ExtFunHeads` 键函数**无需改动**——
本裁决只定义键与判重口径，不假设表的作用域形态。局部扩展函数（语言核心特性）不受本裁决限制。

## 样本迁移

`tuple.subl` 剩余 9 个元组形状（arity 2..10）改用元组接收者：`fun (a, b).size[a, b](): Nat = 2`
与具名形状（`fun SingleTuple[a].size[a](): Nat`）同源登记，键分别为 `Tuple/2…Tuple/10`。

## 验证（待 t40 解除并行冲突后跑最终）

- `compileKotlin` 零错零警告（已通过，含 `.kt:NN` 命中 0）；
- `:frontend:test --rerun-tasks` 全绿 ≥ t37 交付数（t38 新增 ExtFunHeadsTest 8 + ExtensionSemanticsTest
  净增，重载反转 2 条）；
- e2e ≥ 64（tuple.subl 迁移后保持）。
