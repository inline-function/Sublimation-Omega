# LSP 框架大纲：增量化语言服务器架构（设计提案 v0.1）

> 状态：**设计提案 v0.1，零码改、零代裁**（t164 立项令）。队长唯一裁决入口 = §8 OQ 清单 + §10 前置件/门。
> 性质：准备件——LSP 正式开工等 **R-06 修件 t163（R-31 裁决实现）收尾队长验收**后启动（§6 上线门）。
> 本文档遵循全队纪律：诚实边界（§9）、不代裁（§8 开放问题清单）、数字要有出处（§11 引用表）。

---

## 0. 用户硬约束（写在最前）

**增量框架，不是全量。**

- 用户原话（t164 任务书转述）：旧工程 LSP **全量重建**导致非常慢，用户明确「**宁愿新工程开发速度慢一点**」也要增量。
- 本提案的一切取舍按此排序：**增量正确性优先于实现简单度，实现简单度优先于功能覆盖面**。凡"全量重跑更简单"的捷径，在本文内视为**已被否决的候选**，仅可作保底路径（G0，§2.2）。
- 纪律留痕要求（S-3 §2.1：任务书引用须落条款而非对话记忆）：本约束目前仅存于 t164 任务书（对话层载体）。**LSP 正式开工的前置动作之一 = 把「增量约束 + 旧工程全量教训」写入一份新 ADR**（§10 P-L0），避免对话失传。

### 0.1 旧工程教训（字典点查，不通读）

旧工程语言服务模块 `language-service`（F:\KotlinProject\sublimation）核心策略摘录：

> 「核心策略：每次 didOpen/didChange 重新 tokenize + parse + check，缓存 AST 与诊断结果，后续 hover/definition/completion 直接查缓存。」——旧 LspAnalysis.kt 类注释（:18）

即：**结果级缓存、过程级全量**。每次击键全量重跑词法→语法→语义三遍，且 check 输入是「用户条目 + stdlib 拼接、stdlib 位置整体平移到用户文件行数之外再靠行号过滤剔除」（旧 LspAnalysis.kt analyze() :349、diagnosticsFor() :494）——stdlib 符号量不小，每遍全量重挂。结构性结论：**旧 LSP 的慢不是某处实现失误，是架构选择**；新工程要快，必须在架构层就增量，而不是事后优化。

诚实边界（§9-1）：本工程**无旧 LSP 性能实测数据**（仓库无 profiling 记录）；"慢"的依据 = 用户报告 + 上述结构事实。增量收益的具体数字须由 V1 的度量计划（§4.4）实测产出，本提案不做任何延迟断言。

---

## 1. 事实基础（全部实测/点查，取证见 §11）

### 1.1 新工程管线与语义驱动器

| # | 事实 | 取证 |
|---|---|---|
| N1 | 词法器**流式契约已兑现**：`tokens(): Sequence<Token>` 惰性序列，"为增量编译与 LSP 留路"；单遍消费约束（游标推进，只能完整消费一遍） | lexer/Lexer.kt 类注释 + :26/:31 |
| N2 | 解析器三入口（工程/脚本/片段），**容错恢复**：语法错不抛异常，记 E-SYNTAX + Error* 占位节点 + ensureProgress 进展保证 + errDeclSync 声明级同步 | parser/ParseResult.kt；ADR-7 §2.2 |
| N3 | 邻接消歧 **D-1~D-21 全部是 token 邻接判据**（precededBySpace / 同行关系），**跨行即视为下一结构起始**（D-2/D-4）——行级增量不能只看被改行 | ADR-7 §2.3 |
| N4 | 语义驱动器 = **R 轮不动点重跑**：R ≤ 8 轮（`INFER_ROUNDS_DEFAULT = 8`），跨轮**共享单调 NodeIdSource**（H1），每轮新燃料池（H3，`FUEL_DEFAULT = 237500`）；零元变量单趟直通快路径已存在（首轮 metaCount==0 即停） | semantic/Semantic.kt:27/93/108/145；TypeInfer.kt:194 |
| N5 | **轮次确定性**（P-11 §9.3）：静态键同输入同需求 ⇒ 同轮数 ⇒ 同 nid 布局；提升需求（R-06）跨轮单轮预应用重跑（t125 S5b） | semantic/Semantic.kt:209-223 及注释 |
| N6 | 对外入口 `compileFrontend`：纯函数契约（S-1 层间总契约 3：相同输入必产相同输出）；产物 = HIR + 诊断袋 + JSON 文本三件套；**半成品原则**：ERROR 也返回完整树；internal 重载支持 `mounted: Map<String, ModuleSnapshot>`（t137 stdlib 默认挂载快照） | cli/Frontend.kt |
| N7 | **HIR JSON v3 已冻结**（S-5 规范性）：envelope `{schema, compiler, unit, ok, diagnostics, hir}`，94 kind 节点族 sealed 穷尽发射，守门 `HirSchema.check` 版本不符即拒收；冻结时实测 121 枚 envelope schema 字段齐备 | specification/HIR-JSON-Schema-v3.md §0/§3/§7；log/2026-11-14 t158 |
| N8 | 诊断段为 envelope 一等公民：四级别全量发射（实测基线 68 样本：error 10 / supplement 59 / hint 3 / warn 2，覆盖 26 样本文件）——**新增任何诊断码都会改变样本 JSON** | documents/HIR-JSON诊断段契约.md |

