为 Mili 贡献代码
=================

[English](./CONTRIBUTING_EN.md) | **中文**

感谢您愿意为 Mili 做出贡献！本指南给出一个清晰的入门流程、补丁工作流与常见问题解答，以便您快速上手。

快速入门
1. 使用个人账号 Fork 仓库并克隆到本地：

```bash
git clone https://github.com/xucy10/Mili.git
cd Mili
```

2. 应用补丁工作树（在仓库根目录运行）：

```bash
./gradlew applyAllPatches
```

3. 在生成的 `*-api` / `*-server` 目录中进行修改并按补丁流程提交。

开发环境要求
- `git`
- `JDK 21` 或更高

注意（Windows / Git 长路径）
请确保启用了系统与 Git 的长路径支持：
- Windows: https://learn.microsoft.com/windows/win32/fileio/maximum-file-path-limitation
- Git for Windows: https://gitforwindows.org/faq.html#i-get-errors-trying-to-check-out-files-with-long-path-names

补丁模型概览
----------------
Mili 使用基于 Git 的补丁模型，仓库在应用补丁后会在根目录生成一系列 `*-api` / `*-server` 目录：

- `Mili-api`, `luminol-api`, `folia-api`, `paper-api` —— API 相关修改
- `Mili-server`, `luminol-server`, `folia-server`, `paper-server` —— 服务器实现与补丁

这些目录在本质上并非独立 git 仓库：
- 在应用补丁前，基点指向未被修改的上游源码。
- 每一次对 `*-api`/`*-server` 的提交都会在补丁集合中产生相应变更。

如何添加新补丁
----------------
1. 在相应的 `*-api` 或 `*-server` 目录下进行修改。
2. 暂存更改：`git add <files>`（注意：对 Mili 自动创建的新文件不要直接提交为普通提交）。
3. 提交修改：`git commit -m "Describe change"`。
4. 运行：`./gradlew fixupPaperApiFilePatches`（若有新增文件由该任务生成补丁）
5. 运行：`./gradlew rebuildAllServerPatches` 将提交转为补丁。
6. 推送并发起 PR（将补丁文件包含在 PR 中）。

修改已存在补丁
----------------
1. 在 `HEAD` 上修改相关代码。
2. 使用修正提交：`git commit -a --fixup <hash>`（或使用 `--squash` 编辑提交信息）。
3. 自动变基：`git rebase -i --autosquash base`，然后保存并退出。
4. 运行 `./gradlew fixupPaperApiFilePatches`（若需要）
5. 运行 `./gradlew rebuildAllServerPatches`。
6. 推送并更新 PR。

常见问题
---------
- 我应当使用组织账号 Fork 吗？
    - 不建议。组织 Fork 的 PR 无法由本项目直接编辑，合并过程会更复杂。

- 构建失败怎么办？
    - 先运行 `./gradlew assemble --stacktrace` 并检查错误输出与依赖问题。

- 我如何运行本地测试？
    - 使用 `./gradlew test` 或项目提供的特定测试任务（参见 `build.gradle.kts`）。

更多帮助
---------
查阅仓库根目录的 `README.md` / `README_EN.md` 获取构建、依赖和社区链接。如需进一步协助，请在 Issue 中提供构建日志、JDK 版本和复现步骤。

