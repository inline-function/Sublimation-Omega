# ADR-18 stdlib 名字登记边界与 @root 无体语义裁决（t19）

- 状态：已落地（实现 + 测试通过）
- 日期：2026-11-05
- 范围：t17 归因表 B1（stdlib 函数名单缺口）与 B2（@root 无体/占位函数误判）——两者均小改动、
  单文件期名字层补齐，不动推导主框架
- 对齐依据：P-7 旧 compiler（Types.kt builtinPure/builtinImpure 表 + ROOT_WHITELIST 与
  Diffusion.kt 的 typeLooseEq Nothing v2.1 底类型口径）；core/ 模块声明按需点查未通读

## 一、背景（t17 残留）

- B1：strTrim/startsWith/strReplace/strIndexOf/mathMin/mathRound/jsonParse/jsonGet 等 stdlib 函数在
  名字层不可解析 → E-UNBOUND-NAME。诊断面有两层：TypeInfer.builtinSig（t8 已移植旧内建表，
  含 str/math/json 全族签名）与 SymbolTable.BUILTIN_FUNS（名字层登记——此前只到 strUpper/strLower，
  strTrim 之后整段漏带，故 resolveValue 落不到 BUILTIN kind）。另一族是 core 模块声明的普通
  自由函数 sum/range/summation——旧管线靠可见 core 模块（prelude 预加载）解析，单文件期没有该通道。
- B2：encoding/net/regex 的占位函数（= panic() 体）被判「函数体是 Nothing，两边类型不一致」。
  旧实现 v2.1 把 Nothing 当底类型（typeLooseEq 两侧 isNothing → true；发散/不可达值与任意
  声明返回相容），新前端 adaptOk 漏了这条。@root 无体声明（探针：@root fun nativeOp(x: Nat): Nat
  + 调用位）经实测本就解析通过（body 为 null 时跳过体核对），V1 不再引入旧 ROOT_WHITELIST 门禁
  （旧白名单是 codegen @root 内建特判表，本前端不做字典分派）。

## 二、裁决

### 2.1 BUILTIN_FUNS 名字层补齐（旧内建表键全集）

BUILTIN_FUNS 补齐旧 builtinPure 的 str/math/json 族键（与 builtinSig 一一对应，禁止只登记名字
而不给签名——那会让调用点走 lookupValue 兜底得 synthetic，丢失实参核对）：strTrim、startsWith、
endsWith、strReplace、strSplit、strIndexOf、mathMin、mathMax、mathClamp、mathRound、mathSin、
mathCos、mathTan、jsonParse、jsonGet、jsonAt、jsonLen、jsonStr、jsonNum、jsonBool、jsonIsNull、
jsonIsArr、jsonToStr、jsonToList。

### 2.2 PRELUDE_FUNS：core 模块自由函数的签名兜底

core 模块以普通 fun 声明、旧管线靠 prelude 挂载可见的函数，单文件期按 PRELUDE_FUNS 表兜底（同 t14
PRELUDE_CTORS 的位置口径：名字层兜底，用户自声明 fun 优先）。全集以 68 样本实际需要 + core 真实
声明为限（P-7 字典点查，勿臆造）：

| 名字 | 签名 | core 声明来源 |
| --- | --- | --- |
| range | (Nat, Nat) => List[Nat] | core/collection/list.subl 43 |
| sum | (List[Nat]) => Nat | core/collection/list.subl 187 |
| summation | (Nat, Nat) => Nat | core/math/math.subl 45 |

调用点签名经 Elaborator.preludeFunSig 转为 HIR 类型形态（复用 PreludeField 规格与
preludeFieldType 的 root 应用 params 形态），走 callFun 常规核对——签名真的参与实参与返回核对，
不是名字通过即放行（sum(strTrim(...)) 落 E-TYPE-MISMATCH「实参 1 Str 与形参 List[Nat] 不符」）。

### 2.3 Nothing 底类型（B2）

adaptOk 增 Nothing 底类型规则（零实参具名 Nothing，两侧任一命中即相容——旧 typeLooseEq v2.1
对称口径；synthetic 宽容之后、typeEq 之前）。效应：= panic() 占位体与任意声明返回相容；
encoding/net/regex 三样本的「函数体是 Nothing」族清零。funBody 其它路径（E-MISSING-RETURN /
E-NON-SEALED-MATCH / E-RETURN-TYPE）不受影响。

