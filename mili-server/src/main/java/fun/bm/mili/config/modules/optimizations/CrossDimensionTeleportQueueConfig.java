package fun.bm.mili.config.modules.optimizations;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.utils.CrossDimensionTeleportQueue;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "cross-dimension-teleport-queue")
public class CrossDimensionTeleportQueueConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用跨维度传送队列""")
    public static boolean enabled = false;

    @ConfigInfo(name = "max-queue-size", comments = """
            队列最大容量""")
    public static int maxQueueSize = 100;

    @ConfigInfo(name = "process-interval-ticks", comments = """
            队列处理间隔（tick）""")
    public static int processIntervalTicks = 1;

    @ConfigInfo(name = "priority-players", comments = """
            OP 玩家优先传送""")
    public static boolean priorityPlayers = true;

    @ConfigInfo(name = "timeout-seconds", comments = """
            传送超时（秒）""")
    public static int timeoutSeconds = 10;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        CrossDimensionTeleportQueue.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        CrossDimensionTeleportQueue.setEnabled(false);
    }
}
