 # Mili

Mili 是 Lophine 的一个衍生分支，目标是在 Folia 环境下提供更稳定、可配置的服务器运行时与一系列实用功能。(快独立了）

主要目标：在保持与上游兼容的前提下，针对 Folia 的并发/调度模型提供修复与优化，并通过可配置选项增强原版行为与红石（Redstone）相关功能。

当然，你也可以通过查看[wiki（中文）](docs/WIKI.md)

主要模块
- `mili-api` / `luminol-api` / `folia-api` / `paper-api`：对外暴露的 API 和兼容适配。
- `mili-server` / `luminol-server` / `folia-server` / `paper-server`：服务端核心逻辑的兼容补丁与增强实现。
- `mili-rust`：包含与 Rust 组件交互的绑定/工具（若启用）。

核心特性
- 可配置的原版行为（游戏机制调节）
- 针对 Folia 的 Bug 修复与兼容层
- 支持多种存档格式（包括 linear/b_linear）
- 对红石与生存电路的兼容性增强（在 Folia 上更稳定）
- 持续提供实用工具与性能优化补丁

快速开始（开发构建）
1. 克隆仓库：

```bash
git clone https://github.com/xucy10/Mili.git
cd Mili
```

2. 应用补丁并构建（示例）：

```bash
./gradlew applyAllPatches
./gradlew createMojmapPaperclipJar
```

构建产物位于 `mili-server/build/libs` 下。

使用与集成
- 若只需依赖 API，请在 Gradle/Maven 中添加 `fun.bm.mili:mili-api` 的 `compileOnly` 依赖并使用我们提供的仓库地址。

贡献与反馈
- 欢迎 Pull Requests 与 Issue，但请先阅读贡献指南：参见 [贡献指南（中文）](docs/CONTRIBUTING.md) 或 [Contributing (EN)](README_EN.md).
- 报告问题时请提供完整日志、环境信息与复现步骤。

许可证
- 本项目遵循仓库根目录的 LICENSE 文件。

感谢
- 感谢所有贡献者与赞助方对项目的持续支持。若项目对您有帮助，请在 GitHub 上给我们一个 star⭐

