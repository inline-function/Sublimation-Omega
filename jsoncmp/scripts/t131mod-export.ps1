param([ValidateSet('export','verify')][string]$Mode = 'export')
$ErrorActionPreference = 'Stop'
$Root  = 'F:\Sublimation'
$CP    = 'F:\Sublimation\frontend\build\install\frontend\lib\*'
$Main  = 'sublimation.frontend.cli.MainKt'
$List  = Get-Content -LiteralPath (Join-Path $Root 'jsoncmp\manifest\export-t131mod-list.txt')
$Target = Join-Path $Root 'jsoncmp\out\t131mod'
# t131（S7 / R-22）：目录即模块 e2e 导——67 单文件样本 + 模块系统目录形态（OQ-10 新侧基线）。
# 目录行：compile <目录> -o <dst>——主 envelope 落 dst，挂载子模块落 <dst 基名>__<相对路径>.json（自动）。
remove-item -LiteralPath $Target -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $Target | Out-Null
$jobs = @()
foreach ($line in $List) {
  $parts  = $line -split '\|'
  $srcRel = $parts[0]; $dstRel = $parts[1]
  $src = Join-Path $Root $srcRel; $dst = Join-Path $Root $dstRel
  $argList = if (Test-Path -LiteralPath $src -PathType Container) { @('compile', $src, '-o', $dst) }
              else { @('compile', $src, '-o', $dst) }
  $oldEAP = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $output = & java -cp $CP $Main @argList 2>&1
  $ErrorActionPreference = $oldEAP
  $code = $LASTEXITCODE
  $leaf = if (Test-Path -LiteralPath $src -PathType Container) { '目录: ' + $srcRel } else { Split-Path $dst -Leaf }
  $log = $dst -replace '\.json$', '.log'
  $txt = @("cmd: java ... $Main compile $srcRel -o $dstRel", "exit: $code", '----- output -----') + $output
  Set-Content -Path $log -Value $txt -Encoding UTF8
  $jobs += [pscustomobject]@{ src=$srcRel; leaf=$leaf; code=$code }
}
Write-Output ("exported=" + $jobs.Count)
$jobs | Where-Object { $_.code -ne 0 } | ForEach-Object { Write-Output ("  non-zero: " + $_.leaf + " exit=" + $_.code) }
# 模块样本目录形态的子产物随主 -o 自动落盘（ex: examples__模块系统__tool.json）
Write-Output ("module children: " + (Get-ChildItem -LiteralPath $Target -Filter 'examples__*' | Where-Object { $_.Name -like '*__tool*.json' }).Count.ToString())