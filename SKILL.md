---
title: Mili 项目代码审查与 bug 修复工作流
scope: workspace
owner: "Mili"
summary: |
  面向 Mili Minecraft 服务端核心的系统性代码审查工作流。覆盖 Java 源码并发安全、NPE、整数溢出、时间尺度混淆、线程静默死亡等 bug 排查，以及 Rust JNI 模块的安全加固。
tags: [code-review, bug-fix, rust, jni, concurrency, minecraft]
---

# Mili 代码审查与 bug 修复工作流

## 目标

对 Mili 项目（基于 Folia 的 Minecraft 26.2 服务端核心）进行系统性 bug 排查与修复，覆盖 Java 源码中的并发安全、NPE、整数溢出、时间尺度混淆、线程静默死亡等问题，以及 Rust JNI 模块的安全加固。

## 适用场景

- 全项目代码审查（Java + Rust）
- 线程安全问题排查（竞态、死锁、线程静默死亡）
- JNI 边界安全加固
- 区域调度系统稳定性优化
- 网络连接稳定性优化

## 何时触发

- 大规模代码变更后需要全面审查
- 发现服务器线程静默死亡或 OOM 崩溃
- JNI 调用导致 native 崩溃
- 区域调度系统出现任务 ID 碰撞或任务丢失
- 网络连接不稳定需要优化

## 前提条件

- JDK 25 已安装并配置 `JAVA_HOME`
- Rust toolchain（edition 2024）已安装
- 项目已 `applyAllPatches` 并可成功编译
- 构建环境：`./gradlew :mili-server:compileJava` + `cargo clippy --release` + `cargo test --release`

## 工作流步骤

### 1. 环境准备

```bash
export JAVA_HOME="C:/Users/Administrator/Downloads/jdk-25_windows-x64_bin/jdk-25.0.4"
export PATH="$JAVA_HOME/bin:$PATH"
cd "E:/Program Files/Tencent/AndrowsData/Mili"
./gradlew :mili-server:compileJava
```

### 2. Java 代码审查

按严重程度分类排查：

**致命级 — 线程静默死亡**：
- 搜索 `catch(Exception)` 模式，在调度器/线程上下文中改为 `catch(Throwable)`
- 涉及 `ScheduledExecutorService`、`CompletableFuture`、线程池的所有 catch 块

**致命级 — 数据损坏**：
- 检查浮点位操作（如 `SCORE_MASK` 破坏 double 位布局）
- 检查 writeIndex 溢出（`Math.floorMod` 替代 `%`）
- 检查时间尺度混淆（游戏时间 vs 系统时间）

**资源泄漏级**：
- 检查 Map/Queue 无限增长（添加 TTL 清理或上限）
- 检查 `Deflater`/`RandomAccessFile`/`FileChannel` 未在 finally 中关闭
- 检查 UUID 注册后未注销

**并发竞态级**：
- 检查 `volatile boolean` 初始化标志（改为 `AtomicBoolean.compareAndSet`）
- 检查异步遍历 Bukkit 集合（先快照为 ArrayList）
- 检查 `getLocation()` 多次调用竞态（调用一次存入局部变量）
- 检查 `.equals()` 模式 NPE（改为 `Objects.equals()`）

### 3. Rust JNI 安全审查

- 所有 JNI 入口用 `catch_unwind(AssertUnwindSafe(...))` 包装
- `#[no_mangle]` → `#[unsafe(no_mangle)]`（edition 2024）
- `unsafe fn` 内部显式 `unsafe` 块
- `checked_mul` 防止长度溢出
- 负数实体数/null 指针/DirectByteBuffer 容量校验
- EPSILON=1e-6 浮点比较防护

### 4. 验证

```bash
# Java 编译
./gradlew :mili-server:compileJava

# Rust clippy（必须 0 warning）
cd mili-rust/src/rust && cargo clippy --release

# Rust 测试（28 tests passed）
cargo test --release
```

### 5. 修复标记

所有修复使用 `// Mili start - fix:` / `// Mili end` 注释标记。

### 6. 免构建静态验证（改动后**先跑这个**，比等编译快得多）

`applyAllPatches` + `compileJava` 代价高；下面的手段可在**无 `folia-server` 源码**时独立验证。

