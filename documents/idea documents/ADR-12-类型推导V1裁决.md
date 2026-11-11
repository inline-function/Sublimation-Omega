# ADR-12 类型推导 V1 裁决（t8）

- 状态：已落地（实现 + 测试通过）
- 日期：2026-11-05
- 范围：t8「实现类型推导 V1」——在 t7 语义层骨架（S-2/ADR-10）上，按 ADR-6 §四 4.2/4.3 落地类型推导纯函数插入件
- 对齐依据：P-7 全对齐旧 checker/TypeInfer.kt、Types.kt 主干（字典点查；旧实现仅作口径参照，不做全部 36+ checker 码）

## 一、背景与约束

ADR-6 §四 定四维解耦：4.1 结论上树（消灭旁挂表）；4.2 四件套重划——Types.kt 的类型代数与
TypeInfer.kt 合并入语义层 TypeInfer；4.3 **Elaborator 单驱动**——TypeInfer/ProofChecker 不得
自走树，签名形如「（节点，上下文）→（结论，诊断）」；4.4 帧栈 + Γ 快照，诊断单向落 DiagBag 不抛异常。

t7 已交付 Semantic/SymbolTable/Collector/NameResolver/PropNormalizer/Elaborator 六件与 22 项
SemanticTest；HIR 节点字段（含每 Expr 的 type 结论位与 SymbolRef 解析位）由 t6 冻结、t10 对齐
JSON 发射器。t8 在此契约内落地「旧 TypeInfer + Types.kt 主干 + CallCheck/WhenCheck/checkStmtInner/
checkFun/checkBin 的类型族主脉」，证明/效应/字典族推后。

## 二、裁决

### 2.1 插入件定位与代数归属

- `TypeInfer` 为 `internal class TypeInfer(alloc, diag)`：**纯函数语义**，不遍历树、不持有帧、
  不依赖 SymbolTable（避免与解析层双向耦合）；由 Elaborator 各构造分支在节点构造完成、子类型就绪后
  单次调用（§四 4.3 唯一驱动纪律）。
- 旧 Types.kt 代数主干（render/syntheticT/unsealedT/isNominal/isAnyExpect/numericRank/looseEq/
  typeEq/compatibleView/adaptOk/unifyInto/unify/builtinOp/expand 入口）**整体迁入本文件**——
  AST 保持零语义（S-1 §2），类型代数归语义构造层（ADR-6 §四 4.2）。
- `unifyInto` 按旧主干全分支移植（决策 83）：变量 = 名字在 vars 且零实参的 NamedType；变量-变量
  单向绑定；occurs check；结构递归（NamedType/FunType/TupleType）；已绑定遇更高数值类型宽松保持；
  synthetic 不绑定；HKT-U2 kind 应用合一按 arity 表驱动（V1 仅形参态，完整实例化推 t9）。
- 内建签名表 `builtinSig`：旧 builtinimpure/builtinPure 两表的**类型层投影**（参数/返回 + theory
  参数位以 synthetic 承接）；未登记内建按值符号类型兜底（null/true/false → Null/Bool）。

### 2.2 结论上树与字面量兜底

- 推导结论写节点 type 字段（HirVarStmt/HirCall/HirBinExpr/HirIf/HirWhen/HirLambda/HirTuple/
  HirStructCtor/HirNameRef/HirFunRef/HirField/…，ADR-6 §四 4.1）；HIR 节点字段已冻结，**不新增
  字段**——HirIntLit/HirFloatLit/HirStrLit/HirErrorExpr/HirByExpr/HirBlock 等无 type 位者由
  Elaborator 侧 `etype()` 提取（字面量 → Nat/Rat/Str；块 → 尾表达式或末条 return）；块值口径：
  末条 return 的块以返回值类型为块值（Kotlin 语义：return 后无续行）。

### 2.3 签名分层（本文件 vs Elaborator）

- 签名类查询（fun/ctor/method/struct 字段/别名）住 **Elaborator 的声明索引**（funDecls/
  structDecls/enumDecls/implDecls/aliasDecls，file() 预扫描，AST 只读）：其构造需要 type() 映射与
  声明序，属「建立上下文」而非遍历检查；TypeInfer 只消费已算好的子节点类型与签名入参。
- `etype()/blockType()/nameRefType()/sigOf()/sigFunType()/expand()` 为 Elaborator 侧结论提取与
  签名拷贝件；return 上下文帧 FunFrame(retType, sawReturn) 由 funDecl/lambda/anonFun 成对压栈。

### 2.4 调用点静默重解析与去重

- 声明位为规范解析；调用点签名重解析会复用 type()，产生与声明位同码同位同消息的重复条目——
  本不在各分支做静默分支复制，而是在 `Semantic.compile` 收口前统一 `diag.dedupe()`（DiagBag
  既有语义，不吞任何唯一诊断）。该决策同时保 nid 前序确定性（同源两次编译树相等，S-1 §3）。
