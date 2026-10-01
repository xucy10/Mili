# Mili 架构方案：生电保真 × Rust 计算层 × 扩展框架

> 状态：**Phase 0（骨架）已落地** · 维护约定：架构决策变更须同步更新本文档
>
> 与 `WIKI.md` 的分工：WIKI 是功能速查表（"有什么"），本文档是设计决策与路线图（"为什么"与"往哪走"）。两者冲突时以代码为准，并向本文档登记漂移。

---

## 1. 文档定位与阅读指南

| 读者 | 建议章节 |
|---|---|
| 新接手的贡献者 | §2 现状审计 → §3 目标架构 → §8 路线图 |
| 生电服服主 | §4.1 分层开关、§4.2 行为审计矩阵 |
| Rust / 性能贡献者 | §5 Rust 计算层、§7 安全与并发、附录 A/B |
| 想写 Mili 扩展的开发者 | §6 扩展框架、附录 B（C ABI 头文件） |

术语：**生电** = 生存电学/技术生存（红石电路、复制机、大规模机器）；**QC** = 准连通性（Quasi-Connectivity）；**TSM** = TechnicalSurvivalMode 预设；**rustd** = Mili 常驻 Rust 守护进程。

---

## 2. 现状审计（Phase 0 时点）

### 2.1 继承链与补丁体系

```
Minecraft 1.21.11 → Paper → Folia（git submodule）→ Mili
```

- Luminol 优化已内联合并，`luminol-*` 目录为历史残留参考，不再使用
- 补丁目录：`mili-server/minecraft-patches/features/`（NMS 层，最新 0124）、`mili-server/paper-patches/features/`、`mili-server/lophine-patches/features/`（残留 1 个 rebrand）、`mili-api/paper-patches/features/`；`todo/` 与 `unpatched/` 不参与构建
- **铁律**：补丁只做最小 hook，实现体放 `mili-server/src/main/java/fun/bm/mili/`（手写源码，直接属于仓库、无需 rebuildPatches）；配置统一走 `mili_config.toml`（`me.earthme.luminol.config.ConfigsInstance` 入口）

### 2.2 红石/生电现状

| 补丁 | 内容 | 配置开关 |
|---|---|---|
| 0104 | 羊毛漏斗计数器 + `/counter` | Leaves 配置 |
| 0107/0108/0112/0114 | 更新抑制防崩溃链（UpdateSuppressionException 捕获、CCE 路径、掉落保护、不回滚方块） | `UpdateSuppressionCrashFixConfig` |
| 0109 | 红石忽略向上更新（1.20.2 前行为） | `Mili.experiment.redstone.redstone-ignore-upwards-update` |
| 0110 | 即时方块更新器（`InstantNeighborUpdater` 替换 `CollectingNeighborUpdater`） | `instant-block-updater` |
| 0111 | 还原 Paper 对活板门的改动 | — |
| 0113 | 旧方块移除语义（1.21 前 `onRemove`） | `old-block-remove-behaviour` |
| 0121 | 红石引擎三选一：Vanilla / Eigencraft / Alternate Current（`RedStoneWireBlock.shouldUseAlternateCurrent`） | Carpet `fastRedstoneDust` / Paper world config |
| 0119 | Carpet 规则包（TNT 引爆动量、fuse 标准化、`totallyNoBlockUpdate` 等中心短路） | Carpet 兼容层 |
| 0123 | TechnicalSurvivalMode：`maxTntTicksPerTick` 放宽到 2000、水中 TNT 不伤物品等 | `TechnicalSurvivalModeConfig` |

并发模型：Folia 区域化 tick 下，每个 `RegionizedWorldData` 持有独立的 AC `WireHandler` 与 `neighborUpdater`；跨区事件走 `CrossRegionHelper`（含 `RedstoneSignal` 队列）。

观测：`/redstone-stats`（活塞/更新/budTrigger 统计）、`RedstoneDirtyTracker`（脏更新合并）、`/counter`。

### 2.3 Rust 现状（两套体系）

**JNI 路径（实际生效）**：`mili-rust` 产出 cdylib（`mili_optimizer.dll/.so/.dylib`），Java 从 classpath `/rust/` 提取后 `System.load`。

| 模块 | 状态 | 接入点 |
|---|---|---|
| `config.rs`（TOML 配置引擎，1202 行） | 生产运行中 | `ConfigsInstance.java` L207 → `TomlConfigData`，每次启动/重载配置都经过 Rust `toml_edit` |
| `entity_cull.rs`（批量实体剔除） | 已接入、默认关闭 | 补丁 0122（`ChunkMap.newTrackerTick` 批量剔除 + `ServerEntity.sendChanges` 跳过）+ 0123 修正 |
| `frustum.rs`（视锥体数学） | 部分使用 | 批量接口未导出 JNI |
| `jni_bridge.rs`（JNI 导出层） | — | 命名 `Java_fun_bm_mili_rust_RustBridge_<method>`，DirectByteBuffer 零拷贝 |

