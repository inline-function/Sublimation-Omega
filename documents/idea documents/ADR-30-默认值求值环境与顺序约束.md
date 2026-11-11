# ADR-30：默认值求值环境与顺序约束——声明处作用域 + E-DEFAULT-ORDER + 尾部连续省略

**任务**：t45（semantics） · **日期**：2026-11-06 · **前置**：ADR-15（内建容器登记与默认值 arity 规则，决策 69）、ADR-19（结构体构造子族与文法收紧）、ADR-25（P8D 语义层）

## 一、用户裁决（2026-11-06 14:53）

> 函数的参数可以有默认值，fun f(n : Nat = 0)，但是**不得省略参数的类型标注**，结构体的属性也可以用默认值，但是也不得省略类型标注，**默认值可以引用先于它声明的参数**，fun f(a: Nat, b: Nat = a)

## 二、问题陈述（修复前实测，17:46Z 探针）

| # | 源码 | 修复前 | 期望 |
|---|---|---|---|
| A | 命名填补默认值 + 调用者块局部同名 | **静默捕获**块局部（无错） | 默认值按声明处环境求值，不捕获调用者局部 |
| B | fun f(a: Nat, b: Nat = a) + 命名调用 f(a = 1) | **E-UNBOUND-NAME: 未定义的名称 a**（调用点求值） | 默认值引用形参 a，合法 |
| C | fun f(a: Nat = b, b: Nat) | E-UNBOUND-NAME: b（声明位误打） | **E-DEFAULT-ORDER**：默认值只能引用先于它声明的形参 |
| D | struct P(x: Nat = y, y: Nat) | E-UNBOUND-NAME: y | **E-DEFAULT-ORDER**（字段同口径） |
| E | fun f(a: Nat, b: Nat = 0, c: Nat) + 位置调用 f(1) | **静默接受**（c 缺失未检） | E-TYPE-MISMATCH 缺实参 c（位置实参只能从尾部连续省略） |

根因：Elaborator.reorderNamedArgs（原 1557-1562）把声明里的默认值 AST 直接塞进调用点实参位，随后在**调用者帧**里 elaborate——默认值的名字消解随调用点漂移。位置调用则完全不经填补通道（callFun 的 firstDefault 规则只挡无默认值的缺位，中间默认 + 尾部必填被静默放行）。

## 三、计划改法（R-22~R-25）

- **R-22 默认值顺序约束（E-DEFAULT-ORDER）**：声明位逐参数/字段扫描默认值 AST 的自由名字（不穿嵌套作用域）；命中**后序**名 → E-DEFAULT-ORDER，跳过该默认值的声明位 elaborate（不再重复报 E-UNBOUND-NAME）。
- **R-23 默认值求值环境=声明处重建**：调用点填补位在重建的函数帧（被调函数 theory + 接收者 self + **前序**形参）内 expr() 求值；调用者局部变量不可见；后序引用的填补跳过。lambda 两遍通道同收 preHirs。
- **R-24 位置实参尾部连续省略**：callFun 的 required 规则改为「缺省位（argTypes.size 起）必须全部有默认值」，首个无默认值的缺位落既有 E-TYPE-MISMATCH 缺实参口径；命名实参可跨位省略。
- **R-25 类型标注必填**：形参/字段文法本就必填「名: 类型 [= 默认值]」（AST Param.type 非空）——补测固定 parse 期诊断。

**V1 边界**：lambda 形态的默认值在两遍通道按调用点环境 elaborates——声明位类型核对已守住，环境口径留档（本 ADR 五、节）。

## 四、验收清单

- [x] 探针捕获修复前实测（上表 二、节）
- [x] R-22：E-DEFAULT-ORDER 落 DiagBag（含 message 断言）
- [x] R-23：A 案例 = 声明处求值（不捕获局部）；B 案例 = OK；命名填补位 HIR 默认值绑定前序形参符号
- [x] R-24：E 案例落 E-TYPE-MISMATCH；尾部连续默认/命名跨位省略保持
- [x] R-25：fun f(n = 0) / struct P(x = 0) 报错固定
- [x] :frontend:test 0 失败（基线 464/0 → 479/0）；e2e >=46/68 结构违规 0；compileKotlin/compileTestKotlin 双快照 e/w=0
- [ ] log（2026-11-06.md）append-only 段 + 测试代码落盘
## 五、落盘决算（19:26Z 收口）

