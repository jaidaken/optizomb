# OptiZomb

Performance optimization mod for Project Zomboid.

## Quick Start

```bash
# 1. Decompile vanilla (first time + after game updates)
bash scripts/decompile.sh

# 2. Apply patches to create source
bash scripts/apply-patches.sh lite

# 3. Build
bash scripts/build.sh

# 4. Build installer
bash scripts/build-installer.sh

# 5. Install
java -jar build/OptiZomb-Lite-Installer.jar
```

## Tiers

- **Lite** — Client-only rendering optimizations. Safe on vanilla servers.
- **Full** — Lite + parallel zombie simulation. Requires client+server match.

## Documentation

See `docs/` for detailed optimization notes, benchmarks, and architecture.