- 命名实参重排失败（E-NAMED-ARG）时跳过该调用的参数核对，避免重复诊断（t7 测试口径保留）。

### 2.5 V1 emit 码集（P-7 对齐）

E-TYPE-MISMATCH（var 标注 / 函数体 / 实参 / 构造子实参 / 构造子实参数 / struct 字段 / 中缀比较
  七种口径）、E-ARITY（多了）、E-RETURN-TYPE（值/裸 return 两口径）、E-RETURN-OUTSIDE、
E-IMMUT-ASSIGN、E-ASSIGN-TARGET、E-MISSING-RETURN（块体简化见 2.6）、E-NON-SEALED-MATCH、
E-NO-METHOD、E-TUPLE-DESTRUCT、E-TUPLE-ARITY、E-FIELD-MISSING、E-DUP-FIELD、E-UNBOUND-NAME
  （struct 无字段）、W-NON-EXHAUSTIVE、D-TYPE-INFERRED（未标注 var/解构分量）。

### 2.6 已知简化（V1 边界，逐项记因）

- **E-MISSING-RETURN**：仅 sawReturn 单标记，无 diverges/控制流分析（when/if 全覆盖判定）——
  块体核对的旧控制流件推 t9；块体在 sawReturn 为真时不重复体级类型诊断（逐 return 已核对）。
- **lambda 推导**：形参以 synthetic 承接，不做双向反推（T2 决策 55/57）；调用户型核对经 adaptOk
  函数结构递归放行（synthetic 参数位宽松通过），与旧 checkLambda 结论等效的近似口径。
- **when 穷尽性**：密封枚举主题按 ctor 覆盖 + else 判定；非枚举主题无 else → unsealedT（§49）
  且 W-NON-EXHAUSTIVE；var 位静默（D-TYPE-INFERRED），仅函数体位落 E-NON-SEALED-MATCH
  （t7 二十二项口径保持）。
- **数值塔单向**：adaptOk 内 Nat→Rat 升兼容、Rat→Nat 降级拒绝（旧 numericDemoteAt 方向性）；
  var 标注位另显式拒绝降级（unify join 的对称口径会放行降级，故 varAnnotated 单列判定）。
- **synthetic 宽容**：半成品类型（错误表达式/未解析名/lambda 形参）一律放行核对，与旧「宽松通过」
  口径一致（S-1 §2 容错链：hasError 由 DiagBag 判定，半成品树照常返回）。
- **@mut 可写**：语句注解 `var @mut x` 与类型位 `var x: @mut T` 双来源（旧 Frame.isMut 两口径）。

## 三、延后清单（t9 及以后）

W-DIV-ZERO；证明族（E-POST-UNPROVEN/E-CANNOT-PROVE…，PropNormalizer 已就位）；效应族
（E-IMPURE-*/E-NEED-ASYNC，calleeImpure/awaitPoint 字段已冻结）；字典族（E-NO-INSTANCE，
dictionary/slot/dictArgs 字段已冻结）；HKT arity 完整实例化；W-UNREACHABLE/diverges 控制流；
限定类型（HirQualifiedType 解析与核对）；module/多文件；智能转换 smartCast 落点；
W-WHEN-NO-COMMON（命题 when 公共类型）。

## 四、验证

- `./gradlew :frontend:compileKotlin` 绿（零 error，两处 warning 为既有）。
- `./gradlew :frontend:test` **237 项全绿**：SemanticTest 22（t7 回归无影响）+ TypeInferTest 36
  （本轮新增：代数移植 unifyInto/unify/builtinOp/render/adaptOk 九项 + 结论上树 27 项）+
  HirJsonTest 19（93 节点，t10 回归无影响）+ parser/lexer/cli 其余。
- 破例修复（列入 changedPaths）：cli/AstPrinter.kt（EnumDecl 的 Ctor 孩子行：Ctor 非 AstNode，
  按名 + 字段行展开）、cli/CliTest.kt 两处断言口径（邻接标记行尾无尾随 tab；contains 消息位误入
  参数）+ SemanticTest 两处口径（astReadOnlyContract 的 0 参函数误传 1 实参；
  parseNotesRelocatedToHirNodes 的 print(1) { 2 } 换为尾随 lambda 合法形态）。

## 五、索引

- 实现：frontend/src/main/kotlin/sublimation/frontend/semantic/TypeInfer.kt（新增，代数 + 结论方法）、
  Elaborator.kt（接线）、SymbolTable.kt（SymbolEntry.type/mutable + 声明签名扩展）、Semantic.kt
  （dedupe 收口）。
- 测试：frontend/src/test/kotlin/sublimation/frontend/semantic/TypeInferTest.kt（新增 36 项）。
- 上游裁决：ADR-6（四维解耦）、ADR-10（语义层接口）、ADR-9（HIR 契约与 JSON 发射）。
