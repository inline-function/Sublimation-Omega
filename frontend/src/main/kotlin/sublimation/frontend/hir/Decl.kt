package sublimation.frontend.hir

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange

/**
 * HIR 声明层级（S-5~S-14 映射，ADR-1 §3.2）：程序面命名空间。
 * 名称解析结论（symbol）落于每个声明；命题性成分（preps/posts/constraints/props/
 * 规则角组）归一为命题面节点（S-1 §3）。
 */
sealed interface HirDecl : HirNode

/**
 * S-7 函数声明：params 解析为局部绑定（符号 + 类型），retType 为声明或推导类型；
 * preps/posts/constraints 归一为命题（命题面唯一住所）；
 * purity/async 为效应结论（P-PURE；@async；ADR-1 §3.3 #12）。
 * 定义即 ∀ 引入式证明由构造层核对（boring documents 02「命题的逻辑副本：函数」）。
 */
data class HirFunDecl(
    val name: String,
    val symbol: SymbolRef? = null,
    val annotations: List<String> = emptyList(),
    val theory: List<HirTheoryParam> = emptyList(),
    val params: List<HirParam> = emptyList(),
    /** t123（R-16 S4 上文上提）：声明闭后回填——runPurityGate 合成 <pure<f>> 欠条追加位（val 名义、构造后回填，同 purity 留痕口径）。 */
    var preps: List<HirProp> = emptyList(),
    val retType: HirType? = null,
    val posts: List<HirProp> = emptyList(),
    val constraints: List<HirProp> = emptyList(),
    /** t122（R-16 A2）：runPurityGate 闭包回写位——注解优先、推导补位（构造后回填，var 留痕）。 */
    var purity: HirPurity = HirPurity.PURE,
    val async: Boolean = false,
    val body: HirExpr? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
    // P-8/A（卷 TS-28）：扩展函数接收者（null = 自由函数），类型实参与嵌套结构原样保留；
    // 末位以兼容 Elaborator 既有 15 参位置构造——语义层填充由 t30 落。
    val receiver: HirType? = null,
    /**
     * t108（R3 S1，C-8 裁「v3 按需加字段」）：推导元信息最小集——省略类型由脚本模式推导填入本槽位（retType）时
     * inferred=true 并落 evidence（参与求解的使用处位置列表，P-11 §3.1「元变量必须可追溯」）。
     * 精确语义（defaulted/provenance 等）待 C-10 二次裁，本期只做最小集。默认态零产零发射——
     * HirJson 仅在 inferred||evidence 非空时写字段（既有样本产物零新字段，schema 保持 v3）。
     */
    val inferred: Boolean = false,
    val evidence: List<String> = emptyList(),
) : HirDecl

/** S-8 特质声明：parents 为父特质；成员为 S-7 语义函数。 */
data class HirTraitDecl(
    val name: String,
    val symbol: SymbolRef? = null,
    val theory: List<HirTheoryParam> = emptyList(),
    val annotations: List<String> = emptyList(),
    val members: List<HirFunDecl> = emptyList(),
    val parents: List<HirType> = emptyList(),
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
    // P-8/B（卷 TS-6②）：特质常量规范（V1 不参与满足判定，TS-17）；末位默认空表兼容既有构造，
    // 语义层消费（满足判定/默认值供给）由后续 P-8 任务补。
    val constants: List<HirTraitConst> = emptyList(),
) : HirDecl

/** TS-6② 特质常量规范：`val name: T`——声明期的签名登记，无实现。 */
data class HirTraitConst(
    val name: String,
    val symbol: SymbolRef? = null,
    val type: HirType,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirDecl

/** S-10 结构体声明：fields 为字段绑定；props 为其命题性约束（旧实现 §2.10）--归一为命题。 */
data class HirStructDecl(
    val name: String,
    val symbol: SymbolRef? = null,
    val theory: List<HirTheoryParam> = emptyList(),
    val fields: List<HirParam> = emptyList(),
    val annotations: List<String> = emptyList(),
    val props: List<HirProp> = emptyList(),
    // t41（ADR-28 R-18）：空构造子单例旗标——判据=字段数 0（struct P(x: Nat = 0) 不算）。
    // 后端按此位保证引用相等：同一空构造子的所有值恒等（HIR 不为每次使用造新对象标识）。
    val nullary: Boolean = false,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirDecl

/** S-12 类型别名。 */
data class HirTypeAlias(
    val name: String,
    val symbol: SymbolRef? = null,
    val theory: List<HirTheoryParam> = emptyList(),
    val target: HirType,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirDecl

/** 声明层容错占位（AST ErrorDecl 的 HIR 镜像，S-1 §2 容错）。 */
data class HirErrorDecl(
    val message: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirDecl

/**
 * 理论参数层级（S-8）：声明级 theory 段——类型参数与命题参数的并集。
 * 笛卡尔闭包语义的基座（boring documents 02「表达式的逻辑副本」）。
 */
sealed interface HirTheoryParam : HirNode

/** 类型参数（T: K 形）：constraint 为特质约束的解析结论（指向特质声明）。 */
data class HirTypeParam(
    val name: String,
    val constraint: SymbolRef? = null,
    val arity: Int = 0,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTheoryParam

/** 命题参数（[P] 形：约束角组的声明级成分）。 */
data class HirPropParam(
    val name: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTheoryParam

/**
 * S-8/S-10 形参（辅助记录）：局部绑定符号 + 参数类型解析结论 + 默认值 + 解构分量名。
 */
data class HirParam(
    val name: String,
    val type: HirType? = null,
    val symbol: SymbolRef? = null,
    val annotations: List<String> = emptyList(),
    val default: HirExpr? = null,
    val destruct: List<String>? = null,
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
) : HirNode

