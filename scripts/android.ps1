param(
    [ValidateSet("wrapper", "assembleDebug", "testDebugUnitTest", "lintDebug", "verify")]
    [string]$Action = "assembleDebug",
    [string]$GradleExecutable
)

$ErrorActionPreference = "Stop"
foreach ($name in @("JAVA_HOME", "ANDROID_HOME")) {
    $value = [System.Environment]::GetEnvironmentVariable($name, "User")
    if (-not $value) { $value = [System.Environment]::GetEnvironmentVariable($name, "Process") }
    if (-not $value -or -not (Test-Path -LiteralPath $value -PathType Container)) {
        throw "Falta $name en el entorno."
    }
    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
}
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
if (-not (Test-Path -LiteralPath ".\settings.gradle.kts" -PathType Leaf)) {
    throw "Ejecuta este script desde la raiz de aetherio-native."
}
if ($Action -eq "wrapper") {
    if (-not $GradleExecutable -or -not (Test-Path -LiteralPath $GradleExecutable -PathType Leaf)) {
        throw "Indica GradleExecutable para generar el wrapper inicial."
    }
    & $GradleExecutable --no-daemon wrapper --gradle-version 8.14.3 --distribution-type bin
} elseif ($Action -eq "verify") {
    & .\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
} else {
    & .\gradlew.bat --no-daemon ":app:$Action"
}
exit $LASTEXITCODE
