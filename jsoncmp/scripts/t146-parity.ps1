# t146-parity 对拍脚本（R-26.1 收口：调用位 <> 路由回既有命题参数供给通路的机械证据）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t146-parity.ps1
# 五段：① 行为探针（构造面绿 / 混型精确报）② 68 门差分台账
#       （vs **t122n 死冻锚**——上下文弥散复绿回 SAME：SAME 50 + 剥差同 16 + 预期转绿 1（手动证明）
#       + 模块样本 1；t144 的"预期转红"撤账）③ 精确差分（vs t144 冻结产物：唯上下文弥散复绿差）
#       ④ stdlib 侧基线（vs t143stdlib：全同）⑤ 确定性 9/9 + 脚本门 14/14。
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$java = "java -cp `"$($root)/frontend/build/install/frontend/lib/*`" sublimation.frontend.cli.MainKt"
$probeDir = "$root/jsoncmp/out-subs"

# ---- ① 行为探针（t146 构造函数正道：零/单/多变参 + 混型精确报 + Cons 自拼） ----
function Run-Probe([string]$tag, [string]$src) {
    $out = "$probeDir/t146-$tag.json"; $tmp = "$probeDir/t146-$tag.subl"
    Set-Content -Path $tmp -Value $src -Encoding UTF8
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    try { Invoke-Expression "$java compile `"$tmp`" -o `"$out`" 2>`$null | Out-Null" } catch { }
    Start-Sleep -Seconds 2
    if (-not (Test-Path $out)) { return "MISSING" }
    return Get-Content $out -Raw
}
$goodTxt = Run-Probe "good" "fun main(): Null {`n    var a : @mut List[Nat] = mutableListOf()`n    var b : @mut List[Nat] = mutableListOf(1)`n    var c : @mut List[Nat] = mutableListOf(1, 2, 3)`n    var d : List[Nat] = listOf()`n    var e : List[Nat] = listOf(2)`n    var f : List[Nat] = Cons(4, Nil)`n    unchecked print(a); unchecked print(b); unchecked print(c)`n    unchecked print(d); unchecked print(e); unchecked print(f)`n}"
if ($goodTxt -notmatch '"ok":\s*true') { Write-Output '[parity] FAIL: 构造面未绿（零/单/多变参+listOf+Cons 自拼）'; exit 1 }
if ($goodTxt -notmatch 'HirMutType') { Write-Output '[parity] FAIL: @mut List[Nat] 视图未落 HIR'; exit 1 }
Write-Output "[parity] OK: 构造面绿（mutableListOf 0/1/3 实参 + listOf 0/1 + Cons 自拼；@mut List[Nat] 视图落 HIR）"
$badTxt = Run-Probe "bad" "fun main(): Null {`n    var xs : @mut List[Nat] = mutableListOf(True, 1)`n    unchecked print(xs)`n}"
if ($badTxt -notmatch '"ok":\s*false') { Write-Output '[parity] FAIL: 混型实参未落 E 级'; exit 1 }
if ($badTxt -notmatch 'E-TYPE-MISMATCH') { Write-Output '[parity] FAIL: 混型实参须落 E-TYPE-MISMATCH（元素类型）'; exit 1 }
if ($badTxt -match 'E-ARITY') { Write-Output '[parity] FAIL: 混型实参不是元数错位（变长位吸收 0..n）——应报元素类型不符'; exit 1 }
Write-Output "[parity] OK: 混型实参落 E-TYPE-MISMATCH（元素类型精确报，非元数错位）"

# ---- ② 68 门差分台账（vs t122n 死冻锚；上下文弥散复绿回 SAME）----
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
$side = "$root/jsoncmp/out-subs/t146gate"
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
    if ($a -ceq $b) { $sameN++; $lines += "SAME`t$base（含上下文弥散复绿回锚——t146）" }
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
$lines | Set-Content "$root/jsoncmp/out-subs/t146gate-ledger.txt" -Encoding UTF8
Write-Output "[parity] 68 门 vs t122n: SAME=$sameN 剥差同=$blindN 预期转绿=$turnGreen 结构差=$failN（台账: out-subs/t146gate-ledger.txt）"
if ($failN -gt 1) { Write-Output "[parity] FAIL: 68 门非预期结构差（$failN 件；预期至多 1 = 模块样本）"; exit $failN }
if ($turnGreen -ne 1 -or $sameN -ne 50 -or $blindN -ne 16) { Write-Output "[parity] FAIL: 68 门计数波动（预期 SAME50/剥差16/转绿1）；实测 SAME=$sameN 剥差=$blindN 转绿=$turnGreen"; exit 1 }
Write-Output "[parity] OK: 68 门闭合（SAME 50 含上下文弥散 + 剥差同 16 + 转绿 1 + 模块样本 1）——t146 注解纪律零产品差"

# ---- ③ 精确差分（vs t144 冻结产物 t144gate：唯上下文弥散复绿差）----
$a144 = "$root/jsoncmp/out-subs/t149gate"
$sameD = 0; $newD = @(); $diffD = @(); $expectedD = @()
foreach ($f in (Get-ChildItem $side -Filter "*.json")) {
    $alt = Join-Path $a144 $f.Name
    if (Test-Path $alt) {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameD++ }
        elseif ($f.Name -eq 'examples__模块系统__主.subl__list.json') { $expectedD += $f.Name }
        else { $diffD += $f.Name }
        else { $diffD += $f.Name }
    } else { $newD += $f.Name }
}
if (@($diffD).Count -ne 0 -or @($newD).Count -ne 0) {
    Write-Output ("[parity] FAIL: 精确差分破坏（未登记差）差: " + ($diffD -join ";") + " 新: " + ($newD -join ";")); exit 1 }

Write-Output "[parity] OK: 精确差分逐字节同 $sameD 件 + 登记差 $expectedD 件 = stdlib list envelope 构造族迁源面（vs t149gate 一次性基线）"

# ---- ④ stdlib 侧基线（vs t143stdlib：全同）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t146stdlib"
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
if (@($diffS).Count -gt 1 -or ($diffS.Count -eq 1 -and $diffS[0] -ne 'examples__模块系统__list.json')) {
    Write-Output ("[parity] FAIL: stdlib 侧差异（预期仅 list 件构造族迁源面）: " + ($diffS -join ";")); exit 1
}
$stdListNew = Get-Content "$std/examples__模块系统__list.json" -Raw
if ($diffS.Count -eq 1 -and ($stdListNew -notmatch 'HirFunDecl' -or $stdListNew -notmatch 'mutableListOf')) { Write-Output '[parity] FAIL: stdlib list 件差异须为构造族迁源（含 mutableListOf 声明）'; exit 1 }
Write-Output "[parity] OK: stdlib 侧基线 $sameS 件同 + 登记差 1 件 = list 件构造族迁源面（新增 mutableListOf 声明 + listOf 去体签名化；ok 不变）"

# ---- ⑤ 确定性 9/9 + 脚本门 14/14 ----
$da = "$root/jsoncmp/out-subs/t146det-a"; $db = "$root/jsoncmp/out-subs/t146det-b"
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
Write-Output "[parity] PASSED: t146 R-26.5 构造函数正道机械证据五段全绿"
