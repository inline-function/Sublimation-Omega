# ADR-39：子类型偏序（subtype）成为全前端唯一相容性判据

> 状态：已落地（t62） · 日期：2026-11-08 · 前置：ADR-33（t51 燃料）、ADR-36（P-10 步骤 1）、ADR-38（t61 视图降级）
> 来源：P-10 步骤 2（specification/S-1 管线；ST-1~ST-15 承诺）。范围：TypeInfer（subtype 主体/adaptOk 退化为包装/删 compatibleView/造格位 demoteView）、
> Elaborator（traitSubtype 与 structFieldsOf 注入、checkConstraints 界核对并入偏序、theoryParam 声明位不变判据）、测试（SubtypeOrderTest 正负例对）。
> 旧实现（F:\KotlinProject\sublimation）：仅字典点查，未参照。

## 一、决策（一句话）

`subtype(a, b)` 是全前端**唯一**的相容性判据；`adaptOk` 退化为「合成位 R-34 闸 + subtype」的纯包装，
不再自带任何特例放行；`compatibleView` 删除（视图族全部并入偏序，ST-11.1）。

偏序子句序（subtypeBody，先命中先决定——**Kotlin when 无落穿**：凡「形态守卫 + 判定体可为否」的子句，判定值即终值）：

1. **假设集命中**（余归纳，coinduction）：`(got, want) ∈ assume` ⇒ true——递归类型（函数型/互追结构体/联合）的正解；
2. **自反/结构等价** typeEq；3. **ST-6 Nothing 真底**（got 侧单向）；4. **ST-11 Any 真顶**（want 侧）；
5. **ST-11.1 @mut 视图族**：`@mut T <: @mut U ⟺ T = U`（typeEq 内层）／`@mut T <: T`（got-Mut 降级递归）／`T <: @mut T = false`（want-Mut 直接拒——造格不是子类型）；
6. **ST-8 联合**：got 每成员注入 want 某成员（单向拓宽；联合→成员须 is 收窄，偏序不给）；got 为具名联合别名时经 aliasExpander 先展开（t44 未展开形态按同一实体）；
7. **ST-3/ST-9 满足即子类型**：`traitSubtype(got, want.name)`（注入件；非特质名先拒——空规范集空洞满足的健全性补丁）+ 特质父链闭包（TS-8）；
8. **ST-10 数值塔** Nat <: Int <: Rat 单向（rank；Real 不在 rank ⇒ 不入塔）；
9. **同名同参数泛型位**：逐参协变（ST-13.1：List[A] <: List[B] ⟺ A <: B；@mut 不变性由 5 承担）；
10. **2.9 结构体结构满足**：`structSub`（want 字段齐备且逐字段 got <: want；仅普通结构体，泛型结构体走名义同名位；空 want 字段集不成结构顶——健全守卫）；
11. **t47 期望侧具名联合别名展开重试**（R-28）；12. **ST-7 函数型**（参数逆变 want.param <: got.param + 返回协变，assume += (got, want)）；13. 元组逐项；else false。

**【when 无落穿的关键修正】**初版把 2.9 写成形态守卫子句 `got Named && want Named && structFieldsOf != null -> structSub(...)`：
子句命中即**返回** structSub 的 false，别名展开（11）与函数型（12）永不被尝试——588/0 基线 39 条转红（别名嵌套/联合歧义分派/lambda 实参位全坏）。
修正：**判定入守卫**（`... && structSub(got, want, assume) -> true`），失败自然落穿后续子句。设计规则入档：**「形态守卫 + 判定体」子句必须满足「体为 false 时确实没有后续子句能救」**，否则判定必须写成守卫谓词。

## 二、递归终止：假设集（余归纳）+ t51 燃料（同池）

- 函数型与元组子句把 `(got, want)` 加入 assume 后再证成员；结构满足把 `(gotStruct, wantStruct)` 加入；联合成员注入按原样透传。
示范用途：`type Stream = (() => Stream) | Nil` 类互追在有限步内闭合（假设命中立返 true），不再仅靠燃料兜底；
- 燃料**不开新池**：`sub()` 与旧 `adaptOk` 同结构（owner 持池、`fuelEnter/fuelExit`），联合展开/别名展开各自计步（`fuelUnionExpand`/`fuelAliasExpand`）；
耗尽 ⇒ **拒绝**（false，ST-15.3；R-38 落点闸的容忍逻辑不变，只在 reportMismatch 层）。深度哨（FUEL_DEPTH_LIMIT）与轮次上限未动。