### 1.2 诊断模型与模块系统

| # | 事实 | 取证 |
|---|---|---|
| N9 | 诊断四级别 131 码（实测 grep frontend/src/main 去重计数）：**E 76 / W 10 / H 11 / D 34**（与任务书同数）；阻断性由严重级判（**N-SEV-BLOCK**：只有 ERROR 阻断 → ok=false） | diag/Diag.kt；grep 实测 |
| N10 | Diag 结构 = severity + code + pos(SourceRange) + message + modulePath + **file（t155 多文件模块源件归属）** + **nid（HIR 节点身份反引）**；DiagBag dedupe 按 (code,pos,message)、report 默认隐藏 SUPPLEMENT（CLI -v 打开） | diag/Diag.kt |
| N11 | 模块系统：**.settings 是目录级挂载文件，不是源码语句**（语言无 import 产生式）；ModuleTree 解析前一次性建成、全管线只读共享；挂载必须 DAG（E-MOUNT-CYCLE）；目录=模块、父→子可见、子不自动挂载 | R3-S7-模块系统设计-提案.md §1 |
| N12 | 多文件模块体：同目录 .subl 按名序拼接、**诊断行号按模块拼接连续累加**，file 字段指回源件（t155 attributeFiles） | diag/Diag.kt attributeFiles 注释；R3-S7 §1.2 |
| N13 | 挂载态编译产物 = 每模块一个 envelope（unit.path = 模块相对路径）；脚本模式与工程模式互斥（D-12 模式真值单源） | R3-S7 §1.3；S-5 §1 |

### 1.3 测试纪律与旧 LSP 协议面

| # | 事实 | 取证 |
|---|---|---|
| N14 | 差分台账纪律已机械化：t122n **死冻锚** + 五段 parity（68 门台账 / 精确差分逐字比对 + authorized 归因 / stdlib 基线 / 双导出确定性 / 脚本门）；负例先红；零分配 nid | jsoncmp/scripts/t*-parity.ps1（如 t160-parity.ps1） |
| N15 | 管道缺陷台账 D-1~D-6：批量导出静默截断（导出后须核对 COUNT=68）、连续 spawn 丢失输出（≤20/次）、"测试存在"≠"被执行"（XML 例数对账）、基线只读、构建槽不保护"树正在被改"、关键词命中≠残留 | documents/工具与管道缺陷台账.md |
| N16 | 旧 LSP 协议面（LSP 3.17 子集、零第三方依赖、stdio JSON-RPC）：initialize/didOpen/didChange/didClose/publishDiagnostics/hover/definition/completion/documentSymbol；**单线程 stdio 循环**（扩展点/插件亦同） | 旧 LspServer.kt 类注释（811 行）；LspAnalysis.kt（3137 行）；LspExtension.kt 线程模型注释 |
| N17 | 旧 severity 映射：ERROR→Error(1)、WARN→Warning(2)、**SUPPLEMENT→Information(3)**、HINT→Hint(4) | 旧 LspProtocol.kt:196 |
| N18 | 旧 LSP 有 per-code **fix 文案表 DIAG_DOCS**（如 E-ARITY：「实参多了可以删（IDE 灯泡有删多余实参的快速修正）」）——code actions 的文案先例 | 旧 LspAnalysis.kt:2894 |
| N19 | R-26.2 注解纪律已落注册表：通道 × 合法位（AnnPositions）+ E-UNKNOWN-ANN（消息含全注册表文案）+ E-ANN-POSITION（报该注解合法位）——**提示消息已有 fix 文案基础**（t149 批史） | stdlib-设计-提案.md §20 |
| N20 | 前端套件规模 1304/1304（t160 交付态）；e2e 样本 68 件 | log/2026-11-14 t160 |

---

## 2. 增量架构三问

### 2.1 单文档增量 parse/reuse 边界（AST 复用策略）

**现状**：lexer 流式契约已具（N1），但解析入口仍是"整文件 tokenize → 整文件 parse"（parser/ParseResult.kt 三入口）；AST 节点零语义（S-1 §2），位置模型归属 diag（ADR-4）。

**难点（决定边界，不可回避）**：邻接消歧 D-1~D-21 全是 token 邻接判据（N3）——

