param([ValidateSet('baseline','gate')][string]$Mode = 'gate')
$ErrorActionPreference = 'Stop'
$Root    = 'F:\Sublimation\jsoncmp\out-subs'
$CP      = 'F:\Sublimation\frontend\build\install\frontend\lib\*'
$Main    = 'sublimation.frontend.cli.MainKt'
$Samples = Join-Path $Root 'samples'
$Target  = if ($Mode -eq 'baseline') { Join-Path $Root 't01' } else { Join-Path $Root 'run2' }
if (-not (Test-Path $Target)) { New-Item -ItemType Directory -Path $Target | Out-Null }

# 运行清单：src 样本 / suffix 产物后缀 / noDegrade 旗标 / 期望退出码（当前树实跑口径）
$Runs = @(
  @{ src='conv-forward.subs';              suffix='';    nd=$false; expect=0 },
  @{ src='conv-use-first.subs';            suffix='';    nd=$false; expect=0 },
  @{ src='conv-negative-polarity.subs';    suffix='';    nd=$false; expect=0 },
  @{ src='conv-field.subs';                suffix='';    nd=$false; expect=0 },
  @{ src='conv-multi-use.subs';            suffix='';    nd=$false; expect=0 },
  @{ src='ret-infer.subs';                 suffix='';    nd=$false; expect=0 },
  @{ src='ret-infer.subl';                 suffix='';    nd=$false; expect=0 },
  @{ src='ret-infer-arith.subs';           suffix='';    nd=$false; expect=0 },
  @{ src='control-fully-annotated.subs';   suffix='';    nd=$false; expect=0 },
  @{ src='degrade-tuple-grow.subs';        suffix='';    nd=$false; expect=0 },
  @{ src='nosol.subs';                     suffix='';    nd=$false; expect=1 },
  @{ src='nosol.subl';                     suffix='';    nd=$false; expect=1 },
  @{ src='degrade-tuple-grow.subs';        suffix='.nd'; nd=$true;  expect=1 },
  @{ src='conv-forward.subs';              suffix='.nd'; nd=$true;  expect=0 }
)

function Invoke-Run($r) {
  $srcPath  = Join-Path $Samples $r.src
  $outName  = $r.src + $r.suffix + '.json'
  $jsonPath = Join-Path $Target $outName
  $logName  = $outName -replace '\.json$', '.log'
  $logPath  = Join-Path $Target $logName
  $argList  = @('compile', $srcPath, '-o', $jsonPath, '-v')
  $flagText = ''
  if ($r.nd) { $argList += '--no-degrade'; $flagText = ' --no-degrade' }
  $cmdText  = 'java -cp "' + $CP + '" ' + $Main + ' compile "' + $srcPath + '" -o "' + $jsonPath + '" -v' + $flagText
  $oldEAP = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  $p = Start-Process -FilePath 'F:\jdk-17.0.13.11-hotspot\bin\java.exe' -ArgumentList (@('-cp', $CP, $Main) + $argList) -NoNewWindow -Wait -PassThru -RedirectStandardOutput "$logPath.out" -RedirectStandardError "$logPath.err"
  $output = @(Get-Content "$logPath.out" -ErrorAction SilentlyContinue) + @(Get-Content "$logPath.err" -ErrorAction SilentlyContinue)
  Remove-Item "$logPath.out", "$logPath.err" -Force -ErrorAction SilentlyContinue
  $ErrorActionPreference = $oldEAP
  $code     = if ($null -ne $p) { $p.ExitCode } else { $LASTEXITCODE }
  $lines = @("cmd: $cmdText", "exit: $code", '----- output -----') + $output
  Set-Content -Path $logPath -Value $lines -Encoding UTF8
  return @{ outName = $outName; code = $code; jsonPath = $jsonPath; logPath = $logPath }
}

$results = @()
foreach ($r in $Runs) { $results += (Invoke-Run $r) }
Write-Output ("[$Mode] runs: " + $results.Count + " products into " + $Target)

if ($Mode -eq 'baseline') {
  Write-Output '[baseline] 期望产物已由当前树实跑落盘（含红态：nosol* 退出 1，nd 门样本退出 1）'
  $results | ForEach-Object { Write-Output ("  " + $_.outName + " exit=" + $_.code) }
  exit 0
}

# -------- gate 模式：双编译逐字比对 + 退出码 + 语义抽查 --------
$baseDir = Join-Path $Root 't01'
$same = 0
$failures = @()
foreach ($res in $results) {
  $a = Get-Content -Raw -LiteralPath (Join-Path $baseDir $res.outName)
  $b = Get-Content -Raw -LiteralPath $res.jsonPath
  if ($null -ne $a -and $null -ne $b -and $a -ceq $b) { $same++ }
  else { $failures += ('DIFF(字节): ' + $res.outName) }
}
$i = 0
foreach ($r in $Runs) {
  $res = $results[$i]; $i++
  if ($res.code -ne $r.expect) { $failures += ("EXIT: " + $res.outName + ' 期望=' + $r.expect + ' 实际=' + $res.code) }
}

function Check-Codes($outName, $codes, $must) {
  $json = Get-Content -Raw -LiteralPath (Join-Path $baseDir $outName)
  $log  = Get-Content -Raw -LiteralPath (Join-Path $baseDir ($outName -replace '\.json$', '.log'))
  foreach ($c in $codes) {
    $present = ($json -match [regex]::Escape($c)) -or ($log -match [regex]::Escape($c))
    if ($must -and -not $present) { $script:failures += ("缺码 $c 于 $outName") }
    if (-not $must -and $present) { $script:failures += ("不应出现 $c 于 $outName") }
  }
}
Check-Codes 'nosol.subs.json'            @('E-INFER-NO-SOLUTION') $true
Check-Codes 'nosol.subl.json'            @('E-OMITTED-TYPE', 'E-TYPE-MISMATCH') $true
Check-Codes 'nosol.subl.json'            @('D-INFER-DEGRADED') $false
Check-Codes 'degrade-tuple-grow.subs.nd.json' @('E-INFER-DEGRADE-FORBIDDEN') $true
Check-Codes 'degrade-tuple-grow.subs.nd.json' @('D-INFER-DEGRADED') $false
Check-Codes 'degrade-tuple-grow.subs.json'    @('D-INFER-DEGRADED') $true
Check-Codes 'degrade-tuple-grow.subs.json'    @('E-INFER-NO-SOLUTION') $false
Check-Codes 'conv-forward.subs.json'    @('E-') $false
$cfg = Get-Content -Raw -LiteralPath (Join-Path $baseDir 'ret-infer.subl.json')
if ($cfg -notmatch '"mode":\s*"project"') { $failures += 'ret-infer.subl 工程模式未落 project' }
$csf = Get-Content -Raw -LiteralPath (Join-Path $baseDir 'ret-infer.subs.json')
if ($csf -notmatch '"mode":\s*"script"') { $failures += 'ret-infer.subs 脚本模式未落 script' }

Write-Output ("[gate] SAME=" + $same + "/" + $results.Count + " (双编译逐字节)")
if ($failures.Count -gt 0) {
  Write-Output '[gate] FAILED:'
  $failures | ForEach-Object { Write-Output ('  ' + $_) }
  exit 1
}
Write-Output '[gate] PASSED: 双编译逐字同 + 退出码符合 + 语义抽查全过'
exit 0
