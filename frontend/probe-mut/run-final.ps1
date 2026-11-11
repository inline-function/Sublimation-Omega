$ErrorActionPreference = 'Continue'
$lib = 'C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
$cp = 'F:/Sublimation/frontend/probe-mut/cls;' + $lib
$dir = 'F:/Sublimation/frontend/probe-mut/cases'
$mods = @('a1-param', 'a3-field-decl', 'a4-return-造格', 'a5-typearg', 'a6-fun-annotation', 'b1-param-demote', 'b3-return-reject', 'h3-funtype-mut-param', 'f2-atomic')
foreach ($f in (Get-ChildItem -Path $dir -Filter *.subl | Sort-Object Name)) {
  $args = @($f.FullName)
  if ($mods -notcontains $f.BaseName) { $args += '--script' }
  $log = $f.FullName + '.log'
  & java -cp $cp sublimation.frontend.cli.MainKt check @args > $log 2>&1
  $code = $LASTEXITCODE
  $diag = (Get-Content $log | Where-Object { $_ -match '^\[错误\]' } | Select-Object -First 2) -join ' ;; '

  Write-Host ($f.BaseName.PadRight(26) + ' code=' + $code + '  ' + $diag)
}