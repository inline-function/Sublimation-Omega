$lib = 'C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;' + $lib
$mods = @('f6a-fun-param', 'f6b-struct-field', 'f6d-lambda-param', 'f6f-funname-neg', 'f6a2-let-param-prop', 'prop-pos-ok', 'f6h-pattern-bind', 'f6i-when-subj-bind')
foreach ($f in (Get-ChildItem -Path 'F:/Sublimation/frontend/probe-mut/cases-t86/' -Filter *.subl | Sort-Object Name)) {
  $args = @($f.FullName)
  if ($mods -notcontains $f.BaseName) { $args += '--script' }
  $log = $f.FullName + '.log'
  & java -cp $cp sublimation.frontend.cli.MainKt check @args > $log 2>&1
  $err = (Get-Content $log | Where-Object { $_ -match '^\[错误\]' }) -join ' | '

  Write-Host ($f.BaseName.PadRight(24) + ' code=' + $LASTEXITCODE + '  ' + $err)
}