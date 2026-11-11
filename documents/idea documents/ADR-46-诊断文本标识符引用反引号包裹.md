# ADR-46 诊断文本标识符引用：一律反引号包裹（ident() 助手）

**日期**：2026-11-08（t69，用户 17:1x 裁决）
**状态**：已落（诊断规范 D2）

## 一、决策

凡诊断消息中**出现的标识符**一律反引号包裹，经由统一助手 `ident(name: String): String`
（`frontend/src/main/kotlin/sublimation/frontend/diag/Ident.kt`）。反引号不是装饰，解决三件事：
①同名异形可辨（` ` 与 `  ` 裸文本肉眼相同）；②输出可原样复制回源码；③与普通叙述文字区分。

转义规则（6 条，严格转义、诊断不得跨行）：

| # | 名字内字符 | 输出形态 |
|---|---|---|
| E1 | `\\` | `\\\\`（先行，防双重转义互相感染） |
| E2 | `"` | `\\\"`（t68 放开后可含引号，先行就绪） |
| E3 | 内嵌反引号 ```` | `\\``（转义后可复制回源码） |
| E4 | `\\n` / `\\r` | 字面 \\n / \\r |
| E5 | `\\t` | 字面 \\t |
| E6 | 其余 | 原样 |

转义 E4/E5 的根本理由：**诊断不得跨行**——t68 B1 放开裸换行名后，名字可含真换行；诊断输出必须单行。

## 二、适用边界（什么不算标识符，不包反引号）

- 类型渲染（`render(类型)`、实参/形参类型串）：类型有自己的字面形态。
- 命题渲染（`prover.render`、∃ 量词约束名）：命题语法，非标识符引用。
- 签名键/不符清单（`sigKey`、`miss` 列表如 "Eq.==(other: Nat): Bool"）：签名字面。
- 中缀算符（`op`）、self/Self/`_` 等保留字、代码示例子句（`var x : @mut T`）。
- 计数（`.size`）、`$index`、内部合成标签（`syntheticT("函数引用" + name)`）。

## 三、覆盖面（105 处 ident() 站点，97 个 diag 调用点全在 semantic 包）

`Elaborator` 52 / `TypeInfer` 27 / `Collector` 10 / `ProofChecker` 6 / `NameResolver` 3 /
`VarianceInfer` 4 / `PropNormalizer` 2 / `Semantic.kt` H-SHADOW 钩子 1。

## 四、防回退

`DiagIdentConventionTest` 12 例：管线层 6 条（E-DUP-DECL / E-UNBOUND-NAME / E-TYPE-MISMATCH /
E-DEFAULT-TYPE / E-VARARG-LAST / H-SHADOW 断言反引号文本）+ 助手层 6 条（普通名、反引号往返、
空格名、控制字符单行、引号转义、成对反引号）。E-FIXITY-CONFLICT 码在当前码集不存在（grep 零命中，略过）。