**子进程路径（死代码）**：`org.mili.rust.RustOptimizer`（7 命令、argv 传参、纯 Java fallback）——零调用者、无 bin target、期望的 `build/rust/optimizer` 无构建任务产出。**Phase 4 删除**，其"后台任务"职责由 rustd（§5.3）继承。

构建流水线（成熟）：`addRustTargets`（5 平台）→ `buildRustBinariesAll`（Linux x64 强制 cargo-zigbuild + GLIBC 2.28 校验）→ `stageRustBinary`（`build/rust/` 规范命名）→ jar 打包 `rust/` 目录。

### 2.4 缺陷清单（Phase 1 修复依据）

| # | 缺陷 | 级别 | 复现路径 |
|---|---|---|---|
| D1 | `TomlConfigData` 无降级：`RustBridge.load()` 失败抛 `UnsatisfiedLinkError` 直接炸配置加载 → **Rust 库缺失 = 服务器无法启动**（Javadoc L29 承诺的 fallback 未实现） | **P0** | 移除 jar 内 `rust/` 目录后启动 |
| D2 | `CrossRegionHelper.submitRedstoneCrossRegion`（L185/L188）两次取 `level.getCurrentWorldData()`，`srcRegion == tgtRegion` 恒真 → **跨区红石事件永不提交**，跨区红石实际为零实现 | P1 | 任意跨区红石事件 |
| D3 | `nativeInit` JNI 导出为空壳：无 rayon 线程池预热、无 ABI 版本握手 | P1 | — |
| D4 | 实体剔除视锥体硬编码 70° FOV 近似（服务端无真实相机）；`RESULT_CULLED(1)` 永不产生（0122 的 case 1 为死代码）；`EntityCullHelper.resetCulledFlags` 无调用者（剔除失败时上一 tick 冻结状态可能滞留） | P1 | 开启 `RayTrackingEntityTrackerConfig.enabled` |
| D5 | `frustum.rs` 的 `batch_cull_spheres/batch_cull_aabbs/from_matrix` 无 JNI 导出 | P2 | — |
| D6 | rayon 线程池与 Folia 区域线程交互未评估（>64 实体时在 region 线程内 fork/join，多区域并发可能过度订阅） | P2 | 压测 |
| D7 | `WIKI.md` §7.3 与补丁索引漂移（描述的 chunk/varint/nbt/protocol/scheduler.rs 不存在；RustOptimizer 入口为死代码） | P2 | 阅读文档 |
| D8 | `todo/0068-Restore-Vanilla-ender-pearl-behavior.patch` 待转正评审 | P2 | — |
| D9 | **线程资源无治理**：约 22 处自建线程池/线程分散在各组件；`MiliOptimizations.shutdown()` 仅收编 8 个，`AdaptiveTPSManager`/`AsyncKeepaliveManager`/`CrossRegionHelper` 等无关闭路径；`dev.tr7zw.entityculling.CullTask` 用无上界 `newCachedThreadPool`；`DAGScheduler` 的 fallback 分支 per-task `new Thread` 且本体零调用者；周期任务裸用 JDK `scheduleAtFixedRate`（异常即被静默永久取消） | P1 | 长时间运行后线程数单调增长；巡检任务无声失效 |

### 2.5 生电专项缺口

- **复制机类行为零专项补丁**：TNT/沙子/铁轨/地毯复制机的"保留 vs 修复"完全随上游（Paper 已修 → 行为丢失），无 Mili 侧还原路径
- **QC/BUD 零审计**：准连通性、BUD 触发、0tick 作物在 1.21.11 + Folia 链上的实际行为未逐项核对
- **跨区红石无确定性保证**：仅有（失效的）事件队列，无时序语义
- 补丁编号文档漂移（D7）使贡献者难以定位行为归属

---

## 3. 目标架构总览

