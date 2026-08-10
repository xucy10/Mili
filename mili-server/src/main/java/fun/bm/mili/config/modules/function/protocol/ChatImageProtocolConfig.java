package fun.bm.mili.config.modules.function.protocol;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "chat-image", directory = {"protocol"})
public class ChatImageProtocolConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用聊天图片协议""")
    public static boolean enabled = false;
}
