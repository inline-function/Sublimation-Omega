# @mut 与效应：旧工程参考文档 vs 新工程实现——缺口审计（t93）

- 日期：2026-11-09 · 任务 t93（只读分析，零代码改动、零构建）· 尝试 1（`d0cfaedb`）
- 参考件：`documents/旧工程参考-副作用与可变性控制.md`（根文档，163 行，简称「根文档」）、`documents/旧工程参考-效应与@impure.md`（旧 wiki，192 行，记**实测实现**，简称「wiki」）
- 口径：用户已裁决**旧工程作参考**（非逐字上位规格）⇒ 本审计是**差异分析**，不是照抄检查。🔱「新工程另有设计」单列，不当缺口。
- 证据纪律：每条结论给 `文件:行`；诊断码存在性用全 main 源码穷尽 grep（`E-IMPURE`、`E-NON-STRUCTURAL-REC`、`D-AUTO-IMPURE`、`E-NEED-ASYNC`、`@ref`/`pure<` 均 **ZERO** 命中）。

## 一、条款逐条对照表

### 1.1 根文档（副作用与可变性控制）

| 旧工程设计 | 来源 | 新工程现状 | 证据 | 判定 |
|---|---|---|---|---|
| 变量默认不可变，注解 @mut 才可变 | 根文档:13 | `var x` 无 @mut ⇒ 不可变；可写来源两口径：语句注解 `@mut` 或类型位 `@mut T`（对齐旧 `Frame.isMut`） | Elaborator.kt:798-810（declareScriptTopVar/declareLocal 的 `"mut" in s.annotations || declared is HirMutType`） | **✅ 已实现** |
| 赋值闸：不可变名/不可变接收者拒写 | 根文档:56-63（`a.n=1` 通、`b.n=1` 拒） | 名字位 + 字段位两闸；字段写须 field @mut **且** 接收者可写 | Elaborator.kt:833（名字位 `E-IMMUT-ASSIGN`）、:2063-2085（`fieldWriteOk`，t60） | **✅ 已实现**（与根文档 :56-63 例子逐格对应） |
| @mut 结构体声明 + 字段 @mut 注解 | 根文档:47-54 | 结构体声明注解、字段注解、props 一并进 StructDecl | Parser.kt:800-802（StructDecl(fields=parseParams(), anns)）、:2040-2042（注解贴 fun/struct/trait） | **✅ 已实现**（语法层；字段写闸见上） |
| @mut 类型类 / 可变函数（方法只能在类型可变时调用） | 根文档:65-81 | trait 声明注解 + 成员 @mut；"可变视图下才可调"升级为**逐参变构性 σ**（σ_imm/σ_mut 双视图），内建泛型走 BuiltinVariance 硬表，不动点求解 + 一致性诊断 | VarianceInfer.kt:19-38（views 变构对定义）、:87-114（不动点 + varianceOf）、:172/176（一致性诊断）;Elaborator.kt:244-258（.variance() 注入）;TypeInfer.kt:340-412（按视图分派的 sub） | **🔱 新工程另有设计**——σ 双视图比根文档的 "可变类型/可变函数" 更细：旧工程按名义标记，新工程按**每个类型参数**在两个视图下各推一个格值。这是 t64/ADR-41 的演进，**不是缺口** |
| @mut 参数 ⇒ 函数必须标 @impure（强制条款） | 根文档:41 + wiki:52 | 强制项诊断 `E-IMPURE-REQUIRED` | **ZERO 命中**（全 main 源码无该码） | **❌ 未实现**（连诊断码都没有） |
| 纯函数三要求：无不纯表达式 / 不捕获外部可变变量 / 证停机 | 根文档:25-31 | 三条检查各自落点 | 全部 **ZERO**：纯作用域检查无、捕获外层 @mut 检查无、停机闸无（仅 TypeInfer.kt:161 `E-FUEL-EXHAUSTED` = 编译器自查燃料，**非函数级停机**） | **❌ 未实现**（三要求全缺） |
| `pure<f>` 自然命题：未标 @impure 的函数自动注册，证之即纯 | 根文档:23 + wiki:54-60 | Γ 供给 + 调用闸三路放行（调用方非纯 / unchecked / Γ⊢pure<f>） | **ZERO 命中**（无注册、无供给、无调用闸） | **❌ 未实现**（命题层完全零 start） |
| 停机证明两条（结构子项 / 数值靠拢）+ 自递归递减闸 | 根文档:35-39 + wiki:89 | `E-NON-STRUCTURAL-REC`（诊断建议加 @impure 逃逸） | **ZERO 命中**；仅 TypeInfer.kt:161 编译器侧燃料 | **❌ 未实现** |
| 可变变量不承认（除自反外的）相等命题 | 根文档:11 + wiki:80 | 证明器对含可变/不纯调用的等式目标不参与自反/结构相等 | ProofChecker 全文无 mutability/impure 特判（grep `自反/impure/HirPEq mutable` 零相关命中）；`:45` 的 P-STRUCT-EQ 是命题层结构相等，不含可变性口径 | **❌ 未实现** |
| 可变拷贝：@mut 类型赋值即深拷（三向：mut→imm / imm→mut / mut→mut） | 根文档:83-108 | 运行时深拷语义 | **ZERO**（无 deepCopy/copy/拷贝 任何代码；Elaborator.kt:2196 的「拷贝」是模式槽位比较，无关） | **❌ 未实现**（且见 二.4：这格的定性是**架构层缺口**） |
| @ref 四形态（`@ref T` / `@ref @mut T` 双向赋值 + 拷贝/共享规则） | 根文档:110-140 | 引用类型 + 拷贝分派 | **全层 ZERO**：Lexer 无 REF kind；Parser.kt:625 类型位注解**只认 @mut/@atomic/@impure**（写 @ref 直接语法错）；HIR 无 RefType；semantic 零 | **❌ 未实现**（语法层入口都是关的） |
| imm↔ref 拷贝矩阵后四格（imm→ref 共享 / ref→imm 深拷 / ref→ref 引用 / ref→mut 视图） | 根文档:142-163 | 运行时 + 上下文待遇（ref 与 mut 同等待遇） | **ZERO**；且无引用亦无上下文效应口径 | **❌ 未实现** |

