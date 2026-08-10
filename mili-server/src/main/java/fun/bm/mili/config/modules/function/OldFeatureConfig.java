package fun.bm.mili.config.modules.function;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "old-feature")
public class OldFeatureConfig implements IConfigModule {
    @TransformedConfig(name = "spawn_invulnerable_time", directory = {"misc", "old-feature"})
    @ConfigInfo(name = "spawn_invulnerable_time")
    public static boolean spawnInvulnerableTime = false;

    @TransformedConfig(name = "old_zombie_reinforcement", directory = {"misc", "old-feature"})
    @ConfigInfo(name = "old_zombie_reinforcement")
    public static boolean oldZombieReinforcement = false;

    @TransformedConfig(name = "old_explosion_damage_calculator", directory = {"misc", "old-feature"})
    @ConfigInfo(name = "old_explosion_damage_calculator")
    public static boolean oldExplosionDamageCalculator = false;

    @TransformedConfig(name = "old_raid_behavior", directory = {"misc", "old-feature"})
    @TransformedConfig(name = "give_bad_omen_when_kill_raid_captain", directory = {"misc", "revert_raid_changes"}, transformComments = false)
    @ConfigInfo(name = "old_raid_behavior")
    public static boolean oldRaidBehavior = false;

    @TransformedConfig(name = "villager-infinite-trade", directory = {"function", "villager"}, transformComments = false)
    @TransformedConfig(name = "villager-infinite-trade", directory = {"misc", "villager"}, transformComments = false)
    @TransformedConfig(name = "villager-infinite-trade", directory = {"misc", "villager-config"}, transformComments = false)
    @ConfigInfo(name = "villager-void-trade", comments =
            """
                    允许村民虚空交易。""")
    public static boolean villagerVoidTrade = false;

    @ConfigInfo(name = "sound-update-suppression", comments =
            """
                    允许使用幽匿感测体进行声音更新抑制。""")
    public static boolean soundUpdateSuppression = false;

    @ConfigInfo(name = "allow-inf-nan-motion-values", comments =
            """
                    允许实体的速度包含 Inf 或 NaN 值。""")
    public static boolean allowInfNanMotionValues = false;

    @ConfigInfo(name = "copper_bulb_1gt", comments =
            """
                    铜泡菜灯延迟 1 gt（回到旧版本行为）。""")
    public static boolean copperBulb1gt = false;

    @ConfigInfo(name = "crafter_1gt", comments =
            """
                    合成器延迟 1 gt（回到旧版本行为）。""")
    public static boolean crafter1gt = false;

    @ConfigInfo(name = "flatten_triangular_distribution", comments =
            """
                    扁平化三角分布（回到旧版本行为）。""")
    public static boolean flattenTriangularDistribution = false;

    @ConfigInfo(name = "despawn-enderman-with-block", comments = """
            持有方块的末影人是否可以自然消失。""")
    public static boolean despawnEndermanWithBlock = false;

    @ConfigInfo(name = "shave-snow-layers", comments = """
            允许用铲子逐层刮雪层。""")
    public static boolean shaveSnowLayers = false;

    @ConfigInfo(name = "check-frozen-ticks-before-landing-block", comments = """
            在着地前检查冰冻 ticks，优化性能。""")
    public static boolean checkFrozenTicksBeforeLandingBlock = false;

    @ConfigInfo(name = "zero-tick-plants", comments = """
            启用零刻植物生长机制。""")
    public static boolean zeroTickPlants = false;

    @ConfigInfo(name = "old-hopper-suck-in-behavior", comments = """
            旧漏斗吸入行为，不检查上方方块碰撞形状。""")
    public static boolean oldHopperSuckInBehavior = false;

    @ConfigInfo(name = "disable-living-entity-ai-step-alive-check", comments = """
            禁用生物 aiStep 存活检查。""")
    public static boolean disableLivingEntityAiStepAliveCheck = false;