- `>` / `is` 跨行即视为**下一结构起始**（D-2/D-4）：删一行可能改变上一行 token 的消歧结论；
- 紧贴 `<` 是后缀实例化、带空格 `<` 是比较中缀（D-1）：行内增删空格改变结构；
- 句点前后空格决定字段取用 vs 小数（D-3）。

⇒ **行级"只重分析改动行"是不安全的**；增量单元必须取**能证明邻接判据不变的最小区间**。

**提案（路线，非实现）**：**顶层声明粒度 + 同步栅栏**

| 层 | 策略 | 安全依据 | 风险 |
|---|---|---|---|
| 增量词法 | 从**首个变化行**重扫，至**稳定栅栏**（下一顶层关键字起始符 / EOF）停；前缀 token 流整段复用（含 hadSpace 位） | 词法单遍自左向右、最长匹配（L-1~L-12）；行/列绝对位置只随前缀长度平移 | 长串/多行反引号标识符（ADR-45 含裸换行）可跨行吞栅栏——须先证栅栏不被吞 |
| 增量语法 | **顶层声明级复用**：首尾 token 栅栏（首 token kind + 末 token 位置）未变的兄弟声明整树复用；仅重 parse 受影响声明 | 顶层边界由入口关键字/分号/闭合括号定位（errDeclSync 同款判据）；AST 零语义 ⇒ 复用无语义状态 | 旧工程无此机制（全量）；**错误复用 = 静默错诊断**，必须差分证明（下） |
| 失效判据 | (a) 前缀长度变化 ⇒ 后续 token 位置全部平移（复用树须重写位置或改用相对锚）；(b) 栅栏内 token 序列变化 ⇒ 该声明重 parse；(c) 脚本模式顶层语句（非声明）栅栏规则未定（OQ-1） | —— | 位置重写的成本本身需度量（OQ-2） |

**正确性证明义务（纪律接法）**：增量路径的验收 = **全量复跑 vs 增量产物逐字比对**（同 t160 gatecmp 纪律，N14）：68 e2e 样本 + 人工扰动序列（增/删/改/换行/动空格）双路对拍，任何差异即增量 bug，按差分台账登记归因；**负例先红**——先构造已知长文档 + 中部编辑用例，证明朴素行级复用会错、栅栏方案不会。

**诚实边界**：本路线未做原型验证（本件零码）。V1 是否上增量 parse 取决于 P-L4（§10）原型结果；若原型证明栅栏方案复杂度失控，**保底路径 = 全量 parse + 语义层增量**（parse 本身相对语义是廉价层，见 §4.4 度量计划）——这与"增量硬约束"不冲突：硬约束的对象是**框架架构**（不得把全量重建当默认设计），而非强制每个层都增量。

### 2.2 语义层增量粒度（HIR re-elaborate 是整工程还是受影响闭包）

**现状**：语义分析 = **整编译单元内 R 轮不动点**（N4）：跨轮共享单调 nid 源、每轮新 Elaborator、解传递经轮外 Map 注入；推导是**跨函数联动的不动点**（R-02 按空位局部退化、R-06 提升需求跨轮传递）；证明检查在 HIR 构造中完成（S-1 §3）。

**三级粒度方案（提案，供队长选级）**：

| 级 | 粒度 | 内容 | 依赖 | 我的评估 |
|---|---|---|---|---|
| **G0** | 整单元重跑 | 单元（单文件 / 模块体）级完整重 elaborate，驱动器逐字不变 | 现状 | **V1 保底**：纯函数契约（N6）已保证可缓存（输入哈希 → 产物），零语义层改动 |
| **G1** | 声明级结果缓存 | 缓存 key = AST 子树哈希 + 符号环境版本号；轮次驱动器不变，仅轮内对未变声明跳过精化 | 需符号环境版本机制（遮蔽序、扩展桶状态） | **V2 候选**：不动驱动器结构， nod 分配单调性（H1）不受威胁 |
| **G2** | 受影响闭包 | 声明依赖图（名称引用 → 声明）+ 受影响闭包重 elaborate | 需跨函数推导依赖图 + 提升需求传播图（t125 分类账 T1/T2/T3） | **不建议 V1/V2**：R-02/R-06 的不动点语义使"受影响闭包"在轮次维度上非局部——闭包算错会静默漏诊（违负例先红精神）；需先有 G1 的度量数据支撑（OQ-3） |

**跨模块边界**：模块态下 mounted snapshots 对本单元是**只读输入**（N6/N11）——跨模块增量 = 快照失效传播：挂载 DAG 上游模块产物变化 → 下游单元缓存按拓扑序失效。工程内 `ModuleSnapshot` 当前是 internal 类型（N6），LSP 若跨模块缓存须先评估可见性（OQ-4，§10 P-L2）。

