package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.command.RedstoneStatsCommand;
import fun.bm.mili.utils.RedstoneStats;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "redstone-stats")
public class RedstoneStatsConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用红石统计面板""")
    public static boolean enabled = false;

    @ConfigInfo(name = "track-pistons", comments = """
            追踪活塞使用次数""")
    public static boolean trackPistons = true;

    @ConfigInfo(name = "track-updates", comments = """
            追踪方块更新次数""")
    public static boolean trackUpdates = true;

    @ConfigInfo(name = "track-bud", comments = """
            追踪 BUD（方块更新检测）触发次数""")
    public static boolean trackBud = true;

    @ConfigInfo(name = "track-redstone-wire", comments = """
            追踪红石线信号变化次数""")
    public static boolean trackRedstoneWire = true;

    @ConfigInfo(name = "save-interval", comments = """
            统计数据保存间隔（秒）""")
    public static int saveInterval = 300;

    @DoNotLoad
    private static RedstoneStatsCommand command = null;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            RedstoneStats.setEnabled(true);
            if (command == null) {
                command = new RedstoneStatsCommand();
            }
            command.register();
        }
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        RedstoneStats.setEnabled(false);
        if (command != null) {
            command.unregister();
        }
    }
}
