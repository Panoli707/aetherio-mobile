param(
  [string]$ArchivePath,
  [string]$DevArchivePath,
  [string]$Url,
  [string]$DevUrl
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$target = Join-Path $root "src-tauri\bin\mpv"

if ((-not $ArchivePath -and -not $Url) -or (-not $DevArchivePath -and -not $DevUrl)) {
  $release = Invoke-RestMethod `
    -Uri "https://api.github.com/repos/shinchiro/mpv-winbuild-cmake/releases/latest" `
    -Headers @{ Accept = "application/vnd.github+json"; "User-Agent" = "Aetherio-MPV-runtime" }

  if (-not $Url) {
    $runtimeAsset = $release.assets |
      Where-Object { $_.name -match '^mpv-x86_64-\d{8}-git-[a-f0-9]+\.7z$' } |
      Select-Object -First 1
    if (-not $runtimeAsset -or -not $runtimeAsset.browser_download_url) {
      throw "The latest MPV release has no x86_64 runtime archive."
    }
    $Url = $runtimeAsset.browser_download_url
  }

  if (-not $DevUrl) {
    $devAsset = $release.assets |
      Where-Object { $_.name -match '^mpv-dev-x86_64-\d{8}-git-[a-f0-9]+\.7z$' } |
      Select-Object -First 1
    if (-not $devAsset -or -not $devAsset.browser_download_url) {
      throw "The latest MPV release has no matching x86_64 dev archive."
    }
    $DevUrl = $devAsset.browser_download_url
  }
}

if ($ArchivePath) {
  $resolvedArchive = Resolve-Path -LiteralPath $ArchivePath
} else {
  $resolvedArchive = Join-Path ([System.IO.Path]::GetTempPath()) "aetherio-mpv.7z"
  Invoke-WebRequest -Uri $Url -OutFile $resolvedArchive
}
if ($DevArchivePath) {
  $resolvedDevArchive = Resolve-Path -LiteralPath $DevArchivePath
} else {
  $resolvedDevArchive = Join-Path ([System.IO.Path]::GetTempPath()) "aetherio-mpv-dev.7z"
  Invoke-WebRequest -Uri $DevUrl -OutFile $resolvedDevArchive
}
New-Item -ItemType Directory -Force -Path $target | Out-Null

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ("aetherio-mpv-" + [System.Guid]::NewGuid())
$devTemp = Join-Path ([System.IO.Path]::GetTempPath()) ("aetherio-mpv-dev-" + [System.Guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $temp | Out-Null
New-Item -ItemType Directory -Force -Path $devTemp | Out-Null

try {
  tar -xf $resolvedArchive -C $temp
  $mpv = Get-ChildItem -Path $temp -Recurse -Filter mpv.exe | Select-Object -First 1
  if (-not $mpv) {
    throw "The zip does not contain mpv.exe"
  }

  $source = $mpv.Directory.FullName
  Get-ChildItem -Path $source -File |
    Where-Object { $_.Extension -ieq ".dll" -or $_.Name -ieq "yt-dlp.exe" } |
    Copy-Item -Destination $target -Force

  if (-not (Test-Path -LiteralPath (Join-Path $target "libmpv-2.dll"))) {
    tar -xf $resolvedDevArchive -C $devTemp
    $libmpv = Get-ChildItem -Path $devTemp -Recurse -Filter libmpv-2.dll | Select-Object -First 1
    if (-not $libmpv) {
      throw "The dev archive does not contain libmpv-2.dll"
    }
    Copy-Item -LiteralPath $libmpv.FullName -Destination $target -Force
  }

  if (-not (Test-Path -LiteralPath (Join-Path $target "libmpv-2.dll"))) {
    throw "libmpv-2.dll was not installed"
  }
  foreach ($unused in @("mpv.exe", "mpv.com", "updater.bat")) {
    $unusedPath = Join-Path $target $unused
    if (Test-Path -LiteralPath $unusedPath) {
      Remove-Item -LiteralPath $unusedPath -Force
    }
  }

  Write-Host "libmpv runtime installed into $target"
} finally {
  Remove-Item -LiteralPath $temp -Recurse -Force
  Remove-Item -LiteralPath $devTemp -Recurse -Force
  if (-not $ArchivePath -and (Test-Path -LiteralPath $resolvedArchive)) {
    Remove-Item -LiteralPath $resolvedArchive -Force
  }
  if (-not $DevArchivePath -and (Test-Path -LiteralPath $resolvedDevArchive)) {
    Remove-Item -LiteralPath $resolvedDevArchive -Force
  }
}
