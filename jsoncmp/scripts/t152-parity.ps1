# t152-parity 对拍脚本（诊断扩批一 / W 主战场 ×5——「代码有很大风险，但依旧可以通过编译」）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t152-parity.ps1
# 五段：① 判别性探针（五码七面绿 + 保守豁免四面：非递归深拷 / 尾递归 / 块尾投影 / 互斥分支）
#       ② 68 门差分台账（vs t122n 死冻锚：SAME 41 + 剥差同 11 + W 批一增量 11 + 预期差 4（t150/t151 延续） + 转绿 1;t152 波动 = W 增量件 ×5 码 authorized 台账）
#       ③ 精确差分（vs t151gate：逐字节全同 61 + 15 件 W 增量（滤五码后逐字同）+ 零未登记差）
#       ④ stdlib 侧基线（vs t143stdlib：5 件同 + 3 件 W 增量（滤后同）+ list 件构造族迁源面延续）
#       ⑤ 确定性 9/9 + 脚本门（run-gate）。
# 纪律：零分配 nid（W 检查禁 etype/sigOf——68 门逐字比对基线硬约束；块尾投影 / 保守豁免为噪声控制）。
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$javaExe = "F:\jdk-17.0.13.11-hotspot\bin\java.exe"
$cp = "$($root)/frontend/build/install/frontend/lib/*"
$probeDir = "$root/jsoncmp/out-subs"

function Run-Probe([string]$tag, [string]$ext, [string]$src) {
    $out = "$probeDir/t152-$tag.json"; $tmp = "$probeDir/t152-$tag.$ext"
    Set-Content -Path $tmp -Value $src -Encoding UTF8
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $tmp, "-o", $out) -Wait -NoNewWindow -RedirectStandardOutput "$probeDir/t152-$tag.out" } catch { }
    if (-not (Test-Path $out)) { return "MISSING" }
    return Get-Content $out -Raw
}

