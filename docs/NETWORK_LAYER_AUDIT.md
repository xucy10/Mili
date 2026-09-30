# Mili 网络发送链路瓶颈分析报告

> 2026-09-30 排查产出。范围：服务器→客户端网络发送链路 + 入站编码热路径。
> 源码树：`mili-server/src/minecraft/java/net/minecraft/`（已应用补丁）、`mili-server/src/main/java/fun/bm/mili/`。
> 仅静态分析，未运行基准。所有行号基于当前源码树。

---

## 0. 总体结论

上游（Paper/Luminol/Folia）已覆盖大部分经典网络优化：Velocity 原生压缩、写路径 VarInt/UTF-8 Krypton 优化、FlushConsolidationHandler、包限流器。**本树与 Krypton 目标形态最大的差距是：Folia 调度下主线程游戏期间逐包 `writeAndFlush`，且现有 FlushConsolidationHandler 构造参数无法覆盖该场景。**其次是实体广播的每玩家重复序列化与读路径 VarInt。

---

## 1. Connection 写出路径

**现状**
- `send()`（Connection.java L552-594）：Folia 已禁用 Paper 直写路径（L568 `if (false && ...)`），`canSendImmediate` 的 19 种可直写包类型（L1109-1129）整段死代码，**所有包进 pendingActions 队列**，每次 send 后调 `flushQueue()`（L591）。
- `processQueue`（L682-720）：`AtomicBoolean flushingQueue`（L671）保证单线程 flush，region 并发安全。
- `doSendPacket`（L615-644）：`flush ? channel.writeAndFlush(packet) : channel.write(packet)`（L626/L629）。
- **flush 参数判定**（ServerCommonPacketListenerImpl.java L412）：`flag = !suspendFlushingOnServerThread || !server.isSameThread()`。主线程上 `suspendFlushingOnServerThread` 默认 false → flag=true → **游戏内每包一次 writeAndFlush**。suspend/resume 仅用于玩家加入流程（PlayerList.java L363/L516、MinecraftServer.java L2042）；MinecraftServer.java L1928 的全局批量 suspend 被 Folia 注释掉。
- `tick()`（L726-783）调 flushQueue 但**无 tick 末尾集中 flush**（L773 显式 flush 需 `-Dpaper.explicit-flush`，默认关）。

**问题点**
- 【高】逐包 flush：FlushConsolidationHandler 用默认构造（ServerConnectionListener.java L96），`consolidateWhenNoReadInProgress=false`——只在"读进行中"合并 flush；主线程 region tick 批量写时逐包穿透到 socket。
- 【中】每次 send 都 peek+CAS flushQueue，多 region 线程写同一连接时争用。
- 【低】canSendImmediate 直写路径为死代码。

## 2. Bundle 打包

- 出站 `PacketBundleUnpacker`（Connection.java L424-427 插入）：BundlePacket 展开为 N 个子包，源头一次 write、一次 flush，不逐包 flush。入站 PacketBundlePacker 聚合。
- ServerEntity 使用点：L219（motion bundle）、L325（配对数据 bundle）、Entity.java L704（乘客 bundle）。
- 结论：【低】bundle 路径健康。

## 3. 实体广播路径

- `TrackedEntity.sendToTrackingPlayers`（ChunkMap.java L1354-1358）：同一 packet 对象广播所有玩家，**编码发生在各连接自己的 event loop，无每玩家/共享编码结果缓存**。
- `ServerEntity.sendChanges`：位置硬同步含 `tickCount % 60 == 0`（每 3 秒强制位置包，L178）；SparklyPaper "delta movement 未变跳过 distanceToSqr" 已在（L212-230）。
- `updatePlayer`（ChunkMap L1393-1420）：Paper 已消除 Vec3 分配，支持 trackingRangeY。
- 问题：【中-高】高频包（SetEntityData/Move/Motion）每连接各自完整 StreamCodec 编码，高实体×高玩家时是 packet encoder CPU 主项。注意：缓存编码结果需绕开 RegistryFriendlyByteBuf 的 obfuscation session 依赖（ClientboundSetEntityDataPacket L22-26）。

## 4. 编码热路径

- `VarInt.write`：✔ 已优化（查表 getByteSize L11-21 + 1/2 字节 peel L53-67 + Krypton writeVarIntFull L79-99）。
- `VarInt.read`：✘ **未优化**（VarInt.java L37-51，vanilla 逐字节 readByte 循环）。入站移动包每包多次调用（前置长度+字段）。Krypton 快速读路径未移植。
- `Utf8String.write`：✔ Krypton 优化（L34-48）。read 批量解码，尚可。

## 5. 压缩

- `CompressionEncoder`：Velocity 原生 libdeflate 压缩器，每连接创建一次复用；vanilla Deflater 仅为 fallback（L21-27）。压缩级别走 paper 配置 `misc.compressionLevel`。
- 结论：【低】已是业界最优形态。

## 6. 限流/反压

- Paper packet limiter 在 channelRead0（Connection.java L260-303，每入站包）：`System.nanoTime()` + `synchronized(PACKET_LIMIT_LOCK)` + 类层级 HashMap 遍历。Luminol 开关 `PaperPacketLimiterConfig.forceDisable`。
- 【低-中】大流量入站时有可测开销；可换无锁计数。

