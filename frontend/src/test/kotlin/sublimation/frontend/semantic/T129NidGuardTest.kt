package sublimation.frontend.semantic

import sublimation.frontend.json.HirJson
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t129 清账件 4：nid 卫兵测件（机制方案见 documents/nid-卫兵机制-t129.md）。
 *
 * - **N1 分配唯一**：NidGuard 恒等遍历最终树林，不同实例不得同 nid（ADR-14 单一分配源契约机制化）;
 * - **N3 双编译确定性**：同输入两次全流水线 emit 逐字节同（同 SAME 门的单元级缩影）。 */
class T129NidGuardTest {

    private fun compile(src: String): Pair<String, RoundsReport> {
        val pr = parseScriptSourceWithNotes(src, "t129nid.subs")
        val rs = compileWithRounds(pr.file, pr.notes, scriptMode = true)
        return HirJson.emit(rs.hir, rs.diag, "t129nid.subs", scriptMode = true) to rs
    }

    /** N1 + N3 内联语料（零借用 → 借用 → 局部注解 → unchecked → 泛型 → 联合别名逐热点面）。 */
    @Test fun N1唯一_内联语料() {
        val corpus = listOf(
            "var xs : @mut List[Nat] = mutableListOf(1)\nunchecked xs.add(1)\nprint(xs)",
            "@impure fun bump(xs : @mut List[Nat]) { xs.add(1) }\n" +
                "@impure fun main(): Null { var xs : @mut List[Nat] = mutableListOf(1); unchecked bump(&xs) }",
            "@impure fun main(): Null { @impure fun g(): Null { print(1) }; g() }",
            "@impure fun main(): Null { unchecked print(1); var v : Nat = 2; print(v) }",
            "fun g[T](x : T) { print(x) }\nvar v : Nat = 3\ng(v)\nprint(v)",
            "fun bump(xs) { print(xs) }\nvar ys : List[Nat] = Nil\nbump(ys)\nprint(ys)",
            "fun len(xs) { print(xs) }\nvar xs : @mut List[Nat] = mutableListOf(1)\nunchecked len(&xs)\nprint(xs)",
        )
        corpus.forEachIndexed { i, src ->
            val (json1, rs1) = compile(src)
            val (nidN, instN) = NidGuard.assertUniqueNids(rs1.hir, "inline#$i")
            assertTrue(nidN > 0, "inline#$i 产出节点")
            // N3：双编译逐字节
            val (json2, _) = compile(src)
            assertEquals(json1, json2, "inline#$i 双编译 emit 逐字节同（nid 序列确定性）")
        }
    }

    /** N1 + N3 借用 fixture（近期 nid 消费热点面：borrowArgNotes/copy 保 nid）。 */
    @Test fun N1唯一_borrow对拍fixture() {
        val dir = java.io.File("frontend/src/test/resources/diff-pairs")
        listOf("borrow-shared", "borrow-ctor", "borrow-warn", "borrow-neg-plain", "borrow-neg-ref").forEach { name ->
            val src = dir.resolve("$name.subs").readText()
            val (json1, rs) = compile(src)
            NidGuard.assertUniqueNids(rs.hir, name)
            val (json2, _) = compile(src)
            assertEquals(json1, json2, "$name 双编译 emit 逐字节同")
        }
    }
}
