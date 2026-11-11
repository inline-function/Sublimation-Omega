# ADR-33：双向类型检查燃料与不可停机拒绝（R-31）

**任务**：t51（semantics） · **日期**：2026-11-07 · **前置**：ADR-31（R-27/R-28 别名↔联合双向合一）、ADR-28（R-16 联合展开）、P-9（归纳类型别名合法）

## 一、用户裁决（原文）

> 在进行双向类型检查的时候，需要为函数增加燃料，使得函数总在有限次内停机，当函数燃料耗尽却仍然没有完成检查时，拒绝这个检查，并且给出错误诊断：「编译器进行检查时无法停机」

背景：P-9 允许归纳类型别名（`type Peano = Zero | Succ[Peano]`），R-28 的别名↔联合双向合一一旦沿互归纳别名展开（A→Succ[A]→B→Succ[B]→A…）**不终止**。BEFORE 探针（03:39Z）实测：`type A = Zero | Succ[A]` 与 `type B = Zero | Succ[B]` 经 `g[T](t: T, u: T)` 求理论参数 → **java.lang.StackOverflowError（编译崩溃，无诊断）**。这是 P-10（子类型偏序）动工前的地基缺口。

## 二、设计（R-31）

### 2.1 计量单位（判定步，每扣 1）

| 计量点 | 位置 |
|---|---|
| `adaptOk` 入口 | 每次调用扣 1（适应判定的基本步） |
| `unifyInto` 入口 | 每次调用扣 1（合一判定的基本步） |
| 联合展开 | adaptOk 的 union→union / 期望联合位分支（t41/R-16）+ unifyInto 的 R-28 双向展开位 |
| 别名展开 | adaptOk 的 R-28 期望侧具名联合别名分支 |
| 结构成员逐个核对 | struct 字段 / 函数参数位经 `args.zip` 的逐项子调用（每个子 adaptOk/unifyInto 各扣 1，天然计入） |
| 约束求解 | V1 为「登记不校验」（FunDecl 注释），不适用；P-10 步骤 4 落地后纳入 |

### 2.2 预算池（禁止逐层重置）

- `TypeInfer` 新增燃料池状态（`fuel`/`fuelDepth`/`origin`/首对类型快照）+ `checkScope(pos) { block }`：**每个顶层检查请求（表达式/声明级）开一个池**，请求内所有嵌套判定共享同一池；
- 开池位点：`callExpr`（表达式级，调用签名核对 + 实参推导共享一池）、`varAnnotated`（两处 VarStmt/AssignStmt 位）、`funBody`（两处函数体核对位）；`callFun` 等 TypeInfer 入口经 checkScope 复用外层池；
- **裸调用兜底**：未被 checkScope 打开的计量调用（adaptOk/unifyInto 直接进入）自动开池并持有到本子树结束——保证任何发散路径都在同一预算内扣步，不会因遗漏包装位而逃逸；
- **不随递归层重置**：池只在请求边界开/关，嵌套层一律复用（否则总消耗 = O(深度×预算)，等于没有燃料）；
- **确定性**：单线程单调递减 Int 计数器；隶属候选顺序取 `aliasDecls` 的 LinkedHashMap 声明序（t48 R-29 已然），不依赖哈希序；`FuelMeteringTest.fuelCountIsDeterministicPerInput` 断言同输入两次编译的耗尽步数一致。

### 2.3 耗尽行为（严格按裁决）

1. **拒绝**：耗尽的 adaptOk/unifyInto 返回 `false`（调用方落既有 E-TYPE-MISMATCH 级联——拒绝的连带结果），**不降级 synthetic、不吞、不 warn**（ST-1/ST-15.3 红线）；
2. **主句逐字**：`E-FUEL-EXHAUSTED` 文案 = **「编译器进行检查时无法停机」**（裁决原文，无增删）；
3. **分行补充信息**（`D-FUEL-EXHAUSTED`，与主句分开的另一条诊断，每池一次）：①触发位置；②检查目标：实际 X / 期望 Y；③已消耗 N 步（上限 M）；④建议（多由归纳类型别名互展开导致，可加显式类型标注切断推导）。实测样例输出见 §五；
4. **池按请求隔离**：一个请求耗尽不影响其它请求（`FuelMeteringTest.exhaustedPoolDoesNotPolluteLaterChecks` 断言：发散请求后的 `var after: Nat = 1 + 2` / `var s: Nat = after * 2` 零错误）。

