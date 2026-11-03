param([string]$Revision = 'c6012439e4d3ac1914e5624a953d01f079841693')
$ErrorActionPreference = 'Stop'
if ($Revision -notmatch '\A[a-f0-9]{40}\z') { throw 'Se requiere un commit completo.' }
# Factual ID/season/offset crosswalk only; no upstream code, artwork or descriptions.
$url = "https://raw.githubusercontent.com/Fribb/anime-lists/$Revision/anime-list-full.json"
$rows = (Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 60).Content | ConvertFrom-Json
$output = @($rows | Where-Object {
    $_.type -in @('TV', 'TV_SHORT', 'ONA', 'OVA') -and $_.anilist_id -gt 0 -and
    $_.themoviedb_id.tv -gt 0 -and $_.season.tmdb -gt 0
} | ForEach-Object {
    $display = if ($_.season.tvdb -gt 0) { [int]$_.season.tvdb } else { [int]$_.season.tmdb }
    $offset = if ($null -ne $_.episode_offset.tmdb) { [int]$_.episode_offset.tmdb } else { 0 }
    if ($offset -ge 0) { ,@([int]$_.anilist_id, [int]$_.themoviedb_id.tv, [int]$_.season.tmdb, $offset, $display) }
})
$document = [ordered]@{ source = $url; revision = $Revision; rows = $output }
$directory = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\src\main\resources'
[void][IO.Directory]::CreateDirectory($directory)
[IO.File]::WriteAllText((Join-Path $directory 'series-mappings.json'), ($document | ConvertTo-Json -Depth 6 -Compress), [Text.UTF8Encoding]::new($false))
"Generadas $($output.Count) equivalencias factuales; la app rechaza grupos ambiguos antes de usarlas."
