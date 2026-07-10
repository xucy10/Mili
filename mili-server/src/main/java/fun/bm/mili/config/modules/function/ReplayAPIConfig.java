package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "replay-api")
public class ReplayAPIConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable Replay API support") public static boolean enabled = false;
    @ConfigInfo(name = "enable-cache", comments = "Enable photographer cache") public static boolean enableCache = false;
    @ConfigInfo(name = "cache-photographer-time", comments = "Cache photographer time") public static long cachePhotographerTime = 300L;
    @ConfigInfo(name = "cache-photographer-size", comments = "Cache photographer size") public static long cachePhotographerSize = 100L;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