```
┌───────────────────────────────────────────────────────────────────┐
│ 扩展框架层                                                        │
│   内置扩展：MiliExtension trait + ExtensionRegistry（编译进主库）  │
│   外部扩展：C ABI v1 动态库，经 rustd 隔离加载                     │
├───────────────────────────────────────────────────────────────────┤
│ Rust 计算层                                                       │
│   热路径：JNI 零拷贝批量（红石图纯数值 / NBT / 协议成本 / 剔除）   │
│   后台：  rustd 常驻子进程（重型/易崩任务，崩溃隔离重启）          │
├───────────────────────────────────────────────────────────────────┤
│ 生电保真层                                                        │
│   L0 默认（Paper/Folia）< L1 预设（TSM）< L2 细粒度规则            │
├───────────────────────────────────────────────────────────────────┤
│ 共用底座                                                          │
│   mili_config.toml + ConfigsInstance · 观测体系 · 5 平台交叉编译   │
└───────────────────────────────────────────────────────────────────┘
```

**设计公理**

1. **默认行为 = Paper/Folia**：所有还原/保留必须显式开启，正向兼容优先
2. **一切 Rust 能力可降级**：每个 JNI 入口必须定义"Rust 不可用时的 Java 等价路径"（D1 即反面教材）
3. **补丁最小 hook + 实现体在 `fun.bm.mili`**：行为开关统一查询 `VanillaToggleRegistry.isEnabled(id)`，禁止补丁直读散装 config 字段
4. **红线**：红石**语义判定**（何时触发更新、方块状态如何变化）永远留在 Java 原版代码内；Rust 只接收扁平数值做纯计算（拓扑/数值/校验），禁止在 Rust 侧持有 BlockState 或任何 Mojang 依赖

---

## 4. 生电子系统设计

### 4.1 分层开关体系

```
L0 默认层      跟随 Paper/Folia，零改动（所有 VanillaToggle 取 defaultValue）
L1 预设层      TechnicalSurvivalMode=true → 应用 technical_survival 预设
               （VanillaTogglePresets.resolve；叠加现有 maxTntTicksPerTick=2000 等）
L2 细粒度层    mili_config.toml [vanilla] 段 → setExplicitOverride
               规则 id 沿用 Carpet 风格命名（tntDupingFix / sandDupingFix / …），
               与 fun.bm.mili.carpet 兼容层双向映射
```

- **仲裁**：L2 显式配置 > L1 预设 > 默认值（`VanillaToggleRegistry.isEnabled` 三段回退）
- **实现**（Phase 0 骨架已建）：`fun/bm/mili/vanilla/` 下 `VanillaToggle`（规则定义）、`VanillaToggleRegistry`（三层注册表 + volatile 快照）、`VanillaTogglePresets`（预设集 + 占位规则）
- **接线**（Phase 1）：TechnicalSurvivalModeConfig / GeneralCompatConfig 加载完成后注册规则并应用预设；新补丁在行为点插入 `isEnabled` 查询

### 4.2 原版行为审计矩阵

> 策略取值：**保留**（与原版 1:1，含可利用行为）/ **修复**（按上游）/ **可选**（默认修复 + 规则还原）。现状栏为 Phase 0 审计结论，随补丁落地更新。

| 行为项 | 现状 | 策略 | 所需补丁 |
|---|---|---|---|
| TNT 复制机（活塞 BUD 推 TNT） | Paper 已修，无还原路径 | 可选（TSM 默认保留） | 0125 |
| 沙子/混凝土粉末复制机 | 同上 | 可选 | 0125 |
| 铁轨复制机 | 同上 | 可选 | 0125 |
| 地毯复制机 | 同上 | 可选 | 0125 |
| 0tick 作物催熟 | 待审计（Paper 现状未核对） | 可选 | 0126 |
| 活塞准连通性 QC | 原版语义，上游未移除（待确认无漂移） | 保留 | 审计即可 |
| BUD 触发 | 同上 | 保留 | 审计即可 |
| 更新抑制装置 | 0107/0108/0112/0114 已保护（防崩服不拦截装置本身） | 保留 | 已完成 |
| 即时方块更新器 | 0110 已实现 | 可选 | 已完成 |
| 红石引擎 | 0121 三选一 | 可选 | 已完成 |
| 红石忽略向上更新 | 0109 已实现 | 可选 | 已完成 |
| 旧方块移除语义 | 0113 已实现 | 可选 | 已完成 |
| TNT fuse/动量标准化 | 0119 Carpet 规则 | 可选 | 已完成 |
| 末影珍珠原版行为 | `todo/0068` 搁置中 | 保留 | 0143+（转正评审） |
| 活塞时序（1gt 脉冲、事件顺序） | 待审计（跨区场景重点） | 保留 | 0136+（随 §4.3） |

### 4.3 跨区红石确定性设计（v2）

现状：`CrossRegionHelper` 事件队列 + D2 bug（永不提交）。目标模型：

