#!/bin/bash
#
# Script to install BEAST2 JARs to local Maven repository
# Usage: ./install-beast-jars.sh [path-to-beast2]
#

set -e

BEAST_VERSION="2.7.8"

# Determine BEAST2 location
if [ -z "$1" ]; then
    # Try to find BEAST2 in common locations
    if [ -d "/Applications/BEAST $BEAST_VERSION" ]; then
        BEAST_PATH="/Applications/BEAST $BEAST_VERSION"
    elif [ -d "$HOME/Applications/BEAST $BEAST_VERSION" ]; then
        BEAST_PATH="$HOME/Applications/BEAST $BEAST_VERSION"
    elif [ -d "/usr/local/share/beast" ]; then
        BEAST_PATH="/usr/local/share/beast"
    else
        echo "Error: BEAST2 path not provided and couldn't be found automatically."
        echo "Usage: $0 [path-to-beast2]"
        exit 1
    fi
else
    BEAST_PATH="$1"
fi

echo "Using BEAST2 installation at: $BEAST_PATH"

# Check if the path exists
if [ ! -d "$BEAST_PATH" ]; then
    echo "Error: BEAST2 directory not found at $BEAST_PATH"
    exit 1
fi

# Check for required JAR files
BEAST_BASE_JAR="$BEAST_PATH/lib/packages/BEAST.base.jar"
BEAST_APP_JAR="$BEAST_PATH/lib/packages/BEAST.app.jar"
BEAST_LAUNCHER_JAR="$BEAST_PATH/lib/launcher.jar"

if [ ! -f "$BEAST_BASE_JAR" ]; then
    echo "Error: BEAST.base.jar not found at $BEAST_BASE_JAR"
    exit 1
fi

if [ ! -f "$BEAST_APP_JAR" ]; then
    echo "Error: BEAST.app.jar not found at $BEAST_APP_JAR"
    exit 1
fi

if [ ! -f "$BEAST_LAUNCHER_JAR" ]; then
    echo "Error: launcher.jar not found at $BEAST_LAUNCHER_JAR"
    exit 1
fi

# Install BEAST.base.jar
echo "Installing BEAST.base.jar to local Maven repository..."
mvn install:install-file \
  -Dfile="$BEAST_BASE_JAR" \
  -DgroupId=beast2 \
  -DartifactId=beast-base \
  -Dversion="$BEAST_VERSION" \
  -Dpackaging=jar

# Install BEAST.app.jar
echo "Installing BEAST.app.jar to local Maven repository..."
mvn install:install-file \
  -Dfile="$BEAST_APP_JAR" \
  -DgroupId=beast2 \
  -DartifactId=beast-app \
  -Dversion="$BEAST_VERSION" \
  -Dpackaging=jar

# Install launcher.jar
echo "Installing launcher.jar to local Maven repository..."
mvn install:install-file \
  -Dfile="$BEAST_LAUNCHER_JAR" \
  -DgroupId=beast2 \
  -DartifactId=beast-launcher \
  -Dversion="$BEAST_VERSION" \
  -Dpackaging=jar

### add BEASTLabs from local lib path

BEAST2_MAC_PATH="$HOME/Library/Application Support/BEAST/2.7"

BEAST_Labs_VERSION="2.0.3"
BEAST_Labs_JAR="$BEAST2_MAC_PATH/BEASTLabs/lib/BEASTlabs.v$BEAST_Labs_VERSION.jar"
if [ ! -f "$BEAST_Labs_JAR" ]; then
    echo "Error: BEASTlabs.jar not found at $BEAST_Labs_JAR"
    exit 1
fi
# Install BEASTLabs.jar
echo "Installing BEASTLabs.jar to local Maven repository..."
mvn install:install-file \
  -Dfile="$BEAST_Labs_JAR" \
  -DgroupId=beast2 \
  -DartifactId=beast-labs \
  -Dversion="$BEAST_Labs_VERSION" \
  -Dpackaging=jar

echo "All BEAST2 dependencies installed successfully!"
