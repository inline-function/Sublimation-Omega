# ADR-25：P-8/D 语义层裁决——扩展函数桶、三调用形态、结构化满足与约束检查

- 日期：2026-11-06（编号 2026-11-06 由队长勘误：原误编 ADR-24，与 t29 的 ADR-24「HIR schema v2 与 impl 节点删除」重号；t29 先注册故保留 24，本文件改为 25）
- 日期：2026-11-06　任务：t30（P-8/D）　作者：semantics
- 上游卷宗：`documents/boring documents/类型系统卷-TS-v1.md`（TS-8~TS-27）；上游语法裁判：ADR-23（t28）
- 验证：`:frontend:compileKotlin` 零错；`:frontend:test --rerun-tasks` **364/0**（t28 基线 342 + 本任务新增 22）；e2e 57/68，失败项均为旧 `impl` 样本落 `E-IMPL-REMOVED`（t28 起的迁移悬置，归 t31 样本迁移）

## D1 扩展函数登记与桶（TS-11/TS-13）

- 登记：`SymbolTable.extFuns` 以 `(接收者类型头, 名字)` 为键，与自由函数表**完全隔离**——同名自由函数与扩展函数共存不互遮蔽（正例 `ExtensionSemanticsTest.extFunBucketIsolatedFromFreeFuns`）。
- 重复判定：附加 `形参类型列表骨架`（理论参数位归一为 `$n`，其余按名+实参个数）作为第三维；**只有三者全同才判重复**，`E-DUP-EXT` 按位置表逐处报（既有声明各位置 + 本次声明位置，无冗余第三条）。
- 接收者类型头：`NamedType→name`、`QualifiedType→name`、`Mut/Atomic→递归 inner`；头未知 → `W-EXT-OBSOLETE`（卷 TS-9 §9）**warn 不阻断**，且头位改走静默查名（不再落 `E-UNBOUND-NAME`）；Receiver 中**实参位**的未声明名/未声明理论参数仍由全量 `type()` 落 `E-UNBOUND-NAME`（TS-15）。

## D2 三种调用形态（TS-12/TS-14/TS-10）

- **方法糖 `x.f(a)`**：`typeHeadOf(x 的静态类型)` → 扩展桶 → `pickExtDecl`（按 `实参数 = 扩展形参数 + 1` 初选，否则首项）→ `extCallType`（接收者作首参的 `callFun`）。未命中 → builtin/synthetic 兜底（保持原行为）。
- **直接调用 `f(x, a)`**：`resolveValue` **按名**先判——只要存在同名自由函数即归自由函数（即使 arity 不符，报 `E-ARITY`，**不回退扩展桶**）；值域无该名且存在同名扩展候选（`hasExtFun`，或复合名可拆且该头下有该扩展）→ **静默**（不落 `E-UNBOUND-NAME`），待实参 1 定型后按其类型头查桶；落空才补 `E-UNBOUND-NAME`。此为 TS-14「自由函数优先、不打分」的实现口径——**不实现更具体者胜**。
- **类型限定 `T::f`**：复合名 `NameRef` 拆分，`T` 为类型头且该头下存在 `f` → 固定头计划；否则按普通名处理。
- **self 绑定（TS-10）**：扩展函数体内以 `PARAM self` 声明，静态类型 = 接收者 `type(receiver)`（实例如 `fun Str.bad(): Nat = self + 1` 落 `E-TYPE-MISMATCH`，不放宽）；形参名与 `self` 同名 → `E-DUP-NAME`。

## D3 结构化满足（TS-17~TS-20，本任务核心）——实现裁决

判定时机**严格限于使用期**（约束检查现场，TS-18），声明期不判定；判定基于本编译单元**全集索引**（file() 预扫描的 `extFunDecls`，声明顺序无关，TS-19），单次编译按 `(render(T), C)` 缓存。

### 裁决 R-1：规范首位 Self 形参的接收者吸收位

规范（class 体成员函数）的方法签名与扩展函数调用签名 (`receiver, params...`) 对齐规则：

- 规范**首位形参为 Self 形态**（`HirSelfType` 或具名 `Self[...]`）→ 该位由**接收者承担**：接收者扩展类型与替换后的首位形参比对，其余规范形参与扩展函数形参**逐位**对齐。
  - 例：`class Monoid { fun append(a: Self, b: Self): Self }` ↔ `fun Nat.append(b: Nat): Nat = self + b`；`class Functor { fun map[A,B](fa: Self[A], f: (A) => B): Self[B] }` ↔ `fun List[A].map[A,B](f: (A) => B): List[B]`。
- 规范**首位形参非 Self**（如 `fun zero(): Self`）→ 接收者不参与签名比较（桶键 `(head, name)` 已保证接收者头一致）。
- **吸收位必须在 Self 替换之前判定**（否则替换后首位已是具体类型，无法区分）——实现陷阱，已在 t30 修正。

