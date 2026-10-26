param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) {
    $localJdk = 'C:\Program Files\Java\jdk-17'
    if (Test-Path -LiteralPath (Join-Path $localJdk 'bin\javac.exe')) { $JavaHome = $localJdk }
}
if ($JavaHome) {
    $compiler = Join-Path $JavaHome 'bin\javac.exe'
    $runtime = Join-Path $JavaHome 'bin\java.exe'
} else {
    $compiler = (Get-Command javac -ErrorAction Stop).Source
    $runtime = (Get-Command java -ErrorAction Stop).Source
}
if (-not (Test-Path -LiteralPath $compiler) -or -not (Test-Path -LiteralPath $runtime)) {
    throw 'A JDK 17+ installation is required. Pass -JavaHome with its directory.'
}
$testDirectory = Join-Path $PSScriptRoot 'build\engine-tests'
New-Item -ItemType Directory -Force -Path $testDirectory | Out-Null
$sources = @(
    'app\src\main\java\dev\brickzen\app\GameEngine.java',
    'app\src\main\java\io\github\jeremyjia\stardrift\GameEngine.java',
    'tests\BrickEngineTest.java',
    'tests\GameEngineTest.java'
) | ForEach-Object { Join-Path $PSScriptRoot $_ }
& $compiler -encoding UTF-8 -d $testDirectory @sources
if ($LASTEXITCODE -ne 0) { throw "Engine test compilation failed ($LASTEXITCODE)." }
foreach ($testClass in @('dev.brickzen.app.BrickEngineTest', 'GameEngineTest')) {
    & $runtime -ea -cp $testDirectory $testClass
    if ($LASTEXITCODE -ne 0) { throw "$testClass failed ($LASTEXITCODE)." }
}
Write-Output 'PASS: both engine regression suites completed.'
