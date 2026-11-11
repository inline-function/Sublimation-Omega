# ADR-11 前端编译入口与最小 CLI 实现裁决

| 项 | 值 |
| --- | --- |
| 编号 | ADR-11（承接 ADR-10） |
| 任务 | t11 实现前端编译入口与最小 CLI（S-2 §2） |
| 日期 | 2026-11-05 |
| 状态 | 已落（审查前为提案） |

## 一、背景

S-2 §2 的对外契约：以**单源文件**为处理单位，提供「源代码 → HIR → JSON 文件」的一次
编译 API 与最小调用入口；S-2 §4 的包结构给出末位「编译入口——单文件 → HIR → JSON」。
上层管线四层（lexer t2 / parser t4 / semantic t7 / json t10）已交付，入口层需把四层
串成可调用的整体，并给命令行用户一个子命令风格对齐旧实现（D-1 字典点查）的入口。

旧 cli/Main.kt 的子命令族（parse/tokens/check/compile/run-script/explain）与旗标族
（-p/-A/--target/--prover…）混杂了注解处理器、代码生成与脚本执行——本期全部不适用。

## 二、决策

1. **包与依赖**：入口包 `sublimation.frontend.cli`（S-2 §4 末位），依赖终点包——
   lexer/parser/semantic/json/diag 单向引用，无人依赖它（S-2 §3.1 成立）。
2. **对外一次编译 API**：`compileFrontend(source, unitPath, scriptMode = false)`
   返回 `FrontendOutput(hir, diag, json)`——四层一气呵成，诊断一袋汇总
   （词法 + 语法 + 语义），JSON 文本即产物；mode 由调用方决定，默认工程模式
   （脚本模式对齐 t4 双入口）。
3. **子命令集 = tokens / parse / check / compile**——旧族的前端子集；剔除
   run-script（解释执行属 :interpreter，D-6 暂缓）、compile→JS（后端 :backend-js
   待建）、explain（诊断码解释表为全编译器级，前端期不复制）、-p/-A/--target/
   --prover（注解处理器 / 代码生成 / 证明器开关）。
4. **退出码 0/1/2 对齐旧 CLI**：2 = 用法错误（USAGE → stderr），1 = 存在 ERROR 级
   诊断（N-SEV-BLOCK），0 = 通过。
5. **compile 在 ERROR 下仍写盘**（对旧「语义失败不生成」的偏差）：D-9 半成品原则下
   JSON 产物的 envelope 已带 ok=false 与诊断，是可回链的信息载体而非成功的纪念品；
   退出码仍为 1，构建脚本判定不受影响。check 子命令不做写盘（对齐旧职责划分）。
6. **AST 打印器**（parse 子命令）：穷尽 sealed AstNode 的 when（编译期完整性判据，
   对齐 HirJson 发射器口径，S-4 禁死代码）；树形自拟——节点行 = `Kind 主标量 位置`、
   孩子带字段名前缀、TheoryParam/Param/WhenArm/WhenPropArm 等非 AstNode 成员由
   专用小函数渲染（位置自携）。
7. **诊断输出**：`DiagBag.report(includeSupplement = -v, source = 读入源码)`
   逐条渲染旧格式 `[级别][l:c] CODE: msg` + 源码行与箭头（D-10 渲染层口径，
   t6 已实现，入口层只接线）。
8. **tokens 行格式**：`line:col<TAB>kind<TAB>text<TAB>sp|adj`——邻接标记对齐
   L-7（precededBySpace 的事实记录），最小可读形态。
9. **调用入口**：`frontend/build.gradle.kts` 加 application 插件（3 行，零新依赖，
   S-2 §3.3 不变）——`gradlew :frontend:run --args="compile x.subl"` 即可调用；
   main 类 `sublimation.frontend.cli.MainKt`。`runCli` 拆为返回退出码的函数体
   （输出流可注入），main 只做 exitProcess——可测性（S-3 §3 证据线）。

## 三、后果

- 前端期完成「源代码 → JSON 产物」闭环：S-2 §2 对外契约可演示，:cli 模块（S-2 §1
  待建）日后启用时把本包上提即可，CLI 逻辑不重写。
- 编译入口不持有任何状态（compileFrontend 纯函数），层间总契约 3（可复现）由
  CliTest 的重复调用断言守住。
- 半成品写盘决策的下文：下游（t12 验收 / :backend-js）须以 envelope.ok 判定阻断，
  不得以「文件存在」当成功。
