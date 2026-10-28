param(
    [Parameter(Mandatory = $true)]
    [string]$JavaHome,
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
    [switch]$AcceptLicenses
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$toolsVersion = "19.0"
$ndkVersion = "28.2.13676358"

if (-not (Test-Path -LiteralPath $JavaHome -PathType Container)) {
    throw "JavaHome no existe. Indica la carpeta de un JDK 17."
}
if (-not (Test-Path -LiteralPath $SdkRoot -PathType Container)) {
    throw "SdkRoot no existe. Instala Android Studio y su SDK, o indica la ruta correcta."
}
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).ProviderPath
$SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).ProviderPath
$java = Join-Path $JavaHome "bin\java.exe"
$javac = Join-Path $JavaHome "bin\javac.exe"
$sdkManager = Join-Path $SdkRoot "cmdline-tools\$toolsVersion\bin\sdkmanager.bat"
if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or -not (Test-Path -LiteralPath $javac -PathType Leaf)) {
    throw "JavaHome debe contener java.exe y javac.exe. Un JRE no sustituye al JDK."
}

$javaVersion = (& $java --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?m)^(?:openjdk|java) 17\.') {
    throw "Este entorno usa JDK 17. JavaHome debe apuntar a esa version."
}
$javacVersion = (& $javac --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javacVersion -notmatch '(?m)^javac 17\.') {
    throw "El compilador de JavaHome debe ser javac 17."
}
$env:JAVA_HOME = $JavaHome

