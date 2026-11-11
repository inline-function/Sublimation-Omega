
# ADR-1 · HIR（高级语义树）设计

> 编号：ADR-1 —— documents/idea documents/ 首份；后续设计记录按 ADR-2、ADR-3… 追加（documents/README「idea documents」：编号连续，追加而不覆盖）。
> 状态：🟡 提案（2026-11-04，semantics 于 t5 产出），待队长审查；审查通过后方可启动 t6（S-3 §3）。
> 拍板边界：已决事项按决议执行（D-1～D-9、P-1/P-5/P-6/P-7）；P-4 在本文只给候选与推荐动机，拍板权在队长→用户（S-3 §6）。

## 一、背景

### 1.1 HIR 在管线中的位置

S-1 管线（D-3 + D-8）：代码 → 词法单元流 → AST → HIR。HIR 是前端最终产物，JSON 产物由它直接发射（D-7）；后端与解释器以同一棵 HIR 为唯一输入（D-8；P-1 已决）。HIR 因此承担三重角色：

1. **语义归宿**：名称解析、类型推导、证明检查的结论的唯一载体（S-1 §3）；
2. **前端产物**：JSON 由此发射（D-7）；
3. **下游唯一输入**：后端、解释器、（未来的）LSP 共享同一棵树——D-9 的原话是「谁的预算都不是前端的预算」。

### 1.2 旧实现的教训（字典点查 F:\KotlinProject\sublimation，未通读）

- **无语义树，结论走旁挂表**：旧 Checker 直接遍历 AST，语义结论由 15+ 张 IdentityHashMap 旁挂表（dictHits、consHits、dictSubHits、namedArgOrder、methodSugarArgs、moduleHits、deepCopyHits、asyncAwaitHits、flowEmitHits、receiveSmartHits、listMethodHits、rangeHits、hktKindPosHits、mergedStructCtor、flowStmts——compiler/.../checker/Checker.kt:89–180）传给 JsCodeGen；旧注自陈成因：CallExpr 是 data class，结构相等的调用点在普通 HashMap 下互相覆盖。这些表从不落盘。→ 教训一：**结论必须长在节点上**——这正是 D-9 要求的形态。
- **AST 原地突变**：WhenArm.pattern 声明为 var（frontend/.../ast/Ast.kt:496–504），检查器原地归一化，解析产物与检查产物不可区分。→ 教训二：**转换产新树，不嚼旧树**（层间总契约 3 的可测试、可缓存性才有意义）。
- **命题是包内私设**：Prop 家族（checker/Prop.kt）不落盘，codegen 整体擦除。→ 教训三：**命题与证明需要一等节点身份并落盘**（D-8 要求 HIR 是唯一输入）。
- **类型结论 best-effort**：inferredVars（Checker.kt:36）仅供 LSP 碰运气。→ 教训四：类型结论同样是树的字段（D-9）。

### 1.3 硬约束清单

| 条款 | 要求 | 本文落点 |
| --- | --- | --- |
| S-1 §2 | AST 零语义 | §3.1 纪律：语义字段只出现在 HIR |
| S-1 §3 | 语义分析在 HIR 构造过程中完成 | §3.5 两阶段方案 |
| S-1 §3 | 与源码结构保持映射（诊断/跳转/LSP） | §3.1 同构保序 + 受控消解 |
| S-1 §3 | 命题与证明唯一住所；程序位/命题位分家 | §3.4 |
| S-1 §3（D-9） | HIR 尽可能多保留信息 | §3.1/§3.3/§3.4/§3.7 |
| S-1 §3 | 证明检查失败即编译失败 | §3.5/§3.6 |
| S-1 层间总契约 1/2/3 | 无回望 / 诊断带位置 / 纯函数 | §3.1/§3.6/§3.5 |
| S-2 §2/§3.2/§3.3/§4 | 包结构、依赖方向、零第三方运行时依赖 | §3.2、§3.7 |
| P-6（已决） | 手写发射器、零依赖 | §3.7 |
| P-7（已决） | 与旧 checker 全对齐 | §3.2 映射表、§3.6 码集 |
| P-4（待拍板） | 诊断体系如何贯穿四层 | §3.6 候选，不拍板 |

## 二、候选方案

