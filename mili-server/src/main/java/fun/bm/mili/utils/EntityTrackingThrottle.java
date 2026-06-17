package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.EntityTrackingPerfConfig;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实体追踪广播节流器 / Entity tracking broadcast throttle.
 *
 * <p>通过哈希轮询预算和 TPS 感知节流来减少网络广播包 / Reduces network broadcast packets
 * via hash-based round-robin budget and TPS-aware throttling.
 */
public final class EntityTrackingThrottle {
    private EntityTrackingThrottle() {
    }

    /** 全局 tick 计数器 (溢出安全) / Global tick counter (overflow-safe). */
    private static final AtomicInteger currentTick = new AtomicInteger(0);

    /**
     * 在每个 tick 开始时调用 (由 patch 注入) / Called at tick start (injected by patch).
     * 使用 incrementAndGet，溢出后自动回绕 / Uses incrementAndGet, wraps on overflow.
     */
    public static void onTickStart() {
        currentTick.incrementAndGet();
    }

    /**
     * 判断指定实体是否应在本 tick 广播追踪更新 / Determines if the given entity should
     * broadcast its tracking update this tick.
     *
     * <p>逻辑顺序 / Logic order:
     * <ol>
     *   <li>功能关闭时直接放行 / Pass-through when disabled</li>
     *   <li>强制更新间隔到期时放行 / Pass when forced update interval is due</li>
     *   <li>TPS 低于阈值时按比例跳过 / Skip proportionally when TPS is below threshold</li>
     *   <li>超出每 tick 预算时跳过 / Skip when exceeding per-tick budget</li>
     * </ol>
     */
    public static boolean shouldBroadcast(Entity entity) {
        if (!EntityTrackingPerfConfig.enabled) return true;

        int entityId = entity.getId();
        // 使用位掩码取绝对值，避免 Math.abs(Integer.MIN_VALUE) 仍为负数
        // Use bitmask for absolute value to avoid Math.abs(Integer.MIN_VALUE) still being negative
        int absId = entityId & 0x7FFFFFFF;
        int tick = currentTick.get();

        // 强制更新间隔: 保证每个实体在 N tick 内至少更新一次
        // Forced update interval: guarantee each entity updates at least once every N ticks
        int forcedInterval = EntityTrackingPerfConfig.forcedUpdateInterval;
        if (forcedInterval > 0 && Math.floorMod(tick, forcedInterval) == Math.floorMod(absId, forcedInterval)) {
            return true;
        }

        // TPS 感知节流: TPS 越低，跳过越多 / TPS-aware throttle: lower TPS → more skipping
        double tpsThreshold = EntityTrackingPerfConfig.tpsThrottleThreshold;
        if (tpsThreshold > 0) {
            double currentTps = MiliTpsThrottle.currentTps();
            if (currentTps < tpsThreshold) {
                double factor = MiliTpsThrottle.throttleFactor(tpsThreshold, tpsThreshold * 0.5);
                // 使用位运算 & 3 代替 % 4，避免负数模运算陷阱
                // Use bitwise & 3 instead of % 4 to avoid negative modulo pitfall
                if (((entityId + tick) & 3) >= (int) (factor * 4)) {
                    return false;
                }
            }
        }

        // 每 tick 最大更新预算: 哈希轮询分配 / Max updates per tick: hash-based round-robin
        int maxUpdates = EntityTrackingPerfConfig.maxUpdatesPerTick;
        if (maxUpdates > 0) {
            int slot = Math.floorMod(absId, maxUpdates);
            if (slot != Math.floorMod(tick, maxUpdates)) {
                return false;
            }
        }

        return true;
    }
}
