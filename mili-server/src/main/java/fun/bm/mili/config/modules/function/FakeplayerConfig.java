package fun.bm.mili.config.modules.function;

import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.command.bot.BotCommand;

import java.util.List;
import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "fakeplayer")
public class FakeplayerConfig implements IConfigModule {
    @ConfigInfo(name = "enable", comments = """
            启用假人功能""")
    public static boolean enable = true;

    @ConfigInfo(name = "unable-fakeplayer-names", comments = """
            不能用于假人的名称列表""")
    public static List<String> unableNames = List.of("player-name");

    @ConfigInfo(name = "limit", comments = """
            允许的最大假人数量""")
    public static int limit = 10;

    @ConfigInfo(name = "prefix", comments = """
            假人名称前缀""")
    public static String prefix = "";

    @ConfigInfo(name = "suffix", comments = """
            假人名称后缀""")
    public static String suffix = "";

    @ConfigInfo(name = "regen-amount", comments = """
            假人生命恢复量""")
    public static double regenAmount = 0.0;

    @ConfigInfo(name = "resident-fakeplayer", comments = """
            允许假人常驻""")
    public static boolean canResident = false;

    @ConfigInfo(name = "open-fakeplayer-inventory", comments = """
            允许打开假人物品栏""")
    public static boolean canOpenInventory = false;

    @ConfigInfo(name = "use-action", comments = """
            允许假人使用动作""")
    public static boolean canUseAction = true;

    @ConfigInfo(name = "modify-config", comments = """
            允许修改假人配置""")
    public static boolean canModifyConfig = false;

    @ConfigInfo(name = "manual-save-and-load", comments = """
            允许手动保存和加载假人""")
    public static boolean canManualSaveAndLoad = false;

    @ConfigInfo(name = "cache-skin", comments = """
            为假人使用皮肤缓存""")
    public static boolean useSkinCache = false;

    @ConfigInfo(name = "always-send-data", comments = """
            始终为假人发送数据""")
    public static boolean canSendDataAlways = true;

    @ConfigInfo(name = "skip-sleep-check", comments = """
            跳过假人睡眠检查""")
    public static boolean canSkipSleep = false;

    @ConfigInfo(name = "spawn-phantom", comments = """
            允许为假人生成幻翼""")
    public static boolean canSpawnPhantom = false;

    @ConfigInfo(name = "simulation-distance", comments = """
            假人模拟距离（-1 为默认）""")
    public static int simulationDistance = -1;

    @ConfigInfo(name = "enable-locator-bar", comments = """
            为假人启用定位栏""")
    public static boolean enableLocatorBar = false;
    public static ServerBot.TickType tickType = ServerBot.TickType.ENTITY_LIST;

    private BotCommand command = null;

    private boolean registered = false;

    public static int getSimulationDistance(ServerBot bot) {
        return simulationDistance == -1 ? bot.getBukkitEntity().getSimulationDistance() : simulationDistance;
    }

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        if (enable) {
            command = new BotCommand();
            command.register();
            registered = true;
        }
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        if (registered) {
            command.unregister();
            command = null;
        }
    }
}