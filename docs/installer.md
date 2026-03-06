# OptiZomb Lite — Modular Installer

## Status: Implemented

All sections below reflect the current working implementation.

## Concept

Two install modes:
- **Simple** — One button, installs everything, maximum performance.
- **Advanced** — Toggle individual optimizations on/off. For testing, debugging, and isolating bugs.

---

## Distribution Principle: Patch, Never Replace

The installer **never distributes PZ's compiled code**. Instead it ships:
- **Binary diffs** (bsdiff format) for modified vanilla .class files
- **New .class files** that are 100% our code (no vanilla IP)

At install time, the installer reads the user's own vanilla .class files and
applies bspatch to produce the modified versions. Same approach as OptiFine.

**Library:** jbsdiff (`io.sigpipe:jbsdiff:1.0`) — BSD 2-Clause, pure Java.
Used at build time (bsdiff) via CLI and at install time (bspatch) via
`io.sigpipe.jbsdiff.Patch.patch()`. Bundled in `tools/jbsdiff-1.0.jar` +
`tools/commons-compress-1.21.jar`.

---

## Build Pipeline

### `scripts/build.sh [lite|full]`
1. Applies patches via `scripts/apply-patches.sh` (unified diffs from `patches/` → `src/`).
2. Compiles `src/` against **vanilla** classpath (`libs/`) → `build/classes/`.

### `scripts/build-installer.sh`
1. Runs `scripts/build.sh` if classes don't exist yet.
2. For each compiled `.class` file:
   - If it exists in vanilla → generates a `.class.patch` bsdiff file in `build/patches/`.
   - If it does NOT exist in vanilla → copies the raw `.class` file to `build/patches/`.
3. Copies shader files and default config to `build/patches/`.
4. Compiles `OptiZombInstaller.java` with jbsdiff on classpath.
5. Embeds into the installer JAR:
   - `patches/*.class.patch` — bsdiff diffs for PATCH classes
   - `patches/*.class` — raw NEW classes (100% our code)
   - `patches/media/shaders/*` — shader files
   - `patches/optizomb.properties.default` — default config
   - `io/` — jbsdiff runtime classes (for bspatch at install time)
   - `org/` — commons-compress classes (jbsdiff dependency)
4. Output: `PLAN/OPTIZOMB/installer/OptiZomb-Lite-Installer.jar`

---

## Class Categories

### PATCH classes (vanilla .class modified — ship as bsdiff only)
These files exist in vanilla PZ. We ship only the binary diff.
35 source files → `.class.patch` files (including inner classes).

| Source file | Optimization groups |
|-------------|-------------------|
| `PZGLUtil.java` | GL Fix |
| `Shader.java` | GL Fix, GL Chars, Bone TBO |
| `Model.java` | GL Fix, GL Chars |
| `ModelSlotRenderData.java` | GL Fix, GL Chars, Bone TBO |
| `VertexBufferObject.java` | GL Chars |
| `Core.java` | GL Chars |
| `HeightTerrain.java` | Terrain |
| `IsoGridSquare.java` | Tiles+Chars, Floor Pipeline, Floor FBO |
| `IsoObject.java` | Tiles+Chars, Floor Pipeline |
| `SpriteRenderer.java` | Batch Merge |
| `IsoCell.java` | Floor Pipeline, Floor FBO, Shadows, Items |
| `IsoMarkers.java` | Floor Pipeline |
| `IsoChunkMap.java` | Blood |
| `IsoFloorBloodSplat.java` | Blood |
| `IsoGameCharacter.java` | Shadows |
| `BaseVehicle.java` | Shadows |
| `IsoDeadBody.java` | Floor FBO |
| `ItemContainer.java` | Items |
| `InventoryItem.java` | Items |
| `Food.java` | Items |
| `AlarmClock.java` | Items |
| `AlarmClockClothing.java` | Items |
| `Clothing.java` | Items |
| `DrainableComboItem.java` | Items |
| `IngameState.java` | Diagnostics |
| `GameWindow.java` | Diagnostics |
| `MultiTextureFBO2.java` | Zoom |
| `MainScreenState.java` | Branding |
| `IsoWorld.java` | Lite overlay (scene cull, bone precompute) |
| `AnimationPlayer.java` | Bone LOD |
| `ModelInstanceRenderData.java` | Bone TBO |

