package fun.bm.mili.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "auto-update")
public class AutoUpdateConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable auto update checks") public static boolean enabled = true;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
