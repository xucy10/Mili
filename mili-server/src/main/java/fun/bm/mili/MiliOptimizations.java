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

    /**
     * Mili start - fix: exactly one lifecycle entry point, and it must be idempotent.
     * <p>
     * The entry point used to be an external bootstrap patch that injected into
     * {@code DedicatedServer} right after the config files were loaded — i.e. <b>before the
     * worlds existed</b>.  That patch has been retired in favour of the self-bootstrap in
     * {@code FoliaSchedulerAdapter#isRunning()}, which runs on the first region tick and
     * therefore <i>after</i> world load (world-scanning subsystems such as
     * {@code VillagerOptimizer} used to receive an empty world list).
     * <p>
     * The guard lives here, not at the call site, so that a retried bootstrap can never
     * double-start a subsystem.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean INITIALIZED =
            new java.util.concurrent.atomic.AtomicBoolean();
    // Mili end

    /**
     * Mili start - fix: shutdown used to be dead code (zero callers) and is now reachable
     * from the JVM shutdown hook registered in {@link #init(Plugin)}; it must therefore be
     * idempotent — the hook and any future explicit caller must not tear down twice.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean SHUTDOWN =
            new java.util.concurrent.atomic.AtomicBoolean();
    // Mili end

    private MiliOptimizations() {}

    public static void init(Plugin plugin) {
        // Mili start - fix: idempotent entry point (see INITIALIZED).  This is also where
        // the shutdown hook is registered: shutdown() previously had zero callers anywhere
        // in the repository, so the runtime was never torn down in an orderly way.  The
        // hook goes in immediately after the CAS so even a partially failed init gets a
        // best-effort cleanup; shutdown() is idempotent, so double invocation is safe.
        if (!INITIALIZED.compareAndSet(false, true)) {
            LOGGER.fine("[Mili] Optimizations already initialized; duplicate init ignored");
            return;
        }
        Runtime.getRuntime().addShutdownHook(
                new Thread(MiliOptimizations::shutdown, "Mili-Optimizations-Shutdown"));
        // Mili end
        // Mili start - unified runtime: scheduler + worker pool must be up before any
        // subsystem that submits region work (fix.md §19 phase 1).
        fun.bm.mili.scheduler.FoliaSchedulerAdapter.init();
        // Mili end

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
        // Mili start - fix: idempotent (see SHUTDOWN).
        if (!SHUTDOWN.compareAndSet(false, true)) {
            LOGGER.fine("[Mili] Optimizations already shut down; duplicate shutdown ignored");
            return;
        }
        // Mili end
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

        // Mili start - shut the unified runtime down last: every region runtime is torn
        // down through the fix.md §9 order (deactivate -> drain -> cancel -> unregister).
        fun.bm.mili.scheduler.FoliaSchedulerAdapter.shutdown();
        // Mili end

        LOGGER.info("[Mili] All optimizations shutdown");
    }
}