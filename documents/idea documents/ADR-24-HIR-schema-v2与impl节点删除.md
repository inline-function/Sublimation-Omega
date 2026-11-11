# ADR-24 HIR-JSON schema v2 与 impl 节点删除

**日期**：2026-11-06（P-8/C，t29）
**状态**：已落（与卷 TS-30/31 对账）

## 一、决策

1. **SCHEMA 升版**：`HirJson.SCHEMA` 由 `sublimation/hir@v1` 升为 `sublimation/hir@v2`（卷 TS-31）。
   v2 与 v1 的差异：①声明层删除 `HirImplDecl`（及其 AST 源 `ImplDecl`）；
   ②`HirFunDecl` 可选 `receiver` 字段（自由函数省略、扩展函数必现，TS-31）；
   ③类型层新增 `HirSelfType` 节点并以固定形态 `{"k": "self"}` 发射（TS-29/31，t27 已落）。
   ④声明层新增 `HirClassConst`（P-8/B，t28 已落）。
2. **ImplDecl/HirImplDecl 删除**（卷 TS-30）：无条件删除，无兼容期——parser 自 t28 起
   将 `impl` 报 `E-IMPL-REMOVED` 并落 `ErrorDecl`；旧 impl 的语义分派（方法糖查 impl 方法表）
   由 t30 的**扩展函数桶**（`extFunDecls` + `SymbolTable.lookupExtFun`）接管。
3. **零残留清扫**：`ImplDecl`/`HirImplDecl` 在源码与测试中清零（ast/hir/json/cli/Collector/
   Elaborator/各测试）。历史 ADR 与日志保留原文（append-only 决策记录，非残留）。

## 二、裁决理由

- TS-31 将升版与 impl 删除绑定为一项 breaking 变更：接收者/自类型等 v2 字段与节点删除混入 v1
  会让消费者无法判别产物形态，故整版。
- ADR-9 §纪律「breaking 变更须追加新 ADR，不就地改版本」：本 ADR 即其兑现（v1 常量的历史
  文本保留于 ADR-9，不改写）。

## 三、后果

- 正面：产物形态与卷一致；声明层更瘦（结构化满足 D4：T 满足 C ⇔ C 每条规范都有以 T 为
  接收者的扩展函数）。
- 负面：v1 消费者须升版——本期为单工程内部产物，无外部消费者。
- `SymbolKind.IMPL`/`implEntry`/`declareMethod` 等 symbol 表设施保留（公开声明无未用警告；
  t30 视扩展函数登记口径决定去留）。

## 四、实施时序与本次偏差登记

- 按卷时序：t27（A：节点+发射增量）→ t28（B：语法层）→ **t29（C：删除+升版）**→ t30（D：语义层接线）。
- **本次偏差登记（供队长复核）**：t29 任务书 item 4「语义层残留只登记不改」与 item 3「零残留清扫」
  及验收「全绿」冲突——语义层 11 处 `ImplDecl` 引用为 t28 后不可达死代码（parser 不再产出），
  若不切除树不可编译；故对 Collector.kt（1 处 import + 1 处 when 分支 + collectImpl 函数）与
  Elaborator.kt（2 处 import + prescan 分支 + impl 构造块 + 3 处方法糖回退）做了**最小机械切除**，
  零行为变更（implDecls 自 t28 恒空）；语义逻辑（扩展函数桶/Self:=T）未动，归 t30。
- t29 执行中发现：t30（semantics，in_progress；**于 t28 交付后正常开启**，t28 的 342 绿结论成立）已在 Elaborator 落入半成品 ext 消费代码
  （`extFunDecls` 填充 + callExpr 消费 + `lookupExtFun`），其引用的 `typeHeadOf`/
  `pickExtDecl`/`extCallType` 三个私有函数尚无定义——t29 的全绿验证待 t30 落定义后重跑。

## 五、验收判据修正：PowerShell 下 gradle 编译错误的假阴性（队长要求固化）

- 现象：在 PowerShell（pwsh 工具）里跑 `gradlew.bat :frontend:compileKotlin` 时，编译器错误
  经 PowerShell 的 NativeCommandError 流到达，行形如 `gradlew.bat : e: file:///…/Foo.kt:12:3 …`
  （前缀是 `gradlew.bat : e:`，不是裸 `e:`）。用 `/^e:/` 正则过滤 stdout **会漏检全部编译错误**，
  产出「COMPILES CLEAN」的假阴性结论；`w:`（警告）同理。
- 正确判据（t29 起采用，双条件）：
  1. 以构建任务总结标记为准——`BUILD SUCCESSFUL` / `BUILD FAILED`（`:> Task …FAILED` 亦可）；
  2. 错误/告警行扫描用 `.kt:\d+` + `(e:|w:)` 双模式，且必须覆盖 stdout 与 stderr 的合并文本，
     不能只匹配行首裸 `e:`。
- 历史影响：此前若干轮「compileKotlin 零警告」的验证可能含同类假阴性（gradle 输出经 PowerShell
  时）。本判据为后续所有编译验收的最低口径；对既有结论的复核以重跑为准（t29 终验即按此执行）。
- 记录：此项由队长在 t29 代码评审中指出并要求固化（2026-11-06）。

## 六、引用

- 规范性内容：documents/boring documents/类型系统卷-TS-v1.md（TS-28/29/30/31）
- 上游决策：ADR-8（类型系统改向，§3.1 分阶段注记）、ADR-9（发射器与显式 null 纪律）、ADR-23（语法邻接判据）
- 日志：documents/log/2026-11-06.md（t29 条目）
