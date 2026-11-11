# ADR-31：prelude 权威性（容器名让位）+ 别名接收者扩展分派（R-27）+ 期望侧联合别名展开（R-28）

**任务**：t47（semantics） · **日期**：2026-11-06 · **前置**：ADR-29（prelude 去枚举化：虚拟 struct/联合别名）、ADR-28（联合类型与空构造子单例）

## 一、问题陈述（修复前实测，20:57Z / 21:22Z 探针）

| # | 源码 | 修复前 | 期望 |
|---|---|---|---|
| A | `type List[T] = Nil | Cons[T]`（+ struct Nil/Cons） | **E-DUP-DECL**「类型别名 List 与既有类型重名」——`SymbolTable.BUILTIN_TYPES` 仍占位 List/Optional/Result/Map/Set | prelude 虚拟声明为唯一权威，用户同名按 P-7 覆盖 |
| B | `type Opt[T] = None | Some[T]` + `fun Opt[a].map[a,b](f: (a) => b): Opt[b]`，方法糖 `s.map(inc)` / 自由式 `map(s, inc)` / 扩展自递归 | 三形态全报「方法 .map 不可解析／未定义的名称 map」 | 三形态均可分派 |
| C | `fun f(o: Optional[Optional[Nat]]): Nat = 0` + `f(Some(Some(3)))`（/ `var x: Optional[Nat] = Some(3); f(Some(x))`） | **E-TYPE-MISMATCH**「实参 1 Some[Some[Nat]] 与形参 None \| Some[Optional[Nat]] 不符」 | 形参/实参两侧联合别名按同一实体比对 |

根因（两个独立的架构事实）:

1. **登记/查找不对称**：登记侧 `Collector` 按 `ExtFunHeads.ofAst(receiver)` 取**名字**（`Opt`），查找侧 `Elaborator` 取 `ExtFunHeads.ofHir(静态类型)`——而 VarStmt 声明类型**即时 expand**（`var s: Opt[Nat]` 的静态类型 = `None \| Some[Nat]`），`ofHir` 把联合规范化成形状键 `Union/2`，与登记键 `Opt` 永不相交。「expand 早」是全管线口径，不能回退（联合成员分派/空构造子单例都依赖展开形态）。
2. **期望侧不展开**：`TypeInfer.adaptOk`/`unifyInto` 只在 `want is HirUnionType` 时感知联合；形参位的 `Optional[Optional[Nat]]` 经 expand 后是 `None \| Some[Optional[Nat]]`——外层展开了，**成员实参里的 `Optional[Nat]` 仍是未展开形态**，与实参 `Some[Some[Nat]]` 的成员比对时 `Some[Some[Nat]]` vs `Some[Nat]` 落 false。

## 二、决策（R-26 / R-27 / R-28）

- **R-26 容器名让位（prelude 权威性）**：`SymbolTable.BUILTIN_TYPES` 删除 `List/Optional/Result/Map/Set`（保留真内建标量/单例/异步泛类型/Range/Json）。`lookupType` 串尾兜底新增 `lookupPreludeAliasType(name) = PreludeDecls.aliasOf(name) != null → builtinEntry(name)`。
  - 用户 `type List[T] = ...` 按 P-7 覆盖（user `aliasDecls` 先查，旧占位 E-DUP-DECL 消失）；未声明时容器名仍可解析（虚拟声明兜底）。**边界**：虚拟 **struct** 名（None/Some/Nil…）从来无占位，用户 struct 直接覆盖（t44 既有口径不变）。
- **R-27 分派键保留别名身份**：`Elaborator.extHeadOf(t, methodName = "")`（接入方法糖 FieldExpr 分派位与自由式分派位）——
  - 非联合接收者：原口径 `ExtFunHeads.ofHir`（具名头 / Tuple·Fun 形状键）；
  - 联合接收者：成员头集合（`ofHir` 逐成员）反查**别名**——`aliasDecls`（用户别名 + prelude 虚拟联合别名）中 target 联合成员头集合与之全等者取别名名；多候选优先桶内已注册该 `methodName` 的（`symbols.lookupExtDeclVisible`）；无别名命中才回退形状键 `Union/2`（保留 `fun (Nat \| Str).f` 形态的既有登记口径）。
  - 踩坑记录：首版把 `ofHir` 的返回当 nullable 兜底——`ofHir(HirUnionType)` 返回 **`"Union/2"` 而非 null**，导致别名反查分支是死代码（探针实测 `direct=Union/2 size=2 heads=[None,Some]`）。修正为「联合先走别名身份、非联合保原口径、无命中回退 direct」的优先级序。
- **R-28 期望侧（与双向合一位）的联合别名展开**：
  - `adaptOk`：在具名分支之后、`compatibleView` 兜底之前新增 `want is HirNamedType && aliasExpander != null` → 展开期望侧别名成联合重试（`Optional[Nat] → None \| Some[Nat]`）。合成/数值塔/同名实参递归在既有分支先处理，别名只在真命中时改写，递归以「展开后两侧结构层级下降」终止。
  - `unifyInto`：变量判定之后、具名实参合一之前，任一侧为具名联合别名 → 双双展开成联合后按既有联合对位合一继续（**别名身份不参与变量求解**——`b` 仍由成员对位解出）。这一条是任务 2 扩展调用（`extCallType` → `callFun` → `instCall`）能解出理论参数的关键：`unifyInto(None \| Some[Nat], Opt[_i0.b])` → 展开联合对位 → `b := Nat`。

