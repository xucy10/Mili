# Mili 组件健康度审计与修复计划

> **审计方式**：纯静态分析（目录结构 + 构建脚本 + 源码/补丁交叉引用），**未执行任何本地构建**。
> **审计日期**：2026-09-26 ｜ **基线**：分支 `HEAD=2e5d18c`（工作区有未提交改动）
> **说明**：paperweight/hyacinthusweight 项目的"组件耦合"不体现在 Java 源码互引上，而体现在 **补丁文件挂钩** 与 **Gradle 构建链路** 上。本审计以此为判据。

> **执行状态**：已完成 Phase 1 / 3 / 4 / 5 的可验证部分，Phase 2 因 Folia 兼容性阻塞而**分阶段搁置**（详见第 7 节）。
> 本文档第 2、4 节中，凡与「勘误」标注冲突之处，以勘误为准。

---

## 0. 审计方法与全局数据

| 指标 | 数值 |
|---|---|
| 仓库内额外 Java 类（不含补丁生成的 vanilla 代码） | **639** |
| `fun.bm.mili` 自有类 | **~150** |
| 补丁集 `minecraft-patches`（features 124 + todo 1 + unpatched 2） | **127 个补丁** |
| 补丁集 `paper-patches`（server 24 / api 12） | **36 个补丁** |
| 补丁集 `lophine-patches` | **1 个补丁（完全游离）** |

**各补丁集对项目类的承载力（引用次数）**

| 补丁集 | `fun.bm.mili` | `me.earthme.luminol` | `org.leavesmc` |
|---|---|---|---|
| `minecraft-patches` | **150** | **206** | **214** |
| `paper-patches` | 4 | 20 | 41 |
| `mili-api/paper-patches` | 0 | 1 | 9 |

**结论先行**：`minecraft-patches` 承载了项目 **95% 以上的 Mili/Luminol 特性挂钩**，但它在任何 Gradle 构建文件中都 **没有被引用**。这是本项目当前最大的结构性风险。

---

## 1. 结论速览

| 级别 | 问题 | 影响面 |
|---|---|---|
| **P0** | `mili-server/minecraft-patches` 不存在，`minecraft-patches` 未被任何构建脚本引用 | 全部 Mili/Luminol 特性不进构建产物 |
| **P0** | `MiliOptimizations.init()` 零调用点 | Mili 优化子系统运行时完全未启动 |
| **P0** | CI 校验 CIS / Kaiiju Mili 版两套**不存在**的组件 | CI 常绿假象，掩盖真实缺失 |
| **P1** | `mili-api` 依赖 `paper-api` / `folia-api`，但无任何任务生成它们 | API 模块编译链路断裂 |
| **P1** | `org.mili.rust.RustOptimizer` 子进程协议无对应 `[[bin]]` 产物 | 死代码 + 包名违规 + 永远走 fallback |
| **P1** | 57 个 `fun.bm.mili.config.modules.*` 配置模块疑似未产生效果 | 配置项暴露但无人读取 |
| **P2** | 9 组跨包同名重复类（含双份 `AutoUpdateConfig`） | 迁移残留，行为歧义 |
| **P2** | 文档结构/数字漂移（121 vs 124、`folia-api` 目录不存在等） | 新人误导 |

---

## 2. P0 — 断开的组件（硬断裂）

### 2.1 🔴 主补丁集未接入构建链路

**证据**

```kotlin
// mili-server/build.gradle.kts:48-53
upstream.patchDir("foliaServer") {
    upstreamPath = "folia-server"
    excludes = setOf("src/minecraft", "paper-patches", "minecraft-patches", ...)
    patchesDir = rootDirectory.dir("mili-server/folia-patches")   // ← 修复前该目录不存在
    outputDir  = rootDirectory.dir("folia-server")
}
```

