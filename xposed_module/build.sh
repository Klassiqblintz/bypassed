#!/bin/bash
# Build script for KYC Bypasser Xposed module
# Compile in Termux on Android

set -e

echo "=== Building KYC Bypasser Xposed Module ==="

# Get source file
SRC="/storage.emulated/0/kyc_bypasser/xposed_module/src/main/java/com/kycbypasser/xposed/XposedInit.java"
OUT="/storage.emulated/0/kyc_bypasser/xposed_module/"

# Download xposed-api.jar (needed for compilation)
XPOSED_JAR="/tmp/xposed-api.jar"
if [ ! -f "$XPOSED_JAR" ]; then
    echo "Downloading Xposed API..."
    curl -L -o "$XPOSED_JAR" \
        "https://repo.xposed.info/module/de.robv.android.xposed.api" 2>/dev/null || \
    curl -L -o "$XPOSED_JAR" \
        "https://github.com/roger844/xposed-api/raw/master/jar/xposed-api-82.jar" 2>/dev/null || {
        echo "Xposed API not found, using stub approach"
        XPOSED_JAR=""
    }
fi

# Compile
echo "Compiling..."
if [ -n "$XPOSED_JAR" ]; then
    javac -cp "$XPOSED_JAR" -d "$OUT" "$SRC"
else
    # Compile without Xposed API — use reflection at runtime
    javac -d "$OUT" "$SRC"
fi

# Package into JAR
echo "Packaging..."
cd "$OUT"
jar cf kyc_bypasser_xposed.jar *.class

# Clean up
rm -f *.class

echo "=== BUILD COMPLETE ==="
echo "Output: $OUT/kyc_bypasser_xposed.jar"
ls -la "$OUT/kyc_bypasser_xposed.jar"
