# Checks plaintext absence, not secrecy: reconstruction material is still included in the APK.
param([string]$ApkPath = 'app/build/outputs/apk/release/app-release.apk')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not [IO.Path]::IsPathRooted($ApkPath)) { $ApkPath = Join-Path $root $ApkPath }
$token = $env:TMDB_READ_ACCESS_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    $properties = Join-Path $root 'local.properties'
    if (Test-Path -LiteralPath $properties) {
        foreach ($line in [IO.File]::ReadAllLines($properties)) {
            if ($line -match '^\s*tmdb\.readAccessToken\s*[=:]\s*(.*?)\s*$') { $token = $Matches[1] }
        }
    }
}
if ([string]::IsNullOrWhiteSpace($token)) { throw 'No hay credencial local con la que comprobar el APK.' }
$token = $token.Trim()
if ($token.Length -gt 16384 -or $token -cnotmatch '\A[A-Za-z0-9._-]+\z') { throw 'Formato de credencial local no compatible.' }
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw 'No existe el APK que se debe comprobar.' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$ascii = [Text.Encoding]::ASCII
$patterns = @($token, $ascii.GetString([Text.Encoding]::Unicode.GetBytes($token)))
$overlap = ($patterns | ForEach-Object { $_.Length } | Measure-Object -Maximum).Maximum - 1
$zip = [IO.Compression.ZipFile]::OpenRead($ApkPath)
$entries = 0
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.Length -eq 0) { continue }
        $entries++
        $stream = $entry.Open()
        try {
            $buffer = New-Object byte[] 65536
            $tail = ''
            while (($count = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $text = $tail + $ascii.GetString($buffer, 0, $count)
                foreach ($pattern in $patterns) {
                    if ($text.IndexOf($pattern, [StringComparison]::Ordinal) -ge 0) {
                        throw 'El APK contiene la credencial TMDB en texto plano. No se muestra su valor.'
                    }
                }
                $tail = $text.Substring([Math]::Max(0, $text.Length - $overlap))
            }
        } finally { $stream.Dispose() }
    }
} finally { $zip.Dispose() }
"Comprobadas $entries entradas del APK: credencial local ausente en texto plano UTF-8/UTF-16LE."
'Esto no demuestra secreto: el token sigue siendo recuperable mediante analisis o instrumentacion.'
