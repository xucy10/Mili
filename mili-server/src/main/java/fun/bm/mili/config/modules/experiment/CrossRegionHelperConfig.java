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
            启用跨区域辅助线程。
            此线程本身不处理任何游戏逻辑。
            它仅协调跨区域事件（红石、实体传送等），
            通过从源区域收集事件并将其路由到目标区域。
            实际逻辑始终在目标区域自身的 tick 线程上执行。
            """)
    public static boolean enabled = false;

    @ConfigInfo(name = "queue-poll-timeout-ms", comments = "辅助线程等待新事件的最长时间，超时后重新检查")
    public static int queuePollTimeoutMs = 50;

    @ConfigInfo(name = "max-pending-events-per-region", comments = "单个目标区域排队的最大待处理事件数，超出后丢弃旧事件")
    public static int maxPendingEventsPerRegion = 1024;
}