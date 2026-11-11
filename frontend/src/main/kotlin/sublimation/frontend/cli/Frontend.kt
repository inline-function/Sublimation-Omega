package sublimation.frontend.cli

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.json.HirJson
import sublimation.frontend.parser.ParseResult
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.semantic.compile

/**
 * 一次前端编译产物（S-2 §2 对外契约）：HIR + 诊断袋 + JSON 产物文本。
 */
data class FrontendOutput(
    val hir: HirFile,
    val diag: DiagBag,
    val json: String,
)

/**
 * 单源文件一次编译：源代码 → 词法 → AST → HIR → JSON（S-2 §2；S-1 管线四层一气呵成）。
 *
 * 契约：
 * - 诊断一袋汇总（词法 + 语法 + 语义，层间总契约 2）；解析期 ParseNote 由语义层搬入
 *   HIR 节点 notes（D-9），不入诊断袋；
 * - 半成品原则（ADR-1 §3.2）：即使存在 ERROR 级诊断，也返回完整 HIR 树与 JSON
 *   （envelope 的 ok 旗标=false 携带判定结论，N-SEV-BLOCK）——产物是诊断可回链的
 *   信息载体，不只是成功的纪念品；
 * - 输入→输出可复现（层间总契约 3）：相同源码与模式必产相同 JSON。
 */
fun compileFrontend(
    source: String, unitPath: String, scriptMode: Boolean = false,
    /** t118（OQ-5）：--no-degrade——脚本模式「退化即失败」CI 门（Main.kt 旗标透传）。 */
    noDegrade: Boolean = false,
    /** t123（R-16 S4 / OQ-5）：--no-lift——脚本模式「上文上提即失败」CI 门（同 --no-degrade 双口形态）。 */
    noLift: Boolean = false,
): FrontendOutput {
    val pr: ParseResult = if (scriptMode) {
        parseScriptSourceWithNotes(source, unitPath)
    } else {
        parseSourceWithNotes(source, unitPath)
    }
    val diag = DiagBag()
    diag.absorbAll(pr.diag)
    val (hir, semDiag) = compile(pr.file, pr.notes, scriptMode, noDegrade, noLift)
    diag.absorb(semDiag)
    val json = HirJson.emit(hir, diag, unitPath, scriptMode)
    return FrontendOutput(hir, diag, json)
}

/** 把诊断列表按原严重度并入诊断袋（保序；ParseResult.diag → DiagBag 的桥）。 */
internal fun DiagBag.absorbAll(ds: List<Diag>) {
    for (d in ds) when (d.severity) {
        Severity.ERROR -> error(d.code, d.pos, d.message, d.nid)
        Severity.WARN -> warn(d.code, d.pos, d.message, d.nid)
        Severity.HINT -> hint(d.code, d.pos, d.message, d.nid)
        Severity.SUPPLEMENT -> supplement(d.code, d.pos, d.message, d.nid)
    }
}
