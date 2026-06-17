package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

/**
 * mili - Item entity performance/生电 tunables.
 *
 * <p>These settings expose levers that technical players care about for
 * item elevators, sorting systems and large-scale farms. Vanilla has a
 * conservative default that is correct but slow; technical players often
 * want a configurable merge radius and a faster pick-up so their farms
 * stay responsive at scale.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "item-entity-perf")
public class ItemEntityPerfConfig implements IConfigModule {
    @ConfigInfo(name = "optimistic-merge-range-bonus", comments = """
            Extra blocks added to the vanilla item-entity merge radius.
            0 = vanilla behaviour. Higher values let items find stacks to merge
            into across hopper / water-stream gaps, which is useful for
            compact item elevators and water-stream sorters. Recommended
            range: 0-2. Too high values will cause items in unrelated lanes
            to merge incorrectly.""")
    public static double mergeRangeBonus = 0.0;

    @ConfigInfo(name = "fast-pickup-cooldown-ticks", comments = """
            Override the server-side cooldown (in ticks) between item entity
            spawns at the same position. Vanilla is 10 ticks which is too
            slow for many technical builds. Set to 0 to use vanilla value.
            Recommended: 2-4 for item elevators, 0 for vanilla survival.""")
    public static int fastPickupCooldownTicks = 0;

    @ConfigInfo(name = "unrestricted-pickup", comments = """
            If true, the server ignores the per-player item pickup cooldown
            and allows instant pickup of any item the player is standing on.
            This is a major quality-of-life win for technical servers but
            may feel cheaty on survival, so it is disabled by default.""")
    public static boolean unrestrictedPickup = false;

    @ConfigInfo(name = "max-merge-attempts-per-tick", comments = """
            Maximum number of item merge attempts per item entity per tick.
            Vanilla has no limit which can cause severe lag with many items
            in a small area (e.g. super smelters, bulk item elevators).
            Set to 0 for unlimited (vanilla). Recommended: 8-16 for busy
            technical servers.""")
    public static int maxMergeAttemptsPerTick = 0;

    @ConfigInfo(name = "tps-aware-merge-throttle", comments = """
            If true, item merge operations are throttled when the server TPS
            drops below 'tps-aware-merge-threshold'. This prevents item
            processing from consuming excessive tick time during lag spikes,
            which is critical for stability on busy technical servers.""")
    public static boolean tpsAwareMergeThrottle = false;

    @ConfigInfo(name = "tps-aware-merge-threshold", comments = """
            TPS threshold below which item merge operations start being
            throttled. Only relevant when 'tps-aware-merge-throttle' is true.
            Default 16.0 means throttling begins when TPS drops below 80%.""")
    public static double tpsAwareMergeThreshold = 16.0;

    @ConfigInfo(name = "hopper-transfer-boost", comments = """
            Multiplier applied to the hopper transfer cooldown. Values < 1.0
            make hoppers faster (0.5 = twice as fast), values > 1.0 make
            them slower. This is useful for technical servers where hopper
            throughput is critical (e.g. super smelters, storage systems).
            WARNING: Values below 0.5 may cause unexpected behavior.
            1.0 = vanilla speed.""")
    public static double hopperTransferBoost = 1.0;

    // ---- Hopper idle optimization ----

    @ConfigInfo(name = "hopper-idle-skip-ticks", comments = """
            When a hopper is completely idle (all slots empty), it will only
            check for items every N ticks instead of every tick. This reduces
            CPU usage for large sorting systems with many idle hoppers.
            Default 8 means idle hoppers check ~2.5 times per second.
            Set to 1 to disable (check every tick, vanilla behavior).""")
    public static int hopperIdleSkipTicks = 8;

    @ConfigInfo(name = "hopper-idle-threshold", comments = """
            Number of consecutive idle ticks before a hopper enters low-frequency
            check mode. A hopper is considered idle when all its inventory slots
            are empty. Default 4 means a hopper must be empty for 4 ticks before
            the skip-ticks optimization kicks in.""")
    public static int hopperIdleThreshold = 4;

    // ---- Experience orb merging ----

    @ConfigInfo(name = "xp-orb-merge-radius", comments = """
            Search radius (in blocks) for merging nearby experience orbs.
            Larger values merge orbs more aggressively, reducing entity count
            during mob farming. Default 2.0 balances performance with the
            visual experience of seeing individual orbs. Set to 0 to disable.""")
    public static double xpOrbMergeRadius = 2.0;

    @ConfigInfo(name = "xp-orb-merge-interval", comments = """
            How often (in ticks) each experience orb attempts to merge with
            nearby orbs. Default 20 means once per second. Lower values merge
            faster but cost more CPU. Recommended: 10-40.""")
    public static int xpOrbMergeInterval = 20;

    @ConfigInfo(name = "xp-orb-max-value", comments = """
            Maximum experience value a single merged orb can hold. Prevents
            creating excessively large orbs that could cause issues with
            mending repairs or XP distribution. Default 1000 is roughly
            equivalent to 20 levels. Set to 0 for unlimited.""")
    public static int xpOrbMaxValue = 1000;
}
