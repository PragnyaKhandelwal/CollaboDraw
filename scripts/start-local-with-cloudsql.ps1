<#!
.SYNOPSIS
    Starts the Cloud SQL Proxy and launches CollaboDraw (proxy-first mode).

.DESCRIPTION
    Ensures a reliable local TCP tunnel to the Cloud SQL instance, exports the
    required DB_* env vars, waits until the proxy is listening, then runs Maven.
    Use this when you explicitly do NOT want the direct socket-factory connection.

.PARAMETER Instance
    Cloud SQL instance in form project:region:instance. Required.

.PARAMETER SaKeyJson
    Path to service account key JSON (optional if you already ran gcloud auth
    and proxy can use Application Default Credentials). If provided and exists,
    GOOGLE_APPLICATION_CREDENTIALS will be set for this process.

.PARAMETER StartApp
    Switch: if set, the script will run 'mvn spring-boot:run'. Otherwise only
    starts proxy and sets env vars.

.PARAMETER DbName / DbUser / DbPass
    Database credentials. If DbUser or DbPass omitted, existing environment
    values (DB_USER / DB_PASS) are reused.

.EXAMPLE
        ./start-local-with-cloudsql.ps1 -Instance test-e470b:asia-south2:collabodraw -DbName collaborative_workspace_db -DbUser prag1704 -DbPass '***' -StartApp

NOTE: Prefer using run-with-proxy.ps1 for one-click (.env + proxy + run). This script remains for advanced/manual control.

!#>
param(
        [Parameter(Mandatory=$true)][string]$Instance,
        [string]$DbHost = "127.0.0.1",
        [int]$LocalPort = 3307,
        [string]$DbName = "collaborative_workspace_db",
        [string]$DbUser = $Env:DB_USER,
        [string]$DbPass = $Env:DB_PASS,
        [string]$SaKeyJson = $Env:GOOGLE_APPLICATION_CREDENTIALS,
        [switch]$StartApp
)

Write-Host "=== CollaboDraw Cloud SQL Proxy Launcher ===" -ForegroundColor Cyan

if (-not $DbUser) { Write-Host "[WARN] DB_USER not set (will rely on existing pool config later)" -ForegroundColor Yellow }
if (-not $DbPass) { Write-Host "[WARN] DB_PASS not set" -ForegroundColor Yellow }

if ($SaKeyJson -and -not (Test-Path $SaKeyJson)) {
        Write-Host "[WARN] Service account key path not found: $SaKeyJson (continuing, proxy may use ADC)" -ForegroundColor Yellow
} elseif ($SaKeyJson) {
        $Env:GOOGLE_APPLICATION_CREDENTIALS = $SaKeyJson
}

$proxyDir = Join-Path $PSScriptRoot "cloud-sql-proxy"
if (-not (Test-Path $proxyDir)) { New-Item -ItemType Directory -Path $proxyDir | Out-Null }
$proxyExe = Join-Path $proxyDir "cloud-sql-proxy.exe"

if (-not (Test-Path $proxyExe)) {
        Write-Host "Downloading Cloud SQL Proxy binary..." -ForegroundColor Cyan
        $downloadUrl = "https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.11.3/cloud-sql-proxy.x64.exe"
        Invoke-WebRequest -Uri $downloadUrl -OutFile $proxyExe
}

# Free port if occupied
$existing = Get-NetTCPConnection -LocalPort $LocalPort -State Listen -ErrorAction SilentlyContinue
if ($existing) {
        Write-Host "Port $LocalPort in use; attempting to terminate owning processes." -ForegroundColor Yellow
        $pids = $existing | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pid in $pids) { try { Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue } catch {} }
        Start-Sleep -Seconds 1
}

Write-Host "Starting proxy: $Instance -> $DbHost:$LocalPort" -ForegroundColor Cyan
Start-Process -FilePath $proxyExe -ArgumentList "--port $LocalPort $Instance" -WindowStyle Hidden

# Wait until port open (max 15s)
$maxWait = 15; $elapsed = 0
while ($elapsed -lt $maxWait) {
        $conn = Get-NetTCPConnection -LocalPort $LocalPort -State Listen -ErrorAction SilentlyContinue
        if ($conn) { break }
        Start-Sleep -Seconds 1; $elapsed++
}
if (-not $conn) { Write-Host "[ERROR] Proxy did not open port $LocalPort within $maxWait seconds" -ForegroundColor Red; exit 2 }

# Export runtime vars
$Env:DB_HOST = $DbHost
$Env:DB_PORT = "$LocalPort"
$Env:DB_NAME = $DbName
if ($DbUser) { $Env:DB_USER = $DbUser }
if ($DbPass) { $Env:DB_PASS = $DbPass }
$Env:CLOUD_SQL_INSTANCE = $Instance

Write-Host "Proxy ready. DB endpoint: $DbHost:$LocalPort  (Schema=$DbName)" -ForegroundColor Green

if ($StartApp) {
        Write-Host "Launching application via Maven (spring-boot:run)" -ForegroundColor Cyan
        mvn -q spring-boot:run
} else {
        Write-Host "Use -StartApp to auto-run the application." -ForegroundColor DarkGray
}
