# ADR-38：视图降级（@mut T 在类型计算中视为 T）与「不猜类型」纪律

> 状态：已落地（t61） · 日期：2026-11-08 · 前置：ADR-36（P-10 健全化）、ADR-37（t60 赋值/默认值核对）
> 来源：t60 暴露的「`var x: @mut Nat = 1; x = x + 1` 报 Bool」被队长判定为**整类**分类缺陷（非单点）。范围：TypeInfer（demoteView / adaptOk 单向视图 / binExpr 不猜类型）、Elaborator（算符前缀调用分类）；adaptOk 判官主体、e2e 样本、燃料与深度哨未动。

## 一、BEFORE（2026-11-08 01:30 实测，探针）

| # | 源 | BEFORE | 目标 |
|---|---|---|---|
| 1 | `var x: @mut Nat = 1; x = x + 1` | `E-TYPE-MISMATCH: 赋值右值 Bool 与目标 @mut Nat 不符` | 通过（Nat+Nat=Nat） |
| 2 | `fun f(a: @mut Nat): Nat = a` | `E-TYPE-MISMATCH: 函数体是 @mut Nat` | 通过（视图降级） |
| 3 | `fun f(a: @mut Str): Str = a + "x"` | `函数体是 Bool` | 通过 |
| 4 | `fun f(a: @mut Nat): Range[Nat] = 0..a` | `区间 .. 的端点须为数值，实际 Nat 与 @mut Nat` | 通过 |
| 5 | `fun f(p: @mut P): Nat = p.x + 1` | 通过 | 通过（基线对照，不动） |
| 6 | `fun f(a: @mut Nat): Bool = a < 2` | 通过 | 通过（基线对照，不动） |

**对照实测附加发现**：`fun g(a: Nat): @mut Nat = a`（对照例 C1）**BEFORE 实测通过**
（队长任务书预期为报错）：旧路径 `compatibleView` 的反向子句 `want is HirMutType -> typeEq(got, want.inner)`
令任意 `T → @mut T` 被接受。故「返回位不造格」规则需**新建**（见 §三.4），否则对照例无法满足。
`fun g(a: List[Nat]): @mut List[Nat] = a`（C2b）同源，BEFORE 亦通过。

## 二、根因

1. **分类点裸用 NamedType**：`@mut T` 不是 `HirNamedType` → `isNumeric()`/`isNominal()`/`builtinOp` 全部落空：
   binExpr 数值与表内算符走 `builtinOp(op, [l, r])` → null；区间端点、Str 拼接、比较名义分支同理。
2. **猜类型兜底**：binExpr 兜底 `return namedT("Bool")`（T711）把「无法定类」伪装成 Bool。
3. **视图子类型双向对称**：`compatibleView` 对 `@mut T ↔ T` 双向 `typeEq` 互认，且 adaptOkBody 的别名展开子句（`want is HirNamedType &&
   aliasExpander != null`）在兼容回退前就吞掉 `got is HirMutType` 的情形（返回 false），构成「正向（@mut T → T）被拒、反向（T → @mut T）被收」的倒置。

## 三、改动

1. **`TypeInfer.demoteView(t)`**：`while (t is HirMutType) t = t.inner`（多层 @mut 递归剥层）。所有按类型内容**分类**的判定一律先降级。
2. **`adaptOkBody` 单向视图规则**（置于别名展开子句前）：`got is HirMutType && want !is HirMutType -> adaptOk(got.inner, want)`。
   **未加反向规则**（`T <: @mut T` 不成立）。反向仅保留于 `compatibleView`（初始化/赋值造格、调用实参位借用——`impureBump(ct)` 类语义）。数值塔/联合/Any 等既有子句对降级后的内层递归复用。
3. **`binExpr` 改造**：入口 `val l = demoteView(leftType); val r = demoteView(rightType)`，**全分支**用 l/r（区间/in/比较名义/Str 拼接/兜底）；兜底「猜 Bool」**删除**，改为
   `diag.error("E-OP-UNCLASSIFIED", pos, ...) + syntheticT("中缀" + op)`。E-OP-UNCLASSIFIED 与 E-DEFAULT-ORDER 同族（新诊断码，无集中码表）。
4. **`funBody` 返回位不造格**：`eff is HirMutType && bodyType !is HirMutType && !typeEq(bodyType, eff)` → reportMismatch。
   plain 值不得在返回位升格为 @mut 视图——造格（`var x: @mut T = v`）与实参借用（`bump(ct)`）不在此列，经 compatibleView 保留。

## 四、AFTER（2026-11-08 01:50 / 02:00 两轮实测）

| 探针 | AFTER |
|---|---|
| #1~#6 | 全部通过（#1 `x = x + 1` 现为 Nat， #4 得 Range[Nat]） |
| C1 `fun g(a: Nat): @mut Nat = a` | **E-TYPE-MISMATCH: 函数 g 声明返回 @mut Nat，但函数体是 Nat**（对照例 ✓ 方向未搞反） |
| C2 `fun g(a: @mut List[Nat]): @mut List[Any] = a` | **报错**（实参位 typeEq(Nat, Any)=false ⇒ 不变性保持） |
| C2b `fun g(a: List[Nat]): @mut List[Nat] = a` | **报错**（返回位不造格，与 C1 同规则） |
| C3 `fun f(a: @mut Nat): Nat = a + 1` / C4 `fun f(a: @mut Nat): @mut Nat = a` | 通过 |
| 不回退：`var x: @mut Nat = 1; x = 5; x = x + 0`、`b.n = 2`、`impureBump(ct)`（借用） | 全部通过 |

