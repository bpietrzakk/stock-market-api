@echo off
REM Stockmarket startup script for Windows
REM Usage: start.bat [PORT]   (default: 8080)

if "%~1"=="" (
    set PORT=8080
) else (
    set PORT=%~1
)

echo Starting stockmarket on port %PORT%...
set PORT=%PORT%
docker compose up --build