1. **边界感知**：方块更新跨出区域边界时，源区域记录 `(pos, neighborPos, dir, gameTime, seq)`；seq 为源区域内单调递增序号
2. **目标区域重放**：目标区域在下一个自身 tick 开头，按 `(gameTime, seq)` 排序重放队列中的事件
3. **确定性**：同一对相邻区域的注入顺序恒定；AC WireHandler 状态机因此可复现
4. **防崩优先**：队列超限丢弃 + 5s 抑制告警（沿用现有 `eventsDropped` 机制），宁可丢更新不可阻塞 tick
5. **修复 D2**：目标区域不能靠 `level.getCurrentWorldData()`（那是当前线程的区域），需经 `ThreadedRegionizer` 按 neighborPos 解析所属区域

Phase 3 落地（补丁 0136+），验收标准见 §8。

> **承载者已就绪（Phase 1.5）**：波次屏障骨架 `RegionTickDag` 已落地并接线到
> `BalancerSchedulerThreadPool`，语义按"抗卡死"实现（见 §7.3）。依赖边由
> `declareDependency(upstream, downstream)` 声明，因此本机制落地时只需在本节的重放逻辑中
> 按"存在 A→B 的未消费跨区事件"声明边即可，无需再动调度器。

### 4.4 观测工具规划

- `/redstone-stats` 扩展项：WireHandler 命中数、neighborUpdater 队列深度、跨区事件吞吐/丢弃数
- `/mili toggles`：实时输出 `VanillaToggleRegistry.snapshot()`（L2 覆盖标记 + 预设来源）
- `/mili-threads`：线程治理观测门面 —— 池快照、周期任务健康度（runs/fails/耗时）、
  tick 间隔归属（谁在控制）、跨 region 依赖图，以及**未纳管线程审计**。
  最后一节既是迁移进度条，也是防回潮的护栏（见 §7.1）
- Rust 加速的红石 profiler（Phase 3，经 rustd 离线分析，不占 tick）

---

## 5. Rust 计算层设计

### 5.1 统一 JNI 桥

- **模块化命名空间**：新 JNI 导出按 `Java_fun_bm_mili_rust_RustBridge_<module>_<method>` 命名（现有方法保留兼容别名）
- **nativeInit 实装**（修 D3）：rayon 线程池预热 + 返回 ABI 版本号，Java 侧启动时握手，不匹配即禁用该能力并告警
- **降级铁律**：每个 JNI 入口在 Java 侧定义纯 Java 等价路径，`RustBridge.isLoaded() == false` 时自动走降级；D1（TomlConfigData）是必须优先修复的反面教材：
  > **D1 修复方案**：构造函数 try-catch `RustBridge.load()`，失败置 `nativeAvailable=false` 并 WARN 一次；`load()/save()` 走内存 map + 最小 TOML 序列化（仅覆盖服务端自身配置所需子集）

### 5.2 热路径模块规划（JNI，零拷贝批量）

| 模块 | 数据形态 | 优先级 |
|---|---|---|
| 实体剔除修复集（D4/D5：真视锥矩阵、CULLED 语义、resetCulledFlags 接线、frustum 批量导出） | DirectByteBuffer | Phase 1 |
| NBT 流式扫描（不建树、零分配遍历/校验，WIKI §7.3 原规划复活） | byte[]/DirectByteBuffer | Phase 3 |
| 协议包合并成本计算（继承 RustOptimizer.mergePacketCost 语义） | long[] | Phase 3 |
| 红石图纯数值计算（配合 RedstoneDirtyTracker 的批量语义：wire 强度拓扑求解建议、更新批次排序） | int[] 索引图 | Phase 3 |

**红线重申**：红石语义判定留 Java；Rust 拿到的是扁平数值（节点表 + 邻接表），算完还给 Java 决策。Phase 3 验收含红线 grep 审计（Rust 侧零 Mojang 符号）。

### 5.3 常驻子进程 rustd

- **选型**：stdio 管道（ProcessBuilder 天然接管，无端口/防火墙/权限面，跨平台一致）；代价 = stdout 独占协议帧，日志一律 stderr
- **帧协议 v1**：见附录 A（规范源 `mili-rust/src/rust/src/proto.rs`，Java 镜像 `DaemonProtocol`）
- **生命周期**：握手（`--protocol-version` 参数 + PING/PONG 版本回显）→ 帧循环；心跳 30s × 3 次未应答判死；指数退避重启 1s/2s/4s…上限 60s；连续 5 次失败 → `DAEMON_FAILED_DISABLED`（仅 JNI 通道，服务器继续运行）
- **状态机**：`RustRuntime.State`（Phase 0 骨架已建，Phase 1 接线到 `MiliOptimizations` 启动序列）
- **Phase 1 构建接入**：`mili-rust/build.gradle.kts` 增加 rustd bin 的 cargo build + staging + jar 打包（当前流水线全走 `--lib`，bin target 已存在于 Cargo.toml，天然不影响现有构建）

