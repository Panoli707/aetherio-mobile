# Reuse the shared QA device. This script never creates, wipes or deletes an AVD.
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [ValidateSet('Status', 'Start')]
    [string]$Action = 'Status',
    [switch]$Headless,
    [switch]$ColdBoot,
    [ValidateRange(5554, 5682)]
    [int]$Port = 5554
)
$ErrorActionPreference = 'Stop'
if ($Port % 2 -ne 0) { throw 'El puerto del emulador debe ser par.' }
$avd = 'Aetherio_QA_API34'
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User') }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
foreach ($path in @($adb, $emulator)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "No existe la herramienta: $path" }
}

$mutex = [Threading.Mutex]::new($false, 'Local\Aetherio_QA_API34_Launch')
$acquired = $false
try {
    try { $acquired = $mutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $acquired = $true }
    if (-not $acquired) { throw 'Otra tarea esta comprobando o iniciando el AVD principal.' }
    $devices = @(& $adb devices)
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron consultar los dispositivos.' }
    foreach ($line in $devices) {
        if ($line -match '^(emulator-[0-9]+)\s+device(?:\s|$)') {
            $serial = $Matches[1]
            $name = @(& $adb -s $serial emu avd name 2>$null)
            if ($LASTEXITCODE -eq 0 -and $name -contains $avd) {
                $state = if ($Action -eq 'Start') { 'Reutilizado' } else { 'En ejecucion' }
                [pscustomobject]@{ AVD=$avd; Serial=$serial; State=$state; Created=$false }
                return
            }
        }
    }
    $running = @(Get-CimInstance Win32_Process -Filter "Name='emulator.exe' OR Name='qemu-system-x86_64-headless.exe'" | Where-Object {
        $_.CommandLine -match '(?:^|\s)-avd\s+"?Aetherio_QA_API34(?:"|\s|$)'
    })
    if ($running.Count) {
        [pscustomobject]@{ AVD=$avd; State='Arrancando; no se inicia otra instancia'; ProcessId=$running[0].ProcessId; Created=$false }
        return
    }
    if ($Action -eq 'Status') {
        [pscustomobject]@{ AVD=$avd; State='Apagado'; Created=$false }
        return
    }
    $available = @(& $emulator -list-avds)
    if ($LASTEXITCODE -ne 0 -or $available -notcontains $avd) {
        throw "Falta el AVD principal $avd. No se crea uno nuevo automaticamente."
    }
    if (@($devices | Where-Object { $_ -match "^emulator-$Port\s" }).Count) {
        throw "El puerto $Port pertenece a otro dispositivo; no se detiene ni se reutiliza."
    }
    $arguments = @('-avd', $avd, '-port', "$Port", '-memory', '2048', '-cores', '2', '-gpu', 'host', '-no-audio', '-no-boot-anim')
    if ($Headless) { $arguments += '-no-window' }
    if ($ColdBoot) { $arguments += @('-no-snapshot-load', '-no-snapshot-save') }
    if ($PSCmdlet.ShouldProcess($avd, "Iniciar AVD existente en emulator-$Port, sin borrar datos")) {
        $logs = Join-Path $env:LOCALAPPDATA 'Temp\opencode\aetherio-qa-launch'
        if (-not (Test-Path -LiteralPath $logs)) { New-Item -ItemType Directory -Path $logs | Out-Null }
        $stamp = [datetime]::UtcNow.ToString('yyyyMMdd-HHmmssfff')
        $process = Start-Process -FilePath $emulator -ArgumentList $arguments -PassThru -RedirectStandardOutput (Join-Path $logs "$stamp.stdout.log") -RedirectStandardError (Join-Path $logs "$stamp.stderr.log")
        [pscustomobject]@{ AVD=$avd; Serial="emulator-$Port"; State='Inicio solicitado; esperar a que Android termine de arrancar'; ProcessId=$process.Id; Created=$false }
    }
} finally {
    if ($acquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
