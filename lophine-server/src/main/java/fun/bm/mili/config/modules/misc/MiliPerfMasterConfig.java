package fun.bm.mili.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.perf.MiliAffinityAutoTuner;
import fun.bm.mili.perf.MiliRegionLoadMonitor;
import fun.bm.mili.perf.MiliTickProfiler;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * mili - Master switch for all Lophine performance modules.
 *
 * <p>Setting "enabled" here to false disables all Lophine performance
 * optimisations at once. The per-module enabled flags are still
 * respected when this is true, so you can enable the master switch
 * and tune individual modules from the loaded configuration.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "lophine-perf")
public class MiliPerfMasterConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            Master switch for all Lophine performance modules. When false,
            affinity auto-tuning, region load monitoring, and the tick
            profiler are all disabled regardless of their individual
            config values.""")
    public static boolean enabled = true;

    @ConfigInfo(name = "auto-tune-affinity", comments = """
            Convenience switch mirroring MiliAffinityAutoTuner.autoTuneEnabled.
            When this is changed, the underlying value is also updated.""")
    public static boolean autoTuneAffinity = true;

    @ConfigInfo(name = "region-load-monitor", comments = """
            Convenience switch mirroring MiliRegionLoadMonitor.enabled.""")
    public static boolean regionLoadMonitor = true;

    @ConfigInfo(name = "tick-profiler", comments = """
            Convenience switch mirroring MiliTickProfiler.enabled.
            WARNING: enabling the profiler in production is safe (~50ns
            per sample) but the periodic summary logs can be verbose.""")
    public static boolean tickProfiler = false;

    @Override
    public void onLoaded(@Nullable CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        applyToSubModules();
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        // No-op
    }

    /**
     * Apply master switch state to the per-module enabled flags. Called
     * by Lophine bootstrap when the config is (re)loaded. Setting the
     * module's enabled to false here is safe; Lophine code paths
     * short-circuit on the flag.
     */
    public static void applyToSubModules() {
        if (!enabled) {
            MiliAffinityAutoTuner.autoTuneEnabled = false;
            MiliRegionLoadMonitor.enabled = false;
            MiliTickProfiler.enabled = false;
            return;
        }
        MiliAffinityAutoTuner.autoTuneEnabled = autoTuneAffinity;
        MiliRegionLoadMonitor.enabled = regionLoadMonitor;
        MiliTickProfiler.enabled = tickProfiler;
    }
}
