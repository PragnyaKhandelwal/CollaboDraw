<#
DEPRECATED SCRIPT
This script has been superseded by scripts\run-with-proxy.ps1

Please use:
  powershell -ExecutionPolicy Bypass -File .\scripts\run-with-proxy.ps1

Reason: single entrypoint keeps the scripts folder simple and avoids duplication.
#>

Write-Host "[DEPRECATED] Use .\\scripts\\run-with-proxy.ps1 instead." -ForegroundColor Yellow
Write-Host "Example: powershell -ExecutionPolicy Bypass -File .\\scripts\\run-with-proxy.ps1" -ForegroundColor Yellow
exit 1
