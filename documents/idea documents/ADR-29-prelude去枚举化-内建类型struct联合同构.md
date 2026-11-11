# ADR-29：prelude 去枚举化——内建类型与用户代码同构（struct + 联合）

**任务**：t44（semantics） · **日期**：2026-11-07 · **前置**：ADR-28（联合类型与空构造子单例，t41）、ADR-24（HIR schema v2 / 双快照判据）、ADR-15 §2.2（符号登记缓存不跨编译共享）

## 一、背景与裁决

旧 prelude 把内建类型按「枚举概念」实现：SymbolTable 四张表（构造子字段规格 `PRELUDE_CTORS`、theory 元数 `PRELUDE_ENUM_THEORY`、theory 形参名 `PRELUDE_ENUM_THEORY_NAMES`、密封构造子清单 `PRELUDE_ENUM_CTORS` + `PreludeCtor` 记录 + Collector 的 `PRELUDE_ENUMS`）把 Optional/List/Result/Set/Map 描述为「内置枚举 + 构造子」。这与 P-9/t40 删除 enum 后的用户代码形态（struct + 联合别名，ADR-28）**不同构**：内建类型走特权通道（synthetic 宽容、无 arity 核对、空构造子调用静默通过、穷尽性走 sealedCtorNames 特判）。

t44 裁决（用户硬性要求）：
1. 内建类型改造为「struct + 联合」形态，与用户代码**同构**（同一通道、同一核对、同一诊断）；
2. 空构造子（None/Nil/SetNil/MapNil）登记字段数 0 → 走 t41 单例语义，`None()`/`Nil()` 调用报 **E-NULLARY-PAREN**（用户裁决，旧实现静默放行）；
3. `preludeMethod` 名字层表（Task/Channel 方法，t39）**不动**；
4. 穷尽性通道：能复用 t41 联合穷尽性则删表（见 §四：**已删**）；
5. P-7 用户优先口径**不变**。

## 二、机制：虚拟声明（PreludeDecls）

新增 `semantic/PreludeDecls.kt`：一个 internal object，在 Elaborator `init {}` 把**虚拟声明**预注入 `structDecls`/`aliasDecls`：

- **虚拟 struct 族**（字段数即 arity 核对源）：`None()`、`Some[T](value: T)`（t41 后的通用形态）、`Nil()`、`Cons[T](head: T, tail: List[T])`、`Ok[T](value: T)`、`Err[E](value: E)`、`SetNil()`、`SetCons[T](head: T, tail: Set[T])`、`MapNil()`、`MapCons[K,V](key: K, value: V, tail: Map[K,V])`、`Channel[T](cap: Nat = IntLit("0"))`（cap 默认 0=无缓冲，D-1 点查：旧实现 `new Channel(0)`，JsCodeGen:1268 / CallCheck:201）。
- **虚拟联合别名族**：`Optional[T] = None | Some[T]`、`List[T] = Nil | Cons[T]`、`Result[T,E] = Ok[T] | Err[E]`、`Set[T] = SetNil | SetCons[T]`、`Map[K,V] = MapNil | MapCons[K,V]`。

虚拟声明是**文件条目之外的注入**（AST StructDecl/TypeAliasDecl，位置 SYNTHETIC_POS）：不落 HIR/JSON 产物、不参与声明级检查（重复/环/归纳）；用户 file 条目随后注入 → **用户同名声明直接覆盖**（P-7，见 §七结构性例外）。SymbolTable 侧：prelude ctor 符号 kind 由 BUILTIN 改 **CTOR**（`Some(1)` 经 callExpr 的 CTOR 分支 → ctorCall 的 struct 分支，与用户 struct 完全同通道）；`lookupType` 加 `lookupPreludeStructType` 兜底（仅虚拟 struct 名，用 builtinEntry 登记）。