    @ConfigInfo(name = "fix-falling-block-entity-duplicate", comments = """
            修复下落方块实体在末地传送门时复制问题。""")
    public static boolean fixFallingBlockEntityDuplicate = false;

    @ConfigInfo(name = "old-zombie-piglin-drop", comments = """
            旧僵尸猪灵掉落行为，愤怒状态下保持击杀者记忆。""")
    public static boolean oldZombiePiglinDrop = false;

    @ConfigInfo(name = "old-throwable-projectile-tick-order", comments = """
            旧投掷物 tick 顺序。""")
    public static boolean oldThrowableProjectileTickOrder = false;

    @ConfigInfo(name = "keep-leash-connect-when-use-firework", comments = """
            使用烟花时保持拴绳连接。""")
    public static boolean keepLeashConnectWhenUseFirework = false;

    @ConfigInfo(name = "tnt-wet-explosion-no-item-damage", comments = """
            TNT 在水中爆炸时不伤害物品实体。""")
    public static boolean tntWetExplosionNoItemDamage = false;

    @ConfigInfo(name = "old-projectile-explosion-behavior", comments = """
            旧投掷物爆炸行为，使用 setDeltaMovement 替代 push。""")
    public static boolean oldProjectileExplosionBehavior = false;

    @ConfigInfo(name = "ender-dragon-part-can-use-end-portal", comments = """
            末影龙身体部分可以使用末地传送门。""")
    public static boolean enderDragonPartCanUseEndPortal = false;

    @ConfigInfo(name = "old-minecart-motion-behavior", comments = """
            旧矿车运动行为。""")
    public static boolean oldMinecartMotionBehavior = false;

    @ConfigInfo(name = "spear-instant-lunge", comments = """
            允许切换到长矛时立即突进，不重置攻击冷却。""")
    public static boolean spearInstantLunge = false;

    @ConfigInfo(name = "snowball-and-egg-can-knockback-player", comments = """
            允许雪球和鸡蛋击退玩家。""")
    public static boolean snowballAndEggCanKnockback = false;

    @ConfigInfo(name = "shears-in-dispenser-can-zero-amount", comments = """
            允许发射器中的剪刀不消耗耐久。""")
    public static boolean shearsInDispenserCanZeroAmount = false;

    @ConfigInfo(name = "movable-budding-amethyst", comments = """
            允许活塞推动紫水晶芽母岩。""")
    public static boolean movableBuddingAmethyst = false;

    @ConfigInfo(name = "spectator-dont-get-advancement", comments = """
            旁观者模式不获得成就。""")
    public static boolean spectatorDontGetAdvancement = false;

    @ConfigInfo(name = "stick-change-armorstand-arm-status", comments = """
            允许用棍子潜行右键切换盔甲架手臂显示状态。""")
    public static boolean stickChangeArmorStandArmStatus = false;

    @ConfigInfo(name = "string-tripwire-hook-duplicate", comments = """
            恢复 MC-59471 的绊线钩复制特性。""")
    public static boolean stringTripwireHookDuplicate = false;

    @ConfigInfo(name = "renewable-elytra", comments = """
            可再生鞘翅概率（-1 禁用，0~1 为概率）。""")
    public static double renewableElytra = -1.0;

    @ConfigInfo(name = "return-nether-portal-fix", comments = """
            修复返回下界传送门的位置问题。""")
    public static boolean netherPortalFix = false;

    @ConfigInfo(name = "no-feather-falling-trample", comments = """
            拥有摔落保护时不会践踏农田。""")
    public static boolean noFeatherFallingTrample = false;

    @ConfigInfo(name = "lava-riptide", comments = """
            允许在岩浆中使用激流。""")
    public static boolean lavaRiptide = false;

    @ConfigInfo(name = "container-passthrough", comments = """
            允许通过物品展示框和牌子与后方容器交互。""")
    public static boolean containerPassthrough = false;

    @ConfigInfo(name = "avoid-anvil-too-expensive", comments = """
            避免铁砧过于昂贵。""")
    public static boolean avoidAnvilTooExpensive = false;

