package fun.bm.mili.config.modules.optimizations;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "technical-mc-optimizer")
public class TechnicalMCOptimizerConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用生电优化器
            针对刷线机、地毯机、铁轨系统、珍珠炮、天基屠龙炮等大型红石机器进行优化
            注意：默认禁用以保留原版生电特性，启用前请确认不影响你的机器""")
    public static boolean enabled = false;

    @ConfigInfo(name = "hopper-tick-rate", comments = """
            漏斗tick频率优化
            -1=正常(每tick), 0=暂停, N=每Ntick执行一次""")
    public static int hopperTickRate = -1;

    @ConfigInfo(name = "dispenser-tick-rate", comments = """
            发射器/投掷器tick频率优化
            -1=正常, N=每Ntick执行一次""")
    public static int dispenserTickRate = -1;

    @ConfigInfo(name = "rail-update-batch-size", comments = """
            铁轨批量更新大小
            增大可减少频繁的方块更新开销，对大量铁轨系统有帮助""")
    public static int railUpdateBatchSize = 4;

    @ConfigInfo(name = "minecart-tick-optimization", comments = "启用矿车tick优化 (跳过空载矿车的部分计算)")
    public static boolean minecartTickOptimization = true;

    @ConfigInfo(name = "string-duper-optimization", comments = """
            刷线机优化
            减少线实体的不必要更新和碰撞计算""")
    public static boolean stringDuperOptimization = true;

    @ConfigInfo(name = "pearl-cannon-optimization", comments = """
            珍珠炮优化
            减少末影珍珠实体的不必要物理计算""")
    public static boolean pearlCannonOptimization = true;

    @ConfigInfo(name = "piston-update-batch", comments = """
            活塞批量更新
            将相邻活塞的方块更新合并处理，减少大红石机器的卡顿""")
    public static boolean pistonUpdateBatch = true;

    @ConfigInfo(name = "piston-batch-radius", comments = "活塞批量更新合并半径")
    public static int pistonBatchRadius = 2;

    @ConfigInfo(name = "item-entity-merge-optimization", comments = """
            物品实体合并优化
            对刷线机/地毯机产生的大量物品实体进行合并优化""")
    public static boolean itemEntityMergeOptimization = true;

    @ConfigInfo(name = "max-item-merge-distance", comments = "物品实体最大合并距离")
    public static double maxItemMergeDistance = 0.5;

    @ConfigInfo(name = "chunk-tick-entity-limit", comments = """
            每区块每tick最大实体更新数
            防止刷线机/地毯机产生过多实体导致卡顿
            0=无限""")
    public static int chunkTickEntityLimit = 0;

    @ConfigInfo(name = "dragon-killer-optimization", comments = """
            天基屠龙炮优化
            减少龙实体的不必要碰撞检测和路径计算""")
    public static boolean dragonKillerOptimization = true;
}
