$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$resources = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\src\main\res'
if (-not (Test-Path -LiteralPath $resources -PathType Container)) { throw 'No existe la carpeta de recursos.' }
$directory = Join-Path $resources 'drawable-xxhdpi'
if (-not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
$bitmap = [Drawing.Bitmap]::new(600, 180)
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$font = [Drawing.Font]::new('Segoe UI', 96, [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Pixel)
$brush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(232, 228, 217))
$format = [Drawing.StringFormat]::new()
try {
    $graphics.Clear([Drawing.Color]::Transparent)
    $graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $format.Alignment = [Drawing.StringAlignment]::Center
    $format.LineAlignment = [Drawing.StringAlignment]::Center
    $graphics.DrawString('Aetherio', $font, $brush, [Drawing.RectangleF]::new(0, 0, 600, 180), $format)
    $bitmap.Save((Join-Path $directory 'splash_wordmark.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $format.Dispose()
    $brush.Dispose()
    $font.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
}
'Marca de inicio generada.'