### 1.2 旧 wiki（效应与 @impure，实测实现）

| 旧 wiki 条款 | 来源 | 新工程现状 | 证据 | 判定 |
|---|---|---|---|---|
| @impure 语法：声明位 / 类型位（借效）/ 与 @async 叠加 | wiki:10-18 | 类型位**完整对应**：Parser:620-623 只修饰 FunType（写 `@impure (A) => Null`，修饰别的类型直接语法错——与 wiki:18 同口径）；声明位注解贴 fun/struct/trait | Parser.kt:620-625、:2040-2042 | **✅ 已实现**（语法层） |
| @impure 语义：效应声明驱动纯度闸 | wiki:6 | 注解只被读成**布尔位** `HirPurity.PURE/IMPURE`，无任何闸消费 | Elaborator.kt:705（`purity = if ("impure" in d.annotations) …`）；消费点仅 TypeInfer.kt:304（函数类型相等需纯度一致）与 :470（渲染 `@impure ` 前缀） | **⚠️ 部分**：语法+HIR 位在，**纯度闸零**——`@impure` 目前是死标签：标了不检查、不标也不报 |
| 宿主内建函数是 @impure 语义的内建来源（builtinimpure 表） | wiki:20-29 | 内建表**只剩名字层**（注释自陈「旧 builtinimpure/builtinPure 两表的名字层点查对齐，P-7」）——纯度分类被有意合并掉 | SymbolTable.kt:505-524（BUILTIN_FUNS 单一名单） | **⚠️ 部分**：名字登记在，**纯度信息丢**（看似 P-7 有意简化：前端只做名字解析） |
| 三路放行（调用方非纯 / uncheckedDepth>0 / Γ⊢pure<f>） | wiki:36-42/60 | 三路全无：无纯度闸 ⇒ 无放行问题；`unchecked` 解析为 UncheckedStmt 但 Elaborator 直接透传内层语句成 HirUnchecked，**不降任何闸**（无闸可降） | Elaborator.kt:868-872；形式化文法.md:461 自陈「uncheckedDepth 的语义后果不属于本卷」 | **⚠️ 部分**：`unchecked` 形式在，分工意义为零 |
| 借用效应（@impure 函数值形参即效应能力）+ 函数值闸 | wiki:62-66 | 无函数值纯度闸、无 borrowed 概念 | **ZERO** | **❌ 未实现** |
| lambda 纯度推导（体内遇不纯调用/改外层捕获即置位） | wiki:68-75 | **硬编码 PURE**：lambda 一律 `HirPurity.PURE`，无推导 | Elaborator.kt:1188/1190 | **❌ 未实现**（且埋了错信息：不纯 lambda 被标纯） |
| impure 结果不入等式推理 / 声明的下文照常弥散 | wiki:77-81 | 命题层无 impure 口径；下文弥散机制本身存在（proof 层）但与纯度无关 | ProofChecker 无相关特判；wiki:81 的 CallCheck:940 对应机制未见 | **❌ 未实现**（等式侧）；下文弥散**未验证**（不属本审计六条款，未深查） |
| @async 正交（E-NEED-ASYNC） | wiki:90 | @async 注解被读取（HIR 字段在），但调用闸无 | Elaborator.kt:709（`purity, async, body`）；`E-NEED-ASYNC` **ZERO** | **⚠️ 部分**：注解通道在，闸无 |
| 脚本模式顶层=隐式非纯入口 | wiki:91 | 脚本顶层帧概念在（`isScriptTopFrame`），但纯度口径无 | Elaborator.kt:801 | **⚠️ 部分**（帧机制在，纯度无） |
| 全局推导：停机失败自动降级 @impure（D-AUTO-IMPURE） | wiki:92 | **ZERO**（新工程无解释器/全局推导 pass） | — | **❌ 未实现**（且属架构差异，见🔱行） |
| @impure 只存在于检查器、解释器与 JS 生成不读它 | wiki:93 | 新工程是**纯前端**（lexer→parser→AST→HIR→JSON），无解释器/codegen——"编译期性"天然成立 | 工程结构：40 个 main .kt 全在 ast/hir/semantic/parser/lexer/json/cli | **🔱 新工程另有设计**（架构层级差异，不是缺口） |

