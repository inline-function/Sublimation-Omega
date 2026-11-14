# S-5 HIR JSON 契约（schema v3 冻结声明）

**性质**：规范性（specification/——代码与本目录冲突时以本目录为准）。**适用范围**：前端
编译器 `compile`（单文件/脚本/模块目录编译）产出的全部 HIR JSON envelope——后端、解释器、
对拍与工具链的**唯一输入面**（D-8/D-9）。

## 0. 冻结声明

HIR JSON 产品正式作为**前后端契约**冻结：

- 版本标识为 `"schema": "sublimation/hir@v3"`——envelope 必带顶层字段，缺失/版本不符即
  违约（守门见 §7 的 `HirSchema.check`；负例形态见 `T158SchemaGateTest`）。
- **版本号约定**：
  - **breaking 变更**（字段删改、语义翻转、结构重组）→ 经新 ADR 裁决，版本升至 `v4`——
    旧版本标识的产物对消费方是「不可消费」而非「尝试解析」；**不许就地兼容降级**，不破坏
    既有消费方。版本史：v1（ADR-1 §3.7）→ v2（ADR-24，声明层实例节点删除）→ v3（P-10
    class→trait，ADR-35）。
  - **加性扩展**（仅新增可选字段、不删不改既有字段语义）在 v3 内演进——由 ADR-6 决策 2
    「统一形态 + 固定字段集」保障，消费方按字段集读取、遇到未知字段应忽略；发射器新增
    字段若默认态零产（如推导元信息 `inferred/evidence`，仅推导位携带），既有产物零差。
- 冻结时点实测：**全导出路径 121 枚 envelope（模块门 77 + stdlib 10 + 双导出 10 + 脚本门
  14，见 t157gate/t157stdlib/t157det/run2）schema 字段在位且 = v3——无契约缺口，不带病冻结**；
  单文件/脚本/模块三路径皆经 `HirJson.emit` 单一发射源。

## 1. envelope 顶层结构（冻结）

"""
{
  "schema": "sublimation/hir@v3",
  "compiler": { "version": "0.1-SNAPSHOT" },
  "unit": { "path": "<单元路径>", "mode": "project" | "script" },
  "ok": <bool>,
  "diagnostics": [ <诊断条目> ],
  "hir": <HirFile 节点>
}
"""

| 字段 | 形态 | 契约 |
| --- | --- | --- |
| `schema` | 字符串 | 契约版本标识（§0）;**发射序首位**——守门与 diff 的锚点 |
| `compiler.version` | 字符串 | 编译器版本（与根 build.gradle.kts 对齐） |
| `unit.path` | 字符串 | 单元路径（模块态为挂载相对路径） |
| `unit.mode` | `project`/`script` | 工程模式（顶层全声明条目）/脚本模式（含顶层语句）——D-12 模式真值单源（透传，不由条目形态自算） |
| `ok` | bool | **按 N-SEV-BLOCK 由诊断袋 ERROR 决定**（D-10;证明检查失败即编译失败落 ok=false） |
| `diagnostics` | 数组 | 按落袋顺序保序输出（§5） |
| `hir` | HirFile | 全节点发射（§2/§3） |

## 2. 节点统一形态（冻结）

"""
{ "kind": "<HirXxx>", "nid": <数|null>, "pos": "<l:c-l:c>", "notes": [<留痕>], …语义字段 }
"""

- `kind/nid/pos/notes` **对所有 HirNode 齐备**（含辅助记录——消费者无需按位置特判，ADR-1
  §3.7 统一化、ADR-6 决策 2）。
- **固定字段集**：节点全部公开 val 显式发射（`null`/`[]` 显式落盘——消费方无需处理键缺失，
  D-9 信息保留 + schema 稳定性）;例外为推导元信息等「默认态零产」的可选字段（§0 加性扩展）。
- `pos` 紧凑串「l:c-l:c」，单点（end == null）取「l:c」。
- 字面量保留原文（`"value": "100"`）——后端 codegen 复用（ADR-1 §3.3 #8）。
- **发射序 = 字段声明序**（输出可 diff）;条目（HirDeclEntry/HirStmtEntry）透传——条目本身
  不持身份（位置与五件套由所含节点承载）。

## 3. hir 节点族清单（94 kind，发射器 sealed 穷尽分派——增删即 breaking）

分族以 HIR 层级为准（程序面/命题面分家，S-1 §3）：

