package fun.bm.mili.config.modules.function;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "redstone")
public class RedStoneConfig implements IConfigModule {
    @TransformedConfig(name = "shears_rotate", directory = {"misc", "redstone"})
    @TransformedConfig(name = "allow_skip_cooldown", directory = {"misc", "redstone"})
    @ConfigInfo(name = "shears_rotate", comments =
            """
                    允许使用剪刀右键旋转方块。""")
    public static boolean shears = false;
}