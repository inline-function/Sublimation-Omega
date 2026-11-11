# ADR-32：联合成员隶属查找（R-29）+ @list 变长形参（R-30）

**任务**：t48（semantics） · **日期**：2026-11-07 · **前置**：ADR-31（R-27 别名身份分派头 / R-28 期望侧联合别名展开）、ADR-30（默认值求值环境与顺序约束）

## 一、问题陈述（修复前实测，23:39Z 探针 ProbeT48Test）

### 任务 1：联合成员隶属查找

| # | 源码 | 修复前 | 期望 |
|---|---|---|---|
| A | `struct Good()` + `type Verdict = Good \| Bad` + `fun Verdict.show(): Nat = 1` + `show(Good)` | **E-UNBOUND-NAME: 未定义的名称 show** | 按 struct 头的**联合隶属**回退到别名头 Verdict 再查 |
| B | 同上但 `var v: Verdict = Good; show(v)` | OK（t47 R-27 已通） | 保持 |
| C | `struct Ver(n: Nat)` + `type V2 = Ver \| Bad` + `show(Ver(1))` | E-UNBOUND-NAME | 带字段成员同回退 |

根因：扩展分派用实参/接收者的**静态类型头**作键。裸 ctor 调用的静态头是 struct 名（`Good`），而扩展登记在**别名头**（`Verdict`）下。t47 的 R-27 只解决了「接收者静态类型本身是展开联合」（`None \| Some[Nat]`）时的别名身份反查，**struct 头侧**未处理。

### 任务 2：`@list` 变长形参

| # | 源码 | 修复前 | 期望 |
|---|---|---|---|
| D | `fun listOf[T](@list items: T): List[T] = items`（声明本身） | **E-TYPE-MISMATCH: 函数 listOf 声明返回 Nil \| Cons[T]，但函数体是 T** | 体位 items 即序列，类型 `List[T]` |
| E | `listOf(1)` / `listOf(1,2,3)` | **E-ARITY: 只想要 1 个实参，但你传了 3 个（多了）** | 变长位吸收 0..n |
| F | `listOf()` | **E-TYPE-MISMATCH: 缺实参：items** | 空变长合法 |
| G | `fun f(@list a: Nat, b: Nat)` | 静默 OK（无诊断） | **E-VARARG-LAST** |
| H | `fun f(@list a: Nat = 0)` | 静默 OK（无诊断） | **E-VARARG-DEFAULT** |

根因：`@list` 注解由 Parser 收入 `Param.annotations`（`["list"]`，S-6 原始名逐字保留）后**语义层零解释**——形参按普通单参定型（体位 `items: T`），调用位按普通 arity 核对。

## 二、决策

- **R-29 联合成员隶属回退（Elaborator）**：
  - 新增**惰性隶属索引** `unionMembership: Map<String, LinkedHashSet<String>>`（struct 头 → 所属联合别名头集合），由 `aliasDecls`（prelude 虚拟联合别名 + 用户别名，target 为 UnionType 且成员具名）构建——**不是每次线性扫**（队长性能要求）；
  - `extHeadOf`（t47 既有件）的非联合分支扩展：直头查桶 miss（`lookupExtDeclVisible(direct, methodName) == null`）时，按 `unionMembership[direct]` 的候选别名头逐个查桶；**形状键**（`Union/N`、`Tuple/N`、`Fun/N`）不参与回退（它们自己就是登记键）；
  - **多方命中结论（队长要求）**：一个 struct 隶属多个联合且都登记了同名扩展 → 取**声明序最晚者**（`aliasDecls` 顺序 = prelude 先于用户，最晚 = 用户后声明，符合 P-7 覆盖精神），并落 **D-EXT-UNION-AMBIG** supplement 留痕（不报错——V1 禁重载、桶内至多 1 项，取最晚是确定性选择；若两签名真冲突由后续 callFun 核对报 E-TYPE-MISMATCH）；
  - 回退不是万金油：候选桶全空 → 保持原头 → 落既有 **E-UNBOUND-NAME**（反例实测）；
  - 覆盖**方法糖与方法糖外自由式两处分派位**（extHeadOf 共享）。
