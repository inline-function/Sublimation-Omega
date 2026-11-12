# stdlib 标准库设计提案（布局 + 默认挂载 + 首批迁源计划 · 形态 C）

> 状态：**设计提案 v0.1，零码改、零代裁**（队长 t132 立项令）。队长唯一裁决入口 = §7 OQ 清单 + §8 四分类。
> 缘起：标准库立项——机制面已锁（R-22：stdlib 根默认挂载、迁源形态 C、每批基线翻新；S7（t131 已落）：
> .settings 挂载、目录即模块、快照注入）。**本设计件定内容与批次，不是机制重设计**。
> 相关前件：documents/标准库规划.md（队长讨论稿，三层结构 + 公理首批清单）；R3-S7-模块系统设计-提案.md（§10 落地实录）。

---

## 0 现状盘点（设计的全部事实基础，全部实测）

| # | 事实 | 取证 |
|---|---|---|
| F1 | 编译器内嵌的「stdlib 类」内容有**四个 Kotlin 源**：①PreludeDecls.STRUCTS 11 虚拟 struct（None/Some/Nil/Cons/Ok/Err/SetNil/SetCons/MapNil/MapCons/Channel）+ ②ALIASES 5 联合别名（Optional/List/Result/Set/Map），经 structDecls/aliasDecls **预注入**（Elaborator:548-549）；③PreludeDecls.FUNS 2 虚拟构造（MutableListOf/ListOf，t126 R-15 容器面）；④PreludeDecls.EXT_METHODS 1 虚拟扩展方法 **add（无体）**——注释原文「V1 签名面：方法体随后端/标准库源（S2 迁源形态 C）落」 | PreludeDecls.kt:42/58/70/78;Elaborator.kt:548 |
| F2 | 另有两张 Kotlin 函数表：PRELUDE_FUNS 3 项（range/sum/summation——注记原文即「core/collection/list.subl 的 range/sum 与 core/math/math.subl 的 summation，跨模块自由函数」）+ BUILTIN_FUNS/builtinSig 60+ 内建函数（print/json*/str*/math*/channel*等） | SymbolTable.kt:559/566;TypeInfer.kt:790 |
| F3 | **e2e core/collection 7 样本 = 草稿级 stdlib 内容**：list（2 struct+1 alias+4 ext+约25 自由函数）/map/optional/result/set/traits/tuple，头注「依赖：本模块（...）；无跨包依赖」= **按多文件模块设计** | core/collection/*.subl 头注+清单 |
| F4 | **list.subl 冻红机制实测**：单文件编译 rc=1，`E-UNBOUND-NAME: 未定义的名称 getOrElse`（:81）/`add`（:221）/`put`（:227）——同名符号在同目录其他文件，**逐文件单独编译的伪红**（t122n 稳定冻红产物） | CLI 实跑 |
| F5 | **collection 目录编译实测**（t131 目录形态，零 .settings）：rc=1，**E-DUP-DECL 6+ 处**（size/isEmpty/get/filter/toList 跨文件同名）——自由函数按名唯一（ADR-26 禁重载），**7 文件不等于 1 模块**：同名自由函数须按模块分家 | CLI 实跑 |
| F6 | **S7 V1 限面（迁源排序的核心约束）**：ModuleSnapshot 快照**仅自由函数面**——跨模块类型别名/结构体/扩展函数限定通道未通（R3-S7 提案 §10 限制面） | t131 交付 |
| F7 | P-7 用户覆盖口径不变：虚拟 struct/alias 预注入后被用户 file 条目覆盖（符号查询链用户表在前；BUILTIN_TYPES 已 t47 移容器裸名） | PreludeDecls.kt:32-34 |
| F8 | range 声明于 list.subl:38（无体头声明）、sum 声明于 list.subl:182（带体）、summation 于 math.subl:45（= sum(range(a,b))）；**7 个 examples 样本 + core 若干**依赖此三名字（PRELUDE_FUNS 兜底） | grep 取证 |
| F9 | 标准库规划稿三层：kernel（类型+原生操作+内核公理）/ core（容器）/ axioms（命题）——**kernel 与 core 现都写在 Kotlin；公理库不存在；归纳法暂缓（t91 裁决 6）** | documents/标准库规划.md |
| F10 | 68 e2e = 32 core + 36 examples；core/collection 占 7；t131 后基线口径：t122n 不翻、模块系统走新侧 jsoncmp/out/t131mod/ | jsoncmp/manifest；README 分账 |
| F11 | t131 机制就绪面：目录即模块、显式挂载（.settings）、独立 Elaborator 实例 + 挂载侧只读快照（**自由函数面**）、限定引用语义层消歧、E-DUP-DECL 模块作用域化（跨模块同名合法） | t131 落地实录 |
| F12 | list 头注运行时形态：脚本 ListV 数组值 / 编译模式 codegen 特判（Nil=[] / Cons(h,t)=[h,...t]）——**List 的运行时是数组形态**：源码结构性体与 codegen 特判并存，构成 add 落体的硬约束 | list.subl 头注 |

---

## 1 stdlib 目录布局与模块树（设计范围 1）

```
stdlib/                          # 编译器仓库内的标准库源树（V1 提案布局）
|- .settings                     # 根默认挂载合成位（编译入口预置，见 §2）
|- collection/
|  |- .settings                  # 挂五件容器模块（显式 mount——R-22 拒隐式）
|  |- list/    list.subl         # Nil/Cons + type List + ext(map/fold/pure/flatMap) + free(size/isEmpty/head/contains/range/sum/...)
|  |- optional/ optional.subl    # Some/None + type Optional + ext(map/fold/pure/flatMap) + free(isSome/isNone/getOrElse/orElse/...)
|  |- result/  result.subl       # Ok/Err + type Result + ext + free(isOk/isErr/getOrElse/...)
|  |- set/     set.subl          # SetNil/SetCons + type Set + free(size/contains/insert/...)
|  `- map/     map.subl          # MapNil/MapCons + type Map + free(size/get/put/remove/keys/values/...)
`- math/      math.subl          # summation 等（依赖 collection/list 的 sum/range——经限定引用）
```

- **模块边界 = 目录**（目录即模块，S7 既有口径）。每个模块体 V1 为**单 .subl 文件**（与 e2e 样本同构、确定串行序零歧义；多文件模块后置 OQ-12）。
- **stdlib 内部互挂**：collection/.settings 挂五件；内有跨件依赖处（list 调 optional.getOrElse、map 调 list.values 等）在**依赖方 .settings 显式 mount 被依赖件**——限定引用是唯一跨模块通道（S7 V1 口径），即 `optional.getOrElse(o, d)` 形态。
- **可见性口径**：父到子传递闭包（S7 既有）；stdlib 模块对用户编译根的可见面 = 挂载快照的自由函数（F6 限面；类型/ext 面见 B1/B2 批次）。
- **命名空间形态**：stdlib 根默认别名 `stdlib`；模块名 = 目录名。V1 合格形态：`stdlib.list.sum(xs)`（根别名 → 模块名 → 函数）。

---

## 2 默认挂载接法（设计范围 2）

- **接法**：编译入口（cli 编排层）在 ModuleScanner 前为**编译根预置一条隐式挂载**——虚拟行 `mount <stdlib 根> as stdlib` 与用户 .settings 行**并列**（用户行在前；stdlib 挂载不可被用户行遮蔽，但**用户同名声明仍优先**——P-7 不变）。`--no-stdlib` 关（对齐 --no-degrade / --no-lift 开关族）。
- **顺序与遮蔽（R-22 查询链）**：本模块声明 > 挂载 stdlib 快照 > prelude 虚拟表。
  - 过渡期并存：prelude 虚拟四源（F1/F2）**保留作全局兜底**；stdlib 源与虚拟表同名同构时，用户侧解析路径行为不变 → 用户产品 SAME。
  - 用户同名声明仍按 P-7 覆盖（虚拟表与 stdlib 快照都在用户表之后）。
- **每批 retire 记账**：每批下电的 Kotlin 表条目（PRELUDE_FUNS → STRUCTS/ALIASES → EXT_METHODS/FUNS）登记到 jsoncmp/README 分账（R-22 OQ-8 每批基线翻新纪律）。
- **产品口径**（OQ-2，本文倾向）：stdlib envelopes **不随用户产品 emit**——走独立侧基线（jsoncmp/out/t132stdlib/）。理由：用户 67 逐批 SAME 是「stdlib 零侵入」的机器证明，翻新落在 stdlib 侧基线一次，不污染每批用户比对。

---

## 3 首批迁源计划（设计范围 3 · 核心件）

**硬约束**（F6）：快照仅自由函数面 ⇒ 能迁的只有「导出签名在父域可解析」的**自由函数**（签名引用的类型须为内建或 prelude 虚拟类型——**不能引用他模块的类型别名/struct**），且方法糖/ext 函数不跨模块。

| 批 | 内容 | 前置 | retire 的 Kotlin 面 | 用户 SAME 影响 | 侧基线事件 |
|---|---|---|---|---|---|
| **B0-0（极小批，可即刻）** | PRELUDE_FUNS 三项：stdlib/collection/list/ 落 range+sum；stdlib/math/ 落 summation | S7 快照（已就绪）；非限定查询链（OQ-1 队长裁的给否决定此批形态） | PRELUDE_FUNS 表 3 条 | **零差分**（签名逐字同 → 快照链无缝接管；7 依赖样本语义不变） | t132stdlib 首基线落盘 |
| **B0-1** | collection 五件自由函数全家 + 内部互挂（每件 .settings 显式挂依赖件） | B0-0 绿 + 同构假设实跑（§9 风险 2） | 无（虚拟容器面保留） | 零差分（不 retire 虚拟面） | 五模块 envelopes 双编译同门 |
| **B1（等 S7 快照扩类型面）** | struct/alias 迁源：五件容器类型入 stdlib 源 → prelude 虚拟 struct/alias 逐批 retire（11+5） | 快照类型面扩（S7 后续件） | PreludeDecls.STRUCTS/ALIASES | **翻新**——用户 HIR 的构造/类型符号指向 stdlib 源（OQ-10） | re-baseline t132stdlib + 67 用户差分台账 |
| **B2（等扩展函数限定通道）** | ext 函数跨模块（map/fold/flatMap 等）+ **add 落体（§4）** | 快照 ext 面 + 体方案裁决（OQ-4） | EXT_METHODS（add）；FUNS（MutableListOf/ListOf）后同批或 B2.1 | 翻新（方法糖解析源变化） | 同上 |
| **B3（独立后续件）** | 公理库 axioms（规划 §3 清单；归纳法暂缓 t91 裁决 6） | 归纳法讨论（未落） | 无 | 无 | 笺注命题面 |

**每批验收门（R-22 纪律）**：① 用户 67/67 逐字节 SAME（B1 前）或差分登记表（B1/B2）；② stdlib 侧基线双编译逐字节同（nid 纪律延续）；③ 测套只增不减；④ 每批**回滚边界** = 恢复对应 Kotlin 表条目（B0-0 回滚 = 恢复 PRELUDE_FUNS 三行——五分钟级回退）。

---

## 4 add 虚拟方法落体（设计范围 4 · R-15 兑现路径）

现状（F1）：`virtualExtMethods` 注册（SymbolTable:434）+ ext 桶查找 :203，签名面 `@mut List[a].add(v : a) : List[a]`，**无体**。R-15 注释承诺「方法体随后端/标准库源（S2）落」。

落体的三选（OQ-4 队长裁）：

| 方案 | 形态 | 代价/风险 | 本提案倾向 |
|---|---|---|---|
| a · 结构性体 | stdlib 源写 `fun @mut List[a].add(v : a) : List[a] = Cons(v, self)` 形式体 | 与 F12 运行时数组形态（codegen 特判 Nil=[]/Cons=[h,...t]）**不一致**——形式正确但运行语义需 codegen 桥转译；无桥则语义假 | 否（待桥规格） |
| b · 签名面延续 | stdlib 源 add 声明**仍无体**（注释登记体随运行时桥落） | 与现状同构——R-15 兑现延后到桥规格 | **倾向**（最保守） |
| c · 内建桥原语 | 语言增 builtin primitive 声明位（add 形参收 run-time 桥） | 新语言面（超出标准库范围） | 否 |

结论：V1 add 落体 = **B2 批落签名面迁移**（虚拟 → stdlib 源声明，体仍空 + 注释登记），体随「运行时桥规格」后续件落（登记 R-15 兑现债）。

---

## 5 与 e2e 样本的关系（设计范围 5）

实测结论（F4/F5）：collection 7 样本**按多文件模块设计**（同名自由函数按文件分家）——逐文件编译伪红（list/map）、目录单模块编译 E-DUP-DECL 红，**两种现有 e2e 形态都表达不了它的原设计**。

- 提案（倾向 e2e 原样保留 + stdlib 独立新树）：
  - e2e core/collection 7 件 byte 冻结于 t122n（list/map 冻红稳定产物记账），作为**回归基线**不动；68 计数零动（对齐 t131 纪律）。
  - stdlib 树为**独立新内容**（首批内容 = 样本内容按 §1 模块分家重组——换挂载关系与限定形态）。
  - 复制内容债登记：stdlib/collection 与 e2e core/collection 两份近似内容并存至 B1 后的清理批（OQ-3 队长裁清理时机）。
  - stdlib 验收 = **新独立门** jsoncmp/out/t132stdlib/（compile stdlib/collection 形态——5 模块 envelopes；双编译逐字节同 + ok=true 门）。

---

## 6 内容清单与范围裁剪（设计范围 6）

**stdlib V1 = collection 五件 + math 自由函数子集**（仅此而已——刻意小）：

| 模块 | struct | alias | ext | free 自由函数（迁源面） | 备注 |
|---|---|---|---|---|---|
| collection/list | Nil, Cons | List[T] | map, fold, pure, flatMap | size, isEmpty, head, last, elementAt, get, firstOr, contains, indexOf, count, any, all, find, reverse, **range, sum**, emptyList, cons, listOf 等 | F3 清单全量 |
| collection/optional | Some, None | Optional[T] | map, fold, pure, flatMap | isSome, isNone, getOrElse, orElse 等 | |
| collection/result | Ok, Err | Result[T,E] | （样本 ext 面） | isOk, isErr, getOrElse 等 | |
| collection/set | SetNil, SetCons | Set[T] | — | size, isEmpty, contains, insert 等 | |
| collection/map | MapNil, MapCons | Map[K,V] | — | size, isEmpty, get, put, remove, containsKey, keys, values, filter, mapValues, mapKeys, merge, toList（keys/values 依赖 list） | |
| math | — | — | — | **summation**（= sum(range(a,b))——跨件限定引用） | B0-0 落 |

**V1 明确不收**（范围裁剪理由）：
- **kernel 层 60+ 内建函数**（print/read*/json*/str*/math*/channel*等）：属语言内建定义层（规划稿「内核层随编译器版本」）——V1 不迁，以**附录 A 规格登记表**文档化（可审计性补丁，非机制迁移）；
- string/num/bool「基础面」：同上（内建标量 + builtinSig 函数表即「基础面」本体）；
- Tuple 特殊形态（`fun (a, b).size` 元组 ext，e2e 样本级内建形态）：保留 e2e 样本级；
- axioms 公理库：规划 §3 寄存，B3 独立件（归纳法暂缓 t91 裁决 6）。