每个子问题给出候选；D-9（信息保留原则）是本次评估的加权项。下方引用的旧工程内部编号（决策 34/60/65/75/82/93 等）仅作点查定位，不构成新工程条款。

### 2.1 节点体系与 AST 的映射

- **A1 同构保序**：HIR 与 AST 节点一一对应、孩子序列同序；构造期消解在闭名单内进行且留痕。
- **A2 自由重构**：以「语义干净」为目标任意展开糖与临时量，树形可偏离源码。
- **A3 注解式 AST**（旧路线）：AST 节点加可写语义字段 + 旁挂表。

D-9 视角：A3 的结论落在旁挂表与可写字段上，「随节点落盘」无法兑现（旧实现从未序列化这些表）；A1 把结论做成节点不可变字段，天然可序列化。A2 失去源码映射（S-1 §3 明文要求），留痕无从回链。

### 2.2 命题与证明的唯一住所形态

- **B1 双面分家**：HIR 内部分命题面（HirProp 家族 + 证明语句节点 + 证明上下文）与程序面（计算表达式）；AST 命题位表达式在构造时归一为命题面节点。
- **B2 命题即表达式**：不专建命题层，证明检查直接在程序表达式上做。

D-9 视角：B2 没有命题节点可挂证明上下文与核对结论（Γ、匹配定理、所用规则），「证明上下文齐备」无从落地。

### 2.3 语义分析在构造过程中完成

- **C1 单趟构造**：转换即解析即推导即证明。
- **C2 两阶段**：收集（符号表 + 全局事实注册）→ 归约（逐节点构造 HIR：解析/推导/消解/证明检查）。
- **C3 骨架 + 回填**：先构造无语义 HIR，再第二趟写字段。

D-9 视角：C3 回填也能落盘，但中间态树是不可消费的半成品（与容错 AST 的定位重叠）；C1 无法支持前向引用与互相递归（旧 Symbols.kt 两遍收集：collectDecl → checkDeclTypes）。

### 2.4 诊断模型（P-4 候选）

- **D1 直移植**：Diag 五元组照搬（pos 为 "line:col" 字符串）。
- **D2 结构化**：pos 升级为 SourceRange 结构；严重级、码族、码集、袋行为全量沿用旧体系（形式化语义 09 卷）。

D-9 视角：诊断要「齐备保留」并供多种消费者复用，结构化位置是最低门槛。

### 2.5 HIR→JSON 发射方式（P-6 候选）

- **E1 kotlinx.serialization**；**E2 手写发射器**。

D-9 视角：E1 的多态判别与字段形态由库决定，「尽可能多保留」下的字段控制力与 schema 稳定性都不在手；且违反 S-2 §3.3 零依赖。

## 三、决策

### 3.1 节点体系总纪律（A1 + 受控消解闭名单）

1. **同构保序**（A1）：HirFile.entries 与 FileAst.entries 逐一对应；块的孩子序列与 AST 块同序。动机：S-1 §3「与源码结构保持映射——诊断、跳转、LSP 依赖此性质」。
2. **受控消解闭名单**（A1 对 A2 的有限让渡）：以下构造期改写允许，改写产物必须保留源位置，并在 notes 里写明「由何形态改写而来」：
   1. 方法糖 `xs.f(a)` → 自由函数形态 `f(xs, a)` 的 HirCall；
   2. 命名参数 `f(name = e)` → 按形参声明序重排的位置实参；
   3. `$n` 参数引用在声明期改写为参数名（旧决策 65）；
   4. when 解构槽位归一化为相等比较（旧 WhenArm 归一化的等价物，以**拷贝**实现，不原地改写）。
   其余重构一律不做。动机：消解越多下游越干净、回链越弱——闭名单把「可回链性」变成可审查的纪律。
3. **拒绝 A3**：旁挂表是旧实现的缺陷源（教训一），且 D-9 的「随节点落盘」在旁挂表上不可兑现。HIR 节点以不可变 data class + 全 val 落地；构造期改造一律「复制构造」（copy），不做 var 回填（教训二；层间总契约 3）。
4. **节点五件套**（D-9 的最小实现集）：每个 HIR 节点携带 `origin`（源位置）、`nid`（节点身份）、`notes`（解析期留痕），并按节点种类齐备 `symbol`（名称解析结论）与 `type`（推导类型）；证明节点另带 proofContext 与 verdict；诊断在 envelope 齐备并反引节点（§3.3/§3.6/§3.7）。

