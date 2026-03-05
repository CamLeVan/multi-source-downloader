@echo off
echo ===================================================
echo     STARTING MULTI-SOURCE DOWNLOADER SYSTEM
echo ===================================================

echo [1/3] Launching Origin Server...
start "Origin Server (8080)" run-origin.bat
timeout /t 3 >nul

echo [2/3] Launching P2P Tracker...
start "P2P Tracker (8081)" run-tracker.bat
timeout /t 2 >nul

echo [3/3] Launching Client Application...
start "Downloader Client" run-client.bat

echo.
echo ===================================================
echo     SYSTEM STARTED SUCCESSFULLY!
echo ===================================================
echo.
echo Close individual windows to stop components.
pause
