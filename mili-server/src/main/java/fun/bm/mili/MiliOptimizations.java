package fun.bm.mili;

import fun.bm.mili.bridge.ChunkRegionBridge;
import fun.bm.mili.chunk.MiliChunkSystem;
import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import fun.bm.mili.config.modules.optimizations.NetworkOptimizerConfig;
import fun.bm.mili.config.modules.optimizations.TechnicalMCOptimizerConfig;
import fun.bm.mili.config.modules.optimizations.VillagerOptimizerConfig;
import fun.bm.mili.utils.*;
import fun.bm.mili.villager.VillagerOptimizer;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Mili 优化系统总初始化入口
 * 管理所有优化子系统的生命周期:
 * - 区块/区域管理 (ChunkSystem, RegionBalancer, SmartRegionManager)
 * - 实体优化 (VillagerOptimizer, EntityDirtyTracker)
 * - 网络优化 (NetworkOptimizer)
 * - 生电优化 (TechnicalMCOptimizer)
 * - 延迟缓解 (LagRemover)
 */
public final class MiliOptimizations {
    private static final Logger LOGGER = Logger.getLogger("Mili");

    private MiliOptimizations() {}

    public static void init(Plugin plugin) {
        // 核心延迟缓解
        LagRemover.init(plugin);

        // 村民优化
        if (VillagerOptimizerConfig.enabled) {
            VillagerOptimizer.init(plugin);
        }

        // 区块系统
        if (ChunkSystemConfig.enabled) {
            MiliChunkSystem.init(plugin);
        }

        // 区域管理
        if (RegionBalancerConfig.enabled || ChunkSystemConfig.enabled) {
            ChunkRegionBridge.init();
        }
        if (RegionBalancerConfig.enabled) {
            RegionBalancer.init();
            SmartRegionManager.init();
        }

        // 网络优化
        if (NetworkOptimizerConfig.enabled) {
            NetworkOptimizer.init();
        }

        // 生电优化
        if (TechnicalMCOptimizerConfig.enabled) {
            TechnicalMCOptimizer.init();
        }

        LOGGER.info("[Mili] Optimizations initialized (v3.1)");
    }

    public static void shutdown() {
        AsyncKeepaliveManager.shutdown(); // Mili - graceful shutdown of async keepalive scheduler
        // Mili start - fix: only shutdown subsystems that were initialized (config enabled)
        if (ChunkSystemConfig.enabled) {
            MiliChunkSystem.shutdown();
        }
        if (VillagerOptimizerConfig.enabled) {
            VillagerOptimizer.shutdown();
        }
        LagRemover.shutdown();
        if (RegionBalancerConfig.enabled) {
            RegionBalancer.shutdown();
            SmartRegionManager.shutdown();
        }
        if (RegionBalancerConfig.enabled || ChunkSystemConfig.enabled) {
            ChunkRegionBridge.shutdown();
        }
        if (NetworkOptimizerConfig.enabled) {
            NetworkOptimizer.shutdown();
        }
        if (TechnicalMCOptimizerConfig.enabled) {
            TechnicalMCOptimizer.shutdown();
        }
        // Mili end

        LOGGER.info("[Mili] All optimizations shutdown");
    }
}