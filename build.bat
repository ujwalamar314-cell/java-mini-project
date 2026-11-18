@echo off
rem Compiles every .java file under src\ into out\ using nothing but javac.
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo ERROR: javac was not found on your PATH.
    echo        Install any JDK 8 or newer and run this script again.
    exit /b 1
)

if exist out rmdir /s /q out
mkdir out

dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
del sources.txt

echo Build OK - class files are in out\
endlocal
