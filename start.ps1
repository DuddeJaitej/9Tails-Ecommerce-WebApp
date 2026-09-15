param()

$BACKEND      = "d:\Tap Projects\Ecommerce\backend"
$FRONTEND_DIR = "d:\Tap Projects\Ecommerce\Home"
$API_URL      = "http://localhost:8081/api/products?page=0&size=1"
$APP_URL      = "http://localhost:5500/Home.html"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  9Tails Ecommerce - Starting Up          " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/5] MySQL..." -ForegroundColor Yellow
$svc = Get-Service -Name "MySQL80" -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -eq "Running") {
    Write-Host "      OK - running" -ForegroundColor Green
} else {
    Start-Service "MySQL80" -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 4
    Write-Host "      OK - started" -ForegroundColor Green
}

Write-Host "[2/5] Port 8081..." -ForegroundColor Yellow
$p1 = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if ($p1) {
    Stop-Process -Id $p1.OwningProcess -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "      OK - cleared" -ForegroundColor Green
} else {
    Write-Host "      OK - free" -ForegroundColor Green
}

Write-Host "[3/5] Port 5500..." -ForegroundColor Yellow
$p2 = Get-NetTCPConnection -LocalPort 5500 -State Listen -ErrorAction SilentlyContinue
if ($p2) {
    Stop-Process -Id $p2.OwningProcess -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "      OK - cleared" -ForegroundColor Green
} else {
    Write-Host "      OK - free" -ForegroundColor Green
}

Write-Host "[4/5] Starting Spring Boot on :8081..." -ForegroundColor Yellow
Start-Process "cmd.exe" -ArgumentList "/k cd /d `"$BACKEND`" && mvn spring-boot:run" -WorkingDirectory $BACKEND
Write-Host "      Backend window opened" -ForegroundColor Green

Write-Host "[5/5] Starting Python HTTP server on :5500..." -ForegroundColor Yellow
Start-Process "cmd.exe" -ArgumentList "/k python -m http.server 5500" -WorkingDirectory $FRONTEND_DIR
Write-Host "      Frontend server window opened" -ForegroundColor Green

Write-Host ""
Write-Host "Waiting for backend to be ready..." -ForegroundColor Cyan
$waited = 0
$ready = $false
$ErrorActionPreference = "SilentlyContinue"
while ($waited -lt 90) {
    Start-Sleep -Seconds 4
    $waited = $waited + 4
    $r = $null
    $r = Invoke-WebRequest -Uri $API_URL -UseBasicParsing -TimeoutSec 3 2>$null
    if ($r -and ($r.StatusCode -eq 200)) {
        $ready = $true
        break
    }
    Write-Host "  still starting... (${waited}s)" -ForegroundColor DarkGray
}
$ErrorActionPreference = "Continue"

if ($ready -eq $true) {
    Write-Host "  Backend is UP!" -ForegroundColor Green
} else {
    Write-Host "  Backend slow - check the Spring Boot window" -ForegroundColor Yellow
}

Start-Sleep -Seconds 1
Start-Process $APP_URL 2>$null

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  All systems running!" -ForegroundColor Green
Write-Host ""
Write-Host "  Backend  : http://localhost:8081/api" -ForegroundColor White
Write-Host "  Frontend : http://localhost:5500/Home.html" -ForegroundColor White
Write-Host "  Admin    : admin@9tails.com / Admin@123" -ForegroundColor White
Write-Host ""
Write-Host "  Two CMD windows are open:" -ForegroundColor DarkGray
Write-Host "    - Spring Boot (close to stop API)" -ForegroundColor DarkGray
Write-Host "    - Python HTTP server (close to stop frontend)" -ForegroundColor DarkGray
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""