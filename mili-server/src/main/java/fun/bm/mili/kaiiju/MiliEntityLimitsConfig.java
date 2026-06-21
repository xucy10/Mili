package fun.bm.mili.kaiiju;

import fun.bm.mili.config.modules.misc.UnifiedSchedulerConfig;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(
    category = EnumConfigCategory.OPTIMIZATIONS,
    name = "kaiiju_entity_limits",
    comments = """
        Per-region entity limits (ported from Kaiiju).
        When enabled, if there are more entities of a given type in a
        region than the limit, entity ticking is throttled across ticks.
        Now Folia-aware: limits are applied per-tick-region, not globally."""
)
public class MiliEntityLimitsConfig implements IConfigModule {

    @ConfigInfo(name = "enabled", comments = "Enable Kaiiju entity limits")
    public static boolean enabled = false;

    @ConfigInfo(name = "wither_limit", comments = "Max Wither entities ticking per region per tick")
    public static int witherLimit = 100;

    @ConfigInfo(name = "wither_removal", comments = "Entity count that triggers removal (0 = disabled)")
    public static int witherRemoval = 0;

    @ConfigInfo(name = "ender_dragon_limit", comments = "Max Ender Dragon entities ticking per region per tick")
    public static int enderDragonLimit = 10;

    @ConfigInfo(name = "ender_dragon_removal", comments = "Entity count that triggers removal (0 = disabled)")
    public static int enderDragonRemoval = 0;

    @ConfigInfo(name = "iron_golem_limit", comments = "Max Iron Golem entities per region per tick")
    public static int ironGolemLimit = 200;

    @ConfigInfo(name = "iron_golem_removal", comments = "")
    public static int ironGolemRemoval = 0;

    @ConfigInfo(name = "default_limit", comments = "Default limit for entity types not explicitly configured")
    public static int defaultLimit = 500;

    @ConfigInfo(name = "default_removal", comments = "Default removal threshold")
    public static int defaultRemoval = 0;
}
