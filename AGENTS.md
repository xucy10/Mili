# AGENTS.md

This file provides guidance to Qoder (qoder.com) when working with code in this repository.

## Project Overview

Mili is a Minecraft server fork based on Lophine (which derives from Luminol → Folia), focused on enabling technical Minecraft (生电) features on Folia's regionized threading model.

**Key lineage**: Minecraft → Paper → Folia → Luminol → Lophine → **Mili**

## Build System

### Build Commands

```bash
# Apply all patches and build Paperclip JAR (development)
./gradlew applyAllPatches && ./gradlew createMojmapPaperclipJar

# Clean build
./gradlew clean applyAllPatches createMojmapPaperclipJar

# Build specific module
./gradlew :mili-server:build

# Apply patches only (no build)
./gradlew applyAllPatches

# Rebuild patches (when patches modified)
./gradlew rebuildPatches

# Run tests
./gradlew test
```

### Build Output

- Final JAR: `mili-server/build/libs/mili-*.jar`
- Paperclip JAR (distributable): `mili-server/build/libs/mili-*-mojmap-paperclip.jar`

### Patch System Architecture

The project uses hyacinthusweight (a LuminolMC fork of paperweight) with a layered patch system:

1. **minecraft-patches/** - Patches applied to vanilla Minecraft code (53 files in features/)
2. **luminol-patches/** - Patches applied to Luminol base (6 files)
3. **paper-patches/** - Patches applied to Paper/Folia base (7 files)

**Patch layers order**: Minecraft → Folia → Luminol → Lophine → Mili

Each patch file follows the git format with `diff --git` headers and index hashes.

## Code Architecture

### Project Structure

```
Mili/
├── mili-api/           # Public API (Bukkit/Spigot compatible)
│   ├── paper-patches/  # API-level patches
│   └── src/main/java/org/leavesmc/leaves/
├── mili-server/        # Server implementation
│   ├── minecraft-patches/features/  # Core Minecraft patches (0001-0053)
│   ├── luminol-patches/features/  # Luminol patches (0001-0006)
│   ├── paper-patches/features/    # Paper patches (0001-0007)
│   └── src/main/java/fun/bm/mili/ # Mili-specific code
│       ├── carpet/     # Carpet mod compatibility
│       ├── command/    # Custom commands (tpsall, rtp, etc.)
│       ├── config/     # Configuration system
│       ├── feature/    # Feature implementations
│       ├── perf/       # Performance monitoring
│       ├── protocol/   # Protocol implementations
│       └── utils/      # Utility classes
├── luminol-api/        # Upstream Luminol API (reference)
└── luminol-server/     # Upstream Luminol Server (reference)
```

### Key Mili Enhancements

#### Performance Monitoring (mili-perf)
- **MiliTpsAllCommand** - Enhanced `/tpsall` with task UID system (O(1) task indexing)
- **MiliPerfCommand** - Performance profiling commands
- **MiliAffinityAutoTuner** - CPU affinity auto-tuning
- **MiliRegionLoadMonitor** - Region load monitoring with deadlock detection

#### Technical Minecraft Features
- **Cross-region damage trace** - Async entity kill across regions
- **Global Entities Counter** - Cross-region entity tracking
- **Carpet features** - Extensive carpet mod compatibility (0044-Carpet-features.patch)
- **Chunk preloading** - On player tick and teleport (patches 0052-0053)
- **Hopper idle optimization** - Reduced overhead (patch 0046)

#### Folia Scheduler Enhancements
- **Task UID system** - Cancel tasks by ID in FoliaGlobalRegionScheduler (paper-patches/0005)
- **Folia-safe bot iteration** - Safe fake player iteration (patch 0045)

### Configuration System

Configuration uses Kotlin data classes in `fun.bm.mili.config.modules.*`:
- `GlobalEntitiesCounter` - Entity counting
- `CreativeFlyNoClip` - Creative mode noclip
- `WoolHopperCounter` - Wool hopper item counter
- `CommandConfig` - Command enable/disable toggles
- `RedStoneConfig` - Redstone behavior options

Config files generated at: `config/modules/*.yaml`

## Important Files & Directories

### Build Configuration
- `settings.gradle.kts` - Project structure (mili-api, mili-server)
- `build.gradle.kts` - Root build config
- `mili-server/build.gradle.kts.patch` - Fork definition, brand metadata
- `gradle.properties` - Version and group settings

### Core Patches (High Priority)
- `0040-Global-Entities-Counter.patch` - Cross-region entity tracking
- `0044-Carpet-features.patch` - Carpet mod compatibility (1155 lines)
- `0045-Add-Folia-safe-bot-iteration.patch` - Fake player safety
- `0046-Hopper-idle-optimization.patch` - Hopper performance
- `0047-0048` - Mili performance monitoring
- `0049-Entity-tracking-broadcast-performance-optimization.patch`
- `0050-0051` - Pufferfish optimizations
- `0052-0053` - Chunk preloading

### Source Code
- `mili-server/src/main/java/fun/bm/mili/` - All Mili-specific implementations
- Command implementations in `command/` and `feature/`
- Protocol handlers in `protocol/`

## Development Workflow

### Modifying Patches

1. **Edit applied source** in `mili-server/src/minecraft/` (after `applyAllPatches`)
2. **Rebuild patch**: `./gradlew :mili-server:rebuildPatches`
3. **Verify**: `./gradlew :mili-server:applyPatches`
4. **Commit patch file** (not the applied source)

**CRITICAL**: Always commit patch files, never commit applied source changes directly.

### Adding New Patches

1. Apply patches: `./gradlew applyAllPatches`
2. Modify source in `mili-server/src/minecraft/`
3. Generate patch: `./gradlew rebuildPatches`
4. New patch appears in appropriate `*-patches/features/` directory
5. Test: `./gradlew clean applyAllPatches createMojmapPaperclipJar`

### Upstream Sync

When syncing with Lophine upstream:

```bash
# Clone upstream
git clone --branch ver/1.21.11 https://github.com/LuminolMC/Lophine.git /tmp/lophine-upstream

# Compare patches
diff -u lophine-upstream/lophine-server/minecraft-patches/features/0040-*.patch \
         mili-server/minecraft-patches/features/0040-*.patch

# Copy and adapt upstream patches
cp /tmp/lophine-upstream/lophine-server/minecraft-patches/features/*.patch \
   mili-server/minecraft-patches/features/

# Update references: lophine → mili
# Rebuild and test
```

## Common Issues

### Patch Application Failures

**Symptom**: `Hunk #1 FAILED` during `applyAllPatches`

**Cause**: Index hash mismatch due to upstream changes

**Fix**:
1. Check patch hunk headers for correct line numbers
2. Update index hash in patch if upstream changed
3. Re-run `rebuildPatches` to regenerate clean patch

### Build Memory Issues

**Symptom**: Out of memory during build

**Fix**: Increase Gradle memory in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4G -XX:+UseParallelGC
```

### Embedded Git Repository Warning

**Symptom**: `warning: adding embedded git repository`

**Fix**:
```bash
git rm --cached -f mili-server/src/minecraft/resources
git rm --cached -f lophine-upstream
```

## Testing

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "fun.bm.mili.*"

# Build and test
./gradlew build
```

## Version Information

- **Minecraft**: 1.21.11
- **Group**: fun.bm.mili
- **Upstream**: Lophine ver/1.21.11 (LuminolMC/Lophine)
- **Luminol Ref**: 274f341465b3143c421de2c3110a69da4b68b75b

## Brand Identity

- **Project Name**: Mili
- **Brand ID**: luminolmc:mili
- **Brand Name**: Mili
- **Repository**: https://github.com/xucy10/Mili
- **bstats**: Mili (metrics tracking)

## Chunk Independent Scheduler (CIS)

### Architecture

```
ServerLevel
  └── ChunkIndependentScheduler (线程池 = CPU 核心数)
        ├── ChunkWorker [0,0] → Thread-1
        ├── ChunkWorker [1,0] → Thread-2
        ├── ChunkWorker [0,1] → Thread-1 (work-stealing)
        └── CrossChunkBus (专用协调线程 — 只做协调，不执行动作)
              ├── RedstoneBorderRelay (两阶段提交)
              ├── FluidBorderRelay (边界缓冲区 + 延迟传播)
              ├── EntityMigrationBus (CAS + MigrationQueue)
              └── BlockUpdateBorderRelay (延迟注入队列)
```

### Two-Phase Commit for Redstone

```
Phase 1 (ChunkWorker.tick 开始): ChunkBorderCache.captureBorderState()
  采集 4 个边界面的红石线/中继器/比较器/红石块/活塞状态
Phase 2 (tick 结束后): CrossChunkBus 收集所有待处理边界更新
  注入 delayed neighborChanged 调用到邻居区块
  延迟 = 1 tick (可配置 strictMode 回退 region 实现 0-tick)
```

### Deadlock Prevention

1. 所有锁按 ChunkPos 字典序获取
2. CrossChunkBus 永不持有 ChunkWorker 锁 (通过 volatile 标志位通信)
3. 超时降级: 等待 > timeoutMs 则放弃并回退到 region 模式
4. 复用 MiliTickSchedulerHook 的死锁检测器

### Mixed Mode (Default)

| 区块类型 | 调度方式 | 延迟 |
|---------|---------|------|
| 低交互 (无红石/流体/活塞) | 独立并行 tick | 0 |
| 高交互 (含红石/流体/活塞) | Folia region 串行 | 0 (strict) / 1 (normal) |

### Source Files

| File | Path | Lines |
|------|------|-------|
| Main scheduler | `mili-server/.../scheduler/ChunkIndependentScheduler.java` | ~230 |
| Chunk worker | `mili-server/.../scheduler/ChunkWorker.java` | ~170 |
| Cross-chunk bus | `mili-server/.../scheduler/CrossChunkBus.java` | ~200 |
| Border cache | `mili-server/.../scheduler/ChunkBorderCache.java` | ~180 |
| Redstone relay | `mili-server/.../scheduler/border/RedstoneBorderRelay.java` | ~70 |
| Fluid relay | `mili-server/.../scheduler/border/FluidBorderRelay.java` | ~30 |
| Block update relay | `mili-server/.../scheduler/border/BlockUpdateBorderRelay.java` | ~30 |
| Entity migration | `mili-server/.../scheduler/border/EntityMigrationBus.java` | ~82 |
| Dynamic group | `mili-server/.../scheduler/group/DynamicChunkGroup.java` | ~90 |
| Config | `mili-server/.../config/modules/misc/ChunkIndependentConfig.java` | ~60 |
| Verif. matrix | `mili-server/.../scheduler/CISVerificationMatrix.java` | ~90 |

### Integration

CIS is designed as **standalone utility classes** under `fun.bm.mili.scheduler.*`.
No patches to Minecraft/Folia code are required. Key design decisions:

- **ChunkWorker** only performs READ-ONLY border analysis (block state sampling).
  Never calls chunk.tick() — entity ticking remains on Folia region threads.
- **CrossChunkBus** coordinator thread only coordinates; never holds worker locks.
- **EntityMigrationBus** uses entity.getScheduler().run() for Folia-safe migration.
- High-interaction chunks (redstone/fluid/piston) auto-report to Folia region mode.
- Enables via config: `chunk_independent_scheduler.enabled: true`.

### Functional Verification Matrix

参见 `CISVerificationMatrix.java` 中的完整验证矩阵。关键验证项：

| 类别 | 验证项 | 状态 |
|------|--------|------|
| 红石 | 跨区块红石线/中继器/比较器 | DESIGNED — 两阶段提交 |
| 红石 | 活塞/粘性活塞跨区块 | DESIGNED — highInteraction 回退 |
| 红石 | TNT 复制机 | DESIGNED — strictMode 回退 region |
| 流体 | 水流/瀑布/熔岩+水跨区块 | DESIGNED — 边界缓冲区 + 延迟 |
| 实体 | 玩家/掉落物/矿车/弹射物穿越 | INHERITED — Folia 现有协议 |
| 压力 | 1000 实体集中区块 | DESIGNED — 按区块 tick |
| 压力 | 100 区块同时加载 | DESIGNED — 并行 + 混合模式 |
| 回归 | perf monitor / chunk preload | DESIGNED — 异常回退 region |
| 回归 | Carpet 兼容 / 假玩家 | VERIFIED — 不修改 entity tick |

## Kaiiju Port

### Entity Throttling

File: `mili-server/.../kaiiju/MiliEntityThrottler.java`

- Folia-aware: limits applied per-tick-region (RegionizedWorldData)
- Config-driven via `MiliEntityLimitsConfig`
- Wither, EnderDragon, IronGolem + default configurable

### Async Pathfinding

File: `mili-server/.../kaiiju/AsyncPathfindingExecutor.java`

- Dedicated thread pool for async pathfinding computation
- Results queued and applied via entity scheduler on owning region
- One pending async task per entity max

### Xymb Linear Format

See `luminol-server/.../luminol/data/BufferedLinearRegionFile.java` (base implementation).
Mili extends this with `BufferedLinearRegionFileFlusher.java` for async flushing.
