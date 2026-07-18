package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "disable-check")
public class DisableCheckConfig implements IConfigModule {
    @ConfigInfo(name = "disable-op-move-check", comments = """
            禁用 OP 移动检查""")
    public static boolean disableOpMoveCheck = false;

    @ConfigInfo(name = "disable-op-fly-check", comments = """
            禁用 OP 飞行检查""")
    public static boolean disableOpFlyCheck = false;
}