### 3.2 HIR 节点体系与 AST 映射表

包：`sublimation.frontend.hir`（S-2 §4）。根接口：

```kotlin
/** HIR 节点契约：携带源位置（S-1 层间总契约 2）与解析期留痕（D-9）。 */
sealed interface HirNode {
    val nid: NodeId            // 文件内稳定节点身份（构造期先序分配；纯函数输入→输出可复现）
    val origin: SourceRange    // 源位置（1-based 行列区间）
    val notes: List<HirNote>   // 解析期脱糖留痕与 hints/supplements（D-9）
}
```

映射表（AST 节点取自旧 Ast.kt；t3 按形式化文法重建同形节点，映射仍然成立）：

| AST | HIR | 构造期增补 / 消解 |
| --- | --- | --- |
| FileAst / DeclEntry, StmtEntry | HirFile / HirEntry | 五件套 |
| FunDecl | HirFunDecl | symbol；params/retType 类型解析；preps/posts → HirProp 列表；theory 绑定；纯度/异步结论 |
| ClassDecl | HirClassDecl | symbol；parents 类型解析 |
| ImplDecl | HirImplDecl | symbol；委托 `by field` 展开留痕 |
| StructDecl | HirStructDecl | symbol；props → HirProp |
| EnumDecl / Ctor | HirEnumDecl / HirCtor | symbol；构造子登记 |
| TypeAliasDecl | HirTypeAlias | symbol；target 解析 |
| PropDefDecl | HirPropDef | 命题定义（原子谓词 / 等价定义） |
| TypeParam / PropParam | 保留于 HirFunDecl.theory | — |
| Param | HirParam | 类型解析；默认值；解构槽位 |
| VarStmt / VarExpr | HirVarStmt / HirVarExpr | 局部 symbol；推导类型；深拷贝点位 |
| AssignStmt | HirAssign | 目标解析；可变性核对结论 |
| AxiomStmt | HirAxiom | 命题归一；proofContext + verdict（R-AXIOM 免验证注入） |
| ByStmt / ByExpr | HirBy | 断言核对结论（不注入 Γ，证不出即诊断） |
| UncheckedStmt | HirUnchecked | 逃逸边界留痕 |
| ExprStmt | HirExprStmt | — |
| BTSStmt（∵…∴） | HirBecause | 定理匹配 + 前件可证性 + proofContext + verdict |
| WhenPropStmt / WhenPropArm | HirWhenProp / 分臂 | 分支结论核对 |
| IfPropStmt | HirIfProp | 排中律两支核对 |
| ExistIntroStmt | HirExistsIntro | 见证替换核对 |
| ExistsElimStmt | HirExistsElim | ∃ 消去核对 |
| FunStmt（∀ 引入） | HirLocalFun | 局部定理登记 |
| StrategyStmt（@simp） | HirStrategy | 策略调用核对 |
| ReturnStmt | HirReturn | 发散判据（diverges，旧决策 75）结论 |
| IntLit / FloatLit / StrLit | HirIntLit / HirFloatLit / HirStrLit | 值**保留原文**（供后端 codegen） |
| NameRef | HirNameRef | symbol: SymbolRef?；未解析 → 诊断 + 半成品节点（S-1 §2 容错同构） |
| CallExpr | HirCall | callee symbol；方法糖消解；命名参数重排；字典/槽解析结论入字段（旧决策 60/82） |
| FieldExpr | HirField | 字段解析 |
| FunRefExpr | HirFunRef | 目标符号 |
| LambdaExpr / AnonFunExpr | HirLambda / HirAnonFun | 推导函数类型（含 theory/context）；闭包纯度结论 |
| TupleExpr | HirTuple | — |
| StructCtorExpr | HirStructCtor | 字段对齐核对 |
| BlockExpr | HirBlock | tailExpr 计算 |
| IfExpr | HirIf | 分支类型核对 |
| WhenExpr / WhenArm | HirWhen / 分臂 | pattern 拷贝归一化；穷尽性核对结论 |
| TypeTestExpr | HirTypeTest | 智能转换点位留痕 |
| TaskExpr / FlowExpr | HirTask / HirFlow | 效应登记（异步运行时注入位） |
| IsPatExpr | ——（无） | 解析期独有过渡节点，parseIf 立即脱糖（形式化文法）；HIR 不出现 |
| 命题位表达式 → 命题面 | | |
| BinExpr(→/↔/∧/∨) | HirPImp / HirPIff / HirPAnd / HirPOr | P-IMP 等归一 |
| UniExpr(¬) | HirPNot | — |
| QuantExpr(∀/∃/∃!) | HirPForall / HirPExists / HirPExists1 | 命题位归一；程序位不出现 |
| NameRef / InstExpr / SymbolCallExpr / CallExpr（命题位） | HirPAtom(head, terms) | 0 元 / 参数化 / 比较 / 两参解糖 |
| ReturnSym / ParamRefExpr / TopExpr / BotExpr（命题位） | HirPAtom("$") / ("$n") / HirPTop / HirPBot | 无程序面形态 |
| 命题项（PAtom 的 terms） | HirTerm 家族 | 与程序面分家；结构相等（P-STRUCT-EQ） |
| Pattern 家族 | HirPattern 同形家族 | 归一化**拷贝**；绑定符号解析 |
| NamedType / FunType | HirNamedType / HirFunType | 符号指向；`context` → HirProp 列表 |
| TupleType / MutType / AtomicType / UnionType | HirTupleType / HirMutType / HirAtomicType / HirUnionType | — |
| QualifiedType | HirQualifiedType | 本期单文件（S-2 §2）：形态保留、模块折叠留给后期的模块挂载任务 |

