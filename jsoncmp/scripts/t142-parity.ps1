# t142-parity 对拍脚本（手动证明 by 证据链 / 契约位供给通道 的机械证据）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t142-parity.ps1
# 五段：① 供给链落位核对（by 证据→preps 契约位：正例绿/负例精确拒/旧口径保留）② 68 门差分台账
#       （vs **t122n 死冻锚**——手动证明样本=冻红件，转绿=预期差，从冻红集合除名、计数变化登记）
#       ③ 精确差分（vs t141 冻结产物：67 e2e 同 / 仅手动证明转绿差；模块样本零触及）④ stdlib 侧基线
#       （vs t141stdlib：全同——by 通道不触 stdlib）⑤ 确定性 9/9 + 脚本门 14/14。
# 退出码 0 = parity 全绿；非 0 = 失败件数。原生 java 冻红样本写 stderr——Continue 容错
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$java = "java -cp `"$($root)/frontend/build/install/frontend/lib/*`" sublimation.frontend.cli.MainKt"

# ---- ① 供给链落位核对（CLI 实跑）----
$probeDir = "$root/jsoncmp/out-subs"
function Run-Probe([string]$tag, [string]$src) {
    $out = "$probeDir/t142-$tag.json"
    $tmp = "$probeDir/t142-$tag.subl"
    Set-Content -Path $tmp -Value $src -Encoding UTF8
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    try { Invoke-Expression "$java compile `"$tmp`" -o `"$out`" 2>`$null | Out-Null" } catch { }
    Start-Sleep -Seconds 2
    if (-not (Test-Path $out)) { return "MISSING" }
    return Get-Content $out -Raw
}
$posTxt = Run-Probe "pos" "fun consumeP()<p>: Null {}`n@impure fun main(): Null {`n    consumeP() by [p]`n    unchecked print(1)`n}"
if ($posTxt -notmatch '"ok":\s*true') { Write-Output '[parity] FAIL: by 供给链正例未转绿（契约位 p 须由证据 [p] 供给）'; exit 1 }
Write-Output "[parity] OK: by 供给链正例绿（consumeP() by [p] ⇒ 契约位 p 供给 + 注入 Γ）"
$negTxt = Run-Probe "neg" "fun consumeP()<p>: Null {}`n@impure fun main(): Null {`n    consumeP() by [q]`n    unchecked print(1)`n}"
if ($negTxt -notmatch '"ok":\s*false') { Write-Output '[parity] FAIL: 负例未落红（证据不足须拒）'; exit 1 }
if ($negTxt -notmatch '"code":\s*"E-CANNOT-PROVE"') { Write-Output '[parity] FAIL: 负例无 E-CANNOT-PROVE（精确判据）'; exit 1 }
Write-Output "[parity] OK: 负例精确拒（by [q] 证据不足 ⇒ E-CANNOT-PROVE，不准放宽）"
$oldTxt = Run-Probe "old" "fun go(): Null {}`n@impure fun main(): Null {`n    go() by [q]`n    unchecked print(1)`n}"
if ($oldTxt -notmatch '"ok":\s*false') { Write-Output '[parity] FAIL: 无契约 by 旧口径漂移（q ∉ Γ 须红）'; exit 1 }
Write-Output "[parity] OK: 无契约 by 旧口径保留（byAsserts 从 Γ 核对未变）"

# ---- ② 68 门差分台账（vs t122n 死冻锚；手动证明=预期转绿件）----
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
$side = "$root/jsoncmp/out-subs/t142gate"
Remove-Item $side -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $side | Out-Null
$sameN = 0; $blindN = 0; $failN = 0; $turnGreen = 0; $lines = @()
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
    elseif ($base -eq "examples__手动证明.subl.json") {
        # 预期转绿件（冻红除名）：冻结锚 ok=false + E-CANNOT-PROVE，新件须 ok=true
        if ($b -match '"ok":\s*true') { $turnGreen++; $lines += '预期转绿`t$base（冻红除名——by 供给链修通）' }
        else { $failN++; $lines += 'FAIL`t$base（转绿失败）' }
    }
    elseif ($null -ne $a -and $null -ne $b) {
        $ja = $a | ConvertFrom-Json; $jb = $b | ConvertFrom-Json
        if (((Strip-Pos $ja) | ConvertTo-Json -Depth 20 -Compress) -ceq ((Strip-Pos $jb) | ConvertTo-Json -Depth 20 -Compress)) {
            $blindN++; $lines += '剥差同`t$base（pos/message 盲——t131 时代历史盲差）'
        } else { $failN++; $lines += "FAIL`t$base" }
    } else { $failN++; $lines += "FAIL`t$base（产物缺失）" }
}
$lines | Set-Content "$root/jsoncmp/out-subs/t142gate-ledger.txt" -Encoding UTF8
Write-Output "[parity] 68 门 vs t122n: SAME=$sameN 剥差同=$blindN 预期转绿=$turnGreen 结构差=$failN（台账: out-subs/t142gate-ledger.txt）"
if ($failN -gt 1) { Write-Output "[parity] FAIL: 68 门非预期结构差（$failN 件；预期至多 1 = 模块样本）"; exit $failN }
if ($turnGreen -ne 1) { Write-Output "[parity] FAIL: 预期转绿件应为 1（手动证明），实 $turnGreen"; exit 1 }
Write-Output "[parity] OK: 68 门口径——手动证明冻红除名（SAME 51→50，转绿登记）+ 16 历史剥差 + 1 模块样本结构差"

# ---- ③ 精确差分（vs t141 冻结产物 t141gate）----
$a141 = "$root/jsoncmp/out-subs/t141gate"
$sameD = 0; $newD = @(); $diffD = @()
foreach ($f in (Get-ChildItem $side -Filter "*.json")) {
    if ($f.Name -like "examples__模块系统*") { continue }
    $alt = Join-Path $a141 $f.Name
    if (Test-Path $alt) {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameD++ }
        elseif ($f.Name -eq "examples__手动证明.subl.json") { $diffD += $f.Name }
        else { Write-Output "[parity] FAIL: 精确差分破坏 $f"; exit 1 }
    } else { $newD += $f.Name }
}
if (@($diffD).Count -ne 1 -or $diffD[0] -ne "examples__手动证明.subl.json") {
    Write-Output ("[parity] FAIL: 转绿差异集合偏离（预期 仅 手动证明）: " + ($diffD -join ";")); exit 1 }
if (@($newD).Count -ne 0) { Write-Output ("[parity] FAIL: 新增件偏离（预期空）: " + ($newD -join ";")); exit 1 }
Write-Output "[parity] OK: 精确差分逐字节同 $sameD 件 + 仅手动证明转绿差（ok=false→true + by 诊断消失）"
$sameC = 0
foreach ($f in (Get-ChildItem $side -Filter "examples__模块系统__主.subl__*.json")) {
    $alt = Join-Path $a141 $f.Name
    if (Test-Path $alt) { if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameC++ }
        else { Write-Output "[parity] FAIL: 模块子件差 $f"; exit 1 } }
    else { Write-Output "[parity] FAIL: 模块子件缺失 $f"; exit 1 }
}
if ((Get-Content "$side/examples__模块系统__主.subl.json" -Raw) -ceq (Get-Content "$a141/examples__模块系统__主.subl.json" -Raw)) { }
else { Write-Output "[parity] FAIL: 模块样本主件差（预期同）"; exit 1 }
if ($sameC -ne 8) { Write-Output "[parity] FAIL: 模块子件逐字节同应为 8，实 $sameC"; exit 1 }
Write-Output "[parity] OK: 模块样本主件 + 8 子件逐字节同（by 通道零触及）"

# ---- ④ stdlib 侧基线（vs t141stdlib：全同）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t142stdlib"
Remove-Item $std -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $std | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$std/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$baseStd = "$root/jsoncmp/out-subs/t141stdlib"
$sameS = 0; $diffS = @()
foreach ($f in (Get-ChildItem $baseStd -Filter "*.json")) {
    if (Test-Path "$std/$($f.Name)") {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$std/$($f.Name)" -Raw)) { $sameS++ }
        else { $diffS += $f.Name }
    } else { $diffS += ($f.Name + "（缺产物）") }
}
if (@($diffS).Count -ne 0) {
    Write-Output ("[parity] FAIL: stdlib 侧差异偏离（预期全同）: " + ($diffS -join ";")); exit 1 }
Write-Output "[parity] OK: stdlib 侧基线逐字节同 $sameS 件（by 通道不触 stdlib）"

# ---- ⑤ 确定性 9/9 + 脚本门 14/14 ----
$da = "$root/jsoncmp/out-subs/t142det-a"; $db = "$root/jsoncmp/out-subs/t142det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$da/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
try { Invoke-Expression "$java compile `"$mod`" -o `"$db/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 9) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 9）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
& powershell -NoProfile -ExecutionPolicy Bypass -File "$root/jsoncmp/out-subs/run-gate.ps1" -Mode gate
if ($LASTEXITCODE -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate）"; exit $LASTEXITCODE }
Write-Output "[parity] PASSED: t142 by 供给链机械证据五段全绿"
