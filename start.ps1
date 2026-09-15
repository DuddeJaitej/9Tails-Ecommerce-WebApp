param()

$ROOT     = "d:\Tap Projects\Ecommerce"
$BACKEND  = "$ROOT\backend"
$API_URL  = "http://localhost:8081/api/products?page=0&size=1"
$APP_URL  = "http://127.0.0.1:5500/Home/Home.html"
$FRONTEND = "$ROOT\Home\Home.html"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   9Tails Ecommerce - Starting Up         " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Check MySQL
$mysql = Get-Service -Name "MySQL80" -ErrorAction SilentlyContinue
if ($mysql -and $mysql.Status -eq "Running") {
    Write-Host "[OK] MySQL is running" -ForegroundColor Green
} else {
    Write-Host "[STARTING] MySQL80..." -ForegroundColor Yellow
    Start-Service "MySQL80" -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 4
    Write-Host "[OK] MySQL started" -ForegroundColor Green
}

# 2. Kill anything on port 8081
$existing = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    $pid8081 = $existing.OwningProcess
    Write-Host "[INFO] Stopping process on port 8081 (PID $pid8081)..." -ForegroundColor Yellow
    Stop-Process -Id $pid8081 -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "[OK] Port 8081 cleared" -ForegroundColor Green
} else {
    Write-Host "[OK] Port 8081 is free" -ForegroundColor Green
}

# 3. Launch Spring Boot in a new terminal window
Write-Host ""
Write-Host "[STARTING] Launching Spring Boot backend..." -ForegroundColor Cyan

$mvnCmd = "cd /d `"$BACKEND`" && mvn spring-boot:run"
$proc = Start-Process "cmd.exe" -ArgumentList "/k $mvnCmd" -WorkingDirectory $BACKEND -PassThru

Write-Host "[OK] Spring Boot window opened (PID $($proc.Id))" -ForegroundColor Green

# 4. Poll until backend responds
Write-Host ""
Write-Host "[WAITING] Waiting for backend on port 8081 (max 90s)..." -ForegroundColor Yellow

$waited = 0
$ready  = $false

while ($waited -lt 90) {
    Start-Sleep -Seconds 4
    $waited += 4
    $resp = $null
    try {
        $resp = Invoke-WebRequest -Uri $API_URL -UseBasicParsing -TimeoutSec 4
    } catch {
        $resp = $null
    }
    if ($resp -and $resp.StatusCode -eq 200) {
        $ready = $true
        break
    }
    Write-Host "  ...waiting ($waited`s)" -ForegroundColor DarkGray
}

if ($ready) {
    Write-Host "[OK] Backend is UP - API responding with data!" -ForegroundColor Green
} else {
    Write-Host "[WARN] Backend not responding yet - check the Spring Boot window for errors" -ForegroundColor Yellow
}

# 5. Open frontend
Write-Host ""
Write-Host "[OPENING] Frontend in browser..." -ForegroundColor Cyan

# Try Live Server URL first, then raw file
try {
    Start-Process $APP_URL
} catch {
    Start-Process $FRONTEND
}

# 6. Summary
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " 9Tails is running!" -ForegroundColor Green
Write-Host ""
Write-Host " Backend API  : http://localhost:8081/api" -ForegroundColor White
Write-Host " Frontend     : $APP_URL" -ForegroundColor White
Write-Host " Admin login  : admin@9tails.com / Admin@123" -ForegroundColor White
Write-Host ""
Write-Host " To stop backend: close the Spring Boot window or Ctrl+C in it" -ForegroundColor DarkGray
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
