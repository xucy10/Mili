package fun.bm.mili.config.modules.experiment;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

/*
 * This is a config module for redstone in experimental level
 * If we think configs from here is stable for future, we will move them to function module directory
 */
@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "redstone")
public class RedStoneConfig implements IConfigModule {
    @TransformedConfig(name = "enabled", directory = {"experiment", "redstone-ignore-upwards-update"})
    @ConfigInfo(name = "redstone-ignore-upwards-update", comments = """
            Should the pre-1.20 mechanism be reintroduced: 
            Redstone dust does not connect to adjacent redstone dust on trapdoors that are open      
            Pre-1.20.2 mechanism: Redstone dust, redstone repeaters, and redstone comparators do not check for attachment when receiving status updates from below""")
    public static boolean redstoneIgnoreUpwardsUpdate = false;

    @TransformedConfig(name = "enabled", directory = {"experiment", "cce-update-suppression"})
    @ConfigInfo(name = "cce-update-suppression", comments = """
            Is it permissible to use ClassCastException for update suppression?""")
    public static boolean cce = false;

    @HotReloadUnsupported
    @ConfigInfo(name = "instant-block-updater")
    public static boolean instantBlockUpdater = false;

    @ConfigInfo(name = "old-block-remove-behaviour")
    public static boolean oldBlockRemoveBehaviour = false;

    @ConfigInfo(name = "max-redstone-update-depth", comments = """
            Maximum depth for redstone neighbor updates before the server stops
            processing further updates for that tick. Vanilla has no explicit
            limit (relies on StackOverflowError). Setting a value > 0 prevents
            StackOverflow crashes from update suppression while still allowing
            deep redstone chains to function. Set to 0 for vanilla behavior
            (unlimited, may crash on deep update chains). Recommended: 512-1024
            for technical servers that need update suppression protection.""")
    public static int maxRedstoneUpdateDepth = 0;

    @ConfigInfo(name = "tps-aware-redstone-throttle", comments = """
            If true, redstone updates are throttled when the server TPS drops
            below 'tps-aware-redstone-threshold'. This prevents redstone
            contraptions from consuming excessive tick time during lag spikes.
            Critical for stability on busy technical servers with many large
            redstone circuits.""")
    public static boolean tpsAwareRedstoneThrottle = false;

    @ConfigInfo(name = "tps-aware-redstone-threshold", comments = """
            TPS threshold below which redstone updates start being throttled.
            Only relevant when 'tps-aware-redstone-throttle' is true.
            Default 17.0 means throttling begins at 85% TPS.""")
    public static double tpsAwareRedstoneThreshold = 17.0;

    @ConfigInfo(name = "preserve-update-order-on-chunk-load", comments = """
            If true, redstone updates triggered by chunk loading are processed
            in a deterministic order (by block position) rather than the
            default hash-based order. This helps technical players build
            reliable chunk-load-triggered circuits. May slightly increase
            tick time for chunk loads.""")
    public static boolean preserveUpdateOrderOnChunkLoad = false;
}
