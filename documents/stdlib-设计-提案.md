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

---

## 批史 · t137 落地（B0-0 + B0-1）

- **B0-0（零差分 retire）**：PRELUDE_FUNS 三项（range/sum/summation）退役为 stdlib 源（collection/list + math）——数据源替换、分发机制不变（同名经挂载 stdlib 快照 → builtinEntry 懒登记 → HIR 逐字节同 t122n）；编译根默认挂载 stdlib（并入挂载表，用户优先）+ `--no-stdlib` 旗；非限定链注入经 RETIRED_BARE_NAMES 允许表（裸名只吃替身三项，R-22 限定通道口径不变）。实测：e2e 68/68 SAME、套件 +15（T137StdlibB0×9 + T137StdlibCollection×6）。
- **B0-1（collection 五件迁源）**：list/optional/result/set/map 自由函数全家（含 ext map/fold/pure/flatMap）入 stdlib，跨件调用走**限定引用**（list 的 firstOr→`optional.getOrElse`、toSet→`set.add`、toMap→`map.put`）——裸名链隔离（getOrElse/add/put 在用户域保持未解析 → e2e 冻红态不变，68/68 SAME 保持）；stdlib 全件零红（list 的 slice 因 Nat 减法 Int 化改 take/drop 复合，e2e 原文该处亦红）；钻石形备忘按首次路径记 unitPath（optional/set 见于 list 命名空间下）。
- **配套件**：`documents/kernel-内建面规格.md`（附录 A 抽出扩写的 71 名独立登记件）；`jsoncmp/scripts/t137-parity.ps1` + 侧基线 `jsoncmp/out/t137stdlib/`。

---

## 批史 · t138 落地（B1：虚拟 struct + 联合别名 retire）

- **B1（11 虚拟 struct + 5 联合别名 retire → stdlib 源）**：Nil/Cons → list、None/Some → optional、Ok/Err → result、SetNil/SetCons → set、MapNil/MapCons → map 五件**源声明**（形状逐字同构）；联合别名 Optional/List/Result/Set/Map 同件 retire；**Channel 留虚拟**（无 stdlib/channel 件，登记下一件）。机制同 B0 数据源替换纪律：PreludeDecls 虚拟层撤（`STRUCTS` 仅余 Channel、`ALIASES` 置空），新设 `RETIRED_STRUCTS`/`RETIRED_ALIASES` 允许表门——std 挂载快照注入父域经 SymbolTable 注入名集 + Elaborator allowlist 过滤（stdlib 件内非 retired 名裸名不扩散，R-22 限定口径不变）；**不用 declareModuleCtor**（模块符号 id 不入 HIR，builtins 懒登记 id 顺序零动）。
- **DAG 分层重排（B1 纪律增量）**：模块图须 DAG 且「限定构造形态 V1 未通」（结构体经非限定构造名解析、挂载注入父域）——stdlib 件互依赖类型须靠挂载解决，list↔optional 互依成环。分层落：**list 为底层纯 List 域**（不产 Optional/Set/Map）；optional 挂 list+result 并托管**产出 Optional 的 List 访问器**（head/last/elementAt/find/indexOf/maxNat/minNat/get/firstOr，细化命题经限定调用 `list.size(xs)`）；set/map 各挂 list（+optional）并托管 toSet/toMap。9 个函数件属移位登差分台账（函数全家与 e2e 冻红零变——裸名不可见故用户面不动）。
- **差分台账（OQ-10 B1 口径）**：基线 t131mod，最终 68 门 = SAME 52 + **剥差同 16**（差异位全部为 `retType` 联合展开位 pos，共 138 处，message/其他位差异=0——别名展开的变体位置由虚拟声明 SYNTHETIC 位变为 stdlib 模块源实坐标；剥差（仅删 pos）后 16/16 逐字节同，行为零变）；脚本门 14/14 逐字节同（out-subs/t01）；模块双导出确定性 8/8（1+7 件集合）；套件 1111 → **1120**（+9：T138StdlibRetireTest 快照条目在位/用户面解析等价/形状同构/负例先红/裸名面隔离/--no-stdlib 红/Channel 留虚拟/双导出确定性/DAG 分层绿）。
- **工具路径同构**：`compile()`/`compileWithRounds()`/`compileVariance()`/`elaborate()` 新增 `mounted` 透传参数（默认空——非模块编译零影响），虚拟层依赖的 12 件旧测试 script helper 改挂 stdlib 快照（同 t19 口径）；机械证据件 `jsoncmp/scripts/t138-parity.ps1`（四段：retire 核对 / 7 依赖样本剥差门 / 68 门台账导出 `out-subs/t138gate-ledger.txt` / 模块确定性）。

---

## 12 批史 t139（stdlib B2: EXT_METHODS retire + ext 限定调位，2026-11-13）

**完成面**（R-23 批表 B2 行 + OQ-4 结论 b 落地）:

1. **add 落体**：`@mut fun (@mut List[a]).add[a](v: a): List[a]` 落 stdlib collection list 源（基础件），**体仍空**（OQ-4 队长裁 b 签名面延续——结构性体 a 方案在 codegen 数组特判语义下假；体随「运行时桥规格」R4 落，R-15 兑现债登记）。去糖签名面与旧虚拟 EXT_METHODS 逐字同构（@mut 注解 + (@mut List[a]) 接收者首参 + 形参 v:a + 返回 List[a]）。
2. **EXT_METHODS retire**（同 B0/B1 数据源替换纪律）：`EXT_METHODS = listOf()`，新增 `RETIRED_EXT_METHODS = setOf("List" to "add")` 允许表；`SymbolTable.virtualExtMethods` 改可变注入表 + `declareModuleExt(head, name, decl)`（父域注入唯一入口），`lookupExtDeclVisible` 虚拟兜底与 `preludeVirtualExt` 懒登记链零改（builtins id 序零漂移 ⇒ 方法糖/直接调用面 HIR 形态零差分）。`sugarExt` 对 **stdlib 快照**的 retire 键跳过模块面命中（注入虚拟层承接——否则模块符号 id 落 HIR 漂移）；用户件挂载面维持 t134 口径。
3. **ext 跨模块限定通道（ext 限定调位）**: 自由函数面未命中时，`moduleQualifiedCall` 查快照 ext 面（实参 1 = 接收者位，头推导复用 `extHeadOf`）——`list.add(xs, v)` / `list.map(xs, f)` / `optional.map(o, f)` 形态解析到 ext 符号，HIR = `HirField(模块别名, 名, field=ext 符号)` + D-MODULE-REF 旁注 + `methodSugar=false`（限定去糖形态）；签名核对复用 `extCallType`（接收者首参），借用实参位 borrowArgNotes 同方法糖口径，@mut 调用登记 mutMethodCalls。诊断文案升格「自由函数面与扩展函数面均无该名」。负例先红：未挂载早死、缺件早死、裸名直接调不解析（R-22 方法糖/限定唯一通道）。
4. **不动面**: BUILTIN_TYPES/builtins 内建懒登记序、FUNS（MutableListOf/ListOf 分 B2.1 后置）、ext 方法糖/限定通道对 stdlib-local 非 retire 名的隔离（R-22: 不注入非限定直接调链）、68 计数与 t122n 基线（差异入差分台账——见下）。

