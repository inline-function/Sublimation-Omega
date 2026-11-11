$ErrorActionPreference = 'Continue'
$lib = 'C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
$cp = 'F:/Sublimation/frontend/probe-mut/cls;' + $lib
$dir = 'F:/Sublimation/frontend/probe-mut/cases'
$scripts = @('c1-field-write-ok', 'c1b-field-write-stmt-mut', 'c2-field-write-immfield', 'c3-field-write-immrecv', 'c4-field-read-demote', 'c5-reassign-mut-typevar', 'd1-alias-pass', 'd2-alias-param', 'd3-write-via-param', 'd4-nonmut-to-mut-param', 'd5-造格-local-struct', 'd6-demote-local-struct', 'b4-nat-造格-varinit', 'g1-mut-to-plain-param', 'g2-plain-to-mut-param', 'e4-mutex-lambda', 'e4b-mutex-mutview', 'h1-nested-struct-field', 'h2-list-of-mut')
foreach ($f in (Get-ChildItem -Path $dir -Filter *.subl | Sort-Object Name)) {
  $args = @($f.FullName)
  if ($scripts -contains $f.BaseName) { $args += '--script' }
  $log = $f.FullName + '.log'
  & java -cp $cp sublimation.frontend.cli.MainKt check @args > $log 2>&1
  $code = $LASTEXITCODE
  $diag = (Get-Content $log | Where-Object { $_ -match '^\[错误\]' } | Select-Object -First 2) -join ' ;; '

  Write-Host ($f.BaseName.PadRight(26) + ' code=' + $code + '  ' + $diag)
}