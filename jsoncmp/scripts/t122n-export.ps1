param([string]$Mode = 'export')
$ErrorActionPreference = 'Stop'
$Root  = 'F:\Sublimation'
$CP    = 'F:\Sublimation\frontend\build\install\frontend\lib\*'
$Main  = 'sublimation.frontend.cli.MainKt'
$List  = Get-Content -LiteralPath 'F:\Sublimation\jsoncmp\manifest\export-t113-list.txt'
$Target = Join-Path $Root 'jsoncmp\out\t122n'
if (-not (Test-Path $Target)) { New-Item -ItemType Directory -Path $Target | Out-Null }
$jobs = @()
foreach ($line in $List) {
  $parts = $line -split '\|';
  $src = Join-Path $Root $parts[0]; $dst = Join-Path $Root ($parts[1] -replace '/t113n/', '/t122n/')
  $log = $dst -replace '\.json$', '.log'
  $argList = @('compile', $src, '-o', $dst)
  $oldEAP = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $output = & java -cp $CP $Main @argList 2>&1
  $ErrorActionPreference = $oldEAP
  $code = $LASTEXITCODE
  $txt = @("cmd: java -cp lib/* $Main compile $($parts[0]) -o jsoncmp/out/t122n/$(Split-Path $dst -Leaf)", "exit: $code", '----- output -----') + $output
  Set-Content -Path $log -Value $txt -Encoding UTF8
  $jobs += [pscustomobject]@{ src=$parts[0]; leaf=(Split-Path $dst -Leaf); code=$code }
}
Write-Output ("exported=" + $jobs.Count)
$jobs | Where-Object { $_.code -ne 0 } | ForEach-Object { Write-Output ("  non-zero: " + $_.leaf + " exit=" + $_.code) }