**6a. 纯语法检查**（`JavacTask.parse()`，不做符号解析 → 缺依赖零噪声）：

```bash
JH="C:/Users/Administrator/Downloads/jdk-25_windows-x64_bin/jdk-25.0.4/bin"
cd "E:/Program Files/Tencent/AndrowsData/Mili"
"$JH/javac.exe" -d build/parsecheck scripts/ParseCheck.java
"$JH/java.exe" -cp build/parsecheck ParseCheck \
  "mili-server/src/main/java/fun/bm/mili" "mili-api" "luminol-api" "mili-rust/src/main/java"
# 期望：PARSED_FILES=242 SYNTAX_DIAGS=0  ，退出码 1 = 有语法错误
```

> **路径陷阱**：Windows 版 `javac.exe` 只认 Windows 路径；另外 `Write` 工具会把 `/tmp/x`
> 解析成**驱动器相对**路径（落到 `E:/tmp/x`），而 git-bash 的 `/tmp` 指
> `C:/Users/Administrator/AppData/Local/Temp` —— 两者不是同一个地方，别混用。

**6b. 补丁 hunk 头自洽性**（`minecraft-patches/features/*.patch` 改动后必跑）：

```bash
python scripts/check_patch_hunks.py
# 期望：OK: 153 patch file(s) checked, all hunk headers are consistent
```

该脚本认 paperweight 的 **`_` 行号占位语法**（`@@ -15,7 +_,8 @@` = 新侧行号同旧侧），
并覆盖 `mili-server/build.gradle.kts.patch`。**`git apply` 不认 `_` 语法**，故不能用它验证。

**6c. 残留 legacy scheduler 扫描**：在 `mili-server/src/main/java/fun/bm/mili` 下搜
`BukkitRunnable` / `Bukkit.getScheduler()` / `runTaskTimer(` / `runTaskLater(` / `runTaskAsynchronously(`
→ **命中必须全部是注释**。

### 7. Folia 迁移硬规则（本项目最先踩的坑）

1. **Folia 运行期禁用 legacy 调度器**：`Bukkit.getScheduler()` 与 `BukkitRunnable` 调用即抛
   `UnsupportedOperationException`。必须改用 `Bukkit.getGlobalRegionScheduler()` /
   `getRegionScheduler()` / `entity.getScheduler()`。**任何 `.runTaskTimer` 残留都是运行期炸弹**，
   即便编译通过、单测通过。
2. **不要从全局线程突变区域状态**：`entity.remove()`、`chunk.unload()`、视距调整必须派发到
   **拥有该目标的** region / entity 调度器。通用模式：
   **检测/筛选留全局线程（只读快照）→ 突变派发到 region 线程。**
3. **`EntityScheduler` 同名歧义**：`io.papermc.paper.threadedregions.scheduler.EntityScheduler`（Folia）
   与 `fun.bm.mili.scheduler.EntityScheduler`（Mili 自有）同名 → **删 import，用全限定名**。
4. **注册 Folia 任务需要 Plugin 实例**：Mili 是服务端核心、不是 Bukkit 插件，故用
   `org.leavesmc.leaves.plugin.MinecraftInternalPlugin.INSTANCE`（dummy Plugin，`isEnabled()` 恒 `true`）。
   ⚠️ 不要写 `getPluginManager().getPlugin("Mili")` —— **恒返回 `null`**（latent NPE）。
5. **Folia region 身份**：`RegionizedWorldData`（final class）与 `TickRegions.TickRegionData`
   （另一个 final class）**互不继承** → 任何 `currentData == region.getData()` 或
   `instanceof RegionizedWorldData` 的判断都**恒 false**。Mili 统一以
   `ThreadedRegion`（`TickRegionScheduler.getCurrentRegion()`）为 region 身份。

### 8. 补丁工程（Milihyacinthus / paperweight）

- **补丁链是串联的**：后续补丁的 hunk 上下文必须是**前序补丁输出后**的文本。例：0125 的注入点
  紧邻 `features/0056` 改写过的行 → 0125 的 `index` 必须取 **0093 的输出哈希**
  （`f8d0af6473aa3e32408b85a868fb807d9da510e6`），否则 `applyAllPatches` 失败。