### 5.4 验证口径

- 本机（8G 内存约束）：**只跑 `cargo test`，不做 `cargo build`**（链接与交叉编译推迟 CI）
- Phase 0 已验：`cargo test` 45/45 全绿（协议 round-trip/超长拒绝/截断、注册表生命周期、panic 隔离与阈值卸载、ping/pong、shutdown）
- CI：`buildRustBinariesAll` 全平台 + zigbuild/GLIBC 2.28 校验自动拦截链接问题

---

## 6. 扩展框架设计

### 6.1 内置扩展

```rust
pub trait MiliExtension {
    fn name(&self) -> &str;                       // 注册表主键，全局唯一
    fn version(&self) -> &str;
    fn on_init(&mut self) -> Result<(), String>;  // 失败拒绝注册
    fn on_tick(&mut self, tick: u64);             // 主线程串行
    fn on_shutdown(&mut self);
}
```

`ExtensionRegistry`：注册 = init + 插入；注销 = 移除 + shutdown；`tick_all` 单扩展 panic 隔离，连续 3 次 panic 自动卸载（`PanicGuard` 阈值）。

### 6.2 外部扩展 C ABI v1

**选型：手写最小 C ABI，否决 abi_stable/stabby。** 理由：扩展面仅"1 个描述符函数 + 3 个回调指针"，手写一周内可控；第三方库的依赖树会拉高 GLIBC 符号面，与 zigbuild + GLIBC 2.28 铁律相抵；审核成本接近零。

```rust
#[repr(C)]
pub struct MiliExtensionV1 {
    pub abi_version: u32,                    // 必须 == 1
    pub name: *const c_char,                 // UTF-8，静态存储期
    pub version: *const c_char,
    pub on_init: Option<unsafe extern "C" fn() -> c_int>,   // 0 = 成功
    pub on_tick: Option<unsafe extern "C" fn(u64)>,
    pub on_shutdown: Option<unsafe extern "C" fn()>,
}
// 扩展库导出：mili_extension_descriptor() -> *const MiliExtensionV1
```

加载流程（rustd 内）：dlopen → 查找 `mili_extension_descriptor` → 调用得描述符指针 → `abi_version` 与字段校验 → 回调经 `catch_panic` 包装。完整规范见附录 B 与 `mili-rust/include/mili_extension.h`。

### 6.3 panic 边界与崩溃隔离

- 所有跨边界调用包 `catch_unwind`（`AssertUnwindSafe`）；`extern "C"` 回调内部禁止 unwind（UB）
- panic 计数超阈值 → 自动卸载 + stderr 告警
- **扩展加载经 rustd 而非 JVM 内 dlopen**：恶意/劣质扩展崩溃只死 rustd，Java 侧退避重启；JNI 通道只保留可信的内置模块

### 6.4 Java 门面

- `RustRuntime`：状态机 + JNI 可用性 + daemon 持有（Phase 0 骨架，零接线）
- `RustDaemonClient`：进程生命周期 + 帧协议客户端 + 心跳/退避（Phase 0 骨架）
- `ExtensionManager`：`extensions/` 目录扫描 + 经 rustd 的 `ext.load`（Phase 0 骨架；unload 命令 Phase 2 进协议 v1.1）

---

## 7. 安全与并发

| 主题 | 规则 |
|---|---|
| rayon × Folia 区域线程 | JNI 批量调用只在区域 tick 线程发起；rayon 闭包**不触碰任何 JVM 对象**（仅裸数值/直接内存）——与 entity_cull 零拷贝模式一致并固化为铁律；线程数上限待 Phase 1 压测定型（D6） |
| rustd 隔离 | 崩溃不传染 JVM；`destroyForcibly` 兜底；stderr 直通服务端日志 |
| 扩展安全 | 描述符字段校验（name 非空 UTF-8）；ABI 版本协商失败拒绝加载；可选扩展目录校验和（Phase 2） |
| 降级矩阵 | Rust 全缺：配置引擎走内存 TOML（D1 修复后）、剔除走纯 Java 短路、rustd 禁用、扩展全停 —— 服务器功能完整，仅性能回退 |

### 7.1 线程资源治理（自底向上）

背景见 D9。治理层 = `fun.bm.mili.scheduler` 包（`MiliScheduler` / `RecurringTask` / `Tier`），
配置落在 `[experiment.scheduler]`（§2.1 的铁律要求实现体不进补丁，故全部为基源码，不消耗补丁号）。

