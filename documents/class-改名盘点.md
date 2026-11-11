# `class` → `trait` 改名影响面盘点（P-10 步骤 0.2)

> 性质：**只读预核查**，不改任何代码/样例/文档。产出即本表。
> 依据：ST-v1 §2 已裁定「class → trait」（语义不变）；`码名迁移台账.md` 规定关键字/术语可直接改、诊断**码名**缓慢改。
> 采样：2026-11-07 12:40-12:57，纯静态 grep（未跑 Gradle）。

## 核心结论（先看这张）

| 层 | 本语言义（要改） | Kotlin 关键字（绝对不动） |
|---|---|---|
| main `*.kt` | **14 类位点 / 共约 95 行**（见 §1 明细） | ~213 行（`data class`/`enum class`/`private class`/`import` 等，清单见 §1.7） |
| test `*.kt` | 约 **30 行**（节点引用、JSON 节点清单、断言串、`Kind.CLASS`） | ~72 行 |
| e2e 样例 | **9 处 `class` 声明 + 约 11 行注释术语** | — |
| 文档 | 文法卷 12 行 / TS 卷 29 行 / ST 卷残留 5-6 行 / spec README 已声明 | ADR ×19 篇、log ×5 篇 → **一律不改**（§2.5/§2.6） |

**两个身份的判据**：`(data|sealed|enum|abstract|private|internal|open|inner) class ⇢Kotlin`；其余 `Class*`/`*Class*` 标识符、`Kind.CLASS`、串字面量里的 class、`.subl` 里的 `class`、中文「类型类」⇠本语言。

## 1. 代码层盘点（frontend/src/main）

### 1.1 词法：关键字入口（改 = 整条管线源头）

| 文件:行 | 原文 | 身份 | 需改 |
|---|---|---|---|
| lexer/Lexer.kt:47 | `"class" to Kind.CLASS, "struct" to Kind.STRUCT,` | 语言关键字——硬关键字表 | ✅ → `"trait" to Kind.TRAIT` |
| lexer/Kind.kt:15 | `FUN, CLASS, STRUCT, ENUM, VAR, IMPL, FOR, …` | token 枚举值名（标识符名） | ✅ `CLASS` → `TRAIT`（Kind 无 TRAIT，需新增） |

> 注：`Kind` 更名后，全工程 `Kind.CLASS` 引用（main 11 处、test 1 处）随之机械替换；**LexerTest.kt:220 的期望 token 清单**同时依赖 Lexer 关键字表，改 1.1 即红。

### 1.2 AST 节点（`ast/Decl.kt` + 引用）

| 文件:行 | 原文 | 身份 | 需改 |
|---|---|---|---|
| ast/Decl.kt:32 | `data class ClassDecl(` | **Kotlin `class` 不动**；类型名 `ClassDecl` | ✅ → `TraitDecl` |
| ast/Decl.kt:31 | `/** S-9【类型类声明】：class 名 [理论参数]…` | 注释（术语+关键字） | ✅ |
| ast/Decl.kt:7/12/75 | 「类型类」「类型类成员」「类型类约束」 | 注释术语 | ✅ → 「特质」 |

> `ClassDecl` 引用共 **21 处**（main）：AstPrinter×2、hir/Decl.kt×1（HirClassDecl 的输入类型）、HirJson×3、Parser×3、Collector×2、Elaborator×9。

### 1.3 HIR 节点与符号表