命题面（唯一住所，§3.4）：`HirPTop / HirPBot / HirPNot / HirPAnd / HirPOr / HirPImp / HirPIff / HirPForall / HirPExists / HirPExists1 / HirPAtom(head, terms: List<HirTerm>)`，命题项 `HirTerm` 家族：`HirTInt / HirTStr / HirTName / HirTDollar / HirTParamRef / HirTTop / HirTBot / HirTBin / HirTUni / HirTCall / HirTInst / HirTField / HirTSymbolCall`。

### 3.3 信息保留清单（D-9 落实）

每棵 HIR 满足「五件套齐备」：源位置 / 解析结论 / 推导类型 / 证明上下文 / 诊断。「保留什么信息、谁消费」逐项如下；丢弃项另见 §3.8 闭名单。

| # | 保留信息 | 载体（节点字段） | 消费者 | 依据 |
| --- | --- | --- | --- | --- |
| 1 | 源位置（行列区间） | 所有节点 origin | 诊断呈现、跳转、LSP 定位、后端 sourcemap 预留 | S-1 层间总契约 2；D-9 |
| 2 | 节点身份 | 所有节点 nid | 诊断↔节点绑定、LSP 节点定位、产物 diff | D-9 |
| 3 | 解析期留痕（脱糖 / hints / supplements） | 所有节点 notes | LSP hover、教学提示（H 族）、审计（D 族）、CLI -v | D-9；旧体系 H-ARITH-PRECEDENCE 等 |
| 4 | 名称解析结论 | 引用类节点 symbol | 跳转 / 重命名（LSP）、后端 mangle、解释器符号寻址 | S-1 §3；D-9 |
| 5 | 推导类型 | 程序面表达式节点 type | 后端擦除决策、解释器运行时检查、LSP hover | D-9 |
| 6 | 命题与证明上下文 | 证明节点 proofContext / 命题面节点 / preps·posts·props | 解释器断言落地、后端断言生成、LSP 证明状态、教学 | S-1 §3；D-9 |
| 7 | 诊断（全量） | envelope diagnostics（带 nid 反引） | CLI、LSP、QA 回归 | S-1 层间总契约 2；D-9；09 卷 |
| 8 | 字面量原文 | 值节点 value | 后端 codegen、解释器值构造 | 旧 FloatLit 注释动机同源 |
| 9 | 控制流判据 | HirReturn / HirIf / HirWhen 的发散与展平字段 | 后端语句展平 | 旧决策 75 |
| 10 | 字典 / 槽 / 方法糖解析结论 | HirCall 相应字段 | 后端字典传递 | 旧决策 60/82 |
| 11 | 穷尽性 / 证明核对结论 | 分臂与证明节点 verdict | 后端兜底生成、LSP | P-7 对齐 |
| 12 | 纯度 / 异步效应 | 声明与调用节点 purity / async 字段 | 后端 await 注入、解释器放行 | P-PURE（旧决策 34）；旧决策 93 |
| 13 | 深拷贝 / 智能转换点位 | 绑定 / 赋值 / 分支节点标志 | 后端 __dc 注入、解释器拷贝规则 | 旧 root doc 拷贝 4 条 |