**与硬约束的接法**：G0 看似"全量"，但它是**单元级**而非**工程级**全量——旧工程的病在于"每击键 = 该文档全量"叠加"stdlib 全量重挂"（§0.1）。新工程 V1 = G0 + 输入哈希缓存 + 快路径门（零元变量单趟直通，N4 已存在）即已消除旧式无脑重跑；G1/G2 是后续增量深化。**这是"增量框架"的第一性兑现：缓存与失效是框架内置结构，而不是事后优化。**

### 2.3 workspace 模型（挂载态 .settings 的增量感知）

**现状**：.settings 是目录级配置（N11），ModuleTree 解析前建成；文件级错误落 E-MOUNT-SYNTAX 于 .settings 文件位（不污染源码诊断面）。

**提案**：

1. **文件监听面**：.settings / .subl 增删改 / 目录结构变化三类事件（不含任何源码内容解析）。
2. **差分传播**：ModuleTree 差分 → 挂载 DAG 受影响子树标记 → 按 §2.2 快照失效传播重算。
3. **诊断归属**：模块态诊断的 file 字段（N10/N12）→ publishDiagnostics 须按件拆分；**行号反映射**：pos 是模块拼接连续行，拆件需按件起始行偏移回退（attributeFiles 的逆映射，OQ-5）。
4. **编译根口径**：LSP workspace root 与 CLI 编译根（R3-S7：`..` 逃逸出根报 E-MOUNT-OUT-OF-ROOT）须同一判据——workspace 之外的挂载在 LSP 侧如何处置（拒收 / 仅诊断不参与解析）列 OQ-6。

---

## 3. 协议范围分期

对齐旧 LSP 已验证面（N16）与新工程分期纪律（JS 后端提案 §8 同款 V1→V3 结构）：

| 期 | 能力 | 增量底座依赖 | 验收形态 |
|---|---|---|---|
| **V1** | diagnostics（推送）+ hover（类型/签名）+ outline（documentSymbol） | G0 + 输入哈希缓存（§2.2）；severity 映射（§7） | 68 样本诊断死冻锚（§7.3）；全量 vs 增量逐字差分；hover 探针（类型/证明位/命题位分家） |
| **V2** | completion（关键字/已声明符号/prelude）+ definition（nid 反引，N10）+ **重命名**（旧 LSP 无，新面） | 符号索引（HIR 符号引用 id 权威，S-5 §3）；跨模块限定引用解析（R3-S7 §2） | 跳转完备性探针（声明/引用/模块符号/构造子四类）；重命名 = 预览 + 受影响位清单（差分台账登记） |
| **V3+** | code actions（快速修正） | R-26.2 系 fix 文案基础（N19）+ 旧 DIAG_DOCS 先例（N18） | 每码 fix 预期形态负例（先红：无 fix 文案的码不得给 action） |

**协议契约**（V1 即定）：

- **LSP 3.17 子集、零第三方依赖、stdio JSON-RPC**（沿用旧工程先例 N16；帧上限同款（旧 LspServer MAX_HEADER/MAX_BODY））。
- **增量推送**：publishDiagnostics 只推变化的 URI 集合（per-URI 全量替换是该协议要求，但**跨 URI** 只推送 invalidated 集合）。
- **textDocument/didChange** 增量同步（range 形式）必须支持——这是增量框架的协议侧前提；内部统一重构成完整文本后走 §2.1 栅栏。
- **初始化**：workspace root → ModuleTree 建图（§2.3）；initializeOptions 预留（旧工程有 extensions 插件通道，新工程是否移植列 OQ-7）。

---

## 4. 消费面选型评估

### 4.1 候选

| 方案 | 形态 | 先例 |
|---|---|---|
| **A · HIR JSON v3 批量复跑** | LSP 侧（或独立进程）调 CLI 外进程 spawn 编译，消费 envelope JSON | jsoncmp 纪律（N14）；JS 后端已定 C 路"读 HIR JSON v3 唯一输入" |
| **B · Kotlin 内进程 API** | LSP 服务进程内直接调 `compileFrontend` 族，持 HIR/DiagBag 对象 | 旧 LSP 内嵌 Checker（LspAnalysis :349） |

### 4.2 三维权衡

| 维度 | A · JSON 复跑 | B · 内进程 API |
|---|---|---|
| **性能** | 差：每变更一次 JVM spawn + 全量 JSON 序列化/反序列化（jsoncmp 实测 spawn 模型；管道台账 D-2 约束 ≤20 spawn/次，正是 spawn 不可靠之证） | 好：零序列化、零进程边界；可直接复用流式词法与半成品树 |
| **复杂度** | 低（契约即边界，HirSchema.check 守门即验收） | 中（LSP 须依赖 :frontend；编译器异常可击穿服务进程——旧工程以 try/catch 合成 E-CHECK 兜底，LspAnalysis analyze() 异常分支可证） |
| **正确性** | 高（冻结契约 v3 + 守门 + 差分台账，消费面与编译面物理隔离） | 中（同进程共享对象图 = 编译器 bug 直接变 LSP bug；但**符号图直接可用**，hover/跳转信息无损） |
| 契约保护 | 强（schema gate 负例已冻结，T158SchemaGateTest） | 弱（内部 API 无版本守门；internal 类型可见性问题 N6/OQ-4） |

