# CODEBUDDY.md

This file provides guidance to CodeBuddy Code when working with code in this repository.

## 项目概述

**Mili** 是 [Lophine](https://github.com/LuminolMC/Lophine) 的一个衍生分支，基于 Java 21 + Kotlin + Rust 构建的 Minecraft 1.21.11 服务器软件。目标是在 Folia 并发调度环境下提供更稳定、可配置的服务器运行时。

**版本**：`1.21.11-R0.1-SNAPSHOT`  
**构建工具**：Gradle（Kotlin DSL）+ [Hyacinthusweight](https://github.com/LuminolMC/Hyacinthusweight) 补丁系统

---

## 常用命令

### 首次构建（必须先执行）

```bash
./gradlew applyAllPatches
```

### 构建产物

```bash
# 开发用（Mojang 映射，调试友好）
./gradlew :mili-server:createMojmapPaperclipJar

# 生产用（混淆映射）
./gradlew :mili-server:createReobfPaperclipJar

# Bundler JAR
./gradlew :mili-server:createMojmapBundlerJar
```

构建产物位于 `mili-server/build/libs/`。

### 运行服务器

```bash
# 直接运行（无需 JAR，开发调试用）
./gradlew :mili-server:runDevServer

# 从 JAR 运行
./gradlew :mili-server:runServer
```

### 测试

```bash
# 运行全部测试
./gradlew test

# 运行单模块测试
./gradlew :mili-server:test
./gradlew :mili-api:test
```

### 代码检查

```bash
./gradlew check
```

### 补丁管理

```bash
# 应用全部补丁
./gradlew applyAllPatches

# 修改源文件后重建补丁（提交前必须执行）
./gradlew :mili-server:rebuildPatches
./gradlew :mili-api:rebuildPatches
```

### Rust 组件

```bash
# 编译 Rust 优化器二进制文件
./gradlew :mili-rust:stageRustBinary
```

---

## 代码架构

### 模块结构

```
Mili
├── mili-api/          # 对外公开 API（插件开发者使用）
├── mili-server/       # 服务器核心实现（补丁 + 自定义逻辑）
├── mili-rust/         # Rust 性能优化模块（JNI/进程通信）
├── luminol-api/       # 上游 Luminol API（不直接修改）
├── luminol-server/    # 上游 Luminol 服务端（不直接修改）
├── folia-api/         # 上游 Folia API（不直接修改）
├── folia-server/      # 上游 Folia 服务端（不直接修改）
├── paper-api/         # 上游 Paper API（不直接修改）
└── paper-server/      # 上游 Paper 服务端（不直接修改）
```

### 继承链

Mili 基于多层 fork 构建，补丁依次叠加：

```
Minecraft（原版）
  └── Paper（基础服务端框架）
        └── Folia（多线程并发调度）
              └── Luminol（性能优化 fork）
                    └── Mili（本项目）
```

### 补丁系统

自定义代码通过补丁文件管理，位于：

- `mili-server/paper-patches/features/`（7 个补丁，对 Paper 层的修改）
- `mili-server/luminol-patches/features/`（5 个补丁，对 Luminol 层的修改）
- `mili-server/minecraft-patches/`（底层 Minecraft 调整）
- `mili-api/paper-patches/features/`（API 层补丁）

**修改服务器逻辑的工作流**：
1. 编辑 `mili-server/src/minecraft/` 下的源文件
2. 执行 `./gradlew :mili-server:rebuildPatches` 重新生成 `.patch` 文件
3. 将补丁文件纳入 git 提交

### mili-rust 模块

Rust 优化器通过**子进程通信**（非 JNI 库）与 Java 交互：

| Rust 文件 | 功能 |
|-----------|------|
| `chunk.rs` | 区块坐标转换、区域计算 |
| `nbt.rs` | NBT 数据格式解析 |
| `protocol.rs` | 网络包合并成本计算 |
| `scheduler.rs` | 调度任务优化（使用 Rayon 并行库） |
| `varint.rs` | VarInt/VarLong 编解码 |

Java 入口：`mili-rust/src/main/java/org/mili/rust/RustOptimizer.java`。若 Rust 二进制不可用，自动回退到纯 Java 实现。

---

## 关键配置文件

- **`gradle.properties`**：项目版本、Minecraft 版本、上游 commit SHA（`luminolRef`）、发布标志（`release=1` 预发布，`release=2` 正式版）
- **`settings.gradle.kts`**：子模块声明，注册 Hyacinthusweight 插件
- **`mili-server/build.gradle.kts`**：服务器构建核心，包含 fork 链声明、源集聚合、运行任务配置
- **`mili-rust/src/rust/Cargo.toml`**：Rust 依赖，使用 Rayon 做并行计算

---

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 21+ |
| Rust | stable toolchain |
| Git | 2.0+ |

构建时建议分配 ≥ 4GB 内存（`JAVA_OPTS="-Xmx4G"`）。

---

## API 依赖（插件开发）

```gradle
repositories {
    // 使用项目提供的 Maven 仓库
}
dependencies {
    compileOnly("fun.bm.mili:mili-api:<version>")
}
```