### 3.4 命题与证明的唯一住所（B1）

- **唯一住所的三条判据**（可检验口径，供 t6/t9 落实）：
  1. AST 无命题类型节点（零语义，S-1 §2）；json 包不引入第二个命题表示——JSON 只有序列化 HirProp（S-2 §3.2：json → hir）；
  2. `→` 蕴含在 HIR 仅以 HirPImp 存在；AST 的 `BinExpr("→")` 出现在命题位时归一（P-IMP）；程序位箭头是 `=>`，与 `→` 不同源（形式化文法：命题位 →、程序位 =>、`->` 全位非法）；
  3. axiom / ∵…∴ / by / 命题 when / if[p] / ∃ 引入消去 / @simp 只以命题面节点存在。
- **证明上下文保留**（D-9）：每个证明义务节点（HirAxiom / HirBy / HirBecause / HirWhenProp / HirIfProp / HirExistsIntro / HirExistsElim / HirStrategy）携带：
  - `proofContext.facts`：核对时刻作用域内的事实集（P-FRAME 沿父链本帧事实 ∪ 全局事实 P-PURE 等的归一化并集，去重保序）；
  - `proofContext.matched`：匹配到的定理符号引用（∵∴ 的定理空间匹配）；
  - `rule`：discharge 所用规则编号（R-MP / R-MT / R-CONTRA / R-FORALL-INST / R-EXISTS-MATCH / R-AXIOM / 算术通道 A- 族）；
  - `verdict`：`{ status: proven | failed, diagCode: String? }`。
  Γ 的**搜索过程状态**（闭包、visited、实例化矩阵）不入树——见 §3.8 丢弃项 1。
- **命题项位置盲**（P-STRUCT-EQ）：HirTerm / HirProp 的相等按结构（data class 自带相等须显式忽略 origin/nid/notes，t6 实现）；位置只作呈现元数据。这是 HIR「带位置」与命题「位置盲」的共存方案：位置用于诊断呈现，结构用于事实匹配。
- 命题面在 JSON 产物中**保留**（D-9：不提前丢弃）；擦除命题是后端的决定，不是前端的。

### 3.5 构造方案（C2 两阶段，semantic 包）

- **阶段一 collect**：扫 AST 声明建符号表（函数 / 类型 / 类型类 / 构造子 / 字段 / 命题定义 / 局部变量分层符号表，SymbolId 体系），注册全局事实（pure<f> 等，P-PURE；旧决策 34）。两遍结构沿用旧 Symbols.kt（collectDecl → checkDeclTypes）以支持别名前向引用。
- **阶段二 elaborate**：以 FileAst + 符号表 + 解析期 notes 通道为输入，深度遍历构造 HIR：名称解析（NameRef → SymbolRef；未解析名落诊断 + 半成品节点）、类型推导（旧 TypeInfer 主干移植，结论写 type 字段）、糖消解（§3.1 闭名单）、命题归一与证明检查（per-frame Γ；R-NO-GUESS 底线）。
- **纯函数边界**：`elaborate(file, symbols, parseNotes): Pair<HirFile, DiagBag>`（层间总契约 3）；AST 与符号表只读。
- **notes 搬移**：parser 的输出是 `ParseResult(file, notes, diag)`——notes 为「AST 节点 → 留痕列表」的旁挂通道，不进 AST 字段（保 S-1 §2 零语义读法的洁净）；构造期按节点对应搬到 HIR 节点的 notes 字段落盘（D-9：解析期脱糖留痕随节点落盘）。
- **证明检查失败即编译失败**：核对 failure 以 ERROR 落袋（S-1 §3 契约；R-NO-GUESS：Γ ⊬ P 绝不降级为成立）。

### 3.6 诊断模型（D2，P-4 候选）

```kotlin
enum class Severity { ERROR, WARN, HINT, SUPPLEMENT }        // 09 卷 §2.1
data class SourcePos(val line: Int, val col: Int)             // 1-based
data class SourceRange(val start: SourcePos, val end: SourcePos?)
data class Diag(
    val severity: Severity, val code: String,
    val pos: SourceRange, val message: String,
    val modulePath: String = "", val nid: NodeId? = null,     // 反引 HIR 节点（D-9）
)
```

