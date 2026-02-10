#!/bin/bash
# Wivern Android build script
# Patches Unicode regex issues, bundles SillyTavern, and builds APK
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Wivern Android Build ==="

# 1. Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "[1/5] Installing npm dependencies..."
    npm install
else
    echo "[1/5] node_modules exists, skipping npm install"
fi

# 2. Install babel build tools if needed
if [ ! -d "node_modules/@babel/plugin-transform-unicode-property-regex" ]; then
    echo "[2/5] Installing Babel unicode-property-regex plugin..."
    npm install --save-dev @babel/core @babel/plugin-transform-unicode-property-regex
else
    echo "[2/5] Babel plugin already installed"
fi

# 3. Patch Unicode property escapes for nodejs-mobile (no ICU)
echo "[3/5] Patching Unicode property escapes..."
node fix-unicode-regex.mjs

# 4. Create sillytavern.zip
echo "[4/5] Creating sillytavern.zip..."
rm -f /tmp/sillytavern.zip
zip -r -q /tmp/sillytavern.zip . \
    -x ".git/*" \
    -x "backups/*" \
    -x "docker/*" \
    -x "*.colab" \
    -x ".github/*" \
    -x "node_modules/.cache/*" \
    -x "android/*" \
    -x "test-results/*" \
    -x ".vscode/*" \
    -x ".idea/*"

mkdir -p android/app/src/main/assets
cp /tmp/sillytavern.zip android/app/src/main/assets/sillytavern.zip
SIZE=$(du -h android/app/src/main/assets/sillytavern.zip | cut -f1)
echo "   Created sillytavern.zip ($SIZE)"

# 5. Check for libnode binaries
if [ ! -d "android/app/libnode/bin" ]; then
    echo "ERROR: android/app/libnode/ not found."
    echo "Download nodejs-mobile-v18.20.4-android.zip from:"
    echo "  https://github.com/nicolo-ribaudo/ppr-prebuilt-nodejs-mobile/releases"
    echo "Extract to android/app/libnode/"
    exit 1
fi

# 6. Check for local.properties
if [ ! -f "android/local.properties" ]; then
    if [ -n "$ANDROID_SDK_ROOT" ]; then
        echo "sdk.dir=$ANDROID_SDK_ROOT" > android/local.properties
    elif [ -n "$ANDROID_HOME" ]; then
        echo "sdk.dir=$ANDROID_HOME" > android/local.properties
    else
        echo "ERROR: android/local.properties not found and ANDROID_SDK_ROOT not set"
        exit 1
    fi
fi

# 7. Build APK
echo "[5/5] Building APK..."
cd android
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}" ./gradlew assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "=== Build complete ==="
echo "APK: $(pwd)/$APK"
echo "Size: $(du -h $APK | cut -f1)"
