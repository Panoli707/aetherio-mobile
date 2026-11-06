# Compile a small Android-API-only probe; never rebuild or modify the release APK.
param([Parameter(Mandatory=$true)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial, [switch]$BuildOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$sdk = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
$java = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:JAVA_HOME = $java
$tools = (Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$avd = @(& $adb -s $Serial emu avd name)
if ($LASTEXITCODE -ne 0 -or $avd -notcontains 'Aetherio_QA_API34') { throw 'Se requiere el AVD compartido, nunca un telefono personal.' }
$out = Join-Path $root 'app/build/qa-artwork-release-0202'
foreach ($directory in @($out, "$out/classes", "$out/dex")) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
$android = Join-Path $sdk 'platforms/android-36/android.jar'
& "$java/bin/javac.exe" -source 8 -target 8 -cp $android -d "$out/classes" "$PSScriptRoot/qa/ArtworkReleaseInstrumentation.java"
if ($LASTEXITCODE -ne 0) { throw 'javac fallo.' }
$classes = @(Get-ChildItem "$out/classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
& "$tools/d8.bat" --lib $android --min-api 24 --output "$out/dex" @classes
if ($LASTEXITCODE -ne 0) { throw 'd8 fallo.' }
$manifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.panoli707.aetherio.artworkqa0202"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/><application android:label="Artwork release QA"/><instrumentation android:name="com.panoli707.aetherio.artworkqa.ArtworkReleaseInstrumentation" android:targetPackage="com.panoli707.aetherio.nativeapp"/></manifest>'
[IO.File]::WriteAllText("$out/AndroidManifest.xml", $manifest)
& "$tools/aapt.exe" package -f -M "$out/AndroidManifest.xml" -I $android -F "$out/unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'aapt fallo.' }
Push-Location "$out/dex"
try {
    & "$tools/aapt.exe" add "$out/unsigned.apk" classes.dex
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo incluir el dex.' }
} finally { Pop-Location }
& "$tools/zipalign.exe" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign fallo.' }
& "$tools/apksigner.bat" sign --ks "$env:USERPROFILE/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$out/probe.apk" "$out/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo firmar el APK de pruebas.' }
if ($BuildOnly) { "Probe compilado: $out/probe.apk"; return }
& $adb -s $Serial install -r "$out/probe.apk"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar el probe.' }
$result = & $adb -s $Serial shell am instrument -r -w com.panoli707.aetherio.artworkqa0202/com.panoli707.aetherio.artworkqa.ArtworkReleaseInstrumentation
$result | Tee-Object -FilePath "$out/result.txt"
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'INSTRUMENTATION_CODE: -1\s*$') { throw 'Fallo la verificacion del APK release.' }