## 7. Mili 自有网络代码

- `AsyncKeepaliveManager`：单线程 daemon executor，send 走 MT-safe pendingActions 队列，不占 region 线程；假人已排除。✔ 设计正确。开关：OldFeatureConfig.asyncKeepalive。
- `TISCMProtocol.broadcastMsptSample`（L40-64）：每 tick 每玩家一次 MSPT payload 编码+发送（主线程）；已有空支持列表短路。开关：GeneralCompatConfig.tiscmNetworkProtocol / syncServerMsptMetricsData。
- 未发现 mili 在 netty pipeline 插入自定义 handler。

## 8. Folia 特有

- `RegionizedWorldData.tickConnections()`（L647-649）：每 tick `new ArrayList<>(connections)` 复制 + `Collections.shuffle`，分配开销随玩家数线性。上游固有写法，可改为 shuffle 副本仅低频（如每 20 tick）或复用缓冲。

---

## Top 5 优化候选（按预期收益排序）

| # | 优化点 | 严重度 | 置信度 | 方案要点 | 约束 |
|---|--------|--------|--------|----------|------|
| 1 | 逐包 flush 消除 | 高 | 高 | 方案 A：FlushConsolidationHandler 换 `new FlushConsolidationHandler(256, true)`（一行）；方案 B：region tick 末尾集中 flushChannel()（参考 MinecraftServer L1928 被注释的全局 suspend 模式） | 默认行为=Paper 原则下需配置开关 + 灰度验证延迟影响 |
| 2 | 实体广播每玩家重复编码 | 中-高 | 高 | 评估（packet, protocol) 级只读 ByteBuf 缓存/分片复用 | 需绕开 registry/obfuscation session 依赖，复杂度高，建议单独立项 |
| 3 | VarInt.read Krypton 化 | 中 | 高 | 移植 Krypton 快速读到 VarInt.java L37-51 | 纯微优化，风险低，改动小 |
| 4 | 入站 packet limiter 无锁化 | 低-中 | 中 | 用 forceDisable 验证收益，或换无锁计数 | 涉及反作弊保护语义，需默认保留开关 |
| 5 | flushQueue 调频 / 直写路径复活 | 低-中 | 中 | 降低 flushQueue 调用频率或恢复 event-loop 外可直写的快速路径 | Folia 并发正确性敏感，收益是常数级 |

**推荐落地顺序**：#3（微优化，先练手验证补丁流程）→ #1（最大收益，方案 A 一行改动）→ #4/#5 视压测结果 → #2 单独立项。

**验证约束**：本机 8G 内存，Java 侧编译验证一律走 CI；cargo 仅 check/test。

---

## 附：相关配置/系统属性

- `-Dpaper.explicit-flush`（默认关；开启反而每 tick 多一次 flush）
- `-DPaper.disableFlushConsolidate`（禁用 FlushConsolidationHandler）
- `PaperPacketLimiterConfig.forceDisable`（Luminol）
- paper：`misc.compressionLevel`、`packetLimiter.allPackets/overrides`、`entities.trackingRangeY`
- Mili：`OldFeatureConfig.asyncKeepalive`、`GeneralCompatConfig.tiscmNetworkProtocol`、`BytebufProtocolConfig.enabled`

---

## 9. 实施状态（2026-09-30 更新）

| 候选 | 状态 | 产物 |
|------|------|------|
| #1 逐包 flush 消除 | ✅ 已实施 | 补丁 0128：`FlushConsolidationHandler(256, true)`，主线程批量写也合并 flush；可 `-DPaper.disableFlushConsolidate` 关闭 |
| #3 VarInt.read Krypton 化 | ✅ 已实施 | 补丁 0129：展开式读实现，1-5 字节语义与原版一致，单字节场景直接返回 |
| tickConnections 缓冲复用 | ✅ 已实施 | 补丁 0130：实例级 scratch 列表（clear+addAll），消除每 tick ArrayList 分配 |
| #4 入站 limiter 无锁化 | ➖ 无需改动 | 复核发现 Luminol 已有 `forceDisable` 快速出口（Connection.java L260，先于 nanoTime/synchronized），热路径开销已可配置归零 |
| #5 flushQueue 调频 | ⏸️ 暂缓 | 每次 send 一次 AtomicBoolean CAS 相对已修复的逐包 flush 是二阶开销；改动触及 Folia 并发正确性，收益/风险比不划算 |
| #2 实体广播编码缓存 | ⏸️ 单独立项 | 需绕开 RegistryFriendlyByteBuf obfuscation session 依赖；本机无编译验证能力（Java 构建走 CI），在核心序列化路径上盲改风险过高。设计要点：按 (packet, protocol) 缓存只读 ByteBuf 切片、注册表相关包排除、antixray/obfuscation 联动 |

**验证要求**：本机 8G 内存不跑 Gradle/Java。三个补丁已通过 `git apply --check -R` 与源码树逐字节反向校验；编译与运行验证须在 CI 执行 `applyAllPatches` + `:mili-server:test`（必要时压测对比 flush 前后 syscall 数与 MSPT）。
