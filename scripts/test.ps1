$repoRoot = Resolve-Path "$PSScriptRoot\.."
$settingsPath = Join-Path $repoRoot ".maven-settings.xml"

mvn -gs $settingsPath -pl apps/server -am test