**B2 差分台账**（jsoncmp/README.md t139 分账 + out-subs/t139gate-ledger.txt 机械证据）:

- 68 门（vs t131mod）：**SAME 68/68**（B2 全样本逐字节同；重跑 t138-parity 同得 68/68，两证一致）。当 7 依赖样本（vs t122n）：SAME 6 + 剥差同 1（stdlib 集合：联合展开 pos 位，延续 B1 剥差口径）。
- stdlib 侧基线（vs t137stdlib): 逐字节同 3 件 + 预期差 **list 件** = add 声明位（HirFunDecl 新条目 + 源位）; optional/result/set/map 件不在 t137 基线覆盖面（模块样本挂载集差异），以模块双导出确定性 8/8 + 套件门 3/4 兜底。
- 套件 1128/1128（+8 T139StdlibExtTest: 快照条目/体空/方法糖零差分/限定调位绿/负例先红/用户优先/确定性/全件零红）; 脚本门 14/14; 模块双导出 8/8; 模块件全家零红（DAG 分层不变）。
- **方法糖解析源差异的机械证明**（队长裁：由语义层定义解析源元数据域）: B2 将 add 的解析源由虚拟 Kotlin 声明换为 stdlib 源声明（注入虚拟层），可能漂移的元数据域有三——**声明 pos、签名类型 pos、符号 origin**; 实测 《probe》 `xs.add(2)` 用户产物：① 方法糖调用位 `HirNameRef(name=add, symbol={id:6, kind:fun, name:add})`——**SymbolRef schema 仅 {id, kind, name}，不含 pos**（stdlib 声明位 18:25-18:27 不入用户产物，实测未出现）; ② 调用节点 pos = 用户调用位（3:16-3:18）——源位不透传; ③ 符号 id 由 `preludeVirtualExt` builtins 懒登记 `nextId++` 依**首次解析序**分配，注入层与虚拟层同码路径 ⇒ id 序零改。④ 旁注面 D-METHOD-SUGAR 在位、D-MODULE-REF 缺席（模块面命中未走）。故「剥 pos/旁注 ⇒ 逐字节同」的剥差证明在本批**无需启用**（68 门全 SAME 逐字节通过），解析源替换的行为零变由上述 schema 边界直接成立。
- 机械证据脚本 `jsoncmp/scripts/t139-parity.ps1` 五段（retire 核对 / 7 依赖剥差门 / 68 门台账 / 侧基线比对 / 确定性 + 脚本门）。

**R-15 兑现债（B2 登记口径）**：add 体仍空——签名面已迁源，体随「运行时桥规格」（提案 §9 后续件 R4）落；落体裁决 = OQ-4 b（队长裁）。下一批触及体的工程面（codegen 数组特判/运行时桥）时一并处理，B2 不越界。

## 13 B2 毕翻新提案（t122n → 新锚一次性翻新 · **仅提案，未执行**）

**提案目的**（OQ-10 队长裁「B2 毕一次性翻新——防每批翻基线漂移难追」）: 当前主基线 `jsoncmp/out/t122n`（t122 时代产物）+ 68 门对拍基线 `jsoncmp/out/t131mod` 已承载 B0/B1/B2 三轮「虚拟源 → stdlib 模块源」的数据源替换；正常态以剥差（pos/message 盲）双层比较维持行为零变。B2 后 stdlib 数据源替换闭环（FUNS 除外，分 B2.1），可一次性把主基线翻到 **B2 树态导出锚**，从而：

- 消除剥差比较的常设噪声（联合展开 pos 位 1 例 + 侧基线预期差集合），门态回归「逐字节同」单一判据;
- 翻新锚固化「stdlib 快照注入位 + builtins id 序」这一稳定形态，后续批（B2.1/B3/运行时桥）差异不再混入历史 pos 漂移。

**执行方案（提案）**:

| 步 | 动作 | 责任 | 风险/对冲 |
|---|---|---|---|
| 1 | 定锚名 `jsoncmp/out/t139mod` 并导出 68 样本 + 侧基线（模块系统 8 件）双编译 | 执行翻新批的人 | 同 t131mod 导出口径（compile 而非 check） |
| 2 | 7 依赖样本 t122n 比较改为 t139mod 比较（剥差门退化为逐字节门） | 同上 | 历史账目保留：旧账（README t137/t138/t139 分账）不删，仅标注「翻新后基线」 |
| 3 | t122n 产物**归档不删**（rename 至 `out/archive/t122n-pre-b2`?），保留三批差分台账的可回溯链 | 队长裁归档名 | 删除即丢审计链 |
| 4 | 翻新门实测：68 门对 t139mod 全 SAME + 脚本门 14/14 + 套件门 | 同 1 | 翻新失败 = 回滚至旧基线（删 t139mod 即可，零破坏） |

**不在本提案内**: B2.1（FUNS: MutableListOf/ListOf 落 stdlib 源——ext 快照注入通道就绪后构造件落地）、B3 axioms、运行时桥（R4）。翻新执行须队长批准后另批（提案者：semantics；执行门号待队长定）。

**本批未越界声明**: B2 不翻任何基线；上述提案仅登记。
## 14 批史 t140（stdlib B2.2: HKT 特质族·接收者形态 + 满足面双通道 + 快照 trait 面，2026-11-13）

**完成面**（用户裁决：特质方法统一接收者形态 `fun F[A].map[A,B](f: (A)=>B): F[B]`）:

