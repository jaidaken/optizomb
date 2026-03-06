#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME="$PROJECT_DIR/runtime"
BUILD_DIR="$PROJECT_DIR/build"

# For testing, we build a full JAR with our classes injected
OUR_JAR="$BUILD_DIR/optizomb-test.jar"

if [ ! -d "$BUILD_DIR/classes" ]; then
    echo "ERROR: Build not found. Run scripts/build.sh first."
    exit 1
fi

if [ ! -d "$RUNTIME" ]; then
    echo "ERROR: Runtime not found. Run scripts/setup-runtime.sh first."
    exit 1
fi

# Create test JAR: vanilla + our classes
echo "Creating test JAR..."
cp "$PROJECT_DIR/libs/projectzomboid.jar" "$OUR_JAR"
cd "$BUILD_DIR/classes"
jar uf "$OUR_JAR" .
cd "$PROJECT_DIR"

JAVA="$(which java)"

CLASSPATH="$OUR_JAR:$RUNTIME"
for jar in "$RUNTIME"/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

NATIVE_PATH="$RUNTIME/linux64:$RUNTIME"

echo "=== OptiZomb Launch ==="
echo "JAR:     $OUR_JAR"
echo "Runtime: $RUNTIME"
echo "Java:    $($JAVA -version 2>&1 | head -1)"
echo ""

CACHE_DIR="$PROJECT_DIR/userdata"
mkdir -p "$CACHE_DIR"

cd "$RUNTIME"

export LD_LIBRARY_PATH="${NATIVE_PATH}:${LD_LIBRARY_PATH:-}"
XMODIFIERS= LD_PRELOAD="${LD_PRELOAD:-}:libjsig.so:libPZXInitThreads64.so" \
exec "$JAVA" \
    -Djava.awt.headless=true \
    -Xmx32G \
    -Dzomboid.steam=1 \
    -Dzomboid.znetlog=1 \
    "-Djava.library.path=$NATIVE_PATH" \
    -Djava.security.egd=file:/dev/./urandom \
    -XX:+UseZGC \
    -XX:-OmitStackTraceInFastThrow \
    -cp "$CLASSPATH" \
    zombie.gameStates.MainScreenState \
    "-cachedir=$CACHE_DIR" \
    -debug \
    "$@"
