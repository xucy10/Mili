<p align="center">
  <img src="public/image/Mili/mili-logo.png" alt="Mili Logo" width="600">
</p>

<h1 align="center">Mili</h1>

<p align="center">
  <strong>A high-performance Minecraft server core based on Folia, featuring Rust native acceleration and deep technical-play compatibility</strong>
</p>

<p align="center">
  <a href="./README.md">中文</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.1.2-green" alt="Minecraft 26.1.2">
  <img src="https://img.shields.io/badge/JDK-25+-orange" alt="JDK 25+">
  <img src="https://img.shields.io/badge/Rust-edition%202024-red" alt="Rust edition 2024">
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="GPL-3.0">
</p>

---

## Overview

Mili is a Minecraft server core built on the **Paper → Folia** fork chain, designed for **technical / survival-circuit servers**. On top of Folia's region-based multithreading model, it provides redstone compatibility fixes, vanilla behavior toggles, Rust native acceleration, and a complete Carpet/Leaves protocol compatibility layer.

### Fork Chain

```
Minecraft (vanilla)
  └── Paper (server framework)
        └── Folia (region multithreading)
              └── Mili (this project)
```

> Mili was originally derived from Lophine/Luminol but has since migrated to be directly based on Folia, with Luminol's optimization source code merged in.
> Luminol, Lophine, and Hyacinthusweight have all been deleted. Mili is the independent continuation of this technology line.

---

## Core Features

### Rust Native Acceleration (`mili-rust`)

Bridges Rust-compiled native libraries (`mili_optimizer.dll` / `.so` / `.dylib`) via JNI for zero-copy batch acceleration of high-frequency computation paths:

| Module | Function | Highlights |
|--------|----------|------------|
| `config.rs` | TOML config read/write | `toml_edit` preserves comments, JSON ↔ TOML conversion |
| `entity_cull.rs` | Entity frustum culling | 6-plane AABB frustum test, DirectByteBuffer zero-copy, Rayon parallel |
| `frustum.rs` | Frustum construction & testing | Build from camera params or projection matrix, sphere/AABB/point tests |
| `jni_bridge.rs` | JNI bridge layer | Batch-only, one JNI call processes all entities |

**Zero-copy design**: Java packs entity data into `DirectByteBuffer`; Rust reads memory directly via `GetDirectBufferAddress`, avoiding data copies across the JNI boundary. Only M JNI calls per tick (M = number of observers), not N×M (N = entity count).

**Safety design**: All JNI entry points wrapped with `catch_unwind(AssertUnwindSafe(...))` to prevent panics from crossing the boundary; `#[unsafe(no_mangle)]` compliant with edition 2024; `overflow-checks=true` prevents arithmetic overflow UB.

### Folia Stability Fixes

- **Region Balancer**: Shared thread pool + priority queue replacing Folia's per-region exclusive threads, dynamic load balancing
- **Region Load Monitor**: Lock-free sliding window for region tick time statistics
- **Adaptive TPS Manager**: Dynamically adjusts TPS based on real-time load
- **Cross-Region Helper**: Typed cross-region event queue (redstone signals, entity damage, block notifications, etc.)
- **RegionTaskIdRegistry**: Global UUID registry preventing cross-chunk task ID collisions that cause crashes
- **Global Entity Counter**: Aggregates mob counts by region, avoiding O(entities) scans
- **Thread safety hardening**: Project-wide `catch(Exception)` → `catch(Throwable)` fix, preventing OOM/StackOverflow Errors from silently killing scheduler threads

### Redstone & Technical Compatibility

Redstone/technical fixes ported from Leaves and adapted for Folia:

- **Update Suppression**: Catches `UpdateSuppressionException` to prevent server crashes, preserves dropped items, does not roll back placed blocks
- **Redstone ignore upwards update**: Restores 1.20.1/1.19 redstone dust/repeater/comparator upward update behavior
- **Instant Block Updater**: `InstantNeighborUpdater` replaces `CollectingNeighborUpdater`
- **Old block remove behavior**: Restores pre-1.21 `onRemove` semantics
- **Wool Hopper Counter**: Hopper counter via wool colors + `/counter` command

### Carpet / Protocol Compatibility

| Protocol | Description |
|----------|-------------|
| **Carpet** | 50+ Carpet/TIS/AMS rule mappings, TPS/mobcaps/counter HUD sync |
| **TISCM** | `tiscm:network/v1`, MSPT broadcast, handshake |
| **XaeroMap** | Xaero map channel support |
| **Jade** | Jade server data provider |
| **Syncmatica** | Litematic synchronization |
| **Servux** | Servux client service |
| **REI** | Roughly Enough Items server protocol |
| **BBOR** | Bounding Box Outline Reloaded |
| **AppleSkin** | Hunger/saturation sync |
| **Alternative Block Placement** | Accurate/Carpet/Litematica placement protocol |

### Fake Player / Bot System

Ported from Leaves and adapted for Folia. Supports creating/managing/removing fake players, persistent bots, inventory access, action execution (attack, break, fish, jump, move, etc.), with a complete Java event API.

### ReplayMod Photographer

Supports creating ReplayMod photographer entities for recording, with `Photographer` / `PhotographerManager` API.

---

## Quick Start

### Requirements

| Dependency | Version | Notes |
|------------|---------|-------|
| JDK | 25+ | Build toolchain (Mili 26.1.2 branch requires Java 25, not JDK 21) |
| Rust | stable (edition 2024) | Optional, for compiling native optimization library |
| Git | 2.x | Enable long path support on Windows |

### Build Steps

