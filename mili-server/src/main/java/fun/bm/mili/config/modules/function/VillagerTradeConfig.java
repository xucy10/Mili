package fun.bm.mili.config.modules.function;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "villager-trade")
public class VillagerTradeConfig implements IConfigModule {
    @TransformedConfig(name = "shared_villager_discounts", directory = {"function", "villager"}, transformComments = false)
    @ConfigInfo(name = "shared-villager-discounts", comments =
            """
                    允许村民共享重大正面流言折扣。
                    启用后，治愈僵尸村民将为所有玩家提供折扣。""")
    public static boolean sharedVillagerDiscounts = false;

    @TransformedConfig(name = "force_void_trade", directory = {"function", "villager"}, transformComments = false)
    @ConfigInfo(name = "force-void-trade", comments =
            """
                    强制虚空交易：允许通过末地传送门与村民交易。
                    当玩家在交易时通过末地传送门传送，
                    村民的交易不会被消耗。""")
    public static boolean forceVoidTrade = false;

    @TransformedConfig(name = "villager_infinite_discounts", directory = {"function", "villager"}, transformComments = false)
    @ConfigInfo(name = "villager-infinite-discounts", comments =
            """
                    使村民流言的 max 和 decayPerTransfer 字段变为非 final，
                    允许在运行时修改以实现无限折扣。""")
    public static boolean villagerInfiniteDiscounts = false;

    @TransformedConfig(name = "spider_jockeys_drop_gapples", directory = {"function", "villager"}, transformComments = false)
    @ConfigInfo(name = "spider-jockeys-drop-gapples", comments =
            """
                    蜘蛛骑士掉落附魔金苹果的概率。
                    设为 0.0 以禁用。示例：0.01 = 1% 概率。""")
    public static double spiderJockeysDropGapples = 0.0D;
}
