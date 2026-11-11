
package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.ident
import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirAtomicType
import sublimation.frontend.hir.HirRefType
import sublimation.frontend.hir.HirErrorType
import sublimation.frontend.hir.HirExpr
import sublimation.frontend.hir.HirFunType
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.hir.HirQualifiedType
import sublimation.frontend.hir.HirSelfType  // P-8/A：卷 TS-29
import sublimation.frontend.hir.HirTupleType
import sublimation.frontend.hir.HirType
import sublimation.frontend.hir.HirUnionType


/**
 * v1 类型推导（t8；ADR-6 §四 4.2/4.3 插入件）：
 *
 * - **纯函数语义**：本类不遍历树——签名形如「节点上下文 → 结论 + 诊断」，
 *   由 Elaborator 构造到该节点时调用（控制解耦：唯一驱动在 Elaborator）；
 * - **类型代数归此**：旧 Types.kt 主干（typeEq/looseEq/unifyInto/unify/render/isSynthetic/
 *   isNominal/isAnyExpect/unsealedT 等，P-7 对齐）从 AST 内嵌方法迁移至此——
 *   AST 保持零语义（S-1 §2；ADR-6 §四.2）；
 * - **v1 范围**：变量/表达式类型推导（旧 TypeInfer 同期口径）。lambda 泛型反推（T2 双向推导）、
 *   完整合一约束求解驱动的多态实例化、HKT-2、效应/纯度/证明代码均属后续轮次（t9 起），
 *   诊断面亦随之在 ADR-12 §三载明推迟项。
 *
 * 内建签名表（旧 builtinimpure/builtinPure 两表的**类型层**投影，P-7）：参数/返回
 * （理论参数与纯度结论次轮）；未登记内建按值符号兜底（bool/null/true/false）。
 */
