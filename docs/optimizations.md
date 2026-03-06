# OptiZomb Lite — Optimizations Reference

All optimizations numbered 1–20 matching `OptiZombConfig.java` flag order.
Each is independently toggleable via `optizomb.properties`. All are **Lite tier** (client-only, safe for vanilla multiplayer servers).

**Dependency chain:** GL_FIX → GL_CHARS → BONE_TBO (all others independent)

Old-to-new number mapping at end of document.

---

## Opt 1: GL_FIX (`opt.glfix`) [DONE]

**What:** Removes `glGetError()` GPU pipeline stalls and redundant GL calls.

- `PZGLUtil.java` — 2 one-line guards: skip `glGetError()` in `checkGLErrorThrow()` and `checkGLError()`
- `Model.java` — 1 redirect: `OptiZombGLFix.setupVehicleTextures()` replaces 8 redundant `glTexEnvi` calls with 1
- `IsoWorld.java` — Zombie render cap raised 510 → 4096 (8x)

**New file:** `zombie/optizomb/OptiZombGLFix.java` — vehicle texture optimization + diagnostic skip counters

**Measured:** 1.36M GPU pipeline syncs eliminated per 5 seconds (7,356/frame at 37fps). Single largest optimization — eliminated ~60ms of GPU stalls per frame.

---

## Opt 2: GL_CHARS (`opt.glchars`) [DONE]

**Requires:** GL_FIX

**What:** Character rendering pipeline optimization — shader bind caching, trig precompute, VBO/EBO bind caching, push/pop attrib elimination, direct uniform calls, texture caching.

- `Shader.java` — Light ID array caching, precomputed cos/sin per zombie, light early-out, direct `glUniform*ARB()` calls bypassing HashMap, texture bind caching via `startCharacter()`, push/pop attrib narrowing (0xFFFFF → 0x100)
- `VertexBufferObject.java` — VBO/EBO bind caching + bone attrib tracking + `restoreClientState()` replaces `glPopClientAttrib`
- `ModelSlotRenderData.java` — One-line hooks to `OptiZombGLChars` methods
- `Model.java` — Redundant GL call removal, shader `StartIfNeeded()`

**New file:** `zombie/optizomb/OptiZombGLChars.java` — GL state management + diagnostic counters

**Measured (5s interval, ~300 zombies):** 382K setTexture bypasses, 382K uniform bypasses, 76K trig precomputations (760K Math.cos/sin eliminated), 1.9M array-indexed light lookups replacing if-else chains.

---

## Opt 3: BONE_TBO (`opt.bonetbo`) [DONE]

**Requires:** GL_CHARS

**What:** Uploads all zombie bone matrices to GPU via SSBO instead of per-zombie `glUniformMatrix4fv` uniform calls. Single buffer per frame.

- `Shader.java` — SSBO uniforms (`boneBaseIndex`, `boneTBO`)
- `ModelSlotRenderData.java` — `uploadAndBind()` call
- `ModelInstanceRenderData.java` — `boneTexelOffset` field
- `IsoCell.java` — `beginFrame()` call

**New files:** `zombie/optizomb/OptiZombBoneTBO.java` + `OptiZombBoneTBOLog.java`

**Shaders modified:** `basicEffect.vert` (GLSL 130 + texelFetch), `basicEffect_tbo.vert/frag`, `basicEffect_tbo_static.vert`

**Fallback:** Outline/wireframe shaders auto-detect missing `boneTBO` uniform → old `MatrixPalette[60]` path.

**Measured:** 264K SSBO appends/5s, 11.9M bones uploaded/5s (~145 MB/s), zero fallbacks.

---

## Opt 4: TILES_CHARS (`opt.tileschars`) [DONE]

**What:** Cached per-object render flag bitmask replaces ~15 per-object `instanceof`/`getProperties().Is()` hash lookups. Frame-stamp sort dedup. GL call dedup in `renderDeferredCharacters()`. Per-square per-frame occlusion cache.

- `IsoObject.java` — `short renderFlags` (10 bits), `renderFlagsDirty` lazy-init, computed in `setSprite()`/constructors/`load()`
- `IsoGridSquare.java` — `movingObjectsSortFrame` for sort dedup, duplicate `enableAlphaTest()`/`glAlphaFunc()` removed, occlusion cache

**New file:** `zombie/optizomb/OptiZombTilesChars.java`

**Measured:** renderFlags 100% cache hit rate (~418K cached lookups vs ~740 recomputes per 5s). **tilesChars 7.2ms → 2.9ms (−60%).**

---

