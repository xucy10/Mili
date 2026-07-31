package fun.bm.mili.config.modules.optimizations;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.utils.DynamicViewDistanceManager;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "dynamic-view-distance")
public class DynamicViewDistanceConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用每玩家动态视距""")
    public static boolean enabled = false;

    @ConfigInfo(name = "min-view-distance", comments = """
            最小视距""")
    public static int minViewDistance = 4;

    @ConfigInfo(name = "max-view-distance", comments = """
            最大视距""")
    public static int maxViewDistance = 16;

    @ConfigInfo(name = "tps-high-threshold", comments = """
            TPS 高于此值时增加视距""")
    public static double tpsHighThreshold = 19.0;

    @ConfigInfo(name = "tps-low-threshold", comments = """
            TPS 低于此值时减少视距""")
    public static double tpsLowThreshold = 17.0;

    @ConfigInfo(name = "adjust-interval-seconds", comments = """
            调整间隔（秒）""")
    public static int adjustIntervalSeconds = 30;

    @ConfigInfo(name = "player-density-weight", comments = """
            玩家密度权重（越高越倾向降低视距）""")
    public static double playerDensityWeight = 0.5;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        DynamicViewDistanceManager.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        DynamicViewDistanceManager.setEnabled(false);
    }
}
