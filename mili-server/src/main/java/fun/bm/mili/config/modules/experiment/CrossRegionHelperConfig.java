package fun.bm.mili.config.modules.experiment;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "cross_region_helper")
public class CrossRegionHelperConfig implements IConfigModule {
    @HotReloadUnsupported
    @ConfigInfo(name = "enabled", comments = """
            Enable cross-region helper thread.
            This thread does NOT process any game logic itself.
            It only coordinates cross-region events (redstone, entity transfer, etc.)
            by collecting events from source regions and routing them to target regions.
            Actual logic is always executed on the target region's own tick thread.
            """)
    public static boolean enabled = false;

    @ConfigInfo(name = "queue-poll-timeout-ms", comments = "How long the helper thread waits for new events before checking again")
    public static int queuePollTimeoutMs = 50;

    @ConfigInfo(name = "max-pending-events-per-region", comments = "Max pending events queued for a single target region before dropping old ones")
    public static int maxPendingEventsPerRegion = 1024;
}
