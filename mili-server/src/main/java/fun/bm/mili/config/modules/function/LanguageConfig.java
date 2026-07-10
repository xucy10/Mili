package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "language")
public class LanguageConfig implements ConfigModule {
    @ConfigInfo(name = "locale", comments = "Server language locale") public static String locale = "en_us";
    public static String lang = "en_us";
    @ConfigInfo(name = "full-blocking-load", comments = "Enable full blocking language load") public static boolean full_blocking_load = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
