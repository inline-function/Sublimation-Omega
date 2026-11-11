package sublimation.frontend.diag

/**
 * 诊断文本中的标识符引用（D2 规范，t69 用户裁决 2026-11-08）：**凡诊断中出现的标识符一律反引号包裹**。
 *
 * 解决三件事：①同名异形可辨（` ` 与 `  ` 裸文本肉眼相同）；
 * ②输出可原样复制回源码；③与普通叙述文字区分。
 *
 * 转义规则：名字内的反引号转义为反斜杠+反引号（恢复为源码字面），换行/回车/制表
 * 转义为字面 \n / \r / \t——**诊断不得跨行**，控制字符一律显示形态。
 *
 * 例：ident("foo") 得 `foo`；ident("a`b") 得 `a\\`b`；ident("a\nb") 得 `a\\nb`。
 */
fun ident(name: String): String {
    val esc = name
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("`", "\\`")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "`" + esc + "`"
}
