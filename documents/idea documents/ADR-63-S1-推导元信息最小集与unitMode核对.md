# ADR-63:S1 HIR 推导元信息最小集 + unit.mode 核对（t108,R3 S1）

日期：2026-11-11 · 任务：t108 · 裁决依赖：C-8（「v3 按需加字段」）,C-10（四字段精确语义，未裁）

## 语境

R3 脚本模式把类型推导落进 HIR。P-11 §3.1 要求「元变量必须可追溯：HIR 中落 inferred 标记与
evidence（参与求解的使用处位置列表）」,§7 示其四字段形态（inferred/defaulted/evidence/provenance）并
原提 schema 升 v4。C-8 裁「**v3 按需加字段**（保基线）」——不升版、不做全量重基线。C-10 尚未对四字段
的精确语义与可缺失性二次裁决；本期只做**最小集**：inferred 标记 + 可选 evidence 位。

## 决策

1. **载体=推导槽位（P-11 §3.1 空位四种类）,四节点**：
   - [hir.HirParam]（形参槽，同节点兼结构体字段槽，S-10）
   - [hir.HirFunDecl] 的 retType 槽
   - [hir.HirVarStmt]（局部/顶层语句位 var 槽）
   - [hir.HirVarExpr]（表达位 var 槽,Elaborator 的 VarExpr 通道）
   每载体加尾参默认字段：`inferred: Boolean = false`、`evidence: List<String> = emptyList()`
   （位于 notes 之后——同 t109 HirPAtom.provenance 的尾参先例）。evidence 承 P-11 §3.1 口径的
   使用处位置列表（字符串），其具体编码归 C-10。
2. **零改 Elaborator/ProofChecker**:载体的既有构造位全部经尾参默认兼容（Kotlin 省略尾参）,
   无产位、无消费位——本期是纯载体 + 发射层。
3. **HirJson 按需发射,schema 保持 v3**：四 emit 位（HirFunDecl/HirParam/HirVarStmt/HirVarExpr)
   仅在 `inferred || evidence 非空` 时写字段：
   ```kotlin
   if (n.inferred || n.evidence.isNotEmpty()) {
       fieldBool("inferred", n.inferred)
       if (n.evidence.isNotEmpty()) fieldStrings("evidence", n.evidence)
   }
   ```
   默认态产物**零新字段**——「未带推导位的样本不得出现新字段」是本任务的验收尺（结构性达成,
   不依赖样本内容）。
4. **unit.mode 落位核对（范围 2）**:envelope.unit.mode 直读 emit 的 scriptMode 参数（D-12 单源链
   CLI Main.kt:46 → compileFrontend → compile → Elaborator ctor val);推导态闸（t111 改读真值）
   与 envelope 同源。t108 侧用端到端断言核对：脚本真值放行顶层 var 且 envelope=script;默认工程
   真值同一源被门控拒且 envelope=project（真值不嗅探扩展名）。既有 T111ModeTruthTest 4 例已锁单源。
5. **不做项**:defaulted/provenance（C-10 二裁）;任何 producer（元变量生成=S2）;消费位（S6 差分
   对拍、t96 纯度注入）;HirPAtom.provenance 的发射（t109 已定 JSON 不发射,维持）。

## 风险与不变量

- data class 尾参引入 equals/hashCode 变化：默认态两两相等不变（全量门兜底）;copy 与位置构造
  零破。
- JSON 字段顺序在对象尾部追加：schema 无序契约,消费者不得按位置取字段。
- evidence 与 C-10 的可缺失性再裁冲突时，尾参形态可零破坏演化（追加默认值）。
- 按需尺是**结构性**保证：全量样本 diff（SAME 机器验证）+ 默认态负例测试双锁。