### 1.3 判定汇总与 Q23–Q25 落格

- **✅ 4 条**（变量默认不可变、赋值闸、@mut 结构体字段、@impure 语法三位置）
- **🔱 2 条**（σ 双视图变构性、纯前端架构的编译期性）
- **⚠️ 6 条**（@impure 布尔位化、内建纯度表合并、unchecked 空壳、@async 注解无闸、脚本帧无纯度、[+@root 未验证，未入表]）
- **❌ 11 条**（@ref 全层、pure<f>、停机闸、三要求、E-IMPURE-REQUIRED、lambda 纯度推导、深拷矩阵全部 8 格、可变量等式、借用效应、impure 不入等式、D-AUTO-IMPURE）

**Q23–Q25 现在落在哪一格**（验收点 4）：

| 问题 | 落格 | 依据 |
|---|---|---|
| **Q23 @mut 档位** | **✅ 已实现 + 🔱 演进** | `var x : @mut T` / `@mut var x` 两口径（Elaborator:798-810）、字段写闸 t60（:2063-2085）、σ 双视图（VarianceInfer 全套 = 比根文档更细的演进） |
| **Q24 值语义 vs 引用语义** | **❌ 未实现（架构层）** | 深拷矩阵 8 格全缺、@ref 全层缺失；且前端无运行时层，拷贝语义**在本工程无从落地**（见 二.4） |
| **Q25 var 命名** | **✅ 已实现** | `var` 声明 + 双可写口径 + `E-IMMUT-ASSIGN` 闸（:833）覆盖「默认不可变」的全部写法 |

## 二、四个已现形缺口的深挖

### 2.1 `@ref`：补它要动哪几层

**好消息：词法层零成本**——注解机制走 `@` + IDENT 通用通道（Parser.kt:614-616），`@ref` 不需要新 Kind。要动的是：