| 文件:行 | 原文 | 身份 | 需改 |
|---|---|---|---|
| hir/Decl.kt:41 | `data class HirClassDecl(` | Kotlin class 不动；类型名 | ✅ → `HirTraitDecl` |
| hir/Decl.kt:57 | `data class HirClassConst(` | 同上 | ✅ → `HirTraitConst` |
| hir/Decl.kt:53 | `val constants: List<HirClassConst> = emptyList()` | 类型引用 | ✅ |
| hir/Decl.kt:40/51/56/107 | 「类型类声明/常量/约束」注解注释 | 注释术语 | ✅ |
| hir/Symbol.kt:18 | `FUN, CLASS, STRUCT, ENUM, CTOR, …` | SymbolKind 枚举值 | ✅ `CLASS` → `TRAIT` |
| hir/Symbol.kt:19 | `CLASS_CONST,   // P-8/B（TS-6②）：类型类常量规范…` | 枚举值 + 注释 | ✅ → `TRAIT_CONST` |
| hir/Type.kt:87 | `/** 类型类规范内的 Self 占位（卷 TS-29）…` | 注释术语 | ✅ |
| hir/Expr.kt:104 | `* dictionary=类型类字典名（旧决策 60）…` | 注释术语 | ✅ |
| semantic/SymbolTable.kt:48 | `private val classes = LinkedHashMap<String, SymbolEntry>()` | 标识符名 | ✅ → `traits` |
| SymbolTable.kt:350 | `fun classEntry(name: String): SymbolEntry? = classes[name]` | 标识符名 | ✅ → `traitEntry`/`traits` |
| SymbolTable.kt:382-385 | `valueOrTypeTable`/`SymbolKind.CLASS -> classes` | 枚举引用 + 标识符 | ✅ |
| SymbolTable.kt:84 | `SymbolKind.CLASS_CONST -> "类型类常量"` | **诊断文案中文名** | ✅ → `TRAIT_CONST` + 「特质常量」 |
| SymbolTable.kt:290 | `structs[name] ?: enums[name] ?: classes[name]…` | 标识符引用 | ✅ |
| SymbolTable.kt:359 | `classes.containsKey(name) …` | 标识符引用 | ✅ |

> `HirClassDecl` 引用 **12 处**（Elaborator import/构造、HirJson、hir/Decl 内部）；SymbolKind.CLASS 引用：Collector:51、SymbolTable:84/385、HirJsonTest、HirTypeParam 约束点。

### 1.4 Parser（`parseClass` 与分派/同步集）

| 文件:行 | 原文 | 身份 | 需改 |
|---|---|---|---|
| parser/Parser.kt:200 | `Kind.CLASS -> DeclEntry(parseClass(anns))` | 关键字判定 + 函数名 | ✅ `Kind.TRAIT -> parseTrait(…)` |
| parser/Parser.kt:239 | `Kind.CLASS -> parseClass(emptyList())` | 同上 | ✅ |
| parser/Parser.kt:726 | `private fun parseClass(anns: List<String>): Decl {` | 函数名 | ✅ → `parseTrait` |
| parser/Parser.kt:154/193/227/232 | ENTRY_SYNC / DECL_SYNC / CLASS_BODY_SYNC 中的 `Kind.CLASS` | 枚举引用（4 处） | ✅ |
| parser/Parser.kt:765 | `Kind.CLASS, Kind.STRUCT, Kind.ENUM -> errDecl("类型类体内禁止嵌套类型声明（TS-6）：…")` | 枚举引用 + **诊断文案** | ✅（术语同改） |
| parser/Parser.kt:725 | `/** S-9【类型类声明】：class 名 [理论参数] [: 父类型类链]…` | 注释 | ✅ |
| parser/Parser.kt:729 | `… return errDeclSync("期望类型类名", nameTok)` | **诊断文案** | ✅ → 「期望特质名」 |
| parser/Parser.kt:754 | `"val 已废除…。class 不得携带状态：类型类只声明函数规范；…"` | **诊断文案**（t53 刚统一） | ✅ → 「trait 不得携带状态：特质只声明函数规范…」 |
| parser/Parser.kt:278 | `"…类型类体禁止字段声明，数据请放在 struct 的字段里）"` | **诊断文案** | ✅ |
| parser/Parser.kt:24/38/225/725 等注释 | 「无类型类 impl…」「本文件已声明的类型类名…」「类型类成员边界」 | 注释术语 | ✅ |


### 1.5 语义层（Elaborator / Collector）

