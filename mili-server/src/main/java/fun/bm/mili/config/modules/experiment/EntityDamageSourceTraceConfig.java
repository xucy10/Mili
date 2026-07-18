package fun.bm.mili.config.modules.experiment;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "entity_damage_source_trace")
public class EntityDamageSourceTraceConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments =
            """
                    允许跨不同区域调度器追踪伤害来源。""")
    public static boolean enabled = false;
}