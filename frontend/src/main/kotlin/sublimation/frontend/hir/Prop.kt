package sublimation.frontend.hir

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange

/**
 * 命题面（S-1 §3 唯一住所）：→ 蕴含、axiom 前提、∵∴ 证明链的命题本体。
 *
 * 契约（P-STRUCT-EQ）：结构相等**显式忽略 nid/origin/notes**——位置用于诊断呈现，
 * 结构用于事实匹配（旧实现 PropLogic fromExpr/termEq/盲位置同一判据的移植）。
 * 归一入口与 ASCII⇄Unicode 归一表（P-IMP、P-NORM）由构造层（semantic）负责，
 * 节点本身只保证良构形态。
 */
sealed interface HirProp : HirNode {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

/** ⊤（恒真）。 */
data class HirPTop(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean = other is HirPTop
    override fun hashCode(): Int = 991
}

/** ⊥（恒假）。 */
data class HirPBot(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean = other is HirPBot
    override fun hashCode(): Int = 997
}

/** ¬P。 */
data class HirPNot(
    val p: HirProp,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean = other is HirPNot && p == other.p
    override fun hashCode(): Int = 1009 * 31 + p.hashCode()
}

/** P ∧ Q。 */
data class HirPAnd(
    val a: HirProp,
    val b: HirProp,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean = other is HirPAnd && a == other.a && b == other.b
    override fun hashCode(): Int = (1013 * 31 + a.hashCode()) * 31 + b.hashCode()
}

/** P ∨ Q。 */
data class HirPOr(
    val a: HirProp,
    val b: HirProp,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean = other is HirPOr && a == other.a && b == other.b
    override fun hashCode(): Int = (1019 * 31 + a.hashCode()) * 31 + b.hashCode()
}

/** P → Q（命题位蕴含的唯一形态；AST 的 BinExpr("→") 在命题位归一到此，P-IMP）。 */
data class HirPImp(
    val a: HirProp,
    val b: HirProp,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean = other is HirPImp && a == other.a && b == other.b
    override fun hashCode(): Int = (1021 * 31 + a.hashCode()) * 31 + b.hashCode()
}

/** P ↔ Q。 */
data class HirPIff(
    val a: HirProp,
    val b: HirProp,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean = other is HirPIff && a == other.a && b == other.b
    override fun hashCode(): Int = (1031 * 31 + a.hashCode()) * 31 + b.hashCode()
}

/** ∀x⃗. P。 */
data class HirPForall(
    val names: List<String>,
    val body: HirProp,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean =
        other is HirPForall && names == other.names && body == other.body
    override fun hashCode(): Int = (1033 * 31 + names.hashCode()) * 31 + body.hashCode()
}

/** ∃x⃗. P。 */
data class HirPExists(
    val names: List<String>,
    val body: HirProp,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean =
        other is HirPExists && names == other.names && body == other.body
    override fun hashCode(): Int = (1039 * 31 + names.hashCode()) * 31 + body.hashCode()
}

/** ∃!x⃗. P。 */
data class HirPExists1(
    val names: List<String>,
    val body: HirProp,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirProp {
    override fun equals(other: Any?): Boolean =
        other is HirPExists1 && names == other.names && body == other.body
    override fun hashCode(): Int = (1049 * 31 + names.hashCode()) * 31 + body.hashCode()
}

/**
 * 原子命题：head 承载一切原子——0 元（p）、参数化（valid<7>）、比较与等式
 * （$ == 7 → head="=="、terms=[$,7]）、两参解糖（q ○ p → head="○"）。
 * 裸名/返回值/形参引用的整命题形态（p、$、$n）亦以本节点承载：head=name、terms=空。
 */
data class HirPAtom(
    val head: String,
    val terms: List<HirTerm>,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
    /**
     * C-8/R-11 OQ-8（t109）:原子来源——user（用户书写）| synthetic（编译器合成:<:/(e:T)/类型事实族）。
     * trustedBase 留一审计只认 user 来源（axiomFacts 族）,synthetic 永不入信任基——免污染审计口径。
     * JSON 不发射此字段（C-8 裁「按需加字段」:审计内部态,发射层零改）。 */
    val provenance: HirAtomProvenance = HirAtomProvenance.USER,
) : HirProp {
    override fun equals(other: Any?): Boolean =
        other is HirPAtom && head == other.head && terms == other.terms
    override fun hashCode(): Int = head.hashCode() * 31 + terms.hashCode()
}

/**
 * 命题项（命题面的项世界；与程序面 [HirExpr] 分家，S-1 §3「程序位与命题位在此分家」）。
 *
 * 契约（P-STRUCT-EQ）：与 [HirProp] 同——结构相等显式忽略 nid/origin/notes
 * （旧实现 termEq 逐层结构比较的移植；NameRef.pos 是定位元数据而非项同一性基础）。
 */
/** C-8/R-11 OQ-8（t109）:原子命题来源分账（见 [HirPAtom.provenance]）。 */
enum class HirAtomProvenance { USER, SYNTHETIC }

sealed interface HirTerm : HirNode {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

/** 整数字面量项（原文保留，供算术通道与后端复用）。 */
data class HirTInt(
    val value: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean = other is HirTInt && value == other.value
    override fun hashCode(): Int = 1051 * 31 + value.hashCode()
}

/** 字符串字面量项。 */
data class HirTStr(
    val value: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean = other is HirTStr && value == other.value
    override fun hashCode(): Int = 1061 * 31 + value.hashCode()
}

/** 名字项：symbol 为名称解析结论，结构相等只认 name（旧 termEq 同构）。 */
data class HirTName(
    val name: String,
    val symbol: SymbolRef? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean = other is HirTName && name == other.name
    override fun hashCode(): Int = 1063 * 31 + name.hashCode()
}

/** 返回值引用项（$；整命题形态为 PAtom("$")）。 */
data class HirTDollar(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean = other is HirTDollar
    override fun hashCode(): Int = 1069
}

/** 第 n 形参引用项（$n；整命题形态为 PAtom("$n")）。 */
data class HirTParamRef(
    val index: Int,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean = other is HirTParamRef && index == other.index
    override fun hashCode(): Int = 1087 * 31 + index
}

/** ⊤ 项。 */
data class HirTTop(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean = other is HirTTop
    override fun hashCode(): Int = 1091
}

/** ⊥ 项。 */
data class HirTBot(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean = other is HirTBot
    override fun hashCode(): Int = 1093
}

/** 双目运算项（op 为运算符原始文本；ASCII⇄Unicode 归一在命题层，P-NORM）。 */
data class HirTBin(
    val op: String,
    val left: HirTerm,
    val right: HirTerm,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean =
        other is HirTBin && op == other.op && left == other.left && right == other.right
    override fun hashCode(): Int = ((1097 * 31 + op.hashCode()) * 31 + left.hashCode()) * 31 + right.hashCode()
}

/** 一元运算项（-/!/¬/not）。 */
data class HirTUni(
    val op: String,
    val operand: HirTerm,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean = other is HirTUni && op == other.op && operand == other.operand
    override fun hashCode(): Int = (1103 * 31 + op.hashCode()) * 31 + operand.hashCode()
}

/** 调用项（callee 为任意项——覆盖旧实现 __call__ 逃逸形：非名字 callee 的调用整体作项）。 */
data class HirTCall(
    val callee: HirTerm,
    val args: List<HirTerm>,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean =
        other is HirTCall && callee == other.callee && args == other.args
    override fun hashCode(): Int = (1109 * 31 + callee.hashCode()) * 31 + args.hashCode()
}

/** 实例化/显式供给项（覆盖旧实现 __inst__ 逃逸形）。 */
data class HirTInst(
    val target: HirTerm,
    val terms: List<HirTerm>,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean =
        other is HirTInst && target == other.target && terms == other.terms
    override fun hashCode(): Int = (1117 * 31 + target.hashCode()) * 31 + terms.hashCode()
}

/** 字段访问项。 */
data class HirTField(
    val target: HirTerm,
    val name: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean =
        other is HirTField && target == other.target && name == other.name
    override fun hashCode(): Int = (1123 * 31 + target.hashCode()) * 31 + name.hashCode()
}

/** 符号前缀调用项（<(1, 2) 形）。 */
data class HirTSymbolCall(
    val op: String,
    val args: List<HirTerm>,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirTerm {
    override fun equals(other: Any?): Boolean =
        other is HirTSymbolCall && op == other.op && args == other.args
    override fun hashCode(): Int = (1129 * 31 + op.hashCode()) * 31 + args.hashCode()
}
