#!/bin/bash

# AssetFlow ERP - Java Build Script (Mac/Linux)
# Automation for compiling and deploying Java Servlet codebase.

# Resolve the directory of this script
BACKEND_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SRC_DIR="$BACKEND_DIR/src"
WEB_INF="$BACKEND_DIR/WEB-INF"
CLASSES_DIR="$WEB_INF/classes"
LIB_DIR="$WEB_INF/lib"

# Placeholder path to Tomcat's servlet-api.jar (Adjust as needed)
TOMCAT_SERVLET_API="/usr/local/tomcat/lib/servlet-api.jar"

echo "==========================================="
echo "  AssetFlow ERP - Java Build Script (Unix) "
echo "==========================================="

# 1. Ensure WEB-INF/classes and WEB-INF/lib directories exist
if [ ! -d "$CLASSES_DIR" ]; then
    echo "Creating directory: WEB-INF/classes..."
    mkdir -p "$CLASSES_DIR"
fi

if [ ! -d "$LIB_DIR" ]; then
    echo "Creating directory: WEB-INF/lib..."
    mkdir -p "$LIB_DIR"
fi

# 2. Search and list all java files dynamically
echo "Scanning for Java source files in $SRC_DIR..."
SOURCES_FILE="$BACKEND_DIR/sources.txt"
rm -f "$SOURCES_FILE"

find "$SRC_DIR" -name "*.java" > "$SOURCES_FILE"

if [ ! -s "$SOURCES_FILE" ]; then
    echo "ERROR: No Java source files (.java) found in $SRC_DIR"
    rm -f "$SOURCES_FILE"
    exit 1
fi

# 3. Build classpath: include WEB-INF/lib jars and the servlet-api jar
CLASSPATH="$TOMCAT_SERVLET_API:$LIB_DIR/*"

# 4. Compile files
echo "Compiling source files using javac..."
javac -d "$CLASSES_DIR" -cp "$CLASSPATH" @"$SOURCES_FILE"
COMPILE_STATUS=$?

# Cleanup temp files
rm -f "$SOURCES_FILE"

if [ $COMPILE_STATUS -ne 0 ]; then
    echo "==========================================="
    echo "  BUILD ERROR: Compilation failed."
    echo "  Verify JDK is installed and servlet-api.jar path is correct."
    echo "  Current path placeholder: $TOMCAT_SERVLET_API"
    echo "==========================================="
    exit 1
fi

echo "==========================================="
echo "  BUILD SUCCESS: Java classes compiled!"
echo "==========================================="

# 5. Deployment automation to Tomcat webapps folder
echo ""
echo "Deploying to Tomcat..."

# Resolve deploy target relative to TOMCAT_SERVLET_API
TOMCAT_DIR="$(dirname "$TOMCAT_SERVLET_API")/.."
DEPLOY_DIR="$TOMCAT_DIR/webapps/AssetFlow"

echo "Target Directory: $DEPLOY_DIR"

# Copy assets
mkdir -p "$DEPLOY_DIR/WEB-INF"
cp -R "$WEB_INF/"* "$DEPLOY_DIR/WEB-INF/"

if [ $? -eq 0 ]; then
    echo "==========================================="
    echo "  DEPLOYMENT SUCCESSFUL!"
    echo "  Tomcat Context Name: AssetFlow"
    echo "  Servlet Endpoint: http://localhost:8080/AssetFlow/login"
    echo "  Please restart Apache Tomcat to apply updates."
    echo "==========================================="
    exit 0
else
    echo "==========================================="
    echo "  DEPLOYMENT FAILED: Check Tomcat permissions."
    echo "==========================================="
    exit 1
fi
