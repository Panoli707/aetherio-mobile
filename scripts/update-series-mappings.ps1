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
$imdb = [ordered]@{}
foreach ($group in ($rows | Where-Object { $_.themoviedb_id.tv -gt 0 } | Group-Object { [string]$_.themoviedb_id.tv })) {
    $ids = @($group.Group | ForEach-Object { $_.imdb_id } | Where-Object { $_ -cmatch '^tt[0-9]{5,12}$' } | Sort-Object -Unique)
    if ($ids.Count -eq 1) { $imdb[$group.Name] = $ids[0] }
}
$corrections = Get-Content (Join-Path $PSScriptRoot 'series-mapping-corrections.json') -Raw | ConvertFrom-Json
$correctedIds = @($corrections.rows | ForEach-Object { $_[0] })
$finalRows = New-Object 'System.Collections.Generic.List[object]'
foreach ($row in $output) {
    if ($row[0] -notin $correctedIds) { $finalRows.Add([int[]]@($row)) }
}
foreach ($row in $corrections.rows) { $finalRows.Add([int[]]@($row)) }
$output = $finalRows.ToArray()
$document = [ordered]@{ source = $url; revision = $Revision; rows = $output; imdb = $imdb;
    corrections = $corrections.sources; legacyCombined = $corrections.legacyCombined }
$directory = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\src\main\resources'
[void][IO.Directory]::CreateDirectory($directory)
$serialized = $document | ConvertTo-Json -Depth 6 -Compress
$validated = $serialized | ConvertFrom-Json
foreach ($row in $validated.rows) {
    if ($row -isnot [array] -or $row.Count -ne 5) { throw 'Formato de equivalencias no valido; se conserva el recurso anterior.' }
}
[IO.File]::WriteAllText((Join-Path $directory 'series-mappings.json'), $serialized, [Text.UTF8Encoding]::new($false))
"Generadas $($output.Count) equivalencias factuales; la app rechaza grupos ambiguos antes de usarlas."
