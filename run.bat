@echo off
rem Runs the multi-threaded ATM demo. Builds first if out\ is missing.
setlocal
cd /d "%~dp0"

if not exist out\atm\ATMSystem.class call build.bat

java -cp out atm.ATMSystem
endlocal
