package fun.bm.mili.config.modules.optimizations;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.utils.EntityDirtyTracker;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "entity-dirty-tracking")
public class EntityDirtyTrackingConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用实体脏标记系统，跳过未变化实体的 tick""")
    public static boolean enabled = false;

    @ConfigInfo(name = "position-threshold", comments = """
            位置变化阈值（方块），低于此值视为未移动""")
    public static double positionThreshold = 0.001;

    @ConfigInfo(name = "check-interval", comments = """
            脏标记检查间隔（tick）""")
    public static int checkInterval = 1;

    @ConfigInfo(name = "skip-idle-entities", comments = """
            跳过连续 N tick 无变化的实体""")
    public static int skipIdleAfterTicks = 20;

    @ConfigInfo(name = "max-skip-ratio", comments = """
            最大跳过比例（0.0-1.0），防止所有实体被跳过""")
    public static double maxSkipRatio = 0.8;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        EntityDirtyTracker.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        EntityDirtyTracker.setEnabled(false);
    }
}
