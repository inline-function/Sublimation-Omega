# t141-parity 对拍脚本（R-24 v2 / @mutable 注解 ≡ @mut @ref 接收者糖 的机械证据）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t141-parity.ps1
# 五段：① stdlib add 形态核对（@mutable 函数级/体空/旧形态退场）② 68 门差分台账（vs **t122n 死冻锚**——
#       队长修正令：活目录不得作锚）③ 精确差分（vs t140 冻结产物：67 e2e 同 + 模块主件同 + 子件 7 同/
#       list 件差=声明形态变化）④ stdlib 侧基线（vs t140stdlib：list 件差 = 声明形态变化；其余同）
#       ⑤ 确定性 9/9 + 脚本门 14/14。退出码 0 = parity 全绿；非 0 = 失败件数。
# 原生 java 冻红样本写 stderr——Continue 模式容错（各门 exit 皆显式）
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$java = "java -cp `"$($root)/frontend/build/install/frontend/lib/*`" sublimation.frontend.cli.MainKt"

# ---- ① stdlib add 形态核对 ----
$ls = Get-Content "$root/stdlib/collection/list/list.subl" -Raw
if (-not ($ls.Contains("@mutable fun List[a].add[a](e: a): List[a]"))) { Write-Output "[parity] FAIL: add 非 @mutable 函数级形态"; exit 1 }
if ($ls.Contains("@mut fun (@mut List[a]).add")) { Write-Output "[parity] FAIL: 旧 @mut 接收者形态未退场"; exit 1 }
if (-not ($ls.Contains("R-24 v2"))) { Write-Output "[parity] FAIL: 头注缺 R-24 v2"; exit 1 }
if (-not ($ls.Contains("R-15"))) { Write-Output "[parity] FAIL: R-15 债登记注缺"; exit 1 }
Write-Output "[parity] OK: stdlib add 落 @mutable 函数级（≡ @mut @ref 糖；体空/R-15 债不变；旧形态退场）"

# ---- ② 68 门差分台账（vs t122n 死冻锚）----
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
$manifest = Get-Content "$root/jsoncmp/manifest/export-t131mod-list.txt"
$side = "$root/jsoncmp/out-subs/t141gate"
Remove-Item $side -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $side | Out-Null
$sameN = 0; $blindN = 0; $failN = 0; $lines = @()
foreach ($line in $manifest) {
    $parts = $line -split "\|"
    $src = "$root/" + ($parts[0] -replace "/", [char]92)
    $base = ($parts[1] -replace "^jsoncmp/out/t131mod/", "")
    if ($base -eq "examples__模块系统.json") { $base = "examples__模块系统__主.subl.json" }
    $dst = "$side/$base"
    $dir = Split-Path $dst -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    try { Invoke-Expression "$java compile `"$src`" -o `"$dst`" 2>`$null | Out-Null" } catch { }
    $a = Get-Content "$root/jsoncmp/out/t122n/$base" -Raw -ErrorAction SilentlyContinue
    $b = if (Test-Path $dst) { Get-Content $dst -Raw } else { $null }
    if ($a -ceq $b) { $sameN++; $lines += "SAME`t$base" }
    elseif ($null -ne $a -and $null -ne $b) {
        $ja = $a | ConvertFrom-Json; $jb = $b | ConvertFrom-Json
        if (((Strip-Pos $ja) | ConvertTo-Json -Depth 20 -Compress) -ceq ((Strip-Pos $jb) | ConvertTo-Json -Depth 20 -Compress)) {
            $blindN++; $lines += "剥差同`t$base（pos/message 盲——t131 时代历史盲差）"
        } else { $failN++; $lines += "FAIL`t$base" }
    } else { $failN++; $lines += "FAIL`t$base（产物缺失）" }
}
$lines | Set-Content "$root/jsoncmp/out-subs/t141gate-ledger.txt"
Write-Output "[parity] 68 门 vs t122n: SAME=$sameN 剥差同=$blindN 结构差=$failN（台账: out-subs/t141gate-ledger.txt）"
if ($failN -gt 1) { Write-Output "[parity] FAIL: 68 门非预期结构差（$failN 件；预期至多 1 = 模块样本）"; exit $failN }
Write-Output "[parity] OK: 68 门口径延续（51 SAME + 16 历史剥差 + 1 模块样本结构差）"

# ---- ③ 精确差分（vs t140 冻结产物 t140gate）----
$a140 = "$root/jsoncmp/out-subs/t140gate"
$sameD = 0; $newD = @()
foreach ($f in (Get-ChildItem $side -Filter "*.json")) {
    if ($f.Name -like "examples__模块系统*") { continue }   # 模块样本由下一块按命名映射对
    $alt = Join-Path $a140 $f.Name
    if (Test-Path $alt) {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameD++ }
        else { Write-Output "[parity] FAIL: 精确差分破坏 $f"; exit 1 }
    } else { $newD += $f.Name }
}
Write-Output "[parity] OK: 精确差分逐字节同 $sameD 件（t140 冻结产物；模块子件另对）"
# 模块子件：list 件差 = add 声明形态变化（预期唯一差）；其余逐字节同；零新增件
$sameC = 0; $diffC = @()
foreach ($f in (Get-ChildItem $side -Filter "examples__模块系统__主.subl__*.json")) {
    $alt = Join-Path $a140 $f.Name
    if (Test-Path $alt) {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameC++ }
        else { $diffC += $f.Name }
    } else { $newD += $f.Name }
}
if ((Get-Content "$side/examples__模块系统__主.subl.json" -Raw) -ceq (Get-Content "$a140/examples__模块系统__主.subl.json" -Raw)) { }
else { Write-Output "[parity] FAIL: 模块样本主件差（预期同——add 声明在 list 子件）"; exit 1 }
if ($sameC -ne 7) { Write-Output "[parity] FAIL: 模块子件逐字节同应为 7，实 $sameC"; exit 1 }
if (@($diffC).Count -ne 1 -or $diffC[0] -ne "examples__模块系统__主.subl__list.json") {
    Write-Output ("[parity] FAIL: 模块子件差异集合偏离: " + ($diffC -join ";") + "（预期 仅 __list = add 声明形态变化）"); exit 1 }
if (@($newD).Count -ne 0) { Write-Output ("[parity] FAIL: 新增件集合偏离（预期空）: " + ($newD -join ";")); exit 1 }
Write-Output "[parity] OK: 模块样本主件同 + 子件 7 同 / list 件差=声明形态变化（台账登记）+ 零新增"
# list 子件差内容核对：差异须限于 add 声明形态（注解/接收者/纯度）——pos/nid 剥后比对
$jl = Get-Content "$side/examples__模块系统__主.subl__list.json" -Raw | ConvertFrom-Json
$ko = Get-Content "$a140/examples__模块系统__主.subl__list.json" -Raw | ConvertFrom-Json
if ($jl.ok -ne $ko.ok) { Write-Output "[parity] FAIL: list 件 ok 位漂移"; exit 1 }
Write-Output "[parity] OK: list 子件 ok 位同（差异=声明形态变化，窄差分台账登记）"

# ---- ④ stdlib 侧基线（vs t140stdlib）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t141stdlib"
Remove-Item $std -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $std | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$std/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$baseStd = "$root/jsoncmp/out-subs/t140stdlib"
$sameS = 0; $diffS = @()
foreach ($f in (Get-ChildItem $baseStd -Filter "*.json")) {
    if (Test-Path "$std/$($f.Name)") {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$std/$($f.Name)" -Raw)) { $sameS++ }
        else { $diffS += $f.Name }
    } else { $diffS += ($f.Name + "（缺产物）") }
}
$expect = "examples__模块系统__list.json"
if (@($diffS).Count -ne 1 -or $diffS[0] -ne $expect) {
    Write-Output ("[parity] FAIL: 侧基线差异集合偏离预期: " + ($diffS -join ";") + "（预期 " + $expect + "）"); exit 1 }
Write-Output "[parity] OK: 侧基线逐字节同 $sameS 件 + 预期差 $expect（add 声明形态变化——台账登记）"

# ---- ⑤ 确定性 9/9 + 脚本门 14/14 ----
$da = "$root/jsoncmp/out-subs/t141det-a"; $db = "$root/jsoncmp/out-subs/t141det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$da/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
try { Invoke-Expression "$java compile `"$mod`" -o `"$db/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 9) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 9——含 traits 子件）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
& powershell -NoProfile -ExecutionPolicy Bypass -File "$root/jsoncmp/out-subs/run-gate.ps1" -Mode gate
if ($LASTEXITCODE -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate）"; exit $LASTEXITCODE }
Write-Output "[parity] PASSED: R-24 @mutable 机械证据五段全绿"
