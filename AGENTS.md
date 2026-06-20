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
