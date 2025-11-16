<#
.SYNOPSIS
  Build and run CollaboDraw against Aiven MySQL (Windows PowerShell).

.DESCRIPTION
  - Loads .env (if present) to set DB_URL/DB_USER/DB_PASS (or builds DB_URL from AIVEN_HOST/AIVEN_PORT/AIVEN_DB)
  - Cleans and packages the project with Maven Wrapper (skips tests by default)
  - Starts Spring Boot using mvnw.cmd spring-boot:run

.EXAMPLES
  ./scripts/run-aiven.ps1                 # Clean, package (skip tests), run
  ./scripts/run-aiven.ps1 -RunTests       # Clean, package (with tests), run
  ./scripts/run-aiven.ps1 -SkipRun        # Only build (skip tests)
  ./scripts/run-aiven.ps1 -VerboseEnv     # Print resolved env and preflight warnings
#>
param(
  [switch]$RunTests,
  [switch]$SkipRun,
  [switch]$VerboseEnv,
  [int]$Port = 8080,
  [switch]$DevFallback
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Resolve-Path (Join-Path $scriptDir '..')
Push-Location $rootDir
try {
  function Import-DotEnv {
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
          [Environment]::SetEnvironmentVariable($k, $v, 'Process')
        }
      }
    }
  }

  Import-DotEnv

  # Map AIVEN_* to DB_* if DB_* not set (Option A: build URL from parts in application.properties)
  if (-not $env:DB_HOST) {
    $tmp = [Environment]::GetEnvironmentVariable('AIVEN_HOST')
    if (-not [string]::IsNullOrWhiteSpace($tmp)) { $env:DB_HOST = $tmp }
  }
  if (-not $env:DB_PORT) {
    $tmp = [Environment]::GetEnvironmentVariable('AIVEN_PORT')
    if (-not [string]::IsNullOrWhiteSpace($tmp)) { $env:DB_PORT = $tmp } else { $env:DB_PORT = '17118' }
  }
  if (-not $env:DB_NAME) {
    $tmp = [Environment]::GetEnvironmentVariable('AIVEN_DB')
    if (-not [string]::IsNullOrWhiteSpace($tmp)) { $env:DB_NAME = $tmp } else { $env:DB_NAME = 'defaultdb' }
  }

  foreach ($req in 'DB_USER','DB_PASS') {
    $val = [Environment]::GetEnvironmentVariable($req)
    if ([string]::IsNullOrWhiteSpace($val)) {
      Write-Host "Missing $req env var (set it in .env)" -ForegroundColor Red
      exit 2
    }
  }

  # Validate Google OAuth vars (fail fast if missing to avoid silent bypass or misconfig)
  foreach ($g in 'GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET') {
    $gval = [Environment]::GetEnvironmentVariable($g)
    if ([string]::IsNullOrWhiteSpace($gval)) {
      Write-Host "Missing $g env var for Google OAuth (set it in .env)" -ForegroundColor Red
      exit 3
    }
  }

  if ($VerboseEnv) {
    try {
      $dbHost = $env:DB_HOST
      $dbPort = if ($env:DB_PORT) { $env:DB_PORT } else { '17118' }
      $dbName = if ($env:DB_NAME) { $env:DB_NAME } else { 'defaultdb' }
      Write-Host "Resolved DB: host=$dbHost port=$dbPort db=$dbName" -ForegroundColor DarkGray
      Write-Host "DB_USER=$($env:DB_USER) DB_PASS=********" -ForegroundColor DarkGray
      Write-Host "GOOGLE_CLIENT_ID=$($env:GOOGLE_CLIENT_ID) GOOGLE_CLIENT_SECRET=********" -ForegroundColor DarkGray
    } catch {}
  }

  # Optional: quick DNS/port preflight (non-blocking)
  $dbReachable = $true
  try {
    $h = $env:DB_HOST
    $p = if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }
    if ($h -and $p) {
      $dns = Resolve-DnsName -Name $h -ErrorAction SilentlyContinue
      if (-not $dns) { Write-Host "Warning: DNS lookup failed for $h" -ForegroundColor Yellow }
      $tnc = Test-NetConnection -ComputerName $h -Port $p -WarningAction SilentlyContinue
  if ($VerboseEnv) { Write-Host ("Connectivity: {0} to {1}:{2}" -f $tnc.TcpTestSucceeded, $h, $p) -ForegroundColor DarkGray }
      if (-not $tnc.TcpTestSucceeded) { $dbReachable = $false }
    }
  } catch { $dbReachable = $false }

  if (-not $dbReachable -and $DevFallback) {
    Write-Host "DB host/port not reachable; enabling dev profile with H2 (OAuth still enabled via env)." -ForegroundColor Yellow
    [Environment]::SetEnvironmentVariable('SPRING_PROFILES_ACTIVE','dev','Process')
  }

  # Clean + package (skip tests by default)
  if ($RunTests) {
    Write-Host "Building (clean package WITH tests)..." -ForegroundColor Cyan
    & "$rootDir\mvnw.cmd" clean package
  } else {
    Write-Host "Building (clean package, skipping tests)..." -ForegroundColor Cyan
    & "$rootDir\mvnw.cmd" clean package -DskipTests
  }
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  if ($SkipRun) { Write-Host "Build finished. Skipping run as requested." -ForegroundColor Yellow; exit 0 }

  Write-Host "Starting Spring Boot with Aiven on port $Port..." -ForegroundColor Cyan
  # Set SERVER_PORT environment variable to avoid Maven argument parsing issues on Windows
  $env:SERVER_PORT = "$Port"
  & "$rootDir\mvnw.cmd" spring-boot:run
} finally {
  Pop-Location
}
