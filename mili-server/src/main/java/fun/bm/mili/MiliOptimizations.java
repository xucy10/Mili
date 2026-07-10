package fun.bm.mili;

import fun.bm.mili.chunk.MiliChunkSystem;
import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import fun.bm.mili.config.modules.optimizations.VillagerOptimizerConfig;
import fun.bm.mili.utils.LagRemover;
import fun.bm.mili.villager.VillagerOptimizer;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Central initialization point for Mili optimizations.
 * Initializes all optimization systems including chunk management,
 * villager optimizer, TPS tracking, and lag removal.
 */
public final class MiliOptimizations {
    private static final Logger LOGGER = Logger.getLogger("Mili");

    private MiliOptimizations() {}

    public static void init() {
        LagRemover.init(null);

        if (VillagerOptimizerConfig.enabled) {
            VillagerOptimizer.init(null);
        }

        if (ChunkSystemConfig.enabled) {
            MiliChunkSystem.init();
        }

        LOGGER.info("[Mili] Optimizations initialized (v3.0)");
    }

    public static void shutdown() {
        MiliChunkSystem.shutdown();
        VillagerOptimizer.shutdown();
        LagRemover.shutdown();

        LOGGER.info("[Mili] All optimizations shutdown");
    }
}