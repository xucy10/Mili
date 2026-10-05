# 延迟治理手册

> 适用分支：ver/26.2（Folia 区域化调度）
> 现象：在线人数上升后，一部分玩家延迟明显偏高，且往往集中在同一个区域（主城、大型生电厂）。

---

## 1. 先厘清一件事：tab 上的数字不是“网络延迟”

很多排查从这里跑偏，所以必须先说清楚。

Minecraft 显示的延迟 = **keepalive 发出到回包被结算** 的时间差。在本项目的实际代码路径里：

| 环节 | 发生在哪 |
|---|---|
| 打点 tx | `keepConnectionAlive()`，位于玩家所属 **region 的 tick 线程** |
| 下行 → 客户端 → 上行 | 网络 + Netty IO 线程 |
| 回包结算 rx | `PacketUtils.ensureRunningOnSameThread` → `schedulePacket` → **region tick 线程队列** |

也就是说：

```
显示延迟 = 真实网络 RTT + 包在 region 队列里等待的时间
```

**关键推论**：这条队列不是 keepalive 专用的。玩家的移动、方块交互、攻击包走的是**完全相同的队列、完全相同的那根 region tick 线程**。

所以 tab 上的高数值不是“显示的错觉”——它反映的就是这条队列的真实积压。玩家说“卡”，和 tab 数字高，是同一个原因。

---

## 2. 根因：一个热 region 里，几十个人挤在一根线程上

Folia 会把相邻的 ticking 区块合并成一个 **region**，一个 region 只有一根 tick 线程。主城里玩家彼此靠近 → 合并成一个大 region → 这些玩家的所有负荷（入站包 + 实体 tick + 区块任务）全部串在一根线程上，而其他核心可能正闲着。

人越多，这根线程一圈越慢；一圈越慢，队列越长；队列越长，每个人等得越久。

补充：`RegionBalancerConfig.enabled` 默认 **false**，说明当前跑的是 Folia 原生的「每 region 专用线程」，所以**不是多个 region 互相抢线程**造成的。

---

## 3. 已实施的改动：让指标先变准

`fun.bm.mili.network.IoThreadKeepalive`（配置 `io-thread-keepalive.enabled`，默认开）

原理是在 Netty pipeline 里、连接处理器之前插入一个入站 handler，把 keepalive 回包**直接在 IO 线程结算**并终止投递，绕过 region 队列。

- 效果：显示值不再混算 region 排队，读数贴近真实链路
- **它不改善体感**：移动、交互包仍走 region
- 上线后在日志里看到 `IO 线程 keepalive 已接管 N/M 条连接` 即表示生效

这个改动的价值是**把干扰项拿掉**，方便后续判断优化是否真的有效。

两个入口各装一次是有意为之：配置加载时（`IoThreadKeepaliveConfig.onLoaded`）给已在线玩家装，服务端就绪后（`MiliOptimizations.init`，第一次 region tick）再兜底一次——因为配置加载早于 PluginManager 可用，第一次注册可能失败。你想知道哪条路走通了，就看那行日志里 `N/M` 的 M 是不是等于当时的在线人数。

---

## 4. 真正降低延迟：按确定性排序的执行清单

每一条下面写的都是**仓库里已经存在的配置项**，不是让你自己去拼启动参数。

速查表：

| 优先级 | 配置项 | 默认值 | 需要重启 |
|---|---|---|---|
| P0 | `tick-catchup.max-tick-catchup` | **0（已改好）** | 否，支持热重载 |
| P1 | `cpu_affinity.enabled` + `physical-cores-only` | false | 是 |
| P2 | `dynamic-view-distance.enabled` | false（**驱动已接好**） | 否，热重载即可；失败时日志会提示等待就绪 |
| P3 | `network-optimizer.io-event-loop-threads` | 0 | 是 |
| P4 | 世界 `simulation-distance` | 服务端属性 | 否 |
| P5 | `region_balancer.enabled` | false（**保持不动**） | — |

### P0 — 关掉 TPS 追赶（默认已改好）

`tick-catchup.max-tick-catchup` **默认值已从 20 改为 0**。这是 Mili 自己的补丁（0122）加的限制，本意是防止卡顿后一次性快进，但它对入站延迟有直接副作用。

机制在 `TickRegionScheduler` 里：

