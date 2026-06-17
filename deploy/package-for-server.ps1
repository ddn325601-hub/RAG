$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$packageName = "SuperBizAgent-Java-deploy.tar.gz"
$outputPath = Join-Path $projectRoot $packageName

Push-Location $projectRoot
try {
    if (Test-Path $outputPath) {
        Remove-Item $outputPath -Force
    }

    $exclude = @(
        "--exclude=target",
        "--exclude=volumes",
        "--exclude=.idea",
        "--exclude=*.class",
        "--exclude=server-local.log",
        "--exclude=server-local.err.log",
        "--exclude=SuperBizAgent-Java-deploy.tar.gz"
    )

    tar @exclude -czf $packageName .
    Write-Host "Created package: $outputPath"
}
finally {
    Pop-Location
}