- **R-30 `@list` 变长形参（Elaborator + TypeInfer）**：
  - **声明位**（funDecl）：`@list` 形参须在**最后**位 → `E-VARARG-LAST`（非末位形参无法按位置供给）；不得带默认值 → `E-VARARG-DEFAULT`（变长位吸收 0..n，默认值无供给语义，与 t45 默认值通道正交）；
  - **形参双重类型**：`HirParam` 保留**元素类型** + 注解旗标 `["list"]`（D-9 信息保留，JSON 落 `annotations` 数组）；体位符号按 `List[元素]` 登记（`items` 在体内即序列）；
  - **调用位**（callFun 新增 `variadic: List<Boolean>`，instCall 新增 `varargIdx`）：变长位之前形参必填（t45 缺省口径不变）；变长位起无上限（不再报 E-ARITY 多了）；每个尾部实参按**元素类型**核对（保留实际实参个数，不合成压扁）；instCall 对尾部实参逐一与变长形参合一（首解为准，后续不一致由核对位报 E-TYPE-MISMATCH）；
  - **空变长调用**（`listOf()`）：未解理论参数回退 `syntheticT("变长空实参")`，避免自由类型名外泄污染下游比对；
  - **扩展函数**（extCallType）：receiver 占签名首位，变长位随之 +1 偏移，旗标同步下传。

## 三、反例口径（不放宽）

- `show(Good)` 无任何联合成员扩展 → **E-UNBOUND-NAME**（实测 T1e）。多方命中**不报错**（见 §二 D-EXT-UNION-AMBIG 结论）。
- `listOf(1, true)` → **E-TYPE-MISMATCH: 实参 2 Bool 与形参 Nat 不符**（变长元素类型仍按形参声明核对）。
- `fun f(@list a: Nat, b: Nat)` → **E-VARARG-LAST**；`fun f(@list a: Nat = 0)` → **E-VARARG-DEFAULT**。
- `fun f(n: Nat, @list rest: Nat)` + `f()` → **E-TYPE-MISMATCH 缺实参：n**（前序必填位不变）。非变长函数 arity 口径完全不变（`E-ARITY`/缺实参反例实测保持）。

## 四、验证（采样 00:05–00:13，构建槽位独占）

- 正式测试 `UnionMemberAndVarargTest` **14 用例 / 0 失败**：任务 1 ×7（bare ctor/typed var/带字段/多联合单命中/多联合歧义+supplement/反例 E-UNBOUND-NAME/方法糖形态）；任务 2 ×7（0/1/3 实参、元素类型反例、E-VARARG-LAST、E-VARARG-DEFAULT、前序必填位、非变长 arity 不变、JSON 变长旗标）。
- `:frontend:test --rerun-tasks`：**507 用例 / 0 失败**（t47 基线 493 +14 新；数量变化仅新增 14 条 UnionMemberAndVarargTest，无改写/删除）。结构违规 0。
- e2e：**64/68 → 65/68**，`core/collection/set.subl` 转绿（`setOf[T](@list items: T)` 分派链同 R-30）。`core/collection/list.subl` 仍红：残余 3 处 **跨模块符号**（`list.subl:81 getOrElse` ← optional.subl、`:221 add` ← set.subl、`:227 put` ← map.subl）——单文件编译期不可见，属**模块系统缺失（V1 边界）**，按队长口径如实标注、不算失败、未伪造符号。余 2 红 = 手动证明（E-CANNOT-PROVE）+ 模块系统/主（E-UNBOUND-NAME），均 V1 边界，诊断集前后一致。
- compileKotlin + compileTestKotlin 双快照（00:07 全量内嵌 + 00:13 专项）BUILD SUCCESSFUL **e=0 / w=0**（ADR-24 §五）。

## 五、改动位点

| 文件 | 改动 |
|---|---|
| `semantic/Elaborator.kt` | `unionMembership` 惰性索引（R-29）；`extHeadOf` 非联合分支隶属回退 + D-EXT-UNION-AMBIG；funDecl 声明位 E-VARARG-LAST/E-VARARG-DEFAULT（R-30）；`param()` 变长形参体类型 List[元素]；自由函数调用位 + extCallType 变长旗标下传 |
| `semantic/TypeInfer.kt` | `callFun` 新增 `variadic` 形参（变长 arity/核对/空实参 synthetic 回退）；`instCall` 新增 `varargIdx`（尾部实参与变长形参逐一合一） |

## 六、V1 边界（留档）

- `@list` 的 lambda 两遍通道（`lambdaExpectFormals`）未感知变长——变长位传 lambda 的场景（如 `forEach(...)`）未登记，回到全量 callFun 核对（不放宽）。
- 函数**值形态**（`sigFunType`/`o::f` 引用）的形参表仍是元素类型序列——变长信息在类型层面不可见（D-9 只在声明节点 + 调用点留痕）；若需类型层变长签名另立 ADR。
- struct **字段**位的 `@list` 未校验（注解静默忽略）——样例库无此用法；如需可复用 funDecl 同款诊断。
- 多方歧义取**声明序最晚**是确定性裁决而非报错——若后续语义要求显式消歧，新增 E-AMBIG 类型即可（当前结论已落 D-EXT-UNION-AMBIG supplement，可观测）。
- 变长形参的理论参数解按**首个尾部实参**绑定——`listOf[Nat]()` 显式类型实参仍优先（B8 显式优先口径），隐式空变长回退 synthetic。