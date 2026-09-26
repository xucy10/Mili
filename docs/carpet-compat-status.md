## Carpet / 协议兼容状态

本文档汇总 Mili 当前实现或映射的 Carpet 规则与 TIS/AMS 扩展。用作快速兼容性参考。

状态标记：
- **Mapped**：行为已实现或转发到等效的 Mili 配置/功能
- **Removed**：行为被有意移除或禁用
- **Equivalent**：已实现结构或语义等价物

---

### Carpet 核心规则

| 规则 | 状态 | 说明 |
|------|------|------|
| `language` | Mapped | 转发到 `function.language.locale` |
| `commandTick` | Mapped | 转发到 tick 命令补丁 |
| `creativeNoClip` | Mapped | 创造飞行无碰撞行为 |
| `placementRotationFix` | Removed | 放置旋转使用玩家身体方向 |
| `explosionNoBlockDamage` | Removed | 爆炸仍伤害实体但不破坏方块 |
| `xpNoCooldown` | Removed | 经验球可在同一 tick 收集 |
| `commandFunction` | Mapped | `/function` 命令开关 |
| `commandScoreboard` | Mapped | `/scoreboard` 命令开关 |
| `commandSaveAll` | Mapped | 区域安全 `/save-all` |

### Carpet TIS 扩展

| 规则 | 状态 | 说明 |
|------|------|------|
| `yeetUpdateSuppressionCrash` | Mapped | 转发到 Mili 更新抑制崩溃修复 |
| `instantBlockUpdaterReintroduced` | Mapped | `experiment.redstone.instant-block-updater` |
| `optimizedDragonRespawn` | Mapped | 龙重生优化 |
| `totallyNoBlockUpdate` | Removed | 邻居和形状更新集中短路 |
| `tntDupingFix` | Removed | 活塞 TNT 复制路径通过兼容规则控制 |
| `microTiming` | Mapped | 微时序日志（Carpet Logger 协议） |

### Carpet Org / AMS 扩展

| 规则 | 状态 | 说明 |
|------|------|------|
| `hopperNoItemCost` | Removed | 支持羊毛漏斗技巧 |
| `creativeOneHitKill` | Removed | 创造一击必杀行为 |
| `fakePlayerAutoReplenishmentFormShulkerBox` | Mapped | 假玩家从潜影盒补充物品 |

### Mili 扩展规则

| 规则 | 状态 | 说明 |
|------|------|------|
| `commandBot` | Mapped | `/bot` 命令启用 |
| `fakePlayerResident` | Mapped | 假玩家常驻模式 |
| `openFakePlayerInventory` | Mapped | 打开假玩家背包 |
| `fakePlayerLimit` | Mapped | 假玩家数量上限 |
| `fakePlayerPrefix` | Mapped | 假玩家名称前缀 |

### 客户端协议兼容

| 协议 | 状态 | 说明 |
|------|------|------|
| Carpet HUD | Mapped | TPS/mobcaps/counter 同步 |
| TISCM | Mapped | `tiscm:network/v1`，MSPT 广播 |
| XaeroMap | Mapped | Xaero 地图通道 |
| Jade | Mapped | Jade 服务端数据 |
| Syncmatica | Mapped | Litematic 同步 |
| Servux | Mapped | Servux 客户端服务 |
| REI | Mapped | Roughly Enough Items 服务端协议 |
| BBOR | Mapped | Bounding Box Outline Reloaded |
| AppleSkin | Mapped | 饥饿/饱和度同步 |
| Alternative Block Placement | Mapped | Accurate/Carpet/Litematica 放置协议 |

---

### 注意

- 本文件提供高级概览；如需确切行为和配置键，请在代码库中搜索 `fun.bm.mili.carpet` 或 `ConfigsInstance` 条目。
- Carpet 规则同步由 `CarpetCompatSync.java` 处理，规则值变更会同步到 Mili 配置并向客户端广播。
- 部分规则通过 `GeneralCompatConfig.java` 中的 50+ 条映射实现。
