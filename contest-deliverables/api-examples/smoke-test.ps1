param(
  [string]$BaseUrl = "http://127.0.0.1:9900",
  [string]$Token = $env:CONTEST_API_TOKEN
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Token)) {
  Write-Warning "CONTEST_API_TOKEN is empty. /chat will return 401 unless the server uses no token."
}

Write-Host "BaseUrl: $BaseUrl"

Write-Host "`n1. Home page"
(Invoke-WebRequest "${BaseUrl}/" -UseBasicParsing).StatusCode

Write-Host "`n2. Knowledge files"
Invoke-RestMethod "${BaseUrl}/api/knowledge/files" | ConvertTo-Json -Depth 8

Write-Host "`n3. RAG search"
$q = [System.Uri]::EscapeDataString("payment order timeout")
Invoke-RestMethod "${BaseUrl}/api/rag/search?q=$q&topK=3" | ConvertTo-Json -Depth 8

Write-Host "`n4. Contest chat"
$body = @{
  question = "How should we troubleshoot a payment order timeout?"
  session_id = "smoke-test"
  stream = $false
} | ConvertTo-Json -Depth 5 -Compress

Invoke-RestMethod `
  -Method Post `
  -Uri "${BaseUrl}/chat" `
  -Headers @{ Authorization = "Bearer $Token" } `
  -ContentType "application/json; charset=utf-8" `
  -Body $body | ConvertTo-Json -Depth 8
