param(
    [string]$DashScopeApiKey = $env:DASHSCOPE_API_KEY,
    [switch]$IncludeAttu
)

$ErrorActionPreference = "Stop"

$dockerBin = "D:\Docker\Docker\resources\bin"
if (Test-Path $dockerBin) {
    $env:Path = "$dockerBin;$env:Path"
}

if ([string]::IsNullOrWhiteSpace($DashScopeApiKey)) {
    Write-Error "Please set DASHSCOPE_API_KEY first, for example: `$env:DASHSCOPE_API_KEY='your key'"
    exit 1
}

$env:DASHSCOPE_API_KEY = $DashScopeApiKey

if ($IncludeAttu) {
    docker compose -f vector-database.yml up -d
} else {
    docker compose -f vector-database.yml up -d etcd minio standalone
}

mvn -gs maven-settings-central.xml -s maven-settings-central.xml spring-boot:run "-Dspring-boot.run.profiles=local"
