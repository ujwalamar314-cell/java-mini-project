@echo off
rem Runs atm.SelfTest, the dependency-free test harness.
setlocal
cd /d "%~dp0"

call build.bat
echo.
java -ea -cp out atm.SelfTest
endlocal
