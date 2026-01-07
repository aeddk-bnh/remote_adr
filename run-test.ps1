# ARCS Quick Test Runner
# Runs all components for end-to-end testing

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "ARCS - Complete Flow Test" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

# Add Java to PATH
$env:PATH = "D:\jdk-17.0.17.10-hotspot\bin;$env:PATH"

Write-Host "[1/3] Starting Mock Server (port 8080)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd d:\remote_adr\mock-server; npm start"
Start-Sleep -Seconds 3

Write-Host "[2/3] Starting Web Controller (port 3000)..." -ForegroundColor Green  
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd d:\remote_adr\web-controller; npm run dev"
Start-Sleep -Seconds 3

Write-Host "[3/3] Opening Web UI..." -ForegroundColor Green
Start-Process "http://localhost:3000"

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "Services Running:" -ForegroundColor Cyan
Write-Host "  - Mock Server:     ws://localhost:8080" -ForegroundColor Yellow
Write-Host "  - Web Controller:  http://localhost:3000" -ForegroundColor Yellow
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "  1. Build Android APK: cd android-client; .\gradlew.bat assembleDebug" -ForegroundColor White
Write-Host "  2. Install on device: adb install app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor White
Write-Host "  3. Open ARCS app on Android" -ForegroundColor White
Write-Host "  4. Enter server URL: ws://YOUR_PC_IP:8080" -ForegroundColor White
Write-Host "  5. Copy Session ID and paste in Web Controller" -ForegroundColor White
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press any key to stop all services..." -ForegroundColor Red
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

Get-Process | Where-Object {$_.MainWindowTitle -like "*ARCS*" -or $_.MainWindowTitle -like "*npm*"} | Stop-Process -Force
Write-Host "All services stopped." -ForegroundColor Green