1. **特质方法接收者形态语言面**（消假阳）: funDecl 接收者头校验认 kind 形参（`lookupType(F) != null || isTypeParam(F)`）——接收者形态特质声明不再落 W-EXT-OBSOLETE 假阳（真未知头仍警告，冻结用例不动）。
2. **满足面双通道**（satisfiesCheck 形状恒等）: 规范形态路由——**接收者形态规范**（`F[A].map`）经 receiverSpecMatch 匹**同形扩展函数**（kind 显式实例化 F→扩展头后一阶合一，绕开别名展开对 HKT 合一的拦截）; **无接收者规范**（`pure`）先经旧 ext 吸收通道（ADR-24 Self 首位）miss 后经 freeSpecMatch 匹**同名自由函数**（head 钉 concrete 后一阶合一）。三通道 vars 并集含 traitTheory 理论参数（否则 T↔Nat 合一假失败）。缺失规范渲染按规范原形态（`F[A].map(...)`）精确报。
3. **stdlib 特质族件** `stdlib/collection/traits/traits.subl`: Functor / Applicative(:Functor) / Monad(:Applicative) / Foldable / Traversable(:Functor)——map/ap/flatMap/fold/traverse 建接收者位，pure 无接收者（自由函数通道）; 入 stdlib 默认挂载（.settings 第 6 位 collection 件）。
4. **快照 trait 面**（t134 漏面补齐）: ModuleSnapshot 加 `traits` 面（ModuleTrait = SymbolRef(kind=TRAIT) + TraitDecl）——限定类型面 `type()` 加 trait 分支（`traits.Functor` → HirNamedType + 模块符号引用 + D-MODULE-REF）; init 将挂载快照特质注入父域 `traitDecls`（满足判定/变构推断/环检共用——裸名界不经 R-22 非限定链：stdlib 特质界名裸写落 E-UNBOUND-NAME，唯一引用通道是限定父链）。
5. **容器实例 auto-impl**（ST-3/ST-9 声明即满足）: List/Optional/Set 的 map/ap/flatMap/fold/pure **扩展函数**（既有 stdlib 件承载）; 实测 stdlib 挂载 ext 查询链（t134 方法糖链同源）供给继承规范——`trait M : traits.Functor` 的 map 无本地实例亦满足。pure 的自由函数实例在 stdlib **无裸名落位**（R-23 裸名链纪律：特质实例名不入用户裸名面——曾试落 list/optional/set 三件触发模块路径 E-DUP-DECL 裸名撞，即回退; 自由函数通道为用户模块级能力，由语言面测试覆盖）。
6. **traverse 单子约束边界（挂账）**: 卷 §10.2 V1 HKT 约束满足判定实测——`M[_]: Applicative` 在 checkConstraints 直接落 E-TC-BOUNDARY（理论参数 arity>0 不可为界，TS-24）⇒ Traversable 本体挂账：签名以无界形态声明（M 空商位、traverse 理论参数 [A,M,B]），单子律 V1 不可表达。**债登记**，不准硬砸。

**B2.2 差分台账**（jsoncmp/README.md t140 分账 + out-subs/t140*-ledger 机械证据）:

- 精确差分（vs t139 冻结产物 out-subs/t139gate）：**67 e2e 件逐字节同** + 模块样本主件与 8 子件逐字节同 + **唯一 additive = 新子件 `__traits`**（stdlib 默认挂载新增第 7 件的产物——零既有字节变化）。
- 68 门 vs **t122n 死冻锚**（队长修正令口径）: SAME 51 + 剥差同 16（历史盲差 pos/message，t131 时代起从未收敛——非本批所致）+ 结构差 1（模块样本：t131 冻红→绿演化 + t140 traits 子件）。
- stdlib 侧：t137stdlib 基线 3 同（math/tool/主件）+ list 件差（t139 add 声明位，保持）+ t139→t140 快照侧 8/8 逐字节同 + traits 新件。
- 套件 **1144/1144**（1128 基线 + 16 T140StdlibTraitsTest: 特质族落位/容器实例/接收者声明无假阳/真未知头仍警告/双通道三绿/stdlib 注入两门/负例四红/traverse 边界红/旧形态冻结两通道/限定引用绿/未挂载前缀形态保留）; 更新 3 处存量断言（stdlib 件清单 2 处 + 模块子产物 7→8——库存口径随新件更新，测试不减）。
- 模块双导出确定性 **9/9**（原 8/8 → 9 件: +traits 子件）; 脚本门 14/14。
- 机械证据脚本 `jsoncmp/scripts/t140-parity.ps1`（traits 落位核对 / 68 门 t122n 冻锚台账 / 精确差分 / 侧基线 / 确定性 + 脚本门）。

**诚实边界与债**:

- traverse 单子约束（§10.2 V1 不可为界）——本体挂账，后续约束面件一并收;
- stdlib 特质**裸名界不解析**（R-22 限定唯一通道）——界名裸写须本地声明;
- 跨模块**自由函数实例**不经快照面入满足判定（freeSpecMatch 只吃本模块 funDecls）——stdlib auto-impl 靠挂载 ext 链（接收者形态规范）与声明即满足口径;
- 预告 t141（R-24 v2）: `@mutable` 新注解纯糖（映 `@mut @ref` 接收者组合）、⇒ impure、吸收接收者形态 E-MUT-CAPTURE 误伤、stdlib add 改函数级形态、两形态 HIR 逐字节同——批内别按旧 v1 spec 做。

**本批未越界声明**: 不动 B2.1（FUNS 构造件）、不动 add 体（R-15 随运行时桥）、不翻基线（t122n 死冻锚口径沿用，§13 翻新提案仍仅提案）、不硬砸 Traversable 单子约束。

## 15 批史 t141（R-24 v2：@mutable 注解 ≡ @mut @ref 接收者糖，2026-11-13）

**完成面**（用户修订 v2：v1 漏 @ref——`@mutable fun List[T].add[T](e: T)` ≡ `fun (@mut @ref List[T]).add[T](e: T)`）:

