## 状态
已落地（阶段 B 硬拒，2026-11-10 队长批）：闸以 **error 级硬拒**上线（单点 `termDiag` severity）。
阶段 A（warn 报告态）样本冲击度量 = **0/68**（89 处纯函数直接自递归全部通过两形态判定;range 的 `a+1`
被样本作者 `unchecked` 包裹放行）⇒ 批阶段 B：复验同口径——探针 12/12（fire×4 exit=1 / 放行×8）、
全量 857/0、e2e 65/68、out/t97B 68 份 vs out/t96B SAME 68/68 零差异（fire=0）。
**阶段 B 发现 1 处测试夹具冲击**:SubtypeOrderTest.st06 的 `fun boom(): Nothing = boom()`（零参自递归，
无缩小可能——闸判定正确；夹具本证 Nothing 真底供给而非停机性，改块体 `unchecked return boom()`）。

## 形态落定（新工程）
① 子结构缩小的静态识别:
- `when(xs) { is Cons(_, t) -> f(t) }`——**模式解构槽位**:`t` 经 when-臂模式绑定来自主题 `xs`（形参）。
  来源链登记在「臂级来源栈」上（arm push/pop），**嵌套 when 传递**（t2 ← t ← xs）。接收者 `self`
  同为形参位（扩展函数 `fun List[a].map(...)` 的 `when(self)`)。**方法糖接收者计入首实参位**
  （`t.map(f)` 的 t）——三返回位（方法糖 / 扩展桶直调 / 自由调用）单 pass 全覆盖。
- `f(xs.tail)`——**字段链**:HirField 的 target 链根为形参（或解构槽位）。
  形参本身直传 **不**算缩小（`f(n)` 不缩小——NameRef 无来源链即拒）。
NaN

## 实现要点
- **单 pass**（任务规格:新工程 callExpr 已有 callee + 实参位置信息）:checkTermination 在
  callExpr 的三个 `return HirCall(` 位（方法糖 / 扩展桶直调 / 最终返回）调用。
- **FunFrame 扩展**（retType + name + impure + paramNames):自递归判定须知当前具名函数、
  是否 @impure、原参数名集。**lambda 帧无名**（return-放行帧）⇒ 保守跳过：闭包的创建/传递
  不等于调用;体闭包的自调以延迟调用计，不在直接自递归口径（旧工程同保守口径）。
- **来源登记取 HIR 模式的绑定位**（关键设计决定）:AST 里裸名 `t` 与无参构造子同形
  （`PatCtor("t", [], paren=false)`——「无参构造子与绑定变量同形，语义层裁定」），
  在 AST 层登记会把 `is Nil` 的 Nil 也登成绑定（过度放行漏洞）。故取**语义层裁定后**的
  HIR 模式（HirPatBind 名）登记——同形歧义不再泄漏。
- **unchecked 放行**:uncheckedDepth 计数（UncheckedStmt 进入 ++/退出 --），>0 即跳过——
  同 t96 三路放行之一。口径见 ADR-55 §unchecked 不对称（豁免检查不豁免效应存在）。
- 局部 fun（FunStmt → 同一 funDecl）自动覆盖（内层帧）;**互递归（a→b→a）静态不计**
  ——旧工程同口径，本闸只覆盖直接自递归（登记局限，测试锁定）。

## 阶段开关（单点，同 t96）
```kotlin
private fun termDiag(pos: SourceRange, message: String) = diag.error("E-NON-STRUCTURAL-REC", pos, message)
```
severity 为闸开关：阶段 A=warn 报告态（收档）/ 阶段 B=error 硬拒（队长 2026-11-10 批）。
文案含可编译提示（子结构/数值靠拢/标 @impure 三选一）。

## 形态分析（68 样本，89 处纯函数自递归）
形态分布（HIR 静态扫描）:NameRef 族 47+8+3+21+4+1+1+2+1 处（解构槽位/接收者为主，
如 map/fold/flatMap/size/last/contains/indexOf/find/append/take/drop/slice——全部 ✓①）;
减法形态少量（elementAt/take/drop 的 n-1 ✓②）;range 的 `a+1`（**增**）不缩小，
但样本本身以 `unchecked var rest = range(a + 1, b)` 落笔 ⇒ unchecked 放行 ✓。
⇒ 闸位点数 **0/68**,out/t97 68 份 vs out/t96B **SAME 68/68 零差异**。

## 验证（实测）
- 探针矩阵 12/12（`probe-mut/cases-t97/`）:
  fire×4(参数直传 / 常量实参 / 方法糖接收者不变 / 局部 fun 自递归）、放行×8（解构槽位 / 减法 /
  字段链 / 方法糖接收者 / 嵌套 when / @impure 逃逸 / unchecked / 互递归外）。全 exit=0（阶段 A）。
- 全量 **857/0**（833+T96×10+T97×13+T94 I-4 补充×1）;e2e **65/68** 三冻结红逐字不变。
- 68 样本 gate fires **0**;JSON 零差异（阶段 A HIR/JSON 树零改动）。

## 队长结构审查（t97 反馈）随本任务收口项
- **I-2（事实单源化）**:gateCheckCall 的 impure 判定与文案 label 抽 `CalleeKind(impure, label)`
  单源派生（gateClassify/gateLocalOf）——不再两处各判同一组条件。
- **I-4（unchecked 不对称口径）**:口径定案「**豁免检查，不豁免效应存在**」并落 ADR-55 §unchecked;
  锁定测试:T94 `unchecked体内仍计impure()` + T96/T97 的 unchecked 放行用例。
- **小项**:门区 FQN 统一为 import（HirAnonFun/HirFunType/HirStmt/HirTName）;`val nm/name`
  双名 workaround 收进 `nm?.let { ... }` 非空捕获。
- **I-1（containsImpureCall 与 gateWalkExpr 两套平行遍历）**:本轮不动，轮末结构债（方向:抽共享
  visitor/fold 或事件订阅）——t98+ 仍在这套 walker 上增建。

## 后续
- 阶段 B 批后翻 termDiag severity（单点）。
- 互递归环路若要覆盖需另行形式（调用图环路 + 度量）——旧工程未做，登记。

