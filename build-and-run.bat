@echo off
REM ###############################################################################
REM ARCS Complete Flow Test Script for Windows
REM Builds and runs all components: Server, Web Controller, Android Client
REM ###############################################################################

setlocal enabledelayedexpansion

echo ======================================================================
echo ARCS - Android Remote Control System
echo Complete Flow Build ^& Run Script (Windows)
echo ======================================================================

set PROJECT_ROOT=%~dp0
set BUILD_DIR=%PROJECT_ROOT%build
set STEP=1

REM ###############################################################################
REM Helper Functions
REM ###############################################################################

:print_step
    echo.
    echo [Step %STEP%] %~1
    set /a STEP+=1
    goto :eof

:print_error
    echo [ERROR] %~1
    goto :eof

:print_success
    echo [SUCCESS] %~1
    goto :eof

REM ###############################################################################
REM Main Build Process
REM ###############################################################################

REM Create build directory
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

REM ###############################################################################
REM 1. Build Server
REM ###############################################################################
call :print_step "Building C++ Server..."

cd /d "%PROJECT_ROOT%server"
if not exist build mkdir build
cd build

cmake .. -G "Visual Studio 17 2022" -A x64 ^
    -DCMAKE_BUILD_TYPE=Release ^
    -DCMAKE_INSTALL_PREFIX="%BUILD_DIR%\server"

if errorlevel 1 (
    call :print_error "CMake configuration failed"
    exit /b 1
)

cmake --build . --config Release --target install

if errorlevel 1 (
    call :print_error "Server build failed"
    exit /b 1
) else (
    call :print_success "Server built successfully"
)

REM ###############################################################################
REM 2. Build Web Controller
REM ###############################################################################
call :print_step "Building Web Controller..."

cd /d "%PROJECT_ROOT%web-controller"

REM Install dependencies
if not exist node_modules (
    echo Installing npm dependencies...
    call npm install
    if errorlevel 1 (
        call :print_error "npm install failed"
        exit /b 1
    )
)

REM Build for production
call npm run build

if errorlevel 1 (
    call :print_error "Web controller build failed"
    exit /b 1
) else (
    call :print_success "Web controller built successfully"
    if not exist "%BUILD_DIR%\web" mkdir "%BUILD_DIR%\web"
    xcopy /E /I /Y dist "%BUILD_DIR%\web"
)

REM ###############################################################################
REM 3. Build Android Client
REM ###############################################################################
call :print_step "Building Android Client..."

cd /d "%PROJECT_ROOT%android-client"

REM Check for Android SDK
if "%ANDROID_HOME%"=="" (
    echo ANDROID_HOME not set. Checking default locations...
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
        echo Found Android SDK at: !ANDROID_HOME!
    ) else if exist "C:\Android\Sdk" (
        set ANDROID_HOME=C:\Android\Sdk
        echo Found Android SDK at: !ANDROID_HOME!
    ) else (
        call :print_error "Android SDK not found. Please set ANDROID_HOME"
        exit /b 1
    )
)

REM Build debug APK
call gradlew.bat assembleDebug

if errorlevel 1 (
    call :print_error "Android client build failed"
    exit /b 1
) else (
    call :print_success "Android client built successfully"
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "%BUILD_DIR%\arcs-client.apk"
    echo APK saved to: %BUILD_DIR%\arcs-client.apk
)

REM ###############################################################################
REM 4. Generate Configuration
REM ###############################################################################
call :print_step "Generating runtime configuration..."

cd /d "%BUILD_DIR%"

REM Generate random JWT secret (simplified for Windows)
set JWT_SECRET=%RANDOM%%RANDOM%%RANDOM%%RANDOM%

(
echo # ARCS Server Configuration
echo JWT_SECRET=%JWT_SECRET%
echo LOG_LEVEL=info
echo MAX_SESSIONS=100
echo DB_PATH=./arcs.db
echo TLS_ENABLED=false
echo # For TLS: set TLS_ENABLED=true and provide cert/key paths
) > server-config.env

call :print_success "Configuration generated: server-config.env"

REM ###############################################################################
REM 5. Build Summary
REM ###############################################################################
call :print_step "Build Summary"

echo.
echo ======================================================================
echo Build Complete!
echo ======================================================================
echo.
echo Build Artifacts:
echo   Server:      %BUILD_DIR%\server\bin\arcs_server.exe
echo   Web:         %BUILD_DIR%\web\ (static files)
echo   Android APK: %BUILD_DIR%\arcs-client.apk
echo.
echo Configuration:
echo   Server config: %BUILD_DIR%\server-config.env
echo.
echo ======================================================================
echo Next Steps:
echo ======================================================================
echo.
echo 1. Start the server:
echo    cd %BUILD_DIR%
echo    server\bin\arcs_server.exe --host 0.0.0.0 --port 8080
echo.
echo 2. Serve the web controller:
echo    cd %BUILD_DIR%\web
echo    python -m http.server 3000
echo    # Or use any web server
echo.
echo 3. Install Android app:
echo    adb install %BUILD_DIR%\arcs-client.apk
echo    # Then open the app and configure server URL
echo.
echo 4. Connect to server:
echo    - Android: Enter server URL (ws://YOUR_IP:8080)
echo    - Web: Open http://localhost:3000 and enter session ID
echo.
echo ======================================================================
echo.
echo [WARNING] This is a development build. For production:
echo   - Use Docker Compose (see DEPLOYMENT.md)
echo   - Configure proper SSL/TLS certificates
echo   - Set up nginx reverse proxy
echo   - Enable monitoring and logging
echo.
echo Done!

endlocal
