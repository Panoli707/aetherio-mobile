param([Parameter(Mandatory = $true)][string]$Archive)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$resources = Join-Path $root "app\src\main\res"
if (-not (Test-Path -LiteralPath $resources -PathType Container)) { throw "No existe el directorio de recursos Android." }
if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) { throw "No existe el ZIP original." }
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Archive).ProviderPath)
try {
    $symbol = $zip.GetEntry("aetherio-main/src/assets/aetheriologo.png")
    $desktopIcon = $zip.GetEntry("aetherio-main/src-tauri/icons/icon.png")
    if (-not $symbol -or -not $desktopIcon) { throw "El ZIP no contiene los iconos originales esperados." }
    $drawable = Join-Path $resources "drawable-nodpi"
    if (-not (Test-Path -LiteralPath $drawable)) { New-Item -ItemType Directory -Path $drawable | Out-Null }
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($symbol, (Join-Path $drawable "aetherio_logo.png"), $true)
    $stream = $desktopIcon.Open()
    try {
        $image = [System.Drawing.Image]::FromStream($stream)
        try {
            foreach ($density in @{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }.GetEnumerator()) {
                $directory = Join-Path $resources "mipmap-$($density.Key)"
                if (-not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
                $bitmap = [System.Drawing.Bitmap]::new([int]$density.Value, [int]$density.Value)
                $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
                try {
                    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                    $graphics.DrawImage($image, 0, 0, [int]$density.Value, [int]$density.Value)
                    foreach ($name in @("ic_launcher.png", "ic_launcher_round.png")) {
                        $bitmap.Save((Join-Path $directory $name), [System.Drawing.Imaging.ImageFormat]::Png)
                    }
                } finally { $graphics.Dispose(); $bitmap.Dispose() }
            }
        } finally { $image.Dispose() }
    } finally { $stream.Dispose() }
} finally { $zip.Dispose() }
"Iconos generados a partir del logo original de escritorio."