---

## 7 给队长的开放问题清单（不代裁）

| # | 问题 | 本提案倾向 |
|---|---|---|
| OQ-1 | **非限定 stdlib 查询链**（R-22「本模块>挂载子>prelude」的字面落实）：用户裸名 `sum(xs)` 是否回落 stdlib 快照 | 给（过渡期零改用户代码；retire 路径成立的必要件；不许则 B0-0 无意义——快照仅限 `stdlib.list.sum` 形态可用） |
| OQ-2 | stdlib 产品口径：随用户产 emit stdlib envelopes（每批翻 67）vs 独立侧基线（用户 67 逐批保护） | 侧基线（用户 SAME 是零侵入机器证明；翻新收敛到 stdlib 侧） |
| OQ-3 | e2e core/collection 7 样本处置：原样保留作回归（复制内容债）vs 迁成 stdlib 源后移除（68 计数动） | 原样保留 + 独立 stdlib 门（B1 后清理批再决） |
| OQ-4 | add 落体三选：a 结构性体 / b 签名面延续 / c 内建桥原语 | b（R-15 兑现债登记到运行时桥规格件） |
| OQ-5 | 首批粒度：B0-0 极小批（PRELUDE_FUNS 三项）先行 vs B0-1 直上五件 | B0-0（五分钟级回滚边界 + parity 一次验清） |
| OQ-6 | 默认挂载别名：无别名（collection.* 全形态）vs 根别名 `stdlib`（stdlib.list.sum 形态） | 根别名（限定形态可读 + 用户同名 `stdlib` 模块时 P-7 用户优先自然落） |
| OQ-7 | math 模块归属：stdlib/math 独立 vs 并入 collection | 独立（语义边界——sum/range 是 collection，summation 是数论） |
| OQ-8 | kernel 内建面文档化形态：本设计件附录表格 vs 独立 documents/kernel-内建面规格.md | 附录先落 + B0 前抽独立件（60+ 条目表格正文挤占） |
| OQ-9 | 每批 retire parity 门：是否加「虚拟表 vs stdlib 快照」签名对拍脚本 | 加（每批一脚本列条目级 parity——retire 的机械证据） |
| OQ-10 | B1 类型面 retire 时用户 67 基线处理：t122n 翻新 vs 差分台账（受众差异分级） | 差分台账 + B2 毕一次性翻新（防每批翻基线漂移难追） |
| OQ-11 | stdlib 可审计性：stdlib 根 envelope 携版本/hash 登记 vs 侧基线 hash 登记 | 侧基线 hash（jsoncmp 既有机制零新面） |
| OQ-12 | stdlib 模块体 V1 单文件：多文件模块（如 list 拆 core/ext）是否开 | 不开（单文件 + 目录分家已可表达；多文件拼行号面 t131 已收限制） |

