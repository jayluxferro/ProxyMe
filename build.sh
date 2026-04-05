#!/usr/bin/env bash
set -euo pipefail

# ProxyMe Build Script
# Builds both debug and release APKs

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/android_app"
OUTPUT_DIR="$SCRIPT_DIR/build-output"

# Use Java 17 if available (required for Gradle 7.x)
if /usr/libexec/java_home -v 17 &>/dev/null 2>&1; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
elif [[ -z "${JAVA_HOME:-}" ]]; then
    echo "Warning: JAVA_HOME not set. Gradle 7.x requires Java 11-17."
fi

cd "$PROJECT_DIR"

echo "=== ProxyMe Build ==="
echo "Project: $PROJECT_DIR"
echo "Java:    ${JAVA_HOME:-system default}"
echo ""

# Clean previous build
echo "[1/3] Cleaning..."
./gradlew clean --quiet

# Build debug and release
echo "[2/3] Building debug and release APKs..."
./gradlew assembleDebug assembleRelease --quiet

# Collect APKs
echo "[3/3] Collecting APKs..."
mkdir -p "$OUTPUT_DIR"
cp app/build/outputs/apk/debug/*.apk "$OUTPUT_DIR/" 2>/dev/null || true
cp app/build/outputs/apk/release/*.apk "$OUTPUT_DIR/" 2>/dev/null || true

echo ""
echo "=== Build Complete ==="
echo "Output:"
ls -lh "$OUTPUT_DIR"/*.apk 2>/dev/null || echo "  No APKs found"
