@echo off
setlocal enabledelayedexpansion

echo ===========================================
echo   AssetFlow ERP - Java Build Script (Win)
echo ===========================================

:: Define project directory structures relative to script location
set "BACKEND_DIR=%~dp0"
set "SRC_DIR=%BACKEND_DIR%src"
set "WEB_INF=%BACKEND_DIR%WEB-INF"
set "CLASSES_DIR=%WEB_INF%\classes"
set "LIB_DIR=%WEB_INF%\lib"

:: 1. Ensure WEB-INF/classes and WEB-INF/lib directories exist
if not exist "%CLASSES_DIR%" (
    echo Creating directory: WEB-INF\classes...
    mkdir "%CLASSES_DIR%"
)
if not exist "%LIB_DIR%" (
    echo Creating directory: WEB-INF\lib...
    mkdir "%LIB_DIR%"
)

:: 2. Search and list all java files to compile dynamically
:: Use 'dir' bare format listing which is highly robust and avoids nested parenthesis bugs in CMD loops
echo Scanning for Java source files in %SRC_DIR%...
set "SOURCES_FILE=%BACKEND_DIR%sources.txt"

dir /s /b "%SRC_DIR%\*.java" > "%SOURCES_FILE%" 2>nul

if %ERRORLEVEL% neq 0 (
    echo ERROR: No Java source files found in %SRC_DIR%
    if exist "%SOURCES_FILE%" del "%SOURCES_FILE%"
    goto end
)

:: 3. Build classpath: include WEB-INF/lib jars and the servlet-api jar
:: Note: The wildcard path for jars MUST end with '*' and have NO trailing backslash.
:: Semicolon ';' is used to separate multiple paths. 
set "TOMCAT_SERVLET_API=C:\xampp loader\tomcat\lib\servlet-api.jar"
set "CLASSPATH=%TOMCAT_SERVLET_API%;%LIB_DIR%\*"

:: 4. Compile files
echo Compiling source files using javac...
javac -d "%CLASSES_DIR%" -cp "%CLASSPATH%" @"%SOURCES_FILE%"

if %ERRORLEVEL% neq 0 (
    echo ===========================================
    echo   BUILD ERROR: Compilation failed.
    echo   Verify Gson and MySQL connector jars exist in: WEB-INF\lib
    echo   Verify Tomcat path exists: "%TOMCAT_SERVLET_API%"
    echo ===========================================
    goto cleanup
)

echo ===========================================
echo   BUILD SUCCESS: Java classes compiled!
echo ===========================================

:: 5. Deployment automation to Tomcat webapps folder
echo.
echo Deploying to Tomcat...

:: Extract Tomcat home directory dynamically from the servlet-api path
for %%i in ("%TOMCAT_SERVLET_API%") do set "TOMCAT_LIB_DIR=%%~dpi"
set "TOMCAT_HOME=%TOMCAT_LIB_DIR%.."
set "DEPLOY_DIR=%TOMCAT_HOME%\webapps\AssetFlow"

echo Target Directory: "%DEPLOY_DIR%"

:: Create context folder inside Tomcat webapps if it doesn't exist
if not exist "%DEPLOY_DIR%" mkdir "%DEPLOY_DIR%"

:: Copy compiled classes, web.xml and lib jar files to Tomcat deploy folder
echo Copying WEB-INF assets to Tomcat webapps context...
xcopy "%WEB_INF%" "%DEPLOY_DIR%\WEB-INF" /E /I /Y /Q

if %ERRORLEVEL% equ 0 (
    echo ===========================================
    echo   DEPLOYMENT SUCCESSFUL!
    echo   Tomcat Context Name: AssetFlow
    echo   Servlet Endpoint: http://localhost:8080/AssetFlow/login
    echo   Please restart Apache Tomcat in XAMPP to load updates.
    echo ===========================================
) else (
    echo ===========================================
    echo   DEPLOYMENT FAILED: Check Tomcat directory permissions.
    echo ===========================================
)

:cleanup
:: Clean up temporary file
if exist "%SOURCES_FILE%" del "%SOURCES_FILE%"

:end
pause
endlocal