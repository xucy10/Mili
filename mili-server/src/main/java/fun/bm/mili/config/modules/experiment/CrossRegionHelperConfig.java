package fun.bm.mili.config.modules.experiment;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "cross-region-helper")
public class CrossRegionHelperConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable cross-region event helper") public static boolean enabled = false;
    @ConfigInfo(name = "queue-poll-timeout-ms", comments = "Poll timeout in ms") public static long queuePollTimeoutMs = 100L;
    @ConfigInfo(name = "max-pending-events-per-region", comments = "Max pending events per region") public static int maxPendingEventsPerRegion = 256;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