### 4.3 提案立场（供裁）

**混合：B 为主干 + A 为契约面**——

- **运行路径用 B**：性能是 LSP 的第一约束（硬约束=增量即为此），hover/跳转/补全需要符号图（A 方案须从 JSON 重建索引，复杂度反超）。
- **验收/回归面用 A**：LSP 测试台**复用 jsoncmp 全套纪律**（N14）——诊断死冻锚、全量 vs 增量差分、双导出确定性、守门负例。即：正确性保护不靠运行时物理隔离，靠**冻结契约 + 差分台账**（这正是本队已机械化的纪律，零新发明）。
- 由此，LSP 与 JS 后端共享同一契约面（HIR JSON v3），**前后端一致性对拍成为现成工具**。

### 4.4 单文档快速诊断快路径：前端件需求评估

**结论：需要，列为前置件 P-L1。**

依据（现状缺口，逐条点查）：

1. `compileFrontend` **无条件发射 JSON 文本**（cli/Frontend.kt：`HirJson.emit` 是返回三件套的固定一环）——诊断快路径不需要 JSON 串，发射是纯开销。
2. **stdlib 默认挂载在 CLI 层而非公共 API**：公共 5 参数 `compileFrontend` 的 mounted 默认空（N6），t137（R-23）的 stdlib 默认挂载注入（含 `--no-stdlib` 门）实现在 CLI 入口（cli/Main.kt、ModuleFrontend.kt：stdlib 件编为只读快照经 mounted 注入语义层）。LSP 内进程嵌入**必须复刻该挂载装配**，否则裸名 `sum`/`range` 一族落 E-UNBOUND-NAME（旧 LSP 以拼接 core 目录解决同题，旧 LspAnalysis analyze() 可证）——这是消费面选型 B 的隐藏复杂度，计入 P-L1/P-L2。
3. 半成品原则（N6）保证诊断面与 HIR 树同产出——**快路径不得破坏半成品契约**（ERROR 也返完整树）：前置件的验收门 = 产物三件套行为零变化（套件 + parity 全量比对）。

**P-L1 内容（另立任务书）**：`compileFrontend` 的 internal 诊断快路径变体（免 JSON 发射；可选 mounted 快照旁路），**零公开契约变化**，守门 = 全量套件只增不减 + 五段 parity 零波动。

### 4.5 度量计划（补 §0.1 诚实边界）

V1 交付必须附带**实测延迟台账**（缺此不得宣称增量成功）：

- 单文档诊断时延：全量 G0 vs 增量路径（parse 栅栏开/关两态），样本 = 68 e2e（最长件 + 平均件）；
- 多文档场景：N 份并发变更文档的诊断总时长（单线程 vs 受限并行，§5）；
- 度量结果入差分台账同目录（jsoncmp/out-subs/ 或新 lsp-bench/），供 V2 分级决策（G1/G2，OQ-3）。

---

## 5. 并发评估（提案层面论证，不盲目上）

### 5.1 可并行（架构允许，收益待度量）

| 环节 | 依据 | 风险 |
|---|---|---|
| **多文档诊断流水线** | 单元间纯函数（N6）、单元间无共享可变状态（符号表为单元内帧链，N11） | 共享只读快照的并发读安全；结果**按 URI 汇聚保序** |
| **模块体内多文件 parse** | 文件间词法/语法独立（模块体按名序拼接属语义层口径，N12） | 拼接行号映射须串行归并 |
| **索引构建 / JSON 差分比对** | 派生数据，无编译状态 | 内存峰值（须设上限，OQ-8） |

### 5.2 必须串行（不可让步）

| 环节 | 依据 |
|---|---|
| **单元内 R 轮驱动器** | 跨轮共享单调 NodeIdSource（H1，ADR-14 nid 单一源）+ 轮次确定性（P-11 §9.3）——并行轮 = 同 id 异节点（F1 病）+ 确定性丧失 |
| **提升需求单轮预应用重跑**（R-06） | t125：跨轮唯一通道（声明位键→赋值位证据清单），乱序 = 提升结果不确定 |
| **JSON-RPC 连接内消息序** | didChange 有序性是协议前提；DiagBag 落袋保序（N10） |
| **共享缓存失效传播** | §2.3 挂载 DAG 拓扑失效须原子完成（半失效态读取 = 陈旧诊断） |

