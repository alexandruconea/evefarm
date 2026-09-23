@echo off
setlocal
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\jpackage.exe" set "JAVA_HOME="
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
if not exist "%JAVA_HOME%\bin\jpackage.exe" (
  echo No JDK with jpackage found. Set JAVA_HOME to a JDK 25 install.
  pause
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "PROJECT_DIR=%~dp0.."
cd /d "%PROJECT_DIR%"
echo Project dir: %CD%
for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "([xml](Get-Content -Raw 'pom.xml')).project.version"`) do set "APP_VERSION=%%v"
if not defined APP_VERSION (
  echo Could not read the version from pom.xml.
  pause
  exit /b 1
)
echo Version: %APP_VERSION%

echo Building evefarm.jar...
call "%PROJECT_DIR%\mvnw.cmd" -B clean package -DskipTests
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)

echo Staging jar for packaging...
set "STAGE_DIR=%PROJECT_DIR%\packaging\dist\stage"
rmdir /s /q "%STAGE_DIR%" 2>nul
mkdir "%STAGE_DIR%"
copy /y "%PROJECT_DIR%\target\evefarm.jar" "%STAGE_DIR%\evefarm.jar" >nul

echo Packaging EVEFarm.exe...
rmdir /s /q packaging\dist\EVEFarm 2>nul
jpackage ^
  --type app-image ^
  --input "%STAGE_DIR%" ^
  --dest packaging\dist ^
  --name EVEFarm ^
  --main-jar evefarm.jar ^
  --main-class com.evefarm.Main ^
  --icon packaging\evefarm.ico ^
  --app-version %APP_VERSION% ^
  --vendor "Alex Conea" ^
  --description "EVE Online multi-character asset and net-worth tracker"

if errorlevel 1 (
  echo Packaging failed.
  pause
  exit /b 1
)

rmdir /s /q "%STAGE_DIR%" 2>nul

echo Zipping release...
copy /y packaging\README.txt packaging\dist\EVEFarm\README.txt >nul
set "ZIP_FILE=packaging\dist\EVEFarm-%APP_VERSION%-win64.zip"
del /q "%ZIP_FILE%" 2>nul
"%SystemRoot%\System32\tar.exe" -a -c -f "%ZIP_FILE%" -C packaging\dist EVEFarm
if errorlevel 1 (
  echo Zipping failed.
  pause
  exit /b 1
)

echo Done: packaging\dist\EVEFarm\EVEFarm.exe
echo       %ZIP_FILE%
pause
