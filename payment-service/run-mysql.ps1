$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $scriptDir ".env.mysql"

if (-not (Test-Path $envFile)) {
    throw ".env.mysql not found at $envFile"
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        return
    }

    $name, $value = $line -split "=", 2
    if (-not $name -or $null -eq $value) {
        throw "Invalid env line: $_"
    }

    Set-Item -Path "Env:$name" -Value $value
}

Write-Host "PG_DATASOURCE_URL=$env:PG_DATASOURCE_URL"

Push-Location $scriptDir
try {
    ./gradlew bootRun
} finally {
    Pop-Location
}
