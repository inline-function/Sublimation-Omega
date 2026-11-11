package sublimation.frontend.semantic

import sublimation.frontend.ast.ErrorType
import sublimation.frontend.ast.FunType
import sublimation.frontend.ast.MutType
import sublimation.frontend.ast.RefType
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.StructDecl
import sublimation.frontend.ast.TraitDecl
import sublimation.frontend.ast.TupleType
import sublimation.frontend.ast.Type
import sublimation.frontend.ast.TypeAliasDecl
import sublimation.frontend.ast.TheoryParam
import sublimation.frontend.ast.TypeParam
import sublimation.frontend.ast.UnionType
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.ident

/**
 * t64（ADR-41，P-10 步骤 3，ST-13.1）：变构性自动推断——对每个类型声明（特质/结构体）的每个类型参数
 * 推断出**视图变构对** variance(C, T) = (σ_imm, σ_mut)：
 *
 * - σ_imm（不可变视图）：函数返回位/不可变字段位计**正**；**非 @mut 成员**的形参位计**负**（Sink 型）。
 * - σ_mut（可变视图）：σ_imm 的全部，另加 **@mut 接收者成员**的形参位计**负**（仅此视图——
 *   不可变视图无法调用 @mut 方法）；可写字段（读取 + / 写入 − 同时计）**两视图皆不变**
 *   （存储贯通视图：任意不可变值经造格即可得 @mut 视图，写字段毒化两个视图）。
 *
 * R4/R5：非 @mut 全协变但 @mut 中有逆位 ⇒ (协变, 不变)；对称情形 ⇒ (逆变, 不变)（用户裁决 2026-11-08）。
 *
 * 算法：逐声明收集使用处 → 双视图格（双变 > 协变/逆变 > 不变）上 meet 的**单调不动点迭代**
 * （逐步降级；相互递归必然收敛——格高度 2，每轮至多降一级，轮次上限 [SWEEP_CAP] 兜底
 * → E-VARIANCE-NOFIXPOINT）。跨声明应用 G[实参] 处乘上 G 对应参数的当前变构性（负×负=正，ST-12）。
 *
 * 内建泛型（List/Optional/…）走**独立硬表** [BuiltinVariance]（(协变, 不变)），不进一般推断管线；
 * 用户同名声明优先（[varianceOf] 先查用户表）——同名覆盖即「不得被一般推断覆盖」的守卫。
 */
internal enum class Variance { CO, CONTRA, INV;
    val label: String get() = when (this) { CO -> "协变"; CONTRA -> "逆变"; INV -> "不变" }
}
internal data class VariancePair(val imm: Variance, val mut: Variance) {
    override fun toString() = "(" + imm.label + ", " + mut.label + ")"
}

/** 双视图格值：双变（未定/未出现）> 协变/逆变 > 不变。 */
private enum class Lat { BI, CO, CONTRA, INV }
private fun meet(a: Lat, b: Lat): Lat = when (a) {
    Lat.BI -> b
    Lat.CO -> if (b == Lat.CONTRA || b == Lat.INV) Lat.INV else b
    Lat.CONTRA -> if (b == Lat.CO || b == Lat.INV) Lat.INV else b
    Lat.INV -> Lat.INV
}
private fun flip(l: Lat): Lat = when (l) { Lat.CO -> Lat.CONTRA; Lat.CONTRA -> Lat.CO; else -> l }
/** 极性乘极性（负×负=正）；乘不变 ⇒ 不变；乘双变 ⇒ 按协变（未用参数不约束实参型，详 ADR-41）。 */
private fun times(l: Lat, v: Lat): Lat = when (v) {
    Lat.BI -> l
    Lat.CO -> l
    Lat.CONTRA -> flip(l)
    Lat.INV -> Lat.INV
}
private fun Lat.toVariance(): Variance = when (this) { Lat.BI, Lat.CO -> Variance.CO; Lat.CONTRA -> Variance.CONTRA; Lat.INV -> Variance.INV }

/** 内建泛型的变构性（独立硬表，t64 锁死：(协变, 不变)；Map 的 K 不变、V 协变）。 */
internal object BuiltinVariance {
    private val CO_INV = VariancePair(Variance.CO, Variance.INV)
    private val INV_INV = VariancePair(Variance.INV, Variance.INV)
    val TABLE: Map<String, List<VariancePair>> = mapOf(
        "List" to listOf(CO_INV),
        "Optional" to listOf(CO_INV),
        "Result" to listOf(CO_INV, CO_INV),
        "Set" to listOf(CO_INV),
        "Flow" to listOf(CO_INV),
        "Range" to listOf(CO_INV),
        "Map" to listOf(INV_INV, CO_INV),
    )
}

