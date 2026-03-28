#!/usr/bin/env bash
set -euo pipefail

# Build ONLY files that differ from vanilla + new files.
# Vanilla-unchanged files keep their original bytecode in the JAR.

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ROOT_DIR="$(cd "$PROJECT_DIR/.." && pwd)"
VANILLA_DIR="$PROJECT_DIR/vanilla"
SRC_DIR="$PROJECT_DIR/src"
LIBS_DIR="$ROOT_DIR/libs"
BUILD_DIR="$PROJECT_DIR/build"
VANILLA_JAR="$LIBS_DIR/projectzomboid.jar"

echo "=== OptiZomb v2 Minimal Build (modified files only) ==="

if [ ! -f "$VANILLA_JAR" ]; then
    echo "ERROR: Vanilla JAR not found at $VANILLA_JAR"
    exit 1
fi

rm -rf "$BUILD_DIR/classes"
mkdir -p "$BUILD_DIR/classes"

CLASSPATH="$VANILLA_JAR"
for jar in "$LIBS_DIR"/*.jar; do
    [ "$jar" = "$VANILLA_JAR" ] && continue
    CLASSPATH="$CLASSPATH:$jar"
done

# Find files that differ from vanilla or are new
MODIFIED=""
for src_file in $(fd -e java . "$SRC_DIR/"); do
    rel="${src_file#$SRC_DIR/}"
    vanilla_file="$VANILLA_DIR/$rel"
    if [ ! -f "$vanilla_file" ]; then
        # New file (no vanilla counterpart)
        MODIFIED="$MODIFIED $src_file"
    elif ! diff -q "$vanilla_file" "$src_file" > /dev/null 2>&1; then
        # Modified file
        MODIFIED="$MODIFIED $src_file"
    fi
done

FILE_COUNT=$(echo $MODIFIED | wc -w)
echo "Compiling $FILE_COUNT modified/new files only..."
echo "Files:"
for f in $MODIFIED; do echo "  ${f#$SRC_DIR/}"; done

# shellcheck disable=SC2086
javac \
    --release 17 \
    -cp "$CLASSPATH" \
    -d "$BUILD_DIR/classes" \
    -Xlint:none \
    -proc:none \
    $MODIFIED \
    2>&1 | tee "$BUILD_DIR/compile.log"

ERRORS=$(/run/current-system/sw/bin/grep -c " error:" "$BUILD_DIR/compile.log" 2>/dev/null || echo "0")
if [ "$ERRORS" -gt 0 ]; then
    echo "Compilation failed with $ERRORS error(s)."
    exit 1
fi

CLASS_COUNT=$(find "$BUILD_DIR/classes" -name "*.class" 2>/dev/null | wc -l)

echo ""
echo "=== v2 Minimal Build Complete ==="
echo "Classes: $CLASS_COUNT (only modified - rest stays as vanilla bytecode)"
