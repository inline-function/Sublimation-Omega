package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.diag.Severity
import sublimation.frontend.json.HirJson
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t160（R4-A0 / R4 提案 §2.2 前置件）：D-DEEP-COPY 深拷指令 **notes 化**——v3 契约加性扩展。
 *
 * 现状缺口（提案 §2.1 实测）：八站点深拷留痕里仅 var 位有结构化 deepCopy 布尔，其余 7 站点指令
 * 只存于诊断 message 文本——后端消费 = parse 未冻结文案 = 脆耦合。本门验证新通道：
 *
 * - **节点 notes 结构化**：每个深拷站点在值通道节点（HirVarStmt / HirAssign / HirParam /
 *   HirCall / HirStructCtor / HirReturn / HirFunDecl …）上挂 kind=supplement、code=D-DEEP-COPY
 *   的 note，携带 from（源类型串）/ to（目标类型串）/ 必要时 deepElements（元素层计数槽，>0 才发射）。
 * - **诊断照发**（R-29 告知面不变，message 同源单 wording）;notes 只是给消费方的结构化通道。
 * - **默认态零产**：无深拷站点的产物零新字段（门 8）——v3 加性纪律。
 *
 * 目标类型串接受联合别名展开形态（List[Nat] → Nil | Cons[Nat]，ADR-29 字段位展开口径同源）。
 *
 * 纪律：负例先红——实现前本套件 7 站点全红（hir 区段零 D-DEEP-COPY note）;实现后逐站绿。 */
class T160DeepCopyNotesTest {

    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private val prelude = "@mut struct Box(@mut n: Nat)"
    private val from = "@mut List[Nat]"
    private val tos = setOf("List[Nat]", "Nil | Cons[Nat]")

    /** 编出 envelope JSON（同 T150 口径：parse → compile（工程模式 + stdlib 挂载）→ emit）。 */
    private fun json(src: String): String {
        val pr = parseScriptSourceWithNotes(src, "t160.subl")
        val (hir, bag) = compile(pr.file, pr.notes, scriptMode = false, mounted = stdlibMounts)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return HirJson.emit(hir, bag, "t160.subl", false)
    }

    private data class DeepNote(val node: String, val from: String, val to: String, val elem: Int?)

    /** hir 区段内每条 D-DEEP-COPY note（宿主节点 kind + from/to/元素层槽）。 */
    private fun deepNotes(json: String): List<DeepNote> {
        val hir = json.substringAfter("\"hir\":")
        val out = mutableListOf<DeepNote>()
        var i = hir.indexOf("\"code\": \"D-DEEP-COPY\"")
        while (i >= 0) {
            val node = NODE_KIND.find(hir.substring(0, i))?.groupValues?.get(1) ?: "?"
            val after = hir.substring(i, (i + 700).coerceAtMost(hir.length))
            val m = NOTE_FIELD.find(after)
            out.add(DeepNote(node, m?.groupValues?.get(1) ?: "?", m?.groupValues?.get(2) ?: "?",
                ELEM_FIELD.find(after)?.groupValues?.get(1)?.toIntOrNull()))
            i = hir.indexOf("\"code\": \"D-DEEP-COPY\"", i + 1)
        }
        return out
    }

    private val srcParam: String = prelude + "\n@impure fun g(z : List[Nat]) { }\n@impure fun main() {\n" +
        " var a : @mut List[Nat] = mutableListOf(1)\n g(a)\n}"
    private val srcAssign: String = prelude + "\n@impure fun main() {\n" +
        " var u : List[Nat] = Nil\n var a : @mut List[Nat] = mutableListOf(1)\n u = a\n}"
    private val srcVar: String = prelude + "\n@impure fun main() {\n" +
        " var a : @mut List[Nat] = mutableListOf(1)\n var b : List[Nat] = a\n}"
    private val srcReturn: String = prelude + "\n@impure fun h() : List[Nat] {\n" +
        " var a : @mut List[Nat] = mutableListOf(1)\n return a\n}\n" +
        "@impure fun main() { h() }"
    private val srcFunBody: String = prelude + "\n@impure fun k(a : @mut List[Nat]) : List[Nat] = a\n" +
        "@impure fun main() { k(mutableListOf(1)) }"
    private val srcStruct: String = "struct S(f : List[Nat])\n" + prelude + "\n@impure fun main() {\n" +
        " var a : @mut List[Nat] = mutableListOf(1)\n var s = S(f = a)\n}"
    private val srcDefault: String = prelude + "\n@impure fun d(x : @mut List[Nat], z : List[Nat] = x) { }\n" +
        "@impure fun main() { d(mutableListOf(1)) }"

    // ---------- 门 1~7：七站点 notes 化（负例先红：实现前 hir 区段零 D-DEEP-COPY note） ----------

    @Test fun 传参位note挂HirCall() {
        val n = deepNotes(json(srcParam)).firstOrNull { it.node == "HirCall" }
        assertTrue(n != null && n.from == from && n.to in tos,
            "call 实参位（MUT→PLAIN）须在 HirCall 挂 from/to 结构化 note（实得 ${deepNotes(json(srcParam))}）")
    }