### 5.3 提案立场

**V1 单线程**（旧工程先例 N16 + 本队 S-3 §8.3 构建槽位互斥同源：多线程共享树的教训已经在本队发生过两次——t43/t53 双 daemon 覆盖测试结果）。并发列为 **V2 度量驱动决策**：先有 §4.5 多文档实测数据，再决定是否引入"受限并行"（固定大小文档级线程池 + 单元内严格串行）。**引入并发的门槛 = 度量数据证明单线程不满足交互延迟 + 并行方案通过轮次确定性负例**。不满足则继续单线程——"宁可慢"（用户原话方向同样适用于并发）。

---

## 6. R-06 依赖（正式上线门）

### 6.1 断裂根因（既有台账，非本提案推断）

- **现状三实证**（t163 任务书）：工程模式显式 `@mut var v: Box = Box(0); v = Box(1)` 绿；脚本 `var x = 1; x = x+1` **红**；脚本显式 `@mut var x = 1; x = x+1` 绿。
- **机理**：脚本自动提升后槽位被合成 `@mut Nat` 视图，写回 RHS plain Nat 被 R-28 矩阵按 PLAIN→MUT 值位转换拒（E-TYPE-MISMATCH）；T125MutPromotion 4 件在 t150 被翻转吸收（t150 批史冲突点 1）。
- **t163（R-31 裁决）**：提升改走声明位通道（类型保持 plain T、写回=属主写不入矩阵），任务 **pending**（本提案写作时 team.json 状态）。

### 6.2 对 LSP 的具体含义（写清门条款）

1. **脚本文件诊断在 t163 修前 = 满屏红**：脚本态最常见形态（`var x = 1; x = x+1` 一类提升写回）会被误报 E-TYPE-MISMATCH。若此时上线 LSP 诊断面，用户看到的是**系统性误报**——这是产品质量事故级风险，不是"功能不全"。
2. **正式上线门（提案条款）**：LSP **V1 诊断面不得在 t163 收尾且队长验收通过前发布**（脚本模式）；V1 开发与测试可照常进行——验收语料用 project 模式 + 冻结样本（脚本门 14 件与 e2e 68 门中受 t163 影响的诊断项，按差分台账逐项归因后才能定为 LSP 期望面，不得预设其绿）。
3. **同源先例**：t162 JS 后端以"输入门只收 ok=true"规避同类问题（log/2026-11-14）；LSP **没有 ok 门可用**（诊断本身就是产品），故只能以 t163 为硬依赖。
4. **范围外**：CoW、精确逃逸、codegen 与 LSP 无关部分不动。

---

## 7. 与既有纪律的接法

### 7.1 131 码 → LSP severity 映射（提案表，OQ-9 待裁）

| 本工程级别 | 码族 | 实测计数 | LSP DiagnosticSeverity | 依据/备注 |
|---|---|---|---|---|
| ERROR | E | 76 | **1 · Error** | N-SEV-BLOCK：唯一阻断级 → ok=false |
| WARN | W | 10 | **2 · Warning** | 直译 |
| HINT | H | 11 | **4 · Hint** | 直译 |
| SUPPLEMENT | D | 34 | **3 · Information**（旧工程先例 N17） | **OQ-9**：Information 会在客户端显示为 Informative 波浪线；本工程 CLI 默认隐藏 supplement（N10，-v 打开）——LSP 侧对应策略（默认隐藏可配 / 相关信息位降级 / tags）**不代裁** |

映射守门：131 码 × 期望 severity 的**期望表**作为 V1 死冻锚（与 68 门台账同构）；新增诊断码（HIR-JSON诊断段契约纪律一：新增码改变样本 JSON）必须同步更新期望表，否则 LSP 诊断面视为**未对齐**。

### 7.2 结构化字段映射

| 本工程 | LSP | 备注 |
|---|---|---|
| SourceRange（l:c-l:c，1-based） | Range（0-based） | 转换纯函数；单点 end==null 取单点 |
| Diag.code | Diagnostic.code | 131 码原样（source="sublimation"） |
| Diag.message | Diagnostic.message | **ident 反引号格式保持**（D2/t69：标识符一律反引号，含转义） |
| Diag.nid | relatedInformation / hover 锚 | nid 反引 HIR 节点身份（D-9）——跳转与 code action 的节点级回链 |
| Diag.file（模块态） | publishDiagnostics 按件拆分 | 行号连续累加口径的逆映射（OQ-5） |
| modulePath | （客户端 workspace 呈现） | 模块归属上下文 |
| HIR notes（desugar/hint/supplement） | hover 内容来源 | from→to 回溯源写法（S-5 §4，消费方= LSP 明示） |
| W-UNUSED 类 | Diagnostic.tags（Unnecessary） | OQ-10：是否启用 tags |