- **`todo/*.patch` 不参与 `applyAllPatches`**：新补丁想生效必须放进
  `mili-server/minecraft-patches/features/`。**排查"某功能从未执行"时，先确认它的补丁在不在
  `features/`** —— 这是 `MiliOptimizations.init()` 长期零调用点的根因。
- 新增补丁后 `git add`，与既有 124 个补丁保持同一跟踪状态。

### 9. 接线审计（「实现了但没接线」探针）——**本项目最高频的缺陷类型**

Mili 里最常见的不是写法错误，而是**子系统写好了、初始化了、有统计输出，但没有任何东西驱动它**。
这种缺陷**编译通过、启动无报错、配置打开也毫无效果**，只有查调用点才能发现。

**判据：任何"子系统入口"若全仓 0 调用点（含所有 `*.patch`），它就是死代码。**

```bash
cd "E:/Program Files/Tencent/AndrowsData/Mili"
# 对每个可疑组件，查它的驱动器/消费者是否有真实调用点
for sym in AsyncPathfinder WorkerRuntime RegionLoadMonitor RegionBalancer; do
  echo "=== $sym ==="
  grep -rn "$sym\." --include=*.java --include=*.patch \
    mili-server/src/main/java mili-server/minecraft-patches mili-api \
    | grep -v "src/main/java/fun/bm/mili/scheduler/$sym.java" \
    | grep -v "src/main/java/fun/bm/mili/utils/$sym.java"
done
```

**必查清单（截至 2026-09-26 已知为死代码，见计划文件 §9.2）**：

| 组件 | 死因 |
| --- | --- |
| `AsyncPathfinder.findPathAsync/applyPath` | 0 调用点 → 快照寻路整条死 |
| `WorkerRuntime.submitPure` | 唯一调用者是上面 | 
| `RegionBalancer.submitAndWait` | 0 调用点 → `RegionLoadMonitor` 收不到样本 |
| → 连锁 | `SmartRegionManager`/`AdaptiveTPSManager` 读到空数据，统计恒 0 |

**两个必须避开的反模式**：

1. **不要把部分数据喂给期待全量数据的消费者来"解饿"。** 例：`onRegionTick` 的 `elapsed` 只是
   一次 drain 的耗时，而 `RegionLoadMonitor.afterTick` 期待**整个 tick 的总耗时**。喂进去会让
   `loadFactor` 系统性偏低 → `isHighLoad` 永不成立 → 下游**基于错数据决策**。
   **"用错数据让死代码假装活着"比留着死代码更危险。**
   （M4 已用 §10 的方法**确证**该语义，并写死进代码 javadoc。）
2. **不要把 `todo/*.patch` 当成"已启用"。** 见 §8：只有 `features/` 下的补丁参与 `applyAllPatches`。
3. **不要据"配置项存在"推断它生效。** 实测本项目有整类**死配置**：`RegionBalancerConfig.threadPoolSize`
   长期零调用者（M4 已激活）、`AsyncPathfindingConfig.threadCount` 的语义被无关子系统事实抢占
   （M4 已纠正）。判定方法：`grep` 该字段在 `mili-server/src/main/java` 下的使用点。

**排查"某功能明明配了却不生效"的标准顺序**：
① 它的补丁在 `features/` 吗？→ ② 它的 init 有调用点吗？→ ③ 它的驱动器有调用点吗？→ ④ 驱动它的数据语义匹配吗？

**⚠️ 第三方/外部生成的补丁，hunk 自洽 ≠ 可以采信**（0125 教训）：

外部模型生成的补丁可能把注入点选在**消费者前置状态不满足**的位置 —— 0125 把
`MiliOptimizations.init()` 注入在 `loadConfigFiles()` 之后（世界尚未加载），导致
`VillagerOptimizer` 的 `Bukkit.getWorlds()` 首扫恒空。注入点必须用 §10 的
"从消费者反推"法独立验证。**优先方案**：用仓库里已验证、必被周期调用的自有钩子做自举
（0111 的 `FoliaSchedulerAdapter.isRunning()` 每 region tick 调用 → 世界加载后必达），
而不是新造补丁；自举动作要 CAS 幂等 + 派发到全局线程（不在 region 线程内联做全局遍历）。

**⚠️ 零调用点探针的两个已知假阳性**（M4 实测踩到，别再误报）：

