# OptiZomb

Performance optimization mod for Project Zomboid. 20 toggleable client-only rendering optimizations -- safe on any vanilla server. No gameplay or simulation changes.

> **Status:** We are overhauling the optimizations for stability. Only **glfix** (GPU error check removal -- the biggest single performance win) is enabled by default. Other optimizations can be enabled individually in `optizomb.properties` at your own risk. We will re-enable them as they are verified stable.

## Install

1. Download `OptiZomb-Lite-Installer.jar` from [Releases](https://github.com/jaidaken/optizomb/releases)
2. Run: `java -jar OptiZomb-Lite-Installer.jar`
3. The installer auto-detects your PZ install, or browse manually
4. Click **Install**

To uninstall, run the installer again and click **Uninstall**.

## What It Does

**Enabled by default:**
- GPU pipeline stall removal (glGetError bypass) -- **glfix**

**Disabled by default (being overhauled):**
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

Edit `optizomb.properties` in your PZ directory to toggle individual optimizations. Most are disabled by default during the stability overhaul -- enable them at your own risk.

## Requirements

- Project Zomboid (Steam)
- Java 17+ (PZ ships its own JRE, or use any system JDK)

## Documentation

See [`docs/`](docs/) for optimization details, architecture, and safety audit.
