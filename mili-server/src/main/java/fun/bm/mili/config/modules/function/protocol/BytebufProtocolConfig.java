package fun.bm.mili.config.modules.function.protocol;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "bytebuf", directory = {"protocol"})
public class BytebufProtocolConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用 bytebuf API 用于自定义数据包处理""")
    public static boolean enabled = true;
}
