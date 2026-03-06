#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VANILLA_DIR="$PROJECT_DIR/vanilla"
PATCHES_DIR="$PROJECT_DIR/patches"
SRC_DIR="$PROJECT_DIR/src"

TIER="${1:-lite}"

echo "=== OptiZomb — Apply Patches ($TIER tier) ==="

if [ ! -d "$VANILLA_DIR/zombie" ]; then
    echo "ERROR: Vanilla source not found at $VANILLA_DIR"
    echo "Run scripts/decompile.sh first."
    exit 1
fi

# Clean src/
rm -rf "$SRC_DIR"
mkdir -p "$SRC_DIR"

apply_tier() {
    local tier_dir="$1"
    local tier_name="$2"
    local patched=0
    local copied=0

    if [ ! -d "$tier_dir" ]; then
        echo "  No patches for $tier_name tier"
        return
    fi

    echo "Applying $tier_name patches..."

    # Apply .patch files (unified diffs against vanilla)
    while IFS= read -r patchFile; do
        # e.g., patches/lite/zombie/iso/IsoCell.java.patch
        rel="${patchFile#$tier_dir/}"          # zombie/iso/IsoCell.java.patch
        srcRel="${rel%.patch}"                  # zombie/iso/IsoCell.java
        vanillaFile="$VANILLA_DIR/$srcRel"
        outputFile="$SRC_DIR/$srcRel"

        if [ -f "$vanillaFile" ]; then
            mkdir -p "$(dirname "$outputFile")"
            # Apply unified diff patch
            if patch -s -o "$outputFile" "$vanillaFile" "$patchFile" 2>/dev/null; then
                patched=$((patched + 1))
            else
                echo "  FAILED: $srcRel (applying manually, check for conflicts)"
                # Copy vanilla as base so it's at least compilable
                cp "$vanillaFile" "$outputFile"
                # Try with fuzz
                patch -o "$outputFile" "$vanillaFile" "$patchFile" 2>&1 | head -5
            fi
        else
            echo "  WARNING: Vanilla file missing for $srcRel"
        fi
    done < <(find "$tier_dir" -name "*.patch" -not -path "*/new/*" -print0 | tr '\0' '\n')

    # Copy new files (no vanilla counterpart)
    if [ -d "$tier_dir/new" ]; then
        while IFS= read -r newFile; do
            rel="${newFile#$tier_dir/new/}"
            outputFile="$SRC_DIR/$rel"
            mkdir -p "$(dirname "$outputFile")"
            cp "$newFile" "$outputFile"
            copied=$((copied + 1))
        done < <(find "$tier_dir/new" -name "*.java" -print0 | tr '\0' '\n')
    fi

    echo "  Patched: $patched files, New: $copied files"
}

# Always apply lite tier
apply_tier "$PATCHES_DIR/lite" "lite"

# Apply full tier on top if requested
if [ "$TIER" = "full" ]; then
    apply_tier "$PATCHES_DIR/full" "full"
fi

# Summary
TOTAL=$(find "$SRC_DIR" -name "*.java" 2>/dev/null | wc -l)
echo ""
echo "=== Patches Applied ==="
echo "Tier:  $TIER"
echo "Files: $TOTAL in src/"
echo ""
echo "Next: bash scripts/build.sh"
