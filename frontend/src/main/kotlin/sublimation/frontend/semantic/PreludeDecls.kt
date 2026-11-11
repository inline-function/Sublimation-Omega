package sublimation.frontend.semantic

import sublimation.frontend.ast.Expr
import sublimation.frontend.ast.IntLit
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.Param
import sublimation.frontend.ast.StructDecl
import sublimation.frontend.ast.TypeAliasDecl
import sublimation.frontend.ast.TypeParam
import sublimation.frontend.ast.UnionType
import sublimation.frontend.ast.Type as AstType

/**
 * t44（ADR-29）：prelude 去枚举化——内建容器/异步泛型以**虚拟声明**与用户代码同构。
 *
 * 旧形态（t23 前）：SymbolTable 四张「枚举概念」表（构造子字段规格 PRELUDE_CTORS、theory 元数
 * PRELUDE_ENUM_THEORY、theory 形参名 PRELUDE_ENUM_THEORY_NAMES、密封构造子清单
 * PRELUDE_ENUM_CTORS）把 Optional/List/Result/Set/Map 描述为枚举 + 构造子。t41（ADR-28）让
 * 「struct + 联合别名」成为唯一归纳形态后，prelude 改由本表提供**虚拟声明**：
 *
 * - 虚拟 struct（None/Some/Nil/Cons/Ok/Err/SetNil/SetCons/MapNil/MapCons/Channel）——经
 *   [SymbolTable.preludeCtor] 与 [Elaborator] 的 structDecls 预注入，与用户 struct 同通道：
 *   构造调用返回 **struct 名**（Some(1) : Some[Nat]）、字段实参核对、模式字段类型实例化、
 *   空构造子单例（None()/Nil() 报 E-NULLARY-PAREN）。Channel 保留单构造子形态（非联合）：
 *   cap: Nat = 0（无缓冲，旧实现 new Channel(0)，JsCodeGen:1268 / CallCheck:201，P-7 点查）。
 * - 虚拟联合别名（Optional[T] = None | Some[T] 等 5 个）——预注入 aliasDecls，类型位经
 *   [Elaborator] 的 expand 展平为联合；when 穷尽性复用 t41 联合通道（unionSealCtors），
 *   PRELUDE_ENUM_CTORS 密封清单表删除。
 *
 * 虚拟声明**不落 HIR/JSON 产物**（非文件条目），不参与声明级检查（重复/环/归纳判定——prelude
 * 内容为既定良构），且用户同名声明优先：structDecls/aliasDecls 预注入后被用户 file 条目覆盖，
 * 符号表查询链用户表在前（P-7 口径不变）。
 *
 * 字段名（value/head/tail/key/cap）为 t44 新增语义层产物——旧 PreludeField 只有位置形态；
 * prelude 构造子本就只用位置式构造，字段名仅落诊断文本。
 */
internal object PreludeDecls {

    /** 虚拟 struct 族（构造子名 = struct 名；空字段 = 空构造子单例，判据 = 字段数 0，t41）。 */
    val STRUCTS: List<StructDecl> = listOf(
        struct("None"),
        struct("Some", listOf("T"), listOf(field("value", "T"))),
        struct("Nil"),
        struct("Cons", listOf("T"), listOf(field("head", "T"), field("tail", "List", "T"))),
        struct("Ok", listOf("T"), listOf(field("value", "T"))),
        struct("Err", listOf("E"), listOf(field("value", "E"))),
        struct("SetNil"),
        struct("SetCons", listOf("T"), listOf(field("head", "T"), field("tail", "Set", "T"))),
        struct("MapNil"),
        struct("MapCons", listOf("K", "V"), listOf(field("key", "K"), field("value", "V"), field("tail", "Map", "K", "V"))),
        // B8（t23，ADR-21）：Channel——单构造子形态，cap: Nat 可省（默认 0=无缓冲，旧实现口径）。
        struct("Channel", listOf("T"), listOf(field("cap", "Nat", default = IntLit("0", SYNTHETIC_POS)))),
    )

    /** 虚拟联合别名族（prelude 容器 = 空构造子 | 值构造子，同用户归纳联合形态）。 */
    val ALIASES: List<TypeAliasDecl> = listOf(
        alias("Optional", listOf("T"), named("None"), app("Some", "T")),
        alias("List", listOf("T"), named("Nil"), app("Cons", "T")),
        alias("Result", listOf("T", "E"), app("Ok", "T"), app("Err", "E")),
        alias("Set", listOf("T"), named("SetNil"), app("SetCons", "T")),
        alias("Map", listOf("K", "V"), named("MapNil"), app("MapCons", "K", "V")),
    )

    private val structIndex = STRUCTS.associateBy { it.name }
    private val aliasIndex = ALIASES.associateBy { it.name }

    /** 虚拟 struct 查询（同名用户 struct 不在此——以用户为准）。 */
    fun structOf(name: String): StructDecl? = structIndex[name]

    /** 虚拟联合别名查询（同名用户别名不在此——以用户为准）。 */
    fun aliasOf(name: String): TypeAliasDecl? = aliasIndex[name]

    private fun struct(name: String, tps: List<String> = emptyList(), fields: List<Param> = emptyList()): StructDecl =
        StructDecl(name, tps.map { TypeParam(it, null, 0, SYNTHETIC_POS) }, fields, emptyList(), emptyList(), SYNTHETIC_POS)

    private fun field(name: String, type: String, vararg args: String, default: Expr? = null): Param =
        Param(name, NamedType(type, args.map { NamedType(it, emptyList(), SYNTHETIC_POS) }, SYNTHETIC_POS), emptyList(), default, null, SYNTHETIC_POS)

    private fun alias(name: String, tps: List<String>, vararg variants: AstType): TypeAliasDecl =
        TypeAliasDecl(name, tps.map { TypeParam(it, null, 0, SYNTHETIC_POS) }, UnionType(variants.toList(), SYNTHETIC_POS), SYNTHETIC_POS)

    private fun named(name: String): AstType = NamedType(name, emptyList(), SYNTHETIC_POS)

    private fun app(name: String, vararg args: String): AstType =
        NamedType(name, args.map { NamedType(it, emptyList(), SYNTHETIC_POS) }, SYNTHETIC_POS)
}
