package fun.bm.mili.config.modules.function;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.utils.StructureProjectionManager;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "structure-projection")
public class StructureProjectionConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用服务端结构投影""")
    public static boolean enabled = false;

    @ConfigInfo(name = "max-projections-per-player", comments = """
            每个玩家最大投影数""")
    public static int maxProjectionsPerPlayer = 5;

    @ConfigInfo(name = "show-collision-outlines", comments = """
            显示放置碰撞轮廓""")
    public static boolean showCollisionOutlines = true;

    @ConfigInfo(name = "projection-range", comments = """
            投影最大距离（方块）""")
    public static int projectionRange = 64;

    @ConfigInfo(name = "ghost-block-opacity", comments = """
            幽灵方块透明度（0-100）""")
    public static int ghostBlockOpacity = 50;

    @ConfigInfo(name = "allowed-worlds", comments = """
            允许投影的世界（空=全部）""")
    public static java.util.List<String> allowedWorlds = java.util.List.of();

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        StructureProjectionManager.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        StructureProjectionManager.setEnabled(false);
    }
}
