@echo off
setlocal enabledelayedexpansion
REM Script setup cho demo 4 máy (2 host + 2 VM)

echo =══════════════════════════════════════════════════════════════
echo 4-Machine Demo Setup
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
echo =══════════════════════════════════════════════════════════════
echo 4-Machine Demo Architecture:
echo   Machine A: Windows Host (Origin Server)
echo   Machine B: Ubuntu VM on Windows (P2P Tracker)
echo   Machine C: macOS Host (Client 1)
echo   Machine D: Ubuntu VM on macOS (Client 2)
echo =══════════════════════════════════════════════════════════════
echo.

REM Ask for role
echo What is this machine's role?
echo 1) Client (JavaFX Client)
echo 2) Origin Server (Windows Host)
echo 3) P2P Tracker (Ubuntu VM on Windows)
echo 4) Mirror Server
set /p role="Enter choice [1-4]: "

if "%role%"=="1" (
    echo.
    echo ⚠️  Enter IP addresses of OTHER machines:
    echo    This machine's IP: !LOCAL_IP!
    echo.
    echo Expected setup:
    echo   - Windows Host (Origin): Usually 192.168.1.101
    echo   - Ubuntu VM on Windows (Tracker): Usually 192.168.1.102
    echo.
    set /p TRACKER_IP="Enter Tracker IP [default: 192.168.1.102]: "
    if "!TRACKER_IP!"=="" set TRACKER_IP=192.168.1.102
    
    set /p ORIGIN_IP="Enter Origin Server IP [default: 192.168.1.101]: "
    if "!ORIGIN_IP!"=="" set ORIGIN_IP=192.168.1.101
    
    set /p CLIENT_NAME="Enter Client Name [default: Client-1]: "
    if "!CLIENT_NAME!"=="" set CLIENT_NAME=Client-1
    
    echo.
    echo Configuration:
    echo   This machine: !CLIENT_NAME! (!LOCAL_IP!)
    echo   Tracker: http://!TRACKER_IP!:8081
    echo   Origin:  https://!ORIGIN_IP!:8443
    set /p confirm="Continue? [y/N]: "
    if /i not "!confirm!"=="y" (
        echo Cancelled
        exit /b 0
    )
    
    set CONFIG_FILE=..\javafx-client\src\main\resources\config.properties
    (
        echo # Client Configuration - 4-Machine Demo
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
    echo.
    echo Next steps:
    echo   1. Start Origin Server on Windows Host (!ORIGIN_IP!)
    echo   2. Start P2P Tracker on Ubuntu VM (!TRACKER_IP!)
    echo   3. Run: cd javafx-client ^&^& mvn javafx:run
) else if "%role%"=="2" (
    set CONFIG_FILE=..\origin-server\src\main\resources\config.properties
    (
        echo # Origin Server Configuration - 4-Machine Demo
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
    echo.
    echo Next steps:
    echo   1. Run: cd origin-server ^&^& mvn spring-boot:run
    echo   2. Make sure firewall allows port 8443
) else if "%role%"=="3" (
    set CONFIG_FILE=..\p2p-tracker\src\main\resources\config.properties
    (
        echo # P2P Tracker Configuration - 4-Machine Demo
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
    echo.
    echo Next steps:
    echo   1. Run: cd p2p-tracker ^&^& mvn spring-boot:run
    echo   2. Make sure firewall allows port 8081
    echo   3. Verify VM network is in Bridged mode
) else if "%role%"=="4" (
    echo.
    echo Mirror Server setup - see mirror-server\README.md
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

