@REM mobile/run_checks.bat: Runs Android unit tests, lint, and debug APK assembly; created for repeatable verification.
@echo off
setlocal
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
call "%~dp0gradlew.bat" testDebugUnitTest lintDebug assembleDebug --console=plain || exit /b 1