# ---- ① 判别性探针：五码七面绿（编译过 + 落 W）+ 保守豁免四面（零误报防护） ----
# r1:W-REF-ESCAPE 表达式体 @ref 直通返回
$r1 = Run-Probe "r1" "subl" "fun g(v : @ref List[Nat]) : @ref List[Nat] = v`n@impure fun main(): Null {`n  var xs : @mut List[Nat] = mutableListOf(1)`n  var rs : @ref List[Nat] = xs`n  var out : @ref List[Nat] = g(rs)`n  print(1)`n}"
if ($r1 -notmatch 'ok": true' -or $r1 -notmatch 'W-REF-ESCAPE') { Write-Output '[parity] FAIL: r1 表达式体 @ref 返回未绿（编译过 + 落 W-REF-ESCAPE）'; exit 1 }
# r2:W-REF-ESCAPE @ref 视图存入 @mut 字段
$r2 = Run-Probe "r2" "subs" "@mut struct Box(@mut fld : @ref List[Nat])`n@impure fun main(): Null {`n  var xs : @mut List[Nat] = mutableListOf(1)`n  var rs : @ref List[Nat] = xs`n  @mut var bb = Box(xs)`n  bb.fld = rs`n  print(1)`n}"
if ($r2 -notmatch 'ok": true' -or $r2 -notmatch 'W-REF-ESCAPE') { Write-Output '[parity] FAIL: r2 @mut 字段存 @ref 未绿'; exit 1 }
# r3:W-REF-ESCAPE 块体 return @ref
$r3 = Run-Probe "r3" "subl" "fun g(v : @ref List[Nat]) : @ref List[Nat] {`n  return v`n}`n@impure fun main(): Null {`n  var xs : @mut List[Nat] = mutableListOf(1)`n  var rs : @ref List[Nat] = xs`n  var out : @ref List[Nat] = g(rs)`n  print(1)`n}"
if ($r3 -notmatch 'ok": true' -or $r3 -notmatch 'W-REF-ESCAPE') { Write-Output '[parity] FAIL: r3 块体 return @ref 未绿'; exit 1 }
# d1:W-DEEP-COPY-LOOP 递归帧内深拷转换点
$d1 = Run-Probe "d1" "subl" "fun countdown(a : @mut List[Nat], n : Nat) : Nat {`n  var b : List[Nat] = a`n  return if (n == 0) 0 else countdown(a, n - 1)`n}`n@impure fun main(): Null { var xs : @mut List[Nat] = mutableListOf(1); print(countdown(xs, 3)) }"
if ($d1 -notmatch 'ok": true' -or $d1 -notmatch 'W-DEEP-COPY-LOOP') { Write-Output '[parity] FAIL: d1 递归帧深拷未绿'; exit 1 }
# d2（保守豁免）：非递归深拷免
$d2 = Run-Probe "d2" "subl" "fun once(a : @mut List[Nat]) : Nat {`n  var b : List[Nat] = a`n  return 0`n}`n@impure fun main(): Null { var xs : @mut List[Nat] = mutableListOf(1); print(once(xs)) }"
if ($d2 -notmatch 'ok": true' -or $d2 -match 'W-DEEP-COPY-LOOP') { Write-Output '[parity] FAIL: d2 非递归深拷误落 W-DEEP-COPY-LOOP'; exit 1 }
# i1:W-RESULT-IGNORED 纯函数调用结果丢弃（语句位、非块尾）
$i1 = Run-Probe "i1" "subl" "fun double(x : Nat) : Nat = x * 2`n@impure fun main(): Null { double(3); print(1) }"
if ($i1 -notmatch 'ok": true' -or $i1 -notmatch 'W-RESULT-IGNORED') { Write-Output '[parity] FAIL: i1 纯函数结果丢弃未绿'; exit 1 }
# i2（保守豁免）：块尾投影位（if 分支块）免
$i2 = Run-Probe "i2" "subl" "fun double(x : Nat) : Nat = x * 2`nfun g(x : Nat) : Nat = if (x == 0) { double(1) } else { double(2) }`n@impure fun main(): Null { print(g(3)) }"
if ($i2 -notmatch 'ok": true' -or $i2 -match 'W-RESULT-IGNORED') { Write-Output '[parity] FAIL: i2 块尾投影误落 W-RESULT-IGNORED'; exit 1 }
# i3（保守豁免）：块体函数侧豁免（语句式用法）
$i3 = Run-Probe "i3" "subl" "fun blk(x : Nat) : Nat { var y = x + 1; return y }`n@impure fun main(): Null { blk(3); print(1) }"
if ($i3 -notmatch 'ok": true' -or $i3 -match 'W-RESULT-IGNORED') { Write-Output '[parity] FAIL: i3 块体函数误落 W-RESULT-IGNORED'; exit 1 }
# t1:W-REC-NON-TAIL 非尾位递归（停机闸已过）
$t1 = Run-Probe "t1" "subl" "fun fact(n : Nat) : Nat = if (n == 0) 1 else n * fact(n - 1)`n@impure fun main(): Null { print(fact(3)) }"
if ($t1 -notmatch 'ok": true' -or $t1 -notmatch 'W-REC-NON-TAIL') { Write-Output '[parity] FAIL: t1 非尾位递归未绿'; exit 1 }
# t2（保守判据面）：尾位递归免（尾位判定正确性）
$t2 = Run-Probe "t2" "subl" "fun sumTo(n : Nat, acc : Nat) : Nat = if (n == 0) acc else sumTo(n - 1, acc + n)`n@impure fun main(): Null { print(sumTo(3, 0)) }"
if ($t2 -notmatch 'ok": true' -or $t2 -match 'W-REC-NON-TAIL') { Write-Output '[parity] FAIL: t2 尾位递归误落 W-REC-NON-TAIL'; exit 1 }
# u1:W-BRANCH-UNREACHABLE when 分支被无 guard 前序完全包含
$u1 = Run-Probe "u1" "subl" "fun f(xs : List[Nat]) : Nat = when (xs) { is Cons(_, _) -> 1; is Cons(a, b) -> 2; else -> 0 }`n@impure fun main(): Null { print(f(listOf(1))) }"
if ($u1 -notmatch 'ok": true' -or $u1 -notmatch 'W-BRANCH-UNREACHABLE') { Write-Output '[parity] FAIL: u1 前序包含分支未绿'; exit 1 }
# u2（保守判据面）：带 guard 前序不含；互斥分支免
$u2 = Run-Probe "u2" "subl" "fun f(xs : List[Nat]) : Nat = when (xs) { is Cons(h, t) if h > 0 -> 1; is Cons(a, b) -> 2; else -> 0 }`n@impure fun main(): Null { print(f(listOf(1))) }"
if ($u2 -notmatch 'ok": true' -or $u2 -match 'W-BRANCH-UNREACHABLE') { Write-Output '[parity] FAIL: u2 带 guard 前序误落 W-BRANCH-UNREACHABLE'; exit 1 }
Write-Output '[parity] OK: 五码七面绿（REF-ESCAPE×3 / DEEP-COPY-LOOP / RESULT-IGNORED / REC-NON-TAIL / BRANCH-UNREACHABLE）;保守豁免四面（非递归深拷 / 块尾投影 / 块体函数 / 带guard + 尾递归）'