| 层 | 动作 | 证据/现状 |
|---|---|---|
| Parser | :617-626 的 `when (an.text)` 加 `"ref" -> RefType(parseType())` 分支 | 现在 :625 对其余名字直接 `errType`——**入口是关的** |
| AST | 新增 `RefType` 节点 | ast/Type.kt 现有 MutType/AtomicType 可照抄形态 |
| HIR + JSON | 新增 `HirRefType` + HirJson 序列化 | hir/Type.kt:45 有 HirMutType 模板；json/HirJson.kt 需加分支 |
| 语义·可写性 | 赋值闸 `mutable` 判定需认 ref 目标（ref≠可写，但 ref 的**内层** @mut 才是） | Elaborator.kt:823-830 现在只认 `HirMutType`/名字 mut 标记 |
| 语义·子类型 | 旧文档:144 语义「T 永不变，@ref T 可能变」⇒ 方向是 `T <: @ref T`（imm 可当 ref 用），`@ref T` 不能当 `T` 用（需深拷，见下）；`@ref @mut T` 与 `@mut T` 的视图关系需新规则 | TypeInfer.kt:395-412 现有 @mut 视图分派机制（σ）**可复用为 ref 分派的模板** |
| 语义·深拷分派 | 赋值/传参时的拷贝/共享裁定需要 ref 判定（矩阵 8 格的静态面） | 无（见 2.4） |
| 命题层 | 旧文档:142「引用与可变同上下文待遇」⇒ ref 名字不进等式 / 上下文系统对 ref 与 mut 同口径 | ProofChecker 无任何 mutability 口径（一.1 第 9 行） |

**成本量级**：语法/AST/HIR 小；**语义层是大头**（可写性 + 子类型 + 拷贝分派 + 命题待遇四条规则）。

### 2.2 `pure<f>`：从布尔位升成命题

**现状**：`@impure` → `HirPurity` 布尔（Elaborator.kt:705），消费点仅类型相等与渲染——**标了等于没标**。wiki:6 的核心句是「纯度不是挂在函数上的布尔，而是驱动纯度闸」——新工程恰好停在了这句话的前半句。

**升级路径**（按依赖排序）：
1. **Γ 注册**：`funDecl` 落 HIR 时，未标 @impure 的具名函数把自然命题 `pure<f>` 注入全局事实池（wiki:56 的口径；跨模块同名保守拦截可后置）。落地处 = Elaborator.kt:632 funDecl + allFacts 通道（:322 Γ 并集既有机制可挂）。
2. **调用闸**：`callExpr` 位检查 callee symbol 的 purity——纯作用域内调非纯函数，三路放行走「调用方非纯 / unchecked / Γ⊢pure<f>」。注意 wiki:186 的 `pure<λ>` 按字面结构匹配、wiki:187 具名 impure 函数作值传递不带走纯度——这两个实测坑写文档时要带。
3. **上文转嫁**：`<pure<f>>` 形态——Parser 的命题上文机制已存在（`<$ ∈ xs>` 一类），复用即可；配合 E-PRE-UNPROVEN 既有义务。
4. **函数值闸 + 借用形参**（wiki:62-66）：`@impure` 函数值形参即效应能力，纯函数可调。

**与 unchecked 的分工**（wiki:83-85 的核心）：`unchecked` = 词法一次性豁免，`pure<f>` = 把义务转嫁给调用方。新工程 `HirUnchecked` 目前是透传空壳（Elaborator.kt:868-872）——升级 2 落地后，`unchecked` 的降闸语义才有真正对象（纯度/可变/停机三闸）。**顺序建议：先 2（闸）后 unchecked 语义**，否则 unchecked 永远是装饰。

### 2.3 停机闸：旧机制与新工程可复用结构

**旧机制**（wiki:89 + 根文档:35-39 描述）：纯函数**自递归**须有实参在「变小」——两条形态：① 实参是原参数的结构子项（递归到空结构停）；② 实参向某数值靠拢（递归到该数值停）。违者 `E-NON-STRUCTURAL-REC`，诊断**直接建议「给函数加 @impure」**——即 @impure 是停机闸的合法逃逸（代价是放弃终止性检查）。