### NEW classes (our code — ship as complete .class files)
These files don't exist in vanilla. They're 100% our code, no IP concern.
23 source files → `.class` files (including inner classes).

| Source file | Purpose |
|-------------|---------|
| `OptiZombConfig.java` | Runtime feature flags |
| `OptiZombGLFix.java` | GL error bypass + vehicle texture opt |
| `OptiZombGLChars.java` | Character rendering GL state management |
| `OptiZombBoneTBO.java` | SSBO bone matrix upload |
| `OptiZombBoneTBOLog.java` | SSBO diagnostics |
| `OptiZombTilesChars.java` | Render flags bitmask + occlusion caching |
| `OptiZombBatchMerge.java` | Sprite batch texture ID merging |
| `OptiZombFloorPipeline.java` | Floor rendering sub-optimizations |
| `OptiZombFloorFBO.java` | Floor FBO caching diagnostics |
| `OptiZombBlood.java` | Blood splat offscreen cull + caching |
| `OptiZombShadows.java` | Deferred shadow rendering + texture sort |
| `OptiZombItems.java` | Item container type-index optimization |
| `OptiZombPerfLog.java` | Performance monitoring + diagnostics |
| `OptiZombGPUProfiler.java` | CPU-side render phase profiler |
| `OptiZombSceneCull.java` | Rank-based LOD + bone skip classification |
| `OptiZombTerrain.java` | GL attrib narrowing for HeightTerrain |
| `OptiZombZoom.java` | Deferred grid recalc during zoom |
| `OptiZombCutaway.java` | Per-square cutaway result caching |
| `OptiZombStartFrame.java` | StartFrame sub-phase profiling |
| `OptiZombFog.java` | Fog distance-based row skip |
| `OptiZombSeparate.java` | Separation throttle tightening |
| `OptiZombAI.java` | AI update throttle |
| `FloorFBOCache.java` | FBO lifecycle, capture/blit, dirty detection |

### Shader files (our GLSL code — ship as-is)
12 shader files.

| File | Purpose |
|------|---------|
| `basicEffect.vert/frag` | Modified character shader |
| `basicEffect_tbo.vert/frag` | TBO bone matrix variant |
| `basicEffect_static.vert` | Static model variant |
| `basicEffect_tbo_static.vert` | TBO static model variant |
| `floorTile.vert/frag` | Floor tile shader |
| `floorTileInstanced.vert/frag` | Instanced floor tile shader |
| `spriteInstanced.vert/frag` | Instanced sprite shader |

---

## The File Overlap Problem

Many optimizations modify the **same .class files**. We can't compile separate
variants for each toggle combination (2^N — impractical).

**Shared file map (Lite only):**

| File | Modified by |
|------|-------------|
| `Shader.java` | Opt 1 GL_FIX, 2 GL_CHARS, 3 BONE_TBO |
| `Model.java` | Opt 1 GL_FIX, 2 GL_CHARS |
| `ModelSlotRenderData.java` | Opt 1 GL_FIX, 2 GL_CHARS, 3 BONE_TBO |
| `IsoCell.java` | Opt 3 BONE_TBO, 6 FLOOR_PIPELINE, 7 FLOOR_FBO, 9 SHADOWS, 10 ITEMS |
| `IsoGridSquare.java` | Opt 4 TILES_CHARS, 6 FLOOR_PIPELINE, 7 FLOOR_FBO, 9 SHADOWS, 16 CUTAWAY |
| `IsoObject.java` | Opt 4 TILES_CHARS, 6 FLOOR_PIPELINE |
| `VertexBufferObject.java` | Opt 2 GL_CHARS |

## Solution: Runtime Feature Flags

**All optimizations are always installed.** The installer replaces the same set of .class files regardless of toggle selection. Each optimization is gated by a `static final boolean` flag resolved at class-load time.

