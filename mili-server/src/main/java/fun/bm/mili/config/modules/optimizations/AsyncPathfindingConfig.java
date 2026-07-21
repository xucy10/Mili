package fun.bm.mili.config.modules.optimizations;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.utils.AsyncPathfinder;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "async-pathfinding")
public class AsyncPathfindingConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用异步寻路""")
    public static boolean enabled = false;

    @ConfigInfo(name = "thread-count", comments = """
            寻路线程池大小""")
    public static int threadCount = 2;

    @ConfigInfo(name = "max-queue-size", comments = """
            最大队列大小""")
    public static int maxQueueSize = 256;

    @ConfigInfo(name = "timeout-ms", comments = """
            寻路超时（毫秒）""")
    public static long timeoutMs = 5000;

    @ConfigInfo(name = "max-path-length", comments = """
            最大路径长度""")
    public static int maxPathLength = 1024;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        AsyncPathfinder.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        AsyncPathfinder.setEnabled(false);
    }
}
