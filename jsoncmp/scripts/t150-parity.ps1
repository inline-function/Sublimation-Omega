# t150-parity 对拍脚本（R-28 隐式深拷矩阵废止 + R-29 深拷补注：机械证据五段）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t150-parity.ps1
# 五段：① 行为探针（R-28 五拒格 + 消息指引 / 共享格零回归 / R-29 深拷补注硬指标）
#       ② 68 门差分台账（vs t122n 死冻锚——R-28 预期差 4 = 三 e2e 吸收 + 模块样本 1）
#       ③ 精确差分（vs t149gate 冻结产物：唯三 e2e 吸收差——R-28 语义落地证据）
#       ④ stdlib 侧基线（vs t143stdlib：list 件构造族迁源面延续）⑤ 确定性 9/9 + 脚本门。
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$javaExe = "F:\jdk-17.0.13.11-hotspot\bin\java.exe"
$cp = "$($root)/frontend/build/install/frontend/lib/*"
$probeDir = "$root/jsoncmp/out-subs"

function Run-Probe([string]$tag, [string]$src) {
    $out = "$probeDir/t150-$tag.json"; $tmp = "$probeDir/t150-$tag.subl"
    Set-Content -Path $tmp -Value $src -Encoding UTF8
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $tmp, "-o", $out) -NoNewWindow -Wait | Out-Null } catch { }
    if (-not (Test-Path $out)) { return "MISSING" }
    return Get-Content $out -Raw
}

# ---- ① 行为探针：R-28 拒格（5 格落 E-TYPE-MISMATCH + 消息含迁移指引）;共享格零回归;R-29 补注 ----
# 拒格 1:PLAIN → @mut（var 位类型注解）
$r1 = Run-Probe "r1" "@impure fun main(): Null {`n    var a : @mut List[Nat] = listOf(1)`n    print(a)`n}"
if ($r1 -notmatch [regex]::Escape('隐式深拷格已废止')) { Write-Output '[parity] FAIL: R-28 PLAIN→MUT 未落废止指引'; exit 1 }
if ($r1 -notmatch 'E-TYPE-MISMATCH') { Write-Output '[parity] FAIL: R-28 PLAIN→MUT 未落 E-TYPE-MISMATCH'; exit 1 }
if ($r1 -match 'ok": true') { Write-Output '[parity] FAIL: R-28 PLAIN→MUT 放行了'; exit 1 }
# 拒格 2:REF → PLAIN / 拒格 3:REF → MUT / 拒格 4:REFMUT → PLAIN / 拒格 5:REFMUT → MUT
$r2 = Run-Probe "r2" "@impure fun main(): Null {`n    var v : @ref List[Nat] = listOf(1)`n    var g : List[Nat] = v`n    print(g)`n}"
if ($r2 -notmatch 'E-TYPE-MISMATCH') { Write-Output '[parity] FAIL: R-28 REF→PLAIN 未拒'; exit 1 }
$r3 = Run-Probe "r3" "@impure fun main(): Null {`n    var v : @ref List[Nat] = listOf(1)`n    var g : @mut List[Nat] = v`n    print(g)`n}"
if ($r3 -notmatch 'E-TYPE-MISMATCH') { Write-Output '[parity] FAIL: R-28 REF→MUT 未拒'; exit 1 }
$r4 = Run-Probe "r4" "@impure fun main(): Null {`n    var v : @mut List[Nat] = mutableListOf(1)`n    var w : @ref @mut List[Nat] = v`n    var g : List[Nat] = w`n    print(g)`n}"
if ($r4 -notmatch 'E-TYPE-MISMATCH') { Write-Output '[parity] FAIL: R-28 REFMUT→PLAIN 未拒'; exit 1 }
$r5 = Run-Probe "r5" "@impure fun main(): Null {`n    var v : @mut List[Nat] = mutableListOf(1)`n    var w : @ref @mut List[Nat] = v`n    var g : @mut List[Nat] = w`n    print(g)`n}"
if ($r5 -notmatch 'E-TYPE-MISMATCH') { Write-Output '[parity] FAIL: R-28 REFMUT→MUT 未拒'; exit 1 }
# 共享格零回归:MUT→REF / MUT→REFMUT / REF→REF / REFMUT→REF 共享
$s1 = Run-Probe "s1" "@impure fun main(): Null {`n    var v : @mut List[Nat] = mutableListOf(1)`n    var g : @ref List[Nat] = v`n}"
if ($s1 -notmatch 'ok": true') { Write-Output '[parity] FAIL: R-28 MUT→REF 共享格回归'; exit 1 }
$s2 = Run-Probe "s2" "@impure fun main(): Null {`n    var v : @mut List[Nat] = mutableListOf(1)`n    var w : @ref @mut List[Nat] = v`n    var g : @ref @mut List[Nat] = w`n}"
if ($s2 -notmatch 'ok": true') { Write-Output '[parity] FAIL: R-28 REFMUT→REFMUT 共享格回归'; exit 1 }
# R-29 硬指标:var a: @mut T = …; var b: T = a 实测落 D-DEEP-COPY 补注（非 E 级）
$d1 = Run-Probe "d1" "@impure fun main(): Null {`n    var a : @mut List[Nat] = mutableListOf(1)`n    var b : List[Nat] = a`n}"
if ($d1 -notmatch 'D-DEEP-COPY') { Write-Output '[parity] FAIL: R-29 MUT→PLAIN 深拷补注未落'; exit 1 }
if ($d1 -notmatch 'ok": true') { Write-Output '[parity] FAIL: R-29 补注不拦编译'; exit 1 }
$d2 = Run-Probe "d2" "@impure fun main(): Null {`n    var a : @mut List[Nat] = mutableListOf(1)`n    var b : @mut List[Nat] = a`n}"
if ($d2 -notmatch 'D-DEEP-COPY') { Write-Output '[parity] FAIL: R-29 MUT→MUT 深拷补注未落'; exit 1 }
Write-Output '[parity] OK: R-28 五拒格落 E-TYPE-MISMATCH + 消息迁移指引;共享格零回归;R-29 补注覆盖 MUT→PLAIN/MUT→MUT'