internal class VarianceInfer(
    private val traitDecls: Map<String, TraitDecl>,
    private val structDecls: Map<String, StructDecl>,
    private val aliasDecls: Map<String, TypeAliasDecl>,
    private val diag: DiagBag,
) {
    private data class Key(val decl: String, val param: String)

    /** 使用处：类型位 + 极性贡献 + 两视图是否计入。 */
    private data class Site(val type: Type, val pol: Lat, val imm: Boolean, val mut: Boolean)

    private val theParams = mutableMapOf<String, List<TypeParam>>()   // 声明 → 理论参数（顺序）
    private val latImm = mutableMapOf<Key, Lat>()                     // 不动点：σ_imm 格值
    private val latMut = mutableMapOf<Key, Lat>()                     // 不动点：σ_mut 格值
    private var done = false

    /** 变构表（声明名 → 有序 (参数名, (σ_imm, σ_mut))）。 */
    fun table(): Map<String, List<Pair<String, VariancePair>>> = synchronized(this) {
        ensure()
        theParams.mapValues { (decl, ps) ->
            ps.map { p ->
                val k = Key(decl, p.name)
                Pair(p.name, VariancePair(latImm.getValue(k).toVariance(), latMut.getValue(k).toVariance()))
            }
        }
    }

    /**
     * 查名取对：**用户声明优先**（同名覆盖内建表），次取内建硬表；.index 越界/未知 → null（调用方回退旧行为）。
     * 返回 (σ_imm, σ_mut) 供子类型按当前视图分派。
     */
    fun varianceOf(declName: String, index: Int): VariancePair? {
        val ps = theParams[declName]
        if (ps != null) {
            if (index !in ps.indices) return null
            ensure()
            val k = Key(declName, ps[index].name)
            return VariancePair(latImm.getValue(k).toVariance(), latMut.getValue(k).toVariance())
        }
        val bt = BuiltinVariance.TABLE[declName] ?: return null
        return if (index in bt.indices) bt[index] else null
    }

    /**
     * 类型参数 [tp]（fun/trait/struct/别名声明位）的变构性：按使用处正负位 +（@mut 成员形参 / 可写字段
     * 的）视图划分直接算——#8~#10 界方向判定输入；跨声明应用照常乘上全局表（同一推断器，同一不动点）。
     * 仅在 [tp] 自身落贡献；[allTheory] 为同声明的全集理论参数（防同位参数被当未知类型漏掉）。
     */
    fun sitesVariance(
        tp: TypeParam, allTheory: List<TheoryParam>,
        posSites: List<Type?>, negSites: List<Type?>, mutNegSites: List<Type?> = emptyList(),
    ): VariancePair {
        ensure()
        val theory = allTheory.filterIsInstance<TypeParam>()
        val own = theory.map { it.name }.toSet()
        var li = Lat.BI; var lm = Lat.BI
        val sink: (TypeParam, Lat, Boolean, Boolean) -> Unit = { p, pol, imm, mut ->
            if (p == tp) {
                if (imm) li = meet(li, pol)
                if (mut) lm = meet(lm, pol)
            }
        }
        posSites.filterNotNull().forEach { walk(it, Lat.CO, true, true, false, theory, own, sink, mutableSetOf()) }
        negSites.filterNotNull().forEach { walk(it, Lat.CONTRA, true, true, false, theory, own, sink, mutableSetOf()) }
        mutNegSites.filterNotNull().forEach { walk(it, Lat.CONTRA, false, true, false, theory, own, sink, mutableSetOf()) }
        return VariancePair(li.toVariance(), lm.toVariance())
    }

    // ============================ 推断主循环 ============================

    fun ensure() {
        if (done) return
        done = true
        theParams.clear(); latImm.clear(); latMut.clear()
        traitDecls.forEach { (n, d) -> if (d.theory.isNotEmpty()) theParams[n] = d.theory.filterIsInstance<TypeParam>() }
        structDecls.forEach { (n, d) -> if (d.theory.isNotEmpty()) theParams[n] = d.theory.filterIsInstance<TypeParam>() }
        theParams.forEach { (decl, ps) -> ps.forEach { p ->
            latImm[Key(decl, p.name)] = Lat.BI; latMut[Key(decl, p.name)] = Lat.BI
        } }
        var rounds = 0
        var changed = true
        while (changed) {
            if (++rounds > SWEEP_CAP) {
                diag.error("E-VARIANCE-NOFIXPOINT", orgFailPos(),
                    "变构性推断在 " + SWEEP_CAP + " 轮内未收敛（相互递归依赖过度？）；" +
                        "已冻结当前格值——请检查声明间的循环依赖")
                break
            }
            changed = sweep()
        }
        // 单调性机械自检（3.4c，硬要求）：违反即实现 bug
        theParams.forEach { (decl, ps) -> ps.forEach { p ->
            val k = Key(decl, p.name)
            val imm = latImm.getValue(k); val mut = latMut.getValue(k)
            if (imm == Lat.INV && mut != Lat.INV)
                diag.error("E-VARIANCE-MONO", orgFailPos(),
                    "声明 " + ident(decl) + " 的类型参数 " + ident(p.name) + " 单调性违反：" +
                        "σ_imm = 不变 ⇒ σ_mut 必为不变（实际 " + mut.toVariance().label + "）")
            if (mut == Lat.CO && imm != Lat.CO)
                diag.error("E-VARIANCE-MONO", orgFailPos(),
                    "声明 " + ident(decl) + " 的类型参数 " + ident(p.name) + " 单调性违反：" +
                        "σ_mut = 协变 ⇒ σ_imm 必为协变（实际 " + imm.toVariance().label + "）")
        } }
    }

    private fun orgFailPos(): sublimation.frontend.diag.SourceRange = SYNTHETIC_POS

    /** 单轮全仓扫描：以当前格值重算每个声明的使用处贡献，meet 回落（单调降级）。 */
    private fun sweep(): Boolean {
        var changed = false
        theParams.forEach { (decl, ps) ->
            val own = ps.map { it.name }.toSet()
            val li = mutableMapOf<TypeParam, Lat>(); val lm = mutableMapOf<TypeParam, Lat>()
            val sink: (TypeParam, Lat, Boolean, Boolean) -> Unit = { p, pol, imm, mut ->
                if (imm) li[p] = meet(li[p] ?: Lat.BI, pol)
                if (mut) lm[p] = meet(lm[p] ?: Lat.BI, pol)
            }
            sitesOf(decl).forEach { s ->
                walk(s.type, s.pol, s.imm, s.mut, false, ps, own, sink, mutableSetOf())
            }
            ps.forEach { p ->
                val k = Key(decl, p.name)
                val ni = li[p] ?: Lat.BI; val nm = lm[p] ?: Lat.BI
                if (ni != latImm.getValue(k)) { latImm[k] = ni; changed = true }
                if (nm != latMut.getValue(k)) { latMut[k] = nm; changed = true }
            }
        }
        return changed
    }

    /** 声明的使用处以（正/负 × 视图计入）。 */
    private fun sitesOf(decl: String): List<Site> {
        val out = mutableListOf<Site>()
        val tr = traitDecls[decl]; val st = structDecls[decl]
        if (tr != null) {
            tr.parents.forEach { p -> out.add(Site(p, Lat.CO, true, true)) }
            tr.members.forEach { m ->
                // @mut 接收者成员的**全部**使用位（返回位 + 形参位）仅在 @mut 视图可见——
                // 不可变视图无法调用 @mut 方法，其返回类型不可观察（R5 对称例 ⇒ (逆变, 不变)）
                val memberInView = "mut" !in m.annotations
                m.retType?.let { out.add(Site(it, Lat.CO, memberInView, true)) }
                m.params.forEach { q -> out.add(Site(q.type ?: ErrorType("形参类型标注省略", q.pos), Lat.CONTRA, memberInView, true)) }   // t113 机械适配：省略位兜底 ErrorType
            }
        } else if (st != null) {
            st.fields.forEach { f ->
                val writable = "mut" in f.annotations
                out.add(Site(f.type ?: ErrorType("形参类型标注省略", f.pos), Lat.CO, true, true))                    // 读位：两视图皆正
                if (writable) out.add(Site(f.type ?: ErrorType("形参类型标注省略", f.pos), Lat.CONTRA, true, true))   // 写位：两视图皆负（存储贯通）
            }
        }
        return out
    }

    /** 类型游走：极性贡献沿应用链乘下去（G[实参] 处 × G 对应参数变构性）；命中 own 参数即落贡献。 */
    private fun walk(
        t: Type, pol: Lat, imm: Boolean, mut: Boolean, underMut: Boolean,
        theory: List<TypeParam>, own: Set<String>,
        sink: (TypeParam, Lat, Boolean, Boolean) -> Unit,
        aliasSeen: MutableSet<String>,
    ) {
        when (t) {
            is NamedType -> {
                val p = theory.find { it.name == t.name }
                if (p != null) { sink(p, pol, imm, mut); return }   // 自身参数（含高阶 F[X]：贡献极性本身，V1）
                val al = aliasDecls[t.name]
                if (al != null && aliasSeen.add(t.name)) {
                    val names = al.theory.filterIsInstance<TypeParam>().map { it.name }
                    val sub = names.zip(t.args).toMap()
                    walk(substitute(al.target, sub), pol, imm, mut, underMut, theory, own, sink, aliasSeen); return
                }
                if (t.args.isNotEmpty()) {
                    t.args.forEachIndexed { i, a ->
                        val factor = factorOf(t.name, i, underMut)
                        walk(a, times(pol, factor), imm, mut, underMut, theory, own, sink, aliasSeen)
                    }
                }
            }
            is FunType -> {
                t.params.forEach { walk(it, flip(pol), imm, mut, underMut, theory, own, sink, aliasSeen) }
                walk(t.ret, pol, imm, mut, underMut, theory, own, sink, aliasSeen)
            }
            is UnionType -> t.variants.forEach { walk(it, pol, imm, mut, underMut, theory, own, sink, aliasSeen) }
            is TupleType -> t.items.forEach { walk(it, pol, imm, mut, underMut, theory, own, sink, aliasSeen) }
            // @mut 包装下的应用按其 σ_imm/σ_mut 区分（因子取表口径，ST-13.1）
            is MutType -> walk(t.inner, pol, imm, mut, true, theory, own, sink, aliasSeen)
            // t95：@ref 透明过境（视图语义在 t97——本期不改变变构性事实集）
            is RefType -> walk(t.inner, pol, imm, mut, underMut, theory, own, sink, aliasSeen)
            else -> {}
        }
    }

    /** G 的第 i 参数的**当前**格值：用户声明查不动点表（σ_mut/σ_imm 同查——当前轮次值），内建查硬表。 */
    /** G 的第 i 参数的**当前**格值：用户声明查不动点表（σ_mut 仅当应用被 @mut 包装），内建查硬表同口径。
     *  未使用/无表条目退双变（乘以双变 = 不约束——G 的该参数未出现于任何实参型比较）。 */
    private fun factorOf(name: String, i: Int, underMut: Boolean): Lat {
        val ps = theParams[name]
        if (ps != null && i in ps.indices) {
            val k = Key(name, ps[i].name)
            return (if (underMut) latMut[k] else latImm[k]) ?: Lat.BI
        }
        val bt = BuiltinVariance.TABLE[name] ?: return Lat.BI
        val pair = if (i in bt.indices) bt[i] else return Lat.BI
        val s = if (underMut) pair.mut else pair.imm
        return when (s) { Variance.INV -> Lat.INV; Variance.CO -> Lat.CO; Variance.CONTRA -> Lat.CONTRA }
    }

    /** 别名体替换（ paramMap：别名参数名 → 实参类型），递归重建 NamedType。 */
    private fun substitute(t: Type, paramMap: Map<String, Type>): Type = when (t) {
        is NamedType -> {
            val rep = paramMap[t.name]
            if (rep != null && t.args.isEmpty()) rep
            else NamedType(t.name, t.args.map { substitute(it, paramMap) }, t.pos)
        }
        is FunType -> FunType(t.params.map { substitute(it, paramMap) }, substitute(t.ret, paramMap), t.purity, t.theory, t.context, t.pos)
        is UnionType -> UnionType(t.variants.map { substitute(it, paramMap) }, t.pos)
        is TupleType -> TupleType(t.items.map { substitute(it, paramMap) }, t.pos)
        is MutType -> MutType(substitute(t.inner, paramMap), t.pos)
        is RefType -> RefType(substitute(t.inner, paramMap), t.pos)   // t95
        else -> t
    }

    companion object { private const val SWEEP_CAP = 16 }
}