1. **间接调用链**：`AsyncKeepaliveManager.start()` 看似零调用者，但 `register()` 内部**幂等地**调 `start()`，
   而 `register()` 由补丁 0118 在玩家连接时调用 → **接线完整**。
   判据：不要只 grep 目标方法，要连同它的**同类方法**（`register` / `init` / `onEnable`）一起 grep 并读进去。
2. **故意惰性的配置**：`mili-server/build.gradle.kts` 的 `patchesDir = mili-server/folia-patches` 指向
   **不存在**的目录，但这是**故意的**（原因写在 `:53-71` 注释里）：fork 已通过 paperweight 派生的
   "minecraft" patchDir 应用了整套补丁，重复注册会让 0001 vs 0056 的上下文冲突**二次触发**。
   判据：**先读周围注释，再判定为缺陷**——本项目有不少"看起来像 bug 的有意设计"。

### 10. 契约语义判定法（从消费者反推）—— M4 已用此法确证一条

给别人写数据前，先问**消费者拿它做什么**，而不是问字段名叫什么、注释怎么写（注释常常是错的或含糊的）。

**案例（M4 已确证）**：`RegionLoadMonitor.afterTick(region, elapsedNanos)` 的 `elapsedNanos` 该是
"整个 region tick 总耗时"还是"Mili 的工作时间"？反推三个消费者：

| 消费者 | 用法 | 推出的语义 |
| --- | --- | --- |
| `AdaptiveTPSManager:55-59` | `adjusted = 50ms * (1 + loadFactor*0.5)` → 写入 `TickRegionScheduler.TIME_BETWEEN_TICKS` | **必须是整 tick 耗时**；否则"Mili 队列忙"会直接压低全服 TPS |
| `RegionBalancerConfig` 阈值 | 默认 `low-load=2ms` / `high-load=20ms` | MSPT 尺度（20ms = 一个 tick 的 40%） |
| `SmartRegionManager` | `severelyUnderloaded = loadFactor < 0.1`、`isUnderloaded = isLowLoad() && loadFactor < 0.15` | 整 tick 判断（"这个 region 有多闲"） |

三条独立证据一致 → 语义定死 = **MSPT per region**。

**判据**：若某个候选语义会让消费者做出**方向性错误**的动作（不只是数值偏差），它就被排除。
上例中"喂 Mili 工作时间"被排除，因为它会让服务器**因为一个无关队列的繁忙而主动降低自己的 tick 速率**。

## 质量门

- Java `BUILD SUCCESSFUL`
- Rust `cargo clippy --release` — 0 error, 0 warning
- Rust `cargo test --release` — 28 passed, 0 failed

## 异常处理

- 编译错误：检查 JDK 版本是否为 25（不是 21）
- Rust edition 2024 编译错误：检查 `#[unsafe(no_mangle)]` 语法
- `catch_unwind` 编译错误：需用 `AssertUnwindSafe` 包装 `JNIEnv`
- `applyAllPatches` 报 hunk 失败：先跑 `scripts/check_patch_hunks.py` 排除头计数错误，再检查
  该补丁的上下文是否被**更晚的补丁**改写过（需 rebase 到其输出状态）
- `patch does not apply` 且补丁头没问题：`build.gradle.kts.base` 可能已陈旧（已知缺口）
- 运行期抛 `UnsupportedOperationException` 且与调度相关：搜 legacy scheduler 残留（见 §7.1）

## 维护记录

- 版本 0.2 — 适配 Mili 26.2 分支，JDK 25，Rust edition 2024
- 版本 0.3 — 增补：免构建静态验证（§6）、Folia 迁移硬规则（§7）、补丁工程规则（§8）
- 版本 0.4 — 增补「接线审计」探针（§9）：本项目最高频缺陷类型是"实现了但没接线"，
  编译通过且不报错，只有查调用点能发现
- 版本 0.5 — 增补「契约语义判定法」（§10，从消费者反推语义）+ §9 两条新反模式
- 版本 0.6 — §9 新增反模式：外部生成的补丁需审计注入点语义（0125 教训）；自举优先用已验证的周期钩子
  （死配置、部分数据解饿）；理由：M4 用此法确证了 `RegionLoadMonitor` 的 MSPT 语义