## Opt 5: BATCH_MERGE (`opt.batchmerge`) [DONE]

**What:** Compares `TextureID` instead of `Texture` object identity in `SpriteRenderer.isStateChanged()`. Sprites from the same GL texture atlas merge into one batch.

- `SpriteRenderer.java` — `isSameGLTexture()` helper, `TextureID` reference comparison

**New file:** `zombie/optizomb/OptiZombBatchMerge.java`

**Measured:** ~32% of texture state changes eliminated (455K fewer glBindTexture calls per 5s). **Batches −15%.**

---

## Opt 6: FLOOR_PIPELINE (`opt.floorpipeline`) [DONE]

**What:** 8 sub-optimizations for floor rendering CPU path: parameter hoisting, building occlusion cache, profiler strip, bucket bitmask, shader hoisting, uniform caching, typed FloorShaper, lazy config.

- `IsoCell.java` — Hoisted `StartShader`/`EndShader` per z-layer, `s_floorLayerLoc` cached uniform
- `IsoGridSquare.java` — `renderFloorInternal()` accepts hoisted params, cached `CanBuildingSquareOccludePlayer`, `staticBucketBits` byte bitmask
- `IsoObject.java` — `renderFloorTile()` accepts `FloorShaper` directly (eliminates 2× `tryCastTo()` per tile)
- `IsoMarkers.java` — Bucket bit mutation hooks

**New file:** `zombie/optizomb/OptiZombFloorPipeline.java`

**Measured:** Only 54 bucket recomputes out of 514K squares processed. **floor 8.0ms → 6.1ms (−24%).**

---

## Opt 7: FLOOR_FBO (`opt.floorfbo`) [DONE]

**What:** Per-layer FBO caching for floor rendering. Captures floor tiles into FBO texture, blits from cache when floor hasn't changed (stationary camera, no lighting/content changes).

- `IsoCell.java` — FBO dirty check before layer loop, clean/dirty branching
- `IsoDeadBody.java` — `staticBucketDirty = true` after corpse creation
- `IsoGridSquare.java` — Public `staticBucketDirty`

**New files:** `zombie/iso/FloorFBOCache.java` (FBO lifecycle, capture/blit, `glBlendFuncSeparate` alpha fix, dirty detection), `zombie/optizomb/OptiZombFloorFBO.java` (diagnostics)

**Measured:** 95-100% cache hit when stationary. **floor 0.6→0.2ms (67%), batches −31%.**

---

## Opt 8: BLOOD (`opt.blood`) [DONE]

**What:** Blood splat rendering optimization — offscreen culling, tile light cache, per-splat color hash cache, render call chain bypass.

- `IsoChunkMap.java` — 3-line hook: `if (BLOOD) { renderOptimized() } else { vanilla }`
- `IsoFloorBloodSplat.java` — 3 cached color fields (`cachedR/G/B`, NaN sentinel)

**New file:** `zombie/optizomb/OptiZombBlood.java` (4 phases + diagnostics)

**Measured:** 48K splats culled vs 0 rendered (100% culled when offscreen), color cache 98% hit rate. **blood 1.2ms → 0.4ms (−67%).**

---

## Opt 9: SHADOWS (`opt.shadows`) [DONE]

**What:** Deferred shadow rendering — collects all shadow quads into pooled buffer, sorts by texture ID, flushes contiguously. Shadow LOD for distant zombies (rank 201+) uses fixed dimensions instead of bone-based computation.

- `IsoGridSquare.java` — Deferred shadow hook
- `IsoGameCharacter.java` — LOD early-out (`lightUpdateInterval >= 4`)
- `IsoDeadBody.java` — Deferred path
- `BaseVehicle.java` — Deferred path

**New file:** `zombie/optizomb/OptiZombShadows.java` (DeferredShadow pool, texture-ID sort, flush, diagnostics)

**Measured:** ~78K shadows consolidated from individual draw calls into ~123 texture-sorted batches — **639x reduction** in batch breaks.

---

## Opt 10: ITEMS (`opt.items`) [DONE]

**What:** Item container optimization — HashSet for ProcessItems dedup, type-indexed lookups, skip set for FindAndReturn, lazy ProcessItems filtering via `needsProcessing()` overrides.

- `IsoCell.java` — ProcessItems HashSet companion
- `ItemContainer.java` — `typeIndex` HashMap, `FindAll()`/`getCountType()`/`getFirstType()` rewritten
- `InventoryItem.java` — `needsProcessing()` virtual method
- `Food.java`, `AlarmClock.java`, `AlarmClockClothing.java`, `Clothing.java`, `DrainableComboItem.java` — `needsProcessing()` overrides