| 文件:行 | 原文 | 身份 | 需改 |
|---|---|---|---|
| semantic/Collector.kt:3 | `import sublimation.frontend.ast.ClassDecl` | import 标识符 | ✅ |
| semantic/Collector.kt:50-51 | `is ClassDecl -> { if (declareGlobal(SymbolKind.CLASS, …))` | 类型引用 + 枚举引用 | ✅ |
| semantic/Collector.kt:52 | `diag.error("E-DUP-DECL", …, "类型类 " + decl.name + " 重复声明")` | **诊断文案**（码名不含 class，不改码） | ✅ 术语 |
| semantic/Collector.kt:21 | 注释「pass1 …（fun/theorem/struct/enum/class/alias…）」 | 注释 | ✅ |
| semantic/Elaborator.kt:86 | `import …hir.HirClassDecl` | import | ✅ |
| Elaborator.kt:294 | `is ClassDecl -> "classic" in d.annotations` | 类型引用 | ✅ |
| Elaborator.kt:333 | `classCycleCheck(d)` | 标识符名 | ✅ → `traitCycleCheck` |
| Elaborator.kt:337-339 | `// TS-5/TS-25：class 体内 Self…`、`inClassBody++/--` | 注释 + 标识符名 | ✅ → `inTraitBody` |
| Elaborator.kt:341-343 | `…HirClassDecl(d.name, symbols.classEntry(d.name)?.ref(), …)` | 类型引用 + 标识符引用 | ✅ |
| Elaborator.kt:500 | `private fun classCycleCheck(d: ClassDecl)` | 标识符名 + 类型 | ✅ |
| Elaborator.kt:504 | `"类型类继承链出现环（" + d.name + … E-CYCLE` | **诊断文案**（码 E-CYCLE 不含 class，保留） | ✅ 术语 |
| Elaborator.kt:1888-1893 | `// P-8…：class 体内 Self → HirSelfType`、`inClassBody > 0` | 注释 + 标识符 | ✅ |
| Elaborator.kt:1897 | `"Self 只在类型类体内有意义（…TS-25）"` | **诊断文案**（码 `E-SELF-OUTSIDE-CLASS` 见 §3.1） | ✅ 术语 |
| Elaborator.kt:1969/1984 | `…「单个类型变量 : 类型类」…`、`"…是具体类型而非类型类（…）"` | 注释 + **诊断文案** | ✅ 术语（码名 E-TC-BOUNDARY 保留，缓慢改） |
| Elaborator.kt:1990 | `"类型 " + … + " 不满足类型类 " + cn + …` | **诊断文案** | ✅ 术语 |
| Elaborator.kt:2003-2013 | `satisfiesCheck(…, className: String)`、`classes`/`classDecls` | 标识符名 | ✅ |
| Elaborator.kt:2042 | `inClassBody++` | 标识符 | ✅ |
| Elaborator.kt:213/2225 等 | 注释「类型类声明索引」「约束（类型类名）解析结论」 | 注释术语 | ✅ |

> Elaborator 是单个文件内最大簇：`ClassDecl`×9、`HirClassDecl`×4、`inClassBody`/`className`/`classCycleCheck`/`classDecls` 等私有名若干，全部为**标识符替换**（编译级机械改名，无语义变化）。`satisfiesCheck` 的参数名 `className` 连同 e2e 断言串「Ring」类无影响。

### 1.6 JSON 发射（schema 兼容决策点 → 见 §3.2）

| 文件:行 | 原文 | 身份 | 需改 |
|---|---|---|---|
| json/HirJson.kt:16-17 | `import … HirClassDecl / HirClassConst` | import | ✅ |
| json/HirJson.kt:219-220 | `is HirClassDecl -> obj { … head(n, "HirClassDecl") }` | **JSON 节点类型名（产物 schema 字段）** | ⚠️ 决策点 → §3.2 |
| json/HirJson.kt:229-230 | `is HirClassConst -> obj{ … head(n, "HirClassConst") }` | 同上 | ⚠️ 同上 |
| json/HirJson.kt:129 | `const val SCHEMA = "sublimation/hir@v2"` | schema 版本串 | ✅ → `@v3`（若节点名改，必须递增） |
| json/HirJson.kt:（含 ClassDecl 的 3 处） | `when` 分派 + 字段读 | 类型引用 | ✅ |

### 1.7 Kotlin 自身关键字（**绝对不动**——只统计、证明已甄别）

| 类别 | 量级 | 例 |
|---|---|---|
| `data class` | ~60 | `data class ClassDecl(`、`data class SymbolEntry(` |
| `enum class` | 3 | `enum class SymbolKind`、`enum class PatternMode` |
| `private class` | 1 | `private class Elaborator(` |
| `sealed class`/`abstract class` | 0 | — |
| `import …class…`? | 无 class 关键字出现于 import 名 | — |
| main 308 行 `[cC]lass` 总命中 − §1.1-1.6 约 95 行本语言义 | **≈213 行为 Kotlin 关键字** | 均为 `data class X` 形态 |

> 甄别方法：`^s*(private|internal|data|sealed|enum|abstract|open)*s*classs+[A-Z]` 一律 Kotlin；其余（`Kind.CLASS`、`ClassDecl`、`"…class…"` 串、`.subl` 源、中文「类型类」）一律本语言。

### 1.8 测试层（frontend/src/test）