**实现**（Elaborator.kt / TypeInfer.kt / SymbolTable.kt，均不改文法）:
- `param(p, kind, laterNames)`：声明位逐参数/字段扫描默认值 AST 自由名（`freeNameRefs`，不穿嵌套作用域），命中后序名 → `E-DEFAULT-ORDER` 并跳过声明位 elaborate。
- `defaultExprAtParam(funName, paramIdx, callPos)`：调用点填补位在 `symbols.isolatedFrame` 内重建——保留脚本顶层帧，切掉调用者局部帧；依次推 theory（`enterTypeParams`）+ self（有接收者时）+ 前序形参，`expr()` 在此帧求值。`ReorderResult(args, fills)` 登记 argIdx→paramIdx；lambda 两遍通道同收 `preHirs`。
- `callFun`：required 口径改为尾部连续（缺省位必须全部有默认值）；E-ARITY 保留给「实参过多」。
- 判据实测（修复前 → 修复后，探针逐 case 跑通）:

| case | 修复前 | 修复后 |
|---|---|---|
| `var a: Nat = 5; fun f(x: Nat, y: Nat = a); fun g(): Nat { var a = "s"; f(x = 1) }` | E-TYPE-MISMATCH:实参 2 Str 与形参 Nat 不符（**默认值捕获调用者块局部 a: Str**） | **OK**——默认值按声明处绑顶层 a: Nat |
| `fun f(x: Nat, y: Nat = a); fun g(): Nat { var a = "s"; f(x = 1) }` | E-UNBOUND-NAME + E-TYPE-MISMATCH（声明误报 + 捕获） | **E-UNBOUND-NAME**（仅声明位一次，DiagBag 去重） |
| `fun f(a: Nat, b: Nat = a); f(a = 1)` | E-UNBOUND-NAME: 未定义的名称 a | **OK**；HIR 填补位 = HirNameRef(a) 绑 **SymbolKind.PARAM** |
| `fun f(a: Nat = b, b: Nat)` | E-UNBOUND-NAME: b | **E-DEFAULT-ORDER**（文案逐字「默认值只能引用先于它声明的参数；b 声明在 a 之后」） |
| `struct P(x: Nat = y, y: Nat)` | E-UNBOUND-NAME: y | **E-DEFAULT-ORDER**（同判据同文案） |
| `struct P(x: Nat, y: Nat = a)` + 块局部 a 构造 | （未探针） | **E-UNBOUND-NAME**——构造点局部不可见 |
| `fun Nat.f(x: Nat = self); 5.f()` | OK（声明位已对） | OK（不变） |
| `fun f(a: Nat, b: Nat = 0, c: Nat); f(1)` | **静默接受**（c 未检） | **E-TYPE-MISMATCH:函数 f 缺实参：c** |
| `f(1)`，`f(1, c = 2)` | OK | OK（不变：尾部连续/命名跨位省略 + 填补 0） |
| `fun f(n = 0)` / `struct P(x = 0)` | E-SYNTAX:期望形参类型标注（: 类型） | 不变（parse 期，补测固定） |

**验收**:
- `:frontend:test --rerun-tasks` 最终 = **479 用例 / 0 失败**（基线 464 + 本任务新增 `DefaultsSemanticsTest` **15** 用例；临时探针已删）。用例增量逐条：defaultSeesPrecedingParamAndBindsParamSymbol / callerLocalNotCapturedByDefault / defaultBindsDeclarationSiteGlobalNotCallerLocal / forwardParamInDefaultReportsE_DEFAULT_ORDER / selfVisibleInExtDefault / structFieldDefaultSeesPrecedingField / structFieldForwardReportsE_DEFAULT_ORDER / structFieldDefaultNotCaptureCtorSiteLocal / funParamTypeAnnotationRequired / structFieldTypeAnnotationRequired / positionalTailContinuousOmissionOk / midDefaultWithTailRequiredPositionalReports / namedArgSkipsMiddleAndFillsDefault / missingNonDefaultNamedArgReports / noDefaultMissingAllReports。
- e2e = **46/68**（= 基线 46，19 enum 与空安全 None() 属 t46 领地）。结构违规 0。
- compileKotlin + compileTestKotlin（--rerun-tasks）双快照：**e=0 / w=0**。

**V1 边界（留档）**:
- 位置调用（`f(1)`）的默认填补不落 HIR（既有 ADR-15 供给态契约，仅类型核对走 R-24）；命名调用才内联填补 HIR。
- lambda 形态默认值（如 `fun f(g: () => Nat = { 1 })`）在两遍通道按 lambdaExpect 期望 elaborates（声明位类型核对已守住）。`freeNameRefs` 扫描止步于嵌套作用域（lambda/块/if/when/量词/Task/Flow/匿名函数），其内名字归该层自解析——嵌套层引用后序名不落 E-DEFAULT-ORDER（该处依赖外层 elaboration 的自身作用域判据）。
- 扩展函数的命名调用通道不存在（E-NAMED-ARG），故 self 仅在声明位默认值中可见；defaultExprAtParam 仍冗余重建 self 帧以备直调路径。