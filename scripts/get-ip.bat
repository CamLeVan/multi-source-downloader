@echo off
REM Script để lấy IP address trên Windows

echo =══════════════════════════════════════════════════════════════
echo Network Configuration Helper
echo =══════════════════════════════════════════════════════════════
echo.

echo Operating System: Windows
echo.

echo Available Network Interfaces:
echo ──────────────────────────────────────────────────────────────
echo.

for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    set IP=%%a
    set IP=!IP:~1!
    echo   IP Address: !IP!
)

echo.
echo =══════════════════════════════════════════════════════════════
echo Recommended IP (first non-127.0.0.1):
echo ──────────────────────────────────────────────────────────────

for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    set IP=%%a
    set IP=!IP:~1!
    echo !IP! | findstr /v "127.0.0.1" >nul
    if !errorlevel! equ 0 (
        echo IP Address: !IP!
        echo.
        echo Use this IP in your config.properties files:
        echo   tracker.url=http://!IP!:8081
        echo   origin.server.url=https://!IP!:8443
        goto :done
    )
)

:done
echo =══════════════════════════════════════════════════════════════
pause

