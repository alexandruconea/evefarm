@echo off
setlocal
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\jpackage.exe" set "JAVA_HOME="
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"

if not exist "target\evefarm.jar" (
  echo Jar not found, building first...
  call mvnw.cmd package -DskipTests
  if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
  )
)

java -jar target\evefarm.jar
pause
