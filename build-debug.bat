@echo off
rem Baut die Debug-APK und schreibt das Log nach build.log (fuer Claude)
cd /d "%~dp0"
rem Gradle 8.14 laeuft nicht mit Java 25 -> immer die JDK von Android Studio nehmen
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo Building Widgetwerk... (log: build.log)
call gradlew.bat assembleDebug --console=plain > build.log 2>&1
echo EXIT=%ERRORLEVEL%>> build.log
echo Done. You can close this window.