1. **@mutable 纯糖**（函数级注解语义层化）: 扩展函数声明位 `@mutable` → 接收者映 @mut @ref 组合（AST 包裹在 Elaborator.funDecl 内完成——解析器零变、S6 借用通道既有）; 提升位注解剥（hirAnns 剥 "mutable"）⇒ 两形态 **HIR 逐字节同**（T141 门 1 机器证明：pos/nid 剥后 hir 子树全等——注解表/接收者/purity/params 同源）; @mut 函数级语义零变（纯度旗保持 pure——T141 门 6）。
2. **@mutable ⇒ impure**（按引用改调用方状态）: purity 位 + 闸帧 impureByDecl + Γ enriched 表 + FunFrame 同源（接收者 @mut ⇒ impure 一体规则）; 纯域调用落闸（本地扩展经纯度闸 E-IMPURE-CALL; 虚拟层扩展经 E-IMPURE-REQUIRED——@mut 方法糖同位登记位）。
3. **等价化（E-MUT-CAPTURE 误伤消）**: self 是函数自带的可变形参，非外层捕获——接收者 @mut/@mutable 形态声明口径 impure ⇒ 捕获报告 `!impureByDecl && !ancestorsImpure` 同源吸收（a2/a3/a4 实测误伤全消）; **闸范围未放宽**——纯函数真捕获判据逐字零变（T107 形态 T141 门 3 复核红）。
4. **H-MUT-RECEIVER 提示**（H 家族·不拦）: 仅接收者显式 @mut @ref 且未用 @mutable 时发（建议提升）; 函数级零提示; @mut 无 @ref / 纯接收者零提示（T141 门 4 四边界）。@mutable 静默忽略=零语义状态结案（无接收者位无映射——边界同未知注解，体验缺口挂账用户后裁）。
5. **stdlib add 落体改函数级**: `@mutable fun List[a].add[a](e: a): List[a]`（旧 `@mut fun (@mut List[a]).add[a](v: a)`）——体仍空（OQ-4 b 签名面延续; **R-15 债登记不变**）; 快照面零形态差（anns=["mutable"] + 接收者 List 头——提升在 HIR 化层，糖渠道解析面 id 序零漂）。

**B2 期后差分台账**（jsoncmp/README.md t141 分账 + t141-parity 五段机械证据）:

- 精确差分（vs t140 冻结产物）: **67 e2e 件逐字节同** + 模块样本主件同 + 子件 7 同 / **唯一产品差 = list 件 add 声明形态**（anns ["mut"]→[] / purity pure→impure / 接收者 HirMutType(HirNamedType(List))→HirMutType(HirRefType(List)) / 形参 v→e; ok 位同、Δ+173 字节）——窄差分台账登记;
- **e2e 调用面零差分**: `xs.add(…)` calleeImpure 位虚拟层惯性保留（t139/t140 口径同源——本批特意不翻）; 虚拟层 stdlib add 不入 enriched ⇒ 单报无叠;
- 68 门 vs **t122n 死冻锚**: SAME 51 + 剥差同 16（历史盲差，与 t140 同口径）+ 结构差 1（模块样本）; stdlib 侧 8 同 + list 预期差;
- 套件 **1158/1158**（1144 + 14 T141MutableSugarTest: 等价机器证明/纯度闸三路/捕获三门/提示四边界/无接收者静默/stdlib 形态三面/@mut 语义零变）; 存量 1 处断言随 stdlib 形态更新（T139 门 2: @mut 注解→@mutable + 接收者 List 头断言，类 doc 补行——测试零删减）; T128 提升轮/形参视图回写零回归（T3 登记位 +mutable 同位触发）。
- 模块双导出确定性 **9/9**; 脚本门 14/14; 机械证据脚本 `jsoncmp/scripts/t141-parity.ps1`（rc=0）。

**诚实边界与债**:

- **calleeImpure 虚拟层惯性**: @mutable 调用位 calleeImpure=false（虚拟层注解路径未带 purity——@mut 形态同源口径，非新增不一致; E-IMPURE-REQUIRED 闸承载拦截）——留待后续被调侧面件统一;
- **@ref 与借用通道**: 提升的 @ref 在 HIR 接收者位（类型层）; 快照/虚拟层接收者保持裸 List（调用面借用矩阵零变——差分保护）; 注解 @mutable 的接收者借用实参（& 形态）矩阵同 @mut 形态;
- **@mut 函数级仍非 impure**（纯度旗语义——R-24 v2 钦定 @mut 语义不变）; 若后续要 @mut ⇒ impure 另批裁决;
- **@mutable 无接收者静默**（体验缺口——挂账用户后裁，范围外）。

**本批未越界声明**: 不动 B2.1 构造件（FUNS/MutableListOf/ListOf）、不动 add 体（R-15 随运行时桥 R4）、不动 B3 axioms、不翻基线（t122n 死冻锚口径沿用）、不自提交（树留）。

## 16 批史 t142（手动证明 by 证据链：契约位供给通道，2026-11-13）

**完成面**（冻红转绿：examples/手动证明.subl 唯一错 E-CANNOT-PROVE [25:16] 于 `consumeP() by [p]` 修通）:

1. **纸面定位**（实测报告）：`fun consumeP()<p>` 的 `<p>` 在解析器是 **preps（上文前置角组）**（Parser：params 后 `<>` = preps；`: T <q>` = posts 下文）；声明位 preps 注入**函数自身帧** Γ（"调用方在调用点须证，t9 V1 暂仅在声明位登记"——义务未强制）；`produceP()` 的 posts 在声明位由体证（`axiom p` → D-AXIOM-POST），**调用点弥散（Diffusion）为 ProofChecker 头注 V1 推迟项**——即 Γ 无 p；by 断言 `byAsserts` 逐条 `proves(Γ, q)` ⇒ p ∉ Γ ⇒ E-CANNOT-PROVE。**断点 = by 通道缺供给链**。
2. **供给链实现**（Elaborator 两处，证明器零改）：`ByExpr` 分支——目标调用带 preps 时，逐条契约位须有单条证据经既有 `prover.proves` 判据证出（复用，零改主机制）；缺位 ⇒ E-CANNOT-PROVE **精确报契约位**（负例先红：`by [q]` / 多位只供其一 ⇒ 红，不放宽）；供给条经既有 `injectFact` 注入当前块帧 Γ（**下文弥散的手动形态**——Diffusion 推迟项不动）。
3. **byAsserts 上下文截断修**：延迟核对原仅在函数体尾/文件尾，块内注入事实随块帧退栈丢失（`axiom base` 块内 + 后续 `by [base]` 落红 = 既有 bug = 自动证明面既有事实的截断）；`block()` 块尾补 `flushByAsserts()` 落点 ⇒ Γ 完整（对无 by 文件零影响——零回归）。
4. **判据双轨**：契约案例（callee 有 preps）→ 契约位判据（失败位单一精确报——免 byAsserts 叠报；成功位队列保留经块尾 reflexive 复核）；无契约目标（无 preps/非调用/内建）→ byAsserts 旧口径逐字零变。

