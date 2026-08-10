package fun.bm.mili.carpet.config.modules;

import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(
        category = EnumConfigCategory.ROOT,
        name = "fakeplayer",
        directory = {"carpet"},
        comments = """
                Carpet 假人兼容，映射到 Mili 假人系统。
                commandPlayer 目前由 Mili 的 /bot 命令接口支持。"""
)
public class FakePlayerCompatConfig implements IConfigModule {
    @ConfigInfo(name = "commandBot", comments = """
            启用 Mili 的 /bot 命令。""")
    public static boolean commandBot = false;

    @ConfigInfo(name = "commandPlayer", comments = """
            将 Carpet 的 commandPlayer 规则映射到 /bot 所使用的假人命令接口。""")
    public static boolean commandPlayer = false;

    @ConfigInfo(name = "fakePlayerResident", comments = """
            使假人在区块卸载和服务器重启后保持驻留。""")
    public static boolean fakePlayerResident = false;

    @ConfigInfo(name = "openFakePlayerInventory", comments = """
            允许打开假人的物品栏。""")
    public static boolean openFakePlayerInventory = false;

    @ConfigInfo(name = "fakePlayerTicksLikeRealPlayer", comments = """
            在网络阶段 tick 假人，以更好地匹配真实玩家的时序。""")
    public static boolean fakePlayerTicksLikeRealPlayer = false;

    @ConfigInfo(name = "fakePlayerDefaultSurvivalMode", comments = """
            强制新创建的假人以生存模式而非服务器默认游戏模式启动。""")
    public static boolean fakePlayerDefaultSurvivalMode = false;

    @ConfigInfo(name = "fakePlayerInteractLikeClient", comments = """
            使假人的实体交互更贴近客户端侧的回退行为。""")
    public static boolean fakePlayerInteractLikeClient = false;

    @ConfigInfo(name = "fakePlayerAutoReplaceTool", comments = """
            切换假人的自动工具替换功能。""")
    public static boolean fakePlayerAutoReplaceTool = false;

    @ConfigInfo(name = "fakePlayerAutoReplenishment", comments = """
            切换假人的自动补货功能。""")
    public static boolean fakePlayerAutoReplenishment = false;

    @ConfigInfo(name = "fakePlayerAutoReplenishmentFormShulkerBox", comments = """
            让假人补货时从物品栏中的潜影盒中取出匹配的物品。""")
    public static boolean fakePlayerAutoReplenishmentFormShulkerBox = false;

    @ConfigInfo(name = "fakePlayerAutoFish", comments = """
            让手持钓鱼竿的假人自动抛竿和收竿。""")
    public static boolean fakePlayerAutoFish = false;

    @ConfigInfo(name = "fakePlayerReloadAction", comments = """
            在保存和重载之间持久化假人的排队操作。""")
    public static boolean fakePlayerReloadAction = false;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
    }
}
