# Mili Wiki

> 本文档汇总 Mili 的修复、改动与新增功能，并说明各项功能对应的实现位置与优化点。
>
> **适用版本**：`26.2-R0.1-SNAPSHOT`（基于 Paper → Folia 构建）

---

## 1. 项目简介

**Mili** 是直接基于 [Folia](https://github.com/PaperMC/Folia) 的 Minecraft 服务端核心，目标是在 **Folia 并发/区域调度** 环境下提供更稳定、可配置的服务器运行时，并增强原版行为、红石（Redstone）与生存电路的可靠性。

主要设计方向：
- 在保持上游兼容的前提下，修复 Folia 区域化调度引入的并发问题；
- 提供可配置的原版行为开关，方便生存/生电服按需调整；
- 通过 Rust 原生模块对高频计算路径做 JNI 零拷贝加速。

---

## 2. 继承链与补丁系统

```
Minecraft（原版）
  └── Paper（服务端框架）
        └── Folia（区域多线程调度）
              └── Mili（本项目）
```

> Mili 原为 Lophine/Luminol 的衍生分支，现已迁移为直接基于 Folia，将 Luminol 的优化源码内联合并。
> Luminol、Hyacinthusweight 均已删库。

Mili 使用 **Hyacinthusweight**（基于 paperweight）补丁系统管理多层 fork：

| 模块 | 说明 |
|------|------|
| `paper-api` / `paper-server` | 上游 Paper，不直接修改 |
| `folia-api` / `folia-server` | 上游 Folia，不直接修改 |
| `mili-api` / `mili-server` | Mili 自己的 API 与核心补丁/源码 |
| `mili-rust` | Rust 原生优化模块 |

补丁文件位于 `mili-server/minecraft-patches/features/`，共 **121 个** feature 补丁。

---

## 3. 构建与技术栈

| 项目 | 说明 |
|------|------|
| JDK | Java 25（toolchain + `--release 25`） |
| 构建工具 | Gradle 9.4.1（Kotlin DSL）+ Hyacinthusweight 补丁系统 |
| Rust | edition 2024，通过 `cargo build --release` 产出 JNI 原生库 |
| 产物 | `mili-server/build/libs/mili-paperclip-26.2-R0.1-SNAPSHOT.jar` |
| Maven 坐标 | `fun.bm.mili:mili-api:26.2-R0.1-SNAPSHOT` |

首次构建必须先执行：

```bash
./gradlew applyAllPatches --no-configuration-cache --no-build-cache
./gradlew :mili-server:createMojmapPaperclipJar
```

---

## 4. 核心改动概览

### 4.1 品牌与配置体系

| 改动 | 说明 | 位置 |
|------|------|------|
| 重命名为 Mili | 服务端 Mod 名、自动更新指向 `MiliMC/Mili` | `0001-Rebrand-to-Luminol.patch` |
| 新增 Mili 全局配置 | 注册 `mili_config.toml`，命名空间 `fun.bm.mili.config.modules` | 品牌补丁 |
| 新增 Mili Carpet 配置 | 注册 `mili_carpet_config.toml`，命名空间 `fun.bm.mili.carpet.config.modules` | 品牌补丁 |

### 4.2 关键依赖

`mili-server/build.gradle.kts` 额外引入：

- `net.objecthunter:exp4j` — Carpet 计算器兼容
- `io.netty:netty-all` — io_uring 支持
- `com.electronwill.night-config:toml`
- `com.github.luben:zstd-jni`、`net.openhft:zero-allocation-hashing`、`net.openhft:affinity` — 压缩/哈希/亲和性
- `io.github.classgraph:classgraph`
- `org.spongepowered:configurate-gson` — Leaves 配置兼容

---

## 5. Folia 稳定性与并发修复

### 5.1 区域调度与负载均衡

| 功能 | 说明 | 实现 |
|------|------|------|
| **Region Balancer** | 共享线程池 + 优先级队列替代 Folia 每区域独占线程；根据实时负载动态分配 CPU，低负载区域合并执行 | `utils/RegionBalancer.java` |
| **Region Load Monitor** | 无锁滑动窗口统计每个区域的 tick 耗时，为 Region Balancer 提供优先级依据 | `utils/RegionLoadMonitor.java` |
| **Adaptive TPS Manager** | 根据 Region Load Monitor 动态调整 `TIME_BETWEEN_TICKS` | `utils/AdaptiveTPSManager.java` |
| **Smart Region Manager** | 区域迁移任务管理 + 对象池复用 | `utils/SmartRegionManager.java` |
| **RegionTaskIdRegistry** | 全局 UUID 注册中心，`ConcurrentHashMap.putIfAbsent` 原子注册，碰撞重试 8 次，防止跨区块任务 ID 碰撞导致崩溃 | `utils/RegionTaskIdRegistry.java` |
| **Cross-Region Helper** | 类型化跨区事件队列，处理红石信号、实体伤害、方块通知、实体进出区域等跨区事件 | `utils/CrossRegionHelper.java` |
| **Chunk Region Bridge** | 250ms 同步区块热度到区域调度器 | `bridge/ChunkRegionBridge.java` |

### 5.2 跨区事件与数据一致性

| 功能 | 说明 | 实现 |
|------|------|------|
| **跨区伤害追踪** | 击杀/计分等需要在受害/施害者所在区域执行的逻辑，通过 Cross-Region Helper 异步派发 | `minecraft-patches/features/` |
| **全局实体计数器** | 按区域聚合 mob 数量，避免 O(entities) 扫描；修复跨区自然生成与消失不一致 | `utils/EntitiesCounterUtil.java` |

### 5.3 Folia 特定崩溃修复

| 修复 | 说明 | Patch |
|------|------|-------|
| `RegionizedWorldData` 空连接 NPE | 区域合并时 `Connection.getPlayer()` 可能为空，增加空检 | `0112-Add-null-check-in-RegionizedWorldData-conections.patch` |
| 已移除实体仍添加效果 | 在 `addEffect` 全流程前检查实体是否已被移除 | `0113-Add-removed-check-before-all-checks-start-in-addEffe.patch` |
| `/save-all` 区域安全化 | 协调各区域的区块保存，支持超时与进度日志 | `SaveAllUtil.java` |

### 5.4 线程安全加固（全面排查修复）

Mili 对全部 Java 源码进行了系统性 bug 排查，修复了以下类别的问题：

**致命级 — 线程静默死亡**：
- 全项目 22+ 处 `catch(Exception)` → `catch(Throwable)`，防止 `OutOfMemoryError`、`StackOverflowError`、`NoClassDefFoundError` 等 Error 绕过捕获导致调度器线程永久死亡
- 涉及文件：`AsyncPathfinder`、`AutoBackupManager`、`MemoryOptimizer`、`MmapRegionStorage`、`ServerI18nUtil`、`MiliMetrics`、`CrossDimensionTeleportQueue`、`ChunkLifecycleManager`、`AsyncChunkProcessor`、`LagFreeSpawningCompatHelper`、`ChunkDeltaCompressor`、`BStatsConfig`、`DynamicViewDistanceManager` 等

**致命级 — 数据损坏**：
- `ChunkHotness` SCORE_MASK 破坏 double 位布局产生 NaN（已删除 MASK）
- `RegionLoadMonitor` writeIndex 溢出导致负索引 AIOOBE（改为 `Math.floorMod`）
- `VillagerOptimizer` 时间尺度混淆（游戏世界时间与系统时间混存导致 elapsed 计算完全错误）

**资源泄漏级**：
- `RegionBalancer` taskRecords 无限增长 OOM（添加 TTL 清理）
- `MmapRegionStorage` 旧条目失效时未关闭文件句柄（添加 `close()` 调用）
- `ChunkDeltaCompressor` Deflater 的 `end()` 不在 finally 中（native 内存泄漏）
- `CrossRegionHelper` 3 处丢弃事件未注销 UUID（已补 unregister）

**并发竞态级**：
- `MiliChunkSystem`/`SmartRegionManager` volatile boolean initialized 无 CAS（改为 `AtomicBoolean.compareAndSet`）
- `ChunkRegionBridge` 异步线程遍历 Bukkit 集合 CME（改为先快照 ArrayList）
- 多处 `player.getLocation()` 多次调用竞态（改为调用一次存入局部变量）
- `ConcurrentTable` 全部 `.equals()` 模式替换为 `Objects.equals()` 防 NPE

---

## 6. 红石与原版行为修复

### 6.1 更新抑制（Update Suppression）

| 功能 | 说明 | Patch |
|------|------|-------|
| 捕获更新抑制异常 | 在 `PacketProcessor`、`MinecraftServer.tickServer`、`ServerLevel`、`ServerPlayer`、`Entity`、`NeighborUpdater`、`StateHolder`、`ShulkerBoxBlock`、`LevelChunk` 等位置捕获 `UpdateSuppressionException` | `0103-Leaves-Catch-update-suppression-crash.patch` |
| 防止掉落物丢失 | 抛出异常前先捕获掉落物 | `0108-Leaves-Prevent-loss-of-item-drops-due-to-update-supp.patch` |
| 禁止异常时重置已放置方块 | 移除 Paper 在异常时回滚方块状态的逻辑 | `0110-Leaves-Do-not-reset-placed-block-on-exception-Do-not.patch` |
| CCE 更新抑制 | 容器类型转换异常路径的红石比较器信号处理 | `0104-Leaves-CCE-update-suppression.patch` |

### 6.2 红石与方块行为

| 功能 | 说明 | Patch |
|------|------|-------|
| 红石忽略向上更新 | 恢复 1.20.1/1.19 的红石粉/中继器/比较器向上更新行为 | `0105-Leaves-Redstone-ignore-upwards-update.patch` |
| 即时方块更新器 | 用 `InstantNeighborUpdater` 替换 `CollectingNeighborUpdater` | `0106-Instant-Block-Updater.patch` |
| 旧方块移除行为 | 恢复 1.21 之前的 `onRemove` 语义 | `0109-Leaves-Old-Block-remove-behaviour.patch` |
| 羊毛漏斗计数器 | 通过羊毛颜色实现 hopper counter + `/counter` 命令 | `0100-Leaves-Wool-Hopper-Counter.patch` |

---

## 7. Rust 原生优化模块（`mili-rust`）

`mili-rust` 是一个 Rust crate（edition 2024），构建后打包进服务端 JAR，通过 **JNI DirectByteBuffer 零拷贝**与 Java 交互。

### 7.1 模块结构

| 模块 | 功能 | 技术亮点 |
|------|------|----------|
| `config.rs` | TOML 配置文件读写 | `toml_edit` 保留注释与格式，JSON ↔ TOML 双向转换，JNI 批量传输 |
| `entity_cull.rs` | 实体视锥剔除 | 6 平面 AABB frustum 测试，DirectByteBuffer 零拷贝，Rayon 并行批处理 |
| `frustum.rs` | 视锥体构建与测试 | 从相机参数或投影矩阵构建，球体/AABB/点测试，EPSILON 浮点防护 |
| `jni_bridge.rs` | JNI 桥接层 | 批量处理 only，`catch_unwind` 防 panic 传播，负数实体数/null 指针/容量校验 |

### 7.2 JNI 交互方式

**非子进程通信，而是 JNI 原生库直接调用**：

1. Java 侧 `RustBridge.java` 通过 `System.loadLibrary` 加载 `mili_optimizer` 原生库
2. 实体数据打包进 `DirectByteBuffer`，Rust 通过 `GetDirectBufferAddress` 直接读取内存地址
3. 每 tick 仅 M 次 JNI 调用（M = 观察者数量），而非 N×M 次（N = 实体数）
4. Rust 二进制不可用时，自动回退到纯 Java 实现

### 7.3 安全设计

- `catch_unwind(AssertUnwindSafe(...))` 包装所有 JNI 入口，防止 panic 跨 FFI 边界传播
- `#[unsafe(no_mangle)]` 符合 edition 2024 规范
- `overflow-checks=true` 防止算术溢出 UB
- `checked_mul` 防止长度溢出
- EPSILON=1e-6 浮点比较防护
- 退化向量测试

### 7.4 构建配置

```toml
[profile.release]
opt-level = 3
lto = "fat"
codegen-units = 1
panic = "unwind"          # 支持 catch_unwind
strip = "symbols"
overflow-checks = true     # 防止算术溢出 UB
```

验证：`cargo clippy --release` — 0 error, 0 warning；`cargo test --release` — 28 tests passed。

---

## 8. 配置系统

Mili 提供两套 TOML 配置文件：

- **`mili_config.toml`** → 包路径 `fun.bm.mili.config.modules`
- **`mili_carpet_config.toml`** → 包路径 `fun.bm.mili.carpet.config.modules`

| 类别 | 代表模块 | 说明 |
|------|----------|------|
| `function` | `LanguageConfig`、`FakeplayerConfig`、`ContainerExpansionConfig`、`WoolHopperCounterConfig` | 游戏机制与实用功能开关 |
| `experiment` | `CrossRegionHelperConfig`、`RegionBalancerConfig` | 实验性性能/并发功能 |
| `optimizations` | `NetworkOptimizerConfig`、`MmapRegionStorageConfig`、`AsyncPathfindingConfig` | 性能优化 |
| `fixes` | `UpdateSuppressionCrashFixConfig` | 崩溃修复开关 |
| `misc` | `AutoUpdateConfig`、`BStatsConfig` | 自动更新等杂项 |
| `removed` | `RemovedConfig` | 已移除或待清理功能占位 |
| `carpet` | `CoreConfig`、`GeneralCompatConfig`、`CounterCompatConfig`、`FakePlayerCompatConfig` | Carpet 兼容规则映射 |

---

## 9. 客户端协议兼容层

| 协议 | 说明 | Patch |
|------|------|-------|
| **Carpet** | TPS/mobcaps/counter HUD、Carpet 规则同步 | `0115-Carpet-features.patch` |
| **TISCM** | `tiscm:network/v1`，MSPT 广播、握手 | `0115-Carpet-features.patch` |
| **XaeroMap** | Xaero 地图通道支持 | `0098-Leaves-Xaero-Map-Protocol.patch` |
| **Jade** | Jade 服务端数据提供 | `0097-Leaves-Jade-Protocol.patch` |
| **Syncmatica** | Litematic 同步 | `0095-Leaves-Syncmatica-Protocol.patch` |
| **Servux** | Servux 客户端服务 | Leaves 协议核心 |
| **REI** | Roughly Enough Items 服务端协议 | `0099-Leaves-Support-REI-protocol.patch` |
| **BBOR** | Bounding Box Outline Reloaded | `0096-Leaves-BBOR-Protocol.patch` |
| **AppleSkin** | 饥饿/饱和度同步 | Leaves 协议核心 |
| **Alternative Block Placement** | Accurate/Carpet/Litematica 放置协议 | `0101-Leaves-Alternative-block-placement-Protocol.patch` |

---

## 10. 假玩家 / Bot 系统

从 Leaves 移植并适配 Folia：

| 功能 | 说明 |
|------|------|
| `/bot` 命令 | 创建、管理、移除假玩家 |
| 假玩家常驻 | 可配置是否跨重启保留 |
| 假玩家背包 | 可打开假玩家背包 |
| 假玩家动作 | 攻击、破坏、钓鱼、跳跃、移动、使用物品等 |
| Carpet 规则映射 | `commandBot`、`fakePlayerResident`、`openFakePlayerInventory` 等 |

API 事件（`org.leavesmc.leaves.event.bot`）：
- `BotCreateEvent`、`BotJoinEvent`、`BotRemoveEvent`、`BotLoadEvent`、`BotDeathEvent`
- `BotInventoryOpenEvent`、`BotConfigModifyEvent`
- `BotActionEvent`、`BotActionExecuteEvent`、`BotActionScheduleEvent`、`BotActionStopEvent`

---

## 11. 其他功能

| 功能 | 说明 | 实现 |
|------|------|------|
| ReplayMod 摄影师 | 创建摄影师实体进行录像 | `0102-Leaves-Replay-Mod-API.patch` |
| 多语言/i18n | 加载 Mojang 资源，支持服务端消息本地化 | `utils/ServerI18nUtil.java` |
| 随机档案池 | 为假玩家提供随机玩家档案 | `utils/RandomProfilePool.java` |
| 并发表 | `AbstractConcurrentTable` / `OptimizedConcurrentTable` 等并发数据结构 | `utils/concurrent/` |
| 自动更新 | 检查 GitHub Releases 并下载新版 | 配置 `misc.auto-update` |
| 网络优化 | 异步 keepalive、连接池管理、SO_REUSEADDR、背压 | `utils/NetworkOptimizer.java`、`utils/AsyncKeepaliveManager.java` |
| 内存优化 | 周期性内存监控、GC 阈值动态调优 | `utils/MemoryOptimizer.java` |
| 村民优化 | 区块卸载时村民状态保存/恢复 | `villager/VillagerOptimizer.java` |
| 传送门管理 | 传送门配对、原子写入、NPE 防护 | `portal/PortalLinkManager.java` |

---

## 12. 配置速查表

### 12.1 主配置（`mili_config.toml`）

| 配置键 | 类型 | 说明 |
|--------|------|------|
| `function.language.locale` | String | 服务端语言，默认 `en_us` |
| `function.creative-fly-no-clip.enabled` | Boolean | 创造飞行是否无碰撞 |
| `function.container-expansion.enabled` | Boolean | 容器扩展（潜影盒堆叠）总开关 |
| `function.container-expansion.shulker-count` | Int | 潜影盒最大堆叠数量 |
| `function.wool-hopper-counter.enabled` | Boolean | 羊毛漏斗计数器 |
| `function.fakeplayer.enabled` | Boolean | 假玩家总开关 |
| `function.fakeplayer.limit` | Int | 假玩家数量上限 |
| `function.fakeplayer.can-resident` | Boolean | 假玩家是否常驻 |
| `function.fakeplayer.can-open-inventory` | Boolean | 是否可打开假玩家背包 |
| `function.replay-api.enabled` | Boolean | ReplayMod 摄影师 |
| `experiment.region-balancer.enabled` | Boolean | Region Balancer |
| `experiment.region-balancer.thread-pool-size` | Int | 线程池大小 |
| `experiment.cross-region-helper.enabled` | Boolean | 跨区事件辅助 |
| `optimizations.network-optimizer.enabled` | Boolean | 网络优化器 |
| `optimizations.mmap-region-storage.enabled` | Boolean | mmap 区域存储 |
| `fixes.update-suppression-crash-fix.enabled` | Boolean | 更新抑制崩溃修复 |
| `misc.auto-update.enabled` | Boolean | 自动更新 |

### 12.2 Carpet 兼容配置（`mili_carpet_config.toml`）

| 配置键 | 说明 |
|--------|------|
| `carpet.enabled` | Carpet 兼容总开关 |
| `carpet.general.*` | 50+ 条 Carpet/TIS/AMS/Org 规则映射 |
| `carpet.counter.*` | hopper counter 映射 |
| `carpet.fake-player.*` | 假玩家相关规则映射 |

---

## 13. 注意事项与限制

1. **实验性功能**：`experiment` 类别下的 Region Balancer、Cross-Region Helper 等属于实验性优化，建议先在测试环境验证后再上线。
2. **Rust 优化器可选**：`mili-rust` 提供加速，但若 Rust 二进制缺失，Java 侧会回退到纯 Java 实现，不影响功能正确性。
3. **Folia 语义**：部分修复（如全局实体计数器、跨区伤害追踪）是为了在 Folia 区域模型下保持与 Paper 一致的行为，启用后请留意相关插件的兼容性。
4. **Carpet 规则映射**：并非所有 Carpet 规则都已实现，详细状态见 [`docs/carpet-compat-status.md`](carpet-compat-status.md)。
5. **补丁工作流**：修改 `mili-server/src/minecraft/` 后必须执行 `./gradlew :mili-server:rebuildPatches` 并提交生成的 `.patch` 文件。

---

## 14. 相关链接

- Folia（直接上游）: https://github.com/PaperMC/Folia
- Paper: https://github.com/PaperMC/Paper
- LeavesMC（大量特性来源）: https://github.com/LeavesMC/Leaves