| 族 | 数 | kind |
| --- | --- | --- |
| 文件/辅助 | 4 | HirFile、HirParam、HirWhenArm、HirWhenPropArm |
| 声明 HirDecl | 6 | HirFunDecl、HirTraitDecl、HirTraitConst、HirStructDecl、HirTypeAlias、HirErrorDecl |
| 理论参数 HirTheoryParam | 2 | HirTypeParam、HirPropParam |
| 语句 HirStmt | 15 | HirVarStmt、HirAssign、HirAxiom、HirByStmt、HirUnchecked、HirExprStmt、HirBecause、HirWhenProp、HirIfProp、HirExistsIntro、HirExistsElim、HirLocalFun、HirStrategy、HirReturn、HirErrorStmt |
| 表达式 HirExpr | 26 | HirIntLit、HirFloatLit、HirStrLit、HirTop、HirBot、HirNameRef、HirBinExpr、HirBorrow、HirCall、HirField、HirFunRef、HirTypeTest、HirTask、HirFlow、HirInst、HirLambda、HirAnonFun、HirTuple、HirStructCtor、HirByExpr、HirVarExpr、HirBlock、HirIf、HirWhen、HirErrorExpr |
| 模式 HirPattern | 8 | HirPatLit、HirPatCtor、HirPatTuple、HirPatIs、HirPatBind、HirPatElse、HirPatCond、HirErrorPattern |
| 类型 HirType | 9 | HirNamedType、HirFunType、HirTupleType、HirMutType、HirAtomicType、HirRefType、HirUnionType、HirQualifiedType、HirErrorType |
| 命题 HirProp | 11 | HirPTop、HirPBot、HirPNot、HirPAnd、HirPOr、HirPImp、HirPIff、HirPForall、HirPExists、HirPExists1、HirPAtom |
| 命题项 HirTerm | 13 | HirTInt、HirTStr、HirTName、HirTDollar、HirTParamRef、HirTTop、HirTBot、HirTBin、HirTUni、HirTCall、HirTInst、HirTField、HirTSymbolCall |

- `*Error*` 族（HirErrorDecl/Stmt/Expr/Type/Pattern）为恢复期产物**保留通道**（诊断不丢、
  结构不断）——消费方遇 Error 节点即知该子树为错误恢复产物（D-9 信息保留）。
- 证明义务节点携带 proofContext（facts/matched）/rule/verdict 三字段（ADR-1 §3.4/§3.7）。
- 符号引用为辅助记录 `{ "id": <数>, "kind": "<族>", "name": "<名>" }`——id 权威、kind/name
  冗余可读（ADR-1 §3.7）;kind 族 13：fun/trait/struct/enum/ctor/field/alias/propdef/
  theorem/param/local/builtin/trait_const。

## 4. notes 留痕（D-9 信息保留——脱糖/补注的落盘载体）

"""
{ "kind": "desugar"|"hint"|"supplement", "code": "<码>", "message": "<文>", "pos": "<l:c-l:c>",
  "from": "<原形标签>", "to": "<目标形态标签>" }
"""

- `desugar`：脱糖通道留痕——`from/to` 记录原形/目标形态标签，`code` 沿用脱糖规则编号
  （如 `D-PAT-CTOR`）。消费方（LSP/教学/审计）可据 `from→to` 回溯源写法。
- `hint`/`supplement`：旧体系四族中的两个非阻断族（D-10——非阻断**不等于**不出产物：
  见 `documents/HIR-JSON诊断段契约.md` 三条纪律）。
- notes 随节点落盘（非阻断诊断另有顶层 diagnostics 段——两处留痕分家：节点内=结构留痕，
  顶层=编译结论文本）。

## 5. diagnostics 段（四级别形态，冻结）

"""
{ "severity": "error"|"warn"|"hint"|"supplement", "code": "<码>", "pos": "<l:c-l:c>",
  "message": "<文>", "module": "<串>", "file": "<串;仅多文件模块源件归属时发射>", "nid": <数|null> }
"""

- 阻断性由**严重级**判定而非码族字母（N-SEV-BLOCK）：**只有 error 阻断编译**（→ ok=false）;
  族与级同名同义：E→error、W→warn、H→hint、D→supplement（确认性/补充族）。
- `pos` 从不为 null（无位置的编译级结论由调用方落文件级合成位置，S-1 层间总契约 2）。
- `nid` 反引 HIR 节点身份（D-9），未关联节点时 null（lexer/parser 期诊断）。
- `file` 字段仅多文件模块体（源件归属，t155）发射，单文件/挂载期诊断不发射（SAME 面零动）。
- 消费方分流建议：error 阻断消费（ok 为 false 时后端不应继续）;warn/hint/supplement 全量
  保留（审计/教学/LSP/CLI -v 的输入——ADR-1 §3.3 #3）。

