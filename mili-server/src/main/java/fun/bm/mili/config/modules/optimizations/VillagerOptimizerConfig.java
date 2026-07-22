package fun.bm.mili.config.modules.optimizations;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.enums.EnumConfigCategory;

import java.util.ArrayList;
import java.util.List;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "villager_optimizer", comments = """
        结合 LaggRemover 和 VillagerLobotomizer 功能的高级村民 AI 优化。
        在保留交易功能的同时禁用被困村民的 AI。
        包括智能补货、活动检测和 TPS 感知缩放。
        """)
public class VillagerOptimizerConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "启用高级村民 AI 优化")
    public static boolean enabled = true;

    @ConfigInfo(name = "check_interval", comments = "检查村民活动状态的间隔（游戏刻）")
    public static int checkInterval = 150;

    @ConfigInfo(name = "inactive_check_interval", comments = "检查非活跃村民的间隔（游戏刻）")
    public static int inactiveCheckInterval = 150;

    @ConfigInfo(name = "only_professions", comments = "仅对拥有职业的村民禁用 AI")
    public static boolean onlyProfessions = false;

    @ConfigInfo(name = "only_with_experience", comments = "仅对有过交易的村民禁用 AI")
    public static boolean onlyWithExperience = false;

    @ConfigInfo(name = "lobotomize_passengers", comments = "始终对交通工具（船/矿车）中的村民禁用 AI")
    public static boolean lobotomizePassengers = false;

    @ConfigInfo(name = "check_roof", comments = "禁用 AI 前检查村民上方是否有遮挡")
    public static boolean checkRoof = true;

    @ConfigInfo(name = "ignore_stuck_in_doors", comments = "忽略卡在门中的村民")
    public static boolean ignoreStuckInDoors = false;

    @ConfigInfo(name = "ignore_non_solid_blocks", comments = "检查移动时忽略非固体方块")
    public static boolean ignoreNonSolidBlocks = false;

    @ConfigInfo(name = "silent_lobotomized", comments = "使被禁用 AI 的村民静音")
    public static boolean silentLobotomized = false;

    @ConfigInfo(name = "persist_state", comments = "跨区块卸载时保持禁用 AI 的状态")
    public static boolean persistState = true;

    @ConfigInfo(name = "restock_interval", comments = "交易补货间隔（毫秒）")
    public static long restockInterval = 540000;

    @ConfigInfo(name = "restock_random_range", comments = "补货间隔前的随机检查范围（毫秒）")
    public static long restockRandomRange = 0;

    @ConfigInfo(name = "prevent_trading_unlobotomized", comments = "阻止与未禁用 AI 的村民交易")
    public static boolean preventTradingUnlobotomized = false;

    @ConfigInfo(name = "tps_scale_enabled", comments = "根据 TPS 缩放检查间隔")
    public static boolean tpsScaleEnabled = true;

    @ConfigInfo(name = "tps_scale_threshold", comments = "开始缩放间隔的 TPS 阈值")
    public static double tpsScaleThreshold = 18.0;

    @ConfigInfo(name = "tps_scale_factor", comments = "TPS 较低时乘以间隔的倍数（越高 = 检查越不频繁）")
    public static double tpsScaleFactor = 2.0;

    @HotReloadUnsupported
    @ConfigInfo(name = "always_active_names", comments = "始终保持村民活跃的名称列表")
    public static List<String> alwaysActiveNames = new ArrayList<>(List.of("alwaysbrain"));

    @Override
    public void onLoaded(CommentedFileConfig configInstance) {}

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {}
}