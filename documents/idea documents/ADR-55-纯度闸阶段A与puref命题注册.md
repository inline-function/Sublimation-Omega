# ADR-55：纯度闸（t96 阶段 A 报告态）——pure<f> 注册 + 调用闸 + 三路放行 + 借用形参

## 状态
已落地（阶段 B 硬拒，2026-11-10 队长批）：闸以 **error 级硬拒**上线（gateDiag 单点 severity）。
阶段 A（warn 报告态）样本冲击度量 = **0/68**（未越阈）⇒ 队长批阶段 B，翻 error 后复验口径全同：
探针 10/10、全量 843/0、e2e 65/68、out/t96B 68 份 vs out/t92 SAME 68/68 零差异（闸 fire 0 处）。
诊断文案的「阶段 A 报告态」字样已按队长要求去除。

## 规格
R2 落地计划 §一 + wiki 04-效应-副作用与@impure（`documents/旧工程参考-效应与@impure.md`）。
前置依赖 t94（lambda 纯度推导）已落地（ADR-54）：闸的纯作用域判定（impure 闭包吸收）直接消费节点位 purity。

## 四步升级（qa t93 方案）
1. **Γ 注册**：未标 @impure 的具名函数自动注册 `pure<f>`（注册名集——HIR 遍历时取 HirFunDecl 注解，
   @root 名按内建纯度表判，wiki 同款保守口径「用户注解不参与」）。
2. **调用闸**：纯作用域内调非纯被调者且 Γ 无 `pure<f>` ⇒ `E-IMPURE-CALL`（阶段 A warn 级）。
3. **上文转嫁**：上文角组 `<pure<f>>`（preps，归一为 HirPAtom(head=pure, args=[HirTName(f)])——
   e2e 样本 `examples/特性巡礼.subl` 的 `fun pureCaller()<pure<side>>` 即此形态）与 `axiom pure<f>
   （axiomFacts 事实池）供给 Γ ⇒ 放行。
4. **借用形参**：`fun k(f: @impure () => Null)` 的形参即显式效应能力（类型位 @impure 经 type() 
   保留进 HirParam.type 的 purity），纯函数可调——纯侧不闸。**函数值闸最小形态**同时落地：
   callee 类型位为 impure 的函数值调用按非纯计（sigFunType 经 t94 E:1535 已带 @impure 注解位）。

## 三路放行（wiki）
- **调用方非纯**：作用域链任一非纯（@impure 函数体 / t94 推导的 impure 闭包 / unchecked 降闸）。
- **unchecked**：HirUnchecked 内语句整体降闸（单语句一次性豁免;闸后 unchecked 语义才有真正对象，
  R2「先闸后 unchecked」顺序已兑现——t98+ 停机闸同此通道）。
- **Γ⊢pure<f>**：上文角组 / axiom / 自动注册任一命中即放行。

## 实现形态：elaboration 后回看整树
闸跑在 `elaborate()` 尾（`Elaborator.runPurityGate`），不是 callExpr 位——理由：lambda 纯度由 t94 
**推导**得出，只有 body elaboration 完成后才知道（底向）。回看 walker 以成员级互递归实现（Kotlin 局部
函数不支持前向引用——t94 测试同款坑），维护作用域栈：fun（注解口径纯度 + preps 事实 + 借用形参名集）/
lambda（节点位 purity）/ unchecked（降闸）/ 脚本顶层（隐式非纯入口，wiki topPure = !scriptMode——
顶层语句存在即判脚本模式）。被调者分流按 HirCall.symbol.kind：BUILTIN→内建不纯名表；FUN/THEOREM→
声明口径；其余→局部 fun 词法查栈→函数值类型位。

## 阶段开关（单点）
```kotlin
private fun gateDiag(pos: SourceRange, message: String) = diag.error("E-IMPURE-CALL", pos, message)
```
severity 为闸开关：阶段 A=warn 报告态（已验毕收档）;阶段 B=error 硬拒（上线中，队长 2026-11-10 批）。
sortie 文案含三路放行的可编译提示（ADR-48 §八）。

## 保守局限（已知，登记不修）
- 局部变量名遮蔽内建不纯名（如 `var print = ...`）时，符号 kind 为 LOCAL 走函数值通道（类型位判）——
  仍按名查内建表的分支仅在 BUILTIN kind 命中，故**不会**误闸（与旧 wiki 同口径）。
- `axiom pure<f>` 无论词法位置都入全局事实池（axiomFacts 收集全部 axiom 命题）——偏宽松（可能放过
  本应闸的调用）;旧 wiki 同款「全局事实池」口径，phase A 测量无样本命中故无影响。
- 停机闸 E-NON-STRUCTURAL-REC、E-IMPURE-REQUIRED（@mut 形参须标 @impure）**不在 t96 范围**，留 t98。

## 验证（实测）
- 探针矩阵 10/10（`probe-mut/cases-t96/` p1-p10，先红后绿）:
  fire×3（声明 @impure / 内建 print / impure 闭包直接调用）、放行×7（上文 / axiom / unchecked /
  @impure 调用方 / 借用形参 / 闭包吸收 / 纯闭包）。**阶段 B 本地实测**：翻 error 后同样 10/10，3 处
  fire exit=1、7 处放行 exit=0。
- 全量 **843/0**（833 + T96PurityGateTest 10 例）。e2e **65/68** 三冻结红逐字不变。
- **68 样本 JSON vs out/t92: SAME 68/68 零差异**（阶段 A HIR/JSON 零改动）;**闸位点数 0/68**——
  唯一形态候选 特性巡礼 pureCaller→side 被 `<pure<side>>` 上文放行。

## 后续
队长确认阶段 A 结果（0 冲击）⇒ 翻 gateDiag 为 error（阶段 B）。t98 停机闸同 unchecked 通道。

## §unchecked 不对称口径（I-4 定案，2026-11-10）

**unchecked 豁免检查，但不豁免效应存在。**

- t94 的 lambda 纯度推导（containsImpureStmtCall）**递归进 HirUnchecked**:
  `{ => unchecked print("x") }` 的闭包判 **IMPURE**——unchecked 只是「放弃对这段代码的闸检查」，
  效应本身仍在，闭包携带的效应位如实落 HIR（下游 codegen/优化不得按纯闭包处理）。
- t96/t97 闸把 unchecked 当**放行通道**（作用域链/uncheckedDepth）——检查豁免。
两套行为不矛盾：豁免的是「闸的核对义务」，不是「效应的存在」。锁定测试:
T94LambdaPurityTest.`unchecked体内仍计impure()` + T96/T97 探针与单测的 unchecked 放行位。
(出自我（semantics）与队长结构审查 I-4 的对齐：此前 ADR-54/55 未落此决定，补档。）