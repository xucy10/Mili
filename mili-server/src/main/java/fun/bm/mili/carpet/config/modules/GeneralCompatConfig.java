package fun.bm.mili.carpet.config.modules;

import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

@ConfigClassInfo(
        category = EnumConfigCategory.ROOT,
        name = "general",
        directory = {"carpet"},
        comments = """
                Carpet/AMS/TIS/Org 兼容规则，由 Mili 现有功能支持。
                此处仅暴露已有可用服务端实现对应的规则。"""
)
public class GeneralCompatConfig implements IConfigModule {
    @ConfigInfo(name = "language", comments = """
            Carpet 语言值，转发到 mili.function.language.lang。""")
    public static String language = "en_us";

    @ConfigInfo(name = "amsUpdateSuppressionCrashFix", comments = """
            将 AMS 更新抑制崩溃保护映射到 Mili 现有的崩溃修复。""")
    public static boolean amsUpdateSuppressionCrashFix = false;

    @ConfigInfo(name = "yeetUpdateSuppressionCrash", comments = """
            将 TIS 更新抑制崩溃移除映射到同一 Mili 崩溃修复。""")
    public static boolean yeetUpdateSuppressionCrash = false;

    @ConfigInfo(name = "dustTrapdoorReintroduced", comments = """
            将活板门上红石粉的行为映射到 Mili 的红石忽略向上更新选项。""")
    public static boolean dustTrapdoorReintroduced = false;

    @ConfigInfo(name = "shulkerBoxCCEReintroduced", comments = """
            将潜影盒 CCE 更新抑制映射到 Mili 的 cce-update-suppression 选项。""")
    public static boolean shulkerBoxCCEReintroduced = false;

    @ConfigInfo(name = "instantBlockUpdaterReintroduced", comments = """
            启用 Mili 已携带的即时方块更新器补丁。""")
    public static boolean instantBlockUpdaterReintroduced = false;

    @ConfigInfo(name = "commandTick", comments = """
            启用 Mili 已补丁的 tick 命令支持。""")
    public static boolean commandTick = false;

    @ConfigInfo(name = "creativeNoClip", comments = """
            启用现有的创造模式飞行穿墙实现。""")
    public static boolean creativeNoClip = false;

    @ConfigInfo(name = "optimizedDragonRespawn", comments = """
            启用 Luminol 已有的优化末影龙重生实现。""")
    public static boolean optimizedDragonRespawn = false;

    @ConfigInfo(name = "antiSpamDisabled", comments = """
            禁用原版/Spigot 使用的服务端聊天和创造模式丢弃刷屏限制。""")
    public static boolean antiSpamDisabled = false;

    @ConfigInfo(name = "blockPlacementIgnoreEntity", comments = """
            允许创造模式玩家放置方块时不进行实体碰撞检查。""")
    public static boolean blockPlacementIgnoreEntity = false;

    @ConfigInfo(name = "creativeOpenContainerForcibly", comments = """
            允许创造模式玩家强制打开被阻挡的箱子、末影箱和潜影盒。""")
    public static boolean creativeOpenContainerForcibly = false;

    @ConfigInfo(name = "creativeOneHitKill", comments = """
            允许创造模式玩家一击杀死可攻击的非创造、非旁观实体。
            蹲下时该效果扩展为小范围群体攻击。""")
    public static boolean creativeOneHitKill = false;

    @ConfigInfo(name = "observerNoDetection", comments = """
            完全禁用侦测器的检测脉冲。""")
    public static boolean observerNoDetection = false;

    @ConfigInfo(name = "bambooModelNoOffset", comments = """
            移除竹子和竹子幼苗的随机水平模型偏移。""")
    public static boolean bambooModelNoOffset = false;

    @ConfigInfo(name = "creativeNoItemCooldown", comments = """
            跳过创造模式玩家的物品冷却应用。""")
    public static boolean creativeNoItemCooldown = false;

