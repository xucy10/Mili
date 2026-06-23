---
title: 自动化回归测试（个人工作流）
scope: workspace
owner: "个人"
summary: |
  一个面向个人使用的完整、多步骤自动化回归测试工作流模板。包含触发条件、准备步骤、运行测试、收集结果、回滚/报告和质量门控准则。
tags: [testing, regression, ci, workflow]
---

# 自动化回归测试 工作流（个人）

## 目标

为个人开发者提供一套可复用的自动化回归测试工作流，帮助在提交补丁、合并 PR 或切换分支时快速验证回归风险并生成可行动的报告。

## 适用场景

- 本地开发验证（快速回归）
- 在 CI 之前的本地完整回归运行
- 快速复现上游或补丁导致的回归问题

## 何时触发

- 在准备合并补丁或 PR 前
- 在拉取远程分支并集成后
- 在关键功能更改或依赖升级后

## 前提条件

- 本地已配置项目构建工具（Gradle/Maven/Gradle Kotlin DSL）。
- 必要的服务（如数据库、消息队列）可通过脚本启动或有可替代的测试替身。
- 可以从仓库根目录运行测试脚本（`./scripts` 下或 `gradlew`）。

## 工作流步骤

1. 环境准备
   - 检查工作树干净：`git status --porcelain` 应为空。
   - 切换到目标分支并更新：`git checkout <branch>`，`git pull --rebase`。
   - 启动必要的本地服务（可选）：`./scripts/start-test-env.sh`。

2. 构建与快速 smoke 测试
   - 执行增量构建：`./gradlew assemble`。
   - 运行 smoke 测试套件（快速失败以节省时间）：`./gradlew testSmoke`。

3. 完整回归测试（可并行化）
   - 运行完整单元与集成测试：`./gradlew test integrationTest --parallel`。
   - 若项目提供特定回归任务，优先运行：`./gradlew regressionTest`。

4. 收集与归档结果
   - 将测试报告导出到 `build/reports/tests/`。
   - 保存失败测试的栈追踪与相关日志到 `artifacts/regression/<timestamp>/`。

5. 分析与分类失败
   - 将失败分为：环境问题 / 新增回归 / 非确定性（flaky）
   - 对于非确定性失败，运行重复测试：`./gradlew test --tests "com.example.*" --rerun-tasks -Dtest.retries=3`。

6. 回滚或隔离变更（如果是新增回归）
   - 使用二分法（git bisect）在本地确认引入回归的提交。
   - 创建临时分支保存调查进度：`git checkout -b regress/diagnose-<short-commit>`。

7. 报告与下一步
   - 生成简短报告模板并粘贴到 issue/PR 中：
     - 复现步骤
     - 失败的测试列表与日志位置
     - 本地临时分支或回退建议
   - 如果是环境问题，记录可复现环境并更新 `scripts/` 启动脚本。

## 质量门（Quality Gates）

- 阶段一（合并前）：所有 smoke 测试通过。
- 阶段二（CI 入口）：关键模块无失败；若有 flaky，标注并建立 ticket。
- 阶段三（发布）：无高/严重级别失败，回归数不超过阈值（默认 0）。

## 实用脚本与示例命令

- 启动全环境、运行回归并收集报告（示例）：

```bash
./scripts/start-test-env.sh
./gradlew clean assemble regressionTest --parallel
mkdir -p artifacts/regression/$(date +%Y%m%d%H%M%S)
cp -r build/reports/tests/* artifacts/regression/$(date +%Y%m%d%H%M%S)/
```

## 异常处理与常见问题

- 构建失败：先运行 `./gradlew assemble --stacktrace` 并检查依赖或本地构建缓存。
- 测试依赖外部服务超时：在脚本里增加重试与更长超时，或使用本地替身（mock）。

## 可选增强（后续迭代）

- 将工作流集成到本地 git 钩子（`pre-push`）以自动运行 smoke 测试。
- 增加 `--fast` 与 `--full` 模式切换脚本。
- 将结果自动上传到远程归档（例如 S3）并在 PR 中附上链接。

## 示例提示

- "使用此技能在合并前运行完整回归并生成报告。"
- "为当前分支运行 smoke 测试并保存失败日志到 artifacts。"

## 维护记录

- 版本 0.1 — 初始草稿