**验证**（全套门）: 套件 **1166/1166**（1158 + 8 T142ManualProofTest：正例转绿×2 / 负例精确拒×2 / 供给注入 Γ 后续可用 / 无契约旧口径红+绿 / ∵∴ 共存绿）；t142-parity.ps1 五段 rc=0——精确差分 66 e2e 件逐字节同 + 仅手动证明转绿差（ok false→true，Δ+58）；68 门 vs **t122n 死冻锚**：SAME 51→50 + **预期转绿 1**（冻红集合除名登记）+ 剥差同 16 + 模块样本结构差 1（计数闭合 68）；stdlib 侧 9 同（by 通道不触 stdlib）；模块样本主件+8 子件逐字节同（零触及）；确定性 9/9；脚本门 14/14。

**诚实边界与债**:

- **调用点义务仍 V1 仅声明位登记**：`consumeP()` 无 by 不拦（t9 既有口径——本批只通 by 供给通道，不翻义务强制）；
- **下文弥散 Diffusion 依旧 V1 推迟**：本批 by 是**手动**供给通道（证据经判据证出契约位才入 Γ——非免验证注入，与已移除的 E-BY-REMOVED 语句式注入语义隔离）；
- **组合供给（p∧r ⇒ q）不在 V1 判据**：契约位须有**单条**证据证出（逐条映射口径——同任务"by 证据如何映射到契约命题要求位"的纸面约定）；
- 证明器主机制、自动证明面零改（proves/render/快照/injectFact 全复用）。

**本批未越界声明**：不动自动证明主机制、不动 axioms 库（B3）、不动后端运行时；工作树留未自提交。

## 17 批史 t143（R-25 虚拟构造小写化 mutableListOf/listOf，2026-11-13）

**完成面**（用户裁决 R-25：虚拟构造族小写化，`MutableListOf`→`mutableListOf`、`ListOf`→`listOf`；语义零变=纯命名件）:

1. **改名执行**（机械词边界替换 ``——不伤 mutable**ListOf** 内嵌子串不误伤）：主码 6 处（PreludeDecls ctorFun ×2 + 注释 ×4、SymbolTable 注释）+ Kotlin 测试 45 处 + diff-pairs 3 对 6 件 = **51 处 / 17 件**；stdlib 体实测零命中（任务书"1 处"未复现——台账修正）；archive/documents 历史件不改（范围=活码面）。
2. **语义零变机器证**：值参形态 R-20 Alt A 不变（PreludeDecls FUNS 仅名变）；套件 **1166/1166** 存量零 failures；t143-parity 精确差分 vs t142 冻结产物 **76 件逐字节同**（68 门 e2e 全家 + 模块主件+8 子件——e2e 零引用该构造族，产品零差；诊断消息不含构造名 ⇒ 无消息差——任务书"诊断若变须台账登记"边界：未变）。

**验证（全套门）**: 套件 1166/1166（+0 新测——纯命名件无新行为面）；t143-parity.ps1 五段 rc=0——68 门 vs **t122n 死冻锚** **零波动**（SAME 50 + 剥差同 16 + 预期转绿 1（t142 手动证明除名口径延续）+ 模块样本 1，计数闭合）；stdlib 侧 9 同；确定性 9/9；脚本门 14/14；CLI 探针 `mutableListOf(1)` / `listOf(2)` 双绿地。

**诚实边界**:

- **旧 Pascal 名无别名兼容**（纯改名——旧名自此 E-UNBOUND-NAME；用户裁决未提兼容期）；
- 零参 per-call `?T` 形态仍登记后版（P-11 §5.1，R-20——本批不落）；
- 任务书"stdlib 引用 1 处"实测零命中（台账修正登记）。

**本批未越界声明**：不动语义/推导/闸/证明器（0 行行为逻辑改——纯字面改名）；工作树留未自提交。

## 18 批史 t144（R-26.1 括号语义统一：[] 类型实参 + <> 命题参数，2026-11-13）

**完成面**（调用位类型实参从 `<>` 迁 `[]`；`<>` 调用位划归命题参数通道，未通则精确 E 级）:

1. **方括号通道**（Parser）：`parseBracketTypeArgList()` + parsePostfix LBRACKET 后缀分支（同 D-1 失败回滚口径）→ `InstExpr(bracket = true)`；语义层 B8 既有 explicitTps 类型域通路零改（`f[A](args)` / `f[A, B](args)` / `Channel[T](cap)` 绿）。
2. **尖括号调用位精确报**（Elaborator B8 分型）：`bracket=false` 的调用位 InstExpr → **E-CALL-ANGLE**（消息三件：命题通道口径 + 方括号迁移指引 `callee[…]` + by 断言指引 `callee() by [命题]`），降级普通调用不复读类型实参（单点精确、不准静默）。
3. **迁移**：e2e 4 位点（任务书登记）+ Kotlin 测试 4 件；**补登记嵌套角表漏项**——上下文弥散:37 `witness<tagged<7>>()`（正则不跨嵌套尖括号）——按口径落 E-CALL-ANGLE 预期转红（台账闭合）。
4. **零改面**：声明位 `[T]`、类型位 `[Nat]`、命题面（preps/posts、`<p>` 命题括号、`p<v>` 谓词应用、`¬<p>`、by 断言、axiom/∴ 命题列表 `parseBracketList`）逐字零变。

**验证（全套门）**: 套件 **1177/1177**（+11 T144BracketUnifyTest：方括号绿×3 / 负例精确拒×3（类型实参/构造位非命题/嵌套角表命题供给形态）/ 迁移样本绿×3 / 声明位类型位+命题面零回归×2）；t144-parity.ps1 五段 rc=0——精确差分 vs t143 冻结 **75 件逐字节同** + 唯一登记差（上下文弥散转红）；68 门 vs **t122n 死冻锚**：SAME 49 + 剥差同 16 + 转绿 1（t142）+ **预期转红 1** + 模块样本 1（闭合 68）；**4 处糖迁移样本维持 SAME——括号纯语法糖零 HIR 影响已证**；stdlib 侧 9 同；确定性 9/9；脚本门 14/14；行为探针（`[]` 双绿 / `<>` E-CALL-ANGLE 精确码+by 指引）。

**诚实边界**:

- **任务书"4 处"实测漏 1**：嵌套角表 `witness<tagged<7>>()`（上下文弥散：37）未被平正则命中——按 R-26.1 口径预期转红并台账登记（通知链：报告 + 台账）；
- **调用侧命题供给未通**（V1 挂账——同 t142 Diffusion 推迟项一脉）：`witness<谓词>()` 形态须改写 `witness() by [命题]`（命题供给语义本体属手动证明件域，本批不落）；
- 旧 Pascal 形无兼容期（R-26.1 纯迁移裁决同 t143 口径）；E-CALL-ANGLE 消息为中文指引（诊断族口径延续）。