# ---- ② 68 门差分台账（vs t122n 死冻锚;R-28 预期差 4 = 三 e2e 吸收 + 模块样本 1）----
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
$side = "$root/jsoncmp/out-subs/t150gate"
Remove-Item $side -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $side | Out-Null
$sameN = 0; $blindN = 0; $turnGreen = 0; $lines = @()
$expected = @("examples__副作用与unchecked.subl.json", "examples__特性巡礼.subl.json", "examples__类型系统.subl.json")
foreach ($line in $manifest) {
    $parts = $line -split "\|"
    $src = "$root/" + ($parts[0] -replace "/", [char]92)
    $base = ($parts[1] -replace "^jsoncmp/out/t131mod/", "")
    if ($base -eq "examples__模块系统.json") { $base = "examples__模块系统__主.subl.json" }
    $dst = "$side/$base"
    $dir = Split-Path $dst -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $src, "-o", $dst) -NoNewWindow -Wait | Out-Null } catch { }
    $a = Get-Content "$root/jsoncmp/out/t122n/$base" -Raw -ErrorAction SilentlyContinue
    $b = if (Test-Path $dst) { Get-Content $dst -Raw } else { $null }
    if ($a -ceq $b) { $sameN++; $lines += "SAME`t$base" }
    elseif ($base -eq "examples__手动证明.subl.json") {
        if ($b -match 'ok":\s*true') { $turnGreen++; $lines += "预期转绿`t$base（t142 除名——口径延续）" }
        else { $lines += "FAIL`t$base" }
    }
    elseif ($null -ne $a -and $null -ne $b) {
        $ja = $a | ConvertFrom-Json; $jb = $b | ConvertFrom-Json
        if (((Strip-Pos $ja) | ConvertTo-Json -Depth 20 -Compress) -ceq ((Strip-Pos $jb) | ConvertTo-Json -Depth 20 -Compress)) { $blindN++; $lines += "剥差同`t$base（pos/message 盲）" }
        elseif ($expected -contains $base) { $lines += "预期差(R-28)`t$base（e2e 样本吸收——深拷格废止语义）" }
        elseif ($base -eq "examples__模块系统__主.subl.json") { $lines += "预期差`t$base（模块样本——t146 既有登记差延续）" }
        else { $lines += "FAIL`t$base" }
    } else { $lines += "FAIL`t$base（产物缺失）" }
}
$lines | Set-Content "$root/jsoncmp/out-subs/t150gate-ledger.txt" -Encoding UTF8
$expDiff = ($lines | Where-Object { $_ -like "预期差*" }).Count
$failN = ($lines | Where-Object { $_ -like "FAIL*" }).Count
Write-Output "[parity] 68 门 vs t122n: SAME=$sameN 剥差同=$blindN 预期转绿=$turnGreen 预期差(R-28+模块)=$expDiff FAIL=$failN（台账: out-subs/t150gate-ledger.txt）"
if ($failN -gt 0) { Write-Output "[parity] FAIL: 68 门非预期结构差（$failN 件）"; exit $failN }
if ($turnGreen -ne 1 -or $sameN -ne 47 -or $blindN -ne 16 -or $expDiff -ne 4) { Write-Output "[parity] FAIL: 68 门计数波动（预期 SAME47/剥差同16/转绿1/预期差4）"; exit 1 }
Write-Output "[parity] OK: 68 门闭合（SAME 47 + 剥差同 16 + 预期转绿 1 + 预期差 4[三 e2e 吸收 + 模块样本]）"

