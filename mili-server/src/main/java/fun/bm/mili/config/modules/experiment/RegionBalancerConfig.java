package fun.bm.mili.config.modules.experiment;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "region_balancer")
public class RegionBalancerConfig implements ConfigModule {
    @HotReloadUnsupported
    @ConfigInfo(name = "enabled", comments = """
            Enable adaptive region tick balancer.
            Replaces per-region dedicated threads with a fixed-size thread pool
            and priority scheduling based on region load.
            Benefits: fewer context switches, better CPU utilization, stable TPS.
            """)
    public static boolean enabled = false;

    @ConfigInfo(name = "thread-pool-size", comments = """
            Number of worker threads for region ticking.
            Default is CPU cores * 2.  Set to 0 for auto-detect.
            """)
    public static int threadPoolSize = 0;

    @ConfigInfo(name = "history-window-size", comments = "How many recent ticks to average for load calculation")
    public static int historyWindowSize = 10;

    @ConfigInfo(name = "high-load-threshold-ms", comments = "Region tick time above this is considered high-load")
    public static double highLoadThresholdMs = 20.0;

    @ConfigInfo(name = "low-load-threshold-ms", comments = "Region tick time below this is considered low-load")
    public static double lowLoadThresholdMs = 2.0;

    @ConfigInfo(name = "max-tick-catchup", comments = "Max ticks a region can catch up in one execution")
    public static int maxTickCatchup = 3;

    @ConfigInfo(name = "idle-skip-ticks", comments = "Low-load regions can skip this many ticks between execution")
    public static int idleSkipTicks = 1;

    @ConfigInfo(name = "merge-batch-soft-limit", comments = "Soft upper bound for low-load region merge batch size")
    public static int mergeBatchSoftLimit = 4;

    @ConfigInfo(name = "merge-batch-hard-limit", comments = "Hard upper bound for low-load region merge batch size")
    public static int mergeBatchHardLimit = 8;

    public static int getThreadPoolSize() {
        return threadPoolSize > 0 ? threadPoolSize : Runtime.getRuntime().availableProcessors() * 2;
    }

    @Override
    public void onLoaded(CommentedFileConfig configInstance) {}

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {}
}