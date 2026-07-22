package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.TechnicalMCOptimizerConfig;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 生电优化器
 * 专门针对技术型Minecraft机器进行优化:
 *
 * 1. 刷线机优化 - 减少线实体碰撞计算，优化物品合并
 * 2. 地毯机优化 - 限制实体更新频率，批量处理
 * 3. 铁轨系统优化 - 批量更新，矿车tick优化
 * 4. 珍珠炮优化 - 减少末影珍珠物理计算
 * 5. 天基屠龙炮优化 - 减少龙实体碰撞检测
 * 6. 大型红石机器 - 活塞批量更新，方块更新合并
 */
public class TechnicalMCOptimizer {

    private static final LongAdder itemsMerged = new LongAdder();
    private static final LongAdder railUpdatesBatched = new LongAdder();
    private static final LongAdder pistonUpdatesBatched = new LongAdder();
    private static final LongAdder entityTicksSkipped = new LongAdder();
    private static final LongAdder pearlTicksOptimized = new LongAdder();
    private static final LongAdder stringEntityTicksOptimized = new LongAdder();

    private static final ConcurrentHashMap<Long, Integer> pistonBatchCounter = new ConcurrentHashMap<>();
    private static long lastCleanup = System.currentTimeMillis();

    public static void init() {
        if (!TechnicalMCOptimizerConfig.enabled) return;
        Bukkit.getLogger().info("[Mili TechMC] Technical MC optimizer enabled");
        Bukkit.getLogger().info("[Mili TechMC]   hopperTickRate=" + TechnicalMCOptimizerConfig.hopperTickRate
                + " pistonBatch=" + TechnicalMCOptimizerConfig.pistonUpdateBatch
                + " stringDuper=" + TechnicalMCOptimizerConfig.stringDuperOptimization
                + " pearlCannon=" + TechnicalMCOptimizerConfig.pearlCannonOptimization);
    }

    /**
     * 检查漏斗是否应该被跳过
     * @return true 表示应该跳过本次tick
     */
    public static boolean shouldSkipHopperTick() {
        if (!TechnicalMCOptimizerConfig.enabled) return false;
        int rate = TechnicalMCOptimizerConfig.hopperTickRate;
        if (rate <= -1) return false;
        if (rate == 0) return true;
        // 使用全局tick计数
        return (getGlobalTick() % rate) != 0;
    }

    /**
     * 检查发射器/投掷器是否应该被跳过
     */
    public static boolean shouldSkipDispenserTick() {
        if (!TechnicalMCOptimizerConfig.enabled) return false;
        int rate = TechnicalMCOptimizerConfig.dispenserTickRate;
        if (rate <= -1) return false;
        if (rate == 0) return true;
        return (getGlobalTick() % rate) != 0;
    }

    /**
     * 检查矿车是否可以跳过部分计算
     * 空载矿车可以跳过碰撞检测
     */
    public static boolean shouldOptimizeMinecart(boolean hasPassenger, boolean hasCargo) {
        if (!TechnicalMCOptimizerConfig.enabled || !TechnicalMCOptimizerConfig.minecartTickOptimization) return false;
        return !hasPassenger && !hasCargo;
    }

    /**
     * 检查线实体是否应该优化tick
     * 刷线机产生的线实体可以减少碰撞检测
     */
    public static boolean shouldOptimizeStringEntity(boolean isPartOfMachine) {
        if (!TechnicalMCOptimizerConfig.enabled || !TechnicalMCOptimizerConfig.stringDuperOptimization) return false;
        if (isPartOfMachine) {
            stringEntityTicksOptimized.increment();
            return true;
        }
        return false;
    }

    /**
     * 检查末影珍珠是否应该优化
     * 珍珠炮发射的珍珠可以减少不必要计算
     */
    public static boolean shouldOptimizeEnderPearl(boolean isFromCannon) {
        if (!TechnicalMCOptimizerConfig.enabled || !TechnicalMCOptimizerConfig.pearlCannonOptimization) return false;
        if (isFromCannon) {
            pearlTicksOptimized.increment();
            return true;
        }
        return false;
    }

    /**
     * 提交活塞更新到批量处理器
     * @return true 表示本次更新被批量合并，调用者可以跳过立即处理
     */
    public static boolean batchPistonUpdate(long chunkKey) {
        if (!TechnicalMCOptimizerConfig.enabled || !TechnicalMCOptimizerConfig.pistonUpdateBatch) return false;
        pistonUpdatesBatched.increment();
        Integer count = pistonBatchCounter.merge(chunkKey, 1, Integer::sum);
        return count < TechnicalMCOptimizerConfig.pistonBatchRadius;
    }

    /**
     * 检查物品实体是否应该合并
     */
    public static boolean shouldMergeItemEntities(double distanceSq) {
        if (!TechnicalMCOptimizerConfig.enabled || !TechnicalMCOptimizerConfig.itemEntityMergeOptimization) return false;
        double maxDist = TechnicalMCOptimizerConfig.maxItemMergeDistance;
        return distanceSq <= (maxDist * maxDist);
    }

    /**
     * 获取区块tick实体限制
     */
    public static int getChunkTickEntityLimit() {
        if (!TechnicalMCOptimizerConfig.enabled) return 0;
        return TechnicalMCOptimizerConfig.chunkTickEntityLimit;
    }

    /**
     * 检查龙实体是否应该优化
     */
    public static boolean shouldOptimizeDragon(boolean isDragon) {
        if (!TechnicalMCOptimizerConfig.enabled || !TechnicalMCOptimizerConfig.dragonKillerOptimization) return false;
        return isDragon;
    }

    private static long getGlobalTick() {
        return System.currentTimeMillis() / 50; // ~20 TPS
    }

    /**
     * 定期清理计数器
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > 60_000) {
            pistonBatchCounter.clear();
            lastCleanup = now;
        }
    }

    public static Map<String, Object> getStats() {
        return Map.of(
                "enabled", TechnicalMCOptimizerConfig.enabled,
                "items_merged", itemsMerged.sum(),
                "rail_updates_batched", railUpdatesBatched.sum(),
                "piston_updates_batched", pistonUpdatesBatched.sum(),
                "entity_ticks_skipped", entityTicksSkipped.sum(),
                "pearl_ticks_optimized", pearlTicksOptimized.sum(),
                "string_ticks_optimized", stringEntityTicksOptimized.sum(),
                "piston_batch_regions", pistonBatchCounter.size()
        );
    }

    public static void shutdown() {
        pistonBatchCounter.clear();
    }
}