- 修复前**不存在** `mili-server/folia-patches/`；实际存在的目录是 `mili-server/minecraft-patches/`（**124 个 feature 补丁**）。
- 全仓检索 `minecraft-patches`，当时**仅出现在上面这一行 `excludes` 里**，没有任何 `patchesDir` 指向它。
- 出处可追溯：`mili-server/build.gradle.kts.base:47` 与 `.patch:45` 中均为 `luminol-server/folia-patches`。
  说明**上游 Luminol 在 Mili 分支出去前后，把该目录从 `minecraft-patches` 更名为 `folia-patches`**，
  而本仓库继承了新构建脚本 + 旧目录名，**迁移只做了一半**。

**最终处置（采纳最小 diff 方案）**

把 `build.gradle.kts:52` 的 `patchesDir` 改回 `mili-server/minecraft-patches`，**保留原有目录名不动**，
而非重命名 127 个补丁文件。两种方案在构建层面等价，选择理由是：

- 改 1 行 vs 移动 127 个文件，侵入性相差两个数量级；
- 这 124 个补丁的 diff 目标 **100% 是 `net/**`、`io/**`、`ca/**` 包根路径**（实测 422 处），
  即 Mojang-mapped Minecraft 源码树 —— `minecraft-patches` 这个名字**在语义上反而更准确**；
- 仓库全部文档原本就以 `minecraft-patches` 为准，无需连带修改。

代价：与上游 Luminol 现在的目录名不一致，将来若直接同步上游构建脚本需再改一次。
该代价可接受，因为本仓库的 `build.gradle.kts` 已被手工改动到与 `.base`+`.patch` **相差 281 行**，本就不依赖自动同步。

**连带影响（最致命的一点）**

配置框架的引导入口只存在于 `minecraft-patches`：

```
mili-server/minecraft-patches/features/0001-Rebrand-to-Luminol.patch:15
  +  me.earthme.luminol.config.ConfigManager.initConfigs();
mili-server/minecraft-patches/features/0001-Rebrand-to-Luminol.patch:27
  +  me.earthme.luminol.config.ConfigManager.loadConfigFiles();
mili-server/minecraft-patches/features/0056-Force-disable-builtin-spark-plugin.patch:83
     me.earthme.luminol.config.ConfigManager.loadConfigFiles();
```

若该补丁集不生效 → `ConfigManager` 从不初始化 → `ClassLoadUtil.getClasses("fun.bm.mili.config.modules")` 从不执行 → **全部 Mili 配置与依赖配置的功能全部静默失效**，且构建不会报错。

> ⚠️ **待验证**：`rootDirectory` 的精确解析依赖于 hyacinthusweight 内部实现，静态无法 100% 定论。
> **验证方式（唯一需要的一次构建）**：
> ```bash
> ./gradlew applyAllPatches --no-configuration-cache
> ls folia-server/ && git diff --stat HEAD -- folia-server/ | tail -3
> ```
> 若 `folia-server/src/main/java` 中**没有**出现任何 `fun.bm.mili` 相关改动，即确认断裂。

---

### 2.2 🔴 `MiliOptimizations` 总入口零调用

```
mili-server/src/main/java/fun/bm/mili/MiliOptimizations.java
/** Mili 优化系统总初始化入口 */
public final class MiliOptimizations { public static void init(Plugin plugin) { ... } }
```

- 全仓（Java + 全部补丁 + 全部配置）**零引用**。
- 它是 `MiliChunkSystem` / `VillagerOptimizer` / `NetworkOptimizer` / `EntityDirtyTracker` / `LagRemover` 的唯一生命周期入口。
- 后果：即使 2.1 被修复，这些子系统仍然不会启动。

**连带未接入主流程的有 66 个类**（被内部 Java 互引，但无任何补丁挂钩）：
区块系统 8 个（`MiliChunkSystem`、`ChunkLifecycleManager`、`ChunkViewDistanceOptimizer`…）、
命令 10 个（`CounterCommand`、`MiliPerfCommand`、`HeatmapCommand`…）、
优化器 37 个（`RegionBalancer`、`SmartRegionManager`、`AsyncPathfinder`、`TechnicalMCOptimizer`…）、
村民优化器 6 个、portal 2 个、metrics 1 个。

