package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * t156（B3 / 标准库规划 §3 首批公理清单）：stdlib/axioms 公理库落地。
 *
 * - **内容**：A1–A3 数值 + L1–L4 链表长度/拼接（规划 Q4 建议首批「够用且可测」范围）;
 *   归纳法依赖面（L5–L7 / E1–E3 / induction 公理）按 t91 裁决 6 暂缓——不落。
 * - **形态**：@axiom 无体 fun + 命题元变量（规划 §4 类型判定位 `n : Nat` 按 ADR-13 §三 推迟——
 *   以元变量形态落，类型约束由调用位类型事实补）;命题项自由名（length/Nil/Cons/append）走
 *   命题位静默模式（NameResolver 既有口径），故 axioms 件**无依赖挂载**。
 * - **快照面**：@axiom fun 落 funs 表（非 theorems 分表——∴ 搜索面为 @theorem 通道），
 *   通过限定引用可见（R-22）;裸名链不入（裸名隔离——零用户 SAME 影响的机制保证）。
 * - **用户影响**：默认挂载新增 axioms 快照——无引用即零产物变化（68 门 SAME 的承诺）。 */
class T156StdlibAxiomsTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = true): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t156.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    /** 门 1：stdlib 全件绿（axioms 件并入编译根后零红）。 */
    @Test fun axioms件并入零红() {
        val (_, sd) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        assertFalse(sd.hasError, "stdlib 全件（含 axioms）零红: " + sd.diags.map { it.code + it.message }.joinToString(";"))
    }

    /** 门 2：十六条公理在位（首批 A1–A3 + L1–L4 八条 + t157 扩容 M/S/T/G 八条）。
     *  t157 扩容八条随库更新并入本门（同 t140 件数更新先例）。 */
    @Test fun 首批公理条目在位() {
        val (su, _) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        val by = su.associateBy { it.alias }
        assertEquals(true, "axioms" in by.keys, "axioms 件在默认挂载表")
        val ax = by["axioms"]!!.snapshot.funs.keys
        // A1–A3（数值三条）+ L1/L2/L3a/L3b/L4（链表五条——L3 结合律拆右单位元 + 结合律两条）
        // t157（B3 后续件）：M1–M2/S1–S2/T1–T3/G1 八条扩容并入——十六为现态
        // （同 t140 件数更新先例：既有断言随库扩容更新，不掩裂）
        assertEquals(16, ax.size, "公理恰十六条（首批八 + 扩容八）: $ax")
        assertTrue(setOf("nat non-negative", "nat add monotone", "nat sub non-negative").all { it in ax } &&
            setOf("list length nil", "list length cons", "list append nil",
                "list append assoc", "list length append").all { it in ax },
            "A1–A3 + L1–L4 命名在位: $ax")
        assertTrue(setOf("map get put", "map put other key", "set insert contains", "set insert other",
            "text length concat", "text concat assoc", "text concat empty", "range membership").all { it in ax },
            "t157 扩容 M1–M2/S1–S2/T1–T3/G1 命名在位: $ax")
    }

    /** 门 3：限定引用调用绿（公理函数可作普通函数调用——编译期擦除为 Null）。 */
    @Test fun 公理限定引用调用绿() {
        val o = scriptStd("fun m(): Null { axioms.`list length nil`() return null }")
        assertEquals(emptyList<String>(), errs(o), "axioms.`list length nil`() 限定引用调用绿: " + errs(o))
    }

    /** 门 4：裸名链隔离——公理名不入裸名查询（零用户 SAME 影响的机制保证）。 */
    @Test fun 公理不入裸名链() {
        // 反引号公理名**裸用**（无 axioms. 限定前缀）——裸名链不达挂载快照（R-22: 快照面仅限定可见）
        val o = scriptStd("fun m(): Null { `list length nil`() return null }")
        assertTrue("E-UNBOUND-NAME" in errs(o), "公理名裸用须 E-UNBOUND-NAME（裸名链不达 axioms 快照）: " + errs(o))
    }

    /** 门 5：默认挂载用户编译零波——未引用 axioms 的用户代码产物不含公理库足迹。 */
    @Test fun 未引用零足迹() {
        val o = scriptStd("fun m(): Nat = sum(range(1, 10))")
        assertEquals(emptyList<String>(), errs(o), "既有 retire 替身裸名链绿（零回归）: " + errs(o))
        assertNull(o.hir.entries.firstOrNull { it.toString().contains("axioms") },
            "未引用时 HIR 无 axioms 足迹")
    }
}
