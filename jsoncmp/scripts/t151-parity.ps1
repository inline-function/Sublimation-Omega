# t151-parity 对拍脚本（R-26.4 同域修改=纯：E-IMPURE-REQUIRED 判据收窄——机械证据五段）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t151-parity.ps1
# 五段：① 行为探针（判别性翻绿三面 + 负例三面红 + 两路放行零回归）
#       ② 68 门差分台账（vs t122n 死冻锚——t150 预期差 4 延续;t151 波动 0：样本中零纯域局部接收者调用）
#       ③ 精确差分（vs t150gate 冻结产物：逐字节全同——判据收窄为闸行为面，产物面零差）
#       ④ stdlib 侧基线（vs t143stdlib：list 件构造族迁源面延续）⑤ 确定性 9/9 + 脚本门。
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$javaExe = "F:\jdk-17.0.13.11-hotspot\bin\java.exe"
$cp = "$($root)/frontend/build/install/frontend/lib/*"
$probeDir = "$root/jsoncmp/out-subs"

function Run-Probe([string]$tag, [string]$src) {
    $out = "$probeDir/t151-$tag.json"; $tmp = "$probeDir/t151-$tag.subl"
    Set-Content -Path $tmp -Value $src -Encoding UTF8
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $tmp, "-o", $out) -Wait -NoNewWindow | Out-Null } catch { }
    if (-not (Test-Path $out)) { return "MISSING" }
    return Get-Content $out -Raw
}

# ---- ① 行为探针：判别性翻绿三面（局部构造族 / 复制格代入 / @ref 注解位复制格）;负例三面红（借入形参 / 祖先帧捕获 / REFMUT 接收位拒格）;两路放行零回归 ----
# 判别性 1:局部构造族接收者在纯函数体内调 @mut 方法 ⇒ 翻绿（t141 现状误拦面消除）
$p1 = Run-Probe "p1" "fun f(): Nat {`n    var xs : @mut List[Nat] = mutableListOf(1)`n    xs.add(2)`n    return 0`n}`n@impure fun main(): Null { f(); print(1) }"
if ($p1 -notmatch 'ok": true') { Write-Output '[parity] FAIL: R-26.4 局部构造族接收者未翻绿'; exit 1 }
if ($p1 -match 'E-IMPURE-REQUIRED') { Write-Output '[parity] FAIL: R-26.4 局部接收者仍落 E-IMPURE-REQUIRED'; exit 1 }
# 判别性 2:复制格代入（R-28 MUT→MUT 深拷新对象）⇒ 来源局部 ⇒ 翻绿
$p2 = Run-Probe "p2" "fun f(): Nat {`n    var a : @mut List[Nat] = mutableListOf(1)`n    var u : @mut List[Nat] = a`n    u.add(3)`n    return 0`n}`n@impure fun main(): Null { f(); print(1) }"
if ($p2 -notmatch 'ok": true') { Write-Output '[parity] FAIL: 复制格代入局部未翻绿'; exit 1 }
if ($p2 -match 'D-DEEP-COPY') { } else { Write-Output '[parity] FAIL: 复制格代入未落 R-29 D-DEEP-COPY 补注（回归）'; exit 1 }
# 判别性 3:@ref 注解位 + @mut 类型 = MUT→MUT 复制格（@ref 不折入类型）⇒ 局部 ⇒ 翻绿
$p3 = Run-Probe "p3" "fun f(): Nat {`n    var a : @mut List[Nat] = mutableListOf(1)`n    @ref var v : @mut List[Nat] = a`n    v.add(3)`n    return 0`n}`n@impure fun main(): Null { f(); print(1) }"
if ($p3 -notmatch 'ok": true') { Write-Output '[parity] FAIL: @ref 注解位复制格未翻绿'; exit 1 }
# 负例 1:借入形参通道（含 @mut want 值参——R5 保守地板：& 借用歧义）⇒ 维持拦
$n1 = Run-Probe "n1" "fun f(ys : @mut List[Nat]): Null { ys.add(2) }`n@impure fun main(): Null { var v : @mut List[Nat] = mutableListOf(1); f(v); print(1) }"
if ($n1 -notmatch 'E-IMPURE-REQUIRED') { Write-Output '[parity] FAIL: 借入形参通道未维持 E-IMPURE-REQUIRED'; exit 1 }
# 负例 2:祖先帧捕获（E-MUT-CAPTURE 互补——捕获即外域）
$n2 = Run-Probe "n2" "fun outer(): Nat {`n    @mut var xs = mutableListOf(1)`n    fun inner(): Nat { xs.add(2); return 0 }`n    return inner()`n}`n@impure fun main(): Null { outer(); print(1) }"
if ($n2 -notmatch 'E-MUT-CAPTURE' -and $n2 -notmatch 'E-IMPURE-REQUIRED') { Write-Output '[parity] FAIL: 祖先帧捕获未维持拦'; exit 1 }
# 负例 3:REFMUT 接收位拒格（R-28 REF→MUT——可写借入不经类型位 @ref 通道）
$n3 = Run-Probe "n3" "fun f(ys : @mut List[Nat]): Null {`n    var w : @ref @mut List[Nat] = ys`n    w.add(2)`n}`n@impure fun main(): Null { var v : @mut List[Nat] = mutableListOf(1); f(v); print(1) }"
if ($n3 -notmatch 'E-TYPE-MISMATCH') { Write-Output '[parity] FAIL: REFMUT 接收位未按 R-28 拒格'; exit 1 }
# 两路放行零回归（@impure / unchecked）
$w1 = Run-Probe "w1" "@impure fun m(): Null { var xs : @mut List[Nat] = mutableListOf(1); xs.add(2); print(xs) }"
if ($w1 -notmatch 'ok": true') { Write-Output '[parity] FAIL: @impure 调用方放行回归'; exit 1 }
$w2 = Run-Probe "w2" "fun g(): Nat { var xs : @mut List[Nat] = mutableListOf(1); unchecked xs.add(2); return 0 }`n@impure fun main(): Null { g(); print(1) }"
if ($w2 -notmatch 'ok": true') { Write-Output '[parity] FAIL: unchecked 单语句豁免回归'; exit 1 }
Write-Output '[parity] OK: 判别性翻绿三面（构造族/复制格/@ref 注解位）;负例三面红（借入/捕获/REFMUT 拒格）;两路放行零回归'