---

### 2.3 🔴 CI 在校验两组"幽灵组件"

```yaml
# .github/workflows/build.yml:222-232  Kaiiju Entity Throttle Validation
path="mili-server/src/main/java/fun/bm/mili/kaiiju/$f"   # MiliEntityThrottler.java / MiliEntityLimitsConfig.java / AsyncPathfindingExecutor.java
# .github/workflows/build.yml:234-246  Verify CIS Source Files
CIS_DIR="mili-server/src/main/java/fun/bm/mili/scheduler"   # 含 border/ group/ 子目录
# .github/workflows/build.yml:259-262  Available Feature Flags
CIS: ChunkIndependentConfig.enabled / Kaiiju Throttler: MiliEntityLimitsConfig.enabled
```

全仓检索结果（含 java / patch / md / kts / json）：

| 关键字 | 命中数 |
|---|---|
| `ChunkIndependent` | **0** |
| `MiliEntityLimitsConfig` | **0** |
| `MiliEntityThrottler` | **0** |
| `AsyncPathfindingExecutor` | **0** |
| `fun/bm/mili/kaiiju` | **0** |
| `fun/bm/mili/scheduler` | **0** |

**CIS（Chunk Independent Scheduler）在本次静态扫描时（09:12 前）尚不存在，无法从任何 `.patch`、`.java`、文档中检索到其实现。**

> ⚠️ **勘误（后续发现）**：本次扫描后不久（文件时间戳 09:22–09:29），
> `mili-server/src/main/java/fun/bm/mili/scheduler/` 开始出现约 15 个源文件
> （`RegionResolver`、`RegionLifecycle`、`WorkerRuntime`、`TaskController` 等），且在持续变动中——
> **CIS 正处于实时开发阶段，属"尚未完成的进行中功能"，而非"虚构功能"**。
> 上述"命中数 0"仅对扫描时刻成立。

真实存在的 Kaiiju 移植是 `dev/kaiijumc/kaiiju/KaiijuEntityLimits.java` + `KaiijuEntityThrottler.java`，
与 CI 断言的路径不同。CI 断言的具体文件名（`MiliEntityThrottler.java` 等）至今仍不存在。

结论调整：CI 这**三个步骤本身应当移除**（基于不存在的文件做绿判定，无实际防护价值），
但不应据此认为 CIS 永远不会实现——它是正在进行的工作。

---

## 3. P1 — 没有耦合的组件

### 3.1 🟠 `mili-api` 模块缺少上游 API 生成链路

`mili-api/build.gradle.kts:112-121` 声明 sourceSets 依赖：

```
../paper-api/src/main/java   ← 不存在
../folia-api/src/main/java   ← 不存在
../luminol-api/src/main/java ← 存在（通过 srcDir 而非 settings.gradle.kts 引入）
src/main/java                ← 存在，且 import org.bukkit.*
```

- `settings.gradle.kts` 只 include 了 `mili-api` / `mili-server` / `mili-rust`。
- `mili-api` **没有任何 `paperweight` 配置块**，不会触发任何 patch 生成。
- 反观 `mili-server`，其 paperweight 只注册了 `paperServer` 与 `foliaServer` 两个 upstream，**没有 `paperApi`**。
- 因此 `paper-api/` / `folia-api/` 在本仓库构建中无从产生 → `mili-api` 依赖的上游类型缺失。
- 而 `mili-server` 通过 `implementation(project(":mili-api"))` 依赖它，属**传导性断裂**。
- 附带：`mili-api/build.gradle.kts:127` 引用 `../luminol-api/src/test/java`，该目录也不存在。

### 3.2 🟠 `org.mili.rust.RustOptimizer` — 三重复合问题

```
mili-server/src/main/java/org/mili/rust/RustOptimizer.java
private static Path locateBinary() { ... Path.of("build/rust/optimizer") ... }
Process process = new ProcessBuilder(binary.toString(), command, input).start();
```

