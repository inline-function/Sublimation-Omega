package sublimation.frontend.parser

import sublimation.frontend.ast.DeclEntry
import sublimation.frontend.ast.FunDecl
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.StructDecl
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * t113（R3 S2 先决件，t112 §1.1）:形参类型标注可省——解析层接受、零 E-SYNTAX 诊断、省略位=空类型槽。
 *
 * 先红态（旧 parser 实测,classes 未改前 CLI 探针）:`fun f(x) = 1` 与 `struct P(x)` 均落
 * E-SYNTAX「期望形参类型标注（: 类型）」逐字拒；本组验证绿态。
 *
 * 边界：解析器不知模式（D-12 真值单源）——工程模式的 E-OMITTED-TYPE 硬拒是语义层 R-13 的活
 * （t115+），本组只测解析接受与槽位形态,不测任何模式的语义处置。
 */
class T113OmittedParamTest {

    /** 脚本模式：省略形参解析通过、零诊断、省略位为空槽位。 */
    @Test
    fun t113_1_脚本模式省略形参解析通过且零诊断() {
        val r = parseScriptSourceWithNotes("fun double(x) = x", "t.subs")
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, "解析层不得落语法错：${r.diag.map { it.message }}")
        val d = assertIs<FunDecl>((assertIs<DeclEntry>(r.file.entries[0])).decl)
        assertEquals("double", d.name)
        assertEquals(1, d.params.size)
        assertNull(d.params[0].type, "省略位的类型槽位为空（S2 元变量生成点,P-11 §3.1）")
        assertNull(d.retType, "返回位槽位本就可空（零改）")
    }

    /** 混标：省略位与标注位并存槽位形态正确。 */
    @Test
    fun t113_2_省略与标注并存() {
        val r = parseScriptSourceWithNotes("fun g(a, b: Nat) = b", "t.subs")
        assertTrue(r.diag.none { it.severity == Severity.ERROR })
        val d = assertIs<FunDecl>((assertIs<DeclEntry>(r.file.entries[0])).decl)
        assertNull(d.params[0].type)
        assertIs<NamedType>(d.params[1].type).also { assertEquals("Nat", it.name) }
    }

    /** 结构体字段省略同通道（Param 复用,t112 §1.1 随改）。 */
    @Test
    fun t113_3_结构体字段省略同通道() {
        val r = parseScriptSourceWithNotes("struct P(x, y: Nat)", "t.subs")
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, "结构体字段省略不得落语法错")
        val s = assertIs<StructDecl>((assertIs<DeclEntry>(r.file.entries[0])).decl)
        assertNull(s.fields[0].type)
        assertIs<NamedType>(s.fields[1].type).also { assertEquals("Nat", it.name) }
    }

    /** 工程模式解析同样接受（硬拒在语义层 R-13,解析层不截断）。 */
    @Test
    fun t113_4_工程模式解析同样接受() {
        val r = parseSourceWithNotes("fun f(x, y: Nat) = y", "t.subl")
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, "解析层零诊断（工程模式拒绝属语义层 R-13）")
        val d = assertIs<FunDecl>((assertIs<DeclEntry>(r.file.entries[0])).decl)
        assertNull(d.params[0].type)
    }

    /** 默认值与解构通道不受省略影响（既有 parseParams 分支保序）。 */
    @Test
    fun t113_5_默认值通道不受省略影响() {
        val r = parseScriptSourceWithNotes("struct P(x = 0, y: Nat)", "t.subs")
        assertTrue(r.diag.none { it.severity == Severity.ERROR })
        val s = assertIs<StructDecl>((assertIs<DeclEntry>(r.file.entries[0])).decl)
        assertNull(s.fields[0].type)
        assertEquals("0", (s.fields[0].default as sublimation.frontend.ast.IntLit).value)
    }
}
