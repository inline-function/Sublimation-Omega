# ADR-34：P-9 收尾修复——局部函数签名核对与自引用联合判重（R-32 / R-33）

**任务**：t52（semantics） · **日期**：2026-11-07 · **前置**：P-9（裁决 7 / 裁决 8）、t51（ADR-33 双向检查燃料）

## 一、来源与裁决

qa 在 P-9 终局验收（t50）发现、队长独立复现确认的两处「裁决未真正落地」，plus t51 验收时队长发现的一处级联误诊（连同域，一并修）。

| 编号 | 裁决要点 | BEFORE 实测（本任务探针） |
|---|---|---|
| R6（裁决 8 末条）① | 局部函数调用点须做实参类型检查 | `fun g(): Str { fun f(a: Str) = a; return f(0) }` **静默通过**（全局对照 `f(0)` 落 E-TYPE-MISMATCH）；遮蔽场景同静默 |
| R6 ② | 局部函数签名（形参/默认值/变长/命名）与全局同等 | 调用点拿不到签名 → syntheticT 兜底 |
| R7（裁决 7） | `type A = A \| B`（有基态）视为归纳通过；`A = A \| A` 真重复仍报 | 误报 `E-DUP-UNION-MEMBER: 联合类型 A 的成员 B 重复`——报错成员名错（实际重复者是 A；B 根本不重复） |
| t51 验收补充 | 燃料耗尽的级联误诊 | 发散样本同文件出现 `E-TYPE-MISMATCH: 函数 f 声明返回 …，两边类型不一致`——理由是假的（没算完而非不一致），把用户往错误方向引 |

## 二、R-32：局部函数调用点签名核对（复用全局通道，一份真相）

**根因**：`Elaborator.FunStmt` 自由函数分支只做 `declareLocal(name, SymbolKind.LOCAL)`——符号登记了，但** AST 声明未随帧可查**；调用点 `callExpr` 的签名分支按 `symbol?.kind` 分派，LOCAL 落到 `else -> infer.syntheticT("调用" + name)` 兜底，不做任何核对。

**修法**（不另开特判通道，全部走全局那条路）：

1. `SymbolTable` 新增**帧级局部函数槽**（同 t39 局部扩展函数的 `LocalExtFun` 同栖口径）：`Scope.localFuns` + `declareLocalFun(name, pos, paramNames, destructParams, decl)`（符号仍登记为 LOCAL，遮蔽提示不变，条目补 paramNames/destructParams）+ `lookupLocalFunDecl` / `lookupLocalFunEntry`（帧链内→外）。
2. `Elaborator.FunStmt` 自由分支改用 `declareLocalFun` 把声明随帧登记（帧退即消失，遮蔽语义与符号一致）。
3. `callExpr` 签名分支共用新提取的 `sigCheckedCall(d, name, argTypes, pos, explicitTps)`——全局 FUN/THEOREM 与 LOCAL **同走一条**（`sigOf` 形参/返回 + 默认值位旗标 + R-30 变长旗标 + callFun 核对/arity）；命名实参重排入口（`funEntry ?: lookupLocalFunEntry`）、lambda 两遍通道（`calleeDeclLambda`）、默认值求值处（`defaultExprAtParam`）一并接入。
4. **遮蔽顺序**：被调声明查找一律**局部优先于全局同名**（初版误把全局分表放前，遮蔽场景 D1C 查到全局签名而没查局部——实测发现并纠正）。

**实测对照（修复前 → 修复后；探针→正式 LocalFunUnionFixTest 14 例）**：

| 用例 | BEFORE | AFTER |
|---|---|---|
| 局部错参 `f(0)`（形参 Str） | 通过（0 错） | E-TYPE-MISMATCH「实参 1 Nat 与形参 Str 不符」 |
| 局部正确参 | 0 错 | 0 错 |
| 遮蔽错参（裁决 8 末条：局部 f(a: Str) 遮蔽全局 f(a: Nat)，f(0)） | 通过（0 错） | E-TYPE-MISMATCH（同时连带 E-RETURN-TYPE：返值类型实为 Str 不匹配 Nat，同一根因的返值通道） |
| 全局对照 `f(0)` | E-TYPE-MISMATCH | E-TYPE-MISMATCH（不变） |
| 局部默认值 `f(1)`（b = 1 位有默认） | 0 错 | 0 错（**默认值核对通道连通**） |
| 局部 `@list` 变长 `h(2, 3)` | 0 错 | 0 错（**变长通道连通**） |
| 局部缺实参 `f(1)`（两形参无默认） | 通过（0 错） | E-TYPE-MISMATCH（缺实参） |
| 局部命名实参 `f(b = 1, a = 2)` | 0 错 | 0 错（重排按局部形参表） |

## 三、R-33：自引用联合判重（按 AST 原始成员，自引用不计入）

**根因**：判重在 HIR 转换后的 `target` 成员上做；自引用 `A` 经 `type()` 解析时**被别名目标展开**（aliasDecls 已含 A 自身），外层联合被展平出重复的 `B`，于是报「成员 B 重复」（且条目渲染为合成键）。判据被解析展开污染。

**修法**：判重改按**原始 AST 成员**（`d.target as UnionType` 的 variants）：