**New file:** `zombie/optizomb/OptiZombItems.java`

**Measured:** 97-98.6% of processItems calls filtered out early. Chunk load dedup O(n²) → O(n), type lookup O(n) → O(1).

---

## Opt 11: DIAGNOSTICS (`opt.diagnostics`) [DONE]

**What:** Comprehensive per-frame timing infrastructure — ZombPerf CPU timers (16 log line categories, 40+ timer fields) and GPUProfiler (32 render phases).

- `IngameState.java` — Render sub-phase instrumentation
- `GameWindow.java` — Frame-level instrumentation

**New files:** `zombie/optizomb/OptiZombPerfLog.java` (thread-safe atomic accumulators, `[ZombPerf]` output every 5s), `zombie/optizomb/OptiZombGPUProfiler.java` (CPU-side nanoTime render profiler, `[GPUPerf]` output every 5s)

**Overhead:** ~0.1ms from timer reads.

---

## Opt 12: TERRAIN (`opt.terrain`) [DONE]

**What:** Narrows `glPushClientAttrib(-1)` → `glPushClientAttrib(0x2)` and `glPushAttrib(0xFFFFF)` → `glPushAttrib(0x46108)` in `HeightTerrain.render()`. Eliminates 5 redundant GL calls. Removes `glBlendFunc(770, 771)` that causes "puddle diamond" visual artifact.

- `HeightTerrain.java` — GL attrib narrowing + redundant call removal

**New file:** `zombie/optizomb/OptiZombTerrain.java`

---

## Opt 13: ZOOM (`opt.zoom`) [DONE]

**What:** Defers `dirtyRecalcGridStackTime` from every frame during zoom transition to only when zoom reaches target. Reduces manual zoom speed from 5.0 to 4.0.

- `MultiTextureFBO2.java` — Zoom speed + deferred grid recalc

**New file:** `zombie/optizomb/OptiZombZoom.java`

**Measured:** 96% of grid stack recalculations eliminated during zoom transitions.

---

## Opt 14: SCENE_CULL (`opt.scenecull`) [DONE]

**What:** Pre-computed `cachedCullScore` in O(n) pass, sort by cached float. Eliminates O(n log n) redundant score computations. At 1000 zombies: ~40,000 score calls → ~1,000. Also assigns LOD tiers: rank 501+ get `interval=8`, `boneLOD=2` (core skeleton only).

- `IsoWorld.java` — Calls `OptiZombSceneCull.sortByCachedScore()`, falls back to vanilla `CompScoreToPlayer` when disabled
- `IsoZombie.java` — `cachedCullScore` field

**New file:** `zombie/optizomb/OptiZombSceneCull.java` (LOD tier assignment, bone skip classification)

**Measured:** 75% of zombies in LOD Tier 2, producing ~90K bone skips per 5 seconds.

---

## Opt 15: BONE_LOD (`opt.bonelod`) [DONE]

**What:** Skip detail bone transforms (fingers, toes, nubs) for distant zombies. ~40% of bones skippable at distance. ConcurrentHashMap cache per SkinningData for bone classification.

- `AnimationPlayer.java` — Bone LOD skip in `updateMultiTrackBoneTransforms()`
- `IsoGameCharacter.java` — `lightUpdateInterval` for shadow LOD

**Measured:** ~90K bone skips per 5 seconds at ~500+ zombies.

---

## Opt 16: CUTAWAY (`opt.cutaway`) [DONE]

**What:** Caches `IsCutawaySquare()` results per-square using player tile position as cache key. Only recomputes when player moves to a new tile.

- `IsoCell.java` — Cutaway loop hook
- `IsoGridSquare.java` — 3 cache fields + `discard()` reset

**New file:** `zombie/optizomb/OptiZombCutaway.java`

**Measured:** 95-100% cache hit rate. When standing still, 100% of cutaway computations eliminated. During active walking, ~97% cached.

---

## Opt 17: FOG (`opt.fog`) [DONE]

**What:** Rows more than 15 tiles from player skip every other row of fog rendering. Skipped rows doubled in height (96px → 192px) to fill gaps.

- `ImprovedFog.java` — Distance-based row skip in `renderRowsBehind()`

**New file:** `zombie/optizomb/OptiZombFog.java`

**Measured:** ~25% of fog rows eliminated, ~650 ChunkMap grid lookups saved per frame.

---

## Opt 18: STARTFRAME (`opt.startframe`) [DONE]

**What:** Instruments `Core.StartFrame()` with sub-phase nanoTime timers: `offscreenBuf`, `prePop`, `initCam`, `glCommands`.

