# ADR-27：名解析与遮蔽——顶层唯一 / 局部后遮蔽前 / 局部扩展函数 / H-SHADOW / 满足判定可见集

**任务**：t39（semantics） · **日期**：2026-11-06 · **前置**：ADR-26（t38 扩展桶键规范化与禁止重载，复用 ExtFunHeads 键函数与 (头, 名字) 判重）

## 一、背景与用户裁决

旧实现（字典点查，非同构）与 t38 前的现状存在三处偏差：
1. 局部同帧重名**静默覆盖**——行为方向正确但无任何遮蔽提示（全仓无 SHADOW 类诊断）；
2. `extFuns` 是**全局单表**、无帧链——块内声明的扩展函数会被错误登记为全局可见；
3. 脚本模式顶层 `var` 无判重（后写静默覆盖前者）。

**用户裁决（2026-11-06 13:58 + 15:30 补充）**：
> 我们不允许同域有同名的自由函数，不允许同域有相同接收者类型以及相同名称的扩展函数，但是可以遮蔽。嵌套域同名则里面的遮蔽外面的。内层 fun f(a: Str) 遮蔽外层 fun f(a: Nat) 后，内层 f(0) 是非法的。
> 扩展函数理应可以局部声明，而且这是我们语言的核心特性。在同域局部允许同名函数和同名变量，后面的遮蔽前面的，只是不允许顶层的自由变量或者函数重名。
> （15:30 补充）关于脚本模式，是允许顶层声明 var 的。

## 二、规则表（最终）

| 域 | 自由函数 / 变量 | 扩展函数 |
|---|---|---|
| 工程模式顶层 | 唯一，重名 E-DUP-DECL | 同 (接收者头, 名字) 唯一，E-DUP-EXT |
| 脚本模式顶层 | `var` 允许声明；同名（var↔var / var↔fun）仍须唯一 → E-DUP-DECL | 同上 |
| 局部同域 | **允许**重名，**后声明遮蔽先声明**（不报错，落 H-SHADOW） | 同键唯一；不同键共存 |
| 跨层 | 内层按**名字**整体遮蔽外层，**不回退** | 内层按 **(头,名字)** 键遮蔽，**异键共存** |

## 三、决策

### R-10：顶层唯一性（分模式）
- **工程模式**：顶层只能写声明（解析层 `noTopLevelEntry` 既有拦截，**复用不回归**）；顶层自由函数唯一由 `declareFun` 落 E-DUP-DECL（既有）。
- **脚本模式**：顶层 `var` **允许**（用户裁决），但顶层自由变量与函数**同域唯一**：`declareScriptTopVar` 检查顶层隐式帧的同名 var 与 `funs` 分表（Collector 登记的顶层 fun），命中即返回 null，调用方落
  `E-DUP-DECL`「顶层名称 x 重复声明（脚本模式顶层自由变量与函数不允许重名）」。
- 实现：`file()` 的顶层条目统一包一层 `symbols.frame(ScopeKind.SCRIPT_TOP)`——**帧种类即「我在脚本顶层」的判据**（块/函数体另推 BLOCK/FUNCTION 帧）；`isScriptTopFrame()` 查询。工程模式不会出现顶层 StmtEntry，该帧恒空，无副作用。

### R-11：局部同域允许重名，后遮蔽前
- `declareLocal` 的 `put` 语义（**后写覆盖**）正是要的行为——保持，加**遮蔽探测**：
  - 同帧既有同名绑定 → H-SHADOW「同域（line:col）先声明」；
  - 帧链外层或全局（`lookupValueOuter`，跳过当前帧）同名 → H-SHADOW「外层 / 全局（line:col）」。
- 解析侧 `lookupValue` 帧链内→外、命中即返回（不回退）——既有机制零改动即满足「解析到最近一次声明」。

### R-12：局部扩展函数（核心特性，帧链承载）
- `Scope` 增帧槽 `extFuns: (接收者头, 名字) → (符号条目, AST 声明)`——**帧退即消失**，复用既有 `frame()` push/pop，无独立生命周期（裁决点 4）。
- 登记 `declareLocalExtFun`：同帧同键 → 返回 [既有位, 本位]（调用方**两处报 E-DUP-EXT**）；外层帧/全局同键 → H-SHADOW；**异键不受影响**。
- 可见集查询（替代 t38 的全局单表通道）：
  - `lookupExtFunVisible / lookupExtDeclVisible`：帧链内→外按 (头,名字) 键命中即返回、不回退，全局兜底；
  - `hasExtFunVisible`（TS-14 直接调用前置判定）：任一可见帧或全局存在该名即真；
  - `declareExtFun`（Collector 通道）附带 AST 声明入全局索引 `extFunDecls`——**签名核对与满足判定的唯一 AST 来源**，Elaborator 的预扫描索引随之删除（单一来源，不再双索引）。
- 与 ADR-26 R-8/R-9 的衔接：键规范化函数 `ExtFunHeads.ofAst/ofHir` **原样复用**；「禁止重载」仍在**域内**成立（同域同键至多 1 个声明——跨域只有遮蔽、不是重载）。

### R-13：遮蔽提示 H-SHADOW（hint 族）
- `SymbolTable.shadowHintSink`——由拥有 diag 的驱动（`Semantic.compile`）注入，声明方统一发射：
  `这里的 x 遮蔽了{同域/外层/全局}（line:col）的同名{函数/变量/扩展函数}：本作用域内只会解析到这里的声明，外层同名者不可见（不回退）。`