## 6. nullable 与空集合的显式发射

固定字段集（§2）使消费方无需处理键缺失：`null` 与 `[]` 显式落盘。唯一例外为 §0 的
「默认态零产」可选字段（加性扩展纪律——既有产物逐字零差）。

## 7. 版本守门（消费方契约闸门）

`sublimation.frontend.json.HirSchema.check(envelope: String): String?`：

- 通过返回 null;失败返回原因串（缺顶层 schema / 版本不符 / 顶层非 JSON 对象）。
- 实现为最小结构扫描器（不引入第三方解析器；处理字符串转义与嵌套深度，只认**顶层**键——
  嵌套同名键与串内诱导文本一律无效）。
- **版本不符即拒收**：v3 消费方遇 `sublimation/hir@v4` 产物应失败并报告（契约文档为本，
  不静默兼容降级）。
- 契约版本与发射器单源同体：`HirSchema.CONTRACT` 透传 `HirJson.SCHEMA`（改版本只改
  `HirJson.SCHEMA` 一处——双源漂移即违约）。
- 断言测试 `T158SchemaGateTest`（四门）：单文件面 schema 锁；全导出路径（模块 main + 子件
  + stdlib 默认挂载侧）schema 一致；守门负例（缺字段/版本不符/诱导文本/嵌套键/非对象=失败，
  正确 envelope=通过）;冻结快照（单源 + v3 逐字锁定）。

## 8. 消费方指南（后端开张契约面）

1. **先守门再消费**：`HirSchema.check` 通过（schema = sublimation/hir@v3）才进入解析；
   遇未知字段忽略（加性扩展 §0），遇缺失字段报错（固定字段集 §2/§6）。
2. **`ok` 分流**：ok=false ⇒ 诊断袋含 error，后端不得继续（证明检查失败即编译失败，S-1）;
   ok=true ⇒ 产品结构完整可消费，warn/hint/supplement 仅供工具链展示与审计。
3. **`unit.mode` 分流**：script 含顶层语句条目（HirStmtEntry）;project 只有声明条目
   （HirDeclEntry）——模式为真值透传，消费方勿由条目形态自算（D-12）。
4. **notes = 结构性留痕（D-9）**：脱糖回溯（from/to）、教学提示与确认性补充——**不得**视为
   错误;节点内 notes 与顶层 diagnostics 分家消费（§4）。
5. **诊断四级别全量消费**（§5）：阻断只看 severity=error;新增任何诊断码（含 W/H/D 族）
   都会改变受影响样本的 JSON（`documents/HIR-JSON诊断段契约.md` 纪律 1——「提示不影响产物」
   在本项目为错误命题）。
6. **nid 语义**：节点身份由 `nid` 唯一标识（节点 id 单一源，ADR-14）;诊断 `nid` 反引用
   产生该诊断的节点（lexer/parser 期 null）。

## 9. 契约稳定性保障机制（本契约为何可冻结）

- **t122n 死冻锚**：68 门单文件面产物逐字冻结（基线台账），任何意外波动即在各任务的差异
  台账段报红——契约面的「回归锚」。
- **差异台账纪律**（每批次 parity 五段）：68 门波动必须**可归因**（authorized 差=该批次
  显式声明的形态差，如 stdlib 件扩容）+**零未登记差**（其余件逐字同）;中间态允许 diff，
  但必须说得出闭合任务（`documents/HIR-JSON诊断段契约.md` 纪律 3 同源）。
- **零分配 nid 纪律**：快照/通道改造按名回收，新增声明按声明序续号——符号 id 不因通道
  改动漂移（双编译逐字节同的承诺由确定性测试把关，模块双导出 10/10）。
- **发射器 sealed 穷尽分派**（无 else）：节点族增删为编译期齐备性事件——即 §0 的 breaking
  变更信号，逼迫 ADR + v4 流程。
- **纯函数发射**（相同输入产相同输出，S-1 层间总契约 3）+ UTF-8 落盘、pretty-printed
  固定缩进（可 diff）。

## 10. 生效与引用

- 本契约替代 `HirJson` 注释块中的 v1 期自述（v3 为现行版本）;冲突以本目录为准
  （specification/README.md 效力条款）。
- 版本史与动机：ADR-1 §3.7（v1 设计）、ADR-9（发射器实现）、ADR-24（v2）、ADR-35（v3
  class→trait）;诊断段实测基线：`documents/HIR-JSON诊断段契约.md`。
- 冻结批次：t158（2026-11-14，schema v3 冻结声明——契约文档 + 版本守门 + 消费方指南）。
