param(
    [ValidateSet("init", "build", "dev")]
    [string]$Action = "build",
    [ValidateSet("aarch64", "x86_64")]
    [string]$Target = "aarch64"
)

$ErrorActionPreference = "Stop"
foreach ($name in @("JAVA_HOME", "ANDROID_HOME", "NDK_HOME")) {
    $value = [System.Environment]::GetEnvironmentVariable($name, "User")
    if (-not $value) { $value = [System.Environment]::GetEnvironmentVariable($name, "Process") }
    if (-not $value -or -not (Test-Path -LiteralPath $value -PathType Container)) {
        throw "Falta $name. Ejecuta primero scripts/setup-android-sdk.ps1."
    }
    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
}

$cargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
if (-not (Test-Path -LiteralPath (Join-Path $cargoBin "cargo.exe") -PathType Leaf)) {
    throw "Falta Rust. Instala rustup con el toolchain MSVC."
}
if (-not (Test-Path -LiteralPath ".\src-tauri\tauri.conf.json" -PathType Leaf)) {
    throw "Ejecuta este script desde la raiz del repositorio."
}
if ($Action -eq "init" -and (Test-Path -LiteralPath ".\src-tauri\gen\android")) {
    throw "El proyecto Android ya existe y contiene ajustes propios. No lo regeneres sobre esos cambios."
}
$env:Path = "$cargoBin;$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if (-not $env:CARGO_BUILD_JOBS) { $env:CARGO_BUILD_JOBS = "4" }

$arguments = @("run", "tauri", "--", "android", $Action)
switch ($Action) {
    "init" { $arguments += @("--ci", "--skip-targets-install") }
    "build" { $arguments += @("--debug", "--apk", "--ci", "--target", $Target) }
}
& npm.cmd @arguments
exit $LASTEXITCODE
