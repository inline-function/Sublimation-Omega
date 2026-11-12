param([string]$Mode = 'export')
$ErrorActionPreference = 'Stop'
$Root  = 'F:\Sublimation'
$CP    = 'F:\Sublimation\frontend\build\install\frontend\lib\*'
$Main  = 'sublimation.frontend.cli.MainKt'
$List  = Get-Content -LiteralPath (Join-Path $Root 'jsoncmp\manifest\export-t113-list.txt')
$Target = Join-Path $Root 'jsoncmp\out\t125check'
if (-not (Test-Path $Target)) { New-Item -ItemType Directory -Path $Target | Out-Null }
foreach ($line in $List) {
  $parts = $line -split '\|';
  $src = Join-Path $Root $parts[0]; $dst = Join-Path $Root ($parts[1] -replace '/t113n/', '/t125check/')
  $argList = @('compile', $src, '-o', $dst)
  $oldEAP = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $output = & java -cp $CP $Main @argList 2>&1
  $ErrorActionPreference = $oldEAP
  $code = $LASTEXITCODE
}