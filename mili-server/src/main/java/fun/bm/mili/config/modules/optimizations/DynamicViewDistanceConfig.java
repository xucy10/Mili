package fun.bm.mili.config.modules.optimizations;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.utils.DynamicViewDistanceManager;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "dynamic-view-distance")
public class DynamicViewDistanceConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用每玩家动态视距""")
    public static boolean enabled = false;

    @ConfigInfo(name = "min-view-distance", comments = """
            最小视距""")
    public static int minViewDistance = 4;

    @ConfigInfo(name = "max-view-distance", comments = """
            最大视距""")
    public static int maxViewDistance = 16;

    @ConfigInfo(name = "tps-high-threshold", comments = """
            TPS 高于此值时增加视距""")
    public static double tpsHighThreshold = 19.0;

    @ConfigInfo(name = "tps-low-threshold", comments = """
            TPS 低于此值时减少视距""")
    public static double tpsLowThreshold = 17.0;

    @ConfigInfo(name = "adjust-interval-seconds", comments = """
            调整间隔（秒）""")
    public static int adjustIntervalSeconds = 30;

    @ConfigInfo(name = "player-density-weight", comments = """
            玩家密度权重（越高越倾向降低视距）""")
    public static double playerDensityWeight = 0.5;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        DynamicViewDistanceManager.setEnabled(enabled);
        // Mili start - fix: DynamicViewDistanceManager.tick() had no caller anywhere, so enabling
        // this module never actually adjusted anything — it was a dead switch. The driver lives in
        // MiliOptimizations.init() (which runs on the first region tick, after world load), because
        // scheduling from here may be too early. This best-effort attempt only matters for the
        // case where the switch is flipped on a hot reload of an already-running server.
        if (enabled) {
            startTicking();
        }
        // Mili end
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        DynamicViewDistanceManager.setEnabled(false);
        stopTicking();
    }

    // Mili start - fix: periodic driver for the manager (was missing entirely).
    // Must NOT be Bukkit.getPluginManager().getPlugin("Mili"): Mili is a server core, not a
    // registered Bukkit plugin, so that lookup always returns null. MinecraftInternalPlugin is
    // instance the built-ins use for Folia schedulers (see FoliaSchedulerAdapter#scheduleBootstrap).
    @DoNotLoad
    private static volatile io.papermc.paper.threadedregions.scheduler.ScheduledTask adapTickTask = null;

    /** 幂等。由 {@code MiliOptimizations.init()} 在服务端就绪后调用，是本功能的正规启动点。 */
    public static synchronized void startTicking() {
        if (adapTickTask != null) return;
        try {
            // Poll every second and let the manager's own CAS gate honour adjustIntervalSeconds:
            // baking the interval into the scheduler period would freeze it at whatever it was
            // when the config loaded, so hot-reloading adjust-interval-seconds down would be
            // silently ignored.
            adapTickTask = org.bukkit.Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    org.leavesmc.leaves.plugin.MinecraftInternalPlugin.INSTANCE,
                    task -> DynamicViewDistanceManager.tick(), 20L, 20L);
        } catch (Throwable throwable) {
            // 服务端尚未就绪（配置加载早于世界加载），由 MiliOptimizations.init() 兜底启动
            org.bukkit.Bukkit.getLogger().warning("[Mili VD] 动态视距暂未启动，等待服务端就绪后重试");
        }
    }

    public static synchronized void stopTicking() {
        if (adapTickTask != null) {
            adapTickTask.cancel();
            adapTickTask = null;
        }
    }
    // Mili end
}