### 2.4 @root 语义（V1 口径）

@root 为外部实现约定标记：无体声明允许（体核对本就跳过 null 体）、返回类型取声明签名、调用点
按声明签名核对（探针 @root fun nativeOp(x: Nat): Nat + nativeOp(1) 通过）。旧 ROOT_WHITELIST 门禁
（E-ROOT-NOT-ALLOWED/E-FUN-NO-BODY）属 codegen 特判绑定层，本前端 V1 不做字典分派，不引入；
样本内的 @root 白名单内建（print/readFile 等）由 BUILTIN_FUNS/builtinSig 覆盖其名字层与签名。

## 三、逐条对账

B1（E-UNBOUND-NAME 计数，前 → 后）：

| 样本 | 前 | 后 | 说明 |
| --- | --- | --- | --- |
| core/math/math.subl | 2 | 0 | sum/range 登记 |
| core/math/inequality.subl | 0 | 0 | 无缺口（仅 H-ARITH-PREC 提示） |
| core/string/string.subl | 0 | 0 | t17 已清 |
| core/json/json.subl | 0 | 0 | t17 已清 |
| core/math/series.subl | 5 | 2 | summation ×3 清；余 2 为定理命题内自由变量 s（命题面，非 stdlib 名字层） |
| examples/字符串与插值.subl | 9 | 0 | strTrim/startsWith/endsWith/strReplace/strIndexOf/mathMin/mathClamp/mathRound |
| examples/日常-文本处理.subl | 4 | 0 | strTrim/strReplace/startsWith/strIndexOf |
| examples/JSON内建.subl | 13 | 0 | jsonParse/jsonStr/jsonNum/jsonIsNull/jsonIsArr/jsonAt/jsonLen/jsonGet 登记 |

B2（E-TYPE-MISMATCH 计数，前 → 后）：

| 样本 | 前 | 后 |
| --- | --- | --- |
| core/encoding/encoding.subl | 4 | 0 |
| core/net/net.subl | 3 | 0 |
| core/regex/regex.subl | 4 | 0 |

全局：E-UNBOUND-NAME 153 → 122、E-TYPE-MISMATCH 37 → 26；e2e ok=true **31/68 → 37/68**（新增 passing：
encoding、net、regex、math、字符串与插值、日常-文本处理；任务预期 8 样本，series 因定理命题自由
变量 s 未清——非 B1 族，留命题面）。全样本诊断实例总数继续下降（t17 322 基线之上）。

## 四、已知边界

1. series.subl 定理命题里的自由变量 s（2*s 的 s 未声明）属命题面 V1（F6 同族），不在 B1 名字层范围。
2. PRELUDE_FUNS 全集封闭于上述三名字——Tuple/类型类等其它跨模块族仍是单文件既定边界。
3. @root 不做白名单门禁（§2.4）；无体声明的 posts/props 义务核对随同跳过（body==null 路径既有行为）。

## 五、验收

- :frontend:compileKotlin 绿。
- :frontend:test --rerun-tasks **296 tests 0 failures**（新增 TypeInferTest.stdlibNamesAndPanicBodyT19：
  B1 正例 sum(range(1, 10)) 与反例 sum(strTrim(...)) 落实参不配、B2 panic() 体相容；
  E2ERegressionTest 2 全绿、ParserTest 63 / SemanticTest 22 / ProofCheckerTest 49 / TypeInferTest 38 回归全绿）。
- CLI 探针：@root 无体 + 调用位 exit 0；strTrim/startsWith 链 exit 0；jsonParse Optional[Json] exit 0；
  sum(range) exit 0；反例两处落 E-TYPE-MISMATCH。
- 交付物：SymbolTable.kt（BUILTIN_FUNS + PRELUDE_FUNS/preludeFun/preludeFunSpec）、Elaborator.kt
  （preludeFunSig + BUILTIN 分支兜底）、TypeInfer.kt（adaptOk Nothing 底类型）、TypeInferTest.kt、本裁决。