```java
// OptiZombConfig.java
public class OptiZombConfig {
    private static final Properties props = new Properties();

    // Flags are static final — JIT eliminates dead branches at class load
    public static final boolean GL_FIX = resolve("opt.glfix", true);
    public static final boolean GL_CHARS = resolve("opt.glchars", true) && GL_FIX;
    public static final boolean BONE_TBO = resolve("opt.bonetbo", true) && GL_CHARS;
    public static final boolean TILES_CHARS = resolve("opt.tileschars", true);
    public static final boolean BATCH_MERGE = resolve("opt.batchmerge", true);
    public static final boolean FLOOR_PIPELINE = resolve("opt.floorpipeline", true);
    public static final boolean FLOOR_FBO = resolve("opt.floorfbo", true);
    public static final boolean BLOOD = resolve("opt.blood", true);
    public static final boolean SHADOWS = resolve("opt.shadows", true);
    public static final boolean ITEMS = resolve("opt.items", true);
    public static final boolean DIAGNOSTICS = resolve("opt.diagnostics", true);
    public static final boolean TERRAIN = resolve("opt.terrain", true);
    public static final boolean ZOOM = resolve("opt.zoom", true);
    public static final boolean SCENE_CULL = resolve("opt.scenecull", true);
    public static final boolean BONE_LOD = resolve("opt.bonelod", true);
    public static final boolean CUTAWAY = resolve("opt.cutaway", true);
    // ... + FOG, STARTFRAME, SEPARATE_THROTTLE, AI_THROTTLE, ZOMBIE_RENDER_CAP
}
```

**Config file search order** (checked in static initializer):
1. `{user.dir}/optizomb.properties` — inside `projectzomboid/` (where PZ sets CWD)
2. `{user.dir}/../optizomb.properties` — `ProjectZomboid/` parent directory
3. `{user.home}/.optizomb/config.properties` — user home fallback

If no config file is found, all flags default to `true` (all optimizations enabled).

**Performance note:** Flags are `static final boolean` — the JIT compiler eliminates disabled branches entirely. Zero runtime overhead.

**Default config file (`optizomb.properties`):**
```
opt.glfix=true
opt.glchars=true
opt.bonetbo=true
opt.tileschars=true
opt.batchmerge=true
opt.floorpipeline=true
opt.floorfbo=true
opt.blood=true
opt.shadows=true
opt.items=true
opt.diagnostics=true
opt.terrain=true
opt.zoom=true
opt.scenecull=true
opt.bonelod=true
opt.cutaway=true
opt.fog=true
opt.startframe=true
opt.separatethrottle=true
opt.aithrottle=true
# opt.zombiecap=4096
```

---

## Toggle Groups

Each toggle controls one logical optimization that can be independently enabled/disabled at runtime.

### Group 1: Core GL Fix (`opt.glfix`)
**Opt:** 1 (GL_FIX)
**What it does:** Removes glGetError GPU pipeline stalls from Shader.setLight(), strips redundant GL calls from Model.java, removes profiler lambda wrappers.
**Savings:** ~2ms
**Dependencies:** None
**Risk if disabled:** Performance loss. Should almost never be disabled.
**Files:** `Shader.java`, `PZGLUtil.java`, `Model.java`, `ModelSlotRenderData.java`

### Group 2: GL Character Rendering (`opt.glchars`)
**Opt:** 2 (GL_CHARS)
**What it does:** Shader bind caching, trig precompute, VBO/EBO bind caching, camera caching, push/pop elimination. All tightly coupled changes to the character rendering pipeline.
**Savings:** ~3ms
**Dependencies:** `opt.glfix` — enforced in code: `GL_CHARS = resolve(...) && GL_FIX`
**Why grouped:** Shader bind caching, VBO bind caching, camera caching, and push/pop elimination all modify overlapping methods in `Shader.java`, `ModelSlotRenderData.java`, `Model.java`, and `VertexBufferObject.java`. They build on each other incrementally — push/pop elimination requires consolidated GL setup.
**Files:** `Shader.java`, `Model.java`, `ModelSlotRenderData.java`, `VertexBufferObject.java`, `Core.java`