### 裁决 R-2：签名一致 = 合一，非字符串相等

- 规范与扩展的**双方理论参数均作类型变量**（`vars` 取并集），逐位 `unifyInto` 收集绑定；`T = List[Nat]` 时扩展接收者 `List[$0]` 与规范 `Self` 替换后的 `List[Nat]` 经 `$0 := Nat` 合一成功。未解出的变量保持变量形态，**不**判失败。
- V1 注记：**不强制两侧理论参数个数相等**、**不做别名展开**（spec/ext 任一侧含 alias 的签名留后继卷）。

### 裁决 R-3：Self 替换与残留（TS-27）

- 替换律：`Self`（裸）→ 具体类型 `T`；`Self[A...]` → `T` 的头应用其实参（具名 `Self` 以 `NamedType("Self", args)` 承载——`HirSelfType` 无 args 字段，TS-28/29）。
- 替换覆盖 `Named/Fun/Tuple/Mut/Atomic`；**联合类型（`A | Self`）位不替换、保留原文** → 命中即判失败（fail-closed，`SatisfySelfResidue`）：TS-27 的安全侧实现——宁可判「Self 未被完全替换」也不误判满足。测试 `selfResidueFailsClosed` 覆盖。

### 裁决 R-4：部分满足即不满足，且必须列缺失清单（TS-20）

- 任一规范未匹配 → `E-TC-NOT-SATISFIED`（不因满足多数而放宽）；消息附**缺失规范清单**：`来源类.规范名(替换后形参): 替换后返回类型`（如 `Ring.zero(): Nat`），经 `infer.render` 渲染。
- 继承闭包（TS-8）：父类（含多级）规范并入要求，DFS + `seen` 环访问保护（声明期 `E-CYCLE` 已先行）。
- class 的**常量规范（ClassConst）V1 不作为必需项**（TS-17 注：V1 仅函数规范参与判定）。

## D4 约束检查（TS-21~TS-24）

- 调用点（方法糖命中扩展 / 直接调用命中扩展 / 自由函数+理论参数 callee）：`instCall` 解出理论参数 → `checkConstraints`：
  - `solved[T]` 存在且约束为已声明类型类 → `satisfiesCheck(T, C)`；失败 → `E-TC-NOT-SATISFIED` + 缺失清单 / Self 残留原因。
  - 具体类型约束（struct/enum/内建，即 `classDecls` 无该名但可解析）→ `E-TC-BOUNDARY`（V1 仅支持「类型变量 : 类型类」）。
  - 高阶类型参数带约束（`F[_]: C`）→ **声明期**即落 `E-TC-BOUNDARY`（不等调用点解出——V1 的 HKT 约束必然非法，fail-fast）。多约束 `T: A + B` 文法不可解析（t28 已定）。
  - 未解出的理论参数（synthetic 位）→ V1 不查。**不做字典传递**（TS-23）。
- V1 注记：受约束类型参数上的方法调用（`[T: Show]` 函数体内 `x.show()`）**不做规范签名定型**——`x` 的静态类型头是类型参数，扩展桶未命中 → builtin/synthetic 兜底。即约束只做检查、不做分派（TS-23 的直接后果，留后继卷）。

## D5 Self 分界与 HIR 保真（TS-25/TS-26/TS-29）

- class 体内 `Self` → `HirSelfType`（无实参）；带参 `Self[A]` → 具名 `NamedType("Self", A)`；体外（含扩展函数体、自由函数签名）→ `E-SELF-OUTSIDE-CLASS` + `HirErrorType`。
- 满足判定的 Self 替换**只发生在判定算法内部**（规范签名拷贝上），不写入 HIR 树本体（TS-29，正例断言 class 成员形参类型实例为 `HirSelfType`）。

## 组件位置

- `SymbolTable.kt`：`extFuns` 桶 / `extFunOrigins` 位置表 / `declareExtFun` / `lookupExtFun` / `hasExtFun`。
- `Collector.kt`：`collectFun` 接收者分流（登记 + `E-DUP-EXT` 两处报）、`extFunHead` / `sigSkeleton`。
- `Elaborator.kt`：`extFunDecls` 全集索引、`inClassBody` 栈、`funDecl` 接收者解释与 self 绑定、`classCycleCheck`、`type()` Self 分支、`callExpr` 三调用形态分支、`typeHeadOf` / `extCallPlan` / `pickExtDecl` / `extCallType` / `checkConstraints` / `satisfiesCheck` / `extSpecMatch` / `extSigMatch` / `renderSpecAfterSelf` / `specTypeQuiet` / `substSelf` / `isSelfForm` / `containsSelf` / `quietReceiverHead`。
- 测试：`frontend/src/test/kotlin/sublimation/frontend/semantic/ExtensionSemanticsTest.kt`（22 例，D1~D5 正反例齐；满足判定四类：完全满足 / 缺一条规范（清单入消息）/ 继承链满足 / Self 残留）。

