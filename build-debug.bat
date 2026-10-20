@echo off
rem Baut die Debug-APK und schreibt das Log nach build.log (fuer Claude)
cd /d "%~dp0"
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo Building Widgetwerk... (log: build.log)
call gradlew.bat assembleDebug --console=plain > build.log 2>&1
echo EXIT=%ERRORLEVEL%>> build.log
echo Done. You can close this window.
