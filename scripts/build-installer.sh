#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
INSTALLER_SRC="$PROJECT_DIR/installer/src"
INSTALLER_BUILD="$BUILD_DIR/installer"
PATCHES_DIR="$BUILD_DIR/patches"
CONFIG_DIR="$PROJECT_DIR/config"
OUTPUT_JAR="$BUILD_DIR/OptiZomb-Lite-Installer.jar"

JBSDIFF_JAR="$PROJECT_DIR/tools/jbsdiff-1.0.jar"
COMPRESS_JAR="$PROJECT_DIR/tools/commons-compress-1.21.jar"
FIND="/run/current-system/sw/bin/find"

# Game installation (patch base for bsdiff)
PZ_GAME_DIR="/mnt/data/SteamLibrary/steamapps/common/ProjectZomboid/projectzomboid"

# Tier (default: lite)
TIER="${1:-lite}"
PATCH_SRC_DIR="$PROJECT_DIR/patches/$TIER"
SHADERS_DIR="$PATCH_SRC_DIR/resources/shaders"

VERSION=$(cat "$PROJECT_DIR/version.txt" | tr -d '[:space:]')

echo "=== OptiZomb Installer Build (tier: $TIER, v$VERSION) ==="

# Step 1: Build if needed
if [ ! -d "$BUILD_DIR/classes" ]; then
    echo "Classes not found, building first..."
    bash "$PROJECT_DIR/scripts/build.sh" "$TIER"
    echo ""
fi

# Step 2: Generate bsdiff patches — ONLY for files we actually modified
echo "Generating binary patches (bsdiff)..."
rm -rf "$PATCHES_DIR"
mkdir -p "$PATCHES_DIR"

JBSDIFF_CP="$JBSDIFF_JAR:$COMPRESS_JAR"

# Determine vanilla class base
if [ -d "$PZ_GAME_DIR/zombie" ]; then
    VANILLA_BASE="$PZ_GAME_DIR"
    echo "  Patch base: on-disk game classes"
else
    VANILLA_BASE="$BUILD_DIR/vanilla-extract"
    mkdir -p "$VANILLA_BASE"
    (cd "$VANILLA_BASE" && jar xf "$PROJECT_DIR/libs/projectzomboid.jar")
    echo "  Patch base: extracted from JAR (game dir not found)"
fi

PATCH_COUNT=0
NEW_COUNT=0

# 2a: Process MODIFIED vanilla files — generate bsdiff patches
# Source of truth: .java.patch files in patches/<tier>/
echo "  Generating bsdiff for modified classes..."
while IFS= read -r patchFile; do
    # patches/lite/zombie/Foo.java.patch → zombie/Foo
    relJava="${patchFile#"$PATCH_SRC_DIR/"}"
    relJava="${relJava%.patch}"             # zombie/Foo.java
    baseName="${relJava%.java}"             # zombie/Foo

    # Find all .class files for this source (including inner classes)
    # Pattern: zombie/Foo.class, zombie/Foo$Inner.class, zombie/Foo$1.class
    while IFS= read -r -d '' classFile; do
        relClass="${classFile#"$BUILD_DIR/classes/"}"
        vanillaFile="$VANILLA_BASE/$relClass"

        if [ -f "$vanillaFile" ]; then
            patchOut="$PATCHES_DIR/${relClass}.patch"
            mkdir -p "$(dirname "$patchOut")"
            java -cp "$JBSDIFF_CP" io.sigpipe.jbsdiff.ui.CLI diff \
                "$vanillaFile" "$classFile" "$patchOut"
            PATCH_COUNT=$((PATCH_COUNT + 1))
        else
            # Modified source but class not in vanilla (shouldn't happen, but handle it)
            mkdir -p "$(dirname "$PATCHES_DIR/$relClass")"
            cp "$classFile" "$PATCHES_DIR/$relClass"
            NEW_COUNT=$((NEW_COUNT + 1))
        fi
    done < <($FIND "$BUILD_DIR/classes" -path "*/${baseName}.class" -print0 -o \
                                         -path "*/${baseName}\$*.class" -print0)
done < <($FIND "$PATCH_SRC_DIR" -name "*.java.patch" -print)