# ---- ③ 精确差分（vs t149gate 冻结产物：唯三 e2e 吸收差 + 模块样本延续）----
$a149 = "$root/jsoncmp/out-subs/t149gate"
$sameD = 0; $diffD = @(); $expectedD = @()
foreach ($f in (Get-ChildItem $side -Filter "*.json")) {
    $alt = Join-Path $a149 $f.Name
    if (Test-Path $alt) {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameD++ }
        elseif ($expected -contains $f.Name) { $expectedD += $f.Name }
        elseif ($f.Name -eq 'examples__模块系统__主.subl__list.json') { $expectedD += $f.Name }
        else { $diffD += $f.Name }
    }
}
if (@($diffD).Count -ne 0) { Write-Output ("[parity] FAIL: 精确差分破坏（未登记差）: " + ($diffD -join ";")); exit 1 }
Write-Output "[parity] OK: 精确差分逐字节同 $sameD 件 + 登记差 $($expectedD.Count) 件 = 三 e2e 吸收 + list envelope 构造族（vs t149gate）"

# ---- ④ stdlib 侧基线（vs t143stdlib：全同或 list 件迁源面）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t150stdlib"
Remove-Item $std -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $std | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$std/examples__模块系统.json") -NoNewWindow -Wait | Out-Null } catch { }
$baseStd = "$root/jsoncmp/out-subs/t143stdlib"
$sameS = 0; $diffS = @()
foreach ($f in (Get-ChildItem $baseStd -Filter "*.json")) {
    if (Test-Path "$std/$($f.Name)") {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$std/$($f.Name)" -Raw)) { $sameS++ }
        else { $diffS += $f.Name }
    } else { $diffS += ($f.Name + "（缺产物）") }
}
if (@($diffS).Count -gt 1 -or ($diffS.Count -eq 1 -and $diffS[0] -ne 'examples__模块系统__list.json')) {
    Write-Output ("[parity] FAIL: stdlib 侧差异（预期仅 list 件构造族迁源面）: " + ($diffS -join ";")); exit 1
}
Write-Output "[parity] OK: stdlib 侧基线 $sameS 件同 + list 件构造族迁源面（t146 口径延续）"

# ---- ⑤ 确定性 9/9 + 脚本门 ----
$da = "$root/jsoncmp/out-subs/t150det-a"; $db = "$root/jsoncmp/out-subs/t150det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$da/examples__模块系统.json") -NoNewWindow -Wait | Out-Null } catch { }
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$db/examples__模块系统.json") -NoNewWindow -Wait | Out-Null } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 9) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 9）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
$gateRc = (Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "$root/jsoncmp/out-subs/run-gate.ps1", "-Mode", "gate") -NoNewWindow -Wait -PassThru).ExitCode
if ($gateRc -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate，exit=$gateRc）"; exit $gateRc }
Write-Output "[parity] PASSED: t150 R-28/R-29 机械证据五段全绿"
