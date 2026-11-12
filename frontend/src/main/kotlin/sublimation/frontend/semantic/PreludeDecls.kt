package sublimation.frontend.semantic

import sublimation.frontend.ast.Expr
import sublimation.frontend.ast.FunDecl
import sublimation.frontend.ast.IntLit
import sublimation.frontend.ast.MutType
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

    /** 虚拟 struct 族（构造子名 = struct 名；空字段 = 空构造子单例，判据 = 字段数 0，t41）。
     * t138（R-23 B1）：None/Some/Nil/Cons/Ok/Err/SetNil/SetCons/MapNil/MapCons 十项 retire →
     * stdlib 源（各 collection 件内声明，形状逐字同构——见 [RETIRED_STRUCTS] 允许表）。
     * Channel **保留虚拟**（无 stdlib/channel 件——登记下一批：B1.5 / B2 后另件）。 */
    val STRUCTS: List<StructDecl> = listOf(
        // B8（t23，ADR-21）：Channel——单构造子形态，cap: Nat 可省（默认 0=无缓冲，旧实现口径）。
        struct("Channel", listOf("T"), listOf(field("cap", "Nat", default = IntLit("0", SYNTHETIC_POS)))),
    )

    /** t138（R-23 B1）：retire 替身名表——非限定类型面（builtins 懒登记通道）只吃这些名；
     * stdlib 件声明的其他类型保持**限定可见**（R-22 限定唯一通道，裸名面不扩）。 */
    val RETIRED_STRUCTS: Set<String> = setOf(
        "None", "Some", "Nil", "Cons", "Ok", "Err",
        "SetNil", "SetCons", "MapNil", "MapCons",
    )

    /** 虚拟联合别名族（prelude 容器 = 空构造子 | 值构造子，同用户归纳联合形态）。
     * t138（R-23 B1）：五项全部 retire → stdlib 源（见 [RETIRED_ALIASES] 允许表）。 */
    val ALIASES: List<TypeAliasDecl> = listOf()

    /** t138（R-23 B1）：retire 别名替身名表（非限定类型面允许表）。 */
    val RETIRED_ALIASES: Set<String> = setOf("Optional", "List", "Result", "Set", "Map")

    /** t126（S5c / R-15 容器面 · 队长裁 Alt A）：虚拟构造族——值实参解理论参数（既有 instCall 通道，
     *  零新推导机制）：`MutableListOf(x)` → `@mut List[T]`（可变容器，元素类型由实参定）;
     *  `ListOf(x)` → `List[T]`（不可变同构）。零参 per-call ?T 形态登记后版（P-11 §5.1，R-20）。
     *  用户同名声明优先（Collector 先摘虚拟条目再登记——P-7 口径）。 */
    val FUNS: List<FunDecl> = listOf(
        ctorFun("MutableListOf", mut = true),
        ctorFun("ListOf", mut = false),
    )

    /** t126（S5c / R-15 容器面）：虚拟 @mut 扩展方法族——去糖模型 R-18.1（self = 接收者首参，持可写
     *  视图 @mut；方法体写接收者无罪 R-18.2，罪恶在调用边界 R-18.3）。V1 签名面：方法体随后端/
     *  标准库源（S2 迁源形态 C）落。 */
    val EXT_METHODS: List<FunDecl> = listOf(
        methodFun("add", "List", "a",
            Param("v", NamedType("a", emptyList(), SYNTHETIC_POS), emptyList(), null, null, SYNTHETIC_POS),
            NamedType("List", listOf(NamedType("a", emptyList(), SYNTHETIC_POS)), SYNTHETIC_POS)),
    )

    private fun ctorFun(name: String, mut: Boolean): FunDecl {
        val elem = NamedType("T", emptyList(), SYNTHETIC_POS)
        val ret = NamedType("List", listOf(elem), SYNTHETIC_POS)
        return FunDecl(name, if (mut) listOf("mut") else emptyList(),
            listOf(TypeParam("T", null, 0, SYNTHETIC_POS)),
            listOf(Param("first", elem, emptyList(), null, null, SYNTHETIC_POS)),
            emptyList(), if (mut) MutType(ret, SYNTHETIC_POS) else ret, emptyList(),
            null, emptyList(), SYNTHETIC_POS)
    }

    private fun methodFun(name: String, recvHead: String, elem: String, param: Param, ret: sublimation.frontend.ast.Type): FunDecl =
        FunDecl(name, listOf("mut"),
            listOf(TypeParam(elem, null, 0, SYNTHETIC_POS)), listOf(param), emptyList(), ret, emptyList(), null,
            emptyList(), SYNTHETIC_POS,
            receiver = MutType(NamedType(recvHead, listOf(NamedType(elem, emptyList(), SYNTHETIC_POS)), SYNTHETIC_POS), SYNTHETIC_POS))

    private val structIndex = STRUCTS.associateBy { it.name }
    private val aliasIndex = ALIASES.associateBy { it.name }

    /** 虚拟 struct 查询（同名用户 struct 不在此——以用户为准）。t138 后仅 Channel（其余经快照注入）。 */
    fun structOf(name: String): StructDecl? = structIndex[name]

    /** 虚拟联合别名查询（同名用户别名不在此——以用户为准）。t138 后空表（全部经快照注入）。 */
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
