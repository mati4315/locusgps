@echo off
setlocal EnableExtensions

cd /d "%~dp0"
title Locus GPS - Subir cambios a GitHub

echo.
echo ========================================
echo       LOCUS GPS - SINCRONIZAR GITHUB
echo ========================================
echo.

if not exist ".git" (
    echo ERROR: esta carpeta no contiene un repositorio Git.
    pause
    exit /b 1
)

set "GIT_CMD=git"
where git >nul 2>&1
if errorlevel 1 set "GIT_CMD=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd\git.exe"
if not exist "%GIT_CMD%" (
    echo ERROR: Git no esta instalado o no esta disponible en Windows.
    echo Instala Git desde: https://git-scm.com/download/win
    pause
    exit /b 1
)

"%GIT_CMD%" remote get-url origin >nul 2>&1
if errorlevel 1 (
    echo No se encontro el remoto origin. Configurando el repositorio GitHub...
    "%GIT_CMD%" remote add origin https://github.com/mati4315/locusgps.git
    if errorlevel 1 (
        echo ERROR: no se pudo configurar el remoto GitHub.
        pause
        exit /b 1
    )
)

set "MENSAJE=%~1"
if "%MENSAJE%"=="" set /p "MENSAJE=Mensaje del commit: "
if "%MENSAJE%"=="" set "MENSAJE=Actualiza Locus GPS"

echo.
echo [1/4] Preparando archivos...
"%GIT_CMD%" add -A
if errorlevel 1 (
    echo ERROR: no se pudieron preparar los archivos.
    pause
    exit /b 1
)

"%GIT_CMD%" diff --cached --quiet
if not errorlevel 1 (
    echo No hay cambios nuevos para subir.
    "%GIT_CMD%" status --short
    timeout /t 2 /nobreak >nul
    exit /b 0
)

echo [2/4] Archivos que se incluiran:
"%GIT_CMD%" diff --cached --name-status
echo.

echo [3/4] Creando commit...
"%GIT_CMD%" commit -m "%MENSAJE%"
if errorlevel 1 (
    echo ERROR: no se pudo crear el commit.
    pause
    exit /b 1
)

echo [4/4] Subiendo a GitHub...
"%GIT_CMD%" push origin main
if errorlevel 1 (
    echo ERROR: no se pudo subir a GitHub.
    pause
    exit /b 1
)

echo.
echo Cambios subidos correctamente a GitHub.
"%GIT_CMD%" log -1 --oneline
timeout /t 2 /nobreak >nul
exit /b 0