```bash
# 1. Clone repository
git clone https://github.com/xucy10/Mili.git
cd Mili

# 2. Enable long paths on Windows
git config --global core.longpaths true

# 3. Apply patches (required for first build)
./gradlew applyAllPatches --no-configuration-cache --no-build-cache

# 4. Inject Kotlin support
python scripts/inject_kotlin.py

# 5. Compile Rust native library (optional, falls back to pure Java if missing)
./gradlew :mili-rust:stageRustBinary

# 6. Build Paperclip JAR
./gradlew :mili-server:createMojmapPaperclipJar
```

Build artifacts in `mili-server/build/libs/`:
- `mili-paperclip-26.1.2-R0.1-SNAPSHOT.jar` — runnable Paperclip JAR
- `mili_optimizer.dll` / `.so` / `.dylib` — Rust native optimization library (packaged in JAR)

### Rust Standalone Build & Test

```bash
cd mili-rust/src/rust
cargo build --release    # Build
cargo clippy --release   # Lint check (0 warnings)
cargo test --release     # Run unit tests (28 tests)
```

---

## API Usage

### Gradle

```kotlin
repositories {
    maven {
        url = "https://repo.menthamc.org/repository/maven-public/"
    }
}

dependencies {
    compileOnly("fun.bm.mili:mili-api:26.1.2-R0.1-SNAPSHOT")
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>menthamc</id>
    <url>https://repo.menthamc.org/repository/maven-public/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>fun.bm.mili</groupId>
    <artifactId>mili-api</artifactId>
    <version>26.1.2-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

---

## Project Structure

```
Mili/
├── mili-api/                  # Mili API module
│   └── src/main/java/         #   Bot, Photographer, event API
├── mili-server/               # Mili server core
│   ├── minecraft-patches/     #   Patch files (121 features/ + resources/ + sources/)
│   └── src/main/
│       └── java/fun/bm/mili/  #   Java source
│           ├── bridge/        #     Chunk-region bridge
│           ├── carpet/        #     Carpet compatibility
│           ├── chunk/         #     Chunk system
│           ├── command/       #     Command system
│           ├── config/        #     Config modules
│           ├── metrics/       #     bStats metrics
│           ├── portal/        #     Portal management
│           ├── protocol/      #     Protocol compatibility
│           ├── rust/          #     Rust JNI Java-side utilities
│           ├── utils/         #     Utilities (region scheduling, network, memory, etc.)
│           │   └── concurrent/#     Concurrent data structures
│           └── villager/      #     Villager optimizer
├── mili-rust/                 # Rust native optimization module
│   ├── src/main/java/         #   JNI bridge Java side (RustBridge.java)
│   └── src/rust/src/          #   Rust source (edition 2024)
│       ├── config.rs          #   TOML config read/write
│       ├── entity_cull.rs     #   Entity frustum culling
│       ├── frustum.rs         #   Frustum construction & testing
│       ├── jni_bridge.rs      #   JNI exported functions
│       └── lib.rs             #   crate entry
├── docs/                      # Documentation
│   ├── WIKI.md                #   Wiki (full feature index, in Chinese)
│   ├── CONTRIBUTING.md        #   Contributing guide (Chinese)
│   └── carpet-compat-status.md#   Carpet rule compatibility status
├── build.gradle.kts           # Root build script
└── gradle.properties          # Version & upstream ref config
```

---

## Configuration

Mili provides two TOML config files:

| File | Package | Description |
|------|---------|-------------|
| `mili_config.toml` | `fun.bm.mili.config.modules` | Main config: game mechanics, experimental features, fix toggles |
| `mili_carpet_config.toml` | `fun.bm.mili.carpet.config.modules` | Carpet compatibility rule mappings |

Config categories:

| Category | Description | Representative modules |
|----------|-------------|------------------------|
| `function` | Game mechanics & utilities | `LanguageConfig`, `FakeplayerConfig`, `ContainerExpansionConfig` |
| `experiment` | Experimental performance/concurrency | `RegionBalancerConfig`, `CrossRegionHelperConfig` |
| `optimizations` | Performance optimizations | `NetworkOptimizerConfig`, `MmapRegionStorageConfig` |
| `fixes` | Crash fixes | `UpdateSuppressionCrashFixConfig` |
| `misc` | Miscellaneous | `AutoUpdateConfig`, `BStatsConfig` |
| `carpet` | Carpet rule mappings | `CoreConfig`, `GeneralCompatConfig` |

---

## Patch Workflow

Mili uses the **Hyacinthusweight** (paperweight-based) patch system with 121 feature patches:

1. Modify code in `mili-server/src/minecraft/` or `mili-api/`
2. Commit changes: `git commit -m "description"`
3. Rebuild patches: `./gradlew :mili-server:rebuildAllServerPatches`
4. Commit patch files and push

Files under `mili-server/src/minecraft/java/` are generated and will be overwritten by `applyAllPatches`. Changes must be made through patch files in `minecraft-patches/features/`.

See [Contributing Guide](docs/CONTRIBUTING_EN.md) for details.

---

## Contributing

Pull Requests and Issues are welcome! Please read:

- [Contributing Guide (EN)](docs/CONTRIBUTING_EN.md) | [贡献指南（中文）](docs/CONTRIBUTING.md)
- When reporting issues, include full logs, environment info, and reproduction steps

---

## Related Links

| Project | Link |
|---------|------|
| Folia (direct upstream) | https://github.com/PaperMC/Folia |
| Paper | https://github.com/PaperMC/Paper |
| LeavesMC (major feature source) | https://github.com/LeavesMC/Leaves |

---

## Community

[Discord](https://discord.com/invite/BSa67dbvVf)

## Thanks

Thanks to all contributors and sponsors for their continued support. If this project helps you, please consider starring the repository.

## License

This project is licensed under GPL-3.0.
