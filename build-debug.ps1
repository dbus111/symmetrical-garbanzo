$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$drive = @('Z', 'Y', 'X', 'W', 'V', 'U', 'S', 'R', 'Q', 'P') |
    Where-Object { -not (Test-Path "${_}:\") } |
    Select-Object -First 1
if (-not $drive) { throw 'No free drive letter is available.' }

$driveName = "${drive}:"
$mapped = "${drive}:\"
$localProperties = Join-Path $project 'local.properties'
$oldProperties = if (Test-Path -LiteralPath $localProperties) {
    [System.IO.File]::ReadAllBytes($localProperties)
} else { $null }

& subst.exe $driveName $project
if ($LASTEXITCODE -ne 0) { throw 'Failed to map a temporary drive.' }

try {
    $localJdk = Join-Path $mapped '.tools\jdk\jdk-17.0.17+10'
    $localSdk = Join-Path $mapped '.android-sdk'
    if (Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME = $localJdk }
    if (Test-Path -LiteralPath $localSdk) {
        $env:ANDROID_HOME = $localSdk
        [System.IO.File]::WriteAllText($localProperties, "sdk.dir=${drive}:/.android-sdk`n")
    }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath $env:JAVA_HOME)) {
        throw 'JDK 17 is missing. Set JAVA_HOME or keep the local JDK in .tools.'
    }
    if (-not $env:ANDROID_HOME -or -not (Test-Path -LiteralPath $env:ANDROID_HOME)) {
        throw 'Android SDK 35 is missing. Set ANDROID_HOME or keep the local SDK in .android-sdk.'
    }
    $env:GRADLE_USER_HOME = Join-Path $mapped '.gradle-user'
    $env:ANDROID_USER_HOME = Join-Path $mapped '.android-user'
    $env:JAVA_TOOL_OPTIONS = (@($env:JAVA_TOOL_OPTIONS, "-Duser.home=$mapped") |
        Where-Object { $_ }) -join ' '
    Push-Location $mapped
    try {
        & '.\gradlew.bat' ':app:testDebugUnitTest' ':app:assembleDebug' '--no-daemon' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
} finally {
    if ($null -eq $oldProperties) {
        if (Test-Path -LiteralPath $localProperties) { Remove-Item -LiteralPath $localProperties }
    } else {
        [System.IO.File]::WriteAllBytes($localProperties, $oldProperties)
    }
    & subst.exe $driveName /D | Out-Null
}