# ---- ② 68 门差分台账（vs t122n 死冻锚——t152 波动 = W 批一增量 11 件 authorized 台账;SAME 41/剥差同 11 延续） ----
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
function Strip-NewW([object]$o) {
    if ($null -eq $o) { return $null }
    if ($o -is [array]) { return @($o | ForEach-Object { Strip-NewW $_ } | Where-Object { $null -ne $_ }) }
    if ($o -is [System.Collections.IDictionary] -or $o -is [pscustomobject]) {
        if ($o.PSObject.Properties["code"] -and ($o.code -in @("W-REF-ESCAPE","W-DEEP-COPY-LOOP","W-RESULT-IGNORED","W-REC-NON-TAIL","W-BRANCH-UNREACHABLE"))) { return $null }
        $h = [ordered]@{}
        foreach ($p in $o.PSObject.Properties) { $h[$p.Name] = Strip-NewW $p.Value }
        return [pscustomobject]$h
    }
    return $o
}
$manifest = Get-Content "$root/jsoncmp/manifest/export-t131mod-list.txt"
$side = "$root/jsoncmp/out-subs/t152gate"
Remove-Item $side -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $side | Out-Null
foreach ($line in $manifest) {
    $parts = $line -split "\|"
    $src = "$root/" + ($parts[0] -replace "/", [char]92)
    $base = ($parts[1] -replace "^jsoncmp/out/t131mod/", "")
    if ($base -eq "examples__模块系统.json") { $base = "examples__模块系统__主.subl.json" }
    $dst = "$side/$base"
    $dir = Split-Path $dst -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $src, "-o", $dst) -Wait -NoNewWindow } catch { }
}
$sameN = 0; $blindN = 0; $wIncN = 0; $expN = 0; $greenN = 0; $lines = @()
$wExpected = @("core__collection__list.subl.json","core__collection__map.subl.json","core__collection__set.subl.json","core__math__math.subl.json","examples___scratch_bill.subl.json","examples__日常-容器范型.subl.json","examples__日常-数列与区间.subl.json","examples__日常-账单统计.subl.json","examples__日常-通讯录.subl.json","examples__标准库集合.subl.json","examples__高阶函数与lambda.subl.json")
$expOld = @("examples__副作用与unchecked.subl.json","examples__特性巡礼.subl.json","examples__类型系统.subl.json","examples__模块系统__主.subl.json")
function Cmp-Node([string]$aPath, [string]$bPath, [string]$mode) {
    $r = Start-Process -FilePath "node" -ArgumentList @("$root/jsoncmp/scripts/t152-stripcmp.js", $aPath, $bPath, $mode) -Wait -NoNewWindow -PassThru
    return ($r.ExitCode -eq 0)
}
function Cmp-Out([string]$aPath, [string]$bPath, [string]$mode) {
    $txt = (& node "$root/jsoncmp/scripts/t152-stripcmp.js" $aPath $bPath $mode 2>$null) -join ""
    return ($txt -eq "SAME")
}
foreach ($line in $manifest) {
    $parts = $line -split "\|"
    $base = ($parts[1] -replace "^jsoncmp/out/t131mod/", "")
    if ($base -eq "examples__模块系统.json") { $base = "examples__模块系统__主.subl.json" }
    $dst = "$side/$base"
    $aPath = "$root/jsoncmp/out/t122n/$base"
    $bPath = $dst
    $a = Get-Content $aPath -Raw -ErrorAction SilentlyContinue
    $b = if (Test-Path $bPath) { Get-Content $bPath -Raw } else { $null }
    if ($a -ceq $b) { $sameN++; $lines += "SAME`t$base" }
    elseif ($null -ne $a -and $null -ne $b) {
        if (Cmp-Out $aPath $bPath "strippos") { $blindN++; $lines += "剥差同`t$base" }
        elseif (Cmp-Out $aPath $bPath "wstrippos") {
            $wIncN++; $lines += "W增量(批一)`t$base"
            if ($wExpected -notcontains $base) { Write-Output "[parity] FAIL: W 增量件未登记台账: $base"; $lines += "FAIL`t$base" }
        }
        elseif ($base -eq "examples__手动证明.subl.json") {
            if ($b -match 'ok":\s*true') { $greenN++; $lines += "预期转绿`t$base（t142 除名——口径延续）" } else { $lines += "FAIL`t$base" }
        }
        elseif ($expOld -contains $base) { $expN++; $lines += "预期差(R-28/模块)`t$base（t150/t146 既有登记差延续）" }
        else { $lines += "FAIL`t$base" }
    } else { $lines += "FAIL`t$base（产物缺失）" }
}
$lines | Set-Content "$root/jsoncmp/out-subs/t152gate-ledger.txt" -Encoding UTF8
$failN = ($lines | Where-Object { $_ -like "FAIL*" }).Count
Write-Output "[parity] 68 门 vs t122n: SAME=$sameN 剥差同=$blindN W增量(批一)=$wIncN 预期差(R-28/模块)=$expN 转绿=$greenN FAIL=$failN（台账: out-subs/t152gate-ledger.txt）"
if ($failN -gt 0) { Write-Output "[parity] FAIL: 68 门非预期结构差（$failN 件）"; exit $failN }
if ($sameN -ne 41 -or $blindN -ne 11 -or $wIncN -ne 11 -or $expN -ne 4 -or $greenN -ne 1) { Write-Output "[parity] FAIL: 68 门计数波动（.Expect SAME=41 剥差同=11 W增量=11 预期差=4 转绿=1）"; exit 1 }
Write-Output "[parity] OK: 68 门闭合（SAME 41 + 剥差同 11 + W 批一增量 11[authorized 台账] + 预期差 4[t150/t146 延续] + 转绿 1）——老样本零结构性回归，波动仅批一 W 增量"

