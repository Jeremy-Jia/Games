param(
    [ValidateSet('Debug', 'Release')][string]$Configuration = 'Release',
    [switch]$Online
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$workspaceRoot = Split-Path $projectRoot -Parent
$gradleExecutable = Join-Path $workspaceRoot 'work\gradle\gradle-8.11.1\bin\gradle.bat'
$gradleCache = Join-Path $workspaceRoot 'work\gradle-user-home'
if (-not (Test-Path -LiteralPath $gradleExecutable)) {
    $gradleExecutable = Join-Path $projectRoot 'gradlew.bat'
    $gradleCache = $null
}
$buildArguments = @('-p', $projectRoot, '--no-daemon', "assemble$Configuration")
if ($gradleCache) { $buildArguments += @('-g', $gradleCache) }
if (-not $Online -and $gradleCache) { $buildArguments += '--offline' }
& $gradleExecutable @buildArguments
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
$variant = $Configuration.ToLowerInvariant()
$sourceApk = Join-Path $projectRoot "app\build\outputs\apk\$variant\app-$variant.apk"
$releaseDirectory = Join-Path $projectRoot 'releases'
New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
$releaseApk = Join-Path $releaseDirectory "Games-2.0.0-$variant.apk"
Copy-Item -LiteralPath $sourceApk -Destination $releaseApk -Force
Write-Output "APK=$releaseApk"
Write-Output "SHA256=$((Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant())"