**本批未越界声明**：不动命题供给语义本体/证明器/axioms（B3）；零 HIR/JSON 形态改（产物零差的机器证）；工作树留未自提交。

## 19 批史 t148（R-26.1 收口：调用位 <> 路由回既有命题参数供给通路，2026-11-13）

**收口面**（t144 验收退件修——队长查 t122n 冻锚：`witness<tagged<7>>()` t122n 时已绿，callee theory 含 PropParam，调用位 <> 是命题供给正确形态；任务书"调用侧命题供给未通"为队长事实错误 F3，记队长账）:

1. **路由收口**（Elaborator 调用位 B8 一处）：`bracket=false` 先查 `funDecls[nm]?.theory?.any { it is PropParam }`（t22 B6 同源判据）；有 ⇒ **t122n 既有通路供给**（按名解析 + explicitTps 类型域读取——与 bracket 通路同一）；无 ⇒ 保留 E-CALL-ANGLE。**[] 通路零改**（t144 已验收）；**样本零改**（witness<谓词>() 按 R-26 是正确形态）。
2. **判别性**：复绿面 = theory 含 PropParam（`witness[@q]`）；保留拒面 = 纯类型参数（`id[T]`）、虚拟构造（Channel）、preps 契约形态（`witness<p>`——by 断言供给，t142 口径）——E-CALL-ANGLE 精确报泛化零风险。

**验证**（全套门）: 套件 **1184/1184**（+7 T148PropSupplyRouteTest：复绿×3（骨架+样本本体+契约弥散链）/ 保留拒×2 / [] 零回归×2；T144 11 测试无过截全绿）；t148-parity.ps1 五段 rc=0——**上下文弥散复绿并逐字节回 t122n 锚**（SAME 50 含之；68 门闭合：SAME 50 + 剥差 16 + 转绿 1 + 模块 1）；精确差分 vs t144 冻结 75 件逐字节同 + 唯一登记差（上下文弥散复绿）；stdlib 侧 9 同；确定性 9/9；脚本门 14/14；行为探针（<> 供给绿 / 无命题参数红 / [] 绿）。

**挂账**：声明侧 `[@q]`（命题参数在方括号）与 R-26 `<>` 口径相悖——队长裁属语义层裁决项（不代决），本件不碰声明侧（`fun witness[@q]<q>` 维持原样）。

**本批未越界声明**：[] 通路不动（已验收）；样本零改；工作树留未自提交。

## 20 批史 t149（R-26.2 注解纪律：未知注解/错位 E 级 + 合法位注册表，2026-11-13）

**完成面**（旧状=未知注解静默忽略——值通道零校验）:

1. **注册表落码**（Parser `AnnPositions`）：通道 × 合法位——FUN={impure,root,classic,async,mutable,mut,theorem,axiom}；STRUCT={mut,classic}；TRAIT={classic}；VAR={mut,ref}；PARAM={list}；FIELD={mut}；TYPE={mut,atomic,ref,impure}（与 parseType 既有纪律同源）。@ref var 位= t97 共享只读视图（类型系统/特性巡礼样本实证——初版表中漏列，parity 捕获后补正）。
2. **两码**：E-UNKNOWN-ANN（无此注解+全表文案）/ E-ANN-POSITION（报该注解合法位）。parseType 未知类型注解与 @impure 非函数类型既有 E 升级挂同族码。
3. **五通道收口**：顶层声明按种类 / 特质成员 / 语句（var/fun 分通道）/ var 表达式 / 形参（形参 vs 结构体字段分通道）+ 片段前置注解（防双报）。
4. **承接不变**：@ref 形参名前既有 E 口径（t95）；`@x[…]` 策略语句与 `return @label` 零触及；@unchecked 是语句前缀关键字非注解。

**验证（全套门）**: 负例先红（实现前 8 红——未知名静默/错位无报——不准放行纪律达成）；套件 **1197/1197**（+13 T149AnnotationDisciplineTest）；t149-parity.ps1 五段 rc=0——68 门 vs **t122n 死冻锚** 零波动（SAME 50+剥差 16+转绿 1+模块 1）；精确差分 vs t148gate **76 件逐字节同**（零产品差）；stdlib 9 同；确定性 9/9；脚本门 14/14；行为探针（未知 E-UNKNOWN-ANN / 错位 E-ANN-POSITION / 合法面绿）。

**边界（挂账）**：未新增注解（范围外）；效应分析未触（t147 域）；声明侧 `[@q]` 属用户未裁 A/B 项——注册表 **零触及**（按令）；近名建议未落（任务标注"可选"——消息含全注册表文案已足够定位）。

**本批未越界声明**：仅 Parser.kt 一件主码改 + 注册表；语义层/证明器/产物形态零改（零产品差机器证）；工作树留未自提交。

## 21 批史 t146（R-26.5 / B2.1 构造函数正道：构造族落 stdlib 源 + 虚拟构造 retire，2026-11-13）

**完成面**（旧状=虚拟 ctor 单参（first: T）签名——零参/多变参落 E-ARITY/缺实参）:

1. **stdlib 源**：`fun mutableListOf[T](@list e : T) : @mut List[T]` + `fun listOf[T](@list e : T) : List[T]`——@list 变长形参（t48/ADR-32）吸收 0..n 实参；签名面（体随 R4 运行时桥——同 B2 扩展方法纪律）。
2. **retire**：PreludeDecls 虚拟 ctor 表清空 + `RETIRED_CTOR_FUNS` 允许表（{mutableListOf, listOf}——B1/B2 同形第 4 件）；数据源经 stdlib 快照注入 `funDecls` 种子位 + `virtualFuns` 注入面（declareModuleCtorFun，同 declareModuleExt 纪律）——**解析通道逐字承接 t126 FUN-kind**（sigCheckedCall：@list 变长旗标 + 别名展开 + 理论参数解；preludeVirtualFun 懒登记 id 序零漂移）。
3. **B0/B1/B2 纪律落实**：机制零改只换数据源（B0-0 数族 preludeFun/preludeFunSig 通道逐字不动——零漂移）；一处自纠（首轮 BUILTIN 通道改道致 R15 联合展开丢失⇒回注注入面）。