---

## 8 待解决专章四分类（全点名）

| 分类 | 专章 | 待裁点（指向 §） |
|---|---|---|
| **① 语法解析面** | §2 默认挂载插入点；§1 布局 | 编译根 .settings 合成位（入口级 vs scanner 级——零 Parser 改动的插入点）；stdlib 源 parse 门（t131 既有管道复用） |
| **② 语义/符号表面** | §2 查询链；§4 add 落体 | 非限定链开否（OQ-1）；P-7 用户优先与虚拟表/快照的三序位；E-DUP-DECL 模块作用域化（t131 已落——stdlib 分家的依赖前提）；虚拟方法注册 retire 路径（virtualExtMethods SymbolTable:434/:203） |
| **③ 工程与产物面** | §2 产品口径；§3 批次；§5 e2e 关系 | 侧基线脚本（jsoncmp/scripts/t132stdlib 导出脚本新门）；每批 SAME 翻新点（OQ-10）；复制内容债与清理批（OQ-3）；retire 表记账（README 分账） |
| **④ 验收/迁移面** | §3 批次表；§9 风险 | B0-0→B0-1→B1→B2→B3 排序与每批门；回滚边界（恢复表条目）；parity 对拍脚本（OQ-9）；stdlib gate 双编译同 + ok=true |