- **沿用项**（P-7 全对齐）：四级严重级与渲染标签（09 卷 §2.1）；码族 E/W/D/H 与旧码集（旧实测 109 码）；阻断性由严重级决定而非码族字母（N-SEV-BLOCK）——含旧体系「E 码以 WARN 发射」的既定位点（E-PROP-UNBOUND 等），回归须逐点对齐；DiagBag 行为（absorb / dedupe / report(includeSupplement, source) / hasError）。
- **升级项**：pos 由字符串 "line:col" 升级为 SourceRange（含可选 end）；渲染输出保持旧格式「[错误][1:2] CODE: msg」与源码片段上下文（回归兼容）；envelope 结构化（供 JSON / LSP 消费）。
- **贯穿**（层间总契约 2）：lexer 错误 token（S-1 §1「非法字符不停机」）、parser 容错（S-1 §2）与 HIR 构造期诊断同袋。
- **拍板位**：D2 是给 P-4 的候选与推荐动机；决议权在队长→用户（S-3 §6）。另注：t6 任务书述「E-/W-/D- 三族」，旧体系实测**四族**（含 H 族 7 码）——按 P-7 建议全对齐四族；若队长有意裁剪请拍板。

### 3.7 HIR→JSON 目标 schema 草案 v1（E2 手写，P-6 决议）

- **决策**：手写发射器（P-6 已决：手写、零依赖，S-2 §3.3）。权衡对照（P-6 决策录据）：

| 维度 | kotlinx.serialization（E1） | 手写发射器（E2） |
| --- | --- | --- |
| 运行时依赖 | 需要 serialization-runtime + 编译器插件 | 零（StringBuilder + 转义工具） |
| sealed 多态与字段形态 | 库决定（判别键、null 策略） | kind 判别 + 字段完全自控（D-9 友好） |
| schema 稳定性 | 随库版本变化 | 自控，根带 schema 版本字段 |
| 位置 / 符号 / 留痕混排 | 需 custom serializer | 直接写 |
| 转义与深递归 | 库保证 | 需单测覆盖（转义、控制字符、深树） |
| 代码量 | 少 | 多（≈ 节点数 × emit 分派） |

- **schema 草案 v1**（t10 实现期可微调；breaking 变更须追加新 ADR）：
  - 根 envelope：`{ "schema": "sublimation/hir@v1", "compiler": {"version"}, "unit": {"path","mode"}, "ok": bool, "diagnostics": [...], "hir": {...} }`；
  - 节点统一形态：`{"nid": 7, "kind": "…", "pos": "l:c-l:c"（单点 "l:c"）, "notes": […], …语义字段}`；
  - notes 形态：`{"kind": "hint" | "supplement" | "desugar", "code": "…", "message": "…", "pos": "…", "from": "原形标签", "to": "目标形态标签"}`（desugar 留痕沿用脱糖规则编号，如 D-INTP）；
  - 符号引用：`{"id": 2, "kind": "fun" | "param" | …, "name": "…"}`（id 权威，name 冗余可读）；
  - 类型：表达式节点 "type" 字段；推导失败置 null 且对应诊断在 envelope；
  - 证明节点：`"proofContext": {"facts": [HirProp…], "matched": SymbolRef?}, "rule": "R-MP", "verdict": {"status": "proven" | "failed", "diagCode": null}`；
  - 诊断：`{"severity": "…" | "code": "E-…", "pos": "…", "message": "…", "module": "", "nid": 12}`；
  - 字面量保留原文（"value": "100"）——后端 codegen 依赖原文（旧 FloatLit 注释动机同源）。

- **示例 A**（bounded.subl，命题注入节点展示 proofContext）：

源码：
```subl
fun bounded(n: Nat) : Nat {
  axiom n ≤ 100
  n
}
```

产物（为版面紧凑，重复结构以 … 省略，省略号不是 schema 的一部分）：