**职责边界（关键）**：治理层管**线程资源**，不管**调度算法**。谁先 tick、tick 间隔多少、是否追赶
属于 tick 仲裁层，二者通过 `Tier.TICK` 划界——tick 赛道只登记、不代创。

| Tier | 用途 | 可否共享 |
|---|---|---|
| `TICK` | Folia region tick 线程 | **否**，仅登记。线程身份硬约束，见 §7 |
| `CPU` | 寻路、批量数学 | 是 |
| `BLOCKING_IO` | 磁盘、区域文件刷盘、远端 HTTP | 是 |
| `BACKGROUND` | 低频巡检与周期任务 | 是 |
| `VIRTUAL` | 短生命周期批量任务（DAG 波次） | 仅经 `virtualExecutor(name, limit)` 的并发闸门 |

**三条取向**

1. **不碰 tick 线程身份** —— 带身份的线程不可池化，任何"用通用池跑 region tick"的方案都是错的
2. **一律 fail-open** —— 治理层关闭或超预算时降级为"照旧干活 + 告警"，绝不因簿记问题丢功能
   （与公理 2「一切能力可降级」同源）
3. **纳管 ≠ 改写** —— `adopt()` 只接手生命周期所有权，迁移可逐个组件进行、随时回滚

**已识别的取舍**：多组件共享定时池换来线程数收敛，代价是任务间会相互影响。CPU 重的任务应当
走 `namedPool` 或 `CPU` tier，不要塞进 `BACKGROUND`。迁移时若原实现用 `scheduleWithFixedDelay`
（如自动备份），必须经 `RecurringTask.startWithFixedDelay` 保留语义——换成 FixedRate 会在一次
慢任务结束后连续补触发，把偶发卡顿放大成排队风暴。

**共享池 vs 独占池的判据**（治理的目的是"可控"，不是"一律共享"）：

| 形态 | 适用 | 已用例 |
|---|---|---|
| 共享 `BACKGROUND` | 秒级、轻量的巡检 | MemoryOptimizer、AsyncKeepalive、MiliMetrics |
| 独占 `namedScheduledPool` | 周期 ≤250ms 的高频任务，或需特定线程优先级 | ChunkRegionBridge、MiliChunkSystem、SmartRegionManager |
| `adopt` 现有池 | 线程身份不能被替换 | `CullTask` |

> 隔离边界取 250ms 是刻意选择：宁可多一个空闲线程，也不让高频巡检与低频任务互相阻塞。
> 反正周期性任务对延迟抖动远比线程数敏感。

**线程身份陷阱（血泪）**：`TickThread.isTickThread()` 的实现是
`Thread.currentThread() instanceof TickThread`，而 MC 代码中的 `TickThread.ensureTickThread(...)`
依赖它。`CullTask` 的 worker 必须是
`ca.spottedleaf.moonrise.common.util.TickThread` 实例——**换成普通池线程会直接抛
`IllegalStateException`**。这类改动属于"看起来能优化、实际会炸"，
因此该池只改造了有界性与生命周期，线程类型原样保留。

同一族问题也出现在 `DAGScheduler`：其 `executeWave` 原为每任务新建一个线程
（虚拟线程路径无并发上限，平台降级路径更是每个任务一个 OS 线程）。现已改为经
`MiliScheduler.virtualExecutor("dag-wave", limit)` 的并发闸门取用，默认上限为 CPU 核心数，
可由 `Config.MAX_WAVE_CONCURRENCY` 覆写。**虚拟线程廉价，但廉价不等于免费**——每个挂起的
continuation 仍占栈空间，上万节点的波次同时起飞时内存压力并不比平台线程小多少。

### 7.2 tick 间隔仲裁（写入总线）

`TickRegionScheduler.TIME_BETWEEN_TICKS` 是一个全局字段。改造前有 **4 处直接写入**，
分布在 `AdaptiveTPSManager`（2 处）与 `TickDurationGovernor`（2 处），互斥关系靠
`RegionBalancer` 里一段**被复制了两次**的 if 分支维持；而 `AdaptiveTPSManager.setTickInterval`
是 public 的，任何调用者都能绕过这层约定。结果是多写者每秒互相覆盖，生效值随机抖动。

现由 `fun.bm.mili.scheduler.TickIntervalBus` 作为**唯一**写入者（已用 grep 确认全仓仅剩一处写入）：

| 权威等级 | 提案者 | 算法 |
|---|---|---|
| `GOVERNOR` (90) | `TickDurationGovernor` | PI 控制 + cpu 预算 / 队列深度 / 利用率三道硬上限 |
| `ADAPTIVE_TPS` (50) | `AdaptiveTPSManager` | 平均负载线性外推 |
| `FALLBACK` (10) | 预留 | 兜底与观测 |

