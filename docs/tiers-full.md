# OptiZomb Full — Multithreaded Simulation

All Lite changes plus multithreaded zombie processing and AI improvements.
**Both client and server must run the same version.**

**Patch counts:** 42 modified + 6 new files

---

## Implemented

### Parallel Zombie Processing
- `PZTaskScheduler.java` — Platform thread pool with `parallelForEach()` and frame sync
- `MovingObjectUpdateSchedulerUpdateBucket.java` — Parallel update/separate/postupdate dispatch
- `MovingObjectUpdateScheduler.java` — Simplified to single fullSimulation bucket
- `GameTime.java` — `PerObjectMultiplier` always 1.0
- Players/vehicles always run on main thread for safety

### Race Condition Fixes (18 files)

| File | Fix |
|------|-----|
| `WalkTowardState.java` | ThreadLocal vectors + position write fix |
| `LungeState.java` | ThreadLocal temp vector |
| `LungeNetworkState.java` | ThreadLocal temp vectors |
| `CrawlingZombieTurnState.java` | ThreadLocal temp vectors |
| `ZombieOnGroundState.java` | ThreadLocal temp vectors + local variable scoping |
| `SwipeStatePlayer.java` | ThreadLocal for HitList, vectors, movingStatic |
| `IsoGameCharacter.java` | ThreadLocal for tempo, tempo2, tempVector2 (70+ changes) |
| `IsoPlayer.java` | ThreadLocal for tempo, tempVector2 |
| `IsoZombie.java` | ThreadLocal-aware access in targeting/rendering |
| `ParameterShoeType.java` | Static `tempItemVisuals` → ThreadLocal |
| `PolygonalMap2.java` | PathFindRequest pool: ArrayDeque → ConcurrentLinkedDeque |
| `Rand.java` | Static CellularAutomatonRNG → ThreadLocal |
| `AnimationTrack.java` | Static scratch buffers → ThreadLocal; stripped 4 static PerformanceProfileProbe |
| `ModelInstance.java` | `m_lock` string literal → `new Object()` (vanilla bug: interned = global lock) |
| `SharedSkeleAnimationTrack.java` | Stateless `getBoneMatrixAtTime()` |
| `SharedSkeleAnimationRepository.java` | HashMap → ConcurrentHashMap + `putIfAbsent()` |
| `ZombieGroupManager.java` | Synchronized `update()`/`findNearestGroup()` |
| `NetworkZombieSimulator.java` | `ExtraSendQueue`: ArrayDeque → ConcurrentLinkedDeque |

### Thread-Safe Audio (FMOD)
- `CharacterSoundEmitter.java` — Play methods defer to `ConcurrentLinkedQueue<DeferredSound>`, drained on main thread. Stop/query/parameter methods no-op on workers.

### Animation Pipeline Overhaul
- `CollisionManager.java` — Extracted `postupdate()` into own phase
- `AnimationPlayer.java` — Double-buffer `frontModelTransforms[]` for lock-free render reads
- `ModelManager.java` — `publishTransforms()` called inside lock
- `ModelInstance.java` — Per-instance lock, removed dead `instanceSkip` code
- `AnimatedModel.java` — `publishTransforms()` for character creation screen

### Elimination of Bucket Frame-Skipping
- Removed distance-based update buckets (frameMod=2/4/8/16) that caused zombie "teleporting"
- All objects update every frame; parallel dispatch absorbs extra load

### Improved Separation Physics
- `IsoMovingObject.java` — Overlap-scaled push (0.1→0.4), random direction for stacked zombies, 0.15 tile/frame clamp

### Head-On Zombie Avoidance
- `WalkTowardState.java` — Detects head-on zombie pairs, rotates steering ±45° using ID parity

### Fence Climb Loop Fix
- `ClimbOverFenceState.java` — Non-chasing zombies path 2 tiles away after climbing

### Horde Spawner Grid Distribution
- `CreateHordeCommand.java` + `CreateHorde2Command.java` — Grid-based spawning instead of random scatter

### Lua: CopyOnWriteArrayList Exposure
- `LuaManager.java` — Thread-safe collections available to Lua scripts/mods

---

## Planned

### Lazy ProcessItems, Server (extends Opt 10 ITEMS)
Same filtering as client-side ITEMS optimization applied server-side. Will reduce dedicated server CPU load with many loaded chunks.

### Collision Parallelism
Parallel position commit (nx→x per zombie), deferred sequential tile-list update. Estimated savings: 0.5-1.0ms.

---

## Full Tier New Files

| File | Purpose |
|------|---------|
| `zombie/threading/PZTaskScheduler.java` | Platform thread pool |
| `zombie/threading/ThreadLocalCache.java` | ThreadLocal management |
| `zombie/iso/RaycastVision.java` | Raycast vision engine (abandoned) |
| `zombie/iso/RaycastVisionRenderer.java` | Vision stencil renderer (abandoned) |
| `zombie/iso/BuildingShadowRenderer.java` | Building shadow renderer (abandoned) |
| `zombie/commands/serverCommands/SpawnStressCommand.java` | Debug stress test command |
