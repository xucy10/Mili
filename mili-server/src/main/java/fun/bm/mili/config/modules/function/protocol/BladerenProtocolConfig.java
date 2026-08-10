package fun.bm.mili.config.modules.function.protocol;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "bladeren", directory = {"protocol"})
public class BladerenProtocolConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用 bladeren 协议支持""")
    public static boolean enabled = true;

    @ConfigInfo(name = "mspt-sync-protocol", comments = """
            启用 MSPT 同步协议""")
    public static boolean msptSyncProtocol = false;

    @ConfigInfo(name = "mspt-sync-tick-interval", comments = """
            MSPT 同步 tick 间隔（必须 > 0）""")
    public static int msptSyncTickInterval = 20;
}
