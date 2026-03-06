# Architecture Reference

## Game Loop

```
GameWindow.frameStep()
├── logic() → IngameState.updateInternal()
│   ├── IsoWorld.update()
│   │   ├── WorldSimulation.update()
│   │   ├── IsoCell.update() → MovingObjectUpdateScheduler
│   │   │   ├── Zombie update (parallel via PZTaskScheduler)
│   │   │   ├── Zombie separate (parallel, with distance skip/throttle)
│   │   │   └── Zombie postupdate: collision (seq) + animation (parallel)
│   │   ├── CollisionManager.ResolveContacts() (actual collision only)
│   │   ├── MovingObjectUpdateScheduler.postupdate() (animation)
│   │   └── Sound managers (footsteps, thumps, vocals)
│   ├── UpdateStuff() (fire, rain, pathfinding, population)
│   └── LuaEventManager.triggerEvent("OnTick")
├── renderInternal()
│   ├── LightingJNI (non-threaded path)
│   ├── IsoObjectPicker.StartRender()
│   └── IngameState.render()
│       ├── renderframe() per player
│       │   ├── ModelOutlines.startFrameMain()
│       │   ├── IsoWorld.render()
│       │   │   ├── sceneCullZombies()
│       │   │   ├── Parallel bone precomputation
│       │   │   ├── IsoCell.render() (tile/sprite drawing)
│       │   │   └── PolygonalMap2, WeatherFx, SkyBox, etc.
│       │   ├── ModelOutlines.endFrameMain()
│       │   └── Core.EndFrame()
│       ├── renderframetext() per player
│       │   └── Core.EndFrameText()
│       └── renderframeui()
│           ├── UIManager.render()
│           └── Core.EndFrameUI()
├── Core.DoFrameReady()
└── LightingThread.update()
```

## Key Files — Lite Tier

### Modified Vanilla Files (35 patches)

| File | Optimization |
|------|-------------|
| `PZGLUtil.java` | Opt 1: GL_FIX |
| `Model.java` | Opt 1-2: GL_FIX, GL_CHARS |
| `Shader.java` | Opt 1-3: GL_FIX, GL_CHARS, BONE_TBO |
| `ModelSlotRenderData.java` | Opt 1-3: GL_FIX, GL_CHARS, BONE_TBO |
| `VertexBufferObject.java` | Opt 2: GL_CHARS |
| `ModelInstanceRenderData.java` | Opt 3: BONE_TBO |
| `IsoObject.java` | Opt 4, 6: TILES_CHARS, FLOOR_PIPELINE |
| `IsoGridSquare.java` | Opt 4, 6-7, 9, 16: TILES_CHARS, FLOOR_PIPELINE, FLOOR_FBO, SHADOWS, CUTAWAY |
| `SpriteRenderer.java` | Opt 5: BATCH_MERGE |
| `IsoCell.java` | Opt 3, 6-7, 9-10: BONE_TBO, FLOOR_PIPELINE, FLOOR_FBO, SHADOWS, ITEMS |
| `IsoMarkers.java` | Opt 6: FLOOR_PIPELINE |
| `IsoDeadBody.java` | Opt 7: FLOOR_FBO |
| `IsoChunkMap.java` | Opt 8: BLOOD |
| `IsoFloorBloodSplat.java` | Opt 8: BLOOD |
| `IsoGameCharacter.java` | Opt 9, 15, 20: SHADOWS, BONE_LOD, AI_THROTTLE |
| `BaseVehicle.java` | Opt 9: SHADOWS |
| `ItemContainer.java` | Opt 10: ITEMS |
| `InventoryItem.java` | Opt 10: ITEMS |
| `Food.java` | Opt 10: ITEMS |
| `AlarmClock.java` | Opt 10: ITEMS |
| `AlarmClockClothing.java` | Opt 10: ITEMS |
| `Clothing.java` | Opt 10: ITEMS |
| `DrainableComboItem.java` | Opt 10: ITEMS |
| `IngameState.java` | Opt 11: DIAGNOSTICS |
| `GameWindow.java` | Opt 11, 18: DIAGNOSTICS, STARTFRAME |
| `HeightTerrain.java` | Opt 12: TERRAIN |
| `MultiTextureFBO2.java` | Opt 13: ZOOM |
| `IsoWorld.java` | Opt 14, 20: SCENE_CULL, AI_THROTTLE + zombie render cap |
| `IsoZombie.java` | Opt 14, 20: SCENE_CULL, AI_THROTTLE |
| `AnimationPlayer.java` | Opt 15: BONE_LOD |
| `ImprovedFog.java` | Opt 17: FOG |
| `Core.java` | Opt 18: STARTFRAME |
| `IsoMovingObject.java` | Opt 19: SEPARATE_THROTTLE |
| `MainScreenState.java` | Branding |

### New OptiZomb Files (23)

| File | Purpose |
|------|---------|
| `zombie/optizomb/OptiZombConfig.java` | Feature flags + config loading |
| `zombie/optizomb/OptiZombGLFix.java` | Opt 1: GL error bypass + vehicle texture |
| `zombie/optizomb/OptiZombGLChars.java` | Opt 2: Character rendering GL state |
| `zombie/optizomb/OptiZombBoneTBO.java` | Opt 3: SSBO bone matrix upload |
| `zombie/optizomb/OptiZombBoneTBOLog.java` | Opt 3: SSBO diagnostics |
| `zombie/optizomb/OptiZombTilesChars.java` | Opt 4: Render flags + occlusion cache |
| `zombie/optizomb/OptiZombBatchMerge.java` | Opt 5: Batch texture ID merging |
| `zombie/optizomb/OptiZombFloorPipeline.java` | Opt 6: Floor sub-optimizations |
| `zombie/optizomb/OptiZombFloorFBO.java` | Opt 7: Floor FBO diagnostics |
| `zombie/iso/FloorFBOCache.java` | Opt 7: FBO lifecycle + dirty detection |
| `zombie/optizomb/OptiZombBlood.java` | Opt 8: Blood splat cull + caching |
| `zombie/optizomb/OptiZombShadows.java` | Opt 9: Deferred shadow rendering |
| `zombie/optizomb/OptiZombItems.java` | Opt 10: Item container optimization |
| `zombie/optizomb/OptiZombPerfLog.java` | Opt 11: CPU performance monitoring |
| `zombie/optizomb/OptiZombGPUProfiler.java` | Opt 11: GPU render phase profiler |
| `zombie/optizomb/OptiZombTerrain.java` | Opt 12: GL attrib narrowing |
| `zombie/optizomb/OptiZombZoom.java` | Opt 13: Deferred grid recalc |
| `zombie/optizomb/OptiZombSceneCull.java` | Opt 14: LOD + bone skip |
| `zombie/optizomb/OptiZombCutaway.java` | Opt 16: Cutaway result caching |
| `zombie/optizomb/OptiZombFog.java` | Opt 17: Fog row skip |
| `zombie/optizomb/OptiZombStartFrame.java` | Opt 18: StartFrame profiling |
| `zombie/optizomb/OptiZombSeparate.java` | Opt 19: Separation throttle |
| `zombie/optizomb/OptiZombAI.java` | Opt 20: AI update throttle |