1. **包名违规**：`org.mili.rust`，与项目统一包 `fun.bm.mili.rust` 不符（同仓并存两套 Rust 桥接）。
2. **零调用点**：全仓无任何引用，包括 `RustOptimizer.dedup/hash/mergePacketCost/packetSize/scheduler/taskUid/networkOptimize`。
3. **运行机制失效**：它以**子进程 + CLI 协议**（`rust-opt:<cmd>`）调用 Rust，与 CODEBUDDY.md 描述的 JNI 方案（`RustBridge` + `System.loadLibrary("mili_optimizer")`）完全冲突。而 `mili-rust/src/rust/Cargo.toml` **只有 `[lib]`（crate-type cdylib/rlib），没有 `[[bin]]`**，永远不产出名为 `optimizer` 的可执行文件 → `locateBinary()` 恒返回 null → 恒走 Java fallback。

> 对照：JNI 链路本身是**健康**的——`RustBridge.java` 声明的 9 个 native 方法
> （`nativeInit` / `batchCullEntitiesDirect` / `batchCullEntities` / `buildFrustumFromCamera` / `configLoad` / `configSave` / `configSaveMerge` / `configContains` / `configGetValue` / `configRemove` / `configClear`）
> 在 `jni_bridge.rs` 中均有对应的 `#[unsafe(no_mangle)] pub extern "system" fn Java_fun_bm_mili_rust_RustBridge_*`，**符号一一对应，无缺失**。

### 3.3 🟠 `lophine-patches` 游离补丁集

`mili-server/lophine-patches/features/0001-Rebrand-to-Mili.patch`（1 个文件）不被任何构建脚本引用，内容与 `minecraft-patches/0001` 高度重叠，属 Lophine 时代遗留。

### 3.4 🟠 完全孤立的 18 个类（补丁与 Java 双向零引用）

```
fun.bm.mili.MiliOptimizations                    ← 见 2.2，最重要
fun.bm.mili.carpet.CarpetCalculatorCompatHelper
fun.bm.mili.carpet.InteractionUpdateCompatHelper
fun.bm.mili.carpet.LagFreeSpawningCompatHelper
fun.bm.mili.enums.AlternativePlaceType            ← 与 EnumAlternativePlaceType 疑似重复
fun.bm.mili.protocol.tiscm.TISCMProtocol
fun.bm.mili.rust.{EntityCullHelper, RustCow, RustArena, RustOption, RustResult, RustScope, RustSpan}
fun.bm.mili.utils.{AdaptiveTPSManager, EntitiesCounterUtil, PerformanceCollector,
                   ReturnPortalManager, SaveAllUtil, ServerI18nUtil}
```

注意：`fun.bm.mili.rust.EntityCullHelper` 是**唯一例外**——它被补丁 `0118` / `0122` 调用，属已耦合；同包其余 6 个工具类（`RustCow/Arena/Option/Result/Scope/Span`）从未使用。

### 3.5 ✅ 已修复确认（无需处理）

`utils/dagschedule/*`（3 个类）与 `utils/picontrol/*`（2 个类）已在本次改动中删除，全仓**零残留引用**，删除干净。

---

## 4. P2 — 异常的组件（一致性漂移）

### 4.1 跨包同名类 9 组

| 类名 | 冲突包 |
|---|---|
| **`AutoUpdateConfig`** | `fun.bm.mili.config.modules.misc` ⚔ `me.earthme.luminol.config.modules.misc` |
| **`CommandConfig`** | `fun.bm.mili.config.modules.experiment` ⚔ `me.earthme.luminol.config.modules.experiment` |
| **`RemovedConfig`** | `fun.bm.mili.config.modules.removed` ⚔ `me.earthme.luminol.config.modules.removed` |
| **`RedStoneConfig`** | `fun.bm.mili.config.modules.experiment` ⚔ `fun.bm.mili.config.modules.function` |
| `ResetCommand` | `fun.bm.mili.command.counter.sub` ⚔ `me.earthme.luminol.commands.config.sub` |
| `ToggleCommand` | `fun.bm.mili.command.counter.sub` ⚔ `me.earthme.luminol.commands.bar.sub` |
| `ConfigCommand` | `me.earthme.luminol.commands.config` ⚔ `org.leavesmc.leaves.command.bot.subcommands` |
| `ListCommand` | `org.leavesmc.leaves.command.bot.subcommands` ⚔ `...subcommands.action` |
| `PacketType` | `org.leavesmc.leaves.bytebuf` ⚔ `org.leavesmc.leaves.protocol.syncmatica` |

