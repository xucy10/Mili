package fun.bm.mili.config.modules.function.protocol;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "rei", directory = {"protocol"})
public class REIServerProtocolConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用 Roughly Enough Items 协议支持""")
    public static boolean enabled = false;
}