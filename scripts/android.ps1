[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [ValidateSet("wrapper", "assembleDebug", "testDebugUnitTest", "connectedDebugAndroidTest", "lintDebug", "verify", "assembleRelease", "testReleaseUnitTest", "lintRelease", "verifyRelease")]
    [string]$Action = "assembleDebug",
    [string]$AndroidSerial = "6b591e1e",
    [string]$GradleExecutable,
    [switch]$PublicDistribution,
    [switch]$LowMemory,
    [switch]$NoDaemon,
    [switch]$Profile,
    [ValidateRange(1, 8)]
    [int]$MaxWorkers,
    [ValidateRange(512, 4096)]
    [int]$HeapMiB
)

$ErrorActionPreference = "Stop"
foreach ($name in @("JAVA_HOME", "ANDROID_HOME")) {
    $value = [System.Environment]::GetEnvironmentVariable($name, "Process")
    if (-not $value) { $value = [System.Environment]::GetEnvironmentVariable($name, "User") }
    if (-not $value -or -not (Test-Path -LiteralPath $value -PathType Container)) {
        throw "Falta $name en el entorno."
    }
    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
}
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
if (-not (Test-Path -LiteralPath ".\settings.gradle.kts" -PathType Leaf)) {
    throw "Ejecuta este script desde la raiz de aetherio-mobile."
}
if ($Action -eq "wrapper") {
    if (-not $GradleExecutable -or -not (Test-Path -LiteralPath $GradleExecutable -PathType Leaf)) {
        throw "Indica GradleExecutable para generar el wrapper inicial."
    }
    if ($PSCmdlet.ShouldProcess($GradleExecutable, 'Generar wrapper Gradle 8.14.3')) {
        & $GradleExecutable --no-daemon wrapper --gradle-version 8.14.3 --distribution-type bin
        exit $LASTEXITCODE
    }
    exit 0
}

$androidTestAction = $Action -eq 'connectedDebugAndroidTest'
$previousAndroidSerial = $env:ANDROID_SERIAL
if ($androidTestAction) {
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) { throw "No existe ADB: $adb" }
    $state = (& $adb -s $AndroidSerial get-state 2>$null) -join ''
    if ($LASTEXITCODE -ne 0 -or $state.Trim() -cne 'device') {
        throw "El Xiaomi ($AndroidSerial) no está conectado y autorizado. No se usarán otros dispositivos ni emuladores."
    }
    $model = (& $adb -s $AndroidSerial shell getprop ro.product.model) -join ''
    if ($LASTEXITCODE -ne 0 -or $model.Trim() -cne '22101320G') {
        throw "El serial $AndroidSerial no corresponde al Xiaomi 22101320G; se cancela para evitar instalar en otro dispositivo."
    }
    $env:ANDROID_SERIAL = $AndroidSerial
}

$releaseAction = $Action -in @('assembleRelease', 'testReleaseUnitTest', 'lintRelease', 'verifyRelease')
$gradleArguments = @('--no-configuration-cache')
if ($NoDaemon -or $releaseAction) { $gradleArguments += '--no-daemon' }
else { $gradleArguments += '--daemon' }
if ($PublicDistribution) { $gradleArguments += '-PpublicDistribution=true' }
if ($Profile) { $gradleArguments += '--profile' }
if ($PSBoundParameters.ContainsKey('MaxWorkers')) { $gradleArguments += "--max-workers=$MaxWorkers" }
elseif ($LowMemory) { $gradleArguments += '--max-workers=1' }
if ($PSBoundParameters.ContainsKey('HeapMiB')) {
    $gradleArguments += "-Dorg.gradle.jvmargs=-Xmx${HeapMiB}m -Dfile.encoding=UTF-8"
} elseif ($LowMemory) {
    $gradleArguments += '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8'
}
if ($Action -eq 'verify') {
    $gradleArguments += @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
} elseif ($Action -eq 'verifyRelease') {
    $gradleArguments += @(':app:assembleRelease', ':app:testReleaseUnitTest', ':app:lintRelease')
} elseif ($Action -eq 'connectedDebugAndroidTest') {
    # Personal device: UTP must not uninstall either APK, including on failed installation.
    $gradleArguments += '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true'
    $gradleArguments += ':app:connectedDebugAndroidTest'
} else {
    $gradleArguments += ":app:$Action"
}
if ($PSCmdlet.ShouldProcess((Get-Location).Path, "gradlew.bat $($gradleArguments -join ' ')")) {
    try {
        & .\gradlew.bat @gradleArguments
        $gradleExitCode = $LASTEXITCODE
    } finally {
        if ($androidTestAction) { $env:ANDROID_SERIAL = $previousAndroidSerial }
    }
    exit $gradleExitCode
}
if ($androidTestAction) { $env:ANDROID_SERIAL = $previousAndroidSerial }
exit 0