**冲突消解规则**：① 高权威者生效 ② 同级取最近提出者 ③ 全部撤回后回到 50ms 基准
（不会把 tick 间隔留在最后一个非默认值上）④ **从未有人提案时不写入**，保持 Folia 原生行为。

**为什么选择"保留三方策略 + 收口"，而不是"砍到只剩 PI 控制器"**：后者会丢失能力且不可逆，
而收口只替换写入调用点，各家算法一行未动，随时可回退 —— 与公理 2「一切能力可降级」同源。
治理的目的从来不是统一思想，是让"谁在说了算"这件事**可见、可审、可回退**。

顺带修掉：`AdaptiveTPSManager.shutdown()` 此前**零调用者**，其采样线程随 JVM 泄漏；
该线程也已从裸 `Thread` + `sleep` 循环改为治理层周期任务。

### 7.3 跨 region 依赖图（波次屏障）

承接 §4.3 的跨区红石确定性需求，由 `fun.bm.mili.scheduler.RegionTickDag` 承载。

**为什么节点是 region 而不是 tick 子步骤**：单 region 的 tick 子阶段（实体/方块实体/流体）
共享同一份可变世界状态，本来就不能并行，且它们必须跑在 TickThreadRunner 上。跨 region 则不然 ——
每个 region 仍然在自己的 tick 线程上完整执行，只在**波次之间**加屏障，不触碰线程身份约束。

**屏障语义采用「抗卡死」**（明确选择，非默认）：

| | 严格时序方案 | 抗卡死方案（选用） |
|---|---|---|
| 某 region 卡住 | 全场等待，超时后放弃本轮 | **已就绪的继续下一波**，掉队者标记后不再阻塞下游 |
| 代价 | 一个卡死 region 拖慢所有 region | 本轮时序不严格 |
| 高负载下 | 最差：卡顿被放大 | 可控 |

掉队不会变成永久掉队：完成一次 tick 即清除标记并回归；连续掉队达
`dag-max-lag-streak` 次则打印告警，用于定位"哪个 region 在拖后腿"。

**门控形态是"软"的**：未就绪的 region 被降级到优先级队尾（复用
`BalancerSchedulerThreadPool.QUEUE_COMPARATOR` 的现有排序），而非硬阻塞队列。
理由是硬阻塞会让队首卡住整条队列，且 `poll` 返回 null 时等待逻辑面对已过期的 deadline
会立刻返回，退化成忙轮询；优先级降级则复用既有机制，既无死锁也无忙轮询。

**当前零生产影响**：依赖边需由调用方 `declareDependency(upstream, downstream)` 声明，
目前尚无调用者，因此所有 region 恒为 `READY`，行为与现状完全一致。
待 §4.3 的跨区红石重放机制接线后即可生效。

`MiliScheduler.auditUnmanagedThreads()` 扫描进程内所有 Mili 命名线程并剔除已纳管前缀，
既是迁移进度度量，也是防止回潮的护栏。运行期通过 `/mili-threads` 查看。

---

## 8. 分阶段路线图

