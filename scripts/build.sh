#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$PROJECT_DIR/src"
LIBS_DIR="$PROJECT_DIR/libs"
BUILD_DIR="$PROJECT_DIR/build"
VANILLA_JAR="$LIBS_DIR/projectzomboid.jar"

# Tier selection: lite (default) or full
TIER="${1:-lite}"

VERSION=$(cat "$PROJECT_DIR/version.txt" | tr -d '[:space:]')

echo "=== OptiZomb Build - $TIER tier (v$VERSION) ==="

# Inject version into OptiZombConfig.java (src/ is gitignored, safe to modify)
CONFIG_FILE="$SRC_DIR/zombie/optizomb/OptiZombConfig.java"
if [ -f "$CONFIG_FILE" ]; then
    sed -i "s/VERSION = \"[^\"]*\"/VERSION = \"$VERSION\"/" "$CONFIG_FILE"
fi

if [ ! -f "$VANILLA_JAR" ]; then
    echo "ERROR: Vanilla JAR not found at $VANILLA_JAR"
    echo "Run scripts/decompile.sh first."
    exit 1
fi

# Clean previous build
rm -rf "$BUILD_DIR/classes"
mkdir -p "$BUILD_DIR/classes"

# Build classpath from all JARs in libs/
CLASSPATH="$VANILLA_JAR"
for jar in "$LIBS_DIR"/*.jar; do
    [ "$jar" = "$VANILLA_JAR" ] && continue
    CLASSPATH="$CLASSPATH:$jar"
done

# Collect source files based on tier
if [ "$TIER" = "lite" ]; then
    # Lite: only files in src/ that are part of the lite tier
    # All files in src/ are lite by default; full-only files go in src-full/
    SOURCE_FILES=$(fd -e java . "$SRC_DIR/" | /run/current-system/sw/bin/grep -v -E '(Test|test_|testall_)[^/]*\.java$' | /run/current-system/sw/bin/grep -v -E '(/tests/|/integration/LuaReturn\.java)')
elif [ "$TIER" = "full" ]; then
    # Full: src/ + src-full/ (when we add full tier later)
    SOURCE_FILES=$(fd -e java . "$SRC_DIR/" | /run/current-system/sw/bin/grep -v -E '(Test|test_|testall_)[^/]*\.java$' | /run/current-system/sw/bin/grep -v -E '(/tests/|/integration/LuaReturn\.java)')
    if [ -d "$PROJECT_DIR/src-full" ]; then
        SOURCE_FILES="$SOURCE_FILES $(fd -e java . "$PROJECT_DIR/src-full/")"
    fi
else
    echo "ERROR: Unknown tier '$TIER'. Use 'lite' or 'full'."
    exit 1
fi

FILE_COUNT=$(echo "$SOURCE_FILES" | wc -w)
echo "Compiling $FILE_COUNT files (target Java 17)..."

# shellcheck disable=SC2086
javac \
    --release 17 \
    -cp "$CLASSPATH" \
    -d "$BUILD_DIR/classes" \
    -Xlint:none \
    -proc:none \
    $SOURCE_FILES \
    2>&1 | tee "$BUILD_DIR/compile.log"

ERRORS=$(/run/current-system/sw/bin/grep -c " error:" "$BUILD_DIR/compile.log" 2>/dev/null || echo "0")
if [ "$ERRORS" -gt 0 ]; then
    echo ""
    echo "Compilation failed with $ERRORS error(s)."
    exit 1
fi

CLASS_COUNT=$(find "$BUILD_DIR/classes" -name "*.class" 2>/dev/null | wc -l)
echo ""
echo "=== Build Complete ==="
echo "Tier:    $TIER"
echo "Classes: $CLASS_COUNT"
echo "Output:  $BUILD_DIR/classes/"
