#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GAME_DIR="/mnt/data/SteamLibrary/steamapps/common/ProjectZomboid/projectzomboid"
RUNTIME="$PROJECT_DIR/runtime"

if [ ! -d "$GAME_DIR" ]; then
    echo "ERROR: Game directory not found at $GAME_DIR"
    exit 1
fi

echo "Copying game files to runtime/ ..."
rm -rf "$RUNTIME"
cp -a "$GAME_DIR/" "$RUNTIME"

# Remove stock class dirs — our JAR on the classpath takes priority
for d in astar com de fmod javax N3D org se zombie; do
    rm -rf "$RUNTIME/$d"
done

echo "Runtime directory ready at $RUNTIME"
du -sh "$RUNTIME"