## 三、单一入口清单（硬要求 ①：旧路径已死）

| 通道 | 判定 | 去向 |
|---|---|---|
| adaptOk（TypeInfer，判官） | 相容性 | **保留为唯一包装**：合成位 R-34 闸 + subtype；无特例 |
| compatibleView（T326） | @mut 互认（双向） | **删除**：正向并入偏序子句 5；反向（造格/借用）移至调用位 demoteView（§四） |
| subtype（新增） | 偏序 | 全前端唯一相容性判据（checkConstraints 亦走此） |
| looseEq（T306） | 名义/结构等价 | **保留**（非相容性判据）：binExpr 名义比较、unifyInto 一致性 — 类型计算通道 |
| unify（T525） | 推导期合流 | **保留**（非相容性判据）：binExpr 结果/when 分支/var 兜底 + EL1000/EL1031 |
| isAnyExpect / isNothingT | 谓词 | **保留为私有 helper**（判定位移入偏序子句 3/4） |
| adaptOkBody（旧） | 特例主干（联合三族/塔/视图/兼容回退） | **删除**——子句整体迁入 subtypeBody |

调用位清单（adaptOk 消费方，全部保留，语义=「合成位闸 + 偏序」）：varAnnotated T645 / assignTypes T655 / defaultType T665 /
callFun 实参 T787 / ctorEnum 实参 T942 / structCtor 字段 T960 / returnStmt T999 / funBody T1027（t61 viewGap 冗余删除——偏序子句 5 自然拒绝）。

## 四、造格 / 借用位（删 compatibleView 反向的配套）

ST-11.1：初始化、赋值、实参位借用核 `typeof(v) <: T`（want 侧降级），即 `demoteView(want)` 后再走偏序：
varAnnotated / assignTypes / defaultType / callFun 形参 / ctorEnum 实参 / structCtor 字段（6 位）。
返回位（funBody/returnStmt）**不造格**：`fun g(a: Nat): @mut Nat = a` 经子句 5（want-Mut ⇒ false）落 E-TYPE-MISMATCH（t61 对照例 C1 由规则迁入偏序）。
**@mut 不变性的可观位声明**：造格位降级剥**外层** @mut，`@mut List[Nat] → @mut List[Any]` 在 var/assign/实参位必被接受（want 降级为 List[Any]，List 协变）；
不变性仅在非造格位可观（返回位：MUT-LIST-INVARIANT 用例）。ST-13.1 的 (σ_imm, σ_mut) 变构性对**属步骤 3**（本 ADR 不实现不变性的跨视图推导）。

## 五、E-BOUND-INVARIANT（硬要求 ④，真实落地）

- **位置**：`theoryParam`（EL2324，声明位）；判定：`约束 != null && occursInAst(T, 正位) && occursInAst(T, 负位)` ⇒ 报 E-BOUND-INVARIANT（L6 文案逐字）。
- **极性口径（V1，直接位）**：形参位=负、返回位=正、字段/别名目标=正、接收者=负；极性位点经 occursInTypeAst 递归（NamedType 实参/FunType 参数与返回/TupleType/MutType 内层）。
**复合极性（负×负=正 等 ST-12）与 ST-13.1 双视图变构性对属步骤 3**——本步骤只判「正负皆有」的不变形态。
- 调用位（6 处 theoryParam 调用）全部带极性位点注入：TraitDecl（成员形参/返回）、StructDecl（字段正位）、TypeAliasDecl（目标正位）、扩展 FunDecl（形参+接收者负/返回正）、AnonFunExpr、FunType 规范位。
- **旧测试翻新（硬要求 G7，码名迁移见台账）**：`combine[T: Monoid](a:T,b:T):T` 形态（T 形参+返回=不变+界）在 t62 前**合法**，t62 后按 ST-13 必须拒绝——
ExtensionSemanticsTest 4 例（satisfiesFullyNoDiag / missingOneSpecReportsWithList / forwardReferenceToExtension / concreteTypeConstraintReportsBoundary）
与 ShadowAndScopingTest 1 例改写为单形参形态（T 仅负位）或断言 E-BOUND-INVARIANT。纯代数件 TypeInferTest.adaptOkTolerances 的 `adaptOk(Nat, @mut Nat)=true` 旧断言翻为 false（ST-11.1）。

