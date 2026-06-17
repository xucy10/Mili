package fun.bm.mili.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.feature.MiliRegionBalanceCommand;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 区域智能均衡器配置 / Region intelligent balancer configuration.
 *
 * <p>监控 Folia 区域负载并提供拆分建议或自动疏导 /
 * Monitors Folia region load and provides split advice or automatic balancing.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "region-balancer")
public class RegionBalancerConfig implements IConfigModule {

    @ConfigInfo(name = "enabled", comments = """
            启用区域智能均衡器 / Enable region intelligent balancer.
            监控区域 MSPT 和实体数，提供拆分建议 / Monitors region MSPT and
            entity counts, provides split suggestions.""")
    public static boolean enabled = true;

    @ConfigInfo(name = "scan-interval-seconds", comments = """
            扫描间隔 (秒) / Scan interval (seconds).
            每隔此时间扫描一次所有区域负载 / Scans all region loads at this interval.
            推荐 / Recommended: 20-60.""")
    public static int scanIntervalSeconds = 30;

    @ConfigInfo(name = "hotspot-mspt-multiplier", comments = """
            热点判定倍数 / Hotspot detection multiplier.
            当区域 MSPT > 服务器平均 MSPT * 此值时标记为热点 / A region is flagged
            as hotspot when its MSPT > server average MSPT * this value.
            推荐 / Recommended: 1.5-3.0.""")
    public static double hotspotMsptMultiplier = 2.0;

    @ConfigInfo(name = "hotspot-consecutive", comments = """
            连续触发次数 / Consecutive triggers required.
            区域连续超过阈值此次数后才输出告警 / Alert only after exceeding threshold
            this many consecutive times. 防止瞬间抖动误报 / Prevents spike false positives.
            推荐 / Recommended: 2-5.""")
    public static int hotspotConsecutive = 3;

    @ConfigInfo(name = "auto-balance-enabled", comments = """
            启用自动负载疏导 / Enable automatic load balancing.
            开启后会自动将被动生物从热点区域移到低负载区域 / When enabled, automatically
            moves passive mobs from hotspot regions to less loaded ones.
            不移动玩家/命名实体/载具 / Does NOT move players/named entities/vehicles.
            推荐 / Recommended: false (仅建议模式下观察后再开启).""")
    public static boolean autoBalanceEnabled = true;

    @ConfigInfo(name = "auto-balance-entity-threshold", comments = """
            自动疏导实体阈值 / Auto-balance entity threshold.
            热点区域实体数超过此值时触发疏导 / Triggers balancing when hotspot region
            entity count exceeds this value. 推荐 / Recommended: 200-500.""")
    public static int autoBalanceEntityThreshold = 300;

    @ConfigInfo(name = "auto-balance-move-radius", comments = """
            疏导移动半径 (方块) / Balancing move radius (blocks).
            将实体移到此范围内的低负载区域 / Moves entities to low-load regions within
            this radius. 推荐 / Recommended: 100-300.""")
    public static int autoBalanceMoveRadius = 200;

    @ConfigInfo(name = "debug", comments = """
            调试日志 / Enable debug logging.
            输出扫描结果和疏导事件 / Logs scan results and balancing events.""")
    public static boolean debug = false;

    @DoNotLoad
    private static MiliRegionBalanceCommand command;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            if (command == null) {
                command = new MiliRegionBalanceCommand();
            }
            command.register();
        }
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        if (command != null) {
            command.unregister();
            command = null;
        }
    }
}