    @ConfigInfo(name = "bow-infinity-fix", comments = """
            修复无限附魔弓不消耗箭矢的问题。""")
    public static boolean bowInfinityFix = false;

    @ConfigInfo(name = "rng-fishing", comments = """
            使用可预测随机源的钓鱼。""")
    public static boolean rngFishing = false;

    @ConfigInfo(name = "renewable-deepslate", comments = """
            可再生深板岩。""")
    public static boolean renewableDeepslate = false;

    @ConfigInfo(name = "renewable-sponges", comments = """
            可再生海绵（守卫者被雷击变为远古守卫者）。""")
    public static boolean renewableSponges = false;

    @ConfigInfo(name = "renewable-coral", comments = """
            可再生珊瑚模式：FALSE 禁用, TRUE 基础, EXPANDED 扩展。""")
    public static String renewableCoral = "FALSE";

    @ConfigInfo(name = "disable-item-damage-check", comments = """
            禁用物品损坏值检查。""")
    public static boolean disableItemDamageCheck = false;

    @ConfigInfo(name = "disable-distance-check-for-use-item", comments = """
            禁用 UseItemOnPacket 的距离检查。""")
    public static boolean disableDistanceCheckForUseItem = false;

    @ConfigInfo(name = "no-block-update-command", comments = """
            启用无方块更新命令。""")
    public static boolean noBlockUpdateCommand = false;

    @ConfigInfo(name = "vanilla-hopper", comments = """
            恢复原版漏斗行为。""")
    public static boolean vanillaHopper = false;

    @ConfigInfo(name = "vanilla-display-name", comments = """
            使用原版玩家显示名。""")
    public static boolean vanillaDisplayName = false;

    @ConfigInfo(name = "vanilla-portal-handle", comments = """
            使用原版传送门处理逻辑。""")
    public static boolean vanillaPortalHandle = false;

    @ConfigInfo(name = "vanilla-creative-pickup-behavior", comments = """
            使用原版创造模式拾取行为。""")
    public static boolean vanillaCreativePickupBehavior = false;

    @ConfigInfo(name = "stacked-container-destroyed-drop", comments = """
            修复堆叠容器销毁时掉落物数量。""")
    public static boolean stackedContainerDestroyedDrop = false;

    @ConfigInfo(name = "fix-entity-portal-exit-event", comments = """
            修复 EntityPortalExitEvent 逻辑。""")
    public static boolean fixEntityPortalExitEvent = false;

    @ConfigInfo(name = "fix-craft-portal-event", comments = """
            修复 Craft 传送门事件逻辑。""")
    public static boolean fixCraftPortalEvent = false;

    @ConfigInfo(name = "skip-negligible-planar-movement", comments = """
            跳过可忽略的平面运动乘法。""")
    public static boolean skipNegligiblePlanarMovement = false;

    @ConfigInfo(name = "fix-paper-prevent-moving-into-unloaded-chunks", comments = """
            修复 Paper 的 preventMovingIntoUnloadedChunks 配置。""")
    public static boolean fixPaperPreventMovingIntoUnloadedChunks = false;

    @ConfigInfo(name = "force-minecraft-command", comments = """
            强制使用 minecraft: 前缀命令。""")
    public static boolean forceMinecraftCommand = false;

    @ConfigInfo(name = "exp-orb-absorb-mode", comments = """
            快速经验球吸收模式：VANILLA 原版, FAST 快速, FAST_CREATIVE 仅创造快速。""")
    public static String expOrbAbsorbMode = "VANILLA";

    @ConfigInfo(name = "async-keepalive", comments = """
            异步 keepalive。""")
    public static boolean asyncKeepalive = false;

    @ConfigInfo(name = "async-keepalive-timeout-seconds", comments = """
            异步 keepalive 超时秒数。""")
    public static int asyncKeepaliveTimeoutSeconds = 20;
}