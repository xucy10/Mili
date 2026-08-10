package fun.bm.mili.config.modules.function;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "redstone")
public class RedstoneUpdateConfig implements IConfigModule {
    @TransformedConfig(name = "locked_hopper_no_nc_update", directory = {"function", "redstone"}, transformComments = false)
    @ConfigInfo(name = "placing-locked-hopper-no-nc-updates", comments =
            """
                    放置锁定的漏斗不再发送邻居方块更新。
                    这可以防止放置锁定漏斗时破坏红石装置。""")
    public static boolean placingLockedHopperNoNCUpdates = false;
}
