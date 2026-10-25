@echo off
REM Explicit uninstall removes GoCast network integration only.
REM Windows Installer owns program files, shortcuts and product registration.
REM Personal settings, identity and trusted devices are preserved.
setlocal EnableExtensions
netsh advfirewall firewall delete rule name="gocast" >nul 2>&1
netsh advfirewall firewall delete rule name="gocast TCP 8765" >nul 2>&1
endlocal
exit /b 0
