$repoRoot = Resolve-Path "$PSScriptRoot\.."
$settingsPath = Join-Path $repoRoot ".maven-settings.xml"

mvn -gs $settingsPath -pl apps/server -am clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if (Test-Path (Join-Path $repoRoot "apps/web/package.json")) {
    if (-not (Test-Path (Join-Path $repoRoot "apps/web/node_modules"))) {
        Write-Host "Skip web build: apps/web/node_modules not found. Run npm install in apps/web first."
        exit 0
    }

    Push-Location (Join-Path $repoRoot "apps/web")
    try {
        npm run build
        exit $LASTEXITCODE
    } finally {
        Pop-Location
    }
}
