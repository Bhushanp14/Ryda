@echo off
echo ========================================================
echo RYDA: Setting up ADB Reverse Port Forwarding (Port 8000)
echo ========================================================
adb reverse tcp:8000 tcp:8000
if errorlevel 1 (
    echo [ERROR] adb reverse failed. Please ensure a device/emulator is connected and USB debugging is enabled.
    exit /b 1
)
echo [SUCCESS] adb reverse tcp:8000 tcp:8000 configured successfully.

echo.
echo ========================================================
echo Verifying Device-to-PC HTTP Reachability via adb shell curl...
echo ========================================================
adb shell "curl -s -o /dev/null -w '%%{http_code}' http://localhost:8000/ || python3 -c 'import urllib.request; print(urllib.request.urlopen(\"http://localhost:8000/\").getcode())'"
if errorlevel 1 (
    echo [WARNING] Device-to-PC HTTP reachability check returned non-zero status. Make sure your Django backend server is running on PC at port 8000.
) else (
    echo [SUCCESS] Device-to-PC HTTP reachability test completed successfully.
)
pause
