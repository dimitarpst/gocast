@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\dev.ps1" -AndroidOnly %*
if errorlevel 1 (
    echo.
    echo Phone refresh failed. See the message above.
    pause
    exit /b 1
)