- `ConcreteRegionTickHandle#tickRegion` **一批只真正 tick 一次**，`tickCount` 只用于 `advanceBy` 推进调度进度
- 限定上限后，落后被摊成多次迭代；而每次迭代结束时 deadline 仍已过期 → **region 转入紧凑的连续 tick**
- 连续 tick 之间没有闲置窗口去 drain 入站包，玩家的包要堆到追赶结束才被处理

设为 `<=0`（现已默认）时债务一次勾销，下一次 tick 回到正常的 50ms 之后，留出处理包的空隙。这也是 Folia 原生的行为。

**默认值已经是 0，这一步不需要操作。** 若想退回「追赶时间、牺牲响应」的旧行为，改成 `20` 即可。

代价只有一个：区域不补齐落后的游戏时间，TPS 读数会把这段落后如实显示出来（它本来就没追上）。把这看作**诚实的读数**而不是退步——它本来就没追上，只是以前被藏起来了。

这个字段在每次 tick 时读取，**支持热重载**，改完重载配置即可对比效果，无需重启。这也是 P0 能最快出结论的原因。

### P1 — CPU 绑定

`CpuAffinityConfig`（`cpu_affinity` 模块，默认关闭）可以把 **tick region 线程**钉到指定核心。该模块属于 luminol 配置命名空间，并带 `@TransformedConfig` 兼容映射（旧路径 `misc.cpu_affinity`），以实际生成出来的配置文件为准。

需要设置的字段：

- `enabled` → `true`
- `physical-cores-only` → `true`（**推荐用这个**，无需手工填核心编号）
- 或者手工指定：`tickregion_affinity` → `["0","1","2","3","4","5","6","7"]`

`physical-cores-only` 会自动推断物理核集合：多数单路 x86 上逻辑处理器的编号规则是「先编各物理核的首个线程，再编各自的超线程兄弟」，所以前一半通常就是物理核实例。启用后看日志里 `已启用自动物理核绑定，实际绑定集合: [...]`，确认是否符合预期。需重启生效。

**为什么要做**：P/E 核混合的 CPU（12/13/14 代 Intel）上，tick 线程若被丢到 E 核会比 P 核慢数倍；即便没有 E 核，被丢到超线程伙伴上也要和兄弟线程抢同一物理核。表现就是“同一台机器上，某些 region 特别慢，某些正常”——和现象高度吻合。

### P2 — 动态视距（驱动已修好，推荐打开）

`dynamic-view-distance.enabled`

**这个功能此前是坏的**：`DynamicViewDistanceManager.tick()` 在整个项目里**没有任何调用者**，开关打开也永远不会执行。

现在有两层修复：

1. 补上周期驱动（`GlobalRegionScheduler`，每秒一次），实际间隔仍由 manager 内部的 CAS 门控按 `adjust-interval-seconds` 决定——所以改这个值会立刻生效，不会被调度器周期钉死；
2. **启动点放在 `MiliOptimizations.init()`**（第一次 region tick，世界已加载），而不是配置加载时。配置加载发生在 `DedicatedServer` 构造期间，那时世界和调度器都还没准备好。

同时排除了一个隐蔽陷阱：驱动不能用 `getPluginManager().getPlugin("Mili")` 拿插件实例——Mili 是**服务端内核不是 Bukkit 插件**，那个查找恒返回 null，任务会静默起不来（仓库里 `AutoBackupManager` 就踩过，注释里记着）。用的是内置实例 `MinecraftInternalPlugin.INSTANCE`。

启用后它会按 TPS 与**附近玩家密度**自动收敛每个玩家的视距：人越密、TPS 越低，视距越靠近 `min-view-distance`；情况好转自动涨回 `max-view-distance`。这能压住下行洪峰，减轻 event loop 争抢，且是自适应的、可逆的。

配套项：`min-view-distance`、`max-view-distance`、`tps-high-threshold`、`tps-low-threshold`、`player-density-weight`（密度权重，越高越激进）、`adjust-interval-seconds`（调整周期，改完热重载即时生效）。

推荐起点：`min-view-distance = 6`、`max-view-distance = 12`、`player-density-weight` 保持默认 `0.5`，先观察密度驱动的收敛是否符合预期，再决定是否更激进。（默认下限 4 偏狠，直接开容易让主城玩家看见“贴脸刷怪”。）

### P3 — 提高 Netty IO 线程数

