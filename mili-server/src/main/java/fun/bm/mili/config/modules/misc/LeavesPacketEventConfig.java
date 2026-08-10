package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "leaves_packet_event")
public class LeavesPacketEventConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用 Leaves 数据包事件 API，用于自定义数据包拦截。
            默认禁用——仅在需要数据包事件 API 时启用。""")
    public static boolean enabled = false;
}