**验证（全套门）**: 负例先红（实现前 6 红=虚拟 ctor 旧口径零参/多变参/混型 + 3 绿承接）；套件 **1206/1206**（+9 T146CtorFormTest）；t146-parity 五段 rc=0——68 门 vs **t122n 死冻锚** 零波动（SAME 50+剥差 16+转绿 1+模块 1）；精确差分 vs t149gate **75 件逐字节同 + 登记差 1 件 = stdlib list 件 envelope 构造族迁源面**（构造族 e2e 零引用⇒零差实证）；stdlib 侧 8 件同 + list 件登记差；确定性 9/9；脚本门 14/14；行为探针（构造面绿——0/1/3 实参 + @mut List[Nat] 视图落 HIR；混型落 E-TYPE-MISMATCH 元素类型精确报）。

**边界（挂账）**：效应分析未触（t147 域）；命题通道零触及；后端运行时（R4 桥——构造族体）未落（签名面 V1，同 B2 扩展方法口径）；range/sum/summation B0-0 通道不动。R-27 口径（[] 理论参数槽=类型参数+命题参数；<> 实际命题槽）按队长令——本件无参数形式化书写。

**本批越界清单**（inScope）：stdlib/collection/list/list.subl（源）+ PreludeDecls/SymbolTable/Semantic/Elaborator（注入面 retire）+ T146 测试件 + t146-parity 脚本 + README/提案（台账）。工作树留未自提交（t131mod 活目录残留已还原）。

## 22 批史 t150（R-28 隐式深拷矩阵废止 + R-29 深拷补注，2026-11-13）

**完成面**（旧状=16 格中 5 格隐式深拷放行：PLAIN→MUT / REF→PLAIN / REF→MUT / REFMUT→PLAIN / REFMUT→MUT）:

1. **R-28 三态分派**：`TypeInfer.copyDispatch` 拒/复制/共享三态——5 深拷格废止改拒（落既有 E-TYPE-MISMATCH 码族 + 消息含迁移指引：`copy(…)` 取不可变副本 / `toMutableList(…)` 构造可变副本）；MUT→MUT 深拷维持；7 共享格（PLAIN→PLAIN / PLAIN→REF / MUT→REF / MUT→REFMUT / REF→REF / REFMUT→REF / REFMUT→REFMUT）零回归。
2. **R-29 补注**：R-28 后仅存的深拷转换位（MUT→PLAIN / MUT→MUT + R-14 元素层）落 `D-DEEP-COPY` 补充级补注（非 E 级、不拦）——覆盖 8 站点（var 注解位 / 赋值位 / 默认值位 / call 实参位 / 造构实参位 / 结构体字段位 / return 位 / 函数体位）；硬指标 `var a: @mut T = …; var b: T = a` 实测落补注（t150parity 段① 探针 d1/d2）。
3. **套件 fallout 吸收**：T150R28MatrixTest（14 测新建——5 拒格 + 消息指引 / 传参·赋值位 / 补注×4 / 共享格零回归×3 / 既有拒格零回归×2）；旧件形态迁移或期望翻转 20+（R-28 字面口径）——T99 十六格表 @mut 源改构造族 / T98 视图源形态迁移 / ViewDemotion d1/d2/v1 改 @mut var 绑定或 @ref 只读 / T125 三件翻转拒格 / T125MutPromotion 四件翻转（R-06 提升写回被击穿）/ T107 两件翻转（@mut 局部无合法构造}/T126 T3 翻转 / SubtypeOrder 正例迁移 / NarrowSub s4 改绑定 / TypeInferTest 改绑定 / RefTypeInert 两件迁移+翻转 / T128 R211 改 @ref 透传链 + borrow-neg fixture 双门迁移；e2e 样本 3 件（副作用与unchecked / 特性巡礼 / 类型系统）吸收为构造族合法形态。
4. **验证（全套门）**：套件 **1220/1220** 绿（S4/S5 双轮确定性 rc=0）；t150-parity 五段 rc=0——68 门 vs **t122n**：SAME 47 + 剥差同 16 + 预期转绿 1 + 预期差 4（三 e2e 吸收 + 模块样本 1）；精确差分 vs **t149gate**：72 件逐字节同 + 登记差 4（同前）；stdlib 侧 vs t143stdlib 8 同 + list 件迁源面；模块双导出确定性 9/9 + 脚本门 14/14。差分台账：`jsoncmp/out-subs/t150gate-ledger.txt`。

**队长裁决冲突点（结构性后果，本批按字面吸收、已上报待裁）**:

- **冲突点 1（R-06 提升写回被击穿）**：脚本模式 `var x = 1 / x = x + 1` 提升后 x 视图 @mut Nat，RHS plain Nat → PLAIN→MUT 落 E-TYPE-MISMATCH（旧=造格深拷放行）——T125MutPromotion 4 件翻转。处置选项：(a) R-06 提升写回豁免（窄口子）/ (b) @mut var 注解位升级为 HIR @mut T 视图；若维持字面则旧测试 fallout 全量翻转期望（本批已按字面吸收）。
- **冲突点 2（工程模式 @mut 类型位无合法构造）**：`var a : @mut T = T(0)` 现拒（@mut 值唯经 `mutableListOf`/List 族手写）；`@mut var` 绑定 = 注解位（HIR 型仍 plain T，annotations=["mut"]，JSON 无 mutable 键）——T125WriteGate/T107/RefTypeInert/ViewDemotion 等件迁移或翻转。队长硬指标 `var a: @mut T = …` 仅 `var a : @mut List[Nat] = mutableListOf(1)` 形态可走通（t150parity 段① 实测）。

**边界（挂账）**：效应分析实现未触（t151 域）；CoW 与深拷运行时（R2 §三 接位）未落；R-06/R-28 冲突裁决待队长（本批按字面吸收 + 台账登记）。`t146 §21 台账`（本文件 §21）随 t150 提交一并带上——构造族迁源面（R-26.5）为 R-28 合法 @mut 源的唯一供给通道，两批衔接完整。

**本批越界清单**（inScope）：TypeInfer.kt（copyDispatch 三态 + r28Report/deepCopyNote + 8 站点）+ T150R28MatrixTest 新建 + 旧测试件迁移/翻转（T98/T99/T107/T125×2/T126/T128/RefTypeInert/SubtypeOrder/ViewDemotion/NarrowSubtraction/TypeInfer/AssignDefaults）+ e2e 样本 3 件 + diff-pairs fixture 2 对 + 旧工程参考-副作用与可变性控制.md 勘误（:98/:158-162 R-28 废止标注 + :91/:105/:114 格保留标注）+ 效应分析设计-提案.md §1.1 十六格表 7 拒格 + 判定树同步 + jsoncmp/scripts/t150-parity.ps1 五段。

