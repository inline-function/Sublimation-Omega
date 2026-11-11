# ADR-54：lambda 纯度推导（t94,N1 硬前置）与 HirFunType 名义位

## 状态
已落地（t94，2026-11-10）。purity **节点位**（HirLambda / HirAnonFun.purity）改为按 body 真推导；
**类型位**（HirFunType.purity）保持名义 PURE——有意过渡态，t96（pure<f>/PURE<:IMPURE 子类型序）收口。

## 背景（8 处硬编码归类）
| 位点 | 归类 | 处置 |
|---|---|---|
| Elaborator:705 | FunDecl→HirFun，@impure 注解决定 | 不变（既有口径）|
| Elaborator:1188 | LambdaExpr 的 HirFunType | **名义位**：保持 PURE |
| Elaborator:1190 | HirLambda 节点 | **真推导**（主目标）|
| Elaborator:1253 | AnonFunExpr 的 HirFunType | **名义位**：保持 PURE |
| Elaborator:1257 | HirAnonFun 节点 | **真推导**（同 lambda 规则；AnonFunExpr 无注解位已核实）|
| Elaborator:1535 | sigFunType：声明 fun 的引用类型 | **一致性修复**：改读 `d.annotations` |
| TypeInfer:1096 | 方法引用 HirFunType 合成分支 | 名义位，保持 |
| TypeInfer:1158 | funT 工程化缺省参 | 名义位，保持 |
（另有 HIR 节点构造缺省值 Decl.kt:29 / Expr.kt:202,218 / Type.kt:28——非决策位；E:2314/2513 为 AST
类型串→HirPurity 的既有正确转换。ctor 在 HIR 中无独立 purity 位——“ctor 恒纯”无落点，仅注释说明。）

## 决策
1. **Fork B（仅节点层）**:HirLambda/HirAnonFun.purity = body 含不纯调用 ⇒ IMPURE 否则 PURE。
   选 B 而非 A（类型层一起真）的理由：typeEq（TypeInfer:304）对 HirFunType 的 purity 是**严格相等**，
   类型层一旦带真 IMPURE，`var f: () => Null = { => print("x") }`（注解默认 PURE）会新落 E-TYPE-MISMATCH
   ——变相提前激活纯度闸，越 t96（pure<f>/子类型序）的域。节点属 JSON 可见字段，闸不消费它。
2. **有意过渡态登记（队长硬要求）**:同一 lambda 的节点位 = IMPURE（真），类型位 = PURE（名义）。**这不是漏改**——
   HirLambda/HirAnonFun.purity 是 ground truth（本任务起真化）;HirFunType.purity 是**名义位**，
   故意保持，留待 t96 的 pure<f> / PURE<:IMPURE 子类型序设计时统一收口。T94 测试以
   `lambda类型位保持名义PURE` 用例锁定该过渡态。
3. **E:1535 一致性修复**:sigFunType 恒 PURE 与 E:705 注解口径自相矛盾——`@impure fun` 的引用类型
   （o::f / funRef）此前恒 PURE，现按 `d.annotations` 带 IMPURE（属一致性修复而非闸：样本 0 funRef 节点）。
4. **内建不纯名表恢复**:新 builtinSig（61 名）是旧 Types.kt builtinimpure(26 名)/builtinPure(35 名)
   两表合并的类型层投影、纯度信息在投影中**丢失**——builtinSig 无法区分。故按旧 builtinimpure 逐字
   恢复 26 名于 TypeInfer.builtinImpureNames（print/readLine/readFile/writeFile/getArgs/sleep/
   appendFile/fileExists/readDir/timeNow/rand/delay/runBoth/runSeq/collectToList/collectCount/
   collectAll/withMutex/bumpCounter/bumpPair/channelFromList/sendAll/channelDrain/channelPipe/
   sublEval/sublRun），与 builtinSig **同源，改须同步**。
5. **推导规则**:
   - 不纯调用 = callee 落：内建不纯名（用户同名声明遮蔽时以声明注解为准）/ 用户 @impure 声明
     （funDecls 注解，E:705 同源）/ 被直接调用的内层 lambda·anonFun（其 purity 经 elaboration
     递归天然底向算好——T94 嵌套两层用例实测）/ funRef 携带的类型位。
   - 传 lambda 不计（求值只造闭包，潜在效应记在自身节点位）；调用才计。
   - 覆盖复合节点：HirBlock/HirIf/HirWhen/HirTask/HirFlow/HirCall/HirBinExpr/HirUniExpr/
     HirTuple/HirStructCtor/HirByExpr/HirInst/HirField/HirTypeTest/HirVarExpr 及语句族。
   - **已知保守点**：`var g = { => print("x") }` 后 `{ => g() }` 按纯处理——经 var 的数据流追踪
     不在本任务（触发条件：经 var 间接调用不纯闭合）。T94 测试 `保守局限经var间接调用` 锁定登记。

## 发现项（不在本任务预算，转 t96)
- **HirCall.calleeImpure（被调者效应结论字段）当前恒 false**（3 个构造位 Elaborator:1674/1770/1811）。
  本扫描不经它（自带判定链）；其真化会为 68 样本中全部内建不纯调用翻转 JSON(calleeImpure=false→true)，
  属 t96 闸设计的料，**未在本任务 diff 预算内**——留给队长另批。

## 验证
- 全量 **833/0**（813 + T94LambdaPurityTest 10 例；委托前报告批的零 diff 预算闭合）。
- e2e **65/68** 三冻结红逐字不变（list/手动证明/模块系统）。
- 68 样本 JSON:vs out/t92 **SAME=68/68 零差异**;vs base-t74 SAME=55/DIFF=13——diff 集与既有继承集
  逐处一致（t80/t82 源码迁移），**零新增、零未归因**。
- 行为探针（probe-mut/cases-t94/,p1–p9）：`{ => print("x") }`→impure(红基线变绿）、`{ => 1+1 }`→pure、
  嵌套外纯内不纯、直接调用内层→外不纯、@impure 用户声明入 lambda→impure、匿名函数同规则、
  @impure 引用类型带 impure（E:1535)。

## 规格参照
`documents/R2落地计划-纯度闸与@ref.md`（队长）；t96 将消费本任务的 ground-truth 节点位。

## 关联（I-4 定案，2026-11-10 补档）

- **unchecked 不对称口径**（效应存在 vs 检查豁免）:见 ADR-55 §unchecked 不对称。
  containsImpureStmtCall **递归进 HirUnchecked** 的决定（`{ => unchecked print }` 判 IMPURE）
  在该节定案并配锁定测试;
  本 ADR 的 lambda 纯度推导消费该口径——纯度位忠实表达效应，闸（t96/t97）才谈放行。
