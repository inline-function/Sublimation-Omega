# ADR-13 命题与证明检查 V1 裁决（t9）

- 状态：已落地（实现 + 测试通过）
- 日期：2026-11-05
- 范围：t9「实现命题与证明检查 V1」——在 t7 骨架/t8 类型推导之上，按 ADR-6 §四 4.2/4.3/4.4 落地
  证明检查纯函数插入件 ProofChecker 与 Elaborator 侧义务接线
- 对齐依据：P-7 全对齐旧 checker/Prop.kt（PropLogic+Prover 可判定主干）、ArithProve.kt 整体、
> 【2026-11-09 对账标注（t91）】`∃`/`∃!` 已为关键词（裁决 2）；定理库与归纳法暂缓（裁决 6，待讨论）。

  Checker.kt 证明义务位与消息文案、Diffusion.kt proveOrDeep 通道顺序（字典点查；旧实现仅作口径
  参照，不做全部 36+ checker 码）

## 一、背景与约束

ADR-6 §四 四维解耦在 t8 完成类型维（ADR-12），t9 完成证明维：
- 4.2 四件套之 Prop.kt → **PropNormalizer + 命题代数归 ProofChecker**；ArithProve.kt → **ProofChecker
  算术通道**；Diffusion → **Elaborator 帧管理**（调用后把下文命题并入当前帧事实集）。
- 4.3 **Elaborator 单驱动**：ProofChecker 纯函数（节点/命题 + 上下文 → 结论 + 诊断），零自走树、
  零 throw（诊断单向落 DiagBag）。
- 4.4 **帧栈 + Γ 快照**：旧全局 allFacts 可变池废除——facts 挂 ArrayDeque<MutableSet<HirProp>> 帧栈
  （函数帧/命题分支帧），义务核对时刻 snapshot() 归一化并集入 proofContext.facts；证明搜索过程状态
  （闭包、visited、实例化矩阵）按 ADR-1 §3.8 丢弃，不入树。
- R-NO-GUESS：Γ ⊬ P 绝不降级为成立，FAILED 恒对应一条 ERROR 诊断。

t7 已落地 P-STRUCT-EQ 显式 equals/hashCode（忽略 nid/origin/notes）——事实集直用 Set<HirProp>，
旧 termHash/propHash/blindPos/propStructEq 三件套免移植。

## 二、裁决

### 2.1 命题代数（旧 PropLogic 可判定主干迁移）

render/termTag/normalizeFacts（expandIff→flattenAnd→stripNN）/groundTerms（后序收集复合项）/
substTerm/substProp/matchTerm（合一单向特化，V1 未用于定理空间故仅作内部能力）/substDollar/
constAtomValue（in 区间与比较族常量真值，**先判 in 再取左操作数**——旧同名函数同序）。

### 2.2 证明通道（proveOrDeep 的 V1 子集，命中即短路）

proves(facts, goal) 通道顺序（P27 对齐）：
1. **R-CONST**：PNot/PImp/PAnd 常量拆解 + constAtomValue；
2. **A-LINARITH**：ArithProve 整体移植（Lin 常量折叠、单变量绝对界 floorDiv/ceilDiv、±1 差分图
   Bellman–Ford 负环、等式链定义消元 buildDefs 32 轮、in 区间双界、≠ 目标界排除（neLimit=2 案拆）、
   POr 区间补集 disjRefute、= 快照回滚拆双界防半注入）；
3. **R-SUBST**：substBfs 双上限（常 40/100、策略档 400/400）项级深替 + ↔ 命题级替 BFS；
4. **R-MP/R-MT/R-CONTRA**：closure 普通模式饱和（∀ 单名/多名实例化矩阵、MP、MT、矛盾→⊥、收割生长
   256 封顶）+ prove 目标导向分解（深度 4）+ eqReachable 等式图 BFS；deriveRule 由闭包命中形态回推
   规则编号（⊥ 爆炸 → R-CONTRA；imp(目标前件→b) ∧ ¬b ∈ cl → R-MT；其余 → R-MP）。

### 2.3 义务核对（结论上树三件套）

axiom()（R-AXIOM 免证）/because()（前提逐条证 → Γ+前提证结论；E-PREMISE-UNPROVEN/E-CANNOT-PROVE/
D-BTS）/byStmt()（**E-BY-REMOVED**：语句式免验证注入废除，by 只保留委托语义 + 迁移指引文案）/
byAsserts()（后缀式断言逐条核对，**不注入 Γ**——能证出者本就可由 Γ 推出）/strategy()（simp→
A-LINARITH 常量折叠 + 算术 neLimit=4；subst→满档代换；E-STRATEGY-FAILED/D-STRATEGY）/whenProp()
（W-PROP-UNBOUND/W-WHEN-NO-COMMON/D-WHEN-PROP；各支 BTS 结论交注入块外）/ifProp()（E-NEED-CLASSIC
未挂 @classic 旗标；E-IF-CONCLUSION-MISMATCH；排中律两支新事实取结构相等交）/existsIntro()
（见证代入后可证 → R-EXISTS-INTRO + 注入 ∃ 命题 + D-EXIST-INTRO；E-QUANT-UNPROVEN）/existsElim()
（渲染同形 ∃ 匹配 → R-EXISTS-MATCH；体子 P(x) 注入块帧 + 块外注入体 BTS 结论；E-PROP-UNBOUND）/
localFun()（有体 → 定义即证明 D-LOCAL-FUN；无体 → E-QUANT-UNPROVEN ∀ 引入要求）/postcondition()
（$ 代换为体尾表达式（dollarTermOf：块体取唯一 return 或尾表达式）后由函数帧 Γ 证；@axiom 免证
D-AXIOM-POST；E-POST-UNPROVEN）。

