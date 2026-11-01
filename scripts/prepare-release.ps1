# Internal maintainer utility: prepares existing local artifacts; never builds or publishes.
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('\A[A-Za-z0-9][A-Za-z0-9._-]{0,119}\z')]
    [string]$ReleaseTag,
    [switch]$AllowEmbeddedTmdbReadToken
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Path $PSScriptRoot -Parent
$outputs = Join-Path $root 'app\build\outputs'
$metadataPath = Join-Path $outputs 'apk\debug\output-metadata.json'
$sourceApk = Join-Path $outputs 'apk\debug\app-debug.apk'
foreach ($path in @($metadataPath, $sourceApk)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "No existe el artefacto local requerido: $path. Este script no compila."
    }
}

$metadataHash = (Get-FileHash -LiteralPath $metadataPath -Algorithm SHA256).Hash
$metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
$applicationId = 'com.panoli707.aetherio.nativeapp'
if ($metadata.applicationId -cne $applicationId -or $metadata.artifactType.type -cne 'APK' -or
    $metadata.variantName -cne 'debug' -or $metadata.elements -isnot [array] -or
    $metadata.elements.Count -ne 1) {
    throw 'Se requiere un unico APK debug de com.panoli707.aetherio.nativeapp.'
}
$element = $metadata.elements[0]
if ($element.type -cne 'SINGLE' -or $element.filters -isnot [array] -or
    $element.filters.Count -ne 0 -or $element.outputFile -cne 'app-debug.apk') {
    throw 'Se requiere un unico APK sin filtros, llamado app-debug.apk.'
}
if (($element.versionCode -isnot [int] -and $element.versionCode -isnot [long]) -or
    $element.versionCode -lt 1 -or $element.versionCode -gt [int]::MaxValue) {
    throw 'versionCode debe ser un entero positivo compatible con la aplicacion.'
}
if ($element.versionName -isnot [string] -or
    $element.versionName -cnotmatch '\A[\x20-\x7E]{1,80}\z' -or
    $element.versionName -cne $element.versionName.Trim()) {
    throw 'versionName debe contener de 1 a 80 caracteres ASCII imprimibles, sin espacios en los extremos.'
}
$versionName = $element.versionName
$versionCode = [int]$element.versionCode
# The tag only confirms the build version; it never supplies the versionCode.
$tagPattern = '\A(?:native-)?v' + [regex]::Escape($versionName) + '(?:-[A-Za-z0-9][A-Za-z0-9._-]*)?\z'
if ($ReleaseTag -cnotmatch $tagPattern) {
    throw "La etiqueta debe corresponder a la version $versionName del APK (v$versionName o native-v$versionName, con sufijo opcional)."
}
if ((Get-Item -LiteralPath $sourceApk).Length -le 0) { throw 'El APK esta vacio.' }
$sourceHash = (Get-FileHash -LiteralPath $sourceApk -Algorithm SHA256).Hash

# Inspect the actual APK, not generated source which may belong to another build.
$aaptCommand = Get-Command -Name aapt.exe, aapt -CommandType Application -ErrorAction SilentlyContinue |
    Select-Object -First 1
