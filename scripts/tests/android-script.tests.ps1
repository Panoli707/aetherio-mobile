# Isolated CLI regression checks. No JDK, SDK, emulator or Android build is started.
$ErrorActionPreference = 'Stop'
$script = Join-Path (Split-Path $PSScriptRoot -Parent) 'android.ps1'
$tempRoot = Join-Path $env:LOCALAPPDATA 'Temp\opencode'
$fixture = Join-Path $tempRoot ('aetherio-script-test-' + [guid]::NewGuid().ToString('N'))
$saved = @{}
foreach ($name in @('JAVA_HOME', 'ANDROID_HOME', 'MOCK_GRADLE_EXIT')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$locationChanged = $false
try {
    New-Item -ItemType Directory -Path $fixture | Out-Null
    [IO.File]::WriteAllText((Join-Path $fixture 'settings.gradle.kts'), '// CLI test fixture')
    [IO.File]::WriteAllText((Join-Path $fixture 'gradlew.bat'), "@echo off`r`necho CALL:%*`r`necho JAVA_HOME:%JAVA_HOME%`r`nexit /b %MOCK_GRADLE_EXIT%`r`n")
    Push-Location $fixture
    $locationChanged = $true
    $env:JAVA_HOME = $PSHOME
    $env:ANDROID_HOME = $PSHOME
    $env:MOCK_GRADLE_EXIT = '0'
    $cases = @(
        @{ Name='debug-low-memory'; Args=@('-Action','assembleDebug','-LowMemory'); Want=@('--daemon','--max-workers=1','"-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8"',':app:assembleDebug',"JAVA_HOME:$PSHOME"); Reject=@('--no-daemon') },
        @{ Name='release-full'; Args=@('-Action','verifyRelease','-LowMemory','-PublicDistribution','-Profile'); Want=@('--no-daemon','--no-configuration-cache','-PpublicDistribution=true','--profile',':app:assembleRelease',':app:testReleaseUnitTest',':app:lintRelease'); Reject=@('--daemon') },
        @{ Name='debug-isolation'; Args=@('-Action','verify','-NoDaemon'); Want=@('--no-daemon',':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug'); Reject=@('--daemon') },
        @{ Name='explicit-resources'; Args=@('-LowMemory','-MaxWorkers','2','-HeapMiB','1536'); Want=@('--max-workers=2','"-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8"'); Reject=@('--max-workers=1','-Xmx1024m') },
        @{ Name='preview'; Args=@('-Action','verifyRelease','-WhatIf'); Want=@('gradlew.bat','--no-daemon'); Reject=@('CALL:') }
    )
    foreach ($case in $cases) {
        $arguments = $case.Args
        $output = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script @arguments 2>&1)
        if ($LASTEXITCODE -ne 0) { throw "CLI test failed: $($case.Name)" }
        $text = $output -join "`n"
        foreach ($want in $case.Want) {
            if (-not $text.Contains($want)) { throw "Missing argument in $($case.Name): $want" }
        }
        foreach ($reject in $case.Reject) {
            if ($text.Contains($reject)) { throw "Unexpected argument in $($case.Name): $reject" }
        }
        "PASS: $($case.Name)"
    }
    $env:MOCK_GRADLE_EXIT = '17'
    $null = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script -Action assembleDebug
    if ($LASTEXITCODE -ne 17) { throw 'Gradle exit code was not propagated.' }
    'PASS: exit-code-propagation'
    '6 CLI checks passed; no Android build was started.'
} finally {
    if ($locationChanged) { Pop-Location }
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
    # Only delete the two fixture files created above, then the now-empty own directory.
    foreach ($name in @('gradlew.bat', 'settings.gradle.kts')) {
        $path = Join-Path $fixture $name
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path }
    }
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture }
}
