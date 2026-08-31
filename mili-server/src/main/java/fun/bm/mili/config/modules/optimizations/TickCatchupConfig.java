package fun.bm.mili.config.modules.optimizations;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "tick-catchup")
public class TickCatchupConfig implements IConfigModule {
    @ConfigInfo(name = "max-tick-catchup", comments = """
            单次调度迭代中区域最多允许追赶的 tick 数。
            当区域因卡顿落后时，调度器会不停歇地连续补 tick 直到追上；
            过大的落后量会造成"瞬间快进"（游戏时间/天气/计划刻突发推进）。
            该上限把追赶摊平到多次迭代中，避免突发快进。
            设为 <=0 表示不限制（Folia 默认行为）。""")
    public static int maxTickCatchup = 20;
}
