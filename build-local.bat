@echo off
setlocal

set "JAVA_HOME=C:\Users\Administrator\Downloads\jdk21\jdk-21.0.10"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo === Init Luminol Local Repo ===
if exist "Luminol-ver-1.21.11\.git" (
    echo Luminol repo already initialized, skipping
) else (
    cd Luminol-ver-1.21.11
    git init
    git add -A
    git commit -m "luminol"
    cd ..
)

echo === Apply Upstream ===
call gradlew.bat applyUpstream
if %ERRORLEVEL% neq 0 (
    echo FAILED: applyUpstream
    exit /b 1
)

echo === Apply Server Patches ===
call gradlew.bat applyAllServerPatches
if %ERRORLEVEL% neq 0 (
    echo FAILED: applyAllServerPatches
    exit /b 1
)

echo === Done ===
