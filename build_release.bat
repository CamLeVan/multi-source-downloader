@echo off
echo ===================================================
echo   MULTI-SOURCE DOWNLOADER - BUILD SCRIPT
echo ===================================================
echo.
echo [1/4] Dang don dep va build toan bo du an...
call mvn clean package -DskipTests

if %errorlevel% neq 0 (
    echo [ERROR] Build that bai! Vui long kiem tra lai code.
    pause
    exit /b %errorlevel%
)

echo.
echo [2/4] Dang tao thu muc Release...
if exist release rmdir /s /q release
mkdir release
mkdir release\server_files

echo.
echo [3/4] Dang copy file ve thu muc Release...

copy origin-server\target\origin-server*-SNAPSHOT.jar release\origin-server.jar
copy p2p-tracker\target\p2p-tracker*-SNAPSHOT.jar release\p2p-tracker.jar
copy javafx-client\target\javafx-client*-SNAPSHOT.jar release\download-client.jar

echo Copying sample files...
xcopy origin-server\server_files release\server_files /E /I /Y

echo.
echo [4/4] Tao file chay nhanh (Launcher)...

echo @echo off > release\START_SERVER.bat
echo title Origin & Tracker Server >> release\START_SERVER.bat
echo start "Tracker Server" java -jar p2p-tracker.jar >> release\START_SERVER.bat
echo timeout /t 2 >> release\START_SERVER.bat
echo start "Origin Server" java -jar origin-server.jar >> release\START_SERVER.bat
echo echo Servers running... Do not close this window. >> release\START_SERVER.bat

echo @echo off > release\START_CLIENT.bat
echo title Downloader Client >> release\START_CLIENT.bat
echo start "Client" java -jar download-client.jar >> release\START_CLIENT.bat

echo.
echo ===================================================
echo   BUILD THANH CONG!
echo   Thu muc release: %CD%\release
echo ===================================================
pause
