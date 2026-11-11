package sublimation.frontend.semantic

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirPAnd
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirPIff
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPOr
import sublimation.frontend.hir.HirPTop
import sublimation.frontend.hir.HirProp

/**
 * 原生命题注册表（表驱动，单一来源）——《符号标识符与中缀命题》§5 的实现契约表。
 *
 * - **保护名单**：表内名字一律不可由用户重定义（t70 批次 3 落 E-NATIVE-REDECL，位置指向声明处名字）；
 * - **元数**：归一层按元数核对（¬ 是 1 元前缀；元数不符落 E-ARITY）；
 * - **命题形态分派**（P5 批次 1，t73）：命题位 CallExpr/InstExpr 遇到注册表名字按 [propCtors] 分派
 *   到原生节点——否则名字化后 `p ∧ q` 经 D-5 中缀糖落 CallExpr 会静默降级为 HirPAtom
 *   （不报错、产物错、还混进事实集 Γ，qa t72 测绘）；
 * - **扩展性**：t70 只让 ¬ 成为名字材料，∧ ∨ → ↔ 等留 P5——它们已在表内，P5 名字材料化后
 *   自动受保护、自动归一（无需逐个硬编码；本表即 §5 注册表的真子集扩展点）。
 * - ⊤ ⊥ 保持固定 token（Q12 双身份：程序位 Bool 字面量 + 命题位恒真恒假），表中仅为保护与元数登记。
 * - 表内名字不进 trustedBase（逻辑常项而非用户公理，与 ADR-42/t65 口径一致）。
 */
internal object NativeProps {
    val arity: Map<String, Int> = mapOf(
        "¬" to 1,
        "∧" to 2, "∨" to 2, "→" to 2, "↔" to 2,
        "=" to 2, "≠" to 2, "∈" to 2, ":" to 2, "<:" to 2,
        "⊤" to 0, "⊥" to 0,
    )

    /**
     * P5 批次 1（t73）：原生命题的**命题形态分派表**——名字 →（元数，原生节点构造）。
     *
     * - 表驱动：新增原生命题条目只需在此表加一行，PropNormalizer 的分派代码不动；
     * - `¬` 不进本表：其命题应用走 P4 终态既有通道（CallExpr 命题位落 E-PROP-FORM /
     *   InstExpr 走 nativeNotProp，ADR-48 §一），而非一视同仁分派——这是 t70 裁决的**有意例外**；
     * - 关系名（= ≠ ∈ : <:）无独立命题节点，其命题形态就是 head=名字的原子（prop() 默认分支）；
     * - ⊤ ⊥ 命题位形态即 HirPTop/HirPBot（0 元）。
     */
    val propCtors: Map<String, Pair<Int, (List<HirProp>, NodeId, SourceRange) -> HirProp>> = mapOf(
        "∧" to (2 to { ps, nid, pos -> HirPAnd(ps[0], ps[1], nid, pos) }),
        "∨" to (2 to { ps, nid, pos -> HirPOr(ps[0], ps[1], nid, pos) }),
        "→" to (2 to { ps, nid, pos -> HirPImp(ps[0], ps[1], nid, pos) }),
        "↔" to (2 to { ps, nid, pos -> HirPIff(ps[0], ps[1], nid, pos) }),
        "⊤" to (0 to { _, nid, pos -> HirPTop(nid, pos) }),
        "⊥" to (0 to { _, nid, pos -> HirPBot(nid, pos) }),
    )

    fun arityOf(name: String): Int? = arity[name]

    fun isProtected(name: String): Boolean = name in arity

    /** 原生命题的**读音**（仅用于诊断文案：把符号说成人话）。 */
    val spoken: Map<String, String> = mapOf(
        "¬" to "否定", "∧" to "合取", "∨" to "析取", "→" to "蕴含", "↔" to "等价",
    )

    /**
     * P5（批次 2）**粘连提示**：名字里含原生命题符号且两侧都有内容（如 `p∧q`）时，
     * 说明这是**一个标识符**（用户裁决 R1：粘连即整体），并给出加空格的正确写法。
     * 返回 null 表示与粘连无关（交给调用方的其它提示）。
     *
     * 提示里的写法必须本身可编译（ADR-48 §八）：`a ∧ b` 两侧带空格 ⇒ 落 D-5 中缀糖 ⇒ 原生命题节点。
     */
    fun glueHint(name: String, symbol: String): String? {
        val idx = name.indexOf(symbol)
        if (idx <= 0 || idx >= name.length - 1) return null
        val left = name.substring(0, idx)
        val right = name.substring(idx + 1)
        val spokenWord = spoken[symbol] ?: "运算"
        return "——若想写" + spokenWord + "，请在算符两侧加空格：" +
            "`" + left + " " + symbol + " " + right + "`"
    }

    /** 按出现顺序找一个"粘在标识符中间"的原生命题符号（供 [glueHint] 定位）。 */
    fun firstGluedSymbol(name: String): String? =
        listOf("∧", "∨", "→", "↔").firstOrNull { name.indexOf(it) > 0 && name.indexOf(it) < name.length - 1 }
}