$aapt = if ($aaptCommand) { $aaptCommand.Source } else { $null }
$sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)
if ($env:LOCALAPPDATA) { $sdkRoots += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$localProperties = Join-Path $root 'local.properties'
if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
    foreach ($line in Get-Content -LiteralPath $localProperties) {
        if ($line -match '^\s*sdk\.dir\s*=\s*(.+?)\s*$') {
            $sdkRoots += $Matches[1].Replace('\:', ':').Replace('\\', '\')
        }
    }
}
foreach ($sdk in $sdkRoots) {
    if ($aapt -or [string]::IsNullOrWhiteSpace($sdk)) { continue }
    $buildTools = Join-Path $sdk 'build-tools'
    if (-not (Test-Path -LiteralPath $buildTools -PathType Container)) { continue }
    foreach ($toolVersion in (Get-ChildItem -LiteralPath $buildTools -Directory | Sort-Object Name -Descending)) {
        $candidate = Join-Path $toolVersion.FullName 'aapt.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $aapt = $candidate; break }
    }
}
if ($aapt) {
    $badging = & $aapt dump badging $sourceApk
    if ($LASTEXITCODE -ne 0) { throw 'aapt no pudo leer el manifiesto del APK.' }
    $packageLine = @($badging | Where-Object { $_ -cmatch '^package: ' })
    if ($packageLine.Count -ne 1 -or
        $packageLine[0] -cnotmatch "^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'(?: |$)") {
        throw 'No se pudo verificar el paquete y la version del manifiesto del APK.'
    }
    if ($Matches[1] -cne $applicationId -or $Matches[2] -cne [string]$versionCode -or
        $Matches[3] -cne $versionName) {
        throw 'El manifiesto del APK no coincide con output-metadata.json.'
    }
} else {
    throw 'Se necesita aapt para verificar el APK antes de preparar una publicacion.'
}

$manifestTree = & $aapt dump xmltree $sourceApk AndroidManifest.xml
if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar la politica de credenciales del APK.' }
$blocks = [regex]::Matches(($manifestTree -join "`n"), '(?m)^ *E: meta-data\b[^\n]*(?:\n(?! *E:)[^\n]*)*')
$markerName = [regex]::Escape('com.panoli707.aetherio.nativeapp.PRIVATE_TMDB_CREDENTIALS')
$markers = @($blocks | Where-Object { $_.Value -cmatch ('(?m)^\s*A: android:name[^=]*="' + $markerName + '"(?:\s|$)') })
if ($markers.Count -ne 1) { throw 'El APK no permite confirmar la ausencia de credenciales privadas.' }
$values = @($markers[0].Value -split "`n" | Where-Object { $_ -cmatch '^\s*A: android:value\(' })
if ($values.Count -ne 1) { throw 'La politica de credenciales del APK no es valida.' }
if ($values[0] -cmatch '=\(type 0x12\)0xffffffff\s*$') {
    if (-not $AllowEmbeddedTmdbReadToken) {
        throw 'El APK incluye una credencial TMDB extraible. Requiere autorizacion explicita con -AllowEmbeddedTmdbReadToken.'
    }
    Write-Warning 'Publicacion autorizada con token de lectura TMDB incorporado. El token puede extraerse del APK.'
} elseif ($values[0] -cnotmatch '=\(type 0x12\)0x0\s*$') {
    throw 'El APK no permite confirmar la ausencia de credenciales privadas.'
}

$updateMetadata = [ordered]@{
    schemaVersion = 1
    applicationId = $metadata.applicationId
    versionCode = $versionCode
    versionName = $versionName
    apkName = 'Aetherio-debug.apk'
}
$comment = '<!-- aetherio-update: ' + ($updateMetadata | ConvertTo-Json -Compress) + ' -->'
$notes = @(
    "# Aetherio $versionName"
    ''
    'Descarga Aetherio-debug.apk e instalalo manualmente sobre la aplicacion existente para conservar tus datos.'
    'La actualizacion requiere el mismo paquete y firma. No desinstales ni borres los datos; si Android indica un conflicto de firma, solicita un APK compatible.'
    'Las siguientes actualizaciones se descargan desde Aetherio. Puedes activar la descarga automatica en Ajustes; Android siempre pide confirmar la instalacion.'
    ''
    $comment
) -join "`n"

# Create only descendants of the existing, ignored build outputs directory.
$publishRoot = Join-Path $outputs 'publish'
$destination = Join-Path $publishRoot $ReleaseTag
$destinationApk = Join-Path $destination 'Aetherio-debug.apk'
$notesPath = Join-Path $destination 'release-notes.md'
foreach ($path in @($destinationApk, $notesPath)) {
    if (Test-Path -LiteralPath $path) { throw "Ya existe $path. No se sobrescriben artefactos preparados." }
}
foreach ($directory in @($publishRoot, $destination)) {
    $parent = Split-Path -Path $directory -Parent
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw "No existe el directorio padre: $parent" }
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }
    if ((Get-Item -LiteralPath $directory).Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
        throw "No se permiten enlaces en el destino: $directory"
    }
}
if (-not (Test-Path -LiteralPath $destination -PathType Container)) { throw 'No existe el directorio de destino.' }
Copy-Item -LiteralPath $sourceApk -Destination $destinationApk
if ((Get-FileHash -LiteralPath $destinationApk -Algorithm SHA256).Hash -cne $sourceHash -or
    (Get-FileHash -LiteralPath $sourceApk -Algorithm SHA256).Hash -cne $sourceHash -or
    (Get-FileHash -LiteralPath $metadataPath -Algorithm SHA256).Hash -cne $metadataHash) {
    Remove-Item -LiteralPath $destinationApk
    throw 'Los artefactos cambiaron durante la preparacion o la copia no coincide. No se generaron notas.'
}
if (-not (Test-Path -LiteralPath (Split-Path -Path $notesPath -Parent) -PathType Container)) {
    throw 'No existe el directorio padre de las notas.'
}
[System.IO.File]::WriteAllText($notesPath, $notes + "`n", [System.Text.UTF8Encoding]::new($false))
"Preparado localmente: $destinationApk"
"Notas: $notesPath"
"Version: $versionName (codigo $versionCode). SHA256: $sourceHash"
'No se ha publicado nada. La firma del APK no se verifica con este script.'
