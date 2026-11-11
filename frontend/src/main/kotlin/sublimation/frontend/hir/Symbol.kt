package sublimation.frontend.hir

/**
 * 符号引用——名称解析结论的载体（ADR-1 §3.3 #4；S-1 §3 名称解析在 HIR 构造中完成）。
 *
 * 契约：id 为构造期内唯一编号（权威），kind 为符号种类，name 冗余可读（消费者呈现与产物 diff）；
 * 未解析名的节点 symbol=null 并落诊断（S-1 §2 容错：半成品落树而非丢弃）。
 * JSON 产物形态：{"id": n, "kind": "...", "name": "..."}（ADR-1 §3.7）。
 */
data class SymbolRef(
    val id: Int,
    val kind: SymbolKind,
    val name: String,
)

/** 符号种类：本期声明空间 + 局部绑定 + 内建 + 定理（命题面匹配产物）。 */
enum class SymbolKind {
    FUN, TRAIT, STRUCT, ENUM, CTOR, FIELD, ALIAS, PROPDEF, THEOREM, PARAM, LOCAL, BUILTIN,
    TRAIT_CONST,   // P-8/B（TS-6②）：特质常量规范（V1 满足判定不参与，TS-17）
}