### Group 3: Bone Matrix TBO (`opt.bonetbo`)
**Opt:** 3 (BONE_TBO)
**What it does:** Packs all zombie bone matrices into one GPU buffer per frame. Shader reads via texelFetch instead of per-zombie uniform uploads.
**Savings:** ~1ms
**Dependencies:** `opt.glchars` — enforced in code: `BONE_TBO = resolve(...) && GL_CHARS`
**Fallback:** Outline/wireframe shaders auto-detect absence of `boneTBO` uniform and use `MatrixPalette[60]` path. When disabled, ALL zombies use the uniform path.
**Files:** `BoneMatrixTBO.java`, `basicEffect.vert`, `Shader.java`, `ModelInstanceRenderData.java`, `ModelSlotRenderData.java`, `IsoCell.java`

### Group 4: tilesChars Optimization (`opt.tileschars`)
**Opt:** 4 (TILES_CHARS)
**What it does:** Cached renderFlags bitmask replaces per-object instanceof/hash lookups. Frame-stamp sort dedup. GL call dedup.
**Savings:** ~1ms
**Dependencies:** None
**Fallback:** When disabled, skip renderFlags bitmask (use original per-object checks), skip sort dedup.
**Files:** `IsoGridSquare.java`, `IsoObject.java`

### Group 5: Batch Merging (`opt.batchmerge`)
**Opt:** 5 (BATCH_MERGE)
**What it does:** Compare TextureID instead of Texture object identity in SpriteRenderer.isStateChanged(). Reduces batch breaks by ~15%.
**Savings:** ~0.5ms
**Dependencies:** None
**Fallback:** Revert to `Texture` object identity comparison.
**Files:** `SpriteRenderer.java`

### Group 6: Floor Pipeline (`opt.floorpipeline`)
**Opt:** 6 (FLOOR_PIPELINE)
**What it does:** Floor CPU optimization (hoisted invariants), texture-sorted batch rendering, GPU instanced floor tiles.
**Savings:** ~5ms (floor 8.0ms → 1.3ms combined)
**Dependencies:** None
**Why grouped:** Instanced rendering requires tile classification infrastructure (`classifyFloorTiles()`, `DeferredFloorTile`). CPU hoisting is interleaved with the deferred path in `IsoCell.java` and `IsoGridSquare.java`. All form the floor render pipeline rewrite.
**Fallback:** When disabled, use original `renderFloorInternal()` path (per-tile immediate rendering). `IndieGL.StartShader` is hoisted before the fallback loop.
**Files:** `IsoCell.java`, `IsoGridSquare.java`, `IsoObject.java`, `IsoMarkers.java`, `FloorTileBatcher.java`, `floorTileInstanced.vert`, `floorTileInstanced.frag`

### Group 7: Floor FBO Cache (`opt.floorfbo`)
**Opt:** 7 (FLOOR_FBO)
**What it does:** Captures floor rendering to FBO, blits cached result when clean. Dirty detection on camera/lighting/content changes.
**Savings:** ~2ms + batch reduction when cache hits
**Dependencies:** None (works with either floor pipeline)
**Fallback:** Always mark dirty — renders every frame like vanilla.
**Files:** `FloorFBOCache.java`, `IsoCell.java`, `IsoDeadBody.java`, `IsoGridSquare.java`

### Group 8: Blood Splats (`opt.blood`)
**Opt:** 8 (BLOOD)
**What it does:** Offscreen culling, tile light cache, color hash cache, call chain bypass for blood splat rendering.
**Savings:** ~1ms (blood 1.2ms → 0.4ms)
**Dependencies:** None
**Fallback:** Use original `renderBloodSplat()` chain.
**Files:** `IsoChunkMap.java`, `IsoFloorBloodSplat.java`

### Group 9: Shadow LOD + Deferred (`opt.shadows`)
**Opt:** 9 (SHADOWS)
**What it does:** Skip bone-based shadow shape for distant zombies, defer all shadow quads for texture-sorted flush.
**Savings:** ~1ms
**Dependencies:** None
**Fallback:** Immediate `renderPoly()` for each shadow, full bone-based shape for all zombies.
**Files:** `IsoCell.java`, `IsoGameCharacter.java`, `IsoDeadBody.java`, `BaseVehicle.java`