- 成员名 == 被定义别名（裸自引用）→ **不计入成员集合**（交给既有归纳判定：有基态 → D-INDUCTIVE-TYPE 补充通过；全自引无基态 → E-CYCLE）；同一自引用写第二遍仍是真重复，报**被定义名本身**。
- 其余成员按裸名键/结构键（旧 memberKey 口径，结构成员以 `infer.render(type(v0))` 取键保持键等价）判重。

**实测对照**：

| 用例 | BEFORE | AFTER |
|---|---|---|
| `type A = A \| B`（struct B 存在） | E-DUP-UNION-MEMBER「成员 B 重复」（误诊） | **0 错**（D-INDUCTIVE-TYPE 补充：归纳通过，符合裁决 7） |
| `type A = B \| A` | 同样误报 | 0 错（顺序无关） |
| `type A = A \| A`（真重复） | E-DUP「成员 (推导:递归别名)」+ E-CYCLE | E-DUP-UNION-MEMBER「成员 A 重复声明」**（名报对）**+ E-CYCLE（全自引无基态，既有 R-17 逻辑不变） |
| `type A = B \| B`（真重复成员） | E-DUP「成员 B」 | E-DUP「成员 B」（不变，名仍对） |
| `type Peano = Zero \| Succ[Peano]` | 0 错 | 0 错（不变） |

## 四、t51 验收补充：燃料耗尽的级联 E-TYPE-MISMATCH 抑制

队长独立验证 t51 时发现：发散样本同文件除 `E-FUEL-EXHAUSTED`（逐字「编译器进行检查时无法停机」，毫秒级返回，退出码 1 拒绝 ✓）外，还落 `E-TYPE-MISMATCH: 函数 f 声明返回 …，两边类型不一致`——**理由为假**（不是不一致，是没算完）。

**修法**（`TypeInfer`）：新增 `fuelExhaustedNow() = fuelReported && fuelDepth > 0`，**全部 15 处** `diag.error("E-TYPE-MISMATCH", …)` 包裹 `if (!fuelExhaustedNow())`。

- 精确边界：**仅同一顶层检查请求（fuelDepth > 0）且该请求已落耗尽**时的子树级联被抑制——一个根因只报一条；
- 独立性：其它请求的池各自独立（openPool 重置 fuelReported），同文件**独立**的类型错误正常报（实测 C2：发散请求 + `var wrong: Nat = a` → E-FUEL-EXHAUSTED 与 E-TYPE-MISMATCH 并存，前者抑制不影响后者）；
- 不是全局抑制：请求关闭后 fuelDepth == 0，条件自然失效。

实测：C1 发发散样本 请求外无任何错误 → **恰好 1 条 E-FUEL-EXHAUSTED，文案逐字相等**（LocalFunUnionFixTest.fuelCascadeMismatchSuppressed）。

## 五、改动位点

| 文件 | 改动 |
|---|---|
| `semantic/SymbolTable.kt` | `LocalFun` 帧槽类、`Scope.localFuns`、`declareLocalFun`、`lookupLocalFunDecl`、`lookupLocalFunEntry` |
| `semantic/Elaborator.kt` | FunStmt 自由分支登记 `declareLocalFun`；`callExpr` 签名分支统一 `sigCheckedCall`（FUN/THEOREM/LOCAL 同路）+ 被调声明局部优先；命名实参/lambda 两遍/默认值通道接入局部函数；联合判重改 AST 成员 + 自引用不计入 |
| `semantic/TypeInfer.kt` | `fuelExhaustedNow()` + 15 处 E-TYPE-MISMATCH 的耗尽级联抑制 |
| `semantic/LocalFunUnionFixTest.kt`（新） | 14 用例：缺陷 1 八例 + 缺陷 2 五例 + 级联两例 |

## 六、验证（采样 07:38–07:39Z）

- `:frontend:test --rerun-tasks` = **529 用例 / 0 失败**：515（t51 后基线）+ 14（本任务 LocalFunUnionFixTest，逐条见 §二/三/四）；无改写既有断言。
- e2e = **65/68**（基线持平；残余 3 红与 t50 口径一致：list 跨模块符号 ×3 + 手动证明 + 模块系统/主，均 V1 已登记边界）。结构违规 0（E2ERegressionTest 内嵌校验通过）。
- 双快照：G1 = 07:38:59Z 全量 --rerun-tasks 内嵌编译；G2 = 07:39:32Z 独立 `compileKotlin + compileTestKotlin` BUILD SUCCESSFUL，**e=0/w=0**。
- 全程 `tools/build-slot.ps1 run -Note t52`（自动释放）；无双 daemon、无并发构建碰撞。

## 七、边界留档

- 局部函数与全局函数的签名核对通道**已完全合并**；若未来新增签名维度（如具名型参约束 P-10），只改 `sigCheckedCall` 一处。
- 自引用联合判定仍**不做**余归纳假设集（ST-15.5 独立项）；R-33 只修判重口径，归纳判定逻辑（R-17）不变。
- 级联抑制只针对**燃料耗尽直接派生**的诊断；由其它根因（合法错误、缺实参等）派生的 E-TYPE-MISMATCH 不受影响。