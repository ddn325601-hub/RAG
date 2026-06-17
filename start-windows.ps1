param(
    [string]$DashScopeApiKey = $env:DASHSCOPE_API_KEY
)

if ([string]::IsNullOrWhiteSpace($DashScopeApiKey)) {
    Write-Error "请先设置 DashScope API Key，例如：`$env:DASHSCOPE_API_KEY='你的 key'"
    exit 1
}

$env:DASHSCOPE_API_KEY = $DashScopeApiKey

Write-Host "启动 Milvus..."
docker compose -f vector-database.yml up -d

Write-Host "启动 Spring Boot..."
mvn -gs maven-settings-central.xml -s maven-settings-central.xml spring-boot:run
