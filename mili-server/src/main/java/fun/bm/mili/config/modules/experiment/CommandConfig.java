package fun.bm.mili.config.modules.experiment;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "command")
public class CommandConfig implements ConfigModule {
    @ConfigInfo(name = "function", comments = "Enable /function command") public static boolean function = true;
    @ConfigInfo(name = "scoreboard", comments = "Enable /scoreboard command") public static boolean scoreboard = true;
    @ConfigInfo(name = "tick", comments = "Enable /tick command") public static boolean tick = true;
    @ConfigInfo(name = "save-all", comments = "Enable /save-all command") public static boolean saveAll = true;
    @ConfigInfo(name = "log-all-process", comments = "Log all save-all process") public static boolean logAllProcess = false;
    @ConfigInfo(name = "save-all-timeout", comments = "Save-all timeout in ms") public static long saveAllTimeout = 60000L;
    @ConfigInfo(name = "waypoint", comments = "Enable waypoint command") public static boolean waypoint = true;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