**新工程可复用结构**：
- `callExpr`（Elaborator）已能拿到 callee symbol 与实参列表 → 检测「直接自递归」仅需比对 enclosing fun 名字；
- 「变小」的静态识别：实参为 `$n` 的**字段/构造子解构形态**（→①）或**减法/前驱形态**（→②）——TypeInfer 的类型核对已对实参做 AST 形态敏感的检查，加一层形态判定成本小；
- 现有最接近物：`E-FUEL-EXHAUSTED`（TypeInfer.kt:161）——**但层次完全不同**：那是编译器自己进行检查时的燃料（防止检查器不停），不是用户函数的终止性。**不能混为一谈**。

**结论**：机制是**单一语义 pass**，新工程结构完全够用，成本低；收益高（纯函数终止性是根文档哲学「用不可变与纯函数构造程序」的支撑条款之一）。

### 2.4 深拷贝规则：赋值拷贝矩阵实现了哪几格

**答案：0 格**（一.1 已列证据）。但这一格需要**先定性再定缺口**：

- 旧工程有解释器（wiki:92 的 GlobalInference、wiki:93 的 JS 生成）⇒ 深拷是**运行时行为**；
- 新工程是纯前端（产物 = HIR JSON），**没有运行时层**——深拷贝在当前工程里**无从执行**；
- 前端能做的只有两件事：(a) **静态类型转换规则**（`@mut T` 赋给 `T` 是否合法、`@ref T` 赋给 `T` 是否合法）——现状：TypeInfer 的 sub 有 @mut 视图分派（:395-412）但**赋值拷贝意图不建模**；(b) **诊断提示**（转换处提示「此处运行时将深拷」——若后端需要）。

⇒ **判定**：这不是「漏实现的条款」，是**架构层缺口**——根文档的拷贝矩阵要等后端/解释器层任务才真正落地。前端侧的可执行件 = 把 (a) 的类型转换规则与 (b) 的提示**写入形式化文法/类型规则文档**，使后端有据可依。

## 三、排期建议（价值/成本）

| 序 | 缺口 | 价值 | 成本 | 建议 |
|---|---|---|---|---|
| 1 | **纯度闸**（E-IMPURE-CALL + 三路放行 + lambda 纯度推导 + E-IMPURE-REQUIRED） | **高**——@impure 现在是死标签，用户写了无后果，与根文档哲学直接冲突 | 中（与 t92 同域，改 Elaborator/TypeInfer） | **紧接 t92 排**；lambda 纯度推导（现硬编码 PURE，Elaborator:1190 埋错信息）必须同批，否则闸会误报 |
| 2 | **停机闸 E-NON-STRUCTURAL-REC**（@impure 逃逸） | 高（纯函数 sell point） | 低（单 pass，见 2.3） | 与 1 同批或紧随 |
| 3 | **可变量不承相等命题** | 中（防 `∵ m = m'` 类伪证） | 低（ProofChecker reflex 前查 mutable 标记） | 可与 1 并入 |
| 4 | **内建纯度表恢复**（BUILTIN_FUNS 拆出 impure 名单） | 中（1 的数据依赖） | 低（SymbolTable:505-524 一张表） | 随 1 |
| 5 | **`pure<f>` 命题化**（Γ 注册 + 上文转嫁 + 函数值借用） | 中高 | 中高（2.3 升级路径 4 步） | 依赖 1 落地后单排 |
| 6 | **`@ref` 全层** | ？ | 高（2.1 语义层四规则） | **先问用户**：旧工程作参考非上位规格；@ref 的复杂度与根文档「我希望副作用少」的哲学相反，且无后端时无运行时意义。建议**长留待用户确认是否要**，不主动排 |
| 7 | **深拷贝矩阵** | 高（值语义的根基） | —（当前架构不可做） | **架构层**：前端侧只做类型转换规则 + 文档；真正落地随**后端任务** |
| 8 | **unchecked 降闸语义** | 随 1（无闸则无降闸可言） | 低（透传壳已存在） | 随 1 |

**一句话结论**：新工程把 @mut 一侧（Q23/Q25）做到了**比旧工程更细**（σ 双视图是真正的演进），但把 @impure 一侧（效应/纯度/停机）**只搬了个壳**——布尔位在、闸全无。效应族缺口的根因是「纯度闸从未立项」，不是零散遗漏；先补闸（序 1-4），@ref 与深拷贝走用户确认/后端两条长线。