# 2b: Process NEW files — copy compiled classes as-is
echo "  Copying new classes..."
if [ -d "$PATCH_SRC_DIR/new" ]; then
    while IFS= read -r srcFile; do
        relJava="${srcFile#"$PATCH_SRC_DIR/new/"}"
        baseName="${relJava%.java}"

        while IFS= read -r -d '' classFile; do
            relClass="${classFile#"$BUILD_DIR/classes/"}"
            mkdir -p "$(dirname "$PATCHES_DIR/$relClass")"
            cp "$classFile" "$PATCHES_DIR/$relClass"
            NEW_COUNT=$((NEW_COUNT + 1))
        done < <($FIND "$BUILD_DIR/classes" -path "*/${baseName}.class" -print0 -o \
                                             -path "*/${baseName}\$*.class" -print0)
    done < <($FIND "$PATCH_SRC_DIR/new" -name "*.java" -print)
fi

echo "  PATCH (bsdiff): $PATCH_COUNT"
echo "  NEW (raw):      $NEW_COUNT"

# Step 3: Copy shaders
echo "Copying shaders..."
SHADER_COUNT=0
if [ -d "$SHADERS_DIR" ]; then
    mkdir -p "$PATCHES_DIR/../shaders-staging"
    for sf in "$SHADERS_DIR"/*; do
        [ -f "$sf" ] || continue
        cp "$sf" "$PATCHES_DIR/../shaders-staging/"
        SHADER_COUNT=$((SHADER_COUNT + 1))
    done
fi
echo "  $SHADER_COUNT shaders"

# Step 4: Compile installer
echo "Compiling installer..."
rm -rf "$INSTALLER_BUILD"
mkdir -p "$INSTALLER_BUILD"

# Inject version from version.txt into installer source (compile from temp copy)
INSTALLER_TMP="$BUILD_DIR/installer-src"
mkdir -p "$INSTALLER_TMP"
sed "s/VERSION = \"[^\"]*\"/VERSION = \"$VERSION\"/" \
    "$INSTALLER_SRC/OptiZombInstaller.java" > "$INSTALLER_TMP/OptiZombInstaller.java"

javac --release 8 \
    -cp "$JBSDIFF_JAR:$COMPRESS_JAR" \
    -d "$INSTALLER_BUILD" \
    "$INSTALLER_TMP/OptiZombInstaller.java"

rm -rf "$INSTALLER_TMP"

# Step 5: Package installer JAR
echo "Packaging installer JAR..."

# Embed bsdiff patches + new classes
mkdir -p "$INSTALLER_BUILD/patches"
cp -r "$PATCHES_DIR"/* "$INSTALLER_BUILD/patches/"

# Embed shaders (single location, under shaders/)
if [ -d "$PATCHES_DIR/../shaders-staging" ]; then
    mkdir -p "$INSTALLER_BUILD/shaders"
    cp "$PATCHES_DIR/../shaders-staging"/* "$INSTALLER_BUILD/shaders/" 2>/dev/null || true
    rm -rf "$PATCHES_DIR/../shaders-staging"
fi

# Embed config
cp "$CONFIG_DIR/optizomb.properties.default" "$INSTALLER_BUILD/" 2>/dev/null || true

# Embed jbsdiff runtime
cd "$INSTALLER_BUILD"
jar xf "$JBSDIFF_JAR"
jar xf "$COMPRESS_JAR"
rm -rf META-INF/maven META-INF/MANIFEST.MF META-INF/LICENSE* META-INF/NOTICE* 2>/dev/null || true

# Create manifest and package
echo "Main-Class: OptiZombInstaller" > MANIFEST.MF
jar cfm "$OUTPUT_JAR" MANIFEST.MF .
cd "$PROJECT_DIR"

# Cleanup
rm -rf "$BUILD_DIR/vanilla-extract" 2>/dev/null || true

JAR_SIZE=$(du -h "$OUTPUT_JAR" | cut -f1)

echo ""
echo "=== Installer Build Complete ==="
echo "Output:  $OUTPUT_JAR ($JAR_SIZE)"
echo "Patches: $PATCH_COUNT bsdiff + $NEW_COUNT new classes"
echo "Shaders: $SHADER_COUNT"
echo ""
echo "To install: java -jar $OUTPUT_JAR"
