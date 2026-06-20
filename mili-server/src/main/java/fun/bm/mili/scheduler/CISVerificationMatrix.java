package fun.bm.mili.scheduler;

/**
 * CIS 功能验证矩阵 (CIS Functional Verification Matrix).
 *
 * This file documents the expected behavior of the Chunk Independent
 * Scheduler for each critical gameplay mechanic. It is NOT executed
 * at runtime — it serves as a living specification used during
 * development and review.
 *
 * =========================================================================
 * 验证项                        | 状态      | 实现机制
 * =========================================================================
 * 红石线跨区块                 | DESIGNED  | ChunkBorderCache + RedstoneBorderRelay
 *                                |           | Phase 1 采集边界红石线 Power 值
 *                                |           | Phase 2 通过 neighborChanged 注入
 *                                |           | 延迟: 1 tick (configurable)
 * -------------------------------------------------------------------------
 * 中继器跨区块                 | DESIGNED  | BorderCache 检测 RepeaterBlock.POWERED
 *                                |           | 跨区块信号通过 Phase 2 注入
 * -------------------------------------------------------------------------
 * 比较器跨区块                 | DESIGNED  | BorderCache 检测 ComparatorBlock.POWER_OUT
 *                                |           | 与中继器同路径
 * -------------------------------------------------------------------------
 * 活塞/粘性活塞跨区块           | DESIGNED  | BorderCache 检测 PistonBaseBlock
 *                                |           | highInteraction → 回退 region 模式
 *                                |           | 确保活塞 0-tick 兼容性
 * -------------------------------------------------------------------------
 * TNT 复制机                    | DESIGNED  | 依赖活塞跨区块 + 红石跨区块
 *                                |           | strictMode=true 时回退 region 模式
 *                                |           | highInteraction 自动检测
 * -------------------------------------------------------------------------
 * 水流跨区块                   | DESIGNED  | BorderCache 检测 FluidState
 *                                |           | Phase 2 注入 FluidTickManager.schedule()
 *                                |           | 延迟: 1 tick
 * -------------------------------------------------------------------------
 * 瀑布跨区块                   | DESIGNED  | 同上 (falling water 也是 FluidState)
 * -------------------------------------------------------------------------
 * 熔岩+水跨区块               | DESIGNED  | 注入时同时 schedule WATER 和 LAVA
 * -------------------------------------------------------------------------
 * 玩家穿越区块边界             | INHERITED | 依赖 Folia 现有玩家迁移协议
 *                                |           | CIS 不修改玩家移动逻辑
 * -------------------------------------------------------------------------
 * 掉落物穿越区块边界           | INHERITED | 依赖 Folia 现有 ItemEntity tick
 *                                |           | CIS 不拦截实体 tick
 * -------------------------------------------------------------------------
 * 矿车穿越区块边界             | INHERITED | 依赖 Folia 现有 Minecart tick
 * -------------------------------------------------------------------------
 * 弹射物穿越区块边界           | INHERITED | 依赖 Folia 现有 Projectile tick
 *                                |           | CIS 只负责区块 tick，不干预 entity tick
 * -------------------------------------------------------------------------
 * 1000 实体集中一个区块        | DESIGNED  | CIS tick 按区块，不受实体数量影响
 *                                |           | 单区块 tick 时间 = O(entities)
 *                                |           | Work-stealing 不会把该区块迁移
 * -------------------------------------------------------------------------
 * 100 区块同时加载             | DESIGNED  | 独立区块并行 tick
 *                                |           | 高交互区块回退 region (串行)
 *                                |           | 线程池大小 = CPU count
 * -------------------------------------------------------------------------
 * perf monitor 兼容            | DESIGNED  | MiliTickSchedulerHook 在 global tick 调用
 *                                |           | CIS 定时在 schedulerLoop 中触发
 * -------------------------------------------------------------------------
 * chunk preload 兼容           | DESIGNED  | ChunkPreloader 加载后触发
 *                                |           | registerChunk() 注入 CIS
 * -------------------------------------------------------------------------
 * Carpet 兼容                  | VERIFIED  | 0044-Carpet-features.patch 不修改调度
 *                                |           | CIS 可选，不影响现有 Carpet 补丁
 * -------------------------------------------------------------------------
 * 假玩家兼容                   | VERIFIED  | ServerBot tick 由 Folia region 管理
 *                                |           | CIS 不修改 entity tick 路径
 * -------------------------------------------------------------------------
 * 核心组件回归                 | DESIGNED  | ChunkIndependentScheduler.stop()
 *                                |           | 异常时回退 Folia region 模式
 * =========================================================================
 *
 * Implementation Status Key:
 *   DESIGNED    - Architecture designed, code implements the design
 *   VERIFIED    - Verified working with existing patches
 *   INHERITED   - Relies on existing Folia/Minecraft behavior
 *   PENDING     - Not yet implemented
 *   BLOCKED     - Blocked by external dependency
 *
 * Thread Safety:
 *   R  - Read-only (no lock needed)
 *   L  - Lock-protected (see lock ordering)
 *   V  - Volatile flag (lock-free communication)
 *   A  - Atomic operation (CAS)
 *   S  - Synchronized
 *
 * Lock Ordering (deadlock prevention):
 *   1. ChunkPos dictionary order (lower x,z first)
 *   2. CrossChunkBus injection queue (coordinator thread only)
 *   3. No ChunkWorker locks held by CrossChunkBus
 *
 * Fallback Chain:
 *   1. Independent chunk tick (fast path)
 *   2. Work-stealing (idle worker picks up)
 *   3. timeoutMs exceeded → fallback to Folia region mode
 *   4. strictMode = true → skip independent for high-interaction
 *   5. All failure → server continues with regionized ticking
 */
public final class CISVerificationMatrix {
    private CISVerificationMatrix() {}
}