**删除的表**（全文件零残留已验证）：`PreludeCtor`、`PRELUDE_CTORS`、`PRELUDE_ENUM_THEORY`、`PRELUDE_ENUM_THEORY_NAMES`、`PRELUDE_ENUM_CTORS`、访问器 `preludeCtorFields`/`preludeCtorDefaults`/`preludeEnumTheoryOf`/`preludeEnumTheoryNames`/`preludeEnumCtorNames`、Elaborator `preludeFormals`/`explicitCtorCall`（BUILTIN 路由前置）、Collector `PRELUDE_ENUMS`（无消费方死代码，随这次一并清理）；`ctorFieldCtorNames` 简化为「struct 字段类型 → unionSealCtors」单通道。保留：`PreludeField`/`PreludeFun`/`PRELUDE_FUNS` 自由函数签名表（t19 B1，`preludeFunSig` 内联 `preludeFieldType` 形态转换）与 `preludeMethod`/`PRELUDE_METHODS`（t39，未动）。

## 三、逐类型改造前后对照

| 内建类型 | 旧（枚举概念） | 新（t44） | 实测 |
|---|---|---|---|
| Optional[T] | PRELUDE_CTORS 规格；Some(1) : 合成 Optional；None() 静默 | 虚拟 struct None/Some[T] + 别名 None\|Some[T] | `Some(1)` : **Some**（构造调用返 struct 名）；`None` : None 单例；`None()` → E-NULLARY-PAREN |
| List[T] | 同上（Nil/Cons） | 虚拟 struct Nil/Cons[T] + 别名 | `Cons(1, Nil)` : Cons；`Nil()` → E-NULLARY-PAREN；`Cons(1, None)` → E-TYPE-MISMATCH（旧宽容，新行为正确） |
| Result[T,E] | 同上（Ok/Err） | 虚拟 struct Ok[T]/Err[E] + 别名 | `Ok(1)` : Ok、`Err("e")` : Err；`Ok(1,2)` → E-TYPE-MISMATCH（arity 核对） |
| Set[T] | 同上（SetNil/SetCons） | 虚拟 struct SetNil/SetCons[T] + 别名 | `SetNil()` → E-NULLARY-PAREN；when 穷尽经联合 |
| Map[K,V] | 同上（MapNil/MapCons） | 虚拟 struct MapNil/MapCons[K,V] + 别名 | `MapNil()` → E-NULLARY-PAREN；MapCons 3 字段核对 |
| Channel[T] | preludeMethod 特判 + 旧 CallCheck 特判 | 虚拟 struct Channel[T](cap: Nat = 0) | `Channel<Nat>()` : Channel（cap 可省=0 无缓冲）；`Channel<Nat>(4)` 同通道 |
| when 穷尽性 | sealedCtorNames prelude 密封特判 | **unionSealCtors 联合通道**（虚拟别名展平） | `fun f(o: Optional[Nat]): Nat = when (o) { is Some(v) -> v ; is None -> 0 }` 无错；缺 None → E-NON-SEALED-MATCH + W-NON-EXHAUSTIVE |

**E-NULLARY-PAREN 实测文本**（PreludeDeEnumTest 逐字断言，与 UnionSemanticsTest 用户裁决文案一致）：
`空构造子 None 不能用 () 构造，请直接写 None（空构造子全局唯一）`。

## 四、穷尽性通道裁决（硬性要求 3）

**结论：删表，复用 t41 联合穷尽性。** `sealedCtorNames` 的 prelude 密封分支与 `PRELUDE_ENUM_CTORS` 一并删除；prelude 联合别名经 `expand()`（aliasDecls 表）展平为 HirUnionType 后，与用户联合**同一条** unionSealCtors 通道（roots ∈ structDecls 即静态可枚举）。unionSealCtors 能命中虚拟 struct 族（预注入 structDecls），故无需任何 prelude 特判。`Bool` 仍保留 `listOf("true","false")` 两值特例（字面量族，非构造子）。

## 五、适配修复：adaptOk 的具名联合别名实参

实测发现样本回归：`optStr(jsonGet(j, "name"))` 报 `实参 1 Optional[Json] 与形参 None | Some[Json] 不符`。根因：内建函数签名（builtinSig）返回**具名**形态 `Optional[Json]`（未展开），而函数形参经 expand 展平为联合；adaptOk 的成员→联合单向拓宽不覆盖「联合别名具名 → 联合展开」方向。

