#!/usr/bin/env bash
set -euo pipefail

# Build from pure vanilla + only our new optizomb/ classes
# No patches applied to any vanilla file — isolates whether the bug
# is in old patches or our new code

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VANILLA_DIR="$PROJECT_DIR/vanilla"
LIBS_DIR="$PROJECT_DIR/libs"
BUILD_DIR="$PROJECT_DIR/build"
VANILLA_JAR="$LIBS_DIR/projectzomboid.jar"

echo "=== Clean Test Build (vanilla + optizomb classes only) ==="

if [ ! -f "$VANILLA_JAR" ]; then
    echo "ERROR: Vanilla JAR not found"
    exit 1
fi

rm -rf "$BUILD_DIR/classes"
mkdir -p "$BUILD_DIR/classes"

CLASSPATH="$VANILLA_JAR"
for jar in "$LIBS_DIR"/*.jar; do
    [ "$jar" = "$VANILLA_JAR" ] && continue
    CLASSPATH="$CLASSPATH:$jar"
done

# Only compile our new optizomb classes (they have no vanilla file counterpart)
SOURCE_FILES=$(fd -e java . "$PROJECT_DIR/src/zombie/optizomb/")

FILE_COUNT=$(echo "$SOURCE_FILES" | wc -w)
echo "Compiling $FILE_COUNT new optizomb files only..."

# shellcheck disable=SC2086
javac \
    --release 17 \
    -cp "$CLASSPATH" \
    -d "$BUILD_DIR/classes" \
    -Xlint:none \
    -proc:none \
    $SOURCE_FILES \
    2>&1

CLASS_COUNT=$(find "$BUILD_DIR/classes" -name "*.class" 2>/dev/null | wc -l)
echo ""
echo "=== Clean Test Build Complete ==="
echo "Classes: $CLASS_COUNT (optizomb only — rest comes from vanilla JAR)"
