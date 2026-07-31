package fun.bm.mili.config.modules.optimizations;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.utils.EntityDensityTracker;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "entity-density-heatmap")
public class EntityDensityHeatmapConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用实体密度热力图（需要 Rust 加速）""")
    public static boolean enabled = false;

    @ConfigInfo(name = "cell-size", comments = """
            网格单元格大小（方块）""")
    public static int cellSize = 16;

    @ConfigInfo(name = "update-interval", comments = """
            更新间隔（tick）""")
    public static int updateInterval = 20;

    @ConfigInfo(name = "max-density-threshold", comments = """
            高密度警告阈值""")
    public static int maxDensityThreshold = 50;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        EntityDensityTracker.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        EntityDensityTracker.setEnabled(false);
    }
}