| 文件:行 | 原文 | 身份 | 需改 |
|---|---|---|---|
| lexer/LexerTest.kt:220 | `val expected = listOf(Kind.FUN, Kind.CLASS, …)` + 输入串含 `class` | 枚举引用 + 探针源码 | ✅ |
| ast/AstTest.kt:39 | `is ClassDecl -> "ClassDecl"` | 类型引用 + **断言串** | ✅ |
| ast/AstTest.kt:122 | `ClassDecl("C", emptyList(), …)` | 构造 | ✅ |
| ast/AstTest.kt:207 | `"FunDecl", "ClassDecl", "StructDecl", …` | 断言清单串 | ✅ |
| hir/HirTest.kt:37 | `is HirClassDecl -> "class"` | 类型引用 + 标签串 | ✅ → `"trait"` |
| hir/HirTest.kt:41 | `is HirClassConst -> "classconst"` | 同上 | ✅ |
| hir/HirTest.kt:149 | `HirClassDecl("C", nid = 2, origin = pos(2))` | 构造 | ✅ |
| json/HirJsonTest.kt:148 | `"HirFile", "HirFunDecl", "HirClassDecl", …` | **JSON 节点清单断言** | ⚠️ 随 §3.2 决策 |
| json/HirJsonTest.kt:410/445/454 | `ref(SymbolKind.CLASS, "Eq"/"List"/"Cls")` | 枚举引用 | ✅ |
| json/HirJsonTest.kt:453 | `HirClassDecl("Cls", …)` | 构造 | ✅ |
| e2e/E2ERegressionTest.kt:278 | `"HirFunDecl", "HirClassDecl", "HirStructDecl"` | JSON 节点清单断言 | ⚠️ 同上 |
| parser/ParserTest.kt:112/318/852 | `… as ClassDecl` | 类型强转 | ✅ |
| ParserTest.kt:849/989 | `it.message.contains("class 不得携带状态")` | **断言文案串** | ✅ 必须随诊断文案同步，否则红 |
| ParserTest.kt:515/888 | 测试名「无类型类 impl 已废除」「TS-6 类型类体禁字段与嵌套类型」 | 测试名（术语） | ✅ 建议改 |
| ParserTest.kt:987 | 注释「类型类体（既有形态不回归）」 | 注释 | ✅ |
| semantic/ExtensionSemanticsTest.kt:5/248 | import + `filterIsInstance<HirClassDecl>()` | 类型引用 | ✅ |
| ExtensionSemanticsTest.kt:174 | `…contains("Ring"), "清单标注来源类型类…"` | 断言文案串 | ✅ 术语 |
| ExtensionSemanticsTest.kt:240/246 | `contains("E-SELF-OUTSIDE-CLASS")`、`"class 体内 Self 合法"` | 码名断言（保留）+ 说明串 | ⚠️ 码名 §3.1 |
| semantic/ClassInheritanceAndRecursionTest.kt | **测试类名/文件名**（术语） | 标识符 | ✅ 建议改（`TraitInheritanceAndRecursionTest`） |

> test 102 行 `[cC]lass` 命中 − 上述约 30 行本语言义 = ≈72 行 Kotlin 关键字（`class XxxTest`、`import` 等），不动。

### 1.9 e2e 样例（frontend/src/test/resources/e2e）

**9 处 `class` 声明（全部要改 → `trait`）**:

| 文件:行 | 原文 | 说明 |
|---|---|---|
| core/number/num.subl:13 | `class Num { }` | 注释 12 行自述「保留 class Num 作标记声明」 |
| core/collection/tuple.subl:11 | `class Tuple {` | — |
| core/collection/typeclass.subl:12/20/27 | `class Functor[F[_]] {` / `class Foldable[F[_]] {` / `class Monad[F[_]] : Functor[F] {` | 继承形态：`trait Monad[F[_]] : Functor[F]` |
| examples/类型类.subl:13/27 | `class Show[T] { fun show(): Str }` / `class Boast {` | 文件名本身为中文术语 |
| examples/高阶类型.subl:12 | `class Functor[F[_]] {` | — |
| examples/特性巡礼.subl:75 | `class Named {` | — |

**注释/术语（约 11 行）**:

