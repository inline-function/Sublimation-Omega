$ErrorActionPreference = 'Continue'
$lib = 'C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
$cp = 'F:/Sublimation/frontend/probe-mut/cls;' + $lib
$dir = 'F:/Sublimation/frontend/probe-mut/cases'
$scripts = @('a2-local', 'b2-local-demote', 'b4-local-造格', 'b5-bool-op-on-mut', 'c1-field-write-ok', 'c2-field-write-immfield', 'c3-field-write-immrecv', 'c4-field-read-demote', 'c5-local-var-reassign', 'c6-plain-var-reassign', 'd1-alias-pass', 'd2-alias-param', 'd3-param-shadow', 'd4-nonmut-to-mut-param', 'd5-造格-local', 'e1-task-body', 'e2-channel-type', 'e3-channel-send', 'e4-mutex-withlock', 'e5-flow-body', 'f1-at-annotation-var')
foreach ($f in (Get-ChildItem -Path $dir -Filter *.subl | Sort-Object Name)) {
  $args = @($f.FullName)
  if ($scripts -contains $f.BaseName) { $args += '--script' }
  $log = $f.FullName + '.log'
  & java -cp $cp sublimation.frontend.cli.MainKt check @args > $log 2>&1
  $code = $LASTEXITCODE
  $diag = (Get-Content $log | Where-Object { $_ -match '^\[错误\]' } | Select-Object -First 2) -join ' ;; '

  Write-Host ($f.BaseName.PadRight(26) + ' code=' + $code + '  ' + $diag)
}