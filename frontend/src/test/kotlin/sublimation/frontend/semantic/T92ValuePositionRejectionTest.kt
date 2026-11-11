package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t92（裁决 4 表达式层闭合 / 待裁-B）：值位（表达式位）拒绝原生命题连接词 `∧ ∨ → ↔`；
 * `<->` 内建含义退役（E-OP-RETIRED，提示命题层 `↔`）而保留用户自定义（R2）。
 *
 * ## 背景
 * - t74 名字材料化后 `a ∧ b` 经 D-5 糖落 CallExpr，expr() 有「isBuiltinOp ∧ propCtors-2」的
 *   条件交集回落 BinExpr + Bool 签名（TypeInfer builtinOps）——qa/t77 测绘的盲区。
 * - 裁决 4「表达式层**一律** `&` `|` `!`」+「命题**专用** `∧ ∨ → ↔ ¬`」⇒ 值位应当拒绝。
 * - 复用 **E-PROP-FORM**（与 t85 命题位 `&` 反向同码、同半成品模式），**零新增诊断码**；
 *   提示可编译（ADR-48 §八）：∧→`&`、∨→`|`；→/↔ 值位无等价写法——只定位，不杜撰算符。
 * - `¬` 的值位回落（t74 既有机制）不在本任务。
 */
class T92ValuePositionRejectionTest {

    private val and = "∧"
    private val or  = "∨"
    private val imp = "→"
    private val iff = "↔"

    private fun errCodes(src: String, file: String = "t92.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    private fun allDiags(src: String, file: String = "t92.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.map { it.code + " :: " + it.message } +
            diag.diags.map { it.code + " :: " + it.message }
    }

    /** 验收 #1：值位中缀 ∧——E-PROP-FORM + 提示 `&`。 */
    @Test fun 值位中缀与拒绝() {
        val src = "fun f(a: Bool, b: Bool): Bool = a $and b"
        assertTrue(errCodes(src).contains("E-PROP-FORM"), "值位 ∧ 落 E-PROP-FORM：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("布尔与写作 `&`") }, "提示值位 `&`：" + allDiags(src))
    }

    /** 验收 #2：值位中缀 ∨——E-PROP-FORM + 提示 `|`。 */
    @Test fun 值位中缀或拒绝() {
        val src = "fun f(a: Bool, b: Bool): Bool = a $or b"
        assertTrue(errCodes(src).contains("E-PROP-FORM"), "值位 ∨ 落 E-PROP-FORM：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("布尔或写作 `|`") }, "提示值位 `|`：" + allDiags(src))
    }

    /** 验收 #3：值位中缀 → / ↔——E-PROP-FORM +「命题专用」（无值位等价写法，不杜撰算符）。 */
    @Test fun 值位中缀蕴含与等价拒绝() {
        for (nm in listOf(imp, iff)) {
            val src = "fun f(a: Bool, b: Bool): Bool = a $nm b"
            assertTrue(errCodes(src).contains("E-PROP-FORM"), "值位 $nm 落 E-PROP-FORM：" + errCodes(src))
            assertTrue(allDiags(src).any { it.contains("命题专用") && it.contains("没有等价写法") },
                "$nm 提示「命题专用、无等价写法」：" + allDiags(src))
        }
    }

    /** 验收 #4：表达式层唯一写法 `&` / `|` / `!` 通过（拒绝提示的目标写法本身可编译）。 */
    @Test fun 表达式层写法通过() {
        for (src in listOf(
            "fun f(a: Bool, b: Bool): Bool = a & b",
            "fun g(a: Bool, b: Bool): Bool = a | b",
            "fun h(a: Bool): Bool = !a",
        )) assertTrue(errCodes(src).none { it.startsWith("E") }, "零错误：" + errCodes(src))
    }

    /** 验收 #5：命题位不受影响——∨ / ⊤ / ∨⊤ 全通过。 */
    @Test fun 命题位不受影响() {
        for (src in listOf(
            "fun f[p, q]: Null <p $or q>",
            "axiom ⊤",
            "fun g[p]: Null <p $or ⊤>",
        )) assertTrue(errCodes(src).none { it.startsWith("E") }, "命题位零错误：" + errCodes(src))
    }

    /** 验收 #6：用户自定义 `fun <->` 合法（R2——中缀调用通道）。 */
    @Test fun 用户自定义iff合法() {
        val src = "fun <->(a: Bool, b: Bool): Bool = a\n@root fun main(): Bool = True <-> False"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "R2 零错误：" + errCodes(src))
    }

    /** 验收 #7：未定义的 `a <-> b`（内建含义）——E-OP-RETIRED + 提示 `↔`。 */
    @Test fun 未定义iff退役() {
        val src = "fun f(a: Bool, b: Bool): Bool = a <-> b"
        val codes = errCodes(src)
        assertTrue(codes.contains("E-OP-RETIRED"), "未定义 <-> 落 E-OP-RETIRED：" + codes)
        assertFalse(codes.contains("E-OP-UNCLASSIFIED"), "不再走旧 E-OP-UNCLASSIFIED 通道：" + codes)
        assertTrue(allDiags(src).any { it.contains("`↔`") }, "提示命题层 `↔`：" + allDiags(src))
    }

    /** 值位的符号前缀形态 `∧<a, b>`（角表）同码拒绝——qa F2 盲区同面收口。 */
    @Test fun 值位角表形态拒绝() {
        val src = "var x = $and<True, False>"
        assertTrue(errCodes(src).contains("E-UNBOUND-NAME") || errCodes(src).contains("E-PROP-FORM"),
            "角表形态落错（E-UNBOUND-NAME 或 E-PROP-FORM）：" + errCodes(src))
    }

    /** 项位的 → 不受影响（t82/P5Batch2 #6 哨兵的回归口径：项位连接词产 HirTBin）。 */
    @Test fun 项位连接词不受影响() {
        val src = "fun t[x, l, r]: Null <(x ∈ (l..r) $imp x) >= l>"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "项位 → 零错误：" + errCodes(src))
    }
}
