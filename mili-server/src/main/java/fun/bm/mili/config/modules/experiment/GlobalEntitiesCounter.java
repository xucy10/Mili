package fun.bm.mili.config.modules.experiment;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(name = "global_entities_counter", category = EnumConfigCategory.EXPERIMENT)
public class GlobalEntitiesCounter implements IConfigModule {
    @HotReloadUnsupported
    @ConfigInfo(name = "enabled", comments = """
            启用全局实体计数器。
            需要在 paper-world-defaults.yml 或 paper-world.yml 中将 per-player-mob-spawns 设为 false""")
    public static boolean enabled = false;

    @HotReloadUnsupported
    @ConfigInfo(name = "async", comments = "启用异步模式（可能导致错误）")
    public static boolean async = false;

    @HotReloadUnsupported
    @ConfigInfo(name = "always_count", comments = """
            始终统计实体。
            如果你想统计由区块加载器加载的实体，
            必须启用此选项。""")
    public static boolean alwaysCount = false;
}