### 2.4 Elaborator 帧管理接线

- prover = ProofChecker(::freshNid, diag) 插入件；factFrames 帧栈 + allFacts()/injectFact()/factFrame{}；
- file() 包全局事实帧并扫 classicMounted（任一声明 @classic 注解）；**顶层 by 断言在文件事实帧内 flush**；
- funDecl 压函数帧：上 文 preps 注入帧、体尾 flushByAsserts（函数体/lambda 体两落点）、下文核对；
- when[p] 各支压帧注入析取支；if[p] 两支各注入 p / ¬p 后取交；∃ 消去把 x 先入块帧再命题化 P(x)
  （P 的同名引用可解析）；∂ 消去的块子 P(x) 注入块帧。
- classicMounted 的判定是文件级旗标（任一声明），非逐语句——与旧实现口径一致。

## 三、V1 推迟清单（后续轮次）

- **定理空间搜索 searchTheorems**（@theorem 匹配、HirProofContext.matched 位暂留 null）；
- **激进会话**（provesByContradiction 的三维池/不动点/迭代加深/∨ 消去/反证法分支）；
- **Diffusion 调用点下文注入**（函数调用后把 posts 并入调用方帧——V1 仅声明位登记 preps）；
- **expandDefined**（PropDefDecl 自定义命题展开）；
- **类型判定 (e : T) 与 Real 代数通道**（旧 typeJudgement/reduceGoalToConstants）；
  → **2026-11-08 补注**：`(e : T)` 已被定为 `P-12-证明驱动的类型精化` 的**使能通道**（用户裁决：本语言内置证明器，可智能转换函数类型）。故此项**不再是可选项**，而是 P-12 第二级的硬前置；`reduceGoalToConstants` 对应 P-12 第一级（符号归约）。
- **∃! 唯一性**（PExists1 prove 恒 false，排至 V2）；
- **enumGroundMembership**（容器枚举归属）；
- matchTerm 定理空间合一能力（§2.1）。
- **归纳法（数学归纳 / 结构归纳）——队长 2026-11-07 22:50 补登**：原推迟清单**漏列此项**，而实现（`ProofChecker`）中也**零处**涉及归纳。
  现状：只能证明**可判定片段**内的事实；关于递归/归纳数据的全称命题（如 `forall n: Peano. P(n)`）**无法自动证明**，用户只能把归纳原理作为 `axiom` 手工喂入。
  **为何现在必须登记**：P-9 已把**归纳类型别名**（`type Peano = Zero | Succ[Peano]`）列为核心特性，类型系统能**表达**递归，而证明器**不能推理**递归——这是一处结构性不对等。
  **建议**：P-10 收口后排入 P-11 首要项（结构归纳：按其构造子分解目标 → 各分支引入归纳假设 → 递归证）。

## 四、已知偏差与裁决

1. **HirByExpr 的 verdict 位留 null**：by 断言在包围帧尾 flush（名字绑定与事实注入就绪后才可核对），
   verdict 时刻晚于节点构造；结论由诊断承载（E-CANNOT-PROVE），节点只留 proofContext 快照。
   旧 ByStmt 无此位——V1 不新增字段（ADR-1 §3.4 契约），显式裁决为时间窗错位。
2. **∂ 引入见证项不可命题化**（termQuiet 返回 null：仅字面量/名字/运算/调用/实例化/字段可作项）：
   落 E-QUANT-UNPROVEN 形态 FAILED（不冒充成立）。
3. **posts 半成品占位跳过**：E-PROP-FORM 的 PBot 占位不再触发 E-POST-UNPROVEN（不重复报）。
4. **诊断落点时序**：∃ 消去的 E-PROP-UNBOUND 在体走完后核对（Γ 为外层快照）——诊断顺序与源序
   略异，码与位置不变。
5. ** destined for 后端复用**：HirProofContext.facts 是核对时刻快照，非运行时环境——后端应据
   帧栈语义自行重建（ADR-1 §3.8）。

## 五、验收

- :frontend:compileKotlin 绿（零错误，一警告无）；
- :frontend:test **286 tests 0 failures**（新增 ProofCheckerTest 49：代数 6 + 闭包 4 + proves 通道 11 +
  义务核对 12 + 全管线 16；回归 237 全绿——含 t8 TypeInferTest 36、SemanticTest 22，两处 t7 测试
  因新行为（posts 核对）更新预期）；
- 交付物：frontend/src/main/kotlin/sublimation/frontend/semantic/ProofChecker.kt（约 1100 行）、
  Elaborator.kt 义务接线、PropNormalizer.termQuiet、
  frontend/src/test/.../semantic/ProofCheckerTest.kt、本裁决。
