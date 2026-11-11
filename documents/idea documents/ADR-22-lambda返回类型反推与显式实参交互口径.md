# ADR-22：lambda 返回类型反推 与 显式类型实参交互口径（t25）

| 字段 | 值 |
|---|---|
| 编号 | ADR-22 |
| 主题 | B9：lambda 实参返回类型反推（两遍定型）+ 显式类型实参与反推的交互口径 |
| 任务 | t25（t24 收尾判断：最后一项真缺陷，2 样本） |
| 依赖条款 | S-16 lambda/尾随 lambda、ADR-17 调用点泛型实例化（instCall 基座）、ADR-21 显式类型实参（显式优先不覆盖）、决策 55/57 双向反推 |
| 上游 | t8 类型推导、t18 instCall、t23 显式类型实参 |
| 下游 | P-8 类型系统改向 |

## §一 根因

`mapResult(r, { x => x + 1 })` 一族失败根因不在 instCall 本身，而在**定型次序**：
调用点先把全部实参 `expr()` 定型，lambda 形参位此时无任何期望，只能落 synthetic；
`x + 1` 因 x 的 synthetic 形参无法解出 Nat，lambda 体亦退化为 synthetic。随后的 instCall 中
`unifyInto` 对 synthetic 期望位（`if (aa.isSynthetic()) return true`）**宽容返回而不绑定**——
U 因此永远无解，调用结果类型携带字面 U，外层 `unwrapOr` 的形参位回代出「实参 2 Nat 与形参 U 不符」
与 biggest 的「函数体是 U」级联。

## §二 裁决与修法

**两遍定型**（仅调用点 FUN/THEOREM 且实参含 LambdaExpr 时启用；reporting 仍在 callFun 单点）：
1. 第一遍：非 lambda 实参先定型，与显式类型实参一起经 `TypeInfer.lambdaExpectFormals`（= instCall
   换名→逐位 unifyInto→回代）解出理论参数；已解位按解集回代，未解位（`_iN.名` 新鲜变量）经
   `cleanTpVars` 落 synthetic——形参位无着落时体按未知处理，**不得编造类型**。
2. 第二遍：lambda 实参经 `lambdaExpect` 通道取**期望形参的参数位**定型（`HirLambda` 的形参
   `HirParam` 按期望类型登记作用域，体内算术/字段可用）；取用即还（嵌套 lambda 不继承）。
3. 全量核对不变：lambda 定型后与其它实参一起交回 `callFun` 的 instCall/adaptOk——
   `unifyInto` 的 ret 位「lambda 体定型后与该变量合一」在此发生（体为具体类型时绑定 U）。

**显式类型实参交互**（要求 2 的口径）：显式给定时以显式为准——lambdaExpectFormals 内显式实参
先于 unifyInto 绑定（与 instCall 同序，冲突不改写），lambda 期望位取**显式替换后的形参类型**；
显式与 lambda 体冲突时（显式 U=Str、体 Nat），由形参位核对落 E-TYPE-MISMATCH，不被 lambda 改写。

**不放宽**（要求 3）：反推失败仍报错——lambda 体与已定形参不可合一时（体 Str、形参返回 Nat；
或 U 已由 init 解 Nat 而体 Str），E-TYPE-MISMATCH 照报（见 LambdaInferenceTest 两反例）。

## §三 旧实现点查

旧 CallCheck 对 lambda 实参按「形参位合成 synthetic 放行」的宽容口径（决策 55/57 的旧注脚），
旧 mapResult 同族亦不反推——本裁决不偏离旧报错边界，只在**有期望位**时把反推做实。

## §四 验证与残留

- commandsRun：`:frontend:compileKotlin` 绿；`:frontend:test --rerun-tasks`
  **327 tests 0 failures**（t22 后 321 + 新增 LambdaInferenceTest 6，无回归）。
- e2e：ok 56/68 → **58/68**；错误处理.subl（旧通过/新失败差距项）**清零**；
  _scratch_bill.subl 的 `xs_head` 未定义（样本侧）保留，另 2 项 E-TYPE-MISMATCH 系 U 无绑定源
  （init 即未定义名）的**正确不放宽**行为。
- V1 边界：lambda 为**唯一**线索时（形参位无期望来源）不做全局推导（无高层 ML 推断），体按 synthetic
  通过核对；反推依赖期望位（显式实参/其它实参解出）。