| 文件:行 | 原文 | 需改 |
|---|---|---|
| examples/类型类.subl:2/12 | `// 特性：类型类（class ≈ trait / 扩展函数实现…）`、`// class 声明接口（Haskell 拼写、Rust 机制）` | ✅ 术语+关键字 |
| core/collection/typeclass.subl:1/3 | 标题注释「类型类：Functor/Foldable/Monad」、「类型类用 class 声明（成员无体=抽象签名，TS-6）；实例改以扩展函数…」 | ✅ |
| core/collection/{set,optional,list}.subl:2 | `// 依赖：本模块（typeclass 的类型类…）` | ✅ 术语 |
| core/number/num.subl:12 | `// 本样本保留 class Num 作标记声明…` | ✅ |
| core/async/{async,flow,mutex,channel}.subl:4 | `// 对象方法（运行时 class 注入…）` | ⚠️ **建议保留**：这里 class 是**运行时 OOP 类**概念（方法注入），不是本语言的类型类关键字——改了反而编造语义。若要术语洁癖，改「运行时类注入」即可，零行为影响 |

> **文件名**:`examples/类型类.subl`、`core/collection/typeclass.subl` 含术语——改名是**用户可见行为**（e2e 清单/路径引用要同步，属 QA 范畴）。**建议改**：`类型类.subl` → `特质.subl`、`typeclass.subl` → `traits.subl`，但必须在 e2e 驱动（E2ERegressionTest 的样例清单）同步修改后一起提交，否则落盘即红。

## 2. 文档层盘点

### 2.1 形式化文法.md（12 处）——活规范，改

| 行 | 原文 | 需改 |
|---|---|---|
| 78 | `L-9【硬关键字】::= "fun" \| "class" \| "struct" …` | ✅ `"class"` → `"trait"` |
| 174/175 | 入口分派两条：`fun class struct enum impl type def`、`@x fun` / `@x struct` / `@x class` 放行 | ✅ |
| 219 | `S-9【类型类声明】::= 【注解】 "class" 【名字】【理论参数】?` | ✅ 产生式 + S-9 标题改「特质声明」 |
| 645/655 | `## A.2 class 体收紧（S-44…）`、`class 体内 Self 为隐式类型名` | ✅ |
| 677 | `\| D-23 \| class 体成员判定… \| ADR-23、卷 TS-6 \|` | ✅（表内文字） |
| 696/707-709 | P-9 修订段：`…遇入口边界（fun/struct/class…）`、`AST 节点 ClassConst 与 ClassDecl.constants 字段移除…`、`class 体内遇 val 即报 E-VAL-REMOVED（class 不得携带状态…）` | ✅ 但**属历史修订记录**：建议正文改 + 段首追加一行「本段为 P-9 修订记录，其中 class 按新关键字读作 trait」 |

> 判据：文法卷是**现行有效规范**（被 S-2/S-3 纪律引用），class→trait 必须落盘；带「修订记录/裁决追溯」性质的段落保留原文 + 追加读法注记（而非抹掉历史）。

### 2.2 类型系统卷-TS-v1.md（29 处）——活规范，改

| 类别 | 行 | 处理 |
|---|---|---|
| 术语表 | 20/21/24/25「类型类（type class）…」 | ✅ → 「特质」;`type class` → `trait` |
| 分则 TS-2/5/6/8/17/17a/22/23/24/25/26/29 | 14/35-46/85-91/128-140/152-170/195 等 | ✅ 正文术语 + 代码例 `class Functor {` |
| 附录映射表 | 164-170（`impl Functor for List…` → `…类型类成员名…`） | ✅ 术语 |
| 诊断索引表 | 195 `E-SELF-OUTSIDE-CLASS \| Self 出现在 class 体外` | ⚠️ 码名不改（§3.1），**例栏文字随术语** |
| 卷首 | 4「…本卷条款优先于旧「class / struct / impl 三要素」…」 | ✅ → 「trait / struct」 |

> TS 卷与 ST 卷是同源活规范；ST-v1 §2 已把方向写死（见 2.3），TS 卷应随之全文同步而非保留双术语。

### 2.3 子类型与变构性卷-ST-v1.md（8 处）——P-10 总纲，只需清理残留

| 行 | 原文 | 处理 |
|---|---|---|
| 16-24 | `## 2 关键字：class → trait 🟢`「类型类声明关键字由 class 改为 trait…连带：…术语统一为「特质」」 | **保留**（这是改名裁决本体，这就是历史决策的正确形态） |
| 4/111/117/170 | 残留「类型类」：`TS 卷管「类型类/扩展函数…」`、`…类型类约束不再需要独立记法…`、`E-TC-BOUNDARY（「约束是具体类型而非类型类」）` | ✅ 同步「特质」（约束条款本身是 ST-3 的论述，术语需一致） |

> ST 卷的正确姿势 = 它已经声明了改名；盘点只需钉出**尚未同步的 5-6 个残留点**。

