package fun.bm.mili.config.modules.optimizations;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "villager_optimizer", comments = """
        Advanced villager AI optimization combining LaggRemover and VillagerLobotomizer features.
        Disables AI for trapped villagers while preserving trading functionality.
        Includes smart restocking, activity detection, and TPS-aware scaling.
        """)
public class VillagerOptimizerConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable advanced villager AI optimization")
    public static boolean enabled = false;

    @ConfigInfo(name = "check_interval", comments = "Interval in ticks to check villager activity state")
    public static int checkInterval = 150;

    @ConfigInfo(name = "inactive_check_interval", comments = "Interval in ticks to check inactive villagers")
    public static int inactiveCheckInterval = 150;

    @ConfigInfo(name = "only_professions", comments = "Only lobotomize villagers with professions")
    public static boolean onlyProfessions = false;

    @ConfigInfo(name = "only_with_experience", comments = "Only lobotomize villagers that have been traded with")
    public static boolean onlyWithExperience = false;

    @ConfigInfo(name = "lobotomize_passengers", comments = "Always lobotomize villagers in vehicles (boats/minecarts)")
    public static boolean lobotomizePassengers = false;

    @ConfigInfo(name = "check_roof", comments = "Check if there is a roof above villager before lobotomizing")
    public static boolean checkRoof = true;

    @ConfigInfo(name = "ignore_stuck_in_doors", comments = "Ignore villagers stuck in doors")
    public static boolean ignoreStuckInDoors = false;

    @ConfigInfo(name = "ignore_non_solid_blocks", comments = "Ignore non-solid blocks when checking movement")
    public static boolean ignoreNonSolidBlocks = false;

    @ConfigInfo(name = "silent_lobotomized", comments = "Make lobotomized villagers silent")
    public static boolean silentLobotomized = false;

    @ConfigInfo(name = "persist_state", comments = "Persist lobotomized state across chunk unloads")
    public static boolean persistState = true;

    @ConfigInfo(name = "restock_interval", comments = "Interval in milliseconds between trade restocks")
    public static long restockInterval = 540000;

    @ConfigInfo(name = "restock_random_range", comments = "Random range in ms before restock interval to start checks")
    public static long restockRandomRange = 0;

    @ConfigInfo(name = "prevent_trading_unlobotomized", comments = "Prevent trading with unlobotomized villagers")
    public static boolean preventTradingUnlobotomized = false;

    @ConfigInfo(name = "tps_scale_enabled", comments = "Scale check intervals based on TPS")
    public static boolean tpsScaleEnabled = true;

    @ConfigInfo(name = "tps_scale_threshold", comments = "TPS threshold below which to scale intervals")
    public static double tpsScaleThreshold = 18.0;

    @ConfigInfo(name = "tps_scale_factor", comments = "Factor to multiply intervals when TPS is low (higher = less frequent checks)")
    public static double tpsScaleFactor = 2.0;

    @HotReloadUnsupported
    @ConfigInfo(name = "always_active_names", comments = "List of names that will always keep villagers active")
    public static String[] alwaysActiveNames = new String[]{"alwaysbrain"};

    @Override
    public void onLoaded(CommentedFileConfig configInstance) {}

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {}
}