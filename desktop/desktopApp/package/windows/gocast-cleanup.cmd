@echo off
REM Full gocast wipe — firewall rules + per-user data.
REM Designed for MSI uninstall (often runs as SYSTEM) and manual runs.

setlocal EnableExtensions

netsh advfirewall firewall delete rule name="gocast" >nul 2>&1
netsh advfirewall firewall delete rule name="gocast TCP 8765" >nul 2>&1

REM Current context (works when run as the interactive user).
if exist "%LOCALAPPDATA%\gocast" rd /s /q "%LOCALAPPDATA%\gocast" >nul 2>&1
if exist "%USERPROFILE%\.gocast" rd /s /q "%USERPROFILE%\.gocast" >nul 2>&1

REM SYSTEM / elevated uninstall: wipe every Windows profile we can see.
for /d %%U in ("%SystemDrive%\Users\*") do (
  if /I not "%%~nxU"=="Public" if /I not "%%~nxU"=="Default" if /I not "%%~nxU"=="Default User" (
    if exist "%%U\AppData\Local\gocast" rd /s /q "%%U\AppData\Local\gocast" >nul 2>&1
    if exist "%%U\.gocast" rd /s /q "%%U\.gocast" >nul 2>&1
  )
)

reg delete "HKLM\SOFTWARE\gocast" /f >nul 2>&1
reg delete "HKCU\SOFTWARE\gocast" /f >nul 2>&1

endlocal
exit /b 0