### 7.3 差分台账复用（验收面）

| 既有纪律 | LSP 对接 |
|---|---|
| t122n 死冻锚 + 68 门台账 | 诊断死冻锚：68 样本 diagnostics 段（severity 分布基线 N8）→ LSP 推送集合逐字比对 |
| 精确差分 + authorized 归因 | 全量复跑 vs 增量产物差分（**增量正确性证明**，§2.1）；诊断码增删 = authorized 差须登记 |
| 双导出确定性 | LSP 台账确定性门（同输入两次会话逐字同） |
| 负例先红 | 增量失效判据先红（§2.1）；severity 映射错配先红 |
| 管道台账 D-1~D-6 | LSP 测试台直接适用：导出 COUNT 核对（D-1）、spawn ≤20/次（D-2）、XML 例数对账（D-3）、树被改时验证窗口（D-5）、废止注关键词命中（D-6） |

### 7.4 模块与规范落点（S-2 接法）

S-2 §1 当前载「解释器 / LSP / 注解处理器 | ⏸ 暂缓（D-6）」——**LSP 正式开工必须先修订 S-2**（新模块（如 :language-service）或 :frontend 内 lsp 子包的取舍列 OQ-11），并遵守：依赖方向只可前进（S-2 §3.1）、零第三方运行时依赖（S-2 §3.3）、diag 横切零依赖。**本提案不预设该结构裁决。**

---

## 8. 开放问题清单（不代裁）

| OQ | 问题 | 我的倾向（供裁，非决） |
|---|---|---|
| OQ-1 | 脚本模式顶层语句（非声明）的增量栅栏规则 | 同款"入口关键字 + 分号"栅栏；字符串插值/反引号跨行需先证 |
| OQ-2 | 复用 AST 的位置重写成本 vs 位置相对锚 | 先度量（§4.5），成本超阈则保底全量 parse |
| OQ-3 | 语义增量分级时机（G1/G2） | 度量驱动；G2 在 R-06/退化机理稳定前不上 |
| OQ-4 | ModuleSnapshot internal 可见性对跨模块缓存的影响 | 快照对外暴露需独立任务；V1 限单文件 + 模块直编 |
| OQ-5 | 模块态诊断按件拆分的行号反映射机制 | attributeFiles 逆映射；件起始行偏移表在 ModuleTree 差分时建 |
| OQ-6 | workspace 之外的挂载在 LSP 侧的处置 | 同 CLI：E-MOUNT-OUT-OF-ROOT 诊断化，不参与解析 |
| OQ-7 | 旧工程扩展点/插件框架（LspExtension/LspPlugin）是否移植 | V1 不移植（无宿主需求）；保留通道研究 |
| OQ-8 | 并行诊断的内存上限策略 | 固定文档级池 + LRU 失效；V2 决策 |
| OQ-9 | SUPPLEMENT → Information vs 降级/隐藏 | 倾向映射 Information + 客户端可配，由用户裁 |
| OQ-10 | Diagnostic.tags 启用范围 | 仅 W-UNUSED 类，随 V2 |
| OQ-11 | LSP 模块结构（新 Gradle 模块 vs frontend 子包） | 倾向新模块（依赖隔离清晰），队长裁 |
| OQ-12 | didChange 增量范围编辑的内部归并窗口（debounce） | 交互体验项，V1 度量时定 |

---

## 9. 诚实边界与未验证项

1. **无旧 LSP 延迟实测**（§0.1）：本提案"旧工程慢"= 用户报告 + 结构论证；增量收益数字须 §4.5 实测。
2. **增量 parse/AST 复用未做原型**（§2.1）：邻接消歧判据使朴素行级复用不安全；栅栏方案是提案，正确性待 P-L4 差分证明。
3. **语义层 G1/G2 未存在**（§2.2）：不动点轮次使受影响闭包在轮次维度非局部；本提案不建议 V1 触碰。
4. **本件零代码**：所有"实测"数字 = 既有产品/文档点查（取证见 §11），非新跑基准；无任何新增构建/测试证据。
5. **R-06 断裂状态会变化**：§6 基于 t163 pending 态；t163 落地后本提案 §6 门条款须按终态复核（履约式更新，列 §10）。
6. **不代裁**：§8 全部 OQ、§4.3 消费面立场、§7.1 severity 映射均为提案倾向，裁决权在队长/用户。

---

## 10. 前置件与里程碑（供队长排期参考，非任务书）

