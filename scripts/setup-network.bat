@echo off
setlocal enabledelayedexpansion
REM Script tự động setup network config cho Windows

echo =══════════════════════════════════════════════════════════════
echo Auto Network Configuration Setup
echo =══════════════════════════════════════════════════════════════
echo.

REM Get local IP
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    set IP=%%a
    set IP=!IP:~1!
    echo !IP! | findstr /v "127.0.0.1" >nul
    if !errorlevel! equ 0 (
        set LOCAL_IP=!IP!
        goto :found
    )
)

:found
if "!LOCAL_IP!"=="" (
    echo ERROR: Could not detect IP address
    pause
    exit /b 1
)

echo Detected Local IP: !LOCAL_IP!
echo.

REM Ask for role
echo What is this machine's role?
echo 1) Client (JavaFX Client)
echo 2) Origin Server
echo 3) P2P Tracker
echo 4) Mirror Server
set /p role="Enter choice [1-4]: "

if "%role%"=="1" (
    echo.
    echo ⚠️  IMPORTANT: Enter the IP addresses of OTHER machines
    echo    This machine's IP: !LOCAL_IP!
    echo.
    set /p TRACKER_IP="Enter Tracker IP [default: !LOCAL_IP!]: "
    if "!TRACKER_IP!"=="" set TRACKER_IP=!LOCAL_IP!
    
    set /p ORIGIN_IP="Enter Origin Server IP [default: !LOCAL_IP!]: "
    if "!ORIGIN_IP!"=="" set ORIGIN_IP=!LOCAL_IP!
    
    set /p CLIENT_NAME="Enter Client Name [default: Client-1]: "
    if "!CLIENT_NAME!"=="" set CLIENT_NAME=Client-1
    
    echo.
    echo Configuration:
    echo   Tracker: http://!TRACKER_IP!:8081
    echo   Origin:  https://!ORIGIN_IP!:8443
    echo   Client:  !CLIENT_NAME! (!LOCAL_IP!)
    set /p confirm="Continue? [y/N]: "
    if /i not "!confirm!"=="y" (
        echo Cancelled
        exit /b 0
    )
    
    set CONFIG_FILE=..\javafx-client\src\main\resources\config.properties
    (
        echo # Client Configuration
        echo tracker.url=http://!TRACKER_IP!:8081
        echo origin.server.url=https://!ORIGIN_IP!:8443
        echo client.name=!CLIENT_NAME!
        echo.
        echo # Network Configuration
        echo client.bind.address=0.0.0.0
        echo peer.server.port=6881
        echo.
        echo # Logging
        echo log.level=INFO
        echo log.show.ip=true
        echo log.show.flow=true
    ) > "%CONFIG_FILE%"
    echo.
    echo ✓ Client config saved to: %CONFIG_FILE%
) else if "%role%"=="2" (
    set CONFIG_FILE=..\origin-server\src\main\resources\config.properties
    (
        echo # Origin Server Configuration
        echo server.host=!LOCAL_IP!
        echo server.port=8443
        echo server.name=Origin-Server
        echo.
        echo # File Server
        echo file.directory=server_files
        echo.
        echo # Logging
        echo log.level=INFO
        echo log.show.ip=true
        echo log.show.flow=true
    ) > "%CONFIG_FILE%"
    echo.
    echo ✓ Origin Server config saved to: %CONFIG_FILE%
    echo   Server will run on: https://!LOCAL_IP!:8443
) else if "%role%"=="3" (
    set CONFIG_FILE=..\p2p-tracker\src\main\resources\config.properties
    (
        echo # P2P Tracker Configuration
        echo server.host=!LOCAL_IP!
        echo server.port=8081
        echo tracker.name=P2P-Tracker
        echo.
        echo # Logging
        echo log.level=INFO
        echo log.show.ip=true
        echo log.show.flow=true
    ) > "%CONFIG_FILE%"
    echo.
    echo ✓ P2P Tracker config saved to: %CONFIG_FILE%
    echo   Tracker will run on: http://!LOCAL_IP!:8081
) else if "%role%"=="4" (
    echo.
    echo Mirror Server setup - see mirror-server\README.md
    echo For quick setup with Python:
    echo   cd origin-server\server_files
    echo   python -m http.server 8080
) else (
    echo Invalid choice
    pause
    exit /b 1
)

echo.
echo =══════════════════════════════════════════════════════════════
echo Configuration complete!
echo =══════════════════════════════════════════════════════════════
pause