修复（TypeInfer.adaptOk，构造器注入 `aliasExpander`，Elaborator 注入 `expand`，默认 null——mkInfer 纯代数件不变）：
```kotlin
want is HirUnionType && got is HirNamedType && aliasExpander != null ->
    (aliasExpander!!(got) as? HirUnionType)?.let { ge ->
        ge.variants.all { gv -> want.variants.any { wv -> adaptOk(gv, wv) } }
    } ?: want.variants.any { adaptOk(got, it) }
```
具名联合别名实参按其展开联合比对（联合↔联合按成员逐个覆盖）；成员→联合单向拓宽不变。

另修：`structFieldTypes` 的主题实参源。旧具名分支直接取**联合别名整体实参**，与成员 struct 自身 theory 可不同元（`Result[T,E]` 的成员 `Ok[T]`）——异元情形误判 tps 不齐、回落未解析参数（样本 `is Ok(v) -> v` 推导成 T）。修法：主题先归一为联合（具名别名经 expand 展平），再按**成员变体实参**代入（用户旧路径 Box[T]/Lst[T] 同 theory 情形结果不变）。

## 六、验证（采样 2026-11-07）

- compileKotlin **双快照**（01:06:37 / 01:06:58，构建槽位独占）：BUILD SUCCESSFUL，e/w **0**（ADR-24 §五判据）。
- `:frontend:test --rerun-tasks`（01:09：02）：**464 用例 / 0 失败**（基线 440 + 新增 **PreludeDeEnumTest 24**：构造形态 8 + 空构造子单例 4 + 联合形态 6 + 用户优先 3 + HIR/JSON 3；用例净增纯粹新文件，基线零回退、零改写）。
- e2e **46/68**，结构性违规 **0**（含 nid 唯一性回归锁定持续成立）。

## 七、已知影响与裁决建议（e2e -1 的归因）

e2e 46 = 基线 47 **-1**：唯一新红样本是 `examples/空安全.subl`（其余 22 红与 t41 基线 21 红逐一对账一致：19 enum 样本 E-ENUM-REMOVED + 手动证明 + 模块系统/主）。该样本两处带括号空构造子调用（`elvis(None(), 7)`、`pointX(None())`）落本次**硬性要求 1 的用户裁决** E-NULLARY-PAREN——old 行为（静默构造空 Optional）已废。样本属 t46 迁移领地（本任务不动样例），其迁移为两字符改动（`None()` → `None`）。故 46/68 是「用户裁决 × 样本冻结」的必然结果；`≥47` 门槛的超出值（1）建议队长裁决（口径调整为 46=47 基线 +1 裁决红，或授权 t46 顺手迁移该两处）。

结构性例外（P-7 不变条款下的既有保留）：`Optional`/`List`/`Result`/`Set`/`Map` 原已位于 BUILTIN_TYPES 占位表（t44 前既有），用户**别名**同名仍落既有「与既有类型重名」E-DUP-DECL（非 t44 引入，不因虚拟联合别名机制放宽）；虚拟 **struct** 名无此占位——用户同名 struct 直接覆盖（PreludeDeEnumTest 双结构覆盖断言）。保留该不对称是「P-7 口径不变」的直接后果。

## 八、教训

1. 联合机制普及时，「具名联合别名 ↔ 展开联合」的双向适配是易漏面——参数侧 expand 了、返回侧没 expand，adaptOk 就出现跨形态缺口。单源探针 + diag 逐条打印仍是定位手段。
2. 构造子 theory 与联合别名 theory 不同元（Result[T,E] × Ok[T]）时，字段类型代入的**实参源必须是成员变体**，不能是别名整体。
3. 「不失枚举语义」的迁移决策必须有可直接引用的实测文本断言（E-NULLARY-PAREN 逐字文案）落测试，否则裁决会在重构中漂移。