### Group 10: Item Containers (`opt.items`)
**Opt:** 10 (ITEMS)
**What it does:** HashSet for ProcessItems, type index for containers, skip set for FindAndReturn, lazy ProcessItems filtering.
**Savings:** ~1ms (most visible during chunk loading, crafting, inventory operations)
**Dependencies:** None
**Fallback:** Bypass index lookups, use original linear scans.
**Files:** `IsoCell.java`, `ItemContainer.java`, `InventoryItem.java`, `Food.java`, `AlarmClock.java`, `AlarmClockClothing.java`, `Clothing.java`, `DrainableComboItem.java`

### Group 11: Diagnostics (`opt.diagnostics`)
**Opt:** 11 (DIAGNOSTICS)
**What it does:** ZombPerf + GPUProfiler timing infrastructure. Adds `[ZombPerf]` and `[GPUPerf]` log lines.
**Savings:** None (adds ~0.1ms overhead for timer reads, but essential for debugging)
**Dependencies:** None
**Fallback:** Skip all timer start/stop calls. No log output.
**Files:** `ZombiePerformanceLog.java`, `GPUProfiler.java`, `IngameState.java`, `GameWindow.java`

### Group 12: Terrain Fix (`opt.terrain`)
**Opt:** 12 (TERRAIN)
**What it does:** Removes redundant glBlendFunc that causes puddle diamond artifacts.
**Savings:** None (visual fix only)
**Dependencies:** None
**Fallback:** Restore original glBlendFunc call.
**Files:** `HeightTerrain.java`

### Group 13: Zoom Tuning (`opt.zoom`)
**Opt:** 13 (ZOOM)
**What it does:** Slower zoom speed, dirty recalc only at zoom completion.
**Savings:** ~0.3ms during zoom transitions
**Dependencies:** None
**Fallback:** Original zoom speed + per-frame dirty recalc during zoom.
**Files:** `MultiTextureFBO2.java`

### Group 14: Scene Cull (`opt.scenecull`)
**Opt:** 14 (SCENE_CULL)
**What it does:** Pre-computed cached cull scores + LOD tier 3 for rank 501+.
**Savings:** ~0.5ms
**Dependencies:** None
**Files:** `IsoWorld.java`, `IsoZombie.java`, `OptiZombSceneCull.java`

### Group 15: Bone LOD (`opt.bonelod`)
**Opt:** 15 (BONE_LOD)
**What it does:** Skip detail bone transforms for distant zombies.
**Dependencies:** None
**Files:** `AnimationPlayer.java`, `IsoGameCharacter.java`

### Group 16: Cutaway (`opt.cutaway`)
**Opt:** 16 (CUTAWAY)
**What it does:** Position-based cutaway result caching.
**Savings:** ~0.5ms
**Dependencies:** None
**Files:** `IsoCell.java`, `IsoGridSquare.java`, `OptiZombCutaway.java`

### Group 17: Fog (`opt.fog`)
**Opt:** 17 (FOG)
**What it does:** Distance-based fog row skip.
**Dependencies:** None
**Files:** `ImprovedFog.java`, `OptiZombFog.java`

### Group 18: StartFrame (`opt.startframe`)
**Opt:** 18 (STARTFRAME)
**What it does:** StartFrame sub-phase profiling.
**Dependencies:** None
**Files:** `Core.java`, `GameWindow.java`, `OptiZombStartFrame.java`

### Group 19: Separate Throttle (`opt.separatethrottle`)
**Opt:** 19 (SEPARATE_THROTTLE)
**What it does:** Tightened separation distance thresholds.
**Dependencies:** None
**Files:** `IsoMovingObject.java`, `OptiZombSeparate.java`

### Group 20: AI Throttle (`opt.aithrottle`)
**Opt:** 20 (AI_THROTTLE)
**What it does:** Distance-based AI update throttle.
**Dependencies:** None
**Files:** `IsoGameCharacter.java`, `IsoWorld.java`, `IsoZombie.java`, `OptiZombAI.java`

### Branding (always enabled — not toggleable)
`MainScreenState.java` — "OptiZomb" label on main menu. Always active to identify the mod is installed.

---

## Dependency Graph