**两者都会被 `ClassLoadUtil` 包扫描加载**，分别写入 `mili_global_config.toml` 与 `luminol_global_config.toml`。

> **勘误 1 — `AutoUpdateConfig` 不是残留，是有意设计的镜像。**
> `fun/bm/mili/config/modules/misc/AutoUpdateConfig.java` 的类注释明确写道：
> *"This file is only for showing auto update config... Please use luminol-server's auto update config"*，
> 并承诺 *"其中任意一个启用，完整功能即会启用"*。
> 审计初判其为"影子配置、建议删除"是**错误的**，已撤销删除计划。
>
> **但存在一个真问题**：该承诺的"或逻辑"在全仓（含所有补丁）**没有任何实现**。
> `AutoUpdateHelper` 只读 `me.earthme.luminol...AutoUpdateConfig` 侧字段，`enabled` 两侧都不被读取。
> → 处置：保留文件，但需修正其注释，或补齐 OR 逻辑，否则注释对用户是误导。

> **勘误 2 — 两组 `CommandConfig` 字段完全不同，不是重复。**
> `fun.bm.mili` 版：`tick` / `function` / `waypoint` / `scoreboard` / `saveAll` / `logAllProcess` / `saveAllTimeout`。
> `me.earthme.luminol` 版：`data` / `commandBlock` / `waypointsAndWaypointCommand`。
> 二者虽 `@ConfigClassInfo(name = "command")` 同名，但落在两个不同 ConfigsInstance 的 toml 中，互不覆盖。
> → 处置：无需删除，但重名易造成排查困扰，建议后续给 mili 侧改名（如 `command_extra`），纯改善可读性。

### 4.2 配置系统"一管理器两包"，耦合方式隐晦

```java
// me/earthme/luminol/config/ConfigManager.java:23-26
configfiles.put("luminol", ConfigsInstance.of("luminol", "me.earthme.luminol.config.modules"));
configfiles.put("mili",    ConfigsInstance.of("mili",    "fun.bm.mili.config.modules"));
configfiles.put("carpet",  ConfigsInstance.of("carpet",  "fun.bm.mili.carpet.config.modules"));
```

通过 `ClassLoadUtil.getClasses(pack)` **反射扫描包**注册，因此配置模块"零显式引用"属**正常而非必然缺陷**。
真正的风险是：**57 个 `fun.bm.mili.config.modules.*` 中，有多项缺少对应的功能实现方**（如 `VillagerTradeConfig`、`OldMCConfig`、`ItemEntityConfig`、`RayTrackingEntityTrackerConfig`、`DisableCheckConfig`…），即"配置有开关、无代码读取"。

### 4.3 文档与代码漂移

| 文档位置 | 声称 | 实际 |
|---|---|---|
| `CODEBUDDY.md:65`、`README.md:168/234`、`docs/WIKI.md:41` | `minecraft-patches/` 是权威补丁目录，121 个 | 未被构建引用；实际 **124** 个 feature |
| `CODEBUDDY.md`、`README.md` | 存在 `folia-api/` 目录 | **不存在** |
| `README.md:234`、`README_EN.md:261` | 存在 `mili-server/src/minecraft/java/` | **不存在** |
| `CODEBUDDY.md` Rust 章节 | "JNI 原生库直接调用…非子进程通信" | `RustOptimizer` 恰是子进程方案（见 3.2） |
| `CODEBUDDY.md` 常用命令 | `cd mili-rust/src/rust && cargo test --release # 28 tests` | 该目录无 `tests/`，仅 `[lib]` Cargo.toml；命令不可执行 |