### 2.4 步预算 + 深度哨（双触发）

发散互追的判步近乎 1:1 消耗 JVM 栈帧；若纯靠步预算（= 实测最大值 × 100）先行耗尽，JVM 栈会**先溢出崩溃**（BEFORE 探针即如此）。故并列机械深度哨：

- `FUEL_DEFAULT = 237_500`（步预算，实测依据见 §四）；
- `FUEL_DEPTH_LIMIT = 800`（请求内递归深度上限；深度哨触发时同样落 E-FUEL-EXHAUSTED，补充信息 ③ 附「递归深度 D 层（安全上限 800）」）；
- 深度哨是**机械安全网**（保证「拒绝」先于「崩溃」），合法样本实测深度远低于此（50 层嵌套 struct ≈ 60 层）；两触发口径同一诊断、不与步预算冲突。

## 三、修复前后对照

| 项 | BEFORE（03:39Z 探针） | AFTER（FuelMeteringTest 实测） |
|---|---|---|
| 发散样本（A/B 互归纳 + 双参泛型） | `java.lang.StackOverflowError`（崩溃，诊断袋只有崩溃栈，无错误码） | 单一 `E-FUEL-EXHAUSTED:编译器进行检查时无法停机` + `D-FUEL-EXHAUSTED` 补充（①位置 1:8②实际/期望③已消耗 4800 步（上限 237500）；递归深度 801 层（安全上限 800）④建议），毫秒级返回 |
| 同编译后续检查 | 崩溃后无法继续 | 零错误（池隔离） |
| 正常程序（数值塔/联合/变长/调用） | — | 0 错误、0 耗尽 |
| 50 层嵌套 struct（深但有限） | — | 0 错误、0 耗尽 |

## 四、燃料分布实测（采样 05:47Z = 13:47 本地）

- 环境：`SUBL_FUEL_STATS=1` env 门控（仅统计、不改变行为），全量 `:frontend:test --rerun-tasks` 单 JVM 覆盖全量测试套件脚本 + E2ERegressionTest 的 68 样本；另以探针逐文件编译 68 样本得到逐样本口径。
- **分布**：2528 个顶层检查请求；**最大 = 4800 步（4 条，均标记 [耗尽]=本任务反例用例故意发散的请求）**；**合法请求最大 = 72 步**；P99 = 46；中位数 = 1。
- **合法消耗 top-3**：①72 步 — e2e `examples/日常-数列与区间.subl:39:12`；②59 步 — `examples/标准库集合.subl:77:15`；③58 步 — `examples/日常-账单统计.subl:72:30`（其次 58 步 `examples/_scratch_bill.subl:31:45`）。e2e 逐文件口径与之一致（日常-数列与区间 72 / 标准库集合 59 / 日常-账单统计 58）。
- **上限依据**：合法最大 72 → ×100 = 7200 为最小要求；取 **FUEL_DEFAULT = 237_500**（≥ 100×）——超配部分为 P-10 子类型链与更大样本留余量；因深度哨先行拦截发散（步预算在合法路径永不被触及），超配不会诱发误报或掩盖问题。
- **异常高消耗如实报告（任务 4.4）**：打点早期在两次中间运行里读到 2285 / 2375 步的孤立高值（origin 同为 line 6 col 58），深度哨落地后消失（合法最大降为 72）。归因：那两次运行含本任务的 BEFORE 探针（ProbeT51Test，无深度哨时触发 StackOverflowError）；异常解开 finally 链时存在二次溢出风险，使**共享 JVM 内的燃料池全局状态被污染**，后续请求的计数漂移出垃圾值。即该高值不是合法样本的近发散信号，而是测量装置自身的污染——如实记录于此，不靠调上限掩盖；探针已删除，最终分布为干净口径。
- 关于「合法样本是否需要假设集」：合法最大 72 << 上限，**当前无需余归纳假设集**（ST-15.5 仍为独立立项，本任务只用燃料兜底，符合边界纪律）。

## 五、FuelMeteringTest（5 用例，全通过）

