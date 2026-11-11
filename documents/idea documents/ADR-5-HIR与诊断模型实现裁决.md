# ADR-5 HIR 节点体系与诊断模型实现裁决（t6 落地）

> 状态：🟢 已决策（2026-11-04，semantics 落地 t6 时）
> 相关任务：t6；相关规范：S-1 §3、S-2 §3.2/§3.3、D-9、D-10、D-11、P-7；前置：ADR-1（HIR 设计）、ADR-4（位置模型归属）

## 背景

t6 按 ADR-1 §3.2/§3.3/§3.4/§3.6 实现 hir + diag 两包。落地中发现五处 ADR-1 的开放项或口径差，
需要裁决记录（理念文档不参与权威裁决，本裁决是对 ADR-1 实现口径的补全，不推翻 ADR-1 的任何决策）：

1. ADR-1 §4.3 把符号身份（SymbolId）方案留给 t6；
2. t3（ADR-3）新增了 5 个 AST 容错节点（ErrorDecl/ErrorStmt/ErrorExpr/ErrorPattern/ErrorType），
   ADR-1 §3.2 映射表成文早于该批节点，HIR 侧缺乏对应落点；
3. ADR-1 §3.2 的命题项族列表（含 HirTDollar/HirTParamRef/HirTTop/HirTBot）与其映射表
   （ReturnSym → HirPAtom("$")、ParamRefExpr → HirPAtom("$n")、TopExpr → HirPTop、BotExpr → HirPBot）
   表面冲突——两者实际描述命题项与整命题两个层次；
4. ADR-1 映射表把 ByStmt / ByExpr 合并为「HirBy」一条，而两 AST 节点分别落入语句与表达式层级，
   sealed 层级不能共用一个节点；
5. ADR-1 映射表未给程序位 InstExpr（S-33）一行——紧贴 `<...>` 在表达式位同样存在
   （显式供给等），角色判定属构造层（S-1 §3）。

## 决策

1. **符号身份方案**：NodeId = typealias Int 宿 diag（D-11）；HIR 符号引用 `SymbolRef(id, kind, name)`
   宿 hir——id 构造期内唯一（权威），kind 为 `SymbolKind`（FUN/CLASS/IMPL/STRUCT/ENUM/CTOR/FIELD/
   ALIAS/PROPDEF/THEOREM/PARAM/LOCAL/BUILTIN），name 冗余可读（JSON 呈现与 diff）。未解析名 symbol=null
   并落诊断（S-1 §2 容错）。
2. **容错节点镜像**：HIR 侧落 HirErrorDecl/HirErrorStmt/HirErrorExpr/HirErrorPattern/HirErrorType
   各携带 message + 五件套。理由：t7（AST→HIR）遇解析半成品必须有合法落点，否则含恢复错误的文件
   无法产出 HIR——违反 S-1 §2 容错同构原则；五节点与 AST 侧一一对应，t7/t10 无需特判。
3. **命题项族口径**：按映射表 + 旧 checker termEq 闭名单落地，共 13 个 HirTerm——
   HirTInt/HirTStr/HirTName/HirTBin/HirTUni/HirTCall/HirTInst/HirTField/HirTSymbolCall 为 termEq
   同构形态；HirTDollar/HirTParamRef 承载比较式中的 $ 与 $n 项位（整命题形态 $ /$n 仍是
   PAtom("$")/PAtom("$n")，与旧 fromExpr 一致）；HirTTop/HirTBot 覆盖项位 ⊤/⊥。
   旧实现的逃逸头 PAtom("__t__")/__call__/__inst__ 不复刻：HIR 命题项可以递归表达这些形态
   （HirTCall/HirTInst 的 callee/target 为任意项），命题位无法表达的表达式由构造层落诊断。
4. **By 双节点**：HirByStmt（S-27 语句位）与 HirByExpr（S-31 中缀后缀断言式 e by [q]），两者皆
   实现 HirProofObligation（proofContext/rule/verdict）。映射表「HirBy」一条即两节点。
5. **程序面 InstExpr 落点**：新增 HirInst（target + terms + symbol/type），角色由构造层判定；
   命题位 InstExpr 仍归一 HirPAtom。
6. **P-STRUCT-EQ 实现方式**：HirProp/HirTerm 各节点以 data class **显式**重写 equals/hashCode，
   忽略 nid/origin/notes；HirTName 的 symbol 亦不参与结构相等（旧 termEq 同构）。每类取独立盐值
   组合 hashCode，跨类不碰撞。事实匹配（去重、P-FRAME 并集、定理匹配）可直接用 ==/Set。
7. **诊断落袋签名**：保持旧体系 DiagBag.error/warn/hint/supplement(code, pos, message) 四方法，
   pos 升级 SourceRange（D-10），追加可选 nid 形参反引 HIR 节点（D-9）；render 旧格式兼容
   「[族标][l:c] CODE: msg」+ 源码行箭头；N-SEV-BLOCK 由 Severity 判阻断（只有 ERROR）。

## 后果

- t7（collect→elaborate）：AST 69 节点有**完整** HIR 落点（含 5 容错镜像）；映射只需复制构造，
  位置/nid 必填、notes 通道承载解析期留痕（脱糖闭名单 D-SUGAR 等）。
- t9（语义/证明检查）：事实匹配直接用命题面 == / HashSet；效应、控制流、穷尽性、深拷贝点位、
  字典/槽等结论字段已预置（默认值 = 未核对/未发生）。
- t10（JSON）：节点形态按 ADR-1 §3.7（nid/kind/pos「l:c-l:c」/notes），符号引用 kind 取值即
  SymbolKind 序列化；诊断定位用 diag.SourceRange。
- 后端：不可变 HIR + 复制构造使树可安全共享/缓存；调用节点的解析结论字段（dictionary/slot/
  dictArgs/methodSugar/awaitPoint/calleeImpure）是旧的决策 60/82/异步注入/方法糖对齐的落点。

## 验证

- gradlew :frontend:test 全绿：AstTest 12 / LexerTest 38（位置迁移回归）/ HirTest 14 / DiagTest 10 /
  DiagMetaTest 2（合计 76 tests，0 failures）。
- diag 包零依赖契约（S-2 §3.2 / D-11）：diag 主源**无任何 import 行**（仅 Kotlin 标准库）。
- 齐备性双判据：各 sealed 层级穷尽 when（编译期证明）+ 样本名集运行期对账（Decl 8 / Stmt 15 /
  Expr 21 / Prop 11 / Term 13 / Type 8 / Pattern 8 + 辅助记录 7）。