**e2e 零转红（65/68 逐字不变）**：e2e 的 @mut 形态仅为 `@mut struct` 字段写与 `@mut var` 绑定算术，全部落在保留路径；
副作用样本 `impureBump(ct)`（plain 实参借入 @mut 形参）因反向子句保留而未转红。个位新增拒绝仅落在 ViewDemotionTest 对照例。

## 五、机械审计（裸分类点清单，硬要求）

对 TypeInfer.kt / Elaborator.kt 全量 `as? HirNamedType` / `is HirNamedType` / `isNumeric()` / `isNominal()` 分类点逐个判定
（2026-11-08 02:10 采样）:

**须降级（已改）**:
- binExpr 全分类点（区间端点、in 右侧、比较名义、Str 拼接、numeric 兜底）→ 入口 l/r 降级 ✓
- `builtinOp` 的算术/除法 numeric 判定（TI:540/542）→ 经 binExpr 降级后入参 ✓
- Elaborator:896 `infer.builtinOp(e.op, args.map { etype(it) })`（算符前缀调用）→ wraps `demoteView` ✓（实测：脚本语法不产生该路径时无变化，属防御性）

**命名域守卫（@mut 天然不匹配，无需降级）** — 理由：这些是 `is`/`as?` 守卫后作用于 named 类型的谓词，`@mut T` 不通过守卫即落到既有的 Mut/Union/合成路径（结构性重建器 renameTps/substTps/cleanTpVars 本就逐层处理 HirMutType）:
- TI:80 containsSynthetic、TI:246/247 isSynthetic/isUnsealed、TI:272 isAnyExpect、TI:276 isNothingT、TI:279 isNominal 定义本身、TI:285 isNumeric 定义（receiver 即 HirNamedType）
- TI:369/371 adaptOkBody 数值塔与同名同参子句（got/want 已被 `is HirNamedType` 守卫，@mut 由 §三.2 新规则先行接管）
- TI:415/423/456/457/481/482/491/501/518/528/530 unifyInto 族（Only name-vs-name 合一；@mut 操作数经 adaptOk 路径而非 unify；未解则不绑定，不构成静默错误）
- TI:645/657/667 varAnnotated/assignTypes/defaultType 的 demoted 数值塔守卫（named-named 守卫，塔方向性独立于视图；保留 t60 既有口径不改）
- TI:816/834/900、EL:1073/1119/1955/2230/2246/2251 结构性重建/替换枚举（named 域内重建，HirMutType 有对应重建分支）

**结论：0 处裸分类残留**（binExpr/builtinOp/前缀调用三处已降级；其余为命名域守卫，附理由如上）。

## 六、「不猜类型」纪律及兜底点清单

**纪律**：`binExpr` 的「表外/不可分类 → Bool」是全前端唯一把「不知道」伪装成「知道（具名 Bool）」的兜底；改为 `diag.error(E-OP-UNCLASSIFIED) +
syntheticT(...)` —— 与既有的 EL896（`?: syntheticT("算符" + e.op)`）和 R-38 落点闸（`containsSynthetic → 不报；合成位显式标记未推导`）同构。
**适用范围推广**：`unify ... ?: x`（TI:529 builtinOp reduce、TI:652/664/674 demoted 回退 `?: x`）是**数值域内的保守回退**（塔方向确定、不出合成位），不属猜类型；`aliasExpander ?: ...` 的空安全回退是契约域。`uniExpr` 的 `→ Bool`（!/¬）是**表内真规则**（操作数任意皆得 Bool），亦非猜。
**审计到的其他兜底**：reportMismatch 的 containsSynthetic/燃料门（R-38，deliberate）；`subTpNames` 的 `?: t`（无解保形，t33 契约）。未发现第二个「具名猜类型」兜底。

## 七、验收

- `:frontend:test --rerun-tasks` = **588/0**（573 基线 + **15** ViewDemotionTest：v1~v10 正例 + c1~c3 对照拒绝 + d1/d2 不回退）。
- e2e **65/68**（3 既有红逐字不变）；结构违规 **0**；双快照（compileKotlin/compileTestKotlin）BUILD SUCCESSFUL，`w:`/`e:` 行 **0**。
- 燃料（FUEL_DEFAULT）与深度哨未动；adaptOk 判官主体未动（仅新增一条子句 + funBody 位置规则）。
- t60 测试（AssignDefaultsCheckTest 20 例）未回退（同次 full run 全绿）。

## 八、影响面与后续

- 日常语法 `x = x + 1`（可变量自增）恢复可用（t60 的表 #1 主修复）。
- 返回位造格被新拒绝：既有代码 `fun g(): @mut T = <plainT>` 形态将落 E-TYPE-MISMATCH（迁移：返回 @mut 表达式或改签名）。e2e 无此形态。
- 剩余 @mut 族已知点（不在 t61 范围）：@mut 接收者**读**路径字段查表（M2/R-38 闸容忍，P-10 步骤 4 字典消解）；`isNumeric` 目标位不可变性（ST-13.1）属步骤 3 子类型偏序。