package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "fakeplayer")
public class FakeplayerConfig implements ConfigModule {
    @ConfigInfo(name = "enable", comments = "Enable fakeplayer functionality") public static boolean enable = true;
    public static java.util.List<String> unableNames = java.util.Arrays.asList("player-name");
    @ConfigInfo(name = "limit", comments = "Maximum number of fakeplayers allowed") public static int limit = 10;
    @ConfigInfo(name = "prefix", comments = "Prefix for fakeplayer names") public static String prefix = "";
    @ConfigInfo(name = "suffix", comments = "Suffix for fakeplayer names") public static String suffix = "";
    @ConfigInfo(name = "regen-amount", comments = "Regeneration amount for fakeplayers") public static double regenAmount = 0.0;
    @ConfigInfo(name = "resident-fakeplayer", comments = "Allow fakeplayers to be resident") public static boolean canResident = false;
    @ConfigInfo(name = "open-fakeplayer-inventory", comments = "Allow opening fakeplayer inventory") public static boolean canOpenInventory = false;
    @ConfigInfo(name = "use-action", comments = "Allow fakeplayers to use actions") public static boolean canUseAction = true;
    @ConfigInfo(name = "modify-config", comments = "Allow modifying fakeplayer config") public static boolean canModifyConfig = false;
    @ConfigInfo(name = "manual-save-and-load", comments = "Allow manual save and load of fakeplayers") public static boolean canManualSaveAndLoad = false;
    @ConfigInfo(name = "cache-skin", comments = "Use skin cache for fakeplayers") public static boolean useSkinCache = false;
    @ConfigInfo(name = "always-send-data", comments = "Always send data for fakeplayers") public static boolean canSendDataAlways = true;
    @ConfigInfo(name = "skip-sleep-check", comments = "Skip sleep check for fakeplayers") public static boolean canSkipSleep = false;
    @ConfigInfo(name = "spawn-phantom", comments = "Allow phantoms to spawn for fakeplayers") public static boolean canSpawnPhantom = false;
    @ConfigInfo(name = "simulation-distance", comments = "Simulation distance for fakeplayers (-1 for default)") public static int simulationDistance = -1;
    @ConfigInfo(name = "enable-locator-bar", comments = "Enable locator bar for fakeplayers") public static boolean enableLocatorBar = false;
    public static org.leavesmc.leaves.bot.ServerBot.TickType tickType = org.leavesmc.leaves.bot.ServerBot.TickType.ENTITY_LIST;

    public static int getSimulationDistance(org.leavesmc.leaves.bot.ServerBot bot) {
        return simulationDistance == -1 ? bot.getBukkitEntity().getSimulationDistance() : simulationDistance;
    }

    private org.leavesmc.leaves.command.bot.BotCommand command;
    private boolean registered = false;

    @Override
    public void onLoaded(CommentedFileConfig configInstance) {
        if (enable) {
            command = new org.leavesmc.leaves.command.bot.BotCommand();
            command.register();
            registered = true;
        }
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        if (registered && command != null) {
            command.unregister();
            command = null;
        }
    }
}