## 六、E-TC-BOUNDARY 处置（硬要求 ⑤，ST-14 语义作废 + 台账）

- 旧语义「具体类型而非特质（V1 仅支持类型变量:特质）」**作废**：ST-14 规定 `[T: X]` 界统一为偏序 `T <: X`（X 为特质时即满足判定，单一通道）。
checkConstraints 删具体类型拒绝分支（EL2080-2081），改为 `infer.subtype(concrete, infer.namedT(cn))`：
成功（含特质满足）⇒ 无诊断；失败且特质规范缺失 ⇒ E-TC-NOT-SATISFIED 富清单（satisfiesCheck 仅作诊断供给，不再是判定通道）；失败且非特质感 ⇒ E-TYPE-MISMATCH。
- 保留口径：高阶约束（`F[_]: Show`，arity>0）仍落 E-TC-BOUNDARY（新语义=「不可为界」，文案改写），测试 EX216 保留。
- **方向**：界方向推断（协变上界/逆向下界）按 ST-12/ST-13 属步骤 3，V1 取**上界**口径（T <: X；satisfaction 与既有样本/e2e 对齐：EX226 `use[T: Show](x: T): Str` 调用 use(1) 仍通过——Nat 满足 Show）。

## 七、与 ST-11.1 / ST-13.1 对齐声明（硬要求 ⑥ 边界裁定）

- **ST-11.1 对齐**：视图族全部并入偏序（子句 5），删除 compatibleView 与 funBody viewGap；造格/借用 6 位 demoteView(want)（含 e2e impureBump(ct) 的借用路径——反向子句删除后经 callFun 实参位 demute 保留，65/68 逐字不变 ✓）。
- **ST-13.1 对齐**：List[A] <: List[B] ⟺ A <: B（子句 9 协变）；@mut List[A] <: @mut List[B] ⟺ A = B（子句 5 both-Mut + typeEq 内层）——两方向均有测试（LIST-COVARIANT-ANY / MUT-LIST-INVARIANT）。
- **步骤 3 归属（显式声明，防越界）**：ST-12 复合极性、ST-13.1 双视图 (σ_imm, σ_mut) 变构性对、协变上界/逆向下界方向推断、泛型结构体代入结构满足（本步骤仅普通结构体）——均为 P-10 步骤 3 验收项。

## 八、验证（2026-11-08 实测）

- **:frontend:test --rerun-tasks = 597/0**（基线 588/0 + SubtypeOrderTest 9 方法 35 例：负例 17 全拒（RED-OK）、正例 17 全绿、NOCODE 1（E-TC-BOUNDARY 无残留））；
- **e2e 65/68 逐字不变**（3 既有红 root 码集不变）+ **结构性违规 0**；双快照（HirJson 23/0）零警告零错误；
- **ST-1~ST-15 负例必拒**（Str→Nat / Nat→Nothing / 联合→成员无 is / 函数型逆变违例 / 函数型协变违例 / @mut List[Nat]→@mut List[Any] / Nat→@mut Nat / 结构体缺字段 / 结构体深字段不符 / Real↔塔双向 等），**正例必过**（Nothing→任意 / 任意→Any / Nat→Int→Rat / 联合成员注入 / @mut T→T / 结构体宽化 / 特质满足 / 特质父链 / 函数型合法变构两方向）；
- **E-BOUND-INVARIANT 真实落地**：BOUND-INVARIANT-MIXED 落码且 E-TC-BOUNDARY 不残留（G3）；**机械审计**：全仓 compatibleView 引用 = 0（删除），旧 E-TC-BOUNDARY「具体类型而非特质」字串 = 0；
- 燃料/深度哨/轮次上限未动（同池，未开新池）；结构违规 0。

## 九、残余与后续（步骤 3 输入）

1. 联合别名展开仅靠燃料兜底的形态（ST-15.5 允许 V1）；2. 结构满足仅普通结构体（泛型代入属步骤 3）；3. 界方向与复合极性（ST-12/ST-13.1）；4. 造格位降级深 @mut 外层语义的交叉验证（impureBump 保留外，e2e 无 @mut 泛型容器样本，单测已覆盖）。