    @ConfigInfo(name = "ctrlQCraftingFix", comments = """
            当前菜单代码中已存在的上游结果栏 Ctrl+Q 合成修复的兼容标志。""")
    public static boolean ctrlQCraftingFix = false;

    @ConfigInfo(name = "carpetAlwaysSetDefault", comments = """
            Mili 配置加载器的兼容标志，该加载器已在预加载阶段将默认值写入兼容配置。""")
    public static boolean carpetAlwaysSetDefault = false;

    @ConfigInfo(name = "placementRotationFix", comments = """
            使用玩家身体主旋转而非插值头部偏航来进行放置方向检查。""")
    public static boolean placementRotationFix = false;

    @ConfigInfo(name = "tntDoNotUpdate", comments = """
            阻止 TNT 在首次放置时检查红石信号。""")
    public static boolean tntDoNotUpdate = false;

    @ConfigInfo(name = "totallyNoBlockUpdate", comments = """
            全局抑制方块变更的邻居和形状更新。""")
    public static boolean totallyNoBlockUpdate = false;

    @ConfigInfo(name = "tiscmNetworkProtocol", comments = """
            启用 tiscm:network/v1 上的原生 Carpet TIS Addition 网络通道。""")
    public static boolean tiscmNetworkProtocol = false;

    @ConfigInfo(name = "hopperNoItemCost", comments = """
            在漏斗上方放置羊毛块时，将已传输的物品堆恢复到漏斗中。""")
    public static boolean hopperNoItemCost = false;

    @ConfigInfo(name = "explosionNoBlockDamage", comments = """
            让爆炸只伤害实体而不破坏方块。""")
    public static boolean explosionNoBlockDamage = false;

    @ConfigInfo(name = "optimizedTNTHighPriority", comments = """
            当前运行时已携带的优化服务端爆炸路径的兼容标志。""")
    public static boolean optimizedTNTHighPriority = false;

    @ConfigInfo(name = "tntPrimerMomentumRemoved", comments = """
            移除新点燃 TNT 的随机水平发射动量。""")
    public static boolean tntPrimerMomentumRemoved = false;

    @ConfigInfo(name = "tntIgnoreRedstoneSignal", comments = """
            在决定 TNT 是否自动点燃时忽略红石信号。""")
    public static boolean tntIgnoreRedstoneSignal = false;

    @ConfigInfo(name = "tntDupingFix", comments = """
            切换原版 TNT 复制装置使用的活塞不同步路径。""")
    public static boolean tntDupingFix = false;

    @ConfigInfo(name = "interactionUpdates", comments = """
            控制玩家交互的方块变更是否发出正常方块更新。
            设为 false 可抑制方块使用和破坏过程中的邻居和形状更新。""")
    public static boolean interactionUpdates = true;

    @ConfigInfo(name = "xpNoCooldown", comments = """
            允许玩家在同一 tick 内吸收多个经验球而不受拾取延迟限制。""")
    public static boolean xpNoCooldown = false;

    @ConfigInfo(name = "powerfulExpMending", comments = """
            让拾取的经验修复玩家物品栏中所有受损的附魔修复物品，而非仅限装备栏。""")
    public static boolean powerfulExpMending = false;

    @ConfigInfo(name = "clientSettingsLostOnRespawnFix", comments = """
            在重生后重新应用玩家上次已知的客户端设置。""")
    public static boolean clientSettingsLostOnRespawnFix = false;

    @ConfigInfo(name = "sensibleEnderman", comments = """
            限制末影人拾取方块仅为南瓜和西瓜。""")
    public static boolean sensibleEnderman = false;

    @ConfigInfo(name = "entityInstantDeathRemoval", comments = """
            移除死亡生物实体被清除前的正常 20 刻延迟。""")
    public static boolean entityInstantDeathRemoval = false;

    @ConfigInfo(name = "farmlandTrampledDisabled", comments = """
            阻止耕地在实体落地时变为泥土。""")
    public static boolean farmlandTrampledDisabled = false;