---

## 9 风险与诚实边界

1. **B0-0 parity 风险**：PRELUDE_FUNS（Kotlin 表）→ stdlib 快照链的「无缝接管」是**设计推断**（签名逐字同 ⇒ 解析路径同），须 B0-0 落地件实跑 67/67 验证；若断（如纯度/诊断附带的隐性差分）**即回滚**——本提案把 B0-0 定为可即刻批的依据正是其回滚边界小。
2. **名义类型同构假设**：stdlib 模块内本地 struct/alias 与 prelude 虚拟同构（Some/None/List 等 字段同名同型），父域按名解析成立（nominal by name）；B1 前靠「同名同构」成立，**若某件字段细节与虚拟声明有差异（如 Channel 的默认 cap）→ 父域类型核对误报**——B0-1 门须实跑五件与 68 样本的交叉编译验证。
3. **F12 运行时数组形态**：List 的运行时是 codegen 特判数组——结构性体（Cons）与数组形态并存，使 add 等「写容器」的源形式体无法正确表达运行语义（OQ-4 的 a 方案风险源）。
4. **kernel 层不迁**：60+ 内建函数留在 builtinSig——「不可审计」的旧病延续，附录 A 表格是文档化补丁，不是机制解（可审计需独立件 + 命题面）。
5. **内容裁剪的双向风险**：V1 极小（collection 五件 + math 子集）——若用户预期「标准库应有 string/num 面」，本提案是把 string/num 明确归 kernel 内建面（附录登记）而非缺失；反向「什么都往里塞」的风险以批次纪律（每批 retire 一张表）硬约束。
6. 本提案的实测基线为 2026-11-12 树态（t131 收口，commit 0e843af 附近）；Kotlin 表行号锚点随树漂移，落地件复核。

