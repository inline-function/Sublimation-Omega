# t137 parity 对拍脚本（R-23 OQ-9 / 队长裁：retire 的机械证据）
# 用法：pwsh -File jsoncmp/scripts/t137-parity.ps1
# 三段：① PRELUDE_FUNS retire 核对（表已删）② 7 依赖样本零差分（vs t122n）③ stdlib 件基线导出
# 退出码 0 = parity 全绿；非 0 = 差异件数（报告逐件列出）。
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot/../.."
$java = "java -cp `"$($root)/frontend/build/install/frontend/lib/*`" sublimation.frontend.cli.MainKt"

# ---- ① PRELUDE_FUNS retire 核对（SymbolTable.kt 无表 + stdlib 源三项在位）----
$st = Get-Content "$root/frontend/src/main/kotlin/sublimation/frontend/semantic/SymbolTable.kt" -Raw
$hits = ([regex]::Matches($st, "val PRELUDE_FUNS|fun preludeFunSpec\(|data class PreludeField|data class PreludeFun")).Count
if ($hits -ne 0) { Write-Output "[parity] FAIL: SymbolTable.kt 仍含 retire 遗留活动件（表/规格/数据类）x$hits"; exit $hits }
Write-Output "[parity] OK: PRELUDE_FUNS/preludeFunSpec/PreludeField/PreludeFun 活动件已从 SymbolTable.kt retire（注释留痕除外）"
$listSrc = Get-Content "$root/stdlib/collection/list/list.subl" -Raw
$mathSrc = Get-Content "$root/stdlib/math/math.subl" -Raw
if (-not ($listSrc -match "fun range(a : Nat, b : Nat) : List[Nat]" -and $listSrc -match "fun sum(xs : List[Nat]) : Nat"))
    { Write-Output "[parity] FAIL: stdlib list 件 range/sum 签名不在位"; exit 1 }
if (-not ($mathSrc -match "fun summation(a : Nat, b : Nat) : Nat"))
    { Write-Output "[parity] FAIL: stdlib math 件 summation 签名不在位"; exit 1 }
Write-Output "[parity] OK: stdlib 源三项签名条目在位（range/sum/list ← list；summation ← math）"

# ---- ② retire 前后零差分：7 依赖样本逐字节 vs t122n（t122n = retire 前基线，保护区铁律）----
$deps = @(
  "examples__日常-数列与区间.subl.json",
  "examples__标准库集合.subl.json",
  "examples__特性巡礼.subl.json",
  "examples__命题基础.subl.json",
  "examples__模式匹配.subl.json",
  "examples__字符串与插值.subl.json",
  "examples__手动证明.subl.json")
$work = "$root/jsoncmp/out/t137parity"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $work | Out-Null
$diffs = 0
foreach ($d in $deps) {
  $src = "$root/frontend/src/test/resources/e2e/examples/" + ($d -replace "^examples__", "" -replace ".subl.json$", ".subl")
  Invoke-Expression "$java compile `"$src`" -o `"$work/$d`" *> `$null"
  $a = Get-Content "$root/jsoncmp/out/t122n/$d" -Raw
  $b = Get-Content "$work/$d" -Raw
  if ($a -ceq $b) { Write-Output "[parity] SAME: $d" } else { Write-Output "[parity] DIFF: $d"; $diffs++ }
}
if ($diffs -gt 0) { Write-Output "[parity] FAIL: 零差分 retire 破坏（$diffs 件）"; exit $diffs }

# ---- ③ stdlib 件基线（侧目录口径：e2e 主样本零动，stdlib 差异收敛到 jsoncmp 侧目录）----
$side = "$root/jsoncmp/out/t137stdlib"
Remove-Item $side -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $side | Out-Null
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
Invoke-Expression "$java compile `"$mod`" -o `"$side/examples__模块系统.json`" *> `$null"
Get-ChildItem $side -Filter "*.json" | Where-Object { $_.Name -match "__list.json$|__math.json$" } | ForEach-Object {
  Write-Output "[parity] stdlib 件基线: $($_.Name)（$($_.Length) 字节）" }
Write-Output "[parity] PASSED: retire 机械证据三段全绿"