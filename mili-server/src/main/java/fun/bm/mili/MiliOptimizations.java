package fun.bm.mili;

import fun.bm.mili.config.modules.optimizations.VillagerOptimizerConfig;
import fun.bm.mili.utils.LagRemover;
import fun.bm.mili.villager.VillagerOptimizer;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Central initialization point for Mili optimizations.
 * Initializes villager optimizer, TPS tracker, and lag remover.
 */
public final class MiliOptimizations {
    private static final Logger LOGGER = Logger.getLogger("Mili");

    private MiliOptimizations() {}

    public static void init() {
        // Initialize TPS tracking and lag removal
        LagRemover.init(null);

        // Initialize villager optimizer if enabled
        if (VillagerOptimizerConfig.enabled) {
            VillagerOptimizer.init(null);
        }

        LOGGER.info("[Mili] Optimizations initialized");
    }

    public static void shutdown() {
        VillagerOptimizer.shutdown();
        LagRemover.shutdown();
    }
}