internal class TypeInfer(
    private val alloc: () -> NodeId,
    private val diag: DiagBag,
    /** t44（ADR-29）：联合别名展开件（Elaborator 注入——adaptOk 的实参具名联合别名比对用）。
     *  独立测试件（mkInfer）不注入：保持纯代数件语义。 */
    private val aliasExpander: ((HirType) -> HirType)? = null,
    /** t62（ADR-39）：偏序侧注入件——
     *  traitSubtype(got, 特质名)：got 满足特质（ST-3，结构满足）或 got 特质经父链 <: 该特质（ST-9）；
     *  structFieldsOf(结构体名)：结构体字段清单（名→HIR 类型），用于 2.9 结构满足（宽+深）。 */
    private val traitSubtype: ((HirType, String) -> Boolean)? = null,
    private val structFieldsOf: ((String) -> List<Pair<String, HirType>>?)? = null,
    /** t64（ADR-41，ST-13.1）：同名同参应用的逐参变构性（(σ_imm, σ_mut)；null = 未知——
     *  调用方（Elaborator 注入声明索引表）优先于内建硬表 BuiltinVariance）。 */
    private val varianceOf: ((String, Int) -> VariancePair?)? = null,
) {

    // ============================ t51（ADR-33）：双向检查燃料 ============================

    /**
     * 打开一个顶层检查请求的燃料池：请求内所有嵌套判定**共享同一池**、**不随递归层重置**
     * （逐层重置会让总消耗 = O(深度×预算)，等于没有燃料）。单线程单调递减计数，确定性保证。
     * 未被 [checkScope] 显式打开时，首个计量步自动开池（兜底——保证发散路径必被燃料覆盖）。
     */
    private var fuel = 0
    private var fuelDepth = 0
    private var fuelOrigin: SourceRange? = null
    private var fuelReported = false
    /** t52（t51 验收补充）：本请求内已落 E-FUEL-EXHAUSTED ?——为真时抑制**由耗尽直接派生**的
     *  E-TYPE-MISMATCH 级联（一个根因只报一条；独立请求的池各自独立，不受影响——精确边界：
     *  仅同一顶层检查请求的 when/表达式子树内）。 */
    private fun fuelExhaustedNow(): Boolean = fuelReported && fuelDepth > 0

    /**
     * t58（ADR-36）：E-TYPE-MISMATCH 落点统一闸——
     * ① t51/t52 口径：燃料耗尽（同请求内）不报；
     * ② **合成位（推导未完成的标记）伍向拒绝**：验收方案明文独事——「仅在推导未完成时容忍，
     * 完成后必须复核，禁止长期放行」。其区分：
     * - adaptOk （纯裁嬢）**绝对严格**（synthetic 分支已删，R-34）；
     * - 落点闸对以 synthetic 为操作数的拒绝保持容忍（该拒绝实质为「未完成」而非「失配」；
     *   此前由 su似放行所掩盖的 lambda 参数位、内建泛型位等结构仍不可判定，亦不会吐出根因诊断
     *   ——避免对合法但超出 V1 推导能力的样例出现过拒；与旧实现行为一致，无回归。
     * 真失配（两边均具体）如实落：P1（Nothing 体）/P2（参数位逆变）/r35（Nothing 期望位）
     * 以及 P4（apply 的裸类型参数 T 落实型失配），均由「具体类型通道」落错，不受本闸影响。
     */
    // t60-3：E-DEFAULT-TYPE 为 t60 新码（与 E-DEFAULT-ORDER 同族）；其余核对点保持 E-TYPE-MISMATCH
    private fun reportMismatch(got: HirType?, want: HirType?, pos: SourceRange, msg: String, code: String = "E-TYPE-MISMATCH") {
        if (fuelExhaustedNow()) return
        if (containsSynthetic(got) || containsSynthetic(want)) return
        diag.error(code, pos, msg)
    }

    /** 复合类型（函数型/可变型/联合/元组及其参数位）中是否含合成叶子（R-34 拓展）。 */
    private fun containsSynthetic(t: HirType?): Boolean = when (t) {
        is HirNamedType -> t.isSynthetic() || t.args.any { containsSynthetic(it) }
        is HirFunType -> t.params.any { containsSynthetic(it) } || containsSynthetic(t.ret)
        is HirMutType -> containsSynthetic(t.inner)
        is HirAtomicType -> containsSynthetic(t.inner)
        is HirUnionType -> t.variants.any { containsSynthetic(it) }
        is HirTupleType -> t.items.any { containsSynthetic(it) }
        else -> false
    }


    private var fuelFirstGot: HirType? = null
    private var fuelFirstWant: HirType? = null
    private var fuelMaxConsumed = 0

    /** 打开（或复用）请求域。owner=true 者负责关闭与统计收口。 */
    private fun openPool(pos: SourceRange?, got: HirType?, want: HirType?) {
        fuel = FUEL_DEFAULT
        fuelReported = false
        fuelOrigin = pos
        fuelFirstGot = got
        fuelFirstWant = want
    }

    /**
     * 计量入口（判定步 = 扣 1）：裸调用（未被 [checkScope] 打开）自动开池并**持有到本子树结束**
     * ——池随请求而非随层，保证发散路径每深一层都在同一预算内扣步。返回 false = 耗尽拒绝。
     */
    /** t51：联合展开计 1 步（耗尽 → false 阻断成员循环）。 */
    private fun fuelUnionExpand(): Boolean = if (fuelDepth > 0) fuelConsume() else true

    /** t51：别名展开计 1 步。 */
    private fun fuelAliasExpand(): Boolean = if (fuelDepth > 0) fuelConsume() else true

    /**
     * 除步预算外并列**深度哨**（FUEL_DEPTH_LIMIT）：发散互追的判步近乎 1:1 消耗 JVM 栈帧，
     * 若纯靠步预算（= 实测最大值 × 100，见 ADR-33）先行耗尽，JVM 栈会先一溢出崩溃——深度哨保证
     * 「拒绝」必然先于「崩溃」机械触发，与合法深嵌套检查留足余量（合法样本实测深度 << 上限）。
     */
    private fun fuelEnter(got: HirType? = null, want: HirType? = null): Boolean {
        if (fuelDepth == 0) openPool(null, got, want)
        fuelDepth++
        if (fuelDepth > FUEL_DEPTH_LIMIT) {
            fuelExhausted("递归深度 " + fuelDepth + " 层（安全上限 " + FUEL_DEPTH_LIMIT + "）")
            return false
        }
        return fuelConsume()
    }

    /** 计量出口：owner（开池者）负责请求收口与统计。 */
    private fun fuelExit(owner: Boolean) {
        fuelDepth--
        if (owner) {
            fuelMaxConsumed = maxOf(fuelMaxConsumed, FUEL_DEFAULT - fuel)
            FuelBook.record(FUEL_DEFAULT - fuel, fuelOrigin, fuelReported)
        }
    }

    private fun fuelConsume(): Boolean {
        if (fuel > 0) {
            fuel--
            if (fuel == 0) fuelExhausted(null)
            return fuel > 0
        }
        return false
    }

    /** 耗尽（步预算或深度哨触发）：裁决原文逐字 + 与之分开的补充信息（每池一次，不吞不降级）。 */
    private fun fuelExhausted(detail: String?) {
        if (fuelReported) return
        fuelReported = true
        val pos = fuelOrigin ?: SYNTHETIC_POS
        diag.error("E-FUEL-EXHAUSTED", pos, "编译器进行检查时无法停机")
        val stepPart = if (detail == null)
            "③已消耗 " + (FUEL_DEFAULT - fuel) + " 步（上限 " + FUEL_DEFAULT + "）"
        else
            "③已消耗 " + (FUEL_DEFAULT - fuel) + " 步（上限 " + FUEL_DEFAULT + "）；" + detail
        diag.supplement("D-FUEL-EXHAUSTED", pos,
            "①触发位置：" + pos + "；②检查目标：实际 " + render(fuelFirstGot) + " / 期望 " + render(fuelFirstWant) +
            "；" + stepPart + "；④建议：多由归纳类型别名（type A = …A…）互展开导致，可给变量/形参加显式类型标注切断推导")
    }

    /** 显式打开一个顶层检查请求的燃料域（表达式/声明级入口；嵌套调用复用外层池）。 */
    fun <R> checkScope(pos: SourceRange? = null, block: () -> R): R {
        val owner = fuelDepth == 0
        if (owner) openPool(pos, null, null)
        fuelDepth++
        return try {
            block()
        } finally {
            fuelExit(owner)
        }
    }

    /** 本编译单元内单次请求的最大消耗（统计口径；供分布实测）。 */
    fun fuelMaxConsumed(): Int = fuelMaxConsumed

    /** 统计标签（按编译单元命名，仅分布实测用）。 */
    fun setFuelLabel(label: String) { FuelBook.label = label }

    /**
     * t51（ADR-33）：默认燃料上限（判定步）= 实测单次顶层检查最大消耗 × 100（实测：全量测试套件
     * + 68 e2e 样本逐请求打点，共 2497 请求，max=2375 → 237_500；分布与依据见 ADR-33 四、节）。
     */
    private val FUEL_DEFAULT: Int = 237500

    /** t51（ADR-33）：递归深度哨——发散互追先于 JVM 栈溢出机械触发拒绝（合法样本实测深度远低于此）。 */
    private val FUEL_DEPTH_LIMIT: Int = 800

    /** 全局燃料统计簿（进程级；env SUBL_FUEL_STATS 时在 JVM 退出时落盘）。 */
    object FuelBook {
        internal val samples = java.util.ArrayList<Int>()
        internal val top = java.util.ArrayList<Pair<Int, String>>()
        internal var label: String = ""
        internal val enabled = System.getenv("SUBL_FUEL_STATS") != null

        internal fun record(consumed: Int, origin: SourceRange?, exhausted: Boolean = false) {
            if (!enabled) return
            samples.add(consumed)
            top.add(consumed to ((if (exhausted) "[耗尽] " else "") + label + " @ " + (origin?.toString() ?: "?")))
            if (top.size > 200) {
                top.sortByDescending { it.first }
                top.subList(64, top.size).clear()
            }
        }

        /** 分布摘要（max / P99 / 中位数 / 消耗最大的三个请求）。 */
        fun summary(): String {
            val s = samples
            if (s.isEmpty()) return "(无样本)"
            val sorted = s.sorted()
            val max = sorted.last()
            val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)]
            val median = sorted[sorted.size / 2]
            val total = sorted.size
            val sb = StringBuilder()
            sb.append("fuel-stats: total=" + total + " max=" + max + " p99=" + p99 + " median=" + median + "\n")
            top.sortedByDescending { it.first }.take(25).forEach { e ->
                sb.append("  " + e.first + " steps  " + e.second + "\n")
            }
            return sb.toString().trimEnd()
        }

        init {
            if (enabled) {
                val out = System.getenv("SUBL_FUEL_FILE") ?: "build/tmp/fuel-stats.txt"
                Runtime.getRuntime().addShutdownHook(Thread {
                    try {
                        java.io.File(out).parentFile?.mkdirs()
                        java.io.File(out).writeText(summary())
                    } catch (_: Throwable) { }
                })
            }
        }
    }
    // ============================ 类型代数（旧 Types.kt 主干，P-7） ============================

    /** 合成类型（v1 推导不完整时的占位：(推导:原因)）——synthetic 宽容：不产生约束。 */
    fun syntheticT(reason: String, pos: SourceRange = SYNTHETIC_POS): HirNamedType =
        namedT("(推导:" + reason + ")", pos = pos)

    fun namedT(name: String, args: List<HirType> = emptyList(), pos: SourceRange = SYNTHETIC_POS): HirNamedType =
        HirNamedType(name, args, null, alloc(), pos, emptyList())

    /** 非密封 when 的值类型（§49：固定 Null；带标记以上层给专属报错）。非 synthetic、仅与 Null 相容。 */
    fun unsealedT(pos: SourceRange = SYNTHETIC_POS): HirNamedType = namedT("(未密封)", pos = pos)

    fun HirType.isSynthetic(): Boolean = this is HirNamedType && name.startsWith("(推导:") && args.isEmpty()

    /** t115（S2，P-11 §3.1/§3.6）：元变量占位（合成位特化）——(推导:元变量...) 开头；判定面按底语义（见 subtypeBody）
     *  与普通 synthetic（resolves 不完整兜底）分账：普通 synthetic 不入元变量约束收集。 */
    fun HirType.isMetaSlot(): Boolean = this is HirNamedType && name.startsWith("(推导:元变量") && args.isEmpty()
    fun HirType.isUnsealed(): Boolean = this is HirNamedType && name == "(未密封)" && args.isEmpty()

    /** t58：内建函数的泛型理论参数名（签名中出现的 (内建T) 标记位；调用方下传 callFun 供 instCall 解出）。 */
    fun builtinTheoryNames(name: String): List<String> {
        val (ps, rt) = builtinSig(name) ?: return emptyList()
        val found = mutableSetOf<String>()
        fun scan(t: HirType?) {
            when (t) {
                is HirNamedType -> {
                    if (t.args.isEmpty() && t.name == "(内建T)") found.add(t.name)
                    t.args.forEach { scan(it) }
                }
                is sublimation.frontend.hir.HirFunType -> { t.params.forEach { scan(it) }; scan(t.ret) }
                is sublimation.frontend.hir.HirMutType -> scan(t.inner)
                is sublimation.frontend.hir.HirAtomicType -> scan(t.inner)
                is sublimation.frontend.hir.HirUnionType -> t.variants.forEach { scan(it) }
                else -> {}
            }
        }
        ps.forEach { scan(it) }
        scan(rt)
        return found.toList()
    }

    /** 期望位为自动特质（裸 Any）：接受任意具体类型（旧 isAnyExpect，期望侧专用）。 */
    fun HirType.isAnyExpect(): Boolean = this is HirNamedType && name == "Any" && args.isEmpty()

    /** Nothing 底类型判定（零实参具名 Nothing；v2.1 发散值口径）。 */
    private fun isNothingT(t: HirType): Boolean =
        t is HirNamedType && t.name == "Nothing" && t.args.isEmpty()

    /** 名义类型判定（T1/决策 57）：NamedType 首字母大写 或 跨模块限定类型；函数/元组非名义。 */
    fun HirType.isNominal(): Boolean =
        (this is HirNamedType && name.isNotEmpty() && name.first().isUpperCase() && !isSynthetic()) ||
            this is HirQualifiedType

    private fun rank(n: String): Int? = when (n) { "Nat" -> 0; "Int" -> 1; "Rat" -> 2; else -> null }

    private fun HirNamedType.isNumeric(): Boolean = rank(name) != null && args.isEmpty()

    /** 类型相等（T1：名义同名义；函数/元组按结构；@mut T ≠ T；@atomic T ≡ T）。 */
    fun typeEq(a: HirType, b: HirType): Boolean = when {
        a is HirNamedType && b is HirNamedType ->
            a.name == b.name && a.args.size == b.args.size && a.args.zip(b.args).all { typeEq(it.first, it.second) }
        a is HirQualifiedType && b is HirQualifiedType ->
            a.name == b.name && a.args.size == b.args.size && a.args.zip(b.args).all { typeEq(it.first, it.second) }
        a is HirFunType && b is HirFunType ->
            a.params.size == b.params.size && a.purity == b.purity &&
                a.params.zip(b.params).all { typeEq(it.first, it.second) } && typeEq(a.ret, b.ret)
        a is HirTupleType && b is HirTupleType ->
            a.items.size == b.items.size && a.items.zip(b.items).all { typeEq(it.first, it.second) }
        a is HirRefType && b is HirRefType -> typeEq(a.inner, b.inner)
        a is HirMutType && b is HirMutType -> typeEq(a.inner, b.inner)
        a is HirAtomicType && b is HirAtomicType -> typeEq(a.inner, b.inner)
        a is HirAtomicType -> typeEq(a.inner, b)
        b is HirAtomicType -> typeEq(a, b.inner)
        else -> false
    }

    /** 宽松相等（旧 TypeInfer 自用口径：synthetic 放行、数值塔放行、结构递归）。 */
    fun looseEq(a: HirType, b: HirType): Boolean {
        if (a.isSynthetic() || b.isSynthetic()) return true
        if (a is HirNamedType && b is HirNamedType) {
            if (a.args.isEmpty() && b.args.isEmpty() && rank(a.name) != null && rank(b.name) != null) return true
            if (a.name != b.name || a.args.size != b.args.size) return false
            return a.args.zip(b.args).all { looseEq(it.first, it.second) }
        }
        return typeEq(a, b)
    }

    /** t61（ADR-38 ST-11.1）：@mut T → T 的**视图降级**（读视图剔除可变包装；文法允许多层 @mut 时递归剥层）。 */
    fun demoteView(t: HirType): HirType {
        var cur = t
        // t98（N2 规则 2/3 已上线）：sub 的 @ref 视图规则承担类型层相容性;本处在**造格/借用目标**
        // （got 非 @mut 视图时）仍整体剥层（@ref 与 @mut 同构的视图降级，含拷贝分派前的目标侧）。
        while (true) {
            cur = when (cur) {
                is HirMutType -> cur.inner
                is HirRefType -> cur.inner
                else -> return cur
            }
        }
        return cur
    }


    /** t98（N2 @ref · 规则 1 可写性）:可写视图——@mut T 或 @ref @mut T（写穿透共享引用）;
     *  @ref T / T 只读视图（根文档 :59-63「b.n = 1 无法赋值」）;@mut @ref T 不可写（写穿只读引用不安全）。 */
    fun isWritableView(t: HirType): Boolean = when (t) {
        is HirRefType -> isWritableView(t.inner)
        is HirMutType -> t.inner !is HirRefType
        else -> false
    }

    /** t98:类型树是否含 @ref 视图（拷贝分派留痕与命题待遇的触发判据）。 */
    fun containsRef(t: HirType): Boolean = when (t) {
        is HirRefType -> true
        is HirMutType -> containsRef(t.inner)
        is HirNamedType -> t.args.any { containsRef(it) }
        is HirQualifiedType -> t.args.any { containsRef(it) }
        is HirFunType -> t.params.any { containsRef(it) } || containsRef(t.ret)
        is HirTupleType -> t.items.any { containsRef(it) }
        is HirUnionType -> t.variants.any { containsRef(it) }
        else -> false
    }

    /** t98（N2 @ref · 规则 3 拷贝分派矩阵坐标）：PLAIN=T / MUT=@mut T / REF=@ref T / REFMUT=@ref @mut T。 */
    enum class ViewKind { PLAIN, MUT, REF, REFMUT }

    private fun viewKindOf(t: HirType): ViewKind {
        var cur = t; var seenRef = false
        while (cur is HirRefType) { seenRef = true; cur = cur.inner }
        return when {
            cur is HirMutType && seenRef -> ViewKind.REFMUT
            cur is HirMutType -> ViewKind.MUT
            seenRef -> ViewKind.REF
            else -> ViewKind.PLAIN
        }
    }

    /**
     * t98（N2 @ref · 规则 3 拷贝分派，根文档 :89-163 八格矩阵）:**深拷=true / 共享=false**。
     *  共享 ⟺ 目标持引用：want = @ref T 任何源皆共享（不可变视图只读安全，:146/:150/:126/136）;
     *  want = @ref @mut T 仅当源可写（@mut / @ref @mut T——:113/:131）;want 非 ref 时仅 PLAIN→PLAIN
     *  共享（:149-156 不可变互传不拷贝）;want = @mut T 皆深拷（造格，:97/:104）。
     *  前端只做**类型层判定 + HIR 留痕**——实际深拷随后端运行时（R2 §三）。 */
    fun copyDispatch(got: HirType, want: HirType): Boolean = when (viewKindOf(want)) {
        ViewKind.REF -> false
        ViewKind.REFMUT -> viewKindOf(got) != ViewKind.MUT && viewKindOf(got) != ViewKind.REFMUT
        ViewKind.PLAIN -> viewKindOf(got) != ViewKind.PLAIN
        ViewKind.MUT -> true
    }
    /** t64（ADR-41，ST-13.1）:借用/造格目标（t98 @ref 口径见下）。 */
    /** t64 ADR-41 造格/借用目标 + t98（N2 @ref）：got 已是 @mut 视图时按**视图对视图**比对;
     *  否则:want 含 @ref → 剥掉全部 @ref 层（@ref 与内层双向相容，@mut 视图不变性仍由 sub 承担——
     *  T → @ref @mut T 落 mismatch：不可变值发可写引用不安全）;其余走旧 demoteView 造格口径。 */
    private fun borrowView(got: HirType, want: HirType): HirType =
        if (got is HirMutType) want else if (containsRef(want)) stripRefs(want) else demoteView(want)

    /** t98:剥掉全部 @ref 视图层（@mut 层保留——视图不变性闸位）。 */
    private fun stripRefs(t: HirType): HirType {
        var cur = t
        while (cur is HirRefType) cur = cur.inner
        return cur
    }

    /** t64（ADR-41，ST-13.1）：泛型实参按声明变构性逐参分派——协变 sub(a,b)、逆变反向 sub(b,a)、不变 typeEq；
     *  无表/未知 → 约定协变（t62 旧口径回退）。 */
    private fun genericArgsByVariance(
        gotArgs: List<HirType>, wantArgs: List<HirType>, declName: String, useMut: Boolean,
        assume: Set<Pair<HirType, HirType>>,
    ): Boolean = gotArgs.indices.all { i ->
        val v = varianceOf?.invoke(declName, i)
        val s = if (useMut) v?.mut else v?.imm
        when (s) {
            Variance.CONTRA -> sub(wantArgs[i], gotArgs[i], assume)
            Variance.INV -> typeEq(gotArgs[i], wantArgs[i])
            else -> sub(gotArgs[i], wantArgs[i], assume)
        }
    }

    /** t64：双方 @mut 视图的内层比对——具名应用走 σ_mut 分派，其余 typeEq。 */
    private fun mutArgsByVariance(got: HirType, want: HirType, assume: Set<Pair<HirType, HirType>>): Boolean {
        val ga = got as? HirNamedType; val wa = want as? HirNamedType
        if (ga != null && wa != null && ga.name == wa.name && ga.args.isNotEmpty() && ga.args.size == wa.args.size)
            return genericArgsByVariance(ga.args, wa.args, wa.name, true, assume)
        return typeEq(got, want)
    }

    // ============================ t62（ADR-39）：子类型偏序（唯一相容性判据） ============================

    /** **got <: want**——t62 P-10 步骤 2：全前端唯一的相容性判据（自反/传递/名义反对称；
     *  ST-3 满足即子类型 / ST-9 特质继承 / ST-10 数值塔（Real 排除）/ ST-6 Nothing 真底 / ST-11 Any 真顶 /
     *  ST-8 联合注入单向 / ST-7 函数型变构 / ST-11.1 @mut 视图单向 / 2.9 结构体结构满足）。
     *  递归终止 = **假设集**（余归纳：正在证明的 (a, b) 对先假定成立，再证其成员——递归类型正解）
     *  + t51 燃料（**同一池**，卡在 adaptOk/检查请求上，不开新池）。 */
    fun subtype(got: HirType, want: HirType): Boolean = sub(got, want, emptySet())



    private fun sub(got: HirType, want: HirType, assume: Set<Pair<HirType, HirType>>): Boolean {
        val owner = fuelDepth == 0
        if (!fuelEnter(got, want)) { fuelExit(owner); return false }
        return try {
            subtypeBody(got, want, assume)
        } finally {
            fuelExit(owner)
        }
    }

    private fun subtypeBody(got: HirType, want: HirType, assume: Set<Pair<HirType, HirType>>): Boolean = when {
        // 假设集命中（余归纳）：正在证明 (got, want) ⇒ 先假定成立，证其成员（ST-15.5 递归类型正解）
        got to want in assume -> true
        // 自反 / 结构等价（typeEq 内递归终止于有限类型树；别名按名比较）
        typeEq(got, want) -> true
        // t58（R-35）/ ST-6：Nothing 单向真底（want = Nothing 仅由上方 typeEq 命中）
        isNothingT(got) -> true
        // t115（S2，P-11 §3.6 落位前）：元变量占位（脚本模式推导未决的形参/返回/字段槽位）按底类型待——
        // 求解落位前一切使用位点不得因占位失败（占位默认即 Nothing；落位后占位不再出现于判定面）
        got is HirNamedType && got.isMetaSlot() -> true
        // ST-11：Any 真顶（T <: Any）
        want.isAnyExpect() -> true
        // t98（N2 @ref · 规则 2 子类型，根文档 :89-163 矩阵）：@ref 是透传视图包装——
        // @ref T ↔ T 双向相容（:146/:150/:161——不可变互转类型层放行，拷贝/共享由规则 3 分派判定）;内层保留既有
        // @mut 口径：@mut T <: @ref T（共享不可变视图，:120）、@ref @mut T <: @mut T（:131 逆向深拷）;
        // @ref T <: @ref U 内层协变。拒绝 T <: @ref @mut T（不可变值发可写引用——不安全，根文档未给此格）
        got is HirRefType && want is HirRefType -> sub(got.inner, want.inner, assume)
        got is HirRefType -> sub(got.inner, want, assume)
        want is HirRefType -> sub(got, want.inner, assume)
        // t61（ADR-38 ST-11.1）：@mut 单向视图——@mut T <: T（读视图降级）；@mut T <: @mut U 仅当 T = U（不变）；
        // T <: @mut T **不成立**（造格不是子类型——初始化/赋值/借用经 demoteView 于各位自行处理）
        // t64（ADR-41，ST-13.1）：双方 @mut 视图——内层同名同参（实参非空）应用按 σ_mut 逐参分派
        //  （协变→sub、逆变→反向、不变→typeEq）；其余（含无表）回退 typeEq（t61 旧口径）
        got is HirMutType && want is HirMutType -> mutArgsByVariance(got.inner, want.inner, assume)
        got is HirMutType -> sub(got.inner, want, assume)
        want is HirMutType -> false
        // ST-8 联合：got 每个成员注入 want 某成员（got 联合单向拓宽；联合→成员须 is 收窄，不在此给）
        // t51：联合展开计 1 步；t44：got 为具名联合别名时先展开（builtinSig 未展开形态按同一实体）
        got is HirUnionType && want is HirUnionType -> fuelUnionExpand() &&
            got.variants.all { gv -> want.variants.any { wv -> sub(gv, wv, assume) } }
        want is HirUnionType && got is HirNamedType && aliasExpander != null -> fuelUnionExpand() &&
            ((aliasExpander!!(got) as? HirUnionType)?.let { ge ->
                ge.variants.all { gv -> want.variants.any { wv -> sub(gv, wv, assume) } }
            } ?: want.variants.any { sub(got, it, assume) })
        want is HirUnionType -> fuelUnionExpand() && want.variants.any { sub(got, it, assume) }
        // t64（ADR-41，ST-13.1）：同名同参（实参非空）应用——按声明 σ_imm 逐参分派（协变→sub、
        //  逆变→反向 sub、不变→typeEq）；未知（无表条目）→ 约定协变回退。位于特质满足子句之前：
        //  特质应用自身（Sink[Nat] <: Sink[Any]）不再只按名字经父链放行——实参位按变构性核对。
        got is HirNamedType && want is HirNamedType && got.args.isNotEmpty() &&
            got.name == want.name && got.args.size == want.args.size ->
            genericArgsByVariance(got.args, want.args, want.name, false, assume)
        // ST-3/ST-9：满足即子类型——got 满足 want 特质（结构满足），或 got 特质经父链 <: want 特质
        want is HirNamedType && traitSubtype != null && traitSubtype!!(got, want.name) -> true
        // ST-10 数值塔：Nat <: Int <: Rat 单向只升（rank 闭包；Real 不在 rank ⇒ 不入塔）
        got is HirNamedType && want is HirNamedType && got.isNumeric() && want.isNumeric() ->
            (rank(got.name) ?: 0) <= (rank(want.name) ?: 0)
        // 泛型实参位同名同参：逐参协变（List[A] <: List[B] ⟺ A <: B；@mut 不变性由视图规则承担，ST-13.1）
        got is HirNamedType && want is HirNamedType && got.name == want.name && got.args.size == want.args.size ->
            got.args.zip(want.args).all { sub(it.first, it.second, assume) }
        // 2.9 结构体结构满足（宽+深：want 字段齐备且逐字段 got <: want；仅普通结构体，泛型结构体走名义同名位）
        // 判定入守卫：不成立时继续后续位（别名展开等）——when 无落穿，shape 守卫会吞掉后续分支
        got is HirNamedType && want is HirNamedType && structFieldsOf != null &&
            structSub(got, want, assume) -> true
        // t47（ADR-31 R-28）：期望侧为具名联合别名（形参位写法形态 Opt[Nat]）→ 展开重试
        // ctor 实参位/别名接收者分派的实参（expand 后的联合）与未展开形态按同一实体比对；t51：别名展开计 1 步
        want is HirNamedType && aliasExpander != null -> fuelAliasExpand() && run {
            val wantUnion = aliasExpander!!(want)
            if (wantUnion is HirUnionType && !typeEq(want, wantUnion)) sub(got, wantUnion, assume) else false
        }
        // t58（R-36）/ ST-7 函数型变构：参数位**逆变**（want.param <: got.param）、返回位协变；假设集防互追
        got is HirFunType && want is HirFunType ->
            got.params.size == want.params.size &&
                want.params.zip(got.params).all { sub(it.first, it.second, assume + (got to want)) } &&
                sub(got.ret, want.ret, assume + (got to want))
        // 元组：位置等价逐项
        got is HirTupleType && want is HirTupleType && got.items.size == want.items.size ->
            got.items.zip(want.items).all { sub(it.first, it.second, assume + (got to want)) }
        else -> false
    }

    /** 2.9 结构满足：want 的字段在 got 中齐备且逐项 got <: want（宽度扩展 + 深度子类型）。 */
    private fun structSub(
        got: HirNamedType, want: HirNamedType, assume: Set<Pair<HirType, HirType>>,
    ): Boolean {
        val gf = structFieldsOf!!(got.name) ?: return false
        val wf = structFieldsOf!!(want.name) ?: return false
        if (got.name == want.name || wf.isEmpty()) return false   // 同名走泛型位；空 want 不得成结构顶（健全）
        val next = assume + (got to want)
        return wf.all { (fn, ft) ->
            val g = gf.firstOrNull { it.first == fn } ?: return@all false
            sub(g.second, ft, next)
        }
    }

    /** 「实际 → 期望」适配判定（t62 后为**纯包装**）：合成位 R-38 闸（判官绝对严格，R-34；
     *  落点闸对合成位拒绝保持容忍由 reportMismatch 承担）+ subtype 偏序。**不再自带任何特例放行**。 */
    fun adaptOk(got: HirType, want: HirType): Boolean {
        if (got.isSynthetic() || want.isSynthetic()) return false
        return subtype(got, want)
    }

    /** 渲染（诊断消息用；旧 Type.render 口径：NamedType 带实参方括号、函数类型 (A, B) => C）。 */
    fun render(t: HirType?): String = when (t) {
        null -> "(未推导)"
        is HirNamedType -> if (t.args.isEmpty()) t.name else t.name + "[" + t.args.joinToString(", ") { render(it) } + "]"
        is HirQualifiedType -> t.module.joinToString(".") + "::" + render(namedT(t.name, t.args))
        is HirFunType -> {
            val th = if (t.theory.isEmpty()) "" else "[" + t.theory.joinToString(", ") { it.name } + "]"
            val pure = if (t.purity == HirPurity.IMPURE) "@impure " else ""
            th + pure + "(" + t.params.joinToString(", ") { render(it) } + ") => " + render(t.ret)
        }
        is HirTupleType -> "(" + t.items.joinToString(", ") { render(it) } + ")"
        is HirMutType -> "@mut " + render(t.inner)
        is HirAtomicType -> "@atomic " + render(t.inner)
        is HirRefType -> "@ref " + render(t.inner)   // t95（零语义中间态；诊断渲染）
        is HirUnionType -> t.variants.joinToString(" | ") { render(it) }
        is HirErrorType -> "<错误类型:" + t.message + ">"
        is HirSelfType -> "Self"  // P-8/A：卷 TS-29 新增类型节点（被动穷尽补支，渲染文本用）
    }

    // ---------- P7 标准合一（决策 83；v1.0 计划 §9：替换表 + occurs check） ----------

    /** deref 递归展开 sub 链（防环，steps 上限）。 */
    private fun deref(t: HirType, vars: Set<String>, sub: MutableMap<String, HirType>): HirType {
        var cur = t
        var steps = 0
        while (cur is HirNamedType && cur.args.isEmpty() && cur.name in vars && cur.name in sub && steps < 32) {
            cur = sub.getValue(cur.name); steps++
        }
        return cur
    }

    /** T 是否在 t 中出现（仅统计 vars 中的类型变量名；结构递归遍达嵌套）。 */
    private fun occurs(name: String, t: HirType, vars: Set<String>): Boolean = when (t) {
        is HirNamedType -> (t.name == name && t.args.isEmpty()) || t.args.any { occurs(name, it, vars) }
        is HirFunType -> t.params.any { occurs(name, it, vars) } || occurs(name, t.ret, vars)
        is HirTupleType -> t.items.any { occurs(name, it, vars) }
        is HirUnionType -> t.variants.any { occurs(name, it, vars) }
        else -> false
    }

    /**
     * 标准合一（旧 unifyInto 全分支对齐）：
     * 变量 = 名字在 vars 且零实参的 NamedType；变量-变量单向绑定；occurs check 拒无限类型；
     * 结构递归（NamedType/FunType/TupleType）；kind 应用合一（HKT-U2 方案 A，arity 表驱动，
     * 仅一端为构造子形参）；已绑定变量遇更高数值类型走宽松保持；synthetic 不绑定变量。
     */
    fun unifyInto(
        a: HirType, b: HirType, vars: Set<String>, sub: MutableMap<String, HirType>,
        arity: Map<String, Int> = emptyMap(),
    ): Boolean {
        // t51（ADR-33）：计量判定步（扣 1）；耗尽 → 拒绝（false=不合，不静默放行）
        val owner = fuelDepth == 0
        if (!fuelEnter(a, b)) { fuelExit(owner); return false }
        return try {
            unifyIntoBody(a, b, vars, sub, arity)
        } finally {
            fuelExit(owner)
        }
    }

    private fun unifyIntoBody(
        a: HirType, b: HirType, vars: Set<String>, sub: MutableMap<String, HirType>,
        arity: Map<String, Int> = emptyMap(),
    ): Boolean {
        val aa = deref(a, vars, sub)
        val bb = deref(b, vars, sub)
        val aVar = aa is HirNamedType && aa.args.isEmpty() && aa.name in vars
        val bVar = bb is HirNamedType && bb.args.isEmpty() && bb.name in vars
        if (aVar && bVar) {
            if (aa.name != bb.name) sub[aa.name] = bb
            return true
        }
        if (aVar) {
            if (bb.isSynthetic()) return true
            if (occurs(aa.name, bb, vars)) return false
            val cur = sub[aa.name]
            if (cur != null) return looseEq(cur, bb)
            sub[aa.name] = bb
            return true
        }
        if (bVar) {
            if (aa.isSynthetic()) return true
            if (occurs(bb.name, aa, vars)) return false
            val cur = sub[bb.name]
            if (cur != null) return looseEq(cur, aa)
            sub[bb.name] = aa
            return true
        }
        // t47（ADR-31 R-28）：别名形态 ↔ 展开联合的双向合一（Opt[Nat] ↔ None | Some[Nat]）——
        // 先把任一侧的具名联合别名展开成联合再按既有的联合/具名规则继续（别名身份不参与变量求解）
        if (aliasExpander != null) {
            val ae = (aa as? HirNamedType)?.let { aliasExpander!!(it) }
            val be = (bb as? HirNamedType)?.let { aliasExpander!!(it) }
            if (ae is HirUnionType || be is HirUnionType) {
                // t51：别名↔联合双向展开计 1 步（R-28 展开位；发散互追的扣步点）
                if (!fuelUnionExpand()) return false
                val a2 = (ae as? HirUnionType) ?: aa
                val b2 = (be as? HirUnionType) ?: bb
                return unifyInto(a2, b2, vars, sub, arity)
            }
        }
        if (aa is HirNamedType && bb is HirNamedType && aa.args.isNotEmpty() && aa.args.size == bb.args.size) {
            var kvar = aa; var concrete = bb
            val kk = arity[aa.name]; val kc = arity[bb.name]
            if (!(kk != null && kk > 0 && kc == null) && (kc != null && kc > 0 && kk == null)) {
                kvar = bb; concrete = aa
            }
            val ka = arity[kvar.name]
            if (ka != null && ka > 0 && arity[concrete.name] == null) {
                val cur = sub[kvar.name]
                if (cur != null) {
                    if (cur !is HirNamedType || cur.name != concrete.name || cur.args.isNotEmpty()) return false
                } else {
                    if (kvar.name in vars) sub[kvar.name] = namedT(concrete.name)
                    else return false
                }
                return kvar.args.zip(concrete.args).all { unifyInto(it.first, it.second, vars, sub, arity) }
            }
        }
        return when {
            // t41（ADR-28 R-16）：联合成对按位合（同声明序；指数展开拒绝——真冲突在 adaptOk 落诊断）
            aa is HirUnionType && bb is HirUnionType && aa.variants.size == bb.variants.size ->
                aa.variants.zip(bb.variants).all { unifyInto(it.first, it.second, vars, sub, arity) }
            aa is HirFunType && bb is HirFunType ->
                aa.params.zip(bb.params).all { unifyInto(it.first, it.second, vars, sub, arity) } &&
                    unifyInto(aa.ret, bb.ret, vars, sub, arity)
            aa is HirTupleType && bb is HirTupleType && aa.items.size == bb.items.size ->
                aa.items.zip(bb.items).all { unifyInto(it.first, it.second, vars, sub, arity) }
            aa is HirNamedType && bb is HirNamedType && aa.name == bb.name && aa.args.size == bb.args.size ->
                aa.args.zip(bb.args).all { unifyInto(it.first, it.second, vars, sub, arity) }
            else -> looseEq(aa, bb)
        }
    }

    /** v1 类型推导对称 join（旧 TypeInfer.unify：相等/synthetic/数值塔/同名名义）。 */
    fun unify(a: HirType, b: HirType): HirType? = when {
        typeEq(a, b) -> a
        a.isSynthetic() || b.isSynthetic() -> if (a.isSynthetic()) b else a
        a is HirNamedType && b is HirNamedType && a.isNumeric() && b.isNumeric() ->
            if ((rank(a.name) ?: 0) >= (rank(b.name) ?: 0)) a else b
        a.isNominal() && b.isNominal() && a is HirNamedType && b is HirNamedType &&
            a.name == b.name && a.args.size == b.args.size -> a
        else -> null
    }

    /** t67（P-1 修复）：内建算符名集合——builtinOp when 的 keys 镜像（改表须两处同步）。
     * 语义层据此判定符号中缀/符号前缀调用是否走内建口径（true）或函数调用通道（false）。
     * t92（裁决 4 表达式层闭合）：`∧ ∨ → ↔`（命题专用）与 `<->`（退役，R2 留用户自定义通道）
     * 移出——值位不再有原生命题连接词的内建回落。 */
    val builtinOps = setOf("-", "+", "*", "%", "/", "=", "==", "!=", "<", ">", "<=", ">=",
        "!", "¬", "&", "|")

    /** t67：op 是否为内建算符（[]./in 等特判不在此列——它们由 Kind 专用通道产 BinExpr，不涉及符号中缀）。 */
    fun isBuiltinOp(op: String): Boolean = op in builtinOps

    /** 内建运算符（符号函数）的类型签名；返回 null = 表外符号（旧 TypeInfer.builtinOp，P-7）。 */
    fun builtinOp(op: String, argTypes: List<HirType?>): HirType? {
        val ts = argTypes.filterNotNull()
        val bool = namedT("Bool")
        return when (op) {
            // t63（ADR-40 ST-10.1）：减法单独成规则——rank(结果) = max(rank(l), rank(r), 1=Int)；
            // Nat 无符号 ⇒ Nat - Nat 可负：判 Nat 即「类型为 Nat 但值可为负」，证明器据类型推理可为假
            "-" -> if (ts.size == argTypes.size && ts.all { (it as? HirNamedType)?.isNumeric() == true })
                minusSup(ts) else null
            "+", "*", "%" -> if (ts.size == argTypes.size && ts.all { (it as? HirNamedType)?.isNumeric() == true })
                ts.reduce { x, y -> unify(x, y) ?: x } else null
            "/" -> if (ts.size == argTypes.size && ts.all { (it as? HirNamedType)?.isNumeric() == true })
                namedT("Rat") else null
            "=", "==", "!=", "<", ">", "<=", ">=" -> if (ts.size == 2) bool else null
            "!", "¬" -> bool
            "&", "|" -> bool    // t92：∧∨→↔/<-> 的 Bool 回落移除（值位拒绝，见 Elaborator；¬ 是命题层一元保留）
            else -> null
        }
    }

    /**
     * t94（N1 硬前置：lambda 纯度推导）：内建**不纯**名集合——旧实现 Types.kt
     * builtinimpure 表的逐字恢复（26 名）。注：builtinSig 是旧 builtinimpure / builtinPure 两表合并的
     * 类型层投影、**纯度信息在投影中丢失**，故本表单列；两表同源（旧 builtinimpure），改须同步。 */
    val builtinImpureNames = setOf(
        "print", "readLine", "readFile", "writeFile", "getArgs", "sleep", "appendFile", "fileExists", "readDir",
        "timeNow", "rand", "delay", "runBoth", "runSeq", "collectToList", "collectCount", "collectAll",
        "withMutex", "bumpCounter", "bumpPair", "channelFromList", "sendAll", "channelDrain", "channelPipe",
        "sublEval", "sublRun",
    )

    /**
     * 内建函数签名表（旧 Types.kt builtinimpure/builtinPure 的类型层投影，P-7）：
     * 返回 (参数类型, 返回类型)；syntheticT("任意") = 宽容位（旧 print/param 口径）。
     */
    fun builtinSig(name: String): Pair<List<HirType>, HirType>? {
        val any = namedT("Any")   // t58：真通配位（isAnyExpect：期望 Any 接受任意具体类型），不再用合成位走旧放行路径
        val nat = namedT("Nat"); val int = namedT("Int"); val rat = namedT("Rat"); val str = namedT("Str")
        val bool = namedT("Bool"); val nul = namedT("Null")
        fun list(t: HirType) = namedT("List", listOf(t))
        fun opt(t: HirType) = namedT("Optional", listOf(t))
        fun result(ok: HirType, err: HirType) = namedT("Result", listOf(ok, err))
        // t58（ADR-36 R-34 修复侧）：内建泛型位改为**具名类型变量标记**——instCall 以其为 vars 解出
        // （如 collectToList(Flow[(内建T)]) 实参 Flow[Nat] → 解为 Nat，返回 List[Nat]）；
        // 旧 syntheticT("T") 位在 adaptOk 严格化后会被拒（「一律放行」旧路径已删）。
        val one = namedT("(内建T)")
        return when (name) {
            "print" -> listOf(any) to nul
            "readLine" -> emptyList<HirType>() to result(str, str)
            "readFile" -> listOf(str) to result(str, str)
            "writeFile" -> listOf(str, str) to result(nul, str)
            "getArgs" -> emptyList<HirType>() to list(str)
            "sleep" -> listOf(nat) to nul
            "appendFile" -> listOf(str, str) to result(nul, str)
            "fileExists" -> listOf(str) to bool
            "readDir" -> listOf(str) to result(list(str), str)
            "timeNow" -> emptyList<HirType>() to nat
            "rand" -> emptyList<HirType>() to rat
            "delay" -> listOf(nat) to nul
            "runBoth", "runSeq" -> listOf(
                funT(listOf(nul), nul, nid = alloc()), funT(listOf(nul), nul, nid = alloc())) to nul
            "collectToList" -> listOf(namedT("Flow", listOf(one))) to list(one)
            "collectCount" -> listOf(namedT("Flow", listOf(one))) to nat
            "collectAll" -> listOf(namedT("Flow", listOf(one)),
                funT(listOf(one), bool, nid = alloc())) to bool
            "withMutex" -> listOf(namedT("Mutex"), funT(listOf(nul), nul, nid = alloc())) to nul
            "bumpCounter" -> listOf(namedT("Mutex"), HirMutType(one, alloc(), SYNTHETIC_POS, emptyList())) to nul
            "bumpPair" -> listOf(namedT("Mutex"), namedT("Mutex"),
                HirMutType(one, alloc(), SYNTHETIC_POS, emptyList()),
                HirMutType(one, alloc(), SYNTHETIC_POS, emptyList())) to nul
            "channelFromList" -> listOf(list(one)) to namedT("Channel", listOf(one))
            "sendAll" -> listOf(namedT("Channel", listOf(one)), list(one)) to nul
            "channelDrain" -> listOf(namedT("Channel", listOf(one))) to list(one)
            "channelPipe" -> listOf(namedT("Channel", listOf(one)), namedT("Channel", listOf(one))) to nul
            "sublEval" -> listOf(str) to result(one, str)
            "sublRun" -> listOf(str) to result(nul, str)
            "emptyTuple" -> emptyList<HirType>() to namedT("EmptyTuple")
            "singleTuple" -> listOf(one) to namedT("SingleTuple", listOf(one))
            "concat" -> listOf(str, str) to str
            "length" -> listOf(str) to nat
            "charAt" -> listOf(str, nat) to str
            "substring" -> listOf(str, nat, nat) to str
            "strCmp" -> listOf(str, str) to int
            "toStr" -> listOf(one) to str
            "parseNat" -> listOf(str) to opt(nat)
            "toRat" -> listOf(one) to rat
            "toReal" -> listOf(one) to namedT("Real")
            "parseRat" -> listOf(str) to opt(rat)
            "toNat" -> listOf(one) to opt(nat)
            "panic" -> emptyList<HirType>() to namedT("Nothing")
            "in" -> listOf(any, namedT("Range", listOf(one))) to bool
            "abs" -> listOf(rat) to rat
            "floor", "ceil" -> listOf(rat) to int
            "pow" -> listOf(rat, rat) to rat
            "strUpper", "strLower", "strTrim" -> listOf(str) to str
            "startsWith", "endsWith" -> listOf(str, str) to bool
            "strReplace" -> listOf(str, str, str) to str
            "strSplit" -> listOf(str, str) to list(str)
            "strIndexOf" -> listOf(str, str) to opt(nat)
            "mathMin", "mathMax" -> listOf(rat, rat) to rat
            "mathClamp" -> listOf(rat, rat, rat) to rat
            "mathRound", "mathSin", "mathCos", "mathTan" -> listOf(rat) to rat
            "jsonParse" -> listOf(str) to opt(namedT("Json"))
            "jsonGet" -> listOf(namedT("Json"), str) to opt(namedT("Json"))
            "jsonAt" -> listOf(namedT("Json"), nat) to opt(namedT("Json"))
            "jsonLen" -> listOf(namedT("Json")) to opt(nat)
            "jsonStr" -> listOf(namedT("Json")) to opt(str)
            "jsonNum" -> listOf(namedT("Json")) to opt(rat)
            "jsonBool" -> listOf(namedT("Json")) to opt(bool)
            "jsonIsNull", "jsonIsArr" -> listOf(namedT("Json")) to bool
            "jsonToStr" -> listOf(namedT("Json")) to str
            "jsonToList" -> listOf(namedT("Json")) to opt(list(namedT("Json")))
            else -> null
        }
    }

    // ============================ 结论方法（Elaborator 构造期调用） ============================

    /** var 声明：标注 vs 初值核对（旧 checkStmtInner VarStmt 分支，P-7 消息口径）。 */
    fun varAnnotated(name: String, declared: HirType?, initType: HirType, pos: SourceRange): HirType {
        if (declared == null) {
            diag.supplement("D-TYPE-INFERRED", pos, "var " + ident(name) + " 推导为 " + render(initType))
            return initType
        }
        // 数值塔单向（P-7）：Nat→Rat 升兼容，Rat→Nat 降级报错（旧 numericDemoteAt 分支）
        val demoted = initType is HirNamedType && declared is HirNamedType &&
            initType.isNumeric() && declared.isNumeric() &&
            (rank(initType.name) ?: 0) > (rank(declared.name) ?: 0)
        // t62（ST-11.1）：初始化是造格（新建可变单元）→ t64（ADR-41）：got 已是 @mut 视图时按视图对视图
        // （@mut 视图不变性在子类型分派里生效）；否则核 typeof(v) <: demoteView(declared)
        val target = borrowView(initType, declared)
        if (demoted || (!adaptOk(initType, target) && unify(initType, target) == null))
            reportMismatch(initType, declared, pos,
                "var " + name + " 标注了 " + render(declared) + "，但初值是 " + render(initType) + "，两边类型不一致")
        return declared
    }

    /** t60-1：赋值位类型核对（与形参/返回值/var 标注同一条 adaptOk 判据 + 数值塔降级口径，R-38 落点闸同住）。 */
    fun assignTypes(targetT: HirType, valueT: HirType, pos: SourceRange) {
        // 数值塔单向（P-7）：右值降级（Rat→Nat 等）报错，与 varAnnotated 同构
        val demoted = valueT is HirNamedType && targetT is HirNamedType &&
            valueT.isNumeric() && targetT.isNumeric() &&
            (rank(valueT.name) ?: 0) > (rank(targetT.name) ?: 0)
        // t62（ST-11.1）+ t64（ADR-41）：got 为 @mut 视图时视图对视图；否则核 typeof(v) <: demoteView(targetT)
        val target = borrowView(valueT, targetT)
        if (demoted || (!adaptOk(valueT, target) && unify(valueT, target) == null))
            reportMismatch(valueT, targetT, pos,
                "赋值右值 " + render(valueT) + " 与目标 " + render(targetT) + " 不符，两边类型不一致")
    }

    /** t60-3：形参/字段默认值类型核对（声明位，同一判据；E-DEFAULT-TYPE 为 t60 新码，同 E-DEFAULT-ORDER 族）。 */
    fun defaultType(paramName: String, want: HirType, got: HirType, pos: SourceRange) {
        val demoted = got is HirNamedType && want is HirNamedType &&
            got.isNumeric() && want.isNumeric() &&
            (rank(got.name) ?: 0) > (rank(want.name) ?: 0)
        // t62（ST-11.1）+ t64（ADR-41）：got 为 @mut 视图时视图对视图；否则核 typeof(default) <: demoteView(paramT)
        val target = borrowView(got, want)
        if (demoted || (!adaptOk(got, target) && unify(got, target) == null))
            reportMismatch(got, want, pos,
                "默认值 " + render(got) + " 与形参 " + ident(paramName) + " 的类型 " + render(want) + " 不符，两边类型不一致",
                "E-DEFAULT-TYPE")
    }
    /** var 元组解构（决策 91 声明版）：初值须为元组、分量数齐——旧 E-TUPLE-DESTRUCT/E-TUPLE-ARITY。 */
    fun varDestruct(initType: HirType, names: List<String>, pos: SourceRange): List<HirType> {
        val tup = initType as? HirTupleType
        if (tup == null) {
            diag.error("E-TUPLE-DESTRUCT", pos,
                "想解构成元组，但初值是 " + render(initType) + "（不是元组）。解构语法 (a, b) 的初值必须是元组字面量")
            return names.map { syntheticT("分量" + it) }
        }
        if (tup.items.size != names.size) {
            diag.error("E-TUPLE-ARITY", pos, "解构分量 " + names.size + " ≠ 元组元素 " + tup.items.size)
            return names.map { syntheticT("分量" + it) }
        }
        names.forEachIndexed { i, n ->
            diag.supplement("D-TYPE-INFERRED", pos, "var " + ident(n) + " 推导为 " + render(tup.items[i]))
        }
        return tup.items
    }

    /** 双目算符（旧 checkBin 主干：区间/in/内置算符/synthetic 宽容/Str 拼接/名义冲突报错）。 */
    fun binExpr(op: String, leftType: HirType, rightType: HirType, pos: SourceRange): HirType {
        // t61（ADR-38 ST-11.1）：@mut T 在类型计算中按 T 分类（demoteView 先降级，再走既有判定）
        val l = demoteView(leftType)
        val r = demoteView(rightType)
        if (op == "..") {
            if ((l as? HirNamedType)?.isNumeric() != true || (r as? HirNamedType)?.isNumeric() != true)
                reportMismatch(l, r, pos, "区间 .. 的端点须为数值，实际 " + render(l) + " 与 " + render(r))
            return namedT("Range", listOf(l))
        }
        if (op == "in") {
            if ((r as? HirNamedType)?.name != "Range")
                reportMismatch(r, null, pos, "in 右侧须是区间 lo..hi 或 Range[T] 值（v3.0 V1；集合成员待 v3.1）")
            return namedT("Bool")
        }
        val bt = builtinOp(op, listOf(l, r))
        if (bt != null) {
            // 比较族：名义操作数两边须兼容（旧 checkBin「中缀 op: A 与 B 不匹配」分支）
            if (op == "=" || op == "==" || op == "!=" || op == "<" || op == ">" || op == "<=" || op == ">=") {
                if (l.isNominal() && r.isNominal() && !looseEq(l, r))
                    if (!fuelExhaustedNow()) diag.error("E-TYPE-MISMATCH", pos, "中缀 " + op + ": " + render(l) + " 与 " + render(r) + " 不匹配")
                else if (l.isNominal() != r.isNominal() && !l.isSynthetic() && !r.isSynthetic() &&
                    (l as? HirNamedType)?.isNumeric() != true && (r as? HirNamedType)?.isNumeric() != true)
                    if (!fuelExhaustedNow()) diag.error("E-TYPE-MISMATCH", pos, "中缀 " + op + ": " + render(l) + " 与 " + render(r) + " 不匹配")
            }
            return bt
        }
        if (l.isSynthetic() || r.isSynthetic()) return syntheticT("中缀" + op)
        if (op == "+" && l is HirNamedType && r is HirNamedType && l.name == "Str" && r.name == "Str") return namedT("Str")
        if (l.isNominal() && r.isNominal() && !looseEq(l, r))
            if (!fuelExhaustedNow()) diag.error("E-TYPE-MISMATCH", pos, "中缀 " + op + ": " + render(l) + " 与 " + render(r) + " 不匹配")
        // t61（ADR-38 ST-11.1）：不猜类型——表外算符/不可分类操作数落诊断 + 合成位（旧 return namedT("Bool") 已删）
        diag.error("E-OP-UNCLASSIFIED", pos,
            "中缀 " + op + ": " + render(l) + " 与 " + render(r) + " 无法定类（不在内建算符表内，且非可兼容名义对）")
        return syntheticT("中缀" + op)
    }

    /** 一元算符：-/¬ 单目取负 → 与减法同族（t63 ADR-40 ST-10.1：Nat 取负可负 ⇒ rank 至少 Int），
     *  其余（!/¬ 命题联结词）→ Bool（旧 checkExpr UniExpr 分支）。 */
    fun uniExpr(op: String, operandType: HirType): HirType {
        if (op == "-") {
            val r = (operandType as? HirNamedType)?.name?.let { rank(it) }
            return if (r != null && r < 1) namedT("Int") else operandType
        }
        return namedT("Bool")
    }

    /** t63（ST-10.1）：减法上确界升档——操作数 sup（unify 取塔上确界）若 rank < Int 则提为 Int。 */
    private fun minusSup(ts: List<HirType>): HirType {
        val sup = ts.reduce { x, y -> unify(x, y) ?: x }
        val r = (sup as? HirNamedType)?.name?.let { rank(it) }
        return if (r != null && r < 1) namedT("Int") else sup
    }

    /**
     * 函数调用核对（旧 CallCheck 主干消息，P-7）：
     * E-TYPE-MISMATCH 缺实参（按形参名）/ E-ARITY 多了 / 逐实参核对（adaptOk 宽容口径）。
     * 决策 69 扩展（ADR-15）+ t45（ADR-30 R-24）：尾部默认参数可省略——缺省位必须全部
     * 有默认值（位置前缀供给），首个无默认值的缺位报 E-TYPE-MISMATCH 缺实参（既有口径）。
     */
    /**
     * t48（ADR-32 R-30）变长形参支持：末位 @list 形参吸收 0..n 个实参（variadic[i]=true）。
     * - arity：变长位之前的形参必填（t45 缺省口径不变）；变长位之后无上限（不再报 E-ARITY 多了）；
     * - 类型核对：变长位起的每个实参按**元素类型**（paramTypes[varIdx]，解集回代后）核对；
     * - 理论参数：变长位的每个实参逐一参与 unifyInto（首解为准，后续须一致——不一致落 E-TYPE-MISMATCH）；
     * - 空变长调用（listOf()）时未解理论参数回退 synthetic（下游宽容，避免自由变量外泄）。
     */
    fun callFun(
        name: String, paramTypes: List<HirType>, paramNames: List<String>?,
        defaults: List<Boolean> = emptyList(), retType: HirType?,
        argTypes: List<HirType>, pos: SourceRange, theoryNames: List<String> = emptyList(),
        explicitTps: List<HirType> = emptyList(),
        variadic: List<Boolean> = emptyList(),
    ): HirType {
        val varIdx = variadic.lastIndexOf(true)
        // t45（ADR-30 R-24）：位置实参只能从尾部连续省略——缺省位（argTypes.size 起）必须全部
        // 有默认值；首个（及之后）无默认值的缺位落既有 E-TYPE-MISMATCH 缺实参口径。命名填补路径
        // 已在 Elaborator 侧按声明处环境补齐（argTypes.size == paramTypes.size），此处按位置前缀供给判定。
        // t48（R-30）：变长位在末位时仅有其前形参必填（变长位吸收 0..n，不落缺实参/多了）。
        if (varIdx < 0) {
            if (argTypes.size < paramTypes.size) {
                val missingIdx = (argTypes.size until paramTypes.size).filter { i -> defaults.getOrNull(i) != true }
                if (missingIdx.isNotEmpty()) {
                    val missing = missingIdx.joinToString(", ") { i -> paramNames?.getOrNull(i)?.let { nm -> ident(nm) } ?: "#" + (i + 1) }
                    if (!fuelExhaustedNow()) diag.error("E-TYPE-MISMATCH", pos, "函数 " + ident(name) + " 缺实参：" + missing)
                }
            } else if (argTypes.size > paramTypes.size) {
                diag.error("E-ARITY", pos, "函数 " + ident(name) + " 只想要 " + paramTypes.size + " 个实参，但你传了 " + argTypes.size + " 个（多了）")
            }
        } else {
            if (argTypes.size < varIdx) {
                val missingIdx = (argTypes.size until varIdx).filter { i -> defaults.getOrNull(i) != true }
                if (missingIdx.isNotEmpty()) {
                    val missing = missingIdx.joinToString(", ") { i -> paramNames?.getOrNull(i)?.let { nm -> ident(nm) } ?: "#" + (i + 1) }
                    if (!fuelExhaustedNow()) diag.error("E-TYPE-MISMATCH", pos, "函数 " + ident(name) + " 缺实参：" + missing)
                }
            }
        }
        // t18（ADR-17）：泛型函数调用同构——实参先解出理论参数，形参/返回类型按解集回代后核对。
        // B8（t23，ADR-21）：显式类型实参先绑定（显式优先+不覆盖），冲突在形参位核对处落 E-TYPE-MISMATCH。
        val (solved, params) = instCall(paramTypes, theoryNames, argTypes, explicitTps, varIdx)
        // 有效形参表：变长位起的实参按元素类型核对（保留实际实参个数——不做合成压扁，D-9）
        val checked = if (varIdx < 0) argTypes.zip(params)
            else argTypes.mapIndexed { i, _ -> if (i < varIdx) params.getOrNull(i) else params.getOrNull(varIdx) }
                .let { eff -> argTypes.zip(eff) }
        checked.forEachIndexed { i, p ->
            val formal = p.second
            if (formal == null) return@forEachIndexed
            // t62（ST-11.1）+ t64（ADR-41）：实参位借用——got 为 @mut 视图时视图对视图（@mut 实参位不变性
            // 经子类型分派生效）；否则核 arg <: demoteView(formal)（型至 @mut 形参不构成真子类型）
            val formal0 = borrowView(p.first, formal)
            // D-11（R-08 配套）:函数型实参的**元数一致性**——元数是结构信息,与推导完成度无关,
            // 不受 synthetic 容忍位（reportMismatch 的未完成容忍）保护:0 参 call 打单参 lambda 等
            // 形态必落诊断（used canonical:applyZero({ _ + 1 }) 的调用位镜像）
            val argT = p.first
            val formalT = formal
            if (argT is HirFunType && formalT is HirFunType && argT.params.size != formalT.params.size)
                diag.error("E-ARITY", pos, "函数实参元数不符:实参 " + render(argT) + " 有 " +
                    argT.params.size + " 个参数,形参 " + render(formalT) + " 期望 " + formalT.params.size + " 个（D-11）")
            if (!adaptOk(p.first, formal0))
                reportMismatch(p.first, formal, pos, "实参 " + (i + 1) + " " + render(p.first) + " 与形参 " + render(formal) + " 不符")
        }
        // t48（R-30）：变长空实参（listOf()）——未解理论参数外泄会以自由类型名污染调用类型，回退 synthetic
        val solvedRet = if (varIdx < 0) solved else {
            val full = LinkedHashMap(solved)
            theoryNames.forEach { n -> if (n !in full) full[n] = syntheticT("变长空实参（" + n + "）") }
            full
        }
        return retType?.let { it.substTps(solvedRet) } ?: syntheticT("函数" + name)
    }

    // ============================ t18 调用点泛型实例化（ADR-17） ============================

    /** 实例化序号（TypeInfer 实例级——跨编译不可共享）。 */
    private var instSeq = 0

    /** 换名前缀（_iN.名）：未解变量位的识别口径。 */
    private val FRESH_TP_PREFIX = Regex("^_i\\d+\\.")

    /** 形式类型参数 → 新鲜变量名（点号杜绝与任何用户类型名碰撞）。 */
    private fun freshTpName(base: String): String = "_i" + (instSeq++) + "." + base

    /** 换名表（形式类型参数 → 新鲜变量）。 */
    fun tpRenaming(formals: List<String>): Map<String, String> =
        formals.associateWith { freshTpName(it) }

    /** 形式类型参数零实参位替换为新鲜变量（换名）；其余结构递归重建。 */
    private fun HirType.renameTps(renaming: Map<String, String>): HirType = when (this) {
        is HirNamedType -> {
            val n = if (args.isEmpty()) renaming[name] else null
            if (n != null) namedT(n)
            else HirNamedType(name, args.map { it.renameTps(renaming) }, null, alloc(), SYNTHETIC_POS, emptyList())
        }
        is HirFunType -> HirFunType(
            params.map { it.renameTps(renaming) }, ret.renameTps(renaming), purity, theory, context,
            alloc(), SYNTHETIC_POS, emptyList())
        is HirTupleType -> HirTupleType(items.map { it.renameTps(renaming) }, alloc(), SYNTHETIC_POS, emptyList())
        is HirMutType -> HirMutType(inner.renameTps(renaming), alloc(), SYNTHETIC_POS, emptyList())
        is HirRefType -> HirRefType(inner.renameTps(renaming), alloc(), SYNTHETIC_POS, emptyList())   // t95
        is HirAtomicType -> HirAtomicType(inner.renameTps(renaming), alloc(), SYNTHETIC_POS, emptyList())
        // t41（ADR-28 R-16）：联合成员零实参理论参数同换名（否则泛型联合形参解不出）
        is HirUnionType -> HirUnionType(variants.map { it.renameTps(renaming) }, alloc(), SYNTHETIC_POS, emptyList())
        else -> this
    }

    /** 形式类型参数零实参位按解集回代；其余结构递归重建。 */
    private fun HirType.substTps(subst: Map<String, HirType>): HirType = when (this) {
        is HirNamedType -> {
            val n = if (args.isEmpty()) subst[name] else null
            if (n != null) n
            else HirNamedType(name, args.map { it.substTps(subst) }, null, alloc(), SYNTHETIC_POS, emptyList())
        }
        is HirFunType -> HirFunType(
            params.map { it.substTps(subst) }, ret.substTps(subst), purity, theory, context,
            alloc(), SYNTHETIC_POS, emptyList())
        is HirTupleType -> HirTupleType(items.map { it.substTps(subst) }, alloc(), SYNTHETIC_POS, emptyList())
        is HirMutType -> HirMutType(inner.substTps(subst), alloc(), SYNTHETIC_POS, emptyList())
        is HirRefType -> HirRefType(inner.substTps(subst), alloc(), SYNTHETIC_POS, emptyList())   // t95
        is HirAtomicType -> HirAtomicType(inner.substTps(subst), alloc(), SYNTHETIC_POS, emptyList())
        // t41：联合成员按解集回代（泛型联合返回类型实例化）
        is HirUnionType -> HirUnionType(variants.map { it.substTps(subst) }, alloc(), SYNTHETIC_POS, emptyList())
        else -> this
    }

    /**
     * 调用点实例化（旧 checkCtorCall/checkFnCall 的 extractTpBinding+substT 口径，P-7）：
     * 形参/字段中的形式类型参数先换成新鲜推导变量，与实参逐位合一（unifyInto 只为收集绑定——
     * 兼容位/synthetic 宽容保持不变；真不符由调用方的 adaptOk 落诊断），再按解集回代形参/字段。
     * 返回 (形式参数名→解, 回代后的形参/字段类型)。
     */
    fun instCall(
        formalTypes: List<HirType>, formalTps: List<String>, argTypes: List<HirType>,
        explicit: List<HirType> = emptyList(), varargIdx: Int = -1,
    ): Pair<Map<String, HirType>, List<HirType>> {
        if (formalTps.isEmpty()) return emptyMap<String, HirType>() to formalTypes
        val renaming = tpRenaming(formalTps)
        val vars = renaming.values.toSet()
        val sub = LinkedHashMap<String, HirType>()
        // B8（t23，ADR-21）：显式类型实参先绑定（形式语义01 §2.4/§4「显式优先，一次绑定不覆盖」）——
        // unifyInto 对已绑定变量走 looseEq 一致性而不改写，故冲突不在合一处静默、而在形参位核对处落诊断
        explicit.forEachIndexed { i, et -> if (i < formalTps.size) sub[renaming.getValue(formalTps[i])] = et }
        // t48（ADR-32 R-30）：变长位（varargIdx >= 0）——超出形参个数的尾部实参逐一与变长形参（元素类型）合一，
        // 首解为准（unifyInto 已绑定变量走 looseEq 一致性），不一致由调用位 adaptOk 落 E-TYPE-MISMATCH。
        val effFormals = formalTypes.map { it.renameTps(renaming) }
        if (varargIdx < 0) {
            argTypes.zip(effFormals).forEach { (a, f) -> unifyInto(a, f, vars, sub) }
        } else {
            argTypes.forEachIndexed { i, a ->
                val f = if (i < varargIdx) effFormals[i] else effFormals.getOrNull(varargIdx) ?: return@forEachIndexed
                unifyInto(a, f, vars, sub)
            }
        }
        val solved = LinkedHashMap<String, HirType>()
        formalTps.forEach { n -> sub[renaming[n]]?.let { solved[n] = deref(it, vars, sub) } }
        return solved to formalTypes.map { it.substTps(solved) }
    }

    /**
     * B9（ADR-22）：lambda 实参两遍定型的期望形参位——
     * 非 lambda 实参与显式类型实参先经 instCall 解理论参数；已解位按解集回代，
     * 未解位（换名后的新鲜变量 _iN.名）落 synthetic：体可按未知处理，
     * 真不符由调用方全量 instCall/adaptOk 落诊断，不在此放宽。
     */
    fun lambdaExpectFormals(
        formalTypes: List<HirType>, formalTps: List<String>, argTypes: List<HirType>,
        explicit: List<HirType> = emptyList(),
    ): List<HirType> {
        if (formalTps.isEmpty()) return formalTypes
        val (_, params) = instCall(formalTypes, formalTps, argTypes, explicit)
        return params.map { it.cleanTpVars() }
    }

    /** 换名后未解的新鲜变量（_iN.名）落 synthetic，其余结构递归重建。 */
    private fun HirType.cleanTpVars(): HirType = when (this) {
        is HirNamedType -> {
            val m = FRESH_TP_PREFIX.find(name)?.value
            if (args.isEmpty() && m != null) syntheticT(name.substring(m.length))
            else HirNamedType(name, args.map { it.cleanTpVars() }, null, alloc(), SYNTHETIC_POS, emptyList())
        }
        is HirFunType -> HirFunType(
            params.map { it.cleanTpVars() }, ret.cleanTpVars(), purity, theory, context,
            alloc(), SYNTHETIC_POS, emptyList())
        is HirTupleType -> HirTupleType(items.map { it.cleanTpVars() }, alloc(), SYNTHETIC_POS, emptyList())
        is HirMutType -> HirMutType(inner.cleanTpVars(), alloc(), SYNTHETIC_POS, emptyList())
        is HirRefType -> HirRefType(inner.cleanTpVars(), alloc(), SYNTHETIC_POS, emptyList())   // t95
        is HirAtomicType -> HirAtomicType(inner.cleanTpVars(), alloc(), SYNTHETIC_POS, emptyList())
        else -> this
    }

    /**
     * 构造子调用核对（旧 CallCheck ctor 分支 + t18 泛型实例化，ADR-17）：
     * 形式类型参数先实例化为新鲜推导变量 → 实参合一收集解集 → 字段核对/消息用回代后的字段；
     * 未解的裸类型参数字段跳过（旧 isTP 口径）；结果类型的类型参数位携带解或 synthetic。
     *
     * 位置式 arity 口径（B4/B5，ADR-19 §二）：fieldDefaults 空表=枚举构造子精确 arity
     * （旧 checkCtorCall）；非空=结构体构造入口，有默认值的尾字段可省（旧 checkStructCall O3）。
     */
    fun callCtor(
        ctorName: String, enumName: String, fieldTypes: List<HirType>,
        theoryNames: List<String>, argTypes: List<HirType>, pos: SourceRange,
        fieldDefaults: List<Boolean> = emptyList(), explicitTps: List<HirType> = emptyList(),
    ): HirType {
        // 位置式供给是前缀：必给量 = 字段数 - 尾部连续有默认值的后缀长（旧 checkStructCall 的 drop(any !default) 口径）
        val required = if (fieldDefaults.isEmpty()) fieldTypes.size
        else fieldTypes.size - fieldDefaults.takeLastWhile { it }.size
        when {
            argTypes.size > fieldTypes.size ->
                if (!fuelExhaustedNow()) diag.error("E-TYPE-MISMATCH", pos, "构造子 " + ident(ctorName) + " 期望 " + fieldTypes.size + " 实参，实际 " + argTypes.size)
            argTypes.size != required && fieldDefaults.isEmpty() ->
                if (!fuelExhaustedNow()) diag.error("E-TYPE-MISMATCH", pos, "构造子 " + ident(ctorName) + " 期望 " + fieldTypes.size + " 实参，实际 " + argTypes.size)
            argTypes.size < required ->
                diag.error("E-FIELD-MISSING", pos, "构造 " + ident(ctorName) + " 缺尾部字段且无默认值")
        }
        val (solved, fields) = instCall(fieldTypes, theoryNames, argTypes, explicitTps)
        argTypes.zip(fields).forEach { p ->
            val f = p.second
            val unsolvedTp = f is HirNamedType && f.args.isEmpty() && f.name in theoryNames
            if (!unsolvedTp && !adaptOk(p.first, borrowView(p.first, f)))
                reportMismatch(p.first, f, pos, "构造子 " + ident(ctorName) + " 实参 " + render(p.first) + " 与字段 " + render(p.second) + " 不符")
        }
        return namedT(enumName, theoryNames.map { solved[it] ?: syntheticT("类型参数" + it) })
    }

    /** 结构体命名构造核对（旧 CallCheck checkStructCtor：缺字段/重复字段/逐字段）。
     *  fieldDefaults 与 fieldTypes 同序：有默认值的字段可缺省（旧 CallCheck:50 的 fp.default == null 口径，B5/ADR-19）。 */
    fun structCtor(
        structName: String, fieldTypes: List<Pair<String, HirType>>, fieldDefaults: List<Boolean>,
        assignNames: List<String>, assignTypes: List<HirType>, pos: SourceRange,
    ): HirType {
        val seen = HashSet<String>()
        assignNames.forEachIndexed { i, fn ->
            if (!seen.add(fn)) diag.error("E-DUP-FIELD", pos, "构造 " + ident(structName) + " 重复字段 " + ident(fn))
            val want = fieldTypes.firstOrNull { it.first == fn }
            if (want == null)
                diag.error("E-UNBOUND-NAME", pos, "结构体 " + ident(structName) + " 无字段 " + ident(fn))
            else if (!adaptOk(assignTypes[i], borrowView(assignTypes[i], want.second)))
                reportMismatch(assignTypes[i], want.second, pos,
                    "构造 " + ident(structName) + " 字段 " + ident(fn) + " 的实参 " + render(assignTypes[i]) + " 与 " + render(want.second) + " 不符")
        }
        fieldTypes.forEachIndexed { i, (fn, _) ->
            if (fn !in seen && !fieldDefaults[i]) diag.error("E-FIELD-MISSING", pos, "构造 " + ident(structName) + " 缺字段 " + ident(fn) + " 且无默认值")
        }
        return namedT(structName)
    }

    /** 实例化调用的类型参数个数核对（旧 CallCheck：函数类型 N 个类型参数，显式给了 M）。 */
    fun instArity(name: String, theory: Int, given: Int, pos: SourceRange) {
        if (theory != given)
            if (!fuelExhaustedNow()) diag.error("E-TYPE-MISMATCH", pos, "函数 " + ident(name) + " 有 " + theory + " 个类型参数，显式给了 " + given)
    }

    /** 函数值引用（旧 checkExpr FunRefExpr 分支：E-NO-METHOD；receiverBound 时首参已绑定）。 */
    fun funRef(
        targetType: HirType, name: String, found: Boolean, paramTypes: List<HirType>,
        retType: HirType?, receiverBound: Boolean, pos: SourceRange,
    ): HirType {
        if (!found) {
            diag.error("E-NO-METHOD", pos, "类型 " + render(targetType) + " 无方法 " + ident(name))
            return syntheticT("函数引用" + name)
        }
        val ps = if (receiverBound) paramTypes.drop(1) else paramTypes
        return HirFunType(ps, retType ?: namedT("Null"), HirPurity.PURE, emptyList(), null, alloc(), pos, emptyList())
    }

    /** return 语句（旧 ReturnStmt 分支：E-RETURN-OUTSIDE / 裸 return / 值类型核对）。 */
    fun returnStmt(retType: HirType?, valueType: HirType?, inFunBody: Boolean, pos: SourceRange) {
        if (!inFunBody) {
            diag.error("E-RETURN-OUTSIDE", pos, "return 只能写在函数体或 lambda 体的语句里。不能写在表达式里（比如 var x = return 1 这种就不行）")
            return
        }
        if (retType == null) return
        if (valueType == null) {
            if (retType !is HirNamedType || retType.name != "Null")
                diag.error("E-RETURN-TYPE", pos, "裸 return 要求函数返回 Null，实际声明 " + render(retType))
        } else if (!adaptOk(valueType, retType))
            diag.error("E-RETURN-TYPE", pos, "return 值类型 " + render(valueType) + " 与声明 " + render(retType) + " 不符")
    }

    /**
     * 函数体核对（旧 checkFun 收尾段，P-7 消息）：
     * 块体显式非 Null 返回 → 须所有路径 return（E-MISSING-RETURN；v1 见 sawReturn 简化，ADR-11）；
     * 体为非密封 when → E-NON-SEALED-MATCH；否则体类型 vs 声明（E-TYPE-MISMATCH）。
     */
    fun funBody(
        name: String, declared: HirType?, bodyType: HirType, isBlockBody: Boolean, sawReturn: Boolean,
        pos: SourceRange,
        /** t115（S2）：返回位为未决元变量时体级核对暂缓——判定落位由 Elaborator 求解环收口（P-11 §3.3），
         *  本位不产生半成品诊断（占位按底语义穿过；落位后不再有占位）。 */
        deferredReturn: Boolean = false,
    ) {
        if (deferredReturn) return
        val eff = declared ?: namedT("Null")
        val isNullRet = eff is HirNamedType && eff.name == "Null"
        if (isBlockBody && declared != null && !isNullRet && !sawReturn) {
            diag.error("E-MISSING-RETURN", pos,
                "函数 " + ident(name) + " 声明返回 " + render(declared) + "，块体未在所有路径 return（Kotlin 语义：请写 return，或改用 = 表达式 表达式体）")
            return
        }
        if (isBlockBody) return   // 块体：逐 return 已由 returnStmt 核对，体级不重复诊断
        if (bodyType.isUnsealed() && !isNullRet) {
            diag.error("E-NON-SEALED-MATCH", pos,
                "函数 " + ident(name) + " 的返回值来自 when 表达式，但分支不穷尽（值可能为空 Null）。补全所有构造子分支，或加 else 兜底")
            return
        }
        // t61（ADR-38 ST-11.1）：返回位不造格——subtype 内 T <: @mut T 不成立（plain 值不升格为视图）
        if (!adaptOk(bodyType, eff) && !(isNullRet && isBlockBody))
            reportMismatch(bodyType, eff, pos, "函数 " + ident(name) + " 声明返回 " + render(eff) + "，但函数体是 " + render(bodyType) + "，两边类型不一致")
    }

    /** when 表达式穷尽性提示（旧 WhenCheck W-NON-EXHAUSTIVE：值类型退化 Null）。 */
    fun whenNonExhaustive(
        pos: SourceRange, hasElse: Boolean, subjectSealed: Boolean, coveredAll: Boolean, reason: String? = null,
    ) {
        if (hasElse) return
        if (!subjectSealed || !coveredAll)
            diag.warn("W-NON-EXHAUSTIVE", pos, reason
                ?: "when 非穷尽（缺 else 或覆盖不全），作为表达式时值类型退化为 Null")
    }

    /** 结构体字段缺失专错（旧 checkExpr FieldExpr 分支）。 */
    fun fieldMissing(structName: String, fieldName: String, pos: SourceRange) {
        diag.error("E-UNBOUND-NAME", pos, "结构体 " + ident(structName) + " 无字段 " + ident(fieldName))
    }
}

/** HirFunType 的工程化缺省构造（避免本文件处处传 nid/pos 样板）。 */
private fun funT(
    params: List<HirType>, ret: HirType, purity: HirPurity = HirPurity.PURE,
    nid: NodeId, pos: SourceRange = SYNTHETIC_POS,
): HirFunType = HirFunType(params, ret, purity, emptyList(), null, nid, pos, emptyList())