# ---- ③ 精确差分（vs t151gate 冻结产物：61 件逐字节全同 + 15 件 W 增量（滤五码后逐字同）+ 零未登记差） ----
$a151 = "$root/jsoncmp/out-subs/t151gate"
$sameD = 0; $wD = 0; $diffD = @()
foreach ($f in (Get-ChildItem $side -Filter "*.json")) {
    $alt = Join-Path $a151 $f.Name
    if (Test-Path $alt) {
        $a = Get-Content $alt -Raw; $b = Get-Content $f.FullName -Raw
        if ($a -ceq $b) { $sameD++ }
        elseif (Cmp-Out $alt $f.FullName "wstrip") { $wD++ }
        else { $diffD += $f.Name }
    }
}
if (@($diffD).Count -ne 0) { Write-Output ("[parity] FAIL: 精确差分破坏（未登记差）: " + ($diffD -join ";")); exit 1 }
if ($sameD -ne 61 -or $wD -ne 15) { Write-Output "[parity] FAIL: 精确差分计数波动（Expect 逐字同=61 W增量=15；实得 同=$sameD W=$wD）"; exit 1 }
Write-Output "[parity] OK: 精确差分 $sameD 件逐字节全同 + $wD 件 W 增量（滤五码后逐字同——nid 位零漂移，零分配 nid 纪律成立）"

# ---- ④ stdlib 侧基线（vs t143stdlib：5 件同 + 3 件 W 增量 + list 件构造族迁源面延续） ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t152stdlib"
Remove-Item $std -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $std | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$std/examples__模块系统.json") -Wait -NoNewWindow } catch { }
$baseStd = "$root/jsoncmp/out-subs/t143stdlib"
$sameS = 0; $wS = 0; $diffS = @()
foreach ($f in (Get-ChildItem $baseStd -Filter "*.json")) {
    if (Test-Path "$std/$($f.Name)") {
        $a = Get-Content $f.FullName -Raw; $b = Get-Content "$std/$($f.Name)" -Raw
        if ($a -ceq $b) { $sameS++ }
        elseif (Cmp-Out $f.FullName "$std/$($f.Name)" "wstrip") { $wS++ }
        else { $diffS += $f.Name }
    } else { $diffS += ($f.Name + "（缺产物）") }
}
if (@($diffS).Count -ne 1 -or $diffS[0] -ne "examples__模块系统__list.json") { Write-Output ("[parity] FAIL: stdlib 侧差异（预期仅 list 件构造族迁源面 + W 批一混合）: " + ($diffS -join ";")); exit 1 }
if ($sameS -ne 5 -or $wS -ne 3) { Write-Output "[parity] FAIL: stdlib 计数波动（Expect 同=5 W增量=3；实得 同=$sameS W=$wS）"; exit 1 }
Write-Output "[parity] OK: stdlib 侧基线 $sameS 件同 + $wS 件 W 增量（map/optional/set——滤后同）+ list 件构造族迁源面（t146 口径延续 + W 批一增量）"

# ---- ⑤ 确定性 9/9 + 脚本门 ----
$da = "$root/jsoncmp/out-subs/t152det-a"; $db = "$root/jsoncmp/out-subs/t152det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$da/examples__模块系统.json") -Wait -NoNewWindow } catch { }
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$db/examples__模块系统.json") -Wait -NoNewWindow } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 9) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 9）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
& "$root/jsoncmp/out-subs/run-gate.ps1"
$gateRc = $LASTEXITCODE
if ($gateRc -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate，exit=$gateRc）"; exit $gateRc }
Write-Output "[parity] PASSED: t152 诊断扩批一 机械证据五段全绿"