### 4.4 本地构建前置缺失

- `mili-server/build.gradle.kts:23` 依赖 `libs/hyacinthusclip.jar`，**根目录无 `libs/`**（CI 在 build.yml:93-103 下载）。
- `folia-server` 子模块**未初始化**（`git submodule status` 前缀 `-`），本地必须先 `--init --recursive`。
- 两者均为本地首次构建的硬性前置，README 未充分说明。

---

## 5. 分阶段修复计划

### Phase 1 — 止血（先确认，再动手）

| # | 动作 | 验证方式 |
|---|---|---|
| 1.1 | **执行一次 `applyAllPatches`**，确认断裂是否为真（这是全流程唯一的构建调用） | `find folia-server -name '*.java' \| xargs grep -l 'fun.bm.mili' \| head`，为空则确认 P0-2.1 |
| 1.2 | 初始化子模块 + 下载 `libs/hyacinthusclip.jar` | `git submodule update --init --recursive`；`ls -la libs/` |
| 1.3 | 若 1.1 确认为真，**修复错位**。两个等价方案：<br>**A（已采纳，最小 diff）**：把 `build.gradle.kts:52` 的 `patchesDir` 改回 `mili-server/minecraft-patches`，目录不动<br>**B**：把目录重命名为 `folia-patches` 以对齐 `.base` 揭示的上游命名 | `ls mili-server/minecraft-patches` 存在，且构建能找到它 |

> **为何选 A**：改 1 行 vs 移动 127 个文件；且这 124 个补丁的 diff 目标 100% 是包根路径
> （Minecraft 源码树），`minecraft-patches` 这个名字语义更准确。代价是与上游当前目录名不一致。
> 若将来要改用 B，务必用 `git mv` 而非 `cp`，以保留 127 个补丁的历史归属。

### Phase 2 — 接驳主流程（核心工作量）

| # | 动作 | 涉及文件 |
|---|---|---|
| 2.1 | 在补丁集中补 `MiliOptimizations.init(plugin)` 的调用点（建议挂在 `0016-Rebrand-to-Mili.patch` 或新建 `0125-` 补丁，参考现有 `ConfigManager.initConfigs()` 的挂钩方式） | `mili-server/*-patches/features/` |
| 2.2 | 为 `counter` / `perf` / `heatmap` / `portal` / `backup` / `redstone-stats` 六组命令补充 Brigadier 注册钩子 | `fun.bm.mili.command.*` + 新补丁 |
| 2.3 | 逐个处理 66 个"仅内部互引"的类：确认是真孤儿还是漏挂钩，二选一（接驳 / 删除） | `fun.bm.mili.utils/chunk/villager/...` |
| 2.4 | 补齐 `patchRepo("paperApi")`（或等价机制）使 `paper-api` / `folia-api` 可被生成，修复 `mili-api` 编译链路 | `mili-server/build.gradle.kts`、`mili-api/build.gradle.kts` |

### Phase 3 — 清理与去重

| # | 动作 |
|---|---|
| 3.1 | 删除 `org.mili.rust.RustOptimizer`（零引用 + 无产物 + 包名违规），或改写为复用 `RustBridge` |
| 3.2 | 删除 `lophine-patches/`（游离补丁集） |
| 3.3 | 删除 `fun.bm.mili.config.modules.misc.AutoUpdateConfig`（零引用影子配置），保留 luminol 版；同法处理 `CommandConfig` / `RemovedConfig` 去重 |
| 3.4 | 删除未使用的 Rust 工具类 `Rust{Arena,Cow,Option,Result,Scope,Span}` 与孤立 util（`AdaptiveTPSManager`、`PerformanceCollector`、`SaveAllUtil`、`ServerI18nUtil`、`ReturnPortalManager`、`EntitiesCounterUtil`） |
| 3.5 | 决策 `fun/bm/mili/enums`：`AlternativePlaceType` 与 `EnumAlternativePlaceType` 二选一 |

