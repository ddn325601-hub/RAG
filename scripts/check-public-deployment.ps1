param(
    [string]$BaseUrl = "http://121.40.90.107",
    [string]$Token = $env:CONTEST_API_TOKEN
)

$ErrorActionPreference = "Stop"

function Join-Url {
    param(
        [string]$Base,
        [string]$Path
    )
    return $Base.TrimEnd("/") + "/" + $Path.TrimStart("/")
}

function Write-Result {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )

    $status = if ($Passed) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1} - {2}" -f $status, $Name, $Detail)
}

function Test-Get {
    param(
        [string]$Name,
        [string]$Path
    )

    $url = Join-Url $BaseUrl $Path
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 20
        Write-Result $Name ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) ("HTTP " + $response.StatusCode)
    }
    catch {
        Write-Result $Name $false $_.Exception.Message
    }
}

function Test-PostChat {
    param(
        [string]$Name,
        [hashtable]$Headers
    )

    $url = Join-Url $BaseUrl "/chat"
    $body = @{
        question = "How should we troubleshoot a payment order timeout?"
        stream = $false
    } | ConvertTo-Json -Depth 5

    try {
        $response = Invoke-WebRequest -Uri $url -Method Post -Headers $Headers -ContentType "application/json" -Body $body -UseBasicParsing -TimeoutSec 60
        Write-Result $Name ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) ("HTTP " + $response.StatusCode)
    }
    catch {
        $statusCode = $null
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }

        if ($Name -like "*without token*" -and $statusCode -eq 401) {
            Write-Result $Name $true "HTTP 401 as expected"
        }
        else {
            $detail = if ($statusCode) { "HTTP $statusCode" } else { $_.Exception.Message }
            Write-Result $Name $false $detail
        }
    }
}

Write-Host "Checking public deployment: $BaseUrl"

Test-Get "home page" "/"
Test-Get "knowledge files API" "/api/knowledge/files"
Test-Get "RAG search API" "/api/rag/search?q=ServiceUnavailable&topK=3"
Test-PostChat "chat without token" @{}

if ([string]::IsNullOrWhiteSpace($Token)) {
    Write-Host "[SKIP] chat with token - CONTEST_API_TOKEN is not set"
}
else {
    Test-PostChat "chat with token" @{ Authorization = "Bearer $Token" }
}
