# t139-parity 对拍脚本（R-23 B2 / EXT_METHODS retire + ext 限定调位 的机械证据）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t139-parity.ps1
# 五段：① B2 retire 核对（虚拟 ext 表撤 + 允许表 + stdlib 源声明在位/体空）② 7 依赖样本剥差门
#       （vs t122n：逐字节同或剥差后逐字节同——行为零变）③ 全量 68 门差分台账导出
#       ④ stdlib 侧基线比对（vs t137stdlib：预期仅 list 件差 = add 声明位；其余字节同）
#       ⑤ 模块双导出确定性 8/8 + 脚本门 14/14（run-gate gate 模式）
# 退出码 0 = parity 全绿；非 0 = 失败件数。
# 原生 java 冻红样本写 stderr——Stop 模式会误中断脚本：改 Continue（各门 exit 皆显式）
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$java = "java -cp `"$($root)/frontend/build/install/frontend/lib/*`" sublimation.frontend.cli.MainKt"

# ---- ① B2 retire 核对 ----
$pd = Get-Content "$root/frontend/src/main/kotlin/sublimation/frontend/semantic/PreludeDecls.kt" -Raw
if (-not ($pd -match [regex]::Escape("EXT_METHODS: List<FunDecl> = listOf()"))) { Write-Output "[parity] FAIL: EXT_METHODS 未撤（B2 retire 纪律——应空表）"; exit 1 }
if (-not ($pd -match [regex]::Escape("RETIRED_EXT_METHODS"))) { Write-Output "[parity] FAIL: PreludeDecls 无 RETIRED_EXT_METHODS 允许表"; exit 1 }
if (-not ($pd -match [regex]::Escape("setOf(`"List`" to `"add`")"))) { Write-Output "[parity] FAIL: RETIRED_EXT_METHODS 缺 (List, add) 键"; exit 1 }
if ($pd -match [regex]::Escape("private fun methodFun")) { Write-Output "[parity] FAIL: methodFun 助手未随表撤"; exit 1 }
Write-Output "[parity] OK: EXT_METHODS 虚拟表撤 + RETIRED_EXT_METHODS ((List,add)) 允许表在位"
$listSrc = Get-Content "$root/stdlib/collection/list/list.subl" -Raw
$addLine = "@mut fun (@mut List[a]).add[a](v: a): List[a]"
if (-not ($listSrc -match [regex]::Escape($addLine))) { Write-Output "[parity] FAIL: list.subl 缺 add 声明（签名面同构形态）"; exit 1 }
if (-not ($listSrc.Contains($addLine + [char]10))) { Write-Output "[parity] FAIL: add 体非空（OQ-4 b：体仍空，别写结构性体）"; exit 1 }
Write-Output "[parity] OK: add 落体 stdlib list 源（体仍空——R-15 兑现债登记）"

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
$work = "$root/jsoncmp/out-subs/t139work"
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
Write-Output "[parity] OK: 7 依赖样本零差分/剥差同（行为零变，OQ-10 口径延续 B1）"

# ---- ③ 68 门差分台账导出（SAME/剥差同/失败）----
$manifest = Get-Content "$root/jsoncmp/manifest/export-t131mod-list.txt"
$side = "$root/jsoncmp/out-subs/t139gate"
Remove-Item $side -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $side | Out-Null
$sameN = 0; $blindN = 0; $failN = 0; $lines = @()
foreach ($line in $manifest) {
    $parts = $line -split "\|"
    $src = "$root/" + ($parts[0] -replace "/", [char]92)
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
$lines | Set-Content "$root/jsoncmp/out-subs/t139gate-ledger.txt"
Write-Output "[parity] 68 门: SAME=$sameN 剥差同=$blindN 失败=$failN（台账: out-subs/t139gate-ledger.txt）"
if ($failN -gt 0) { Write-Output "[parity] FAIL: 68 门出现非剥差差异（$failN 件）"; exit $failN }

# ---- ④ stdlib 侧基线比对（vs t137stdlib：预期仅 list 件差 = add 声明位；其余字节同）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t139stdlib"
Remove-Item $std -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $std | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$std/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$baseStd = "$root/jsoncmp/out/t137stdlib"
$sameS = 0; $diffS = @()
foreach ($f in (Get-ChildItem $baseStd -Filter "*.json")) {
    if (Test-Path "$std/$($f.Name)") {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$std/$($f.Name)" -Raw)) { $sameS++ }
        else { $diffS += $f.Name }
    } else { $diffS += ($f.Name + "（缺产物）") }
}
$expect = "examples__模块系统__list.json"   # B2 预期：仅 list 件差（add 声明位新条目）
if (@($diffS).Count -ne 1 -or $diffS[0] -ne $expect) {
    Write-Output ("[parity] FAIL: 侧基线差异集合偏离预期: " + ($diffS -join ";") + "（预期 " + $expect + "）"); exit 1 }
Write-Output "[parity] OK: 侧基线逐字节同 $sameS 件 + 预期差 $expect（add 声明位——台账登记）"

# ---- ⑤ 模块双导出确定性 8/8 + 脚本门 14/14 ----
$da = "$root/jsoncmp/out-subs/t139det-a"; $db = "$root/jsoncmp/out-subs/t139det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$da/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
try { Invoke-Expression "$java compile `"$mod`" -o `"$db/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 8) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（件数应为 8）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
& powershell -NoProfile -ExecutionPolicy Bypass -File "$root/jsoncmp/out-subs/run-gate.ps1" -Mode gate
if ($LASTEXITCODE -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate）"; exit $LASTEXITCODE }
Write-Output "[parity] PASSED: B2 retire 机械证据五段全绿"
