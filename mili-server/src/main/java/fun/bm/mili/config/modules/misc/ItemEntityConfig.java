package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "item-entity")
public class ItemEntityConfig implements IConfigModule {
    @ConfigInfo(name = "follow-tick-sequence-merge", comments = """
            由于 Paper 修改了合并半径，
            当合并半径较大且包含大量物品的堆叠卡在意外位置时，
            单个物品可能永远无法到达目的地。
            此配置选项用于修复此行为。""")
    public static boolean followTickSequenceMerge = false;
}