| 阶段 | 内容 | 补丁号段 | 验收标准 |
|---|---|---|---|
| **Phase 0（已完成）** | 本文档 + WIKI §7.3 修正 + Rust 骨架（proto/extensions/rustd/ABI 头）+ Java 骨架（rust/runtime 4 类 + vanilla 4 文件） | 无 | cargo test 45/45；Java 编译验证推迟 CI；现有配置引擎行为零变化 |
| **Phase 1（已完成）** | D1 TomlConfigData 降级（NightConfig fallback）；D2 跨区红石三断点闭环修复（submitRedstoneCrossRegion 区域判定 + onRegionTick 消费实装 + enabled 默认 true）；D3 nativeInit 实装（rayon 预热）；D4 实体剔除修复集（FOV 配置化 115°/2.34 + default fail-open + resetCulledFlags 接线 + marker 盔甲架豁免 + 补丁 0125）；D5 frustum 批量导出（推迟 Phase 3）；VanillaToggleRegistry 接线 TechnicalSurvivalModeConfig；rustd 进 gradle 构建（build.gradle.kts + Cargo.toml bin）；RustRuntime 接线（RustRuntimeConfig.onLoaded）；MiliOptimizations 接线（补丁 0127）；命令 /mili-rust-status + /mili-toggles | 0125, 0127 | cargo test 45/45；补丁 check_patch_hunks 通过；Java 编译验证推迟 CI |
| **Phase 1.5 线程治理** | D9：治理层骨架（`fun.bm.mili.scheduler` + `[experiment.scheduler]`）；迁移后台与 IO 散装池；修复 `CullTask` 无界池与 `DAGScheduler` per-task `new Thread`；**tick 间隔写入总线 `TickIntervalBus`（§7.2）**；`DAGScheduler` 转跨 region 依赖图（承接 §4.3）；观测命令 `/mili-threads` | 无（全部落在基源码），仅打补丁到 NMS 层时按需占用 | `auditUnmanagedThreads()` 返回空；周期任务抛异常后仍继续；CullTask 线程数有界；**全仓 `TIME_BETWEEN_TICKS` 仅剩总线一处写入** |
| **Phase 2 扩展上线** | C ABI v1 冻结 + 发布头文件；unload 命令进协议 v1.1；示例扩展 1 个；`/mili rust status`、`/mili toggles` | 0132–0136（原标 0131–0135，0131 已被 /tps 占用故顺延） | 示例扩展人为 panic 后 JVM 存活、扩展自动卸载并告警 |
| **Phase 3 热路径 Rust 化** | NBT 流式扫描、协议合并成本、红石图纯数值计算、跨区红石 tickStamp 重放 v2（§4.3） | 0136–0142 | NBT 扫描基准 ≥2x、剔除零回归；双区域压测下跨区红石时序与单区一致；红线 grep 审计通过 |
| **Phase 4 收尾** | WIKI 全面重写消除漂移（D7）；todo/0068 末影珍珠转正（D8）；删除 RustOptimizer 死代码；性能基准报告归档 | 0143+ | 文档与代码逐项核对零漂移；死代码零引用 |

---

## 附录 A：帧协议 v1 全文

规范源：`mili-rust/src/rust/src/proto.rs`（含对应用断言测试）；Java 镜像：`fun/bm/mili/rust/runtime/DaemonProtocol.java`。

```
传输层      stdio（stdout 独占协议帧，日志一律 stderr）
帧格式      [u32 LE 长度][u8 op][payload]
            长度 = 1 + payload.len()（不含前缀自身），合法范围 1..=1+16MiB
操作码      0x01 PING   payload = u16 LE 协议版本
            0x02 PONG   payload 原样回显版本（应用层版本握手）
            0x10 EXEC   payload = UTF-8 命令串
            0x11 RESULT payload = [1B 状态码(0=ok,1=err)] + UTF-8 消息
            0x7F SHUTDOWN
启动参数    --protocol-version <n>（不匹配退出码 2）
退出码      0 = 正常（EOF/SHUTDOWN）；1 = IO 错误；2 = 参数/协议错误
骨架命令    echo <text> / ext.list / ext.load <path>
```

## 附录 B：C ABI v1 规范

规范源：`mili-rust/src/rust/src/extensions/abi.rs`；机器可读头文件：`mili-rust/include/mili_extension.h`（含完整 C 示例）。要点：

- 扩展为 C ABI 动态库（.dll/.so/.dylib），导出 `mili_extension_descriptor`（返回静态描述符指针的函数）
- `on_init`/`on_shutdown` 由 rustd 主线程调用；`on_tick` 主线程串行；回调内禁止 unwind 与 stdio
- 构建约定与宿主一致：Linux x86_64 GLIBC ≤ 2.28（zigbuild）、aarch64 Linux、Windows x86_64（gnu）、macOS x86_64/arm64
- 版本协商：`abi_version != 1` 拒绝加载；未来破坏性变更走 ABI v2 双注册

## 附录 C：Carpet 规则名对照表（节选）

| Carpet 规则 | Mili 归属 | 状态 |
|---|---|---|
| fastRedstoneDust | 0121 引擎选择 → VanillaToggleRegistry `fastRedstoneDust` | Mapped |
| instantBlockUpdaterReintroduced | 0110 → `instantBlockUpdaterReintroduced` | Mapped |
| yeetUpdateSuppressionCrash / amsUpdateSuppressionCrashFix | 0107 链 → `updateSuppressionCrashFix` | Mapped |
| totallyNoBlockUpdate | 0119 中心短路 | Mapped（语义收窄） |
| tntDupingFix | **Removed（原状态）** → Phase 1 起还原路径回归，映射 `tntDupingFix` | 复活中 |
| commandTick / tickCommandPermission / tickFreezeCommandToggleable | Carpet 兼容层 | Mapped |

> 完整映射以 `fun/bm/mili/carpet/config/modules/GeneralCompatConfig.java` 与 `docs/carpet-compat-status.md` 为准；Phase 1 接线后本表与 `VanillaTogglePresets` 同步更新。
