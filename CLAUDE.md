# OptiZomb - Claude Code Instructions

## Project Overview
Performance optimization mod for Project Zomboid. Two versions coexist for comparison:
- **v1/** - patches against the old decompile (94.8% bytecode match)
- **v2/** - patches against the new decompile (97.2% bytecode match)

Both use unified diffs against vanilla decompiled source. Two tiers: Lite (client-only rendering) and Full (parallel simulation).

## Project Layout
```
pz-optizomb/
  v1/                    # Old decompile version
    patches/lite/        # 35 modified + 24 new files
    scripts/             # v1 build pipeline
    vanilla/             # Old decompiled source (gitignored)
    src/                 # Old patched source (gitignored)
    build/               # Old build output (gitignored)
  v2/                    # New decompile version (in progress)
    patches/lite/        # Patches being ported feature-by-feature
    scripts/             # v2 build pipeline
    vanilla/             # New decompiled source (gitignored)
    src/                 # New patched source (gitignored)
    build/               # New build output (gitignored)
  libs/                  # Shared dependency JARs
  installer/             # Shared installer source
  config/                # Shared config files
  tools/                 # Shared tools (decompiler, JDK)
  runtime/               # Game files for local testing
  docs/                  # Optimization documentation
```

## v2 Build Pipeline
```
v2/scripts/apply-patches.sh → v2/scripts/build-minimal.sh → v2/scripts/build-installer.sh
```

1. `bash v2/scripts/apply-patches.sh [lite|full]` - Apply patches to vanilla -> v2/src/
2. `bash v2/scripts/build-minimal.sh` - Compile modified files -> v2/build/classes/
3. `bash v2/scripts/build-installer.sh` - Generate bsdiff patches + package installer JAR

## v1 Build Pipeline (reference)
```
v1/scripts/apply-patches.sh → v1/scripts/build-minimal.sh → v1/scripts/build-installer.sh
```

## Testing
- `bash scripts/setup-runtime.sh` - Copy game files for local testing (one-time, shared)
- `bash v2/scripts/launch.sh` - Run v2 modded game
- Check `userdata/Logs/*_DebugLog.txt` for performance data (`grep ZombPerf`)

## Conventions
- After code changes, update the patch: `bash v2/scripts/update-patches.sh`
- New files go in `v2/patches/lite/new/<path>`
- Classify every optimization: Lite (client-only) or Full (client+server)
- Do NOT lower the zombie render cap (short0 = 4096)
- Do NOT use install.sh - always build the installer JAR

## System Notes
- `find` is aliased to `fd` on this system
- `grep` is aliased to `rg` - use `/run/current-system/sw/bin/grep` for POSIX grep in scripts
- Game dir: `/mnt/data/SteamLibrary/steamapps/common/ProjectZomboid/projectzomboid/`
