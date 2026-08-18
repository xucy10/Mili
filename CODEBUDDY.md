# CODEBUDDY.md

This file provides guidance to CodeBuddy / AI code assistants when working with code in this repository.

## 项目概述

**Mili** 是直接基于 [Folia](https://github.com/PaperMC/Folia) 的 Minecraft 26.1.2 服务端核心，使用 Java 25 + Rust（edition 2024）构建。目标是在 Folia 并发调度环境下提供更稳定、可配置的服务器运行时。

**版本**：`26.1.2-R0.1-SNAPSHOT`
**构建工具**：Gradle 9.4.1（Kotlin DSL）+ Hyacinthusweight 补丁系统（121 个 feature 补丁）
**上游**：Folia `62dc0f2`（`foliaRef` in `gradle.properties`）

> Mili 原为 Lophine/Luminol 衍生分支，现已直接基于 Folia。Luminol 已删库。

---

## 常用命令

### 构建

```bash

# 首次构建（必须）
./gradlew applyAllPatches --no-configuration-cache --no-build-cache

# 构建 Paperclip JAR
./gradlew :mili-server:createMojmapPaperclipJar

# Rust 原生库
./gradlew :mili-rust:stageRustBinary
```

### 编译验证

```bash
# Java 编译
./gradlew :mili-server:compileJava

# Rust clippy + test
cd mili-rust/src/rust
cargo clippy --release   # 必须 0 warning
cargo test --release     # 28 tests
```

### 补丁管理

```bash
# 应用全部补丁
./gradlew applyAllPatches

# 修改源文件后重建补丁
./gradlew :mili-server:rebuildAllServerPatches
```

---

## 代码架构

### 模块结构

```
Mili/
├── mili-api/          # 对外公开 API（Bot、Photographer、事件）
├── mili-server/       # 服务器核心
│   ├── minecraft-patches/features/  # 121 个补丁文件
│   └── src/main/java/fun/bm/mili/   # Java 源码
├── mili-rust/         # Rust 原生优化模块
│   ├── src/main/java/ #   JNI Java 侧（RustBridge.java, TomlConfigData.java）
│   └── src/rust/src/  #   Rust 源码（4 个 .rs 文件）
├── folia-server/      # Folia 子模块（上游，不修改）
└── folia-api/         # Folia API（不修改）
```

### Java 包结构（`fun.bm.mili`）

| 包 | 说明 |
|---|------|
| `bridge` | 区块-区域桥接（ChunkRegionBridge） |
| `carpet` | Carpet 兼容层（规则同步、计算器兼容、生成优化兼容） |
| `chunk` | 区块系统（生命周期管理、异步处理、热度追踪、视距优化） |
| `command` | 命令系统（`/counter` 等） |
| `config` | 配置模块（function/experiment/optimizations/fixes/misc/carpet） |
| `metrics` | bStats 统计 |
| `portal` | 传送门管理（配对、原子写入、NPE 防护） |
| `protocol` | 协议兼容（CarpetLogger、TISCM） |
| `rust` | Rust JNI Java 侧工具类（RustSpan, RustCow, RustScope, RustResult, RustOption, RustArena） |
| `utils` | 工具类（区域调度、网络优化、内存管理、村民、并发数据结构等） |
| `villager` | 村民优化器 |

### Rust 模块（`mili-rust/src/rust/src/`）

| 文件 | 功能 | JNI 交互 |
|------|------|----------|
| `config.rs` | TOML 配置读写 | JSON string 批量传输 |
| `entity_cull.rs` | 实体视锥剔除 | DirectByteBuffer 零拷贝，Rayon 并行 |
| `frustum.rs` | 视锥体构建与测试 | 值传递（float 数组） |
| `jni_bridge.rs` | JNI 导出函数 | 批量处理，catch_unwind 防止 panic 传播 |

**交互方式**：JNI 原生库直接调用（`System.loadLibrary("mili_optimizer")`），非子进程通信。Rust 二进制不可用时自动回退纯 Java。

**edition 2024 约束**：
- `#[no_mangle]` → `#[unsafe(no_mangle)]`
- unsafe fn 内部必须显式 `unsafe` 块
- `catch_unwind` 需用 `AssertUnwindSafe` 包装 `JNIEnv`

### 继承链

```
Minecraft（原版）
  └── Paper（服务端框架）
        └── Folia（区域多线程调度）
              └── Mili（本项目）
```

---

## 关键配置文件

- **`gradle.properties`**：项目版本 `26.1.2-R0.1-SNAPSHOT`、MC 版本 `26.1.2`、`foliaRef=62dc0f2`、`weightVersion=2.0.15`
- **`mili-server/build.gradle.kts`**：服务器构建核心
- **`mili-rust/src/rust/Cargo.toml`**：Rust edition 2024，`panic=unwind` + `overflow-checks=true`

---

## 修复标记约定

所有代码修复使用 `// Mili start - fix:` / `// Mili end` 注释标记（Rust 侧同理）。

---

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 25+（路径：`C:\Users\Administrator\Downloads\jdk-25_windows-x64_bin\jdk-25.0.4`） |
| Rust | stable (edition 2024) |
| Git | 2.0+（Windows 启用长路径） |

构建时建议分配 >= 4GB 内存。