```
opt.branding        (always active, not toggleable)
opt.terrain         (independent)
opt.zoom            (independent)
opt.diagnostics     (independent)
opt.batchmerge      (independent)
opt.tileschars      (independent)
opt.blood           (independent)
opt.shadows         (independent)
opt.items           (independent)
opt.floorfbo        (independent)
opt.floorpipeline   (independent)
opt.scenecull       (independent)
opt.bonelod         (independent)
opt.cutaway         (independent)
opt.fog             (independent)
opt.startframe      (independent)
opt.separatethrottle (independent)
opt.aithrottle      (independent)
opt.glfix           (independent)
opt.glchars         → requires opt.glfix
opt.bonetbo         → requires opt.glchars
```

Only one dependency chain: `opt.glfix → opt.glchars → opt.bonetbo`

Dependencies are enforced in code via `&&` in the flag definition:
```java
public static final boolean GL_CHARS = resolve("opt.glchars", true) && GL_FIX;
public static final boolean BONE_TBO = resolve("opt.bonetbo", true) && GL_CHARS;
```

17 of 20 toggles are fully independent. The GL rendering chain has a natural dependency — you wouldn't want bind caching without the glGetError fix, and you wouldn't want TBO without the shader infrastructure.

**Installer enforces:** Enabling `opt.glchars` auto-enables `opt.glfix`. Enabling `opt.bonetbo` auto-enables `opt.glchars` + `opt.glfix`. Disabling `opt.glfix` auto-disables `opt.glchars` + `opt.bonetbo`.

---

## Installer Implementation

### GUI (`OptiZombInstaller.java`)
Swing-based GUI with:
- Auto-detection of PZ install directory (Linux/Windows/macOS Steam paths + `libraryfolders.vdf` parsing)
- Browse button for manual selection
- "What This Changes" summary panel listing all groups
- Collapsible "Advanced Options" panel with per-group checkboxes (13 groups with star ratings and savings estimates)
- Dependency auto-check/uncheck logic in `onToggleChanged()`
- Install / Uninstall / Exit buttons
- Scrollable log output area

### Install Process (`doInstall()`)
1. **Load patch resources** from installer JAR — separates `.class.patch` (bsdiff), `.class` (new), shaders, and default config.
2. **Apply bsdiff patches** — reads vanilla `.class` from user's installation (loose files or JAR), applies `Patch.patch()`, produces patched bytes in memory.
3. **Package `optizomb.jar`** — writes all patched + new classes into a single JAR in `projectzomboid/`.
4. **Patch classpath JSON** — adds `"optizomb.jar"` as first entry in `ProjectZomboid64.json` and `ProjectZomboid32.json`. Java classloader loads our classes before vanilla.
5. **Install shaders** — copies to `projectzomboid/media/shaders/`, backs up vanilla originals to `.optizomb-backup/`.
6. **Write config** — writes `optizomb.properties` to `installDir` (ProjectZomboid/). In advanced mode, reflects checkbox selections; in simple mode, uses default (all enabled).

### Uninstall Process (`onUninstall()`)
1. Remove `optizomb.jar`
2. Remove `"optizomb.jar"` line from classpath JSON
3. Remove `optizomb.properties`
4. Restore vanilla shaders from `.optizomb-backup/` (or remove new-only shaders)
5. Clean up `.optizomb-backup/` directory

### Install Detection
- **Loose-class install** (Linux): `projectzomboid/zombie/` directory exists, no `projectzomboid.jar`
- **JAR-based install** (Windows): `projectzomboid.jar` exists

---

## Config File Location

Priority order (checked in `OptiZombConfig` static initializer):
1. `{user.dir}/optizomb.properties` — inside `projectzomboid/` where PZ sets CWD
2. `{user.dir}/../optizomb.properties` — `ProjectZomboid/` parent directory
3. `{user.home}/.optizomb/config.properties` — user home fallback

The installer writes config to `installDir` (ProjectZomboid/), which is found via path #2.
The `install.sh` script writes to `projectzomboid/`, found via path #1.
Both work.

---

## Known Issues / Notes

- The installer writes config to `installDir` (ProjectZomboid/ parent). OptiZombConfig searches `{user.dir}/optizomb.properties` first, then `{user.dir}/../optizomb.properties`. Both paths work.
