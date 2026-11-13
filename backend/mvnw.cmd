@REM Minimal Maven Wrapper for Windows (only-script mode)
@echo off
setlocal
set "BASE_DIR=%~dp0"
for /f "usebackq tokens=1,* delims==" %%A in ("%BASE_DIR%.mvn\wrapper\maven-wrapper.properties") do if "%%A"=="distributionUrl" set "DIST_URL=%%B"
for %%F in ("%DIST_URL%") do set "DIST_NAME=%%~nF"
set "VER_DIR=%DIST_NAME:-bin=%"
set "DIST_HOME=%USERPROFILE%\.m2\wrapper\dists\%DIST_NAME%"
if not exist "%DIST_HOME%\%VER_DIR%\bin\mvn.cmd" (
  if not exist "%DIST_HOME%" mkdir "%DIST_HOME%"
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%DIST_HOME%\dist.zip'; Expand-Archive -Force '%DIST_HOME%\dist.zip' '%DIST_HOME%'; Remove-Item '%DIST_HOME%\dist.zip'"
)
set "MAVEN_PROJECTBASEDIR=%BASE_DIR%"
"%DIST_HOME%\%VER_DIR%\bin\mvn.cmd" %*
