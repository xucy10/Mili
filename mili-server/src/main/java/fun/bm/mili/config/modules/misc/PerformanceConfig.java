package fun.bm.mili.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * mili - Performance tuning hints for Folia region-based threading.
 *
 * <p>This does NOT modify Folia's scheduler. It only provides recommendations
 * logged at startup based on the detected CPU topology. The actual thread
 * count is controlled by Folia's {@code paper-global.yml}.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "performance-tuning")
public class PerformanceConfig implements IConfigModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("mili|PerfTuning");

    @ConfigInfo(name = "suggested-thread-count", comments = """
            Suggested Folia region thread count. 0 = auto-calculate based on CPU cores.
            This value is NOT enforced by Lophine; it is only logged as a recommendation
            at server startup. To apply it, set the same value in paper-global.yml under
            'region-scheduler.thread-count'. For technical servers with clustered players
            and large machines, fewer threads often perform better than more.""")
    public static int suggestedThreadCount = 0;

    @ConfigInfo(name = "log-thread-recommendation", comments = """
            If true, logs the recommended Folia thread count at server startup.
            Useful for first-time setup; disable once you have configured paper-global.yml.""")
    public static boolean logThreadRecommendation = true;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (!logThreadRecommendation) return;

        int processors = Runtime.getRuntime().availableProcessors();
        int recommended = calculateOptimalThreads(processors);

        if (suggestedThreadCount > 0) {
            recommended = suggestedThreadCount;
            LOGGER.info("User-configured thread count: {} (CPU cores: {})", recommended, processors);
        } else {
            LOGGER.info("Recommended Folia thread count: {} (CPU cores: {})", recommended, processors);
        }

        LOGGER.info("To apply: set 'region-scheduler.thread-count' to {} in paper-global.yml", recommended);

        if (processors >= 8) {
            LOGGER.info("Technical server tip: With {} cores, consider running fewer threads for better per-thread performance", processors);
        }
    }

    /**
     * Calculate optimal thread count for Folia's region scheduler on technical servers.
     *
     * <p>Technical servers tend to have players clustered around large machines,
     * so fewer threads with better per-thread performance is often preferable
     * to many threads with higher synchronization overhead.
     */
    private static int calculateOptimalThreads(int processors) {
        if (processors <= 4) {
            // Small server: use all but one core
            return Math.max(2, processors - 1);
        } else if (processors <= 8) {
            // Medium server: leave one core for main thread + GC
            return processors - 1;
        } else if (processors <= 16) {
            // Large server: leave two cores for overhead
            return processors - 2;
        } else {
            // Very large server: cap at 32 to avoid diminishing returns
            return Math.min(processors - 4, 32);
        }
    }
}