## 23 批史 t156（B3 axioms 公理库：A1–A3 + L1–L4，2026-11-14）

**落地**（规划 §3 首批清单 · Q4 建议范围;归纳法按 t91 裁决 6 暂缓——不落 L5–L7 / E1–E3 / induction 面）:

| 项 | 落位 | 内容 |
|---|---|---|
| 模块 | `stdlib/axioms/axioms.subl` + `stdlib/.settings` 增 `mount axioms` | 默认挂载并入编译根（R-23）;件无依赖挂载（命题项自由名走命题位静默模式——ADR-13 既有口径） |
| 公理 | 同上 | **A1–A3 数值**（nat non-negative / nat add monotone / nat sub non-negative）+ **L1–L4 链表**（list length nil / cons + append 右单位元 / 结合律 / length 可加——L3 拆两条） = 8 条 @axiom 无体 fun |
| 形态约束 | — | @axiom（AnnPositions FUN 级，R-26.2 注册表）+ 命题元变量 [n]（规划 §4 类型判定位延迟——ADR-13 §三）;反引号多词命名 → 裸名链零污染;@axiom 落 funs 表（非 theorems 分表——∴ 搜索面为 @theorem 通道，公理库为规格面） |

- **纪律**：负例先红（T156 测套落盘先跑：门 2/3/4 三红——快照无 axioms 件）;零分配 nid（@axiom 无体声明——零新类型求值）;既有件逐字不动（B3 只加模块——vs t155gate 76 逐字同零增量、68 门分类零波动）;stdlib 9→10 闭合。
- **t156 实测门**：套件 **1281/1281**（1276 + T156StdlibAxiomsTest 5 测：件并入零红 / 八条公理在位 / 限定引用调用绿 / 裸名链隔离 / 未引用零足迹）;T137/T131/T137B0 既有断言随件数更新（键集 +axioms、子件 8→9）;68 门 vs t122n 闭合（38/11/5/3/6/4/1 零波动）;vs t155gate 逐字同 76 + axioms 新增件 1;stdlib vs t143stdlib 10 闭合（4 同 + 3 纯W + 1 纯批二 + list 件登记差 + axioms 新增）;模块双导出确定性 10/10;脚本门 run-gate 过（五段机械证据见 `jsoncmp/scripts/t156-parity.ps1`）。
- **诚实边界**：跨模块命题面（用户 posts 直接实例化 stdlib 公理）未通——@axiom 不入 theorems 分表，公理库现为**规格面**（spec of record）;一致性测试（t65 注入事实 ⊆ 公理可实例化，规划 §6 Q2/路线③）为后续件;M1–M2 / S1–S2 / T1–T3 / G1 随核心库迁入扩容（规划路线⑤）。

## 24 批史 t157（B3 后续件：跨模块命题实例化收口 + 注入一致性 + M/S/T/G 扩容，2026-11-14）

**1. 跨模块命题实例化面（R-27 模块态）**——摸现状定位根因后收口：

- **缺口（负例先红，CLI 探针实测）**：挂载模块 callee 的紧贴角表两路皆红——`mod.gid[Nat](x)` 的类型
  实参按**值域**读（`E-UNBOUND-NAME: Nat` 双错）;`mod.witness<p>()` 落 `E-CALL-ANGLE` 兜底「callee」。
  根因：Elaborator InstExpr callee 分支只查本模块 `funDecls` 判定 PropParam/角表角色，挂载快照 funs 面未接入。
- **收口**:callee target 为限定链时经 `moduleChainResolve` 消解 + 快照被调声明（`ModuleSnapshot.funs[name].decl`——
  t134 既有 AST 承载面）:`[]` 恒为类型实参通道（按类型域读，`moduleQualifiedCall` 携带 `explicitTps` 经
  `sigCheckedCall` 同本地方案）;`<>` 须被调含 PropParam 理论参数（R-26.1/t148 同口径，本模块与快照两处查）,
  否则 E-CALL-ANGLE 精确落位（hint 指回限定链末段函数名）;中段未命中单错落位（免双错同源）。
- **既有边界（登记未强造）**:数值公理形（本库 `[n]` 元变量 + posts）不经调用注入——R-26.1 既有口径（t148 收口）;
  跨文件定理空间搜索按 ADR-13 §三 V1 推迟（归归纳法面 t91 一并暂缓）。用户实例化以 `axiom` 语句 / `by` 断言就地写。

**2. 注入一致性测试（防静默漂移）**——`T157AxiomsConsistencyTest`（cli 测套 4 门）:

- 以 stdlib 编译产物的 HirFunDecl posts **JSON 区段**为基线，逐 16 条断言命题形关键片段（head/谓词名/构造子/
  字面量/量词符）——改任一公理的命题即触红（drift 门:公理集增删与片段两重断言）;
- **注入≡规格**:用户 post `<n >= 0>` 走类型注入（D-NAT-PROP，provenance=type）绿，与 A1 公理声明同形
  断言（快路径与规格分家但口径对应——零 nid、零新通道，不为注入加公理依赖）。

**3. M/S/T/G 扩容（先评估后定 Scope）**——先行 CLI 探测四族命题形（函数应用/构造子应用 `Some(v)`/真值字面量
  `True`/字符串字面量 `""`/区间项 `lo..hi` 与 `∈`）**全部可写**，零强造语法：M1–M2（map 查找-插入/异键不扰）、
S1–S2（set 插入自含/异元不扰）、T1–T3（文本长度可加/结合律/空串单位元）、G1（区间成员等价，闭区间口语——
半开/闭合最终语义随 P-11 区间定案校准）。axioms 件 8→16 条。归纳法族（L5–L7/E1–E3/induction）仍按 t91 暂缓。

- **t157 实测门**:套件 **1289/1289**（1281 + 一致性 4 + 角表通道 4;T156 门 2 随库更新 8→16 命名断言）;
  68 门 vs t122n 闭合零波动（38/11/5/3/6/4/1）;vs t156gate **76 逐字同 + axioms 件 8→16 authorized 零未登记差**;
  stdlib 10 闭合（vs t143stdlib）+ ④b vs t156stdlib 九件逐字同 + axioms 8→16;模块双导出确定性 10/10;
  脚本门 run-gate 过;parity 五段全绿（`jsoncmp/scripts/t157-parity.ps1`）。
- 文档分账：标准库规划 §3.2–3.5 对账标注（M/S/T/G 已落）。
