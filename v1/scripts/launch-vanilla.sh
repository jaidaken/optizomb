#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME="$PROJECT_DIR/runtime"
VANILLA_JAR="$PROJECT_DIR/libs/projectzomboid.jar"

if [ ! -f "$VANILLA_JAR" ]; then
    echo "ERROR: Vanilla JAR not found at $VANILLA_JAR"
    echo "Run scripts/decompile.sh first."
    exit 1
fi

if [ ! -d "$RUNTIME" ]; then
    echo "ERROR: Runtime not found. Run scripts/setup-runtime.sh first."
    exit 1
fi

JAVA="$(which java)"

CLASSPATH="$VANILLA_JAR:$RUNTIME"
for jar in "$RUNTIME"/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

NATIVE_PATH="$RUNTIME/linux64:$RUNTIME"

echo "=== Vanilla Launch (decompiled) ==="
echo "JAR:     $VANILLA_JAR"
echo "Runtime: $RUNTIME"
echo "Java:    $($JAVA -version 2>&1 | head -1)"
echo ""

CACHE_DIR="$PROJECT_DIR/userdata-vanilla"
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
