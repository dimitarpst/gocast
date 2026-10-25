@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\package-windows.ps1" %*
if errorlevel 1 (
    echo.
    echo Setup packaging failed. See the message above.
)
pause