- `Core.java` — Sub-phase timer hooks
- `GameWindow.java` — Timer integration

**New file:** `zombie/optizomb/OptiZombStartFrame.java`

**Finding:** `prePop` = 0.08-0.26ms. Total startFrame = 0.1-0.3ms. Not a bottleneck.

---

## Opt 19: SEPARATE_THROTTLE (`opt.separatethrottle`) [DONE]

**What:** Tightened separation distance thresholds for zombie push physics.

| Distance | Frequency |
|----------|-----------|
| >40 tiles | Skip entirely |
| 25–40 | Every 8th frame |
| 15–25 | Every 4th frame |
| 8–15 | Every 2nd frame |
| <8 | Every frame |

- `IsoMovingObject.java` — Distance-based throttle tiers

**New file:** `zombie/optizomb/OptiZombSeparate.java`

---

## Opt 20: AI_THROTTLE (`opt.aithrottle`) [DONE]

**What:** Distance-based AI throttle. Rank 201+ zombies get `aiUpdateInterval = 2` (think every other frame). Rank 401+ get interval 4. Only `RespondToSound()` and `updateSearchForCorpse()` are throttled; movement/state machine always runs.

- `IsoGameCharacter.java` — `aiUpdateInterval` field
- `IsoWorld.java` — LOD-based interval assignment in `sceneCullZombies()`
- `IsoZombie.java` — `_doFullAI` gate

**New file:** `zombie/optizomb/OptiZombAI.java`

---

## Additional (always active)

### Zombie Render Cap (`opt.zombiecap`)
Configurable zombie render limit. Vanilla = 510, OptiZomb default = 4096. Set in `IsoWorld.java`.

### Branding
`MainScreenState.java` — "OptiZomb" label on main menu. Always active, not toggleable.

---

## Old-to-New Number Mapping

| Old Opt # | New Opt # | Config Flag | Description |
|-----------|-----------|-------------|-------------|
| 9 | 1 | GL_FIX | glGetError removal + redundant GL calls |
| 6, 16, 16b, 27a | 2 | GL_CHARS | Character rendering pipeline |
| 27g | 3 | BONE_TBO | GPU bone matrix SSBO |
| 17a, 17d, 17e | 4 | TILES_CHARS | Render flags bitmask + sort dedup |
| 18a, 18b | 5 | BATCH_MERGE | Sprite batch texture merging |
| 20a-h, 27b | 6 | FLOOR_PIPELINE | Floor rendering CPU optimization |
| 21 | 7 | FLOOR_FBO | Floor FBO dirty-rect caching |
| 27d | 8 | BLOOD | Blood splat optimization |
| 27f | 9 | SHADOWS | Shadow LOD + deferred rendering |
| 28a-g | 10 | ITEMS | Item container optimization |
| 19 | 11 | DIAGNOSTICS | Performance diagnostics |
| — | 12 | TERRAIN | Terrain blend fix |
| — | 13 | ZOOM | Zoom FBO optimization |
| 34, 32 | 14 | SCENE_CULL | Cached cull sort + LOD tier 3 |
| 8 | 15 | BONE_LOD | Distance-based bone LOD |
| 35 | 16 | CUTAWAY | Cutaway result caching |
| 40 | 17 | FOG | Fog distance-based row skip |
| 39 | 18 | STARTFRAME | StartFrame sub-phase profiling |
| 33 | 19 | SEPARATE_THROTTLE | Separation throttle tightening |
| 30, 36 | 20 | AI_THROTTLE | AI update throttle |

---

## Rejected / Abandoned Optimizations

See [reverted.md](reverted.md) for full details.

- **Opt 29/37 (GPU Instanced Sprites)** — PZ uses painter's algorithm (`GL_ALWAYS`, depth mask off). Instancing breaks draw order.
- **Opt 22 (GPU Lighting Texture)** — Removed, replaced by FBO caching approach.
- **Opt 23 (Building-Edge Shadows)** — Multiply blend corrupts deferred sprite renderer.
- **Opt 25 (Raycast Vision)** — Isometric projection artifacts with stencil approach.
- **Opt 27e (Persistent VAO)** — VAO state encapsulation incompatible with ring buffer advancement.
- **Opt 38 (cacheLightInfo dirty-flag)** — `cacheLightInfo()` is a trivial pointer copy; JNI layer already caches.
- **Opt 5 (Light Throttling)** — Negligible savings (~0.1ms), visual cost not worth it.
- **Opt 26 (UI Rendering)** — Deferred: only 1.0ms at 1046 zombies.
