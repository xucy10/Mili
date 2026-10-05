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

            延迟敏感场景请设为 <=0（Folia 默认行为）：调查查阅 TickRegionScheduler 可知，
             ConcreteRegionTickHandle#tickRegion 一批只真正 tick 一次，tickCount 只用于
             advanceBy 推进调度进度。因此限定上限后，落后会被摊成若干次迭代，
            而每次迭代结束时 deadline 仍已过期，区域会转入紧凑的连续 tick，
            期间没有闲置窗口去 drain 入站包 —— 玩家的包要等到追赶结束才被处理。
            设为 <=0 时债务一次勾销，下一次 tick 回到正常的 50ms 之后，
            留出 drain 包的空隙，入站延迟明显更低。

            代价是区域不补齐落后的游戏时间（TPS 读数会如实显现出这段落后）。
            默认值已改为 0（即不追赶）：把"TPS 读数好看"换走实实在在的入站延迟并不划算。""")
    // Mili start - 默认不再追赶：把响应性放在"账面 tick 数"之前，见上方 comments
    public static int maxTickCatchup = 0;
    // Mili end
}
