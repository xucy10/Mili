package fun.bm.mili.config.modules.function;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.utils.RandomProfilePool;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "replay-api")
public class ReplayAPIConfig implements IConfigModule {
    @HotReloadUnsupported
    @ConfigInfo(name = "enable-cache")
    public static boolean enableCache = true;

    @HotReloadUnsupported
    @ConfigInfo(name = "cache-photographer-time", comments = """
            缓存摄影师资料的时间（秒）""")
    public static int cachePhotographerTime = 3600;

    @HotReloadUnsupported
    @ConfigInfo(name = "cache-photographer-size", comments = """
            缓存摄影师资料的最大数量""")
    public static int cachePhotographerSize = 100;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> e) {
        RandomProfilePool.init();
    }
}