@echo off
REM Builds hq-shared-lib, packs it, and copies the .tgz into Rider_Web and Rider_AdminWeb.
echo Installing dependencies...
call npm install

echo Building hq-shared-lib...
call npx ng build

IF %ERRORLEVEL% EQU 0 (
    cd dist\hq-shared-lib\
    del hq-shared-lib*.tgz 2>nul
    echo Packing npm module...
    call npm pack
    copy /Y hq-shared-lib-*.tgz ..\..\..\Rider_Web\
    copy /Y hq-shared-lib-*.tgz ..\..\..\Rider_AdminWeb\
) ELSE (
    echo Error in compile
    exit /b 1
)