    @ConfigInfo(name = "shulkerGolem", comments = """
            允许在潜影盒上方放置雕刻南瓜来召唤潜影贝。""")
    public static boolean shulkerGolem = false;

    @ConfigInfo(name = "preventEndSpikeRespawn", comments = """
            在末影龙重生时跳过黑曜石柱的重新生成。""")
    public static boolean preventEndSpikeRespawn = false;

    @ConfigInfo(name = "yeetOutOfOrderChatKick", comments = """
            忽略乱序安全聊天链检查，而非使聊天会话失效。""")
    public static boolean yeetOutOfOrderChatKick = false;

    @ConfigInfo(name = "betterCraftableBoneBlock", comments = """
            添加 AMS 的替代骨块合成配方：9 个骨头产出 3 个骨块。""")
    public static boolean betterCraftableBoneBlock = false;

    @ConfigInfo(name = "betterCraftableDispenser", comments = """
            添加 AMS 的替代发射器合成配方：使用投掷器合成。""")
    public static boolean betterCraftableDispenser = false;

    @ConfigInfo(name = "viewDistance", comments = """
            用 Carpet 兼容值覆盖专用服务器的启动视距。""")
    public static int viewDistance = 12;

    @ConfigInfo(name = "tickCommandPermission", comments = """
            覆盖 /tick 命令的权限等级。
            接受 0..4 范围的值，其中 2 对应旧版 Carpet 行为，3 保持原版。""")
    public static int tickCommandPermission = 3;

    @ConfigInfo(name = "tickFreezeCommandToggleable", comments = """
            使 /tick freeze 在服务器已冻结时再次执行可切换回运行状态。""")
    public static boolean tickFreezeCommandToggleable = false;

    @ConfigInfo(name = "syncServerMsptMetricsData", comments = """
            通过原生 TISCM 协议通道广播实时 MSPT 采样数据。""")
    public static boolean syncServerMsptMetricsData = false;

    @ConfigInfo(name = "simpleInGameCalculator", comments = """
            将以 = 开头的聊天消息作为简单计算器表达式求值并私信回复。""")
    public static boolean simpleInGameCalculator = false;

    @ConfigInfo(name = "microTiming", comments = """
            Folia/Moonrise 内置的区域性能分析器和计时工具的兼容标志。""")
    public static boolean microTiming = false;

    @ConfigInfo(name = "fastRedstoneDust", comments = """
            通过 Alternate Current 快速更新后端路由红石粉更新。""")
    public static boolean fastRedstoneDust = false;

    @ConfigInfo(name = "lagFreeSpawning", comments = """
            使用轻量级碰撞和预处理生物生成路径进行自然生成检查。""")
    public static boolean lagFreeSpawning = false;

    @ConfigInfo(name = "optimizedFastEntityMovement", comments = """
            Moonrise/Paper 始终启用的快速实体移动碰撞管线的兼容标志。""")
    public static boolean optimizedFastEntityMovement = false;

    @ConfigInfo(name = "optimizedHardHitBoxEntityCollision", comments = """
            Moonrise/Paper 始终启用的硬碰撞箱实体碰撞优化的兼容标志。""")
    public static boolean optimizedHardHitBoxEntityCollision = false;

    @ConfigInfo(name = "tntFuseDuration", comments = """
            覆盖默认的已点燃 TNT 引信时长（单位：刻）。
            接受 0..32767 范围的值。""")
    public static int tntFuseDuration = 80;

    @ConfigInfo(name = "defaultLoggers", comments = """
            Carpet 风格的玩家默认日志订阅。
            示例：["tps", "mob_caps", "counter white"]""")
    public static List<String> defaultLoggers = List.of();

    public static int normalizedTntFuseDuration() {
        return Math.clamp(tntFuseDuration, 0, Short.MAX_VALUE);
    }

    public static int normalizedTickCommandPermission() {
        return Math.clamp(tickCommandPermission, 0, 4);
    }

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
    }
}
