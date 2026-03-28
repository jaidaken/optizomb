#!/usr/bin/env bash
set -euo pipefail

# Verify decompiler output by recompiling vanilla source and comparing
# the resulting bytecode against the original game classes.
#
# Usage:
#   bash scripts/verify-decompile.sh              # verify all vanilla files
#   bash scripts/verify-decompile.sh --summary    # summary only
#   bash scripts/verify-decompile.sh --javac /path/to/javac  # use specific javac
#
# For best results, use the exact JDK that compiled PZ:
#   Azul Zulu JDK 17.0.1 (Zulu17.30+15-CA)
#   https://www.azul.com/downloads/?version=java-17-lts&package=jdk#zulu

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VANILLA_DIR="$PROJECT_DIR/vanilla"
LIBS_DIR="$PROJECT_DIR/libs"
VANILLA_JAR="$LIBS_DIR/projectzomboid.jar"
DECOMPILER_DIR="$PROJECT_DIR/tools/ZomboidDecompiler-src"
BUILD_DIR="$PROJECT_DIR/build"
VERIFY_CLASSES="$BUILD_DIR/verify-classes"

JAVAC_CMD="javac"
EXTRA_ARGS=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --javac)
            JAVAC_CMD="$2"
            shift 2
            ;;
        --summary)
            EXTRA_ARGS="$EXTRA_ARGS --summary-only"
            shift
            ;;
        --verbose)
            EXTRA_ARGS="$EXTRA_ARGS --verbose"
            shift
            ;;
        --strict)
            EXTRA_ARGS="$EXTRA_ARGS --strict-vars"
            shift
            ;;
        --no-color)
            EXTRA_ARGS="$EXTRA_ARGS --no-color"
            shift
            ;;
        --semantic)
            EXTRA_ARGS="$EXTRA_ARGS --semantic"
            shift
            ;;
        --help)
            echo "Usage: verify-decompile.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --javac PATH    Use specific javac compiler"
            echo "  --summary       Show summary statistics only"
            echo "  --verbose       Show all methods including matches"
            echo "  --strict        Compare variable indices directly"
            echo "  --no-color      Disable ANSI colors"
            echo "  --help          Show this help"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "=== OptiZomb - Decompiler Verification ==="
echo ""

# Validate prerequisites
if [ ! -d "$VANILLA_DIR" ]; then
    echo "ERROR: Vanilla directory not found at $VANILLA_DIR"
    echo "Run 'bash scripts/decompile.sh' first."
    exit 1
fi

if [ ! -f "$VANILLA_JAR" ]; then
    echo "ERROR: Vanilla JAR not found at $VANILLA_JAR"
    echo "Run 'bash scripts/decompile.sh' first."
    exit 1
fi

# Check javac
if ! command -v "$JAVAC_CMD" &>/dev/null; then
    echo "ERROR: javac not found: $JAVAC_CMD"
    exit 1
fi

JAVAC_VERSION=$("$JAVAC_CMD" -version 2>&1)
echo "javac: $JAVAC_VERSION"

# Check if this is the matching JDK
if echo "$JAVAC_VERSION" | /run/current-system/sw/bin/grep -q "17.0.1"; then
    echo "JDK version matches PZ build (17.0.1)"
else
    echo "WARNING: PZ was compiled with Azul Zulu JDK 17.0.1 (Zulu17.30+15-CA)"
    echo "         Using a different JDK will produce false positives (DUP optimizations, etc.)"
    echo "         Download exact JDK: https://www.azul.com/downloads/?version=java-17-lts&package=jdk#zulu"
    echo ""
fi

# Build classpath
CP="$VANILLA_JAR"
for jar in "$LIBS_DIR"/*.jar; do
    [ "$jar" = "$VANILLA_JAR" ] && continue
    CP="$CP:$jar"
done

# Clean and prepare output
rm -rf "$VERIFY_CLASSES"
mkdir -p "$VERIFY_CLASSES"

# Find vanilla source files (zombie.* only, excluding test files that need JUnit)
echo ""
echo "Finding vanilla source files (zombie.* only)..."
ARGFILE="$BUILD_DIR/verify-sources.txt"
fd -e java . "$VANILLA_DIR/zombie/" | /run/current-system/sw/bin/grep -viE '(test[^/]*\.java$|junit|IsoPuddles\.java$|IsoWater\.java$)' > "$ARGFILE"
SOURCE_COUNT=$(wc -l < "$ARGFILE")
echo "Found $SOURCE_COUNT .java files in zombie/ (excluding test files)"

# Compile all vanilla source
echo ""
echo "Compiling all vanilla source (this may take a minute)..."
"$JAVAC_CMD" \
    --release 17 \
    -cp "$CP" \
    -d "$VERIFY_CLASSES" \
    -Xlint:none \
    -proc:none \
    @"$ARGFILE" \
    2>&1 | tail -5

CLASS_COUNT=$(fd -e class . "$VERIFY_CLASSES" 2>/dev/null | wc -l)
echo "Compiled $CLASS_COUNT class files"

# Build decompiler if needed
if [ ! -d "$DECOMPILER_DIR/build/classes" ]; then
    echo ""
    echo "Building decompiler..."
    cd "$DECOMPILER_DIR"
    ./gradlew compileJava -q 2>&1 | tail -3
    cd "$PROJECT_DIR"
fi

# Run verification
echo ""
echo "Running bytecode verification..."
echo ""

cd "$DECOMPILER_DIR"
# shellcheck disable=SC2086
./gradlew -q verifyBytecode --args="$VANILLA_JAR $VERIFY_CLASSES --class-pattern zombie.* --context 5 $EXTRA_ARGS" 2>&1 | /run/current-system/sw/bin/grep -v "^WARNING:"

echo ""
echo "Verification classes saved in: $VERIFY_CLASSES"
echo "Re-run with --summary for quick overview, --verbose for full details."