每个连接绑定到一个 event loop，同一 loop 上的连接**互相争抢处理时间**：只要有少数几个连接正在猛拉区块/实体数据，挂在同一个 loop 上的其他人都会被一起拖慢。这会呈现出“一部分人延迟高”，且与他们在哪个 region 无关——所以它对「同 region 聚集」之外的那批高延迟玩家特别有效。

配置项 `network-optimizer.io-event-loop-threads`（默认 `0` = 不改）。取值建议：**并发连接峰值 ÷ 3**，且控制在物理核数的 2~4 倍以内——不是越大越好，过多线程只会增加上下文切换。

一个诚实的提醒：Netty 的线程数在 event loop group **首次初始化时**就定了，而 Mili 的配置加载可能晚于那一刻。所以：

- 若你在日志里看到 `已请求 Netty IO 线程数=N`，说明配置侧已接上
- 若没有生效（线程数没变），改用 JVM 启动参数 `-Dio.netty.eventLoopThreads=N`
- 两者同时存在时**以启动参数为准**，配置项不会覆盖它

验证是否生效：`jcmd <pid> Thread.print | grep -c "Netty Server IO"`，需要重启才能改变。

### P4 — 缩小单个 region 的规模

region 越大，挤在上面的人越多，串行越严重。让 region 变小就是让它并行。

- 下调主城所在世界的 **simulation distance**（这是决定 region 合并半径的主要因素）
- 把玩家的聚集区物理分散（旅游点、摊位分区）
- 用项目自带工具定位实体热点并定点清理：

```
/heatmap export <world>
```

导出后看哪些区块实体密度异常高——那里既是 tick 热点，也是把该 region 撑大的元凶。

**代价**：sim distance 影响机器的 tick 范围，生电服需要权衡。建议先只对**主城世界**调整，保留生电世界的原值。

### P5 — 暂时不要动的东西

**不要急着开 `region_balancer.enabled`**。它改用固定大小的共享线程池（默认 CPU/2），会让 region 之间开始抢线程。对一个“单个热 region 主导”的场景，很可能比现在更糟。配置注释也提示了可能影响跨区域红石机器。

---

## 5. 怎么判断有没有起效

不要凭“感觉快了”下结论。固定一个可复现的场景：同一时段、同样的在线人数规模，对比改动前后。

记录三个数：

1. **tab 延迟的分布**：低于 50ms / 50-150ms / 高于 150ms 各有多少人
2. **最慢 region 的 MSPT 和 TPS**：

```
/tps server 10
```

这是 Folia 的健康报告（不是普通 tps），会用 `${util}% util at ${mspt} MSPT at ${tps} TPS` 的格式列出最慢的 10 个 region，并附带每个 region 的 Chunks / Players / Entities。

3. **分布的收敛程度**——重点看高延迟那批人的数量有没有下降，而不是平均值

建议按 **P0 → P1 → P2 → P3 → P4** 的顺序推进，每做一个就重新记录一次上面的数据对比：

| 步骤 | 见效速度 | 代价 |
|---|---|---|
| P0 | 热重载即可，最快 | TPS 读数变诚实 |
| P1 | 需重启 | 无（只是限制 OS 调度自由） |
| P2 | 热重载即可打开；若日志提示「暂未启动」则重启 | 视距会变，玩家可能察觉 |
| P3 | 需重启 | 更多线程上下文切换 |
| P4 | 需重载世界 | 生电世界的 tick 范围受影响 |

如果第 1 项分布明显收敛、第 2 项的 MAX region MSPT 下降，说明治理走在正确的方向上。

一个判断技巧：如果做完 P0~P2 后，**仍有某一批固定玩家高延迟且他们不在同一个 region**，那说明主因是 P3（event loop 争抢）而不是 region 串行——两者的现象相似，但受影响的玩家集合不同。

---

## 6. 待 CI 处理的深层改动

以下两条都在 mc 层，本机缺少 apply 后的源码树（`mili-server/src/minecraft/java` 未物化），无法安全构造补丁，需要在具备源码树的环境中实施：

- **包 drain 的优先级**：`TickRegionScheduler#runRegionTasks` 里 tick 任务、chunk 任务、`drainOnePacket()` 是公平轮转的。region 积压时，入站包的处理机会会被区块任务稀释。改成优先 drain 包再处理 chunk 任务，可直接缩短入站延迟，代价只是区块加载略慢。
- **移植 1.21.11 分支的网络层优化**：0128 flush 合并、0129 VarInt 读取、0130 tickConnections 复用。
