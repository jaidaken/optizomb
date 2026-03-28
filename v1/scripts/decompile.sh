#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="$PROJECT_DIR/tools"
DECOMPILER_DIR="$TOOLS_DIR/ZomboidDecompiler-src"
DECOMPILER_DIST="$DECOMPILER_DIR/build/install/ZomboidDecompiler-src"
VANILLA_DIR="$PROJECT_DIR/vanilla"
LIBS_DIR="$PROJECT_DIR/libs"

# Game installation path
PZ_GAME_DIR="/mnt/data/SteamLibrary/steamapps/common/ProjectZomboid/projectzomboid"

echo "=== OptiZomb - Decompile Vanilla ==="
echo "Game:   $PZ_GAME_DIR"
echo "Output: $VANILLA_DIR"
echo ""

if [ ! -d "$PZ_GAME_DIR/zombie" ]; then
    echo "ERROR: Game directory not found at $PZ_GAME_DIR"
    echo "Update PZ_GAME_DIR in this script to match your installation."
    exit 1
fi

if ! command -v java &>/dev/null; then
    echo "ERROR: Java not found in PATH"
    exit 1
fi

echo "Java: $(java --version 2>&1 | head -1)"
echo ""

# Build the decompiler if not already built
if [ ! -d "$DECOMPILER_DIST" ]; then
    echo "[0/5] Building ZomboidDecompiler..."
    cd "$DECOMPILER_DIR"
    if [ -f "gradlew" ]; then
        ./gradlew installDist 2>&1 | tail -5
    else
        echo "ERROR: No gradlew found in $DECOMPILER_DIR"
        echo "Build the decompiler manually first."
        exit 1
    fi
    cd "$PROJECT_DIR"
fi

# Clean previous output
rm -rf "$VANILLA_DIR"
mkdir -p "$VANILLA_DIR" "$LIBS_DIR"

# Step 1: Copy dependency JARs from game
echo "[1/5] Copying dependency JARs..."
cp "$PZ_GAME_DIR"/*.jar "$LIBS_DIR/" 2>/dev/null || true
echo "  Copied $(ls "$LIBS_DIR"/*.jar 2>/dev/null | wc -l) JARs to libs/"

# Step 2: Pack game classes into JAR for the decompiler
echo "[2/5] Packing class files..."
TEMP_JAR="$PROJECT_DIR/build/projectzomboid-temp.jar"
mkdir -p "$PROJECT_DIR/build"
(cd "$PZ_GAME_DIR" && jar cf "$TEMP_JAR" zombie/ se/ com/ de/ fmod/ javax/ N3D/ org/ astar/ 2>/dev/null) || true
echo "  Created temp JAR for decompilation"

# Step 3: Decompile zombie/ with ZomboidDecompiler
# The decompiler expects projectzomboid.jar inside the game dir.
# PZ ships loose .class files, so we create a staging dir with the JAR.
echo "[3/5] Decompiling zombie/ with ZomboidDecompiler..."
DECOMPILE_STAGE="$PROJECT_DIR/build/decompile-stage"
rm -rf "$DECOMPILE_STAGE"
mkdir -p "$DECOMPILE_STAGE"
cp "$TEMP_JAR" "$DECOMPILE_STAGE/projectzomboid.jar"
# Copy jre64 dir if present (decompiler uses it for java runtime resolution)
if [ -d "$PZ_GAME_DIR/jre64" ]; then
    ln -s "$PZ_GAME_DIR/jre64" "$DECOMPILE_STAGE/jre64"
fi
"$DECOMPILER_DIST/bin/ZomboidDecompiler-src" "$DECOMPILE_STAGE" "$PROJECT_DIR/vanilla-temp" 2>&1

# Move decompiled source to vanilla/
if [ -d "$PROJECT_DIR/vanilla-temp/source" ]; then
    mv "$PROJECT_DIR/vanilla-temp/source"/* "$VANILLA_DIR/" 2>/dev/null || true
fi

# Step 4: Decompile non-zombie packages with raw Vineflower
echo "[4/5] Decompiling remaining packages..."
VINEFLOWER_JAR="$DECOMPILER_DIST/lib/vineflower-1.11.2-module.jar"
TEMP_OTHER="$PROJECT_DIR/build/source-other"
(cd "$PZ_GAME_DIR" && jar cf "$PROJECT_DIR/build/pz-other.jar" se/ astar/ com/ de/ fmod/ javax/ N3D/ org/ 2>/dev/null) || true
mkdir -p "$TEMP_OTHER"
java -jar "$VINEFLOWER_JAR" "$PROJECT_DIR/build/pz-other.jar" "$TEMP_OTHER/" 2>&1 | tail -5

# Apply post-decompile transforms
echo "  Applying post-decompile transforms..."
java --module-path "$DECOMPILER_DIST/lib" \
    -m com.github.zomboiddecompiler/com.github.zomboiddecompiler.TransformFiles \
    "$TEMP_OTHER/" 2>&1

# Merge into vanilla/
for pkg in se astar com de fmod javax N3D org; do
    if [ -d "$TEMP_OTHER/$pkg" ]; then
        cp -r "$TEMP_OTHER/$pkg" "$VANILLA_DIR/"
    fi
done

# Step 5: Create vanilla JAR for compilation
echo "[5/5] Creating libs/projectzomboid.jar..."
cp "$TEMP_JAR" "$LIBS_DIR/projectzomboid.jar"

# Cleanup
rm -rf "$PROJECT_DIR/vanilla-temp" "$TEMP_OTHER" "$TEMP_JAR" "$PROJECT_DIR/build/pz-other.jar" "$DECOMPILE_STAGE"

# Verify
echo ""
echo "=== Decompilation Complete ==="
TOTAL=$(find "$VANILLA_DIR" -name "*.java" 2>/dev/null | wc -l)
echo "Decompiled $TOTAL .java files to vanilla/"
echo "Dependency JARs in libs/"
du -sh "$VANILLA_DIR" "$LIBS_DIR"
