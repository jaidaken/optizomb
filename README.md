# OptiZomb

Performance optimization mod for Project Zomboid Build 41. 20 toggleable client-only rendering optimizations - safe on any vanilla server. No gameplay or simulation changes.

## Versions

- **v1/** - patches against old decompile (94.8% bytecode match). All 20 features implemented.
- **v2/** - patches against new decompile (97.2% bytecode match). Porting in progress.

## v2 Build

```
bash v2/scripts/apply-patches.sh
bash v2/scripts/build-minimal.sh
bash v2/scripts/build-installer.sh
```

## v2 Testing

```
bash scripts/setup-runtime.sh     # one-time: copy game files
bash v2/scripts/launch.sh         # run modded game
```

Check `userdata/Logs/*_DebugLog.txt` for `[ZombPerf]` and `[GPUPerf]` lines (debug mode).

## Configuration

Edit `optizomb.properties` in your PZ directory. See `config/optizomb.properties.default` for all flags.

## Requirements

- Project Zomboid Build 41 (Steam)
- Java 17+ (PZ ships its own JRE)
