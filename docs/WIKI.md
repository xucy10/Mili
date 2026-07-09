# Mili Wiki

> 本文档汇总 Mili 相对于上游 Lophine 核心的修复、改动与新增功能，并说明各项功能对应的实现位置与优化点。
>
> **适用版本**：`1.21.11-R0.1-SNAPSHOT`（基于 Paper → Folia → Luminol → Lophine 构建）

---

## 1. 项目简介

**Mili** 是 [Lophine](https://github.com/LuminolMC/Lophine) 的衍生分支，目标是在 **Folia 并发/区域调度** 环境下提供更稳定、可配置的服务器运行时，并增强原版行为、红石（Redstone）与生存电路的可靠性。

主要设计方向：
- 在保持上游兼容的前提下，修复 Folia 区域化调度引入的并发问题；
- 提供可配置的原版行为开关，方便生存/生电服按需调整；
- 通过 Rust 辅助模块对高频计算路径做原生加速。

---

## 2. 与 Lophine 的关系 & 继承链

```
Minecraft（原版）
  └── Paper（服务端框架）
        └── Folia（区域多线程调度）
              └── Luminol（性能优化 fork）
                    └── Lophine（Mili 的直接上游）
                          └── Mili（本项目）
```

Mili 使用 **Hyacinthusweight**（基于 paperweight）补丁系统管理多层 fork：

| 模块 | 说明 |
|------|------|
| `paper-api` / `paper-server` | 上游 Paper，不直接修改 |
| `folia-api` / `folia-server` | 上游 Folia，不直接修改 |
| `luminol-api` / `luminol-server` | 上游 Luminol，不直接修改 |
| `mili-api` / `mili-server` | Mili 自己的 API 与核心补丁/源码 |
| `mili-rust` | Rust 原生优化模块 |

---

## 3. 构建与技术栈

| 项目 | 说明 |
|------|------|
| JDK | Java 21（toolchain + `--release 21`） |
| 构建工具 | Gradle（Kotlin DSL）+ Hyacinthusweight 补丁系统 |
| Kotlin | 2.0.21 JVM plugin |
| Rust | `mili-rust` 通过 `cargo build --release` 产出优化器二进制 |
| 产物 | `mili-server/build/libs/*-paperclip-*-mojmap.jar` |
| Maven 坐标 | `fun.bm.mili:mili-api:<version>` |

首次构建必须先执行：

```bash
./gradlew applyAllPatches
./gradlew :mili-server:createMojmapPaperclipJar
```

---

## 4. 核心改动概览（对比 Lophine）

### 4.1 品牌与配置体系

| 改动 | 说明 | 位置 |
|------|------|------|
| 重命名为 Mili | 服务端 Mod 名、自动更新指向 `MiliMC/Mili` | `mili-server/luminol-patches/features/0001-Rebrand-to-Mili.patch` |
| 新增 Mili 全局配置 | 注册 `mili_config.toml`，命名空间 `fun.bm.mili.config.modules` | `0001-Rebrand-to-Mili.patch` |
| 新增 Mili Carpet 配置 | 注册 `mili_carpet_config.toml`，命名空间 `fun.bm.mili.carpet.config.modules` | `0001-Rebrand-to-Mili.patch` |
| 配置迁移注解 | 通过 `@TransformedConfig` 把 Luminol/Lophine 旧配置路径映射到 Mili 新路径 | `0002-Transformed-Configs.patch` |

### 4.2 关键依赖补充

`mili-server/build.gradle.kts` 在 Lophine 基础上额外引入：

- `net.objecthunter:exp4j` — Carpet 计算器兼容
- `io.netty:netty-all` — io_uring 支持
- `com.electronwill.night-config:toml`
- `com.github.luben:zstd-jni`、`net.openhft:zero-allocation-hashing`、`net.openhft:affinity` — 压缩/哈希/亲和性
- `io.github.classgraph:classgraph`
- `org.spongepowered:configurate-gson` — Leaves 配置兼容

---

## 5. Folia 稳定性与并发修复

Folia 将世界切分为多个 `Region` 并在独立线程上调度。Mili 针对由此引入的跨区、调度与空指针问题做了以下修复：

### 5.1 区域调度与负载均衡

| 功能 | 说明 | 实现 |
|------|------|------|
| **Region Balancer** | 用共享线程池 + 优先级队列替代 Folia 的每个区域独占线程；根据实时负载动态分配 CPU，低负载区域合并执行 | `mili-server/src/main/java/fun/bm/mili/utils/RegionBalancer.java` |
| **Region Load Monitor** | 无锁滑动窗口统计每个区域的 tick 耗时，为 Region Balancer 提供优先级依据 | `mili-server/src/main/java/fun/bm/mili/utils/RegionLoadMonitor.java` |
| **Adaptive TPS Manager** | 根据 Region Load Monitor 动态调整 `TIME_BETWEEN_TICKS` | `mili-server/src/main/java/fun/bm/mili/utils/AdaptiveTPSManager.java` |
| **`/tick` 命令集成** | 通过配置启用 `/tick` 命令，并把 tick 调度 hook 到 Region Balancer | `0007-Add-config-to-enable-tick-command.patch` |

### 5.2 跨区事件与数据一致性

| 功能 | 说明 | 实现 |
|------|------|------|
| **Cross-Region Helper** | 类型化跨区事件队列，处理红石信号、实体伤害、方块通知、实体进出区域等跨区事件 | `mili-server/src/main/kotlin/fun/bm/mili/utils/CrossRegionHelper.kt` + `experiment/CrossRegionHelperConfig.kt` |
| **跨区伤害追踪** | 击杀/计分等需要在受害/施害者所在区域执行的逻辑，通过 Cross-Region Helper 异步派发 | `0002-Add-config-to-enable-cross-region-damage-trace.patch` |
| **全局实体计数器** | 按区域聚合 mob 数量，避免 O(entities) 扫描；修复跨区自然生成与消失不一致 | `mili-server/src/main/java/fun/bm/mili/utils/EntitiesCounterUtil.java` + `0040-Global-Entities-Counter.patch` |

### 5.3 Folia 特定崩溃修复

| 修复 | 说明 | Patch |
|------|------|-------|
| `RegionizedWorldData` 空连接 NPE | 区域合并时 `Connection.getPlayer()` 可能为空，增加空检并补全本地玩家连接 | `0041-Add-null-check-in-RegionizedWorldData-conections.patch` |
| 已移除实体仍添加效果 | 在 `addEffect` 全流程前检查实体是否已被移除 | `0042-Add-removed-check-before-all-checks-start-in-addEffe.patch` |
| `/save-all` 区域安全化 | 协调各区域的区块保存，支持超时与进度日志 | `0005-Add-config-to-enable-save-all-command.patch` + `SaveAllUtil.java` |

---

## 6. 红石与原版行为修复

Mili 从 Leaves 移植并适配了一批红石/生电相关修复，重点解决 Folia 下更新抑制、方块移除语义变化导致的问题。

### 6.1 更新抑制（Update Suppression）

| 功能 | 说明 | Patch |
|------|------|-------|
| 捕获更新抑制异常 | 在 `PacketProcessor`、`MinecraftServer.tickServer`、`ServerLevel`、`ServerPlayer`、`Entity`、`NeighborUpdater`、`StateHolder`、`ShulkerBoxBlock`、`LevelChunk` 等位置捕获 `UpdateSuppressionException`，避免服务端崩溃 | `0032-Leaves-Catch-update-suppression-crash.patch` |
| 防止掉落物丢失 | 抛出 `UpdateSuppressionException` 前先捕获掉落物 | `0037-Leaves-Prevent-loss-of-item-drops-due-to-update-supp.patch` |
| 禁止异常时重置已放置方块 | 移除 Paper 在异常时回滚方块状态的逻辑，避免破坏更新抑制装置 | `0039-Leaves-Do-not-reset-placed-block-on-exception-Do-not.patch` |
| CCE 更新抑制 | 容器类型转换异常路径的红石比较器信号处理 | `0033-Leaves-CCE-update-suppression.patch` |

### 6.2 红石与方块行为

| 功能 | 说明 | Patch |
|------|------|-------|
| 红石忽略向上更新 | 恢复 1.20.1/1.19 的红石粉/中继器/比较器向上更新行为 | `0034-Leaves-Redstone-ignore-upwards-update.patch` |
| 即时方块更新器 | 用 `InstantNeighborUpdater` 替换 `CollectingNeighborUpdater` | `0035-Instant-Block-Updater.patch` |
| 旧方块移除行为 | 恢复 1.21 之前的 `onRemove` 语义（容器、红石、铁轨、活塞等） | `0038-Leaves-Old-Block-remove-behaviour.patch` |
| 容器扩展/潜影盒堆叠 | 可配置潜影盒堆叠数量与 NBT 匹配规则 | `0003-Leaves-Item-overstack-util.patch` + `0011-Leaves-Item-overstack-util.patch` |
| 羊毛漏斗计数器 | 通过羊毛颜色实现 hopper counter，并配套 `/counter` 命令 | `0029-Leaves-Wool-Hopper-Counter.patch` |

---

## 7. 新增功能详解

### 7.1 配置系统

Mili 保留lophine的两套配置：

- **`mili_config.toml`** → 包路径 `fun.bm.mili.config.modules`
- **`mili_carpet_config.toml`** → 包路径 `fun.bm.mili.carpet.config.modules`

配置模块分为几大类：

| 类别 | 代表模块 | 说明 |
|------|----------|------|
| `function` | `LanguageConfig`、`CreativeFlyNoClipConfig`、`ContainerExpansionConfig`、`WoolHopperCounterConfig`、`FakeplayerConfig`、`ReplayAPIConfig` | 游戏机制与实用功能开关 |
| `experiment` | `CrossRegionHelperConfig`、`RegionBalancerConfig` | 实验性性能/并发功能 |
| `fixes` | `UpdateSuppressionCrashFixConfig` | 崩溃修复开关 |
| `misc` | `AutoUpdateConfig` | 自动更新等杂项 |
| `removed` | `RemovedConfig` | 已移除或待清理功能占位 |
| `carpet` | `CoreConfig`、`GeneralCompatConfig`、`CounterCompatConfig`、`FakePlayerCompatConfig` | Carpet 兼容规则映射 |

### 7.2 区域调度优化（Region Balancer）

**目标**：解决 Folia 低负载区域各自占用独立线程导致的资源浪费，以及高负载区域互相抢 CPU 的问题。

**设计要点**：
- 用固定大小线程池替代每区域独占线程；
- `PriorityBlockingQueue` 按实时负载优先级调度；
- 低负载区域动态合并批量执行；
- 通过 `RegionLoadMonitor` 无锁统计每个区域 tick 耗时；
- 支持任务取消、重试、状态追踪；
- 与 `AdaptiveTPSManager` 联动，根据整体负载调整 TPS。

**Rust 集成**：
- `RegionBalancer` 反射调用 `org.mili.rust.RustOptimizer` 的 `scheduler()` 获取批量/工作线程建议；
- 若 Rust 二进制不可用，自动回退到 Java 默认策略。

### 7.3 Rust 优化器（`mili-rust`）

`mili-rust` 是一个 Rust crate，构建后打包进服务端 JAR 的 `rust/` 目录，通过 JNI/子进程与 Java 交互。

| 模块 | 功能 | 优化点 |
|------|------|--------|
| `chunk.rs` | 区块坐标转换、区域计算、region key 打包 | 原生位运算加速 |
| `varint.rs` | Minecraft VarInt/VarLong 编解码 | 栈上分配、快速路径 |
| `nbt.rs` | NBT 流式扫描 | 不实例化完整 NBT 树，零分配扫描 |
| `protocol.rs` | 网络包合并成本计算 | 类 Huffman 合并策略，批处理建议 |
| `scheduler.rs` | 任务调度 | Rayon 工作窃取 + 小任务顺序 fast path |
| `occlusion.rs` | 批量遮挡剔除 | 每帧一次 JNI 调用，Rayon 并行 AABB/DDA |
| `util.rs` | Bitmap / MurmurHash3 / 2 的幂次 helper | 位图与哈希辅助 |

Java 侧入口：`org.mili.rust.RustOptimizer`（反射调用，缺失时回退 Java 实现）。

### 7.4 假玩家 / Bot 系统

从 Leaves 移植并适配 Folia：

| 功能 | 说明 | 配置/实现 |
|------|------|-----------|
| `/bot` 命令 | 创建、管理、移除假玩家 | `0022-Leaves-Fakeplayer.patch` |
| 假玩家常驻 | 可配置是否跨重启保留 | `FakeplayerConfig.canResident` |
| 假玩家背包 | 可打开假玩家背包 | `FakeplayerConfig.canOpenInventory` |
| 假玩家动作 | 攻击、破坏、钓鱼、跳跃、移动、使用物品等 | `mili-api` 中 `BotAction` 体系 |
| Locator Bar | 控制假玩家是否出现在路径点/定位栏 | `0004-Leaves-Fakeplayer.patch` |
| Carpet 规则映射 | `commandBot`、`fakePlayerResident`、`openFakePlayerInventory` 等 | `carpet/config/modules/FakePlayerCompatConfig.java` |

API 事件（`org.leavesmc.leaves.event.bot`）：
- `BotCreateEvent`、`BotJoinEvent`、`BotRemoveEvent`、`BotLoadEvent`、`BotDeathEvent`
- `BotInventoryOpenEvent`、`BotConfigModifyEvent`
- `BotActionEvent`、`BotActionExecuteEvent`、`BotActionScheduleEvent`、`BotActionStopEvent`

### 7.5 ReplayMod 摄影师

| 功能 | 说明 | 配置/实现 |
|------|------|-----------|
| 摄影师实体 | 支持创建 ReplayMod 摄影师进行录像 | `0031-Leaves-Replay-Mod-API.patch` |
| API | `Photographer` / `PhotographerManager` | `mili-api/src/main/java/org/leavesmc/leaves/entity/photographer/` |
| 配置开关 | `ReplayAPIConfig` | `function/ReplayAPIConfig.kt` |

### 7.6 客户端协议兼容层

Mili 继承了lophine实现的Leaves 协议核心，并接入多种客户端 mod 协议：

| 协议 | 说明 | 配置 | Patch |
|------|------|------|-------|
| **Carpet** | TPS/mobcaps/counter HUD、Carpet 规则同步 | `carpet/config/modules/*` | `0044-Carpet-features.patch` |
| **TISCM** | `tiscm:network/v1`，MSPT 广播、握手 | — | `0044-Carpet-features.patch` + `protocol/tiscm/TISCMProtocol.java` |
| **XaeroMap** | Xaero 地图通道支持 | `XaeroMapProtocolConfig` | `0027-Leaves-Xaero-Map-Protocol.patch` |
| **Jade** | Jade 服务端数据提供 | `JadeProtocolConfig` | `0026-Leaves-Jade-Protocol.patch` |
| **Syncmatica** | Litematic 同步 | `SyncmaticaProtocolConfig` | `0024-Leaves-Syncmatica-Protocol.patch` |
| **Servux** | Servux 客户端服务 | `ServuxProtocolConfig` | `0015-Leaves-Servux-Protocol.patch` |
| **REI** | Roughly Enough Items 服务端协议 | `REIServerProtocolConfig` | `0028-Leaves-Support-REI-protocol.patch` |
| **BBOR** | Bounding Box Outline Reloaded | `BBORProtocolConfig` | `0025-Leaves-BBOR-Protocol.patch` |
| **AppleSkin** | 饥饿/饱和度同步 | `AppleSkinProtocolConfig` | Leaves 协议核心 |
| **Alternative Block Placement** | Accurate/Carpet/Litematica 放置协议 | `AlternativeBlockPlacementProtocolConfig` | `0030-Leaves-Alternative-block-placement-Protocol.patch` |

协议核心：`0009-Leaves-Base-Protocol-Core.patch` 提供自定义 payload 的注册/编解码 hook。

### 7.7 Carpet / TIS / AMS 兼容

Mili 通过 `fun.bm.mili.carpet` 包实现 Carpet 规则到 Mili 配置的映射：

| 文件 | 作用 |
|------|------|
| `carpet/CarpetCompatSync.java` | 将 Carpet 规则值同步到 Mili 配置，并向客户端广播规则 |
| `carpet/CarpetCalculatorCompatHelper.java` | Carpet 计算器的数值/函数兼容 |
| `carpet/InteractionUpdateCompatHelper.java` | 交互更新相关规则兼容 |
| `carpet/LagFreeSpawningCompatHelper.java` | 生成优化规则兼容 |
| `carpet/config/modules/CoreConfig.java` | Carpet 总开关 |
| `carpet/config/modules/GeneralCompatConfig.java` | 50+ 条 Carpet/TIS/AMS/Org 规则映射 |
| `carpet/config/modules/CounterCompatConfig.java` | hopper counter 规则映射 |
| `carpet/config/modules/FakePlayerCompatConfig.java` | 假玩家相关规则映射 |

部分规则状态参见 [`docs/carpet-compat-status.md`](carpet-compat-status.md)。

### 7.8 命令系统

Mili 通过配置重新启用了若干 Folia 默认禁用的原版/Leaves 命令，并新增 `/counter` 命令：

| 命令 | 说明 | Patch |
|------|------|-------|
| `/function` | 通过配置启用 | `0004-Add-config-to-enable-function-command.patch` |
| `/scoreboard` | 通过配置启用 | `0006-Add-config-to-enable-scoreboard-command.patch` |
| `/save-all` | 区域安全保存，支持超时 | `0005-Add-config-to-enable-save-all-command.patch` |
| `/tick` | 通过配置启用，并接入 Region Balancer | `0007-Add-config-to-enable-tick-command.patch` |
| `/waypoint` | 通过配置启用 | `0002-Transformed-Configs.patch` 中 waypoint 映射 |
| `/counter` | 羊毛漏斗计数器：toggle/reset/display | `mili-server/src/main/kotlin/fun/bm/mili/command/counter/` |

### 7.9 其他实用功能

| 功能 | 说明 | 实现 |
|------|------|------|
| 多语言/i18n | 加载 Mojang 资源，支持服务端消息本地化 | `utils/ServerI18nUtil.java` |
| 随机档案池 | 为假玩家等提供随机玩家档案 | `utils/RandomProfilePool.java` |
| 并发表 | `AbstractConcurrentTable` / `OptimizedConcurrentTable` 等并发数据结构 | `utils/concurrent/` |
| 自动更新 | 检查 GitHub Releases 并下载新版 Mili jar | `0005-Diff-in-auto-update-patch.patch` |

---

## 8. API 新增（`mili-api`）

### 8.1 Kotlin 事件

位于 `org.leavesmc.leaves.event.bot`，均继承 `org.leavesmc.leaves.event.BukkitEvent`：

- `BotCreateEvent` — 可取消，包含皮肤、位置、原因、创建者
- `BotJoinEvent`、`BotRemoveEvent`、`BotLoadEvent`、`BotDeathEvent`
- `BotInventoryOpenEvent` — 可取消
- `BotConfigModifyEvent` — 可取消，包含 key 与旧/新值
- `BotActionEvent`、`BotActionExecuteEvent`、`BotActionScheduleEvent`、`BotActionStopEvent` — 可取消

### 8.2 Java API

- `org.leavesmc.leaves.entity.bot.Bot`（继承 `Player`）
- `BotManager` / `BotCreator`
- `BotAction` 及具体动作实现：Attack、BreakBlock、Drop、Fish、Jump、Look、Move、Mount、Rotation、Sneak、Swim、UseItem 等
- `org.leavesmc.leaves.entity.photographer.Photographer`（继承 `Player`）
- `PhotographerManager`
- `UpdateSuppressionEvent`、`PlayerOperationLimitEvent`
- `BukkitRecorderOption`

### 8.3 API 构建

`mili-api/build.gradle.kts` 聚合 `paper-api`、`folia-api`、`luminol-api` 的源码与资源，发布为单一 `fun.bm.mili:mili-api` 构件。

---

## 9. 文件与补丁索引

### 9.1 Luminol 层补丁（`mili-server/luminol-patches/features/`）

| Patch | 内容 |
|-------|------|
| `0001-Rebrand-to-Mili.patch` | 品牌重命名、新增 Mili 全局配置与 Carpet 配置 |
| `0002-Transformed-Configs.patch` | Luminol/Lophine 旧配置路径迁移到 Mili |
| `0003-Leaves-Item-overstack-util.patch` | 潜影盒堆叠接入 Mili 配置 |
| `0004-Leaves-Fakeplayer.patch` | 假玩家与 waypoint/locator bar 集成 |
| `0005-Diff-in-auto-update-patch.patch` | 自动更新指向 Mili 仓库与路径 |

### 9.2 Minecraft 层补丁（`mili-server/minecraft-patches/features/`）

| Patch | 内容 |
|-------|------|
| `0001-Add-config-to-disable-some-check-for-operators.patch` | OP 飞行/移动检查绕过 |
| `0002-Add-config-to-enable-cross-region-damage-trace.patch` | 跨区伤害追踪 |
| `0003-Add-config-to-enable-raytracing-tracker.patch` | 实体遮挡剔除追踪器 |
| `0004-Add-config-to-enable-function-command.patch` | `/function` 命令开关 |
| `0005-Add-config-to-enable-save-all-command.patch` | 区域安全 `/save-all` |
| `0006-Add-config-to-enable-scoreboard-command.patch` | `/scoreboard` 命令开关 |
| `0007-Add-config-to-enable-tick-command.patch` | `/tick` 命令 + Region Balancer hook |
| `0009-Leaves-Base-Protocol-Core.patch` | Leaves 自定义 payload 协议核心 |
| `0011-Leaves-Item-overstack-util.patch` | 广义物品堆叠 hook |
| `0016-LeavesHooks.patch` | `PaperHooks` 非 final 化 |
| `0021-Spawn-invulnerable-time.patch` | 旧版出生无敌时间 |
| `0022-Leaves-Fakeplayer.patch` | 完整假玩家集成 |
| `0029-Leaves-Wool-Hopper-Counter.patch` | 羊毛漏斗计数器 |
| `0031-Leaves-Replay-Mod-API.patch` | ReplayMod 摄影师 |
| `0032-Leaves-Catch-update-suppression-crash.patch` | 更新抑制崩溃捕获 |
| `0033-Leaves-CCE-update-suppression.patch` | 容器转换异常更新抑制 |
| `0034-Leaves-Redstone-ignore-upwards-update.patch` | 红石忽略向上更新 |
| `0035-Instant-Block-Updater.patch` | 即时邻居更新器 |
| `0037-Leaves-Prevent-loss-of-item-drops-due-to-update-supp.patch` | 更新抑制时保留掉落物 |
| `0038-Leaves-Old-Block-remove-behaviour.patch` | 旧方块移除语义 |
| `0039-Leaves-Do-not-reset-placed-block-on-exception-Do-not.patch` | 异常时不回滚方块 |
| `0040-Global-Entities-Counter.patch` | 全局实体计数器 |
| `0041-Add-null-check-in-RegionizedWorldData-conections.patch` | Folia 区域合并 NPE 修复 |
| `0042-Add-removed-check-before-all-checks-start-in-addEffe.patch` | 已移除实体效果添加检查 |
| `0043-Leaves-Creative-fly-no-clip.patch` | 创造飞行无碰撞 |
| `0044-Carpet-features.patch` | 50+ Carpet/TIS/AMS 规则实现 |

### 9.3 Mili 专属源码

| 路径 | 说明 |
|------|------|
| `mili-server/src/main/java/fun/bm/mili/utils/` | RegionBalancer、RegionLoadMonitor、AdaptiveTPSManager、SaveAllUtil、ServerI18nUtil、EntitiesCounterUtil、RandomProfilePool、MiliLogger |
| `mili-server/src/main/java/fun/bm/mili/utils/concurrent/` | 并发表实现 |
| `mili-server/src/main/java/fun/bm/mili/carpet/` | Carpet 兼容同步与辅助类 |
| `mili-server/src/main/java/fun/bm/mili/protocol/` | CarpetLoggerProtocol、TISCMProtocol |
| `mili-server/src/main/kotlin/fun/bm/mili/utils/CrossRegionHelper.kt` | 跨区事件队列 |
| `mili-server/src/main/kotlin/fun/bm/mili/command/counter/` | `/counter` 命令 |
| `mili-server/src/main/kotlin/fun/bm/mili/config/modules/` | Kotlin 配置模块 |
| `mili-server/src/main/kotlin/fun/bm/mili/enums/` | Kotlin 枚举（如 `AlternativePlaceType`） |
| `mili-rust/src/rust/` | Rust 优化器 crate |

---

## 10. 配置速查表

### 10.1 主配置（`mili_config.toml`）

| 配置键 | 类型 | 说明 |
|--------|------|------|
| `function.language.locale` | String | 服务端语言，默认 `en_us` |
| `function.creative-fly-no-clip.enabled` | Boolean | 创造飞行是否无碰撞 |
| `function.container-expansion.enabled` | Boolean | 容器扩展（潜影盒堆叠）总开关 |
| `function.container-expansion.shulker-count` | Int | 潜影盒最大堆叠数量 |
| `function.container-expansion.nbt-shulker-stackable` | Boolean | 是否按完整 NBT 匹配潜影盒堆叠 |
| `function.wool-hopper-counter.enabled` | Boolean | 羊毛漏斗计数器 |
| `function.wool-hopper-counter.unlimited-speed` | Boolean | 计数器是否无速率限制 |
| `function.fakeplayer.enabled` | Boolean | 假玩家总开关 |
| `function.fakeplayer.limit` | Int | 假玩家数量上限 |
| `function.fakeplayer.prefix` / `suffix` | String | 假玩家名称前后缀 |
| `function.fakeplayer.can-resident` | Boolean | 假玩家是否常驻 |
| `function.fakeplayer.can-open-inventory` | Boolean | 是否可打开假玩家背包 |
| `function.fakeplayer.enable-locator-bar` | Boolean | 假玩家是否显示在定位栏 |
| `function.replay-api.enabled` | Boolean | ReplayMod 摄影师 |
| `experiment.region-balancer.enabled` | Boolean | Region Balancer |
| `experiment.region-balancer.thread-pool-size` | Int | 线程池大小 |
| `experiment.cross-region-helper.enabled` | Boolean | 跨区事件辅助 |
| `fixes.update-suppression-crash-fix.enabled` | Boolean | 更新抑制崩溃修复 |
| `misc.auto-update.enabled` | Boolean | 自动更新 |

### 10.2 Carpet 兼容配置（`mili_carpet_config.toml`）

| 配置键 | 说明 |
|--------|------|
| `carpet.enabled` | Carpet 兼容总开关 |
| `carpet.general.*` | 50+ 条 Carpet/TIS/AMS/Org 规则映射 |
| `carpet.counter.*` | hopper counter 映射 |
| `carpet.fake-player.*` | 假玩家相关规则映射 |

---

## 11. 注意事项与限制

1. **实验性功能**：`experiment` 类别下的 Region Balancer、Cross-Region Helper 等属于实验性优化，建议先在测试环境验证后再上线。
2. **Rust 优化器可选**：`mili-rust` 提供加速，但若 Rust 二进制缺失，Java 侧会回退到纯 Java 实现，不影响功能正确性。
3. **Folia 语义**：部分修复（如全局实体计数器、跨区伤害追踪）是为了在 Folia 区域模型下保持与 Paper 一致的行为，启用后请留意相关插件的兼容性。
4. **Carpet 规则映射**：并非所有 Carpet 规则都已实现，详细状态见 [`docs/carpet-compat-status.md`](carpet-compat-status.md)。
5. **补丁工作流**：修改 `mili-server/src/minecraft/` 后必须执行 `./gradlew :mili-server:rebuildPatches` 并提交生成的 `.patch` 文件。

---

## 12. 相关链接

- 上游 Lophine: https://github.com/LuminolMC/Lophine
- Luminol: https://github.com/LuminolMC/Luminol
- Folia: https://github.com/PaperMC/Folia
- Paper: https://github.com/PaperMC/Paper
- LeavesMC（大量特性来源）: https://github.com/LeavesMC/Leaves
- Hyacinthusweight 补丁系统: https://github.com/LuminolMC/Hyacinthusweight
