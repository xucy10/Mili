package fun.bm.mili;

import fun.bm.mili.bridge.ChunkRegionBridge;
import fun.bm.mili.chunk.MiliChunkSystem;
import fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig;
import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import fun.bm.mili.config.modules.optimizations.NetworkOptimizerConfig;
import fun.bm.mili.config.modules.optimizations.TechnicalMCOptimizerConfig;
import fun.bm.mili.config.modules.optimizations.VillagerOptimizerConfig;
import fun.bm.mili.scheduler.FoliaSchedulerAdapter;
import fun.bm.mili.utils.*;
import fun.bm.mili.villager.VillagerOptimizer;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Mili 优化系统总初始化入口
 * 管理所有优化子系统的生命周期:
 * - 区域调度 (Mili scheduler, RegionBalancer, SmartRegionManager)
 * - 区块/区域管理 (ChunkSystem, ChunkRegionBridge)
 * - 实体优化 (VillagerOptimizer, EntityDirtyTracker)
 * - 网络优化 (NetworkOptimizer)
 * - 生电优化 (TechnicalMCOptimizer)
 * - 延迟缓解 (LagRemover)
 *
 * <p><b>Note on lifecycle.</b> This method is the only entry point for every
 * subsystem listed above, so if nothing calls it none of them run at all. That is
 * exactly the state the subsystems were in: {@code init()} had zero call sites in
 * the repository, which meant the scheduler, chunk system, villager optimiser and
 * the rest were compiled, configured, reported on by {@code /mili perf} — and
 * never actually active. The bootstrap patch that calls this belongs with the
 * other feature patches; see {@code docs/SCHEDULER_ARCHITECTURE.md}.
 */
public final class MiliOptimizations {
    private static final Logger LOGGER = Logger.getLogger("Mili");

    private MiliOptimizations() {}

    public static void init(Plugin plugin) {
        // Scheduling infrastructure first: everything below either submits work
        // through it or installs a hook into it.
        installSchedulerHooks();
        FoliaSchedulerAdapter.init();

        // 跨区事件路由（需要在调度器之后，因为它会向调度器注册 region locator）
        if (CrossRegionHelperConfig.enabled) {
            CrossRegionHelper.installSchedulerLocator();
            CrossRegionHelper.init();
        }

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

        LOGGER.info("[Mili] Optimizations initialized (v3.2)");
    }

    /**
     * Install the per-region table cleanup hooks that the scheduler's destroy
     * sequence calls (fix.md §13 steps 7 and 8).
     * <p>
     * Without these, "region destroy" only cleans up state the scheduler itself
     * owns, and the components below keep their per-region entries until the JVM
     * exits. Every one of these cleanup methods already existed and was already
     * uncalled; this is where they get wired in.
     */
    private static void installSchedulerHooks() {
        FoliaSchedulerAdapter.installLifecycleHooks(
                (region, regionId) -> {
                    RegionLoadMonitor.removeById(regionId);
                    CrossRegionHelper.onRegionUnload(regionId);
                    RegionTaskIdRegistry.unregisterByScheduleRef(region);
                },
                // Nothing to close on the Minecraft side yet: the region's own
                // lifecycle is managed by Folia, and Mili does not own any context
                // that outlives it. This becomes non-null if Mili ever allocates
                // per-region native or pooled resources.
                null);
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

        // The scheduler holds work for every subsystem above, so it goes last:
        // cancelling its tasks before the producers have stopped would just make
        // them re-submit into a scheduler that is already draining.
        if (CrossRegionHelperConfig.enabled) {
            CrossRegionHelper.shutdown();
        }
        // Independent pool: stop it before the scheduler so no compute result is
        // produced for a region that is already shutting down.
        AsyncPathfinder.shutdown();
        FoliaSchedulerAdapter.shutdown();
        RegionLoadMonitor.clear();

        LOGGER.info("[Mili] All optimizations shutdown");
    }
}