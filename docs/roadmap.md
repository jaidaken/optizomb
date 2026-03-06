# OptiZomb — Roadmap

Remaining optimization opportunities, ranked by estimated savings at ~1046 zombies.

---

## Viable Optimization Candidates

| # | Target | Current | Approach | Est. Savings | Effort | Tier |
|---|--------|---------|----------|--------------|--------|------|
| 1 | **Character draw batching** | 5.4ms CPU (1.0ms GPU) | Multi-draw indirect — batch same-model zombies. ~857 draws → ~10-20. | **2–3ms** | Med-High | Lite |
| 2 | **Floor tile CPU packing** | 6.2ms CPU (0.72ms GPU) | Pre-classify tiles by texture during gridStacks. | **2–3ms** | Medium | Lite |
| 3 | **Collision parallelism** | 1.5ms | Parallel position commit, deferred tile-list update. | **0.5–1.0ms** | Medium | Full |
| 4 | **Lazy ProcessItems (server)** | Unknown | Same filtering as client Opt 10, applied server-side. | **Variable** | Low | Full |

---

## Completed (This Pass)

These were on previous roadmaps and have been implemented:

- Cutaway caching → Opt 16 (CUTAWAY)
- sceneCull incremental sort → Opt 14 (SCENE_CULL)
- Bone LOD increase → Opt 14 (SCENE_CULL, LOD tier 3)
- Separate throttle increase → Opt 19 (SEPARATE_THROTTLE)
- AI throttle increase → Opt 20 (AI_THROTTLE)
- StartFrame investigation → Opt 18 (STARTFRAME, found not a bottleneck)
- Fog row skip → Opt 17 (FOG)
- minusFloor occlusion cache → Opt 4 (TILES_CHARS)

## Rejected / Not Viable

- **Sprite instancing** — PZ painter's algorithm (`GL_ALWAYS`, depth off) breaks draw order
- **Shadow instancing** — Same painter's algorithm incompatibility
- **gridStacks cacheLightInfo dirty-flag** — `cacheLightInfo()` is a trivial pointer copy, JNI already caches
- **GPU Instanced Floor Tiles** — Same painter's algorithm incompatibility

---

## Remaining Hotspot Breakdown (~1046 zombies, ~40.9ms/frame)

```
render 28.9ms ████████████████████████████░░ 71%
  characters   8.9  █████████░░░░░░░░░░░░░░░░░░░░ 22%  ← largest remaining target
  floor        6.2  ██████░░░░░░░░░░░░░░░░░░░░░░░ 15%
  gridStacks   4.8  █████░░░░░░░░░░░░░░░░░░░░░░░░ 12%
  bones        2.4  ██░░░░░░░░░░░░░░░░░░░░░░░░░░░  6%
  startFrame   2.0  ██░░░░░░░░░░░░░░░░░░░░░░░░░░░  5%
  env          1.4  █░░░░░░░░░░░░░░░░░░░░░░░░░░░░  3%
  sceneCull    1.3  █░░░░░░░░░░░░░░░░░░░░░░░░░░░░  3%
  renUI        1.0  █░░░░░░░░░░░░░░░░░░░░░░░░░░░░  2%

logic 11.9ms  ████████████░░░░░░░░░░░░░░░░░░ 29%
  processObj   4.9  █████░░░░░░░░░░░░░░░░░░░░░░░░ 12%
  animation    3.9  ████░░░░░░░░░░░░░░░░░░░░░░░░░ 10%
  separate     2.7  ███░░░░░░░░░░░░░░░░░░░░░░░░░░  7%
  collision    1.5  ██░░░░░░░░░░░░░░░░░░░░░░░░░░░  4%
```

**Total remaining viable opportunity: ~5–9ms** → would bring frame time from ~40.9ms to ~32-36ms (~28-31 FPS at 1046 zombies).