| 编号 | 件 | 内容 | 依赖 |
|---|---|---|---|
| P-L0 | 纪律件（**已落簿**） | 增量硬约束 + 旧工程全量教训落条款（S-3 §2.1 对话失传防护）：ADR-65 + specification/README.md 已决事项 D-13（t165 执行，队长验收中） | 本提案批准后 |
| P-L1a | 前端件（t165 执行） | **stdlib 默认挂载通道公共化**（§4.4 依据 2 的缺口）：公共入口 compileFrontendWithStdlib（旗标形态——ModuleSnapshot 为 internal；CLI 单文件层复用同一通道不重复装配）；守门 = 套件只增不减 + parity 零波动（待重跑核实） | 无 |
| P-L1b | 前端件 | compileFrontend 诊断快路径（免 JSON 发射，零公开契约变化，套件 + parity 守门）——§4.4 依据 1/3，**另立任务书** | P-L1a |
| P-L2 | 模块件 | 文件监听 + ModuleTree 差分（.settings 增量感知）+ 行号反映射（OQ-5） | P-L1 |
| P-L3 | 规范件 | S-2 修订（LSP 模块入表，OQ-11 裁决） | 队长 |
| P-L4 | 评估件 | 增量 parse 栅栏原型 + 全量/增量差分正确性证明（负例先红） | P-L1 |
| **门** | —— | **t163（R-06×R-28/R-31）收尾 + 队长验收通过 → V1 诊断面（脚本）可发布** | t163 |
| V1 | —— | diagnostics + hover + outline（G0 + 哈希缓存 + §7 映射 + 度量台账 §4.5） | P-L1~P-L4 + 门 |
| V2 | —— | completion + definition + 重命名；并发度量决策（§5.3） | V1 度量数据 |
| V3+ | —— | code actions（R-26.2 fix 文案基础 N19 + DIAG_DOCS 先例 N18） | V2 |

---

## 11. 引用与出处（数字溯源）

**本工程**（F:\Sublimation）：

- specification/S-1-编译器架构.md（§1 流式契约、§2 容错、§3 HIR 信息保留、层间总契约 1/2/3）
- specification/S-2-模块划分与依赖规则.md（§1 暂缓表、§3 依赖硬规则）
- specification/S-3-流程与角色分工.md（§2.1 任务书条款纪律、§7 测试台账、§8.3 构建槽位）
- specification/HIR-JSON-Schema-v3.md（S-5：§0 冻结/121 envelope、§1 envelope 结构、§3 94 kind、§4 notes、§5 诊断段、§7 守门）
- frontend/src/main/kotlin/sublimation/frontend/diag/Diag.kt（Severity 四族、N-SEV-BLOCK、DiagBag、t155 file 字段）
- frontend/src/main/kotlin/sublimation/frontend/cli/Frontend.kt（compileFrontend 契约、半成品原则、mounted 重载）
- frontend/src/main/kotlin/sublimation/frontend/semantic/Semantic.kt（:27 INFER_ROUNDS_DEFAULT=8、:93 H3、:108 单趟直通、:209-223 提升单轮重跑）
- frontend/src/main/kotlin/sublimation/frontend/semantic/TypeInfer.kt（:194 FUEL_DEFAULT=237500）
- frontend/src/main/kotlin/sublimation/frontend/parser/ParseResult.kt（三入口、ParseResult 三件套）
- frontend/src/main/kotlin/sublimation/frontend/lexer/Lexer.kt（流式 Sequence 契约）
- documents/idea documents/ADR-7-语法分析与容错模型.md（§2.2 容错恢复、§2.3 D-1~D-21 邻接判据）
- documents/R3-S7-模块系统设计-提案.md（F1-F12、§1 挂载机制、§2 限定引用、§3 可见性）
- documents/HIR-JSON诊断段契约.md（68 样本 severity 分布：error 10/supplement 59/hint 3/warn 2）
- documents/stdlib-设计-提案.md（§20 R-26.2 注解纪律 t149 批史；§22 R-28 与 R-06 冲突点 1、T125MutPromotion 4 件翻转）
- documents/工具与管道缺陷台账.md（D-1~D-6）
- documents/log/2026-11-14.md（t158 冻结/121 envelope、t160 套件 1304/1304、t161 JS 后端提案）
- jsoncmp/scripts/t160-parity.ps1（五段 parity 纪律、t122n 死冻锚）
- 131 码计数：grep frontend/src/main 去重（E 76 / W 10 / H 11 / D 34；任务书同数）
- t163 任务状态：.agent-teams/frontend-refactor/team.json（pending）

**旧工程**（F:\KotlinProject\sublimation，字典点查）：

- language-service/.../LspAnalysis.kt（3137 行；:18 全量策略、:349 analyze、:494-506 diagnosticsFor、:2894 DIAG_DOCS）
- language-service/.../LspServer.kt（811 行；协议面 8 方法、单线程 stdio、帧上限）
- language-service/.../LspProtocol.kt（LSP 3.17 子集；:196 severity 映射）
- language-service/.../LspExtension.kt / LspPlugin.kt（扩展点/插件框架，线程模型注释）

**任务书**：t164（用户硬约束转述、六块范围、验收标准）