- **severity = HINT**（DiagBag 断言守护，见 ShadowAndScopingTest）。遮蔽合法、不报错，提示仅为留痕。

### R-14：方法糖四通道皆无 → E-UNBOUND-NAME
- 修前：方法糖 `x.f()` 在扩展桶未命中时**静默降级为合成类型**（`syntheticT("方法f")`）——局部扩展退帧后块外调用也不报错，与「局部扩展是按作用域的核心特性」直接矛盾。
- 修后：`ext == null && builtinSig == null && 自由函数无该名 && prelude 值方法无该名 && 无命名实参` → `E-UNBOUND-NAME`（保留合成类型维持半成品树）。
- 边界（两条既有合法通道保留）：
  - TS-12③ 的**自由函数 receiver 入首参位**方法糖（`fun show(self: Nat)` ↔ `n.show()`）——`symbols.lookupValue(c.name) != null` 时不报；
  - **prelude 值方法通道**：`preludeMethod(接收者头, 方法名)`——Task（start/join/isAlive）/Channel（send/receive/close/isClosed）等异步内建泛型的**对象方法**。旧实现（CallCheck:224-251，P-7 字典点查）在方法糖之前特判：它们是 codegen/运行时生成的对象字段（天然生成 `t.start`/`ch.send`），语义层只认「接收者头 + 方法名」即合法通道。e2e 验证：`异步与并发`/`日常-异步任务` 两样本回归转绿（t43 收口时这两个样本因本通道缺失而新红）。

### R-15：满足判定的可见集口径（TS-19）
- `extSpecMatch` 改用 `lookupExtDeclVisible`：判定基于**调用点可见的扩展函数集合**（帧链内→外按键遮蔽后的可见集 + 全局其余键）。保留性质：**同一域内声明顺序无关**（可见集是集合语义）。
- **「同一类型在不同词法位置满足性可以不同」**——示例（测试 `satisfyVisibilityDiffersByLexicalPosition`）：

```
class Named { fun name(self): Str }
fun Nat.name(): Str = "nat"
fun combine[T: Named](a: T, b: T): T = a

fun probe(): Str {
    fun Str.name(): Str = "local"     // 局部扩展：Str 满足 Named
    var a = combine("x", "y")          // T=Str：调用点可见集含局部 Str.name → 满足，无诊断
    "p"
}
var b = combine("x", "z")              // 块外：可见集只有全局 Nat.name → E-TC-NOT-SATISFIED
```

- 这是**局部扩展函数的正当结果**、不是缺陷：可见性是词法性质（声明按作用域），满足判定是调用点性质——二者的接口正是可见集。全局单表时代 Str 在任何位置都不满足；t39 后「Str 满足 Named」在 probe 体内成立、体外不成立，与 R-12 的遮蔽语义完全一致。

## 四、落地范围
- SymbolTable.kt：Scope 帧槽、LocalExtFun、shadowHintSink、declareScriptTopVar、declareLocalExtFun、可见集三查询、isScriptTopFrame。
- Elaborator.kt：顶层 SCRIPT_TOP 帧、FunStmt 局部扩展分支、方法糖/直接调用/前置判定/满足判定六处换可见集、预扫描 ext 索引删除、E-UNBOUND-NAME 新通道、VarStmt 顶层唯一判定。
- Collector.kt：declareExtFun 附 AST 声明。
- Semantic.kt：H-SHADOW 注入。
- 测试：ShadowAndScopingTest（13 例：任务 1~6 正反例 + H-SHADOW severity/格式 + TS-19 满足可见集）。

## 五、验证（采样 2026-11-06T11:39Z）
- 验收构建槽位独占（S-3 §8.3）；compileKotlin **双快照**（11:35Z / 11:37Z）BUILD SUCCESSFUL，e/w 零命中（含 syntax 报的测试侧 redundant-else 警告已消）。
- compileKotlin + compileTestKotlin：BUILD SUCCESSFUL，e/w 零命中（测试侧两处 HirEntry 密封类 redundant-else 警告已消）。
- :frontend:test --rerun-tasks：**418 用例 / 18 失败**——恰为 t40 阶段基线（t41 领地）逐条一致（PatternBindingSemanticsTest 6、SemanticTest 4、TypeInferTest 4、LambdaInferenceTest 2、StructCtorTest 1、WhenExhaustivenessTest 1），新增 13 例全绿，**零回归**。
- 方法糖新 E-UNBOUND-NAME 通道曾误伤 TS-12③ 自由函数方法糖（methodSugarDesugars）——以 `lookupValue != null` 守护修复，回归验证通过。
- e2e：**47/68 = t40 基线**（结构性违规 0；21 红 = 19 enum 驱动 + 手动证明 + 模块系统/主，逐条与 t40 一致）——`异步与并发`/`日常-异步任务` 两位经 R-14 prelude 值方法通道修复后回归转绿，**无新增红**。

## 六、教训
- 语义层工具坑再受：测试 XML 自闭合 testcase 的正则配对会错配失败归属（本轮复现一次，已按「切 testcase 标签 + 自闭合判别」纠正）。
- 表达式体函数禁止 `return`——FunStmt 分支重写为 if/else 表达式形态（两次编译错误均为该形态的语法约束）。
