<#!
.SYNOPSIS
  One-click launcher: loads .env, ensures Cloud SQL proxy, then runs the app.

.DESCRIPTION
  Steps:
    1. Load .env (if present)
    2. Validate: CLOUD_SQL_INSTANCE, DB_NAME, DB_USER, DB_PASS
    3. Download proxy binary if missing
    4. Start proxy on DB_PORT (default 3307) if not already listening
    5. (Optional) Build (-Build) then launch Spring Boot

  Quick Usage:
    First time build + run:
      powershell -ExecutionPolicy Bypass -File .\scripts\run-with-proxy.ps1 -Build
    Subsequent runs:
      .\scripts\run-with-proxy.ps1

  Optional PowerShell profile alias (add to $PROFILE):
      Set-Alias cdw "C:\MyData\2nd Year\SEM_3\PBL\CollaboDraw\scripts\run-with-proxy.ps1"
      # Then just run: cdw -Build

.PARAMETER Build
  If supplied, executes a full 'mvnw clean package' before starting the app.

.PARAMETER CleanOnly
  If supplied, runs 'mvnw clean' and exits (no proxy start).

.PARAMETER SkipRun
  Start proxy & export env but do NOT run Maven.

.EXAMPLE
  ./scripts/run-with-proxy.ps1 -Build

.EXAMPLE
  ./scripts/run-with-proxy.ps1 -SkipRun  # Just ensure proxy and env
!#>
param(
  [switch]$Build,
  [switch]$CleanOnly,
  [switch]$SkipRun
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Resolve-Path (Join-Path $scriptDir '..')
Set-Location $rootDir

function Load-DotEnv {
  $envFile = Join-Path $rootDir '.env'
  if (Test-Path $envFile) {
    Write-Host "Loading .env" -ForegroundColor Cyan
    Get-Content $envFile | ForEach-Object {
      $line = $_.Trim()
      if (-not $line -or $line.StartsWith('#')) { return }
      $eq = $line.IndexOf('=')
      if ($eq -lt 1) { return }
      $k = $line.Substring(0,$eq).Trim()
      $v = $line.Substring($eq+1).Trim()
      if ($k -match '^[A-Za-z_][A-Za-z0-9_]*$') {
        Set-Item -Path Env:$k -Value $v
      }
    }
  } else {
    Write-Host ".env not found (using existing environment variables)" -ForegroundColor Yellow
  }
}

Load-DotEnv

# Defaults if not provided
if (-not $Env:DB_HOST) { $Env:DB_HOST = '127.0.0.1' }
if (-not $Env:DB_PORT) { $Env:DB_PORT = '3307' }

$missing = @()
foreach ($req in 'CLOUD_SQL_INSTANCE','DB_NAME','DB_USER','DB_PASS') {
  $item = Get-Item -Path Env:$req -ErrorAction SilentlyContinue
  if (-not $item) { $missing += $req; continue }
  if ([string]::IsNullOrWhiteSpace($item.Value)) { $missing += $req }
}
if ($missing.Count -gt 0) {
  Write-Host "Missing required variables: $($missing -join ', ')" -ForegroundColor Red
  Write-Host "Edit .env (see .env.example) or set them, then re-run." -ForegroundColor Red
  exit 2
}

if ($CleanOnly) {
  Write-Host "Running clean only..." -ForegroundColor Cyan
  ./mvnw clean
  exit $LASTEXITCODE
}

# Proxy management
$proxyBase = Join-Path $scriptDir 'cloud-sql-proxy'
if (-not (Test-Path $proxyBase)) { New-Item -ItemType Directory -Path $proxyBase | Out-Null }
$proxyExe = Join-Path $proxyBase 'cloud-sql-proxy.exe'
if (-not (Test-Path $proxyExe)) {
  Write-Host 'Downloading Cloud SQL Proxy binary...' -ForegroundColor Cyan
  $downloadUrl = 'https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.11.3/cloud-sql-proxy.x64.exe'
  Invoke-WebRequest -Uri $downloadUrl -OutFile $proxyExe
}

$port = [int]$Env:DB_PORT
$already = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($already) {
  Write-Host "Proxy (or another process) already listening on port $port. Skipping start." -ForegroundColor Yellow
} else {
  Write-Host "Starting Cloud SQL Proxy ($($Env:CLOUD_SQL_INSTANCE)) on port $port" -ForegroundColor Cyan
  Start-Process -FilePath $proxyExe -ArgumentList "--port $port $($Env:CLOUD_SQL_INSTANCE)" -WindowStyle Hidden
  $maxWait = 15; $elapsed = 0
  while ($elapsed -lt $maxWait) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conn) { break }
    Start-Sleep -Seconds 1; $elapsed++
  }
  if (-not $conn) { Write-Host "Proxy failed to open port $port within $maxWait seconds" -ForegroundColor Red; exit 3 }
}

Write-Host "Environment ready:" -ForegroundColor Green
Write-Host "  CLOUD_SQL_INSTANCE=$($Env:CLOUD_SQL_INSTANCE)" -ForegroundColor DarkGray
Write-Host "  DB_HOST=$($Env:DB_HOST)  DB_PORT=$($Env:DB_PORT)  DB_NAME=$($Env:DB_NAME)" -ForegroundColor DarkGray

if ($SkipRun) { Write-Host "Skipping application run per -SkipRun." -ForegroundColor Cyan; exit 0 }

if ($Build) {
  Write-Host "Building project (clean package)..." -ForegroundColor Cyan
  ./mvnw clean package
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "Starting Spring Boot (spring-boot:run)..." -ForegroundColor Cyan
./mvnw spring-boot:run
