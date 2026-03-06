#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VANILLA_DIR="$PROJECT_DIR/vanilla"
SRC_DIR="$PROJECT_DIR/src"
PATCHES_DIR="$PROJECT_DIR/patches"

TIER="${1:-lite}"

echo "=== OptiZomb — Update Patches ($TIER tier) ==="

if [ ! -d "$VANILLA_DIR/zombie" ]; then
    echo "ERROR: Vanilla source not found. Run scripts/decompile.sh first."
    exit 1
fi

if [ ! -d "$SRC_DIR/zombie" ]; then
    echo "ERROR: Source not found. Run scripts/apply-patches.sh first, then make your edits."
    exit 1
fi

TIER_DIR="$PATCHES_DIR/$TIER"
updated=0
new_files=0

# For each .java file in src/, generate or update the patch
while IFS= read -r srcFile; do
    rel="${srcFile#$SRC_DIR/}"  # e.g., zombie/iso/IsoCell.java
    vanillaFile="$VANILLA_DIR/$rel"

    if [ -f "$vanillaFile" ]; then
        # Modified file — generate unified diff
        patchFile="$TIER_DIR/$rel.patch"
        mkdir -p "$(dirname "$patchFile")"
        diff -u "$vanillaFile" "$srcFile" > "$patchFile" 2>/dev/null || true
        if [ -s "$patchFile" ]; then
            updated=$((updated + 1))
        else
            # No differences — remove stale patch
            rm -f "$patchFile"
        fi
    else
        # New file — copy to new/
        mkdir -p "$TIER_DIR/new/$(dirname "$rel")"
        cp "$srcFile" "$TIER_DIR/new/$rel"
        new_files=$((new_files + 1))
    fi
done < <(find "$SRC_DIR" -name "*.java" -print0 | tr '\0' '\n')

echo "Updated: $updated patches"
echo "New files: $new_files"
echo ""
echo "Remember to git add patches/$TIER/ and commit."