## 三、反例口径（不放宽的边界）

- `f(Some(Some(true)))` 对 `o: Optional[Optional[Nat]]` → **E-TYPE-MISMATCH**（内层 Bool/Nat 真不符）。
- `f(Some(Some(Some(3))))` 对 `o: Optional[Optional[Nat]]` → **E-TYPE-MISMATCH**（实参嵌套层数多于形参——结构性真错：`Some[Some[Nat]] ∉ None \| Some[Nat]`。「三层嵌套」的正例是**形参位数匹配**的 `o: Optional[Optional[Optional[Nat]]]` × `Some(Some(Some(3)))`，见 §四正例）。
- 扩展分派成功后签名核对照旧：`s.map(1)`（传 Nat 给 `f: (a) => b`）→ **E-TYPE-MISMATCH**（并连带返回类型 E-RETURN-TYPE，均为既有口径）。

## 四、验证（采样 22:24–22:26，构建槽位独占）

- 正式测试 `AliasSemanticsTest`，**14 用例 / 0 失败**：
  - 任务 1：userListAliasOverridesPrelude / preludeContainerWorksWhenNotDeclared / userStructNamedNilNoDup / userOptionalAliasTakesEffect（后者覆盖旧测 `preludeAliasNameReservationIsUnchangedP7`——其断言的「BUILTIN_TYPES 占位 → E-DUP-DECL」被 R-26 直接推翻，测试随新行为改写并注明）；
  - 任务 2（三形态 + prelude 接收者 + 反例）：aliasReceiverMethodSugarDispatch / aliasReceiverFreeFormDispatch / aliasReceiverExtSelfRecursion / preludeListReceiverUserExtDispatch / aliasReceiverDispatchStillChecksArgs；
  - 任务 3（正反例）：nestedCtorArgMatchesAliasUnion / varThenCtorArgMatchesAliasUnion / threeLevelNestingMatches / ctorArgDeepMismatchStillReports / ctorArgLevelMismatchStillReports。
- `:frontend:test --rerun-tasks`：**493 用例 / 0 失败**（t45 基线 479 + 新增 14；数量变化只有「+14 AliasSemantics」与「PreludeDeEnumTest 改写 1 条（24 条不变）」）。结构违规 0（nid 唯一性回归锁定持续成立）。
- e2e：对**当前样本集**修复前后对照（revert-run 求差法，22:05Z 前状态 → 22:26Z 后状态）:
  - 前：ok=true **48**（20 红）；后：ok=true **50**（18 红）；新绿 **2**：`examples/模式匹配.subl`、`examples/特性巡礼.subl`（均为 `Optional[Optional[…]]` 嵌套 ctor 场景，R-28 正例）。其余 18 红的驱动与 t46 样本迁移领地一致（E-ENUM-REMOVED 16 个 `enum` 旧形态样本 + 手动证明 + 模块系统/主）。
  - 注：队长交付文引用的 e2e 基线 46/68 采样于 t45 收口；本任务执行期内样本集（t46 在并行动作）使前状态为 48/68——报告以同一代码+同一样本集的可复现差分（48→50）为准。
- compileKotlin + compileTestKotlin（22:26 前后两次）BUILD SUCCESSFUL **e=0 / w=0**（ADR-24 §五）。

## 五、改动位点

| 文件 | 改动 |
|---|---|
| `semantic/SymbolTable.kt` | BUILTIN_TYPES 删 5 容器名（R-26）；lookupType 串尾追加 lookupPreludeAliasType |
| `semantic/Elaborator.kt` | 新增 `extHeadOf`（R-27），接入方法糖/自由式分派头计算位 |
| `semantic/TypeInfer.kt` | adaptOk 期望侧别名展开（R-28）；unifyInto 别名↔联合双向合一（R-28） |
| `semantic/PreludeDeEnumTest.kt` | `preludeAliasNameReservationIsUnchangedP7` → `preludeAliasUserOverrideP7`（P-7 覆盖口径） |

## 六、V1 边界（留档）

- R-27 的别名反查要求联合成员**全具名**（`ofHir` 逐成员非 null 且头集合大小 == 成员数）；含嵌套联合成员（`a \| (b \| c)`）的联合回退形状键。
- R-28 只在 `aliasExpander` 注入的主管线生效（独立测试件 `mkInfer` 不注入，保持纯代数件语义）。
- 没有引入 ctor 实参位的 expected-type **下传**（即「调用处期望 → 联合成员 ctor 的字段位」通道）：`Some(Some(3))` 仍按实参自底定型后由 adaptOk 联合成员比对接受。层数超配的真结构性错误（反例 2）在这一模型下可正确判负；未来若需 ctor 参数位 contra 逆变期望传递，另立 ADR。