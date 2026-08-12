<div align="center">
  
  <img src="./docs/mili-logo.png" alt="Mili" width="160">

  
  # Mili（米粒） 

  **基于 Folia 的高性能 Minecraft 服务端核心，融合 Rust 原生加速与深度生电兼容**

  [中文](./README.md) | [English](./README_EN.md)

  ![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green)
  ![Java](https://img.shields.io/badge/JDK-21+-orange)
  ![Rust](https://img.shields.io/badge/Rust-stable-red)
  ![License](https://img.shields.io/badge/License-GPL--3.0-blue)
</div>

---

## 项目简介

Mili 是一个基于 **Paper → Folia** 三层 fork 链的 Minecraft 服务端核心，专为 **生电服 / 技术服** 场景设计。它在 Folia 区域多线程调度模型之上，提供了红石兼容性修复、原版行为开关、Rust 原生加速优化和完整的 Carpet/Leaves 协议兼容层。

### 继承链

```
Minecraft（原版）
  └── Paper（服务端框架）
        └── Folia（区域多线程调度）
              └── Mili（本项目）
```

> Mili 原为 Lophine/Luminol 的衍生分支，现已迁移为直接基于 Folia，将 Luminol 的优化源码内联合并。
> Luminol已在与canvas争议中删库

---

## 核心特性

### Rust 原生加速（`mili-rust`）

通过 JNI 桥接 Rust 编译的原生库（`mili_optimizer.dll` / `.so` / `.dylib`），对高频计算路径做零拷贝批量加速：

| 模块 | 功能 | 技术亮点 |
|------|------|----------|
| `config.rs` | TOML 配置文件读写 | `toml_edit` 保留注释与格式，JSON ↔ TOML 双向转换 |
| `entity_cull.rs` | 实体视锥剔除 | 6 平面 AABB frustum 测试，DirectByteBuffer 零拷贝，Rayon 并行 |
| `frustum.rs` | 视锥体构建与测试 | 从相机参数或投影矩阵构建，球体/AABB/点测试 |
| `jni_bridge.rs` | JNI 桥接层 | 批量处理 only，一次 JNI 调用处理全部实体 |

**零拷贝设计**：Java 侧将实体数据打包进 `DirectByteBuffer`，Rust 通过 `GetDirectBufferAddress` 直接读取内存地址，避免 JNI 边界的数据拷贝。每 tick 仅 M 次 JNI 调用（M = 观察者数量），而非 N×M 次（N = 实体数）。

### Folia 稳定性修复

- **Region Balancer**：共享线程池 + 优先级队列替代 Folia 每区域独占线程，动态负载均衡
- **Region Load Monitor**：无锁滑动窗口统计区域 tick 耗时
- **Adaptive TPS Manager**：根据实时负载动态调整 TPS
- **Cross-Region Helper**：类型化跨区事件队列（红石信号、实体伤害、方块通知等）
- **全局实体计数器**：按区域聚合 mob 数量，避免 O(entities) 扫描
- **崩溃修复**：`RegionizedWorldData` 空连接 NPE、已移除实体效果添加、`/save-all` 区域安全化

### 红石与生电兼容

从 Leaves/Lophine 移植并适配 Folia 的红石/生电修复：

- **更新抑制（Update Suppression）**：捕获 `UpdateSuppressionException` 防止服务端崩溃，保留掉落物，不回滚已放置方块
- **红石忽略向上更新**：恢复 1.20.1/1.19 的红石粉/中继器/比较器行为
- **即时方块更新器**：`InstantNeighborUpdater` 替换 `CollectingNeighborUpdater`
- **旧方块移除行为**：恢复 1.21 之前的 `onRemove` 语义
- **羊毛漏斗计数器**：通过羊毛颜色实现 hopper counter + `/counter` 命令

### Carpet / 协议兼容

| 协议 | 说明 |
|------|------|
| **Carpet** | 50+ 条 Carpet/TIS/AMS 规则映射，TPS/mobcaps/counter HUD 同步 |
| **TISCM** | `tiscm:network/v1`，MSPT 广播、握手 |
| **XaeroMap** | Xaero 地图通道支持 |
| **Jade** | Jade 服务端数据提供 |
| **Syncmatica** | Litematic 同步 |
| **Servux** | Servux 客户端服务 |
| **REI** | Roughly Enough Items 服务端协议 |
| **BBOR** | Bounding Box Outline Reloaded |
| **AppleSkin** | 饥饿/饱和度同步 |
| **Alternative Block Placement** | Accurate/Carpet/Litematica 放置协议 |

### 假玩家 / Bot 系统

从 Leaves 移植并适配 Folia，支持创建/管理/移除假玩家，假玩家常驻、背包操作、动作执行（攻击、破坏、钓鱼、跳跃、移动等），配套完整的 Kotlin 事件 API。

### ReplayMod 摄影师

支持创建 ReplayMod 摄影师实体进行录像，提供 `Photographer` / `PhotographerManager` API。

---

## 快速开始

### 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 构建工具链 |
| Rust | stable | 可选，用于编译原生优化库 |
| Git | 2.x | 需启用长路径支持（Windows） |

### 构建步骤

```bash
# 1. 克隆仓库
git clone https://github.com/xucy10/Mili.git
cd Mili

# 2. Windows 需启用长路径
git config --global core.longpaths true

# 3. 应用补丁（首次构建必须执行）
./gradlew applyAllPatches --no-configuration-cache --no-build-cache

# 4. 注入 Kotlin 支持
python scripts/inject_kotlin.py

# 5. 编译 Rust 原生库（可选，缺失时自动回退纯 Java）
./gradlew :mili-rust:stageRustBinary

# 6. 构建 Paperclip JAR
./gradlew :mili-server:createMojmapPaperclipJar
```

构建产物位于 `mili-server/build/libs/`：
- `mili-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar` — 可直接运行的 Paperclip JAR
- `mili_optimizer.dll` / `.so` / `.dylib` — Rust 原生优化库（打包进 JAR）

### Rust 单独编译与测试

```bash
cd mili-rust/src/rust
cargo build --release    # 编译
cargo test --release     # 运行单元测试（25 个）
```

---

## API 使用

### Gradle

```kotlin
repositories {
    maven {
        url = "https://repo.menthamc.org/repository/maven-public/"
    }
}

dependencies {
    compileOnly("fun.bm.mili:mili-api:1.21.11-R0.1-SNAPSHOT")
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
    <version>1.21.11-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

---

## 项目结构

```
Mili/
├── mili-api/                  # Mili API 模块
│   └── src/main/java/         #   Bot、Photographer、事件 API
├── mili-server/               # Mili 服务端核心
│   ├── minecraft-patches/     #   补丁文件（features/ + fixes/）
│   └── src/main/
│       ├── java/fun/bm/mili/  #   Java 源码（utils、carpet、protocol）
│       └── kotlin/fun/bm/mili/#   Kotlin 源码（config、command、utils）
├── mili-rust/                 # Rust 原生优化模块
│   ├── src/main/java/         #   JNI 桥接 Java 侧（RustBridge.java）
│   └── src/rust/src/          #   Rust 源码
│       ├── config.rs          #   TOML 配置读写
│       ├── entity_cull.rs     #   实体视锥剔除
│       ├── frustum.rs         #   视锥体构建与测试
│       ├── jni_bridge.rs      #   JNI 导出函数
│       └── lib.rs             #   crate 入口
├── docs/                      # 文档
│   ├── WIKI.md                #   中文 Wiki（完整功能索引）
│   ├── CONTRIBUTING.md        #   贡献指南
│   └── carpet-compat-status.md#   Carpet 规则兼容状态
├── build.gradle.kts           # 根构建脚本
└── gradle.properties          # 版本与上游 ref 配置
```

---

## 配置系统

Mili 提供两套 TOML 配置文件：

| 文件 | 包路径 | 说明 |
|------|--------|------|
| `mili_config.toml` | `fun.bm.mili.config.modules` | 主配置，涵盖游戏机制、实验功能、修复开关 |
| `mili_carpet_config.toml` | `fun.bm.mili.carpet.config.modules` | Carpet 兼容规则映射 |

配置分类：

| 类别 | 说明 | 代表模块 |
|------|------|----------|
| `function` | 游戏机制与实用功能 | `LanguageConfig`、`FakeplayerConfig`、`ContainerExpansionConfig` |
| `experiment` | 实验性性能/并发功能 | `RegionBalancerConfig`、`CrossRegionHelperConfig` |
| `fixes` | 崩溃修复 | `UpdateSuppressionCrashFixConfig` |
| `misc` | 杂项 | `AutoUpdateConfig` |
| `carpet` | Carpet 规则映射 | `CoreConfig`、`GeneralCompatConfig` |

完整配置项速查表见 [Wiki](docs/WIKI.md#10-配置速查表)。

---

## 补丁工作流

Mili 使用 **Hyacinthusweight**（基于 paperweight）补丁系统：

1. 在 `mili-server/src/minecraft/` 或 `mili-api/` 中修改代码
2. 提交变更：`git commit -m "描述"`
3. 重建补丁：`./gradlew rebuildAllServerPatches`
4. 提交补丁文件并推送

修改 `mili-server/src/minecraft/java/` 下的生成文件会被 `applyAllPatches` 覆盖，必须通过 `minecraft-patches/features/` 下的补丁文件修改。

详细流程见 [贡献指南](docs/CONTRIBUTING.md)。

---

## 贡献

欢迎 Pull Requests 与 Issue！请先阅读：

- [贡献指南（中文）](docs/CONTRIBUTING.md) | [Contributing (EN)](docs/CONTRIBUTING_EN.md)
- 报告问题时请提供完整日志、环境信息与复现步骤

---

## 相关链接

| 项目 | 链接 |
|------|------|
| Folia（直接上游） | https://github.com/PaperMC/Folia |
| Paper | https://github.com/PaperMC/Paper |
| LeavesMC（大量特性来源） | https://github.com/LeavesMC/Leaves |
| Lophine（原直接上游） | https://github.com/LophineLabs/Lophine 由原开发者继续开发 |
| Luminol | https://github.com/LuminolMC/Luminol 已删库 |
| Hyacinthusweight 补丁系统 | https://github.com/LuminolMC/Hyacinthusweight 已删库 | 

---

## 社区

欢迎各位
[Discord](https://discord.com/invite/BSa67dbvVf)

## 感谢

感谢所有贡献者与赞助方对项目的持续支持。若项目对您有帮助，请在 GitHub 上给我们一个 star

## 许可证

本项目遵循 GPL-3.0 许可证。