# ---- ② 68 门差分台账（vs t122n 死冻锚;t150 预期差 4 延续 = 三 e2e 吸收 + 模块样本;t151 波动 0）----
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
$side = "$root/jsoncmp/out-subs/t151gate"
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
    try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $src, "-o", $dst) -Wait -NoNewWindow | Out-Null } catch { }
    $a = Get-Content "$root/jsoncmp/out/t122n/$base" -Raw -ErrorAction SilentlyContinue
    $b = if (Test-Path $dst) { Get-Content $dst -Raw } else { $null }
    if ($a -ceq $b) { $sameN++; $lines += "SAME`t$base" }
    elseif ($base -eq "examples__手动证明.subl.json") {
        if ($b -match 'ok":\s*true') { $turnGreen++; $lines += "预期转绿`t$base（t142 除名——口径延续）" }
        else { $lines += "FAIL`t$base" }
    }
    elseif ($null -ne $a -and $null -ne $b) {
        $ja = $a | ConvertFrom-Json; $jb = $b | ConvertFrom-Json
        if (((Strip-Pos $ja) | ConvertTo-Json -Depth 20 -Compress) -ceq ((Strip-Pos $jb) | ConvertTo-Json -Depth 20 -Compress)) { $blindN++; $lines += "剥差同`t$base" }
        elseif ($expected -contains $base) { $lines += "预期差(R-28)`t$base（t150 e2e 吸收——深拷格废止语义，t151 延续）" }
        elseif ($base -eq "examples__模块系统__主.subl.json") { $lines += "预期差`t$base（模块样本——t146 既有登记差延续）" }
        else { $lines += "FAIL`t$base" }
    } else { $lines += "FAIL`t$base（产物缺失）" }
}
$lines | Set-Content "$root/jsoncmp/out-subs/t151gate-ledger.txt" -Encoding UTF8
$expDiff = ($lines | Where-Object { $_ -like "预期差*" }).Count
$failN = ($lines | Where-Object { $_ -like "FAIL*" }).Count
Write-Output "[parity] 68 门 vs t122n: SAME=$sameN 剥差同=$blindN 预期转绿=$turnGreen 预期差(R-28+模块)=$expDiff FAIL=$failN（台账: out-subs/t151gate-ledger.txt）"
if ($failN -gt 0) { Write-Output "[parity] FAIL: 68 门非预期结构差（$failN 件）"; exit $failN }
if ($turnGreen -ne 1 -or $sameN -ne 47 -or $blindN -ne 16 -or $expDiff -ne 4) { Write-Output "[parity] FAIL: 68 门计数波动（预期 SAME47/剥差同16/预期转绿1/预期差4）"; exit 1 }
Write-Output "[parity] OK: 68 门闭合（SAME 47 + 剥差同 16 + 预期转绿 1 + 预期差 4[t150 延续]——t151 波动 0）"

# ---- ③ 精确差分（vs t150gate 冻结产物：逐字节全同——t151 判据收窄为闸行为面，产物面零差）----
$a150 = "$root/jsoncmp/out-subs/t150gate"
$sameD = 0; $diffD = @()
foreach ($f in (Get-ChildItem $side -Filter "*.json")) {
    $alt = Join-Path $a150 $f.Name
    if (Test-Path $alt) {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameD++ }
        else { $diffD += $f.Name }
    }
}
if (@($diffD).Count -ne 0) { Write-Output ("[parity] FAIL: 精确差分破坏（未登记差）: " + ($diffD -join ";")); exit 1 }
Write-Output "[parity] OK: 精确差分逐字节全同 $sameD 件（t151 波动 0——闸行为面收窄，产物面零差，OQ-10 报告态先行）"

# ---- ④ stdlib 侧基线（vs t143stdlib：全同或 list 件迁源面）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t151stdlib"
Remove-Item $std -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $std | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$std/examples__模块系统.json") -Wait -NoNewWindow | Out-Null } catch { }
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
$da = "$root/jsoncmp/out-subs/t151det-a"; $db = "$root/jsoncmp/out-subs/t151det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$da/examples__模块系统.json") -Wait -NoNewWindow | Out-Null } catch { }
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$db/examples__模块系统.json") -Wait -NoNewWindow | Out-Null } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 9) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 9）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
$gateRc = (Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "$root/jsoncmp/out-subs/run-gate.ps1") -Wait -NoNewWindow -PassThru).ExitCode
if ($gateRc -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate，exit=$gateRc）"; exit $gateRc }
Write-Output "[parity] PASSED: t151 R-26.4 机械证据五段全绿"
