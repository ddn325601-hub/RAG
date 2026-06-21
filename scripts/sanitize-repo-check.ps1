param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$patterns = @(
    "DASHSCOPE_API_KEY\s*=\s*sk-",
    "CONTEST_API_TOKEN\s*=\s*(?!replace|change|your|demo|<)[A-Za-z0-9_-]{16,}",
    "KAFU_API_TOKEN\s*=\s*(?!replace|change|your|demo|<)[A-Za-z0-9_-]{16,}",
    "Authorization:\s*Bearer\s+(?!<|sk-demo-\*\*\*\*\*\*)[A-Za-z0-9._-]{16,}",
    "api[-_]?key\s*[:=]\s*sk-[A-Za-z0-9._-]+",
    "password\s*[:=]\s*(?!`"`"|''|replace|your|change-me|null|password\b)[A-Za-z0-9._@#$%+-]{8,}"
)

$excludeDirs = @(
    ".git",
    "target",
    "logs",
    "uploads",
    "volumes",
    "contest-deliverables/submission-display-images"
)

$files = Get-ChildItem -LiteralPath $RepoRoot -Recurse -File -Force | Where-Object {
    $relative = Resolve-Path -LiteralPath $_.FullName -Relative
    foreach ($dir in $excludeDirs) {
        if ($relative -like ".\$dir\*" -or $relative -like "./$dir/*") {
            return $false
        }
    }
    return $true
}

$findings = New-Object System.Collections.Generic.List[string]

foreach ($file in $files) {
    $extension = [System.IO.Path]::GetExtension($file.Name).ToLowerInvariant()
    if ($extension -in @(".png", ".jpg", ".jpeg", ".gif", ".zip", ".jar", ".class", ".dll", ".pdf", ".docx", ".pptx", ".mp4")) {
        continue
    }

    try {
        $lines = Get-Content -LiteralPath $file.FullName -ErrorAction Stop
    }
    catch {
        continue
    }

    for ($i = 0; $i -lt $lines.Count; $i++) {
        foreach ($pattern in $patterns) {
            if ($lines[$i] -match $pattern) {
                $relativePath = Resolve-Path -LiteralPath $file.FullName -Relative
                $findings.Add(("{0}:{1}: {2}" -f $relativePath, ($i + 1), $pattern))
            }
        }
    }
}

if ($findings.Count -eq 0) {
    Write-Host "[PASS] No obvious hard-coded secrets were found."
    exit 0
}

Write-Host "[WARN] Potential secret-like content found. Please review before publishing:"
$findings | ForEach-Object { Write-Host $_ }
exit 1
