package sublimation.frontend.semantic

import sublimation.frontend.hir.HirFile
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap

/**
 * t129 清账件 4：nid 卫兵（测试期钩子——main 零侵入）。机制方案见 documents/nid-卫兵机制-t129.md。
 *
 * - **N1 分配唯一**：恒等遍历全树，不同节点实例不得持同一 nid（快照/符号共享实例的重访不算违例——
 *   用 IdentityHashMap 去重访问）。违例 ⇔ 第二节点计数器或身份复制 bug（ADR-14 F1 契约）。
 * - **N3 双编译确定性**由测件侧两次全流水线 emit 逐字节比对覆盖（见 T129NidGuardTest）。 */
object NidGuard {

    /**
     * 恒等遍历 [hir] 全树，断言 N1：不同节点实例持不同 nid。
     * 返回访问到的（nid 数, 实例数）供测件诊断。
     */
    fun assertUniqueNids(hir: HirFile, label: String): Pair<Int, Int> {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val byNid = HashMap<Int, Any>()
        var nidCount = 0
        fun walk(n: Any?) {
            when (n) {
                null, is String, is Number, is Boolean, is Enum<*> -> return
                is Collection<*> -> { n.forEach { walk(it) }; return }
                is Map<*, *> -> { n.keys.forEach { walk(it) }; n.values.forEach { walk(it) }; return }
            }
            if (!n.javaClass.name.startsWith("sublimation.frontend.")) return
            if (!visited.add(n)) return    // 恒等重访（共享实例/快照/符号回指）非违例
            var klass: Class<*>? = n.javaClass
            while (klass != null && klass!!.name.startsWith("sublimation.frontend.")) {
                for (f in klass!!.declaredFields) {
                    f.isAccessible = true
                    val v = runCatching { f.get(n) }.getOrNull() ?: continue
                    if (f.name == "nid" && (f.type == Integer.TYPE || v is Int)) {
                        nidCount++
                        val prev = byNid.put(v as Int, n)
                        check(prev == null || prev === n) {
                            "[$label] N1 破：nid $v 双开支于 ${prev!!.javaClass.simpleName} 与 ${n.javaClass.simpleName}"
                        }
                    } else walk(v)
                }
                klass = klass!!.superclass
            }
        }
        walk(hir)
        return nidCount to visited.size
    }
}