### 2.4 specification/README.md（5 处）——已含全局读法声明，基本不动

| 行 | 处理 |
|---|---|---|
| 61-63（P-8/P-9/P-10 三行） | ✅ 改 P-8 行的「二要素（class + 类型声明）」为「trait + 类型声明」?——**不改**：P-8 行记录的是**当时拍板**，67 行已全局声明读法 |
| 65/67 | 「P-8 的「class 二要素」中 class 已改名 trait」「P-9 中「class」一律读作 P-10 后的「trait」（逐字替换尚未执行，见 `码名迁移台账.md`）」 | ⚠️ **改名完成后**：67 行的「逐字替换尚未执行」须更新为「已执行」+ 台账状态勾销 |

### 2.5 ADR-*.md（32 篇，19 篇命中 class/类型类）——**不改，明确表态**

**我的表态：历史 ADR 一律不改。** 理由（三条，独立于队长倾向）：
1. **功能**：ADR 的唯一职责是固化「某时刻面对什么信息、做了什么决定」。改后旧 ADR 与新现状不符**正是它的工作产物**——篡改它会让「当时为什么这么决定」不可复核（例如 ADR-23/25 用 class 表述 P-8 裁决；抹掉 class 就无法解释 ST 卷后来为什么要改名）。
2. **机制已存在**：spec README:65/67 已建立**全局读法声明**（P-8/P-9 的 class 读作 trait）——这比逐篇改 19 个 ADR 更可持续，且它是仲裁链上的一环（P-10 > P-8 的冲突解决规则）。
3. **成本/风险**：19 篇 × 平均 5 行的机械替换=纯体力活，却引入把**决策原文改错**的风险（例如把「class 二要素」语境里的 class 误替换为 struct 语义无关词）。

**代替做法（一处落点）**：改名任务交付时新增一篇 `ADR-33-class更名trait.md`（或挂 P-10 计划段），内容：①声明 P-10 起 `class`→`trait`；②声明旧 ADR/旧日志中的 class 应读作 trait;③记录 JSON schema v2→v3 与 `SCHEMA` 串的迁移（§3.2）;④勾销 `码名迁移台账.md` 的 #1/#2 行。

### 2.6 documents/log/*.md（5 篇，共 97 行命中）——**append-only，一律不改**

判据是 S-3 §8 的纪律本身（log 只追加）。且 t42-t53 的对账表里大量 E-VAL-REMOVED 文案原样引用了旧文案——改 log = 破坏对账证据链。旧日志里的 class 是**当时的事实**。新名字只出现在今后的 log 段。

## 3. 语义/行为影响面清单

### 3.1 诊断码名（`E-TC-*` 等）——**缓慢改，本任务不许顺手全改**

| 码 | 产出点 | 现状 | 本次改名任务的处理 |
|---|---|---|---|
| `E-TC-BOUNDARY` | Elaborator.kt:1974/1983/2229 | 台账 #3/#4：码名缓慢改 + **语义待 ST-13 重写**（ST 卷 L7 裁决其边界判断作废） | **码名与语义都不在本任务动**；仅改文案中的「类型类」术语（如「约束 X 是具体类型而非类型类」） |
| `E-TC-NOT-SATISFIED` | Elaborator.kt:1989/1992 | 台账待迁 | 同上：文案术语随改，码名留台账 |
| `E-SELF-OUTSIDE-CLASS` | Elaborator.kt:1897 码 + ExtensionSemanticsTest:240 断言 | **台账漏登**（含 CLASS 字样，属 P-10 改名连带） | 建议本任务**只登记**：在 `码名迁移台账.md` `E-TC-*` 明细表追加一行 `E-SELF-OUTSIDE-CLASS → E-SELF-OUTSIDE-TRAIT`，状态「缓慢」；**不改产出点** |
| `E-DUP-DECL` / `E-CYCLE` | Collector:52 / Elaborator:504 | 码名不含 class，语义仍准确 | 不动（台账「已锁定不改」同源理由） |

> **依据**：用户裁决「码名缓慢改」+ 台账规则 2「改名须四处同步」——四个同步点是**以一次完整迁移为单位**的，E-TC 系列的迁移单位含语义重写（等 ST-13），所以本次只做术语层。**注意**：诊断**文案**（含「类型类」字样的 39 行）与码名不同——文案是用户可读的，术语统一属台账 #2「待做」，本次应改。

### 3.2 JSON 产出节点名（schema 兼容性）——**建议：一次改干净 + schema 升 v3，不做旧名别名**

