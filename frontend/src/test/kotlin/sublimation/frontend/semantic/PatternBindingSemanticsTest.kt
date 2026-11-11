package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t33（卷 TS-12/TS-14 三形态等价）：when 模式绑定变量的静态类型——
 * 修前直接调用 f(x) 落 E-UNBOUND-NAME、方法糖 x.f() 静默降级为合成类型；修后两者均走扩展桶。
 */
class PatternBindingSemanticsTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "t33.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return hir to bag
    }

    private fun errs(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }
    private fun errMsgs(bag: DiagBag) = errs(bag).joinToString("\n") { it.code + ":" + it.message }
    private fun stmts(hir: HirFile) = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt }
    private fun lastVar(hir: HirFile) = stmts(hir).filterIsInstance<HirVarStmt>().last()

    /** when 内首个扩展调用（body 层）——用于三形态对齐断言。 */
    private fun firstWhenCall(hir: HirFile): HirCall? {
        var found: HirCall? = null
        fun walk(e: sublimation.frontend.hir.HirExpr) {
            if (found != null) return
            when (e) {
                is HirCall -> { found = e; e.args.forEach { walk(it) } }
                is HirWhen -> e.arms.forEach { a -> a.body?.let { walk(it) } }
                is sublimation.frontend.hir.HirField -> walk(e.target)
                else -> { }
            }
        }
        lastVar(hir).value.let { walk(it) }
        return found
    }

    // t41（ADR-28）：enum Box 废除 → 联合形态（struct Some + struct None + type Box）
    private val boxSrc = "struct Some(x: Nat)\nstruct None()\ntype Box = Some | None\nfun Nat.succ(): Nat = self + 1\nvar v = Some(1)\nvar z = when (v) {\n    is Some(x) -> succ(x)\n    None -> 0\n}"

    // ---- 修后行为（直接调用 / 方法糖 / 类型限定三形态等价） ----

    @Test
    fun directCallOnPatternBoundResolvesExtension() {
        val (hir, bag) = script(boxSrc)
        assertFalse(errs(bag).isNotEmpty(), "修前此样例落 E-UNBOUND-NAME；修后应零错。actual=" + errMsgs(bag))
        val call = firstWhenCall(hir)
        assertTrue(call != null && !call.methodSugar, "直接调用形态")
        val ret = call?.type as? sublimation.frontend.hir.HirNamedType
        assertEquals("Nat", ret?.name, "走扩展桶：类型为 Nat（修前为合成体（推导:调用succ））")
    }

    @Test
    fun sugarCallOnPatternBoundResolvesExtension() {
        val (hir, bag) = script(boxSrc.replace("succ(x)", "x.succ()"))
        assertFalse(errs(bag).isNotEmpty(), "actual=" + errMsgs(bag))
        val call = firstWhenCall(hir)
        assertTrue(call != null && call.methodSugar, "方法糖形态")
        val ret = call?.type as? sublimation.frontend.hir.HirNamedType
        assertEquals("Nat", ret?.name, "走扩展桶：类型为 Nat（修前为合成体（推导:方法succ）静默降级）")
    }

    @Test
    fun qualifiedCallOnPatternBoundResolvesExtension() {
        val (hir, bag) = script(boxSrc.replace("succ(x)", "Nat::succ(x)"))
        assertFalse(errs(bag).isNotEmpty(), "类型限定形态；actual=" + errMsgs(bag))
        val call = firstWhenCall(hir)
        val ret = call?.type as? sublimation.frontend.hir.HirNamedType
        assertEquals("Nat", ret?.name)
    }

    // ---- TS-14 自由函数优先（不回退） ----

    @Test
    fun freeFunWinsOverExtForPatternBoundArg1() {
        val src = "struct Some(x: Nat)\n" +
            "struct None()\n" +
            "type Box = Some | None\n" +
            "fun h(x: Nat): Nat = x\n" +
            "fun Nat.h(x: Nat, y: Nat): Nat = x\n" +
            "var v = Some(1)\n" +
            "var z = when (v) {\n" +
            "    is Some(x) -> h(x, 1)\n" +
            "    None -> 0\n" +
            "}"
        val (hir, bag) = script(src)
        assertEquals(listOf("E-ARITY"), errs(bag).map { it.code },
            "TS-14：同名自由函数存在即归自由函数（E-ARITY），不回退扩展桶；actual=" + errMsgs(bag))
    }

    // ---- 泛型枚举：主题实例化（不泄漏枚举自身理论参数名） ----

    @Test
    fun genericEnumPatternInstantiatesBySubject() {
        // t41：泛型枚举 → 泛型联合（type Box[T] = Some[T] | None）；None 为空构造子单例，裸名构造
        val src = "struct Some[T](x: T)\n" +
            "struct None()\n" +
            "type Box[T] = Some[T] | None\n" +
            "fun inc(x: Nat): Nat = x + 1\n" +
            "fun Box[a].mapSucc[a](f: (a) => a): Box[a] = when (self) {\n" +
            "    is Some(v) -> Some(f(v))\n" +
            "    None -> None\n" +
            "}\n" +
            // t41：值过 Box[Nat] 形参（方法糖接收者按 Box 头解析——联合实参位经别名实参代入）
            "fun app(b: Box[Nat]): Box[Nat] = b.mapSucc(inc)\n" +
            "var v: Box[Nat] = Some(1)\n" +
            "var z = app(v)"
        val (hir, bag) = script(src)
        assertFalse(errs(bag).isNotEmpty(), "enum Box[T] 的 T 按主题 self: Box[a] 实例化为 a（不泄漏 T）；actual=" + errMsgs(bag))
    }

    @Test
    fun concreteSubjectInstantiatesFieldTypes() {
        val src = "struct Some[T](x: T)\n" +
            "struct None()\n" +
            "type Box[T] = Some[T] | None\n" +
            "fun Nat.succ(): Nat = self + 1\n" +
            "var v: Box[Nat] = Some(1)\n" +
            "var z = when (v) {\n" +
            "    is Some(x) -> x.succ()\n" +
            "    None -> 0\n" +
            "}"
        val (hir, bag) = script(src)
        assertFalse(errs(bag).isNotEmpty(), "主题 Box[Nat] → x: Nat（方法糖走扩展桶 Nat.succ）；actual=" + errMsgs(bag))
        val ret = firstWhenCall(hir)?.type as? sublimation.frontend.hir.HirNamedType
        assertEquals("Nat", ret?.name)
    }
}
