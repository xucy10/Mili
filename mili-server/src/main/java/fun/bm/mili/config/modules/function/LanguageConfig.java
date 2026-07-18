package fun.bm.mili.config.modules.function;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "language")
public class LanguageConfig implements IConfigModule {
    @TransformedConfig(name = "lang", directory = {"optimizations", "language"})
    @ConfigInfo(name = "lang", comments = """
            请使用 https://minecraft.wiki/w/Language 中的语言键
            格式示例：en_us zh_cn zh_hk zh_tw""")
    public static String lang = "en_us";

    @ConfigInfo(name = "full_blocking_load", comments = """
            是否允许在加载本地化语言时阻塞服务器加载。
            如果你希望终端中只显示本地化语言，
            则需要启用此选项。
            
            警告：这可能会降低启动速度！""")
    public static boolean full_blocking_load = false;
}