## 附录（t33，2026-11-06）：when 模式绑定变量的静态类型——三形态对称的真正根

### 教训 A：静默降级掩盖了同一根因

t31 现场观察到「TS-14 直接调用落 E-UNBOUND-NAME、TS-12 方法糖同位置正常」，最初的判断是「两条取头路径不对称」。复现后证明**判断浅了一层**：两条路径取静态类型头的逻辑本身一致（均为 `typeHeadOf(实参 1 的静态类型)`），真正的缺陷是 **when 模式绑定变量根本没拿到静态类型**——`x` 的 HIR NameRef 类型是合成体 `(推导:未定义x)`：

- 直接调用 `succ(x)` → 头取不到 → 扩展桶未命中 → 落 `E-UNBOUND-NAME`；
- 方法糖 `x.succ()` → 同样未命中，但兜底链给了**合成类型 `(推导:方法succ)` 且不报错**——「看似正常」实为静默降级。

**教训**：一个形态「能过」未必因为其对，可能只是它的失败路径更安静。定位不对称缺陷时，先核对两条路径的**共同输入**（这里是实参 1 的静态类型），再核对各自的差异；否则会把根因误判为分支差异。

### 裁决 R-5：模式绑定按 ctor 字段声明类型登记（含 is/as 绑定）

- `pattern()` 新增 `slotT`（所属 ctor 字段声明类型）下传：`PatCtor` 按位取字段类型，`PatBind`/`bareNamePattern` 的槽位绑定以此为静态类型登记；`PatIs` 的 `as 绑定`与零字段 ctor 归一的绑定登记 is 类型。
- 主题绑定（`when (v) { … }` 的 `v`）既有口径不变（登记主题静态类型）。
- 实参多余于字段位时尾部槽位为 null（绑定无静态类型）——与 ctorCall 侧 arity 收口互补，不重复报错。

### 裁决 R-6：泛型枚举按 when 主题实例化字段类型（E[T] + 主题 E[arg] ⇒ T := arg）

第一轮按 R-5 只登记字段**声明**类型，引入了 7 个 e2e 回归：泛型枚举自身的理论参数名（如 `enum Box[T]` 的 `T`）会**泄漏**进绑定类型，与外层扩展函数的理论参数名（`a`/`b`）不符（`f: (a) => a` 的实参按 `v: T` 传入 → `E-TYPE-MISMATCH`）。裁决:

- `whenExpr` 将主题类型前移并下传 `arm()` → `pattern()`；
- `ctorSubst(subjectT, ctorName)`：`enum E[T1..Tn]` + 主题类型 `E[arg1..argn]` → 替换表 `{Ti := argi}`；
- 字段类型经 `subTpNames`（零实参具名位替换、结构递归重建）实例化后再作槽位类型；
- **嵌套 ctor 模式以所在槽位类型为题递归实例化**（`Box[c].inner` 的内层 `is Some(w)` 得到 `w: c`）。
- 主题类型不可解析（合成体）时退化为字段声明类型本身（优于无类型）。

### 验证（t33 交付）

- `:frontend:compileKotlin` 零错零警告（`.kt:\d+` 命中 0）；
- `:frontend:test --rerun-tasks` **370/0**（t30 基线 364 + `PatternBindingSemanticsTest` 6）；
- e2e 64/68（= t31 口径；残留 4 条均为 t31 既有：list/_scratch_bill/手动证明/模块系统）；
- 三形态等价各一例（直接调用 / 方法糖 / 类型限定，均走扩展桶、类型 Nat）+ 自由函数优先仍成立（同名自由函数 → E-ARITY 不回退）；
- 修前/修后对比：直接调用由 E-UNBOUND-NAME → 扩展桶解析；方法糖由合成体 `(推导:方法succ)` → 扩展桶解析（不再静默降级）。

### 裁决 R-7（t34 补测收口）：TS-8 环检的形态覆盖与正确实现

t34 补测发现 `classCycleCheck` 漏报自继承 `A : A`（seen 去重先于环判定，把首个环节点吞掉）；修复过程中的迭代式改法又引入菱形（`A : B, C` + 共同父类 D）误报（onPath 不回缩，共享父类被跨分叉误判为回环）。最终实现为**带回溯的递归 DFS**：`n == d.name` 即环（覆盖自继承/互环/长链回环），`done` 沉淀已探查无环节点（菱形共享父类正确跳过），未声明的父名不构成环（外部类型类/报错名）。诊断文本：`类型类继承链出现环（d ← pn，TS-8 循环继承报 E-CYCLE）`，每处环声明各自落 1 条（互环共 2 条）。