### Phase 4 — 治理与防回归

| # | 动作 |
|---|---|
| 4.1 | 修正 CI：删除「Kaiiju Entity Throttle Validation」「Verify CIS Source Files」两个幽灵校验步骤，或将 `fail-fast` 加上（现状只打 WARN 不失败） |
| 4.2 | 修正 CI 的 `Check for Pending Patch Rebuild`，路径从 `mili-server/src/minecraft/` 改为实际输出目录 |
| 4.3 | 更新 `CODEBUDDY.md` / `README.md` / `README_EN.md` / `docs/WIKI.md`：补丁数 121→124、移除 `folia-api` 与 `src/minecraft/java` 描述、修正 Rust 交互描述与 cargo 测试命令 |
| 4.4 | **新增 CI 护栏**（建议 `.github/workflows/consistency.yml`）：<br>① 校验 `build.gradle.kts` 中所有 `patchesDir` 指向的目录存在<br>② 校验 `*/build.gradle.kts` 中所有 `srcDir("../xxx")` 存在<br>③ 校验 CI 步骤中引用的文件路径存在<br>④ 对 `fun.bm.mili` 类做静态孤儿检测（本次审计所用脚本可改造复用） |
| 4.5 | README 补充本地构建前置：子模块初始化 + `libs/hyacinthusclip.jar` 下载 |

---

## 6. 建议的处置优先级

```
立即确认  ──▶ Phase 1.1（唯一需要的一次构建，决定 Phase 2 是否必要）
   │
   ├── 若断裂为真 ──▶ Phase 1.3-A（改回 patchesDir 指到 minecraft-patches）──▶ Phase 2.1（补 MiliOptimizations 钩子）
   │                                                                   └─▶ Phase 2.4（修 mili-api 链路）
   │
   └── 若断裂为假 ──▶ 只需重跑引用分析，确认配置是否真的被加载

并行可做的低风险清理 ──▶ Phase 3.3 / 3.4 / 4.1 / 4.3
```

**最需要警惕的一点**：本项目当前的最大危险不是"代码写错了"，而是"**一切都显得正常**"——构建不报错、CI 是绿的、配置项能生成、命令手册齐全，但 Mili 的核心优化子系统可能一个都没有真正运行。

---

*本文档由静态分析生成。所有 `file:line` 证据均可在不构建的前提下复核。*

---

## 7. 执行记录

### 7.1 ✅ 已完成

| # | 变更 | 位置 |
|---|---|---|
| 1 | **采纳方案 A（最小 diff）**：目录保持 `minecraft-patches` 不动，改 `build.gradle.kts:52` 的 `patchesDir` 为 `mili-server/minecraft-patches`，使 124 个补丁重新进入构建链路 | `mili-server/build.gradle.kts:52` |
| 2 | 保持原有 `excludes`，并补上遗漏的 `build.gradle.kts.empty.patch` | `mili-server/build.gradle.kts:51` |
| 3 | 删除 `RustOptimizer`（零引用 + 无 `[[bin]]` 产物 + 包名违规） | `mili-server/src/main/java/org/mili/rust/RustOptimizer.java` |
| 4 | 移除 CI 中两个幽灵校验步骤（Kaiiju / CIS），并留注释说明为何删除 | `.github/workflows/build.yml` |
| 5 | 重构 Post-Build 冒烟测试：改为统计 jar 内 `fun/bm/mili` 类数量，作为"补丁集是否真的生效"的**间接探针** | `.github/workflows/build.yml` |
| 6 | 修正 Pending Patch Rebuild 检查路径（`src/minecraft` → `folia-server` 子模块工作副本） | `.github/workflows/build.yml` |
| 7 | 文档修正：补丁数 121→124 | `README.md`、`README_EN.md`、`CODEBUDDY.md`、`docs/WIKI.md`、`docs/CONTRIBUTING*.md` |
| 8 | 更正 CODEBUDDY.md 中不存在的 `folia-api/` 目录描述，标注 `mili-api` 上游缺口 | `CODEBUDDY.md` |
| 9 | 新增生成源码位置变更提示（避免贡献者依据错误路径操作） | `README.md`、`README_EN.md` |
| 10 | 清理 `.gitignore` 中已无对应模块的 Lophine 条目 | `.gitignore` |
| 11 | 新增静态一致性检查脚本 | `scripts/check_component_consistency.py` |
| 12 | 新增一致性护栏工作流 | `.github/workflows/consistency.yml` |