| 用例 | 断言要点 |
|---|---|
| `deepButFiftyNestedStructsDoNotExhaust` | 50 层嵌套 struct 链 + 49 层嵌套构造表达式：0 错误、0 耗尽 |
| `ordinaryProgramsDoNotExhaust` | 数值塔/联合别名/变长/普通调用 4 样本：0 错误、0 耗尽 |
| `divergentMutualInductiveAliasReportsExhausted` | 恰好 1 条 E-FUEL-EXHAUSTED；主句**逐字** `编译器进行检查时无法停机`；D-FUEL-EXHAUSTED 含 ①②③④ 全部要素；耗尽后仅允许级联 E-TYPE-MISMATCH；不崩溃不超时 |
| `exhaustedPoolDoesNotPolluteLaterChecks` | 发散请求之后的第 9~10 行两条检查零错误（池按请求隔离，非全局共享） |
| `fuelCountIsDeterministicPerInput` | 同输入两次独立编译的「已消耗 N」完全一致（确定性计数） |

发散反例脚本（任务参考构造的可复现版本，确认在当前语义下必发散——单纯 `fun f(x: A): B = x` 的返值核对在 adaptOk 名字级快速失败终止，须经双参泛型 `g[T](t: T, u: T)` 的理论参数合一才进入 unifyInto 互追循环）：

```subl
struct Zero()
struct Succ[T](pred: T)
type A = Zero | Succ[A]
type B = Zero | Succ[B]
fun g[T](t: T, u: T): T = t
var a: A = Zero
var b: B = Zero
var r = g(a, b)      // line 8: unifyInto(A,B) → R-28 双向展开互追 → 深度哨拦截
```

## 六、改动位点

| 文件 | 改动 |
|---|---|
| `semantic/TypeInfer.kt` | 燃料池（openPool/fuelEnter/fuelExit/fuelConsume/fuelExhausted）、checkScope 开池 API、步预算 + 深度哨常量、E-FUEL-EXHAUSTED/D-FUEL-EXHAUSTED 诊断、adaptOk/unifyInto 计量包裹与展开计步、FuelBook 全局统计簿（env 门控，JVM 退出落盘 distribution） |
| `semantic/Elaborator.kt` | callExpr / varAnnotated×2 / funBody×2 位 checkScope 包装（请求级池与诊断位置） |
| `semantic/FuelMeteringTest.kt`（新） | 5 用例（任务 5 正反例 + 隔离 + 确定性） |

## 七、验证（采样 05:39–05:50Z）

- `:frontend:test --rerun-tasks` = **515 用例 / 0 失败**：507 基线 + 3（t53 ParserTest 新增，非本任务）+ 5（FuelMeteringTest，逐条见 §五）；新增用例仅此 5 条，无改写既有断言。
- e2e = **65/68**（与基线持平；残余 3 红 = list.subl 跨模块符号 ×3（模块系统 V1 边界）+ 手动证明 + 模块系统/主，诊断集前后一致）。结构违规 0（E2ERegressionTest 内嵌校验通过）。
- 双快照：G1 = 05:47Z 全量 --rerun-tasks 内嵌 compileKotlin；G2 = 05:50Z 独立 `compileKotlin + compileTestKotlin` BUILD SUCCESSFUL，**e=0/w=0**（ADR-24 §五双条件，合并流 .kt:位置行扫描 0 命中）。
- 全程经 `tools/build-slot.ps1`（acquire → gradle → release）独占槽位；无双 daemon。

## 八、V1 边界留档

- 不做余归纳假设集（ST-15.5 独立项）；合法样本最大 72 步，无需它兜底——若 P-10 子类型链带来更深的合法结构比较（指数型展开），届时按任务 4.4 口径重新实测分布。
- 深度哨 800 是机械安全阈值（合法深度 ~60），非语义判定；被拦截的请求按耗尽拒绝处理。
- 步预算超配（237_500 vs 最小 7200）为 P-10 与样本库增长留余量；发散路径由深度哨保证短停机，不存在「调大上限掩盖」路径。
- `FuelBook` 统计簿 env 门控（`SUBL_FUEL_STATS`/`SUBL_FUEL_FILE`），默认不落盘、不改变行为。