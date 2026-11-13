# t144-parity 对拍脚本（R-26.1 括号语义统一：[] = 类型实参（调用位）、<> = 命题参数）的机械证据）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t144-parity.ps1
# 五段：① 括号统一行为探针（[] 绿 / <> 调用位落 E-CALL-ANGLE 精确报）② 68 门差分台账
#       （vs **t122n 死冻锚**——t143 台账口径延续：SAME 50 + 剥差同 16 + 预期转绿 1（手动证明）
#       + 模块样本 1；括号纯语法糖 ⇒ 产品零差）③ 精确差分（vs t143 冻结产物：全逐字节同）
#       ④ stdlib 侧基线（vs t143stdlib：全同）⑤ 确定性 9/9 + 脚本门 14/14。
# 退出码 0 = parity 全绿；非 0 = 失败件数。
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$java = "java -cp `"$($root)/frontend/build/install/frontend/lib/*`" sublimation.frontend.cli.MainKt"
$probeDir = "$root/jsoncmp/out-subs"

# ---- ① 括号统一行为探针 ----
function Run-Probe([string]$tag, [string]$src) {
    $out = "$probeDir/t144-$tag.json"; $tmp = "$probeDir/t144-$tag.subl"
    Set-Content -Path $tmp -Value $src -Encoding UTF8
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    try { Invoke-Expression "$java compile `"$tmp`" -o `"$out`" 2>`$null | Out-Null" } catch { }
    Start-Sleep -Seconds 2
    if (-not (Test-Path $out)) { return "MISSING" }
    return Get-Content $out -Raw
}
$okTxt = Run-Probe "brk" "fun id[T](x: T): T = x`n@impure fun m(): Null {`n    var a = id[Nat](1)`n    unchecked print(a)`n}"
$okTxt2 = Run-Probe "ch" "@impure fun m(): Null {`n    var ch: Channel[Nat] = Channel[Nat](2)`n    unchecked print(ch)`n}"
if ($okTxt -notmatch '"ok":\s*true') { Write-Output '[parity] FAIL: 调用位方括号类型实参未绿'; exit 1 }
if ($okTxt2 -notmatch '"ok":\s*true') { Write-Output '[parity] FAIL: Channel[Nat] 通道方括号构造未绿（类型位零动）'; exit 1 }
Write-Output "[parity] OK: 调用位 [] 类型实参通道绿（f[A](args) / Channel[T](cap)）"
$angTxt = Run-Probe "ang" "@impure fun m(): Null {`n    var ch = Channel<Nat>(2)`n    unchecked print(ch)`n}"
if ($angTxt -notmatch '"ok":\s*false') { Write-Output '[parity] FAIL: 调用位 <> 未落精确红（E-CALL-ANGE 须拒）'; exit 1 }
if ($angTxt -notmatch 'E-CALL-ANGLE') { Write-Output '[parity] FAIL: 调用位 <> 须落 E-CALL-ANGLE 精确码'; exit 1 }
if ($angTxt -notmatch 'by 断言') { Write-Output '[parity] FAIL: E-CALL-ANGLE 消息须指向 by 断言通道'; exit 1 }
if ($angTxt -notmatch '命题参数通道') { Write-Output '[parity] FAIL: E-CALL-ANGLE 消息须声明命题通道口径'; exit 1 }
Write-Output "[parity] OK: 调用位 <> 精确落 E-CALL-ANGLE（指向 by 断言/方括号迁移，不准静默）"

# ---- ② 68 门差分台账（vs t122n 死冻锚；t143 台账口径延续）----
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
$side = "$root/jsoncmp/out-subs/t144gate"
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
    elseif ($base -eq "examples__上下文弥散.subl.json") {
        if ($b -match 'E-CALL-ANGLE') { $turnRed++; $lines += '预期转红`t$base（t144：witness<谓词>() 调用位命题供给未通——E-CALL-ANGLE 精确报，R-26.1 口径）' }
        else { $failN++; $lines += "FAIL`t$base" }
    }
    elseif ($base -eq "examples__手动证明.subl.json") {
        if ($b -match '"ok":\s*true') { $turnGreen++; $lines += '预期转绿`t$base（t142 除名——口径延续）' }
        else { $failN++; $lines += 'FAIL`t$base' }
    }
    elseif ($null -ne $a -and $null -ne $b) {
        $ja = $a | ConvertFrom-Json; $jb = $b | ConvertFrom-Json
        if (((Strip-Pos $ja) | ConvertTo-Json -Depth 20 -Compress) -ceq ((Strip-Pos $jb) | ConvertTo-Json -Depth 20 -Compress)) {
            $blindN++; $lines += '剥差同`t$base（pos/message 盲——t131 时代历史盲差）'
        } else { $failN++; $lines += "FAIL`t$base" }
    } else { $failN++; $lines += "FAIL`t$base（产物缺失）" }
}
$lines | Set-Content "$root/jsoncmp/out-subs/t144gate-ledger.txt" -Encoding UTF8
Write-Output "[parity] 68 门 vs t122n: SAME=$sameN 剥差同=$blindN 预期转绿=$turnGreen 结构差=$failN（台账: out-subs/t144gate-ledger.txt；t148 复算口径——上下文弥散回 SAME 预期转红撤账）"
if ($failN -gt 1) { Write-Output "[parity] FAIL: 68 门非预期结构差（$failN 件；预期至多 1 = 模块样本）"; exit $failN }

if ($turnGreen -ne 1 -or $sameN -ne 50 -or $blindN -ne 16) { Write-Output "[parity] FAIL: 68 门计数波动（t148 复算口径 SAME50/剥差16/转绿1）；实测 SAME=$sameN 剥差=$blindN 转绿=$turnGreen"; exit 1 }
Write-Output "[parity] OK: 68 门闭合（SAME 50 含上下文弥散复绿 + 剥差同 16 + 转绿 1 + 模块样本 1）——t148 复算口径（预期转红撤账）；4 处括号糖迁移样本维持 SAME——零 HIR 影响（糖零差已证）"

# ---- ③ 精确差分（vs t143 冻结产物 t143gate：括号纯语法糖⇒零产品差）----
$a143 = "$root/jsoncmp/out-subs/t143gate"
$sameD = 0; $newD = @(); $diffD = @()
foreach ($f in (Get-ChildItem $side -Filter "*.json")) {
    $alt = Join-Path $a143 $f.Name
    if (Test-Path $alt) {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameD++ }
        else { $diffD += $f.Name }
    } else { $newD += $f.Name }
}
if (@($diffD).Count -ne 0 -or @($newD).Count -ne 0) {
    Write-Output ("[parity] FAIL: 精确差分破坏（t148 复算口径：全逐字节同——上下文弥散复绿回 t143 基线）差: " + ($diffD -join ";") + " 新: " + ($newD -join ";")); exit 1 }
Write-Output "[parity] OK: 精确差分逐字节同 $sameD 件（t148 复算口径——括号统一零产品差，上下文弥散复绿逐字节回 t143 基线）"

# ---- ④ stdlib 侧基线（vs t143stdlib：全同）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t144stdlib"
Remove-Item $std -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $std | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$std/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$baseStd = "$root/jsoncmp/out-subs/t143stdlib"
$sameS = 0; $diffS = @()
foreach ($f in (Get-ChildItem $baseStd -Filter "*.json")) {
    if (Test-Path "$std/$($f.Name)") {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$std/$($f.Name)" -Raw)) { $sameS++ }
        else { $diffS += $f.Name }
    } else { $diffS += ($f.Name + "（缺产物）") }
}
if (@($diffS).Count -ne 0) { Write-Output ("[parity] FAIL: stdlib 侧差异（预期全同）: " + ($diffS -join ";")); exit 1 }
Write-Output "[parity] OK: stdlib 侧基线逐字节同 $sameS 件（括号统一不触 stdlib 体）"

# ---- ⑤ 确定性 9/9 + 脚本门 14/14 ----
$da = "$root/jsoncmp/out-subs/t144det-a"; $db = "$root/jsoncmp/out-subs/t144det-b"
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
Write-Output "[parity] PASSED: t144 R-26.1 括号统一机械证据五段全绿"
