package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "redstone-function")
public class RedStoneConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable redstone function") public static boolean enabled = false;
    @ConfigInfo(name = "cce", comments = "Shulker box CCE reintroduced") public static boolean cce = false;
    @ConfigInfo(name = "shears", comments = "Enable shears wrench") public static boolean shears = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