**现状证据**:
- `HirJson.kt:129` envelope 已带 schema 版本串：`SCHEMA = "sublimation/hir@v2"`（第 153 行 `field("schema")` 输出）——**已有版本化机制**，v2 本身就是 P-8/C 时从 v1 升上来的（ADR-24），不是冻结不可变契约。
- 下游消费者：`specification/README.md` D-6/D-7 与 S-2 §3 「首期=编译器全管线…后端与解释器直接消费 HIR；**解释器、LSP、注解处理器暂缓**（2026-11-04）」——**V1 无外部 JSON 消费者**。仓库内唯一的 schema 断言是 `HirJsonTest.kt:148` 与 `E2ERegressionTest.kt:278` 的节点类型清单（内部测试，随改随修）。

**我的判断：一次改干净（`HirClassDecl`→`HirTraitDecl`、`HirClassConst`→`HirTraitConst` 字符串同步），SCHEMA 升 `@v3`，新增 ADR-33 记录。** 理由:
1. **别名是给真实下游买的保险，而这边没有买家**——零外部消费者下保留旧名别名=让一个已死的双名路径永久留在发射器里，后续每处 HIR 维护都要问「用旧名还是新名」，是长期税。
2. **本次改名是纯术语层**（ST §2 明文「语义不变」），不是 schema 语义演进——但节点类型名属于 schema 表面，按 ADR-9 的纪律（schema 变更走新 ADR）正好**立此为据**：v3 的 ADR 就是给未来真正的外部消费者（后端建立之时）看的第一份变更记录。
3. **若日后真出现外部消费者**：以那一版 schema 为冻结点即可，本仓库拥有全部内部断言，回溯成本为零。

> 与队长倾向（保留旧名别名）的差异点：队长担心「破坏 schema 对下游是硬约束」——该前提在本仓库被 D-6/D-7 **证伪**（下游暂缓）。若队长掌握我不知道的外部消费方（如团队外的解释器分支），请推翻本条并按别名方案施工。

### 3.3 现有测试断言——哪些会因提示词变化而红

| 亮度 | 文件:行 | 断言串 | 变化源 |
|---|---|---|---|
| 🔴 必红 | ParserTest.kt:849/989 | `contains("class 不得携带状态")` | 诊断文案（Parser:754）改「trait 不得携带状态」 |
| 🔴 必红 | LexerTest.kt:220 | `listOf(Kind.FUN, Kind.CLASS, …)` + 关键字探针源 | Kind/lexer 关键字改 |
| 🔴 必红 | AstTest.kt:39/122/207 | `"ClassDecl"` 清单与构造 | AST 节点改名 |
| 🔴 必红 | HirTest.kt:37/41/149 | `"class"`/`"classconst"` 标签与 `HirClassDecl(…)` | HIR 节点改名 |
| 🔴 必红 | HirJsonTest.kt:148 | JSON 节点清单含 `"HirClassDecl"` | JSON 节点名（§3.2） |
| 🔴 必红 | E2ERegressionTest.kt:278 | 同上 | 同上 |
| 🟡 文案红 | ExtensionSemanticsTest.kt:174 | `"清单标注来源类型类…"` 说明串 | 术语 |
| 🟡 文案红 | ExtensionSemanticsTest.kt:246 | `"class 体内 Self 合法…"` 说明串（**注意 240 行的 `E-SELF-OUTSIDE-CLASS` 码名不断言红**——码名保留） | 术语 |
| 🟡 文案红 | 与「类型类」术语断言同行 | （grep 结果：测试层「类型类」5 处，均注释/说明串，无核心断言） | 术语 |
| ⚪ 不红 | ExtensionSemanticsTest.kt:240 | `contains("E-SELF-OUTSIDE-CLASS")` | 码名保留（§3.1） |
| ⚪ 不红 | 各 `E-TC-*` 断言 | 码名 | 同上 |

> **结论**：约 12 处断言在改名后必红（全部为**可预见的机械红**——节点名/清单串，无语义红）。这也是先盘点的价值：这 12 处与 §1.1-1.6 的代码改动可以**点对点同步**，不是探险。

### 3.4 中文术语「类型类」→「特质」

| 层 | 量级 | 说明 |
|---|---|---|
| main 代码注释/诊断文案 | 39 行命中「类型类」 | 全改（含 SymbolTable:84 的 kindLabel「类型类常量」→「特质常量」——这是用户可见诊断串） |
| 样例注释 | 约 8 行 | 同上 |
| 测试 | 5 行（注释/说明串）+ 2 个测试名 | 同上 |
| 文档 | 文法卷/TS 卷/ST 卷残留 | 同上 |
| ADR/log | 不改（§2.5/§2.6） | — |