---

## 附录 A · kernel 内建面规格登记（V1 不迁 · 文档化补丁）

builtinSig（TypeInfer:790）60+ 内建函数按域分类登记——**语言定义层**（随编译器版本，规划稿 §2 内核层口径）：

| 域 | 名字 | 签名形态 |
|---|---|---|
| IO/系统 | print, readLine, readFile, writeFile, appendFile, fileExists, readDir, getArgs, timeNow, sleep, delay, rand | Any→Null / Str→Result 等 |
| 异步 | runBoth, runSeq, withMutex, bumpCounter, bumpPair, collectToList, collectCount, collectAll, channelFromList, sendAll, channelDrain, channelPipe, sublEval, sublRun | Flow/Channel/Mutex 域 |
| 字符串 | concat, length, charAt, substring, strCmp, toStr, strUpper, strLower, strTrim, startsWith, endsWith, strReplace, strSplit, strIndexOf | Str 域 |
| 数学 | abs, floor, ceil, pow, mathMin, mathMax, mathClamp, mathRound, mathSin, mathCos, mathTan, parseNat, parseRat, toRat, toNat, toReal | Rat/Nat/Real 域 |
| JSON | jsonParse, jsonGet, jsonAt, jsonLen, jsonStr, jsonNum, jsonBool, jsonIsNull, jsonIsArr, jsonToStr, jsonToList | Json 域 |
| 控制流 | panic, in, emptyTuple, singleTuple | Nothing / Range / Tuple |

- prelude 类型面（B1 后 retire）：BUILTIN_TYPES 标量/单例/异步泛类型（Nat/Int/Rat/Real/Str/Bool/Null/Nothing/Any/EmptyTuple/SingleTuple/Task/Channel/Mutex/Flow/Range/Json）；容器五件（List/Optional/Result/Map/Set）由 PreludeDecls 虚拟声明。
- retire 路线（R-22 形态 C）：B0-0 PRELUDE_FUNS(3) → B1 STRUCTS(11)+ALIASES(5) → B2 EXT_METHODS(1)+FUNS(2)；BUILTIN_FUNS/builtinSig 60+ **不在 retire 路线**（kernel 层常驻）。
