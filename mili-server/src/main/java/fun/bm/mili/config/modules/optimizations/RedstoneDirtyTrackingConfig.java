package fun.bm.mili.config.modules.optimizations;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.utils.RedstoneDirtyTracker;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "redstone-dirty-tracking")
public class RedstoneDirtyTrackingConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用红石脏追踪，只更新有变化的红石线路""")
    public static boolean enabled = false;

    @ConfigInfo(name = "batch-size", comments = """
            每 tick 处理的最大脏方块数""")
    public static int batchSize = 1000;

    @ConfigInfo(name = "coalesce-radius", comments = """
            合并邻近更新的半径""")
    public static int coalesceRadius = 2;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        RedstoneDirtyTracker.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        RedstoneDirtyTracker.setEnabled(false);
    }
}
