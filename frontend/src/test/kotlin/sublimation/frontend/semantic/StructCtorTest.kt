package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirTop
import sublimation.frontend.hir.HirBot
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 结构体构造子族与程序面 ⊤/⊥ 验收（t20；B4/B5/C1，ADR-19）。
 *
 * - B4：结构体名即位置式构造入口——Collector 把结构体名登记入 ctor 分表（与枚举 ctor 同通道），
 *   callExpr 的 CTOR 分支按 enumDecls/structDecls 取形参（泛型回代同理）；
 * - B5：位置式与命名构造核对共享同一套 required 计算——有默认值的尾字段可省（旧 CallCheck:50
 *   的 fp.default == null 与 checkStructCall O3 口径），省略无默认值字段落 E-FIELD-MISSING；
 * - C1：S-34 初等项含 ⊤/⊥，程序位为 Bool 字面量（旧 Checker:1006），命题位仍归 HirPTop/HirPBot。
 */
class StructCtorTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "struct-ctor.subl")
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

    private fun errCodes(bag: DiagBag): List<String> =
        bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun varStmts(hir: HirFile): List<HirVarStmt> =
        hir.entries.filterIsInstance<HirStmtEntry>().map { it.stmt }.filterIsInstance<HirVarStmt>()

    @Test
    fun `B4 位置式结构体构造解析通`() {
        val src = "struct User(name: Str = \"u\", age: Nat = 0)\nvar u = User(\"alice\", 30)"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "结构体名作为位置式构造入口应解析（B4）")
        val call = varStmts(hir).first().value as HirCall
        assertEquals("User", (call.type as HirNamedType).name)
    }

    @Test
    fun `B4 泛型结构体位置式构造回代类型参数`() {
        val src = "struct Pair[A, B](fst: A, snd: B)\nvar p = Pair(1, \"s\")"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "泛型结构体构造按实参推导类型参数（旧 HKT-2 口径）")
        val call = varStmts(hir).first().value as HirCall
        assertEquals(listOf("Nat", "Str"),
            (call.type as HirNamedType).args.map { (it as HirNamedType).name })
    }

    @Test
    fun `B5 位置式缺省有默认值的尾字段合法`() {
        val (_, bag0) = script("struct User(name: Str = \"u\", age: Nat = 0)\nvar u = User(\"alice\")")
        assertFalse(bag0.hasError, "缺省的尾字段有默认值（正例，B5）")
        val (_, bag1) = script("struct User(name: Str = \"u\", age: Nat = 0)\nvar u = User()")
        assertFalse(bag1.hasError, "全部字段有默认值时可零实参（正例，B5）")
    }

    @Test
    fun `B5 位置式缺省无默认值字段报缺尾字段`() {
        val (_, bag) = script("struct User(name: Str, age: Nat = 0)\nvar u = User()")
        assertEquals(listOf("E-FIELD-MISSING"), errCodes(bag))
        assertTrue(bag.diags.any { it.message.contains("缺尾部字段且无默认值") },
            "实际: ${bag.diags.joinToString { it.message }}")
    }

    @Test
    fun `B5 命名构造跳过有默认值字段、缺无默认值字段照报`() {
        val (_, bag0) = script("struct User(name: Str = \"u\", age: Nat = 0)\nvar u = User(name = \"alice\")")
        assertFalse(bag0.hasError, "命名构造缺省有默认值的字段合法（正例，B5）")
        val (_, bag1) = script("struct User(name: Str, age: Nat = 0)\nvar u = User(age = 1)")
        assertEquals(listOf("E-FIELD-MISSING"), errCodes(bag1))
        assertTrue(bag1.diags.any { it.message.contains("缺字段 `name` 且无默认值") })
    }

    @Test
    fun `B4 嵌套枚举实参位内的结构体构造同通道`() {
        // 样本族：Cons(Bill("rice", 10), ...)——枚举 ctor 实参位内的结构体位置式构造
        // t41（ADR-28）：枚举 List 族 → 归纳联合（Nil / Cons）；Cons 实参位嵌套结构体构造
        val src = "struct Nil()\nstruct Cons[T](head: T, tail: Lst[T])\ntype Lst[T] = Nil | Cons[T]\n" +
            "struct Bill(name: Str, amount: Nat)\n" +
            "var xs = Cons(Bill(\"rice\", 10), Nil)"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "嵌套位结构体构造解析（B4）")
        val call = varStmts(hir).first().value as HirCall
        // t41：struct 构造返回 struct 名（旧枚举返 List 枚举名）
        assertEquals("Cons", (call.type as HirNamedType).name)
        assertEquals("Bill", ((call.type as HirNamedType).args[0] as HirNamedType).name)
    }

    @Test
    fun `Q20 程序位 ⊤ 与 ⊥ 落 E-PROP-POS`() {
        // t79（Q20，用户裁决 2026-11-09）：⊤/⊥ 是命题专用关键词——值位拒绝并提示 True / False。
        // BEFORE（旧 C1 / S-34 / Checker:1006 双身份口径）："var t: Bool = ⊤ / ⊥" 合法产
        // HirTop / HirBot（本测试原名「C1 程序位 ⊤ 与 ⊥ 是 Bool 字面量」）；AFTER：落
        // E-PROP-POS——红→绿属规格强制，非放松；记录见 ADR-50 §二。
        val (_, bagT) = script("var t: Bool = ⊤")
        assertTrue(bagT.diags.any { it.code == "E-PROP-POS" && it.message.contains("True") },
            "⊤ 值位拒绝并提示 True: " + bagT.diags.joinToString { it.code + " :: " + it.message })
        val (_, bagF) = script("var f: Bool = ⊥")
        assertTrue(bagF.diags.any { it.code == "E-PROP-POS" && it.message.contains("False") },
            "⊥ 值位拒绝并提示 False: " + bagF.diags.joinToString { it.code + " :: " + it.message })
    }

    @Test
    fun `C1 命题位 ⊤ 仍归命题面`() {
        val (_, bag) = script("fun f(): Null <⊤> {}")
        assertFalse(bag.hasError, "命题位的 ⊤ 走 HirPTop（C1 不退化为程序面字面量）")
    }
}
