package fun.bm.mili.config.modules.function.protocol;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "appleskin", directory = {"protocol"})
public class AppleSkinProtocolConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用 AppleSkin 协议支持""")
    public static boolean enabled = false;
    @ConfigInfo(name = "sync-tick-interval", comments = """
            设置 AppleSkin 同步频率（单位：游戏刻）""")
    public static int syncTickInterval = 20;
}