### 7.2 🔴 Phase 2 阻塞：子系统尚未 Folia 化

已准备启动钩子补丁，但**刻意放在 `minecraft-patches/todo/`（不参与构建）**，未合入 `features/`，原因见下：

```
mili-server/minecraft-patches/todo/0125-Bootstrap-Mili-optimizations.patch
```

**阻塞原因**：`MiliOptimizations.init()` 触达的子系统仍在使用 Folia 运行时**已禁用**的旧调度器：

| 文件 | 调用 | 备注 |
|---|---|---|
| `utils/LagRemover.java` | `new BukkitRunnable()...runTaskTimer/runTaskLater` ×3 | **`init()` 无条件调用**，配置项关不掉 |
| `utils/TPSTracker.java` | `new BukkitRunnable()...runTaskTimer` | |
| `villager/VillagerOptimizer.java` | `new BukkitRunnable()...runTaskTimer` | |
| `chunk/MiliChunkSystem.java` | `Bukkit.getScheduler().runTaskTimer` | |
| `utils/AutoBackupManager.java` | `Bukkit.getScheduler().runTask` ×2 | |

共 13 处。**这不只是 API 替换**：`LagRemover` 与 `VillagerOptimizer` 在全局调度线程里遍历
`Bukkit.getWorlds()` 并直接改实体，属于 Folia 的区域线程安全违规，需要围绕 per-region 调度重新设计。
在无法编译验证的前提下强行改写，风险远大于收益 —— 故分阶段处理。

**解阻塞顺序**：
1. 先跑一次 `applyAllPatches`，确认 `minecraft-patches` 生效（Phase 1.1）
2. Folia 化上述 5 个文件的调度逻辑
3. 把 `0125` 补丁从 `todo/` 移入 `features/`
4. 启动一次服务器，确认日志中没有 scheduler 相关异常

### 7.3 🟡 新发现（未列于原审计）

- **`build.gradle.kts` 三件套已失同步**：实际手工编辑过的 `build.gradle.kts` 与
  `build.gradle.kts.base` + `build.gradle.kts.patch` 的组合差异达 281 行（含 `kotlin` 插件、
  `hyacinthusclip` 从坐标改为本地 jar、`fork`→`folia`/`luminol`→`mili` 重命名等）。
  这意味着 `rebuildAllServerPatches` 若被执行会重写该 patch 文件，需在首次构建后重新对齐。
- **`build.gradle.kts.empty.patch`**：引用已不存在的 `lophine-server/build.gradle.kts` 的空补丁，
  属 Lophine 时期遗留（已加入 exclude，不再被拷入产物）。
- **`mili-api` 的 `srcDir` 缺口**：`../paper-api`、`../folia-api` 无人生成，
  但 Gradle 会静默忽略缺失 srcDir，因此**不报错、只是悄悄少源码** —— 与 P0-2.1 属同一类故障模式。

### 7.4 ⏭ 遗留待办

- [ ] 首次 `applyAllPatches` 验证（唯一必需的构建，决定 Phase 2 是否必要）
- [ ] Folia 化 5 个文件的调度逻辑，然后启用 `0125` 补丁
- [ ] 补齐 `paperApi` upstream 生成链路，修复 `mili-api`
- [ ] 修正 `AutoUpdateConfig` 注释与其未实现的 OR 逻辑
- [ ] 决策 `fun.bm.mili.rust.Rust{Arena,Cow,Option,Result,Scope,Span}` 等未接线工具类的去留
      （`RustCow` 目前被孤儿类 `AdaptiveTPSManager` 引用，未一并删除）
