package fun.bm.mili.config.modules.experiment;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "entity-tracking-perf")
public class EntityTrackingPerfConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            Enable entity tracking broadcast optimization.
            When enabled, entity position/rotation/metadata broadcasts are
            throttled using a hash-based round-robin budget system, reducing
            network packet volume when many entities are near players.""")
    public static boolean enabled = false;

    @ConfigInfo(name = "max-updates-per-tick", comments = """
            Maximum number of entity tracking updates processed per tick.
            When the number of tracked entities exceeds this value, updates
            are distributed across multiple ticks using hash-based round-robin
            keyed on entity ID. Lower values reduce network latency at the
            cost of slightly less smooth entity movement.
            Set to 0 for unlimited (vanilla behavior).
            Recommended: 80-150 for servers with many entities.""")
    public static int maxUpdatesPerTick = 100;

    @ConfigInfo(name = "forced-update-interval", comments = """
            Maximum ticks an entity can go without sending a tracking update.
            Prevents permanent desync if an entity is consistently skipped
            by the budget system. After this many ticks without an update,
            the entity is force-sent regardless of budget constraints.""")
    public static int forcedUpdateInterval = 20;

    @ConfigInfo(name = "tps-throttle-threshold", comments = """
            TPS below which entity tracking updates are further reduced.
            When the region TPS drops below this value, the effective budget
            is scaled down proportionally. Set to 0 to disable TPS-aware
            throttling. Recommended: 16.0.""")
    public static double tpsThrottleThreshold = 16.0;
}
