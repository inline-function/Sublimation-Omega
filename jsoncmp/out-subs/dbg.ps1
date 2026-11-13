$root = "F:\Sublimation"
$javaExe = "F:\jdk-17.0.13.11-hotspot\bin\java.exe"
$cp = "$root/frontend/build/install/frontend/lib/*"
$probeDir = "$root/jsoncmp/out-subs"
$tmp = "$probeDir/dbg-good.subl"
$out = "$probeDir/dbg-good.json"
$body = "@impure fun main(): Null {`n    var a : @mut List[Nat] = mutableListOf()`n    print(a)`n}"
Set-Content -Path $tmp -Value $body -Encoding UTF8
Remove-Item $out -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $tmp, "-o", $out) -NoNewWindow -Wait -PassThru
Write-Output "EXIT: $($p.ExitCode)"
Write-Output "EXISTS: $(Test-Path $out)"
