# ADR-57：N2 @ref 语义层四规则（可写性 / 子类型 / 拷贝分派 / 命题待遇）

| 状态 | 决议 | 落点 |
|---|---|---|
| 已落地（t98 阶段 A，2026-11-10） | 规则 1-3 为**静态类型语义**（error 级，与既有 @mut 同位）;规则 4 命题待遇为**闸类**（warn 报告态，单点 severity 待批）| Elaborator / TypeInfer / PropNormalizer |

## 依据

- R2 落地计划 §二：语义四规则 + σ 双视图分派机制可作模板（**复用 VarianceInfer 的分派结构，未重造**）。
- 根文档「旧工程参考-副作用与可变性控制」:59-163（只读参考）：可写性 :59-63、共享 :110-140、深拷 :89-108/158-163、命题待遇 :142。
- t95（ADR-52 §二.6 标注的零语义中间态「@ref 可拆卸视图包装」）→ **本任务取代该中间态**：RefTypeInertTest 的 t95 中间态断言（不可变 → @ref @mut 放行）相应改为 t98 双态断言。

## 规则 1 可写性（E-IMMUT-ASSIGN，error）

- `a.n = 1` 需要字段 `@mut` 声明 **且** 接收者为可写视图：`@mut T` 或 `@ref @mut T`（写穿透共享引用，根文档 :113-117）。
- `@ref T` / `T` 只读：`d.n = 1` 落 E-IMMUT-ASSIGN（根文档 :122）。
- 实现：`TypeInfer.isWritableView`（@ref 层透传、@mut 包内层不得为 @ref——`@mut @ref T` 不可写）替代 fieldWriteOk 原先的 `recvT is HirMutType` 判据;
  fieldWriteOk 的结构体名解包同时剥 @ref 视图层（否则 @ref 接收者的字段登记落空 ⇒ 闸被绕过——探针 p3 抓到的实装漏洞）。

## 规则 2 子类型（sub 视图规则）

```
got is HirRefType && want is HirRefType -> sub(got.inner, want.inner)   // 内层协变
got is HirRefType -> sub(got.inner, want)                                // @ref T <: T（深拷 ?:161）
want is HirRefType -> sub(got, want.inner)                               // T <: @ref T（共享 ?:150）
```

- @mut 视图口径经内层沿用（@mut T <: @ref T 共享只读 :120;@ref @mut T <: @mut T 深拷）。
- **健全性填补**：`T <: @ref @mut T` 落 false（不可变值发可写引用不安全；根文档未给此格）——造格路径（borrowView）同口径：
  want 含 @ref 时剥 @ref 层后保留 @mut 视图不变性（stripRefs），避免旧 demoteView 把 @ref @mut 整体看穿。

## 规则 3 拷贝分派（类型层判定 + D-REF-DISPATCH 留痕）

矩阵（根文档 :89-163 八格 + 未给定格按不安全侧填）：

| got \ want | T | @mut T | @ref T | @ref @mut T |
|---|---|---|---|---|
| **T** | 共享 :155 | 深拷 :97 | 共享 :150 | **拒绝** |
| **@mut T** | 深拷 :89 | 深拷 :104 | 共享 :120 | 共享 :113 |
| **@ref T** | 深拷 :161 | 深拷 | 共享 :126 | **拒绝** |
| **@ref @mut T** | 深拷 | 深拷 | 共享 :136 | 共享 :131 |

两拒绝格（`T → @ref @mut T` / `@ref T → @ref @mut T`）为根文档未给格、按不安全侧：类型层落 E-TYPE-MISMATCH（borrowView 剥 @ref 层后保留 @mut 不变性）——**t99 实测确认并修正本表**（原标「深拷*」:copyDispatch 侧记深拷、类型层先拒）。矩阵正式收口见 `documents/赋值语义矩阵.md`（R2 §三，8 格规则 + 文档表格），t98 提供判定函数与留痕。

- 前端**只做类型层判定 + HIR 备注留痕**（实际深拷随后端运行时，R2 §三）。留痕 `D-REF-DISPATCH`（SUPPLEMENT 级 notes）**仅在 @ref 涉及时落**——@mut/T→T 侧产物零改动（回归保护）。
- **实装陷阱（nid 幂等）**：assign 分支 `etype(value)` 每次调用落新类型节点并消耗 nid——在既有调用之外重复调用会使全文件 nid 序列整体漂移（68 样本 3 份出现未归因 diff）。修复：复用单次 `valueT` 局部量，`assignTypes` 与分派判定共用。度量结论：**`etype` 得当不可重入用**，后续任何树改须复用既有类型局部量。

## 规则 4 命题待遇（E-PROP-VIEW，**warn 报告态**）

- 根文档 :142「引用与可变一样不可预测，因此在上下文中享有和可变相同的待遇」:
  - 不进上下文系统：**类型事实注入（t65）按 @mut 既有口径**处理 @ref（demoteView 剥层后注入）——两者同等待遇，未引入新差;
  - 不承认相等命题：相等命题（`=` / `≠`）两侧**直接名字项**的类型含 @ref（或对称的 @mut）视图 ⇒ 落 E-PROP-VIEW warn（PropNormalizer.prop 单点，经名字→类型查表 + render 回调注入）。
- 范围限定（V1）：仅直接名字项——字段链命题项（`d.n = ...`）留后续形态扩展;@mut 对称为「同等待遇」主动选择（非根文档例证，报队长裁决是否 phase B）。
- **阶段 A 度量**：68 样本 E-PROP-VIEW fire = 0（样本的 @mut/相等命题分布不触该形态）;探针 p9 fire=1（warn 不阻断）;T98 测试锁定（ref 项落 warn / plain 项静默 / @mut 项同等待遇）。

## 验证（t98 阶段 A 收口）

- 探针矩阵 **14/14**（根文档 :59-63 两形态实测 / @ref 只读两路 / @ref @mut 写透 / 子类型四路 + 健全性拒绝 / 命题 warn / σ 双视图回归 / 分派留痕）。
- 全量 **872/0**（833 基线 + T96×10 + T97×13 + T94 I-4×1 + T98×15）;e2e **65/68** 三冻结红逐字不变;68 样本 JSON vs out/t97B **SAME 68/68 零差异**。
- 测试夹具冲击（队长 D-8 纪律）:RefTypeInertTest.t95_hir_2 的零语义中间态断言被 t98 真规则取代（ADR-52 §二.6 预告的替换点）+2 个 PropNormalizer 测试构造位补参数。

## 待批（队长）

1. 规则 4 是否翻 **error 硬拒**（phase B）;
2. 规则 4 的 @mut 对称口径是否保留（现行与 @ref 同等待遇，根文档 :142）;
3. `T <: @ref @mut T` 拒绝（矩阵未给格）与 `@mut @ref T` 不可写的健全性填法是否有异议。

