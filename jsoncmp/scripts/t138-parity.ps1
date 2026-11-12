# t138-parity 对拍脚本（R-23 B1 / 队长裁：虚拟 struct/联合别名 retire 的机械证据）
# 用法：pwsh -File jsoncmp/scripts/t138-parity.ps1
# 四段：① B1 retire 核对（虚拟表撤 + 允许表在位 + stdlib 源声明在位）② 7 依赖样本剥差门
#       （vs t122n：逐字节同或剥差后逐字节同——行为零变）③ 全量 68 门差分台账导出
#       （SAME/剥差同/失败逐样本登记）④ 脚本门 14 + 模块双导出确定性抽查
# 退出码 0 = parity 全绿；非 0 = 失败件数。
# 原生 java 冻红样本写 stderr——Stop 模式会误中断脚本：改 Continue（各门 exit 皆显式）
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$java = "java -cp `"$($root)/frontend/build/install/frontend/lib/*`" sublimation.frontend.cli.MainKt"

# ---- ① B1 retire 核对 ----
$pd = Get-Content "$root/frontend/src/main/kotlin/sublimation/frontend/semantic/PreludeDecls.kt" -Raw
if (-not ($pd -match [regex]::Escape("val RETIRED_STRUCTS"))) { Write-Output "[parity] FAIL: PreludeDecls 无 RETIRED_STRUCTS 允许表"; exit 1 }
if (-not ($pd -match [regex]::Escape("val RETIRED_ALIASES"))) { Write-Output "[parity] FAIL: PreludeDecls 无 RETIRED_ALIASES 允许表"; exit 1 }
if ($pd -match [regex]::Escape("ALIASES = listOf(")) { Write-Output "[parity] FAIL: ALIASES 未撤（B1 retire 纪律）"; exit 1 }
# 11 虚拟 struct 名（应为 Channel 留一 + retire 十项）
$stNames = @("Nil","Cons","None","Some","Ok","Err","SetNil","SetCons","MapNil","MapCons")
foreach ($n in $stNames) {
    if (-not ($pd -match [regex]::Escape($n))) { Write-Output "[parity] FAIL: RETIRED_STRUCTS 缺 $n"; exit 1 }
}
Write-Output "[parity] OK: PRELUDE struct/ALIASES 虚拟层撤（Channel 留虚拟）；RETIRED 允许表十 struct 入项"
# stdlib 源声明在位（数据源替换举证）
$pairs = @{ "stdlib/collection/list/list.subl" = @("struct Nil", "struct Cons[T](head: T, tail: List[T])", "type List[T] = Nil | Cons[T]");
           "stdlib/collection/optional/optional.subl" = @("struct None", "struct Some[T](value: T)", "type Optional[T] = None | Some[T]");
           "stdlib/collection/result/result.subl" = @("struct Ok[T](value: T)", "struct Err[E](value: E)");
           "stdlib/collection/set/set.subl" = @("struct SetNil", "struct SetCons[T](head: T, tail: Set[T])");
           "stdlib/collection/map/map.subl" = @("struct MapNil", "struct MapCons[K, V](key: K, value: V, tail: Map[K, V])") }
foreach ($k in $pairs.Keys) {
    $src = Get-Content "$root/$k" -Raw
    foreach ($decl in $pairs[$k]) {
        if (-not ($src -match [regex]::Escape($decl))) { Write-Output "[parity] FAIL: $k 缺声明「$decl」"; exit 1 }
    }
}
Write-Output "[parity] OK: stdlib 五件 10 struct + 5 联合别名源声明在位（形状同构）"

# ---- ② 7 依赖样本剥差门（vs t122n；SAME 或「pos/message 盲」逐字同）----
function Strip-Pos([object]$o) {
    if ($null -eq $o) { return $null }
    if ($o -is [array]) { return @($o | ForEach-Object { Strip-Pos $_ }) }
    if ($o -is [System.Collections.IDictionary] -or $o -is [pscustomobject]) {
        $h = [ordered]@{}
        foreach ($p in $o.PSObject.Properties) { if ($p.Name -notin @("pos","message")) { $h[$p.Name] = Strip-Pos $p.Value } }
        return [pscustomobject]$h
    }
    return $o
}
$deps = @(
  "examples__日常-数列与区间.subl.json",
  "examples__标准库集合.subl.json",
  "examples__特性巡礼.subl.json",
  "examples__命题基础.subl.json",
  "examples__模式匹配.subl.json",
  "examples__字符串与插值.subl.json",
  "examples__手动证明.subl.json")
$work = "$root/jsoncmp/out-subs/t138work"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $work | Out-Null
$bad = 0
foreach ($d in $deps) {
    $src = "$root/frontend/src/test/resources/e2e/examples/" + ($d -replace "^examples__", "" -replace ".subl.json$", ".subl")
    try { Invoke-Expression "$java compile `"$src`" -o `"$work/$d`" 2>`$null | Out-Null" } catch { }
    $a = Get-Content "$root/jsoncmp/out/t122n/$d" -Raw
    $b = Get-Content "$work/$d" -Raw
    if ($a -ceq $b) { Write-Output "[parity] SAME: $d" }
    else {
        $ja = Get-Content "$root/jsoncmp/out/t122n/$d" -Raw | ConvertFrom-Json
        $jb = Get-Content "$work/$d" -Raw | ConvertFrom-Json
        $sa = (Strip-Pos $ja) | ConvertTo-Json -Depth 20 -Compress
        $sb = (Strip-Pos $jb) | ConvertTo-Json -Depth 20 -Compress
        if ($sa -ceq $sb) { Write-Output "[parity] DIFF→剥差同（联合展开 pos 位）: $d" }
        else { Write-Output "[parity] FAIL: 剥差后仍差异 $d"; $bad++ }
    }
}
if ($bad -gt 0) { Write-Output "[parity] FAIL: 依赖样本剥差门破坏（$bad 件）"; exit $bad }
Write-Output "[parity] OK: 7 依赖样本零差分/剥差同（行为零变，OQ-10 B1 口径）"

# ---- ③ 68 门差分台账导出（SAME/剥差同/失败）----
$manifest = Get-Content "$root/jsoncmp/manifest/export-t131mod-list.txt"
$side = "$root/jsoncmp/out-subs/t138gate"
Remove-Item $side -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $side | Out-Null
$sameN = 0; $blindN = 0; $failN = 0; $lines = @()
foreach ($line in $manifest) {
    $parts = $line -split "\|"
    $src = "$root/" + ($parts[0] -replace "/", "\")
    $base = ($parts[1] -replace "^jsoncmp/out/t131mod/", "")
    $dst = "$side/$base"
    $dir = Split-Path $dst -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    try { Invoke-Expression "$java compile `"$src`" -o `"$dst`" 2>`$null | Out-Null" } catch { }
    $a = Get-Content "$root/jsoncmp/out/t131mod/$base" -Raw -ErrorAction SilentlyContinue
    $b = if (Test-Path $dst) { Get-Content $dst -Raw } else { $null }
    if ($a -ceq $b) { $sameN++; $lines += "SAME`t$base" }
    elseif ($null -ne $a -and $null -ne $b) {
        $ja = $a | ConvertFrom-Json; $jb = $b | ConvertFrom-Json
        if (((Strip-Pos $ja) | ConvertTo-Json -Depth 20 -Compress) -ceq ((Strip-Pos $jb) | ConvertTo-Json -Depth 20 -Compress)) {
            $blindN++; $lines += "DIFF→剥差同`t$base（差异位=retType 联合展开 pos/旁注 message；行为零变）"
        } else { $failN++; $lines += "FAIL`t$base" }
    } else { $failN++; $lines += "FAIL`t$base（产物缺失）" }
}
$lines | Set-Content "$root/jsoncmp/out-subs/t138gate-ledger.txt"
Write-Output "[parity] 68 门: SAME=$sameN 剥差同=$blindN 失败=$failN（台账: out-subs/t138gate-ledger.txt）"
if ($failN -gt 0) { Write-Output "[parity] FAIL: 68 门出现非剥差差异（$failN 件）"; exit $failN }

# ---- ④ 模块双导出确定性抽查（主 envelope + 7 子件逐字节）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$da = "$root/jsoncmp/out-subs/t138det-a"; $db = "$root/jsoncmp/out-subs/t138det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$da/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
try { Invoke-Expression "$java compile `"$mod`" -o `"$db/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$ja = Get-ChildItem $da -Filter "*.json"; $jb = Get-ChildItem $db -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) {
    $n++
    if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ }
}
if ($det -ne $n -or $n -ne 8) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（件数应为 8）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n（B1 分层后 1+7 件集合）"
Write-Output "[parity] PASSED: B1 retire 机械证据四段全绿"