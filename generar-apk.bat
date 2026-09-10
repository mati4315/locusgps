@echo off
setlocal EnableExtensions

rem Genera el APK debug actualizado de Locus GPS.
rem Ejecutar este archivo desde cualquier ubicaci?n: resuelve su propia carpeta.

cd /d "%~dp0"
title Locus GPS - Generar APK

echo.
echo ========================================
echo        LOCUS GPS - GENERAR APK
echo ========================================
echo.

if not exist "gradlew.bat" (
    echo ERROR: no se encontro gradlew.bat en:
    echo %CD%
    pause
    exit /b 1
)

echo [1/3] Compilando APK debug...
call gradlew.bat --no-daemon :app:assembleDebug --console=plain
if errorlevel 1 (
    echo.
    echo ERROR: la compilacion fallo. Revisa el mensaje anterior.
    pause
    exit /b 1
)

set "APK_ORIGEN=%CD%\app\build\outputs\apk\debug\app-debug.apk"
set "CARPETA_SALIDA=%CD%\dist-apk"

if not exist "%APK_ORIGEN%" (
    echo ERROR: Gradle termino pero no se encontro el APK esperado:
    echo %APK_ORIGEN%
    pause
    exit /b 1
)

if not exist "%CARPETA_SALIDA%" mkdir "%CARPETA_SALIDA%"

for /f %%a in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "MARCA_TIEMPO=%%a"
set "APK_DESTINO=%CARPETA_SALIDA%\locusgps-debug-%MARCA_TIEMPO%.apk"

echo [2/3] Copiando APK generado...
copy /y "%APK_ORIGEN%" "%APK_DESTINO%" >nul
if errorlevel 1 (
    echo ERROR: no se pudo copiar el APK a dist-apk.
    pause
    exit /b 1
)

echo [3/3] APK listo.
echo.
echo Archivo principal:
echo %APK_ORIGEN%
echo.
echo Copia con fecha:
echo %APK_DESTINO%
echo.
echo Se abrira la carpeta de copias.
explorer "%CARPETA_SALIDA%"
pause
exit /b 0
