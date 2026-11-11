package sublimation.frontend.hir

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange

/**
 * HIR 表达式层级（S-31~S-40 映射，ADR-1 §3.2）：程序面值空间。
 * 使用点节点携带推导类型到使用点（ADR-1 §3.3 #5）与名称解析结论；
 * 命题位语法在 AST 中复用的表达式节点（如命题位 CallExpr）在 HIR 一律落到
 * [HirProp]/[HirTerm]——程序面不再有量词/蕴含节点；⊤/⊥ 例外落 [HirTop]/[HirBot]
 * （S-34 初等项合法、旧实现程序位类型化 Bool；命题位仍归 HirPTop/HirPBot，ADR-19 §C1）。
 * AST 的命题 QualExpr/TopExpr/BotExpr/BinExpr/UniExpr/CallExpr/InstExpr/SymbolCallExpr/
 * ReturnSym/ParamRefExpr 混形由构造层按语法位置归一到对应面。
 */
sealed interface HirExpr : HirNode

/** S-31 整数字面量（原文保留，大数由算术通道解析）。 */
data class HirIntLit(
    val value: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-31 浮点字面量。 */
data class HirFloatLit(
    val value: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-31 字符串字面量。 */
data class HirStrLit(
    val value: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-34 ⊤（程序面 Bool 字面量）。
 *
 * 补绘说明（ADR-19 §C1；同 [HirBinExpr] 补绘先例）：文法 S-34 初等项显式含 ⊤/⊥，
 * 旧实现 Checker.kt:1006 于程序位类型化为 Bool（命题位归 PropNormalizer → HirPTop）。
 * ADR-1 §3.2 映射表原未列程序面 ⊤/⊥ 落点，本节点为 t20 按文法补齐。
 */
data class HirTop(
    val type: HirType,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-34 ⊥（程序面 Bool 字面量；同 [HirTop]，ADR-19 §C1）。 */
data class HirBot(
    val type: HirType,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-34 名字引用：symbol=名称解析结论，type=使用点推导类型。 */
data class HirNameRef(
    val name: String,
    val symbol: SymbolRef? = null,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-31 双目运算（程序面）：op 为运算符原始文本（ASCII/Unicode 等价归一在命题层，§1.2 注 5）。
 *
 * 补绘说明（ADR-9 §契约补绘）：ADR-1 §3.2 映射表原只列命题位 BinExpr（→ 命题面连接词/原子），
 * 遗漏程序面 BinExpr——解析器算术与比较一律产出 AST BinExpr（t4），程序面无落点则最常见的
 * 表达式不可表示。本节点与 [HirUniExpr] 为 t7 补齐，JSON 发射分支同步落（t10 协调过）。
 */
data class HirBinExpr(
    val op: String,
    val left: HirExpr,
    val right: HirExpr,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-32 一元运算（程序面）：- / ! / ¬ / not（命题位一元归一为 HirPNot）。 */
data class HirUniExpr(
    val op: String,
    val operand: HirExpr,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * R3 S6 t101【借用】：&v 的 HIR 承载（零语义中间态）——type 透传内层类型，不做视图推导；
 * 可写性/子类型/拷贝分派（R-01 want 合成 @ref → t99 copyDispatch）是后续阶段。
 */
data class HirBorrow(
    val operand: HirExpr,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-33 调用：消解后统一形态——方法糖与命名参数重排在构造期完成（ADR-1 §3.1 闭名单），
 * 重排/改写留痕于 notes（D-9）。
 * 解析结论字段（ADR-1 §3.3 #10）：symbol=callee 解析结论；
 * dictionary=特质字典名（旧决策 60）；slot=约束槽名（旧决策 82）；
 * dictArgs=字典实参；methodSugar=方法糖来源标记（后端生成风格选择）；
 * awaitPoint=@async 挂起点（后端 await 注入）；calleeImpure=被调者效应结论。
 */
data class HirCall(
    val callee: HirExpr,
    val args: List<HirExpr> = emptyList(),
    val symbol: SymbolRef? = null,
    val type: HirType? = null,
    val dictionary: String? = null,
    val slot: String? = null,
    val dictArgs: List<String> = emptyList(),
    val methodSugar: Boolean = false,
    val awaitPoint: Boolean = false,
    var calleeImpure: Boolean = false,   // t122（R-16 A2/OQ-7=T）：阶段 2 闸回写真化（被调侧结论）
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-33 字段访问：field=字段解析结论，type=使用点推导类型。 */
data class HirField(
    val target: HirExpr,
    val name: String,
    val field: SymbolRef? = null,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-33 引用（o::f）：构造子复合名的折叠在解析层完成。 */
data class HirFunRef(
    val target: HirExpr,
    val name: String,
    val symbol: SymbolRef? = null,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-31 中缀 is：a is T 运行时类型测。
 * smartCast=智能转换点位标记（ADR-1 §3.3 #13：ifis/when 模式后的智能转换留痕，
 * 旧实现 smartCastHits 的节点级替代）。
 */
data class HirTypeTest(
    val target: HirExpr,
    val type: HirType,
    val smartCast: Boolean = false,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-33 Task 块（闭包语义的语句体）。 */
data class HirTask(
    val body: HirBlock,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-33 Flow 块（惰性冷流）。 */
data class HirFlow(
    val body: HirBlock,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-33 程序位实例化/显式供给（紧贴的 <...>）：角色由构造层判定
 * （类型供给、提示性参数化等）；命题位 InstExpr 归一为命题面。
 */
data class HirInst(
    val target: HirExpr,
    val terms: List<HirExpr> = emptyList(),
    val symbol: SymbolRef? = null,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-36 lambda：params 解析为局部绑定（名字 + 符号 + 类型）；implicitIt 标记解析器
 * 合成的隐式参数糖——成参与否 / arity 由语义层按期望类型定（R-08 / R-10）:单参期望位
 * `_` 启用（参数可不用）、零参期望位成零参、多参期望须显式参数表;无期望位模式分立
 * （工程拒 / 脚本用法自定）;type 为推导函数类型（含 theory/context），
 * purity 为闭包效应结论（ADR-1 §3.3 #12）。
 */
data class HirLambda(
    val params: List<HirParam> = emptyList(),
    val body: HirExpr,
    val implicitIt: Boolean = false,
    val type: HirFunType? = null,
    val purity: HirPurity = HirPurity.PURE,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-34 匿名函数（fun _[T](a: T): T = a 及省略式；name 为 _ 或空串，不入符号表）。 */
data class HirAnonFun(
    val name: String,
    val theory: List<HirTheoryParam> = emptyList(),
    val params: List<HirParam> = emptyList(),
    val preps: List<HirProp> = emptyList(),
    val retType: HirType? = null,
    val posts: List<HirProp> = emptyList(),
    val body: HirExpr? = null,
    val type: HirFunType? = null,
    val purity: HirPurity = HirPurity.PURE,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-34 元组字面量（≥2 项；单项是括号表达式，不产出本节点）。 */
data class HirTuple(
    val items: List<HirExpr> = emptyList(),
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/** S-33 命名字段构造（A(name = e, …)）。 */
data class HirStructCtor(
    val struct: String,
    val assigns: List<Pair<String, HirExpr>> = emptyList(),
    val symbol: SymbolRef? = null,
    val type: HirType? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-31 中缀 by：e by [q] 后缀断言式。断言核对结论落在 verdict：
 * 不注入 Γ，证不出即诊断（boring documents 02「断言式」）。
 */
data class HirByExpr(
    val target: HirExpr,
    val props: List<HirProp> = emptyList(),
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr, HirProofObligation

/**
 * S-35 var 表达式（声明即表达式）。属性与 [HirVarStmt] 对齐（deepCopy 深拷贝点位）。
 */
data class HirVarExpr(
    val name: String,
    val symbol: SymbolRef? = null,
    val annotations: List<String> = emptyList(),
    val type: HirType? = null,
    val value: HirExpr,
    val destruct: List<String>? = null,
    val deepCopy: Boolean = false,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
    /**
     * t108（R3 S1，C-8 裁「v3 按需加字段」）：推导元信息最小集——省略类型由脚本模式推导填入本槽位时
     * inferred=true 并落 evidence（参与求解的使用处位置列表，P-11 §3.1「元变量必须可追溯」）。
     * 精确语义（defaulted/provenance 等）待 C-10 二次裁，本期只做最小集。默认态零产零发射——
     * HirJson 仅在 inferred||evidence 非空时写字段（既有样本产物零新字段，schema 保持 v3）。
     */
    val inferred: Boolean = false,
    val evidence: List<String> = emptyList(),
) : HirExpr

/**
 * S-16 块表达式：语句序列作为表达式值；tailExpr 为末条 ExprStmt 的表达式（结构性投影）。
 * 旧实现的 flowStmts 等解析层形态不在此保留（解析期事实经 notes 通道落盘，ADR-1 §3.1）。
 */
data class HirBlock(
    val stmts: List<HirStmt> = emptyList(),
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr {
    val tailExpr: HirExpr? get() = (stmts.lastOrNull() as? HirExprStmt)?.expr
}

/** S-34 if 表达式：diverges=条件分支必发散的核对结论（后端展平判据，ADR-1 §3.3 #9）。 */
data class HirIf(
    val cond: HirExpr,
    val thenBlock: HirBlock,
    val elseBlock: HirBlock? = null,
    val type: HirType? = null,
    val diverges: Boolean = false,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-37 when 表达式：subject 为 null=无主题布尔分支；subjectBinding 为
 * when(var e = 0) 绑定主题；exhaustive=穷尽性核对结论（W-NON-EXHAUSTIVE 判据，
 * ADR-1 §3.3 #11）。
 */
data class HirWhen(
    val subject: HirExpr? = null,
    val subjectBinding: String? = null,
    val arms: List<HirWhenArm> = emptyList(),
    val type: HirType? = null,
    val diverges: Boolean = false,
    val exhaustive: Boolean = false,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr

/**
 * S-37 when 分臂（辅助记录）：pattern/guard/body 与 AST 同构；
 * cond 为无主题 when 的条件（此时 pattern 为 PatCond 哨兵）。
 * 模式归一化（解构槽位旧名 → 相等比较）在构造期以拷贝完成。
 */
data class HirWhenArm(
    val pattern: HirPattern,
    val guard: HirExpr? = null,
    val body: HirExpr,
    val cond: HirExpr? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirNode

/** 表达式层容错占位（AST ErrorExpr 的 HIR 镜像，S-1 §2 容错）。 */
data class HirErrorExpr(
    val message: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirExpr
