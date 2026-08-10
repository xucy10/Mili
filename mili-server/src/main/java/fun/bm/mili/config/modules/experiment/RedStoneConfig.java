package fun.bm.mili.config.modules.experiment;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

/*
 * 这是实验性级别的红石配置模块
 * 如果我们认为此处的配置在将来足够稳定，会将它们移动到 function 模块目录
 */
@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "redstone")
public class RedStoneConfig implements IConfigModule {
    @TransformedConfig(name = "enabled", directory = {"experiment", "redstone-ignore-upwards-update"})
    @ConfigInfo(name = "redstone-ignore-upwards-update", comments = """
            是否重新引入 1.20 之前的机制：
            红石粉不会连接到相邻打开活板门上的红石粉
            1.20.2 之前的机制：红石粉、红石中继器和红石比较器在接收来自下方的状态更新时不检查附着""")
    public static boolean redstoneIgnoreUpwardsUpdate = false;

    @TransformedConfig(name = "enabled", directory = {"experiment", "cce-update-suppression"})
    @ConfigInfo(name = "cce-update-suppression", comments = """
            是否允许使用 ClassCastException 进行更新抑制？""")
    public static boolean cce = false;

    @HotReloadUnsupported
    @ConfigInfo(name = "instant-block-updater")
    public static boolean instantBlockUpdater = false;

    @ConfigInfo(name = "old-block-remove-behaviour")
    public static boolean oldBlockRemoveBehaviour = false;

    @ConfigInfo(name = "tps-throttle-enabled", comments = "启用基于 TPS 的红石更新节流")
    public static boolean tpsThrottleEnabled = false;

    @ConfigInfo(name = "tps-throttle-threshold", comments = "低于此 TPS 阈值时开始节流红石更新")
    public static double tpsThrottleThreshold = 18.0;

    @ConfigInfo(name = "tps-throttle-skip-chance", comments = "当 TPS 低于阈值时跳过红石更新的概率（0.0-1.0）")
    public static double tpsThrottleSkipChance = 0.1;
}