    @Test fun 赋值位note挂HirAssign() {
        val ns = deepNotes(json(srcAssign))
        val n = ns.firstOrNull { it.node == "HirAssign" }
        assertTrue(n != null && n.from == from && n.to in tos, "赋值位须在 HirAssign 挂结构化 note（实得 $ns）")
    }

    @Test fun var初始化位note挂HirVarStmt且deepCopy真() {
        val j = json(srcVar)
        val ns = deepNotes(j)
        val n = ns.firstOrNull { it.node == "HirVarStmt" && it.to in tos }
        assertTrue(n != null && n.from == from, "var 初始化位须在 HirVarStmt 挂结构化 note（实得 $ns）")
        assertTrue(j.contains("\"deepCopy\": true"), "var 位既有结构化 deepCopy 布尔须同在")
    }

    @Test fun return位note挂HirReturn() {
        val ns = deepNotes(json(srcReturn))
        val n = ns.firstOrNull { it.node == "HirReturn" }
        assertTrue(n != null && n.from == from && n.to in tos, "return 值位须在 HirReturn 挂结构化 note（实得 $ns）")
    }

    @Test fun 表达式体note挂HirFunDecl() {
        val ns = deepNotes(json(srcFunBody))
        val n = ns.firstOrNull { it.node == "HirFunDecl" }
        assertTrue(n != null && n.from == from && n.to in tos, "函数表达式体位须在 HirFunDecl 挂结构化 note（实得 $ns）")
    }

    @Test fun struct构造字段位note挂HirStructCtor() {
        val ns = deepNotes(json(srcStruct))
        val n = ns.firstOrNull { it.node == "HirStructCtor" }
        assertTrue(n != null && n.from == from && n.to in tos, "struct 构造字段位须在 HirStructCtor 挂结构化 note（实得 $ns）")
    }

    @Test fun 构造子字段位note挂HirCall() {
        val src: String = "struct S(f : List[Nat])\n" + prelude + "\n@impure fun main() {\n" +
            " var a : @mut List[Nat] = mutableListOf(1)\n var s = S(a)\n}"
        val ns = deepNotes(json(src))
        val n = ns.firstOrNull { it.node == "HirCall" }
        assertTrue(n != null && n.from == from && n.to in tos, "构造子字段位（位置式 S(a)）须在 HirCall 挂结构化 note（实得 $ns）")
    }

        @Test fun 默认值位note挂HirParam() {
        val ns = deepNotes(json(srcDefault))
        val n = ns.firstOrNull { it.node == "HirParam" }
        assertTrue(n != null && n.from == from && n.to in tos, "默认值位须在 HirParam 挂结构化 note（实得 $ns）")
    }

    @Test fun 元素层计数槽深拷下钻() {
        val src: String = prelude + "\n@impure fun main() {\n" +
            " var inner : @mut List[Nat] = mutableListOf(1)\n" +
            " var a : @mut List[@mut List[Nat]] = mutableListOf(inner)\n" +
            " var u : List[List[Nat]] = a\n}"
        val ns = deepNotes(json(src))
        val n = ns.firstOrNull { it.node == "HirVarStmt" && it.elem != null && it.elem > 0 }
        assertTrue(n != null && n.from == "@mut List[@mut List[Nat]]",
            "嵌套容器元素层深拷须在 HirVarStmt 挂含 deepElements>0 的 note（实得 $ns）")
    }

        // ---------- 门 8：默认态零产（v3 加性纪律）+ 诊断照发 ----------

    @Test fun 无深拷站点零note() {
        val j = json(prelude + "\n@impure fun main() {\n" +
            " var t : Box = Box(0)\n var u : Box = t\n var s = Box(1)\n print(s.n)\n}")
        assertEquals(emptyList(), deepNotes(j), "T→T 共享/标量程序须零 D-DEEP-COPY note（默认态零产）")
        assertFalse(j.contains("\"deepElements\""), "零元素层计数时不得发射 deepElements 字段")
    }

    @Test fun 诊断照发且消息同源() {
        val j = json(srcVar)
        val diag = j.substringAfter("\"diagnostics\": [").substringBefore("\"hir\":")
        assertTrue(diag.contains("\"code\": \"D-DEEP-COPY\""), "D-DEEP-COPY 诊断照发（告知面不变）")
        assertTrue(diag.contains("隐式深拷"), "诊断消息含隐式深拷（与 note 同源 wording）")
    }

    private val NODE_KIND = Regex("""^.*"kind": "(Hir[A-Za-z]+)"""", RegexOption.DOT_MATCHES_ALL)
    private val NOTE_FIELD = Regex(""".*?"from": "([^"]*)",\s*"to": "([^"]*)"""", RegexOption.DOT_MATCHES_ALL)
    private val ELEM_FIELD = Regex(""""deepElements": (\d+)""")
}