if (-not (Test-Path -LiteralPath $sdkManager -PathType Leaf)) {
    $toolsRoot = Join-Path $SdkRoot "cmdline-tools"
    $toolsDestination = Join-Path $toolsRoot $toolsVersion
    if (Test-Path -LiteralPath $toolsDestination) {
        throw "La carpeta cmdline-tools\$toolsVersion existe pero esta incompleta. Revisala antes de continuar."
    }

    "Consultando el repositorio oficial de Android..."
    $repository = Invoke-RestMethod -Uri "https://dl.google.com/android/repository/repository2-3.xml"
    $package = $repository.SelectSingleNode("//*[local-name()='remotePackage' and @path='cmdline-tools;$toolsVersion']")
    if (-not $package) { throw "No se encontro el paquete de herramientas de Android." }
    $archive = $package.SelectSingleNode("./*[local-name()='archives']/*[local-name()='archive'][*[local-name()='host-os']='windows']/*[local-name()='complete']")
    if (-not $archive) { throw "No se encontro un archivo de herramientas para Windows." }

    $archiveName = [string]$archive.url
    if ($archiveName -notmatch '^commandlinetools-win-[0-9]+_latest\.zip$') {
        throw "El repositorio devolvio un nombre de archivo inesperado."
    }
    $checksum = $archive.SelectSingleNode("./*[local-name()='checksum']")
    if (-not $checksum) { throw "El repositorio no incluye checksum." }
    $algorithm = $checksum.GetAttribute("type").ToUpperInvariant()
    if (-not $algorithm) { $algorithm = "SHA1" }
    if ($algorithm -notin @("SHA1", "SHA256")) {
        throw "Algoritmo de verificacion no soportado: $algorithm"
    }
    $expectedHash = $checksum.InnerText.Trim()
    if (-not $expectedHash) { throw "El repositorio no incluye checksum." }

    $tempRoot = [System.IO.Path]::GetTempPath()
    if (-not (Test-Path -LiteralPath $tempRoot -PathType Container)) {
        throw "No existe la carpeta temporal del usuario."
    }
    $downloadRoot = Join-Path $tempRoot ("aetherio-sdk-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $downloadRoot | Out-Null
    try {
        $zip = Join-Path $downloadRoot $archiveName
        "Descargando $archiveName desde dl.google.com..."
        Invoke-WebRequest -UseBasicParsing -Uri "https://dl.google.com/android/repository/$archiveName" -OutFile $zip
        $actualHash = (Get-FileHash -LiteralPath $zip -Algorithm $algorithm).Hash
        if ($actualHash -ne $expectedHash) { throw "El checksum de la descarga no coincide." }

        $unpacked = Join-Path $downloadRoot "unpacked"
        Expand-Archive -LiteralPath $zip -DestinationPath $unpacked
        $extractedTools = Join-Path $unpacked "cmdline-tools"
        if (-not (Test-Path -LiteralPath (Join-Path $extractedTools "bin\sdkmanager.bat") -PathType Leaf)) {
            throw "El archivo descargado no contiene sdkmanager.bat."
        }
        if (-not (Test-Path -LiteralPath $toolsRoot -PathType Container)) {
            New-Item -ItemType Directory -Path $toolsRoot | Out-Null
        }
        Move-Item -LiteralPath $extractedTools -Destination $toolsDestination
        "Command-line Tools instaladas; checksum $algorithm verificado."
    }
    finally {
        if (Test-Path -LiteralPath $downloadRoot) {
            Remove-Item -LiteralPath $downloadRoot -Recurse -Force
        }
    }
}

if ($AcceptLicenses) {
    "Aceptando las licencias del SDK por solicitud del usuario..."
    1..100 | ForEach-Object { "y" } | & $sdkManager "--sdk_root=$SdkRoot" --licenses
    if ($LASTEXITCODE -ne 0) { throw "No se pudieron aceptar las licencias del SDK." }
}

& $sdkManager "--sdk_root=$SdkRoot" "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;$ndkVersion"
if ($LASTEXITCODE -ne 0) {
    throw "No se pudieron instalar los paquetes. Revisa las licencias con sdkmanager --licenses."
}

$ndkRoot = Join-Path $SdkRoot "ndk\$ndkVersion"
foreach ($required in @("platform-tools\adb.exe", "platforms\android-35\android.jar", "build-tools\35.0.0\aapt2.exe", "ndk\$ndkVersion\toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe")) {
    if (-not (Test-Path -LiteralPath (Join-Path $SdkRoot $required) -PathType Leaf)) {
        throw "La instalacion esta incompleta: falta $required"
    }
}
foreach ($package in @{ "cmdline-tools\$toolsVersion" = $toolsVersion; "build-tools\35.0.0" = "35.0.0"; "ndk\$ndkVersion" = $ndkVersion }.GetEnumerator()) {
    $properties = Join-Path $SdkRoot "$($package.Key)\source.properties"
    if (-not (Test-Path -LiteralPath $properties -PathType Leaf)) {
        throw "Faltan los metadatos de $($package.Key). Repara ese paquete con Android Studio."
    }
    $revision = [regex]::Match([System.IO.File]::ReadAllText($properties), '(?m)^Pkg\.Revision\s*=\s*([^\r\n]+)').Groups[1].Value.Trim()
    if ($revision -ne $package.Value) {
        throw "Revision inesperada en $($package.Key): $revision"
    }
}
$platformProperties = Join-Path $SdkRoot "platforms\android-35\source.properties"
if (-not (Test-Path -LiteralPath $platformProperties -PathType Leaf) -or
    [System.IO.File]::ReadAllText($platformProperties) -notmatch '(?m)^AndroidVersion\.ApiLevel\s*=\s*35\s*$') {
    throw "No se pudieron validar los metadatos de Android API 35."
}

foreach ($entry in @{ JAVA_HOME = $JavaHome; ANDROID_HOME = $SdkRoot; NDK_HOME = $ndkRoot }.GetEnumerator()) {
    [System.Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "User")
    [System.Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
}

$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
$pathEntries = @($userPath -split ";" | Where-Object { $_ })
foreach ($directory in @((Join-Path $env:USERPROFILE ".cargo\bin"), (Join-Path $SdkRoot "platform-tools"), (Join-Path $SdkRoot "cmdline-tools\$toolsVersion\bin"))) {
    if ($pathEntries -notcontains $directory) { $pathEntries += $directory }
}
[System.Environment]::SetEnvironmentVariable("Path", ($pathEntries -join ";"), "User")

"SDK preparado: API 35, Build Tools 35.0.0, NDK $ndkVersion."
"Abre una terminal nueva para cargar JAVA_HOME, ANDROID_HOME, NDK_HOME y PATH."
"Si otro SDK aparece antes en el PATH de tu equipo, usa las rutas completas bajo ANDROID_HOME."
"Esto prepara herramientas; no genera ni compila una aplicacion Android."
