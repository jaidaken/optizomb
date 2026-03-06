# OptiZomb

Performance optimization mod for Project Zomboid. 2.5-3x FPS improvement at 500+ zombies.

## Install

1. Download `OptiZomb-Lite-Installer.jar` from [Releases](https://github.com/jaidaken/optizomb/releases)
2. Run: `java -jar OptiZomb-Lite-Installer.jar`
3. The installer auto-detects your PZ install, or browse manually
4. Click **Install**

To uninstall, run the installer again and click **Uninstall**.

## What It Does

20 toggleable client-only rendering optimizations — safe on any vanilla server. No gameplay or simulation changes.

- GPU pipeline stall removal (glGetError bypass)
- GL state caching (shader bind, VBO/EBO, camera)
- Bone matrix TBO (per-zombie uniform upload → single GPU buffer)
- Render flag bitmasks (cached instanceof checks)
- Batch texture merging (fewer draw call breaks)
- Floor tile instancing (GPU instanced rendering)
- Floor FBO caching (skip unchanged frames)
- Blood splat optimization (offscreen cull + color cache)
- Shadow LOD + deferred rendering
- Item container indexing (HashSet dedup, type index)
- Scene cull + distance-based LOD
- Bone LOD for distant zombies
- Cutaway result caching
- Fog row skip
- Separation + AI throttling

## Configuration

Edit `optizomb.properties` in your PZ directory to toggle individual optimizations. All are enabled by default.

## Requirements

- Project Zomboid (Steam)
- Java 17+ (PZ ships its own JRE, or use any system JDK)

## Documentation

See [`docs/`](docs/) for optimization details, architecture, and safety audit.
