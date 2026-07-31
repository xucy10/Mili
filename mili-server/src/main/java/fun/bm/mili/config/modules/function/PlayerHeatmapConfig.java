package fun.bm.mili.config.modules.function;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.command.HeatmapCommand;
import fun.bm.mili.utils.PlayerHeatmap;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "player-heatmap")
public class PlayerHeatmapConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用玩家活动热力图""")
    public static boolean enabled = false;

    @ConfigInfo(name = "track-interval", comments = """
            记录间隔（tick）""")
    public static int trackInterval = 1200;

    @ConfigInfo(name = "max-history-minutes", comments = """
            保留历史数据的最大分钟数""")
    public static int maxHistoryMinutes = 60;

    @ConfigInfo(name = "cell-size-blocks", comments = """
            热力图每个单元格的方块大小""")
    public static int cellSizeBlocks = 16;

    @ConfigInfo(name = "export-path", comments = """
            导出热力图数据的文件路径""")
    public static String exportPath = "plugins/Mili/heatmap/";

    @DoNotLoad
    private static HeatmapCommand command = null;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            PlayerHeatmap.setEnabled(true);
            if (command == null) {
                command = new HeatmapCommand();
            }
            command.register();
        }
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        PlayerHeatmap.setEnabled(false);
        if (command != null) {
            command.unregister();
        }
    }
}