## 4. 施工顺序建议

按依赖与**隔离转红**原则排序（每步结束给一次可定位的回归）:

| 步 | 内容 | 为何在此序 |
|---|---|---|
| 0 | `码名迁移台账.md` 追加 `E-SELF-OUTSIDE-CLASS` 行 + 新建 ADR-33 草稿（含 schema v3 声明） | 先立据，后施工——台账规则 1 |
| 1 | **Lexer + Kind**:`"trait" to Kind.TRAIT`、`Kind.CLASS`→`TRAIT` | 全管线源头；此时全工程编译必红 |
| 2 | **Parser**:`Kind.TRAIT` 分派、`parseClass`→`parseTrait`、同步集、parseClass 头注释与文案术语 | 依赖 1 |
| 3 | **AST**:`ClassDecl`→`TraitDecl`（含 ast/Decl.kt 文档注释）+ AstPrinter 输出串 | 依赖 2 |
| 4 | **HIR + 符号表**:`HirClassDecl`/`HirClassConst`/`SymbolKind.CLASS`/`CLASS_CONST`/`classes`→`traits`/`classEntry`/`classCycleCheck`/`inClassBody` | 依赖 3;**最大簇** |
| 5 | **语义层引用**:Elaborator/Collector 的 import、`is ClassDecl`、构造点 | 依赖 3+4 |
| 🛑 **回归点 A** | 跑 `:frontend:test`——**预期红**：LexerTest/AstTest/HirTest/HirJsonTest/E2ERegressionTest 的约 12 处断言（§3.3）。**此时任何红若不在该清单内 = 替换错（如误改 Kotlin 关键字），一眼定位** | 隔离「改名错误」与「断言过期」 |
| 6 | **诊断文案术语**:Parser/Elaborator/Collector/SymbolTable 的「类型类」→「特质」、`class 不得携带状态`→`trait 不得携带状态` | 与标识符分开改，避免两类红混在一起 |
| 7 | **测试断言同步**:§3.3 的 12 处 + 测试名/类名 | 5、6 之后清单已钉死 |
| 8 | **JSON 节点名 + schema v3**:`head(n, "HirTraitDecl")`、`SCHEMA`@v3 | 放后面——schema 是对外表面，最后一次性翻面 |
| 9 | **样例**:9 处声明 + 注释 + 两文件名（e2e 清单同步） | 8 之后 JSON 断言已绿，e2e 才能跑 |
| 🛑 **回归点 B** | 全量 `:frontend:test` + e2e——**应全绿**;若红= 步骤 6-9 漏改，逐项对 §1.9/§3.3 | — |
| 10 | **文档**:文法卷/TS 卷/ST 卷/台账勾销/spec README:67 更新 + ADR-33 定稿 | 最后；文档不改代码 |

**最易大范围转红的一步 = 第 4 步（HIR+符号表）**:`HirClassDecl`×12、`SymbolKind.CLASS`×多、Elaborator 私有名（`classes`/`className`/`classDecls`）密集耦合——它牵动 3 个测试套件且与 SymbolTable/Elaborator 的表查询逻辑交织（`valueOrTypeTable`/`lookupType` 的 `classes` 分支）。**但**它不是最危险的一步：Kotlin 编译器会一次性报出全部引用错（类型安全网）。**真正危险的是第 6 步**——文案替换是串字面量修改，编译器**不检查**，漏改/误改只在 e2e 与断言红点暴露，且与第 7 步的断言修同步进行时最易互相掩盖。故建议在**第 5 步之后、第 6 步之前**插入回归点 A（此时断言红都是节点改名引起的、可逐条对表），让第 6 步只剩纯文案层 diffs。

## 附：本盘点未覆盖的（诚实边界）

- 未逐行展开 Elaborator 的全部 `inClassBody`/`className` 私有名出现（约 8 处，列于 §1.5 代表行，机械改名时以 IDE rename 处理）。
- 未盘点 `frontend/build/` 生成物（构建产物，重编译即弃）。
- 旧实现 `F:KotlinProjectsublimation` 不在范围（字典点查角色，按纪律不联动改名）。
- 未跑 Gradle（槽位归 t51）;本表全部数字来自只读 grep，样例清单 9 处与 qa 的「约 9 处」吻合。
