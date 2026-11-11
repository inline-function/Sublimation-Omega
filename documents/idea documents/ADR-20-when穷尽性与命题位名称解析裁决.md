# ADR-20：when 穷尽性/密封判定 与 命题位名称解析（t22）

| 字段 | 值 |
|---|---|
| 编号 | ADR-20 |
| 主题 | B3 when 穷尽性/密封判定 + B6 命题位名称解析（err 码族沿用 E-NON-SEALED-MATCH / W-NON-EXHAUSTIVE / E-UNBOUND-NAME） |
| 任务 | t22（t21 归因表 B3/B6） |
| 依赖条款 | S-27/S-28 when 条款、ADR-12 §2.6（var 位静默/函数返回位报错口径）、ADR-15 prelude 构造子表、ADR-17 调用点泛型实例化、D-9 信息保留 |
| 上游 | t13 语义修复、t18 调用点实例化、t19 stdlib 名字登记 |
| 下游 | P-8 类型系统改向（穷尽性与命题面在新体系依然存在） |

## §一 B3：密封主题的 ctor 覆盖判定

**根因**：Elaborator.whenExpr 对密封主题（用户 enum / prelude 密封容器 / Bool）只按「有无 else」
二值判定，不做构造子覆盖合并——密封枚举主题的齐整分支被误判为非穷尽（值位 W-NON-EXHAUSTIVE、
函数返回位级联 E-NON-SEALED-MATCH）；而臂顶层的 `true`/`false` 又被解析层按 PatBind 送入，被全吸收，
真非穷尽的反证方向同时失真（双向证据均缺）。

**裁决与修法**（要点：判定只改 whenExpr 一处分流 + 一组类级助手，不动 NameResolver/TypeInfer 纪律）：
1. 取主题命名类型 `subjName`：用户 enum → `enumDecls` 构造子清单；prelude 密封容器 →
   `symbols.preludeEnumCtorNames`（Optional/Result/List）；Bool → `[true, false]`。
2. `whenCovered(arms, ctors, subjName, subjectT)`：守卫分支不吸收（旧 WhenCheck.bindingCovered
   同口径）；任一无守卫臂是全吸收形（HirPatBind/HirPatElse）即覆盖；否则逐构造子
   `ctorCovered` 联合判定。
3. `ctorCovered`：该构造子的各分支实参集里，任一分支实参全吸收（绑定/else）即覆盖；否则
   逐字段位联合覆盖——字段类型为密封枚举时（用户 enum 字段取 `Ctor.fields`；prelude ctor
   形参位代入主题实参：`List[Nat]` 的 Cons 第二字段位解为 `List` 密封表），该位各分支的 ctor
   模式名合并须盖全字段密封构造子。零参值字面量引用密封构造子的形态（嵌套 `Some(None)` 的
   内层 None）按 `patCtorRefName` 归一计入——嵌套解构覆盖（Flatten）为超出旧实现的增量。
4. Bool 字面量模式（D-PAT-BOOL 脱糖）：解析层把臂顶层 `true`/`false` 产出 PatBind；语义层
   于 `pattern()` TOP 位与 `bareNamePattern()` 双入口脱糖为 `HirPatLit(HirNameRef(...))`，
   覆盖判定经 `boolLitName` 按 true/false 两值计入（⊤/⊥ 同形）。SLOT 位不受影响（槽位裸名
   仍按比较/绑定裁定）。
5. 分流沿用 ADR-12 §2.6：`hasElse || covered` 落 `HirWhen.exhaustive`；值位仅 W-NON-EXHAUSTIVE，
   函数返回位 E-NON-SEALED-MATCH；密封闭合取真 joined 类型、未密封取 `(未密封)` 固定语义不变。

**旧实现点查**（D-1 字典点查，WhenCheck 族）：旧 bindingCovered「守卫不吸收、PTop 全吸收、
密封枚举 ctor 齐」三口径已全部复刻；本裁决扩展至 prelude 密封容器、Bool 两值与嵌套字段位联合
覆盖（旧实现无嵌套覆盖能力）。

**双向证据**（CLI 探针 + 单测 + e2e）：
- 真穷尽不报：`enum E {A,B}` 齐 → 无 W/E 且 joined 取真类型（`HirWhen.exhaustive=true`，
  WhenExhaustivenessTest.sealedEnumTrueExhaustiveNoReport）；Bool true/false 齐 → 无 W/E；
  `Optional` Some/None 齐 → 无 W/E；嵌套 `Some(Some(y))`/`Some(None)`/`None` 三臂 → 无 W/E。
- 真非穷尽必报：缺构造子 → E+W 同报；缺 false → W；缺 None → E；`模式匹配.subl` L15 partial
  缺 Blue → 仅 W（void 位不报错，样本自身注释即为该设计）。

## §二 B6：命题位名称解析

**根因**：NameResolver 的 resolveValue/resolveType 只有一套「报错+占位」通道，命题位（axiom 自由
变量、`InstExpr` 的命题形参位 `(i: Nat)`）按值位口径落 E-UNBOUND-NAME/E-PROP-FORM。

**裁决与修法**（要点：静默开关 + 命题位判定，解析形态完全不动）：
1. NameResolver 新增 `resolveValueQuiet`/`resolveTypeQuiet` 与 `schematicNames { ... }` 块
   （`silentNames` 翻转）：静默通道不落 E-UNBOUND-NAME，返回 null 占位，供命题面使用；
   值面通道行为不变（E-UNBOUND-NAME 口径与码值沿用）。
2. PropNormalizer.typeParamQuiet：命题位类型参数按静默解析，不落错。
3. Elaborator.InstExpr 分支：`calleeName` 命中 `funDecls` 且其 `theory` 形参为 PropParam 时，
   该位实参包入 `resolver.schematicNames { expr(t) }`（命题位供给）；其余实参走值面通道。

**口径**：axiom 的命题自由变量允许（不做值位查名）；绑定变量（量词/形参）须在作用域——
与旧 checker 命题面同口径（字典点查确认）。

## §三 验证与残留

- commandsRun：`:frontend:compileKotlin` 绿；`:frontend:test --rerun-tasks` **321 tests 0 failures**
  （TypeInferTest 38 + 新增 WhenExhaustivenessTest 7 + 其余无回归）。
- e2e：ok 43/68 → 56/68（+13 绿 0 红）；E-NON-SEALED-MATCH 15 → 4、W-NON-EXHAUSTIVE 31 → 5、
  E-UNBOUND-NAME 105 → 78。
- 残留（V1 不追，归其所属域）：list/类型类的 4×E+3×W 为「内层 when 于构造子字段绑定」/「impl 隐式
  self 类型未落」——是绑定类型注入与 impl 接收者域，非覆盖判定本身；模式匹配 L17 W 为样本设计行为
  （真非穷尽必报的反证）。
- 事故记录：t22 中误用 read 窗口回写整文件致 TypeInferTest.kt 截断，已按 javap 字节码全文重建
  （38 测试与 305 轮口径对齐，见 log 事故段）。

