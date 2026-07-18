package fun.bm.mili.config.modules.fixes;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FIXES, name = "update-suppression-crash-fix")
public class UpdateSuppressionCrashFixConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            是否阻止由更新抑制引起的崩溃？""")
    public static boolean enabled = true;
}