```json
{
  "schema": "sublimation/hir@v1",
  "compiler": { "version": "0.1-SNAPSHOT" },
  "unit": { "path": "bounded.subl", "mode": "project" },
  "ok": true,
  "diagnostics": [],
  "hir": {
    "kind": "HirFile", "nid": 1, "pos": "1:1-4:1", "notes": [],
    "entries": [
      {
        "kind": "HirFunDecl", "nid": 2, "pos": "1:1-4:1", "notes": [],
        "name": "bounded",
        "symbol": { "id": 1, "kind": "fun", "name": "bounded" },
        "annotations": [], "theory": [],
        "params": [
          { "name": "n", "nid": 3, "pos": "1:13-1:14",
            "type": { "kind": "HirNamedType", "nid": 4, "pos": "1:16-1:19", "notes": [],
                      "name": "Nat", "args": [],
                      "symbol": { "id": 0, "kind": "builtin", "name": "Nat" } } }
        ],
        "preps": [],
        "retType": { "kind": "HirNamedType", "nid": 5, "pos": "1:23-1:26", "notes": [],
                     "name": "Nat", "args": [],
                     "symbol": { "id": 0, "kind": "builtin", "name": "Nat" } },
        "posts": [],
        "purity": "pure",
        "body": {
          "kind": "HirBlock", "nid": 6, "pos": "1:27-4:1", "notes": [],
          "stmts": [
            {
              "kind": "HirAxiom", "nid": 7, "pos": "2:3-2:15", "notes": [],
              "props": [
                { "kind": "HirPAtom", "nid": 8, "pos": "2:9-2:15", "notes": [],
                  "head": "<=",
                  "terms": [
                    { "kind": "HirTName", "nid": 9, "pos": "2:9-2:10", "notes": [],
                      "name": "n", "symbol": { "id": 2, "kind": "param", "name": "n" } },
                    { "kind": "HirTInt", "nid": 10, "pos": "2:13-2:15", "notes": [], "value": "100" }
                  ] }
              ],
              "proofContext": {
                "facts": [
                  { "kind": "HirPAtom", "nid": 11, "pos": "1:5-1:12", "notes": [],
                    "head": "pure",
                    "terms": [ { "kind": "HirTName", "nid": 12, "pos": "1:5-1:12", "notes": [],
                                 "name": "bounded",
                                 "symbol": { "id": 1, "kind": "fun", "name": "bounded" } } ] }
                ],
                "matched": null
              },
              "rule": "R-AXIOM",
              "verdict": { "status": "proven", "diagCode": null }
            },
            {
              "kind": "HirExprStmt", "nid": 13, "pos": "3:3-3:4", "notes": [],
              "expr": {
                "kind": "HirNameRef", "nid": 14, "pos": "3:3-3:4", "notes": [],
                "name": "n",
                "symbol": { "id": 2, "kind": "param", "name": "n" },
                "type": { "kind": "HirNamedType", "nid": 15, "pos": "1:16-1:19", "notes": [],
                          "name": "Nat", "args": [],
                          "symbol": { "id": 0, "kind": "builtin", "name": "Nat" } }
              }
            }
          ]
        }
      }
    ]
  }
}
```

- **示例 B**（∵∴ 证明链节点，展示 verdict / matched / rule）：

```json
{
  "kind": "HirBecause", "nid": 21, "pos": "3:3-3:35", "notes": [],
  "form": { "kind": "HirPForall", "nid": 22, "pos": "3:4-3:17", "notes": [],
            "names": ["n"],
            "body": { "kind": "HirPAtom", "nid": 23, "pos": "3:10-3:16", "notes": [],
                      "head": ">",
                      "terms": [ { "kind": "HirTName", "nid": 24, "pos": "3:10-3:11", "notes": [],
                                   "name": "n", "symbol": { "id": 2, "kind": "param", "name": "n" } },
                                 { "kind": "HirTInt", "nid": 25, "pos": "3:15-3:16", "notes": [],
                                   "value": "0" } ] } },
  "premises": [ "…同构命题…" ],
  "conclusion": [ "…同构命题…" ],
  "proofContext": {
    "facts": [ "…作用域事实快照（含 matched 定理的实例化形态）…" ],
    "matched": { "id": 5, "kind": "theorem", "name": "allPositive" }
  },
  "rule": "R-MP",
  "verdict": { "status": "failed", "diagCode": "E-CANNOT-PROVE" }
}
```

- **示例 C**（解析期脱糖留痕，D-9）：

```json
{ "kind": "desugar", "code": "D-INTP",
  "message": "字符串插值脱糖为拼接调用",
  "pos": "1:20-1:34",
  "from": "StrLit(插值)", "to": "HirCall(concat, …)" }
```

### 3.8 丢弃项闭名单（D-9 的反面：每项写明动机）

| # | 不保留的东西 | 动机 |
| --- | --- | --- |
| 1 | Prover 搜索簿记（visited / seenSubs / 轮次计数 / 实例化矩阵 / 深度截断状态） | 判定算法的运行时状态，不是程序的信息；截断可观测性已由诊断通道承担（E-DEEP-SATURATION 级提示）；可重现性由层间总契约 3（纯函数）与回归测试保证 |
| 2 | 类型推导定点迭代的中间态（合成占位类型） | 算法内部状态；落盘的是收敛后的最终类型（§3.3 #5） |
| 3 | Γ 归一化前的原始注入序列 | 归一化并集无信息损失（P-NORM）；proofContext 已保留去重保序的事实集与来源节点引用 |
| 4 | token 流与 trivia（空白 / 注释原文） | S-1 分层：HIR 是第 3 层，只依赖 AST 输出（层间总契约 1）；源码原文经 origin 可回链；注释 / trivia 的保留是 LSP 的独立职责（本期暂缓，S-2 §1） |
| 5 | 脱糖前子树的完整副本 | 留痕以 notes 承载（原形标签 + 源位置 + 目标形态），源码原文经 origin 可重建；整树副本是纯冗余且引入一致性风险 |

除上述五项外不丢弃任何上游信息；新增丢弃项须先修订本 ADR（追加 ADR，不覆盖，documents/README）。

## 四、后果

### 4.1 正向

- 语义结论全在树上：旁挂表消灭（同构污染、原地突变两类旧病根除）；纯函数转换可测试、可缓存（层间总契约 3）。
- 信息保留齐备（D-9）：后端 / 解释器 / LSP 各取所需；诊断与留痕可审计。
- 命题一等公民且落盘：D-8「唯一输入」有了物质基础；擦除与否是后端的权限。
- AST 零语义可独立单测（t3 的交付边界与 HIR 解耦）。

### 4.2 成本与风险

- 节点规模 ≈ 70 个 sealed 节点（命题项家族分裂是「分家」的代价）；KDoc 契约注释负担（S-4）。
- JSON 体积：proofContext 与 facts 内嵌会放大产物（D-9 接受此预算；v2 可考虑 facts 按 nid 参照去重）。
- 保序同构与消解的纪律风险 → 闭名单 + notes 留痕作为审查项。
- 手写发射器：转义 / 控制字符 / 深递归需单测覆盖（t10）。
- P-7 全对齐的回归成本：旧 109 诊断码 +「E 码 WARN 发射」位点逐点对齐（t6/t9）。

### 4.3 开放项

- P-4 拍板（D2 与码族口径：四族 vs 三族）。
- schema v1 细节（pos 区间紧凑串 vs 结构、facts 参照化、unit.sha256）在 t10 定稿。
- SymbolId 方案（构造期序号 vs 路径式）由 t6 定。

### 4.4 下游输入契约（交付清单，S-3 §3）

- **t6**：节点清单（§3.2）、五件套字段（§3.1.4）、诊断模型（§3.6）。
- **t7**：两阶段边界（§3.5）、符号表形态、消解闭名单（§3.1.2）、notes 通道。
- **t9**：命题面判据（§3.4）、证明上下文与 verdict 形态、丢弃项闭名单（§3.8）。
- **t10**：schema v1（§3.7）、发射方式 E2（P-6）。

### 4.5 本 ADR 对 t5 任务要求的对照

| 任务要求 | 落点 |
| --- | --- |
| 节点体系与 AST 的映射 | §3.1 / §3.2 |
| 命题与证明的唯一住所形态（S-1 §3） | §3.4 |
| 名称解析 / 类型推导 / 证明检查在构造中完成 | §3.5 |
| 诊断模型（S-1 层间总契约 2） | §3.6（P-4 候选） |
| HIR→JSON schema 草案 + P-6 两候选权衡 | §3.7 |
| D-9：保留信息清单与丢弃动机 | §3.3 / §3.8 |
| P-7：与旧 checker 全对齐 | §3.2 映射表、§3.6 码集 |
