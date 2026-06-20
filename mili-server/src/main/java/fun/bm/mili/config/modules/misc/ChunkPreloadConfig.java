package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

/**
 * 玩家行为预测区块预加载配置 / Predictive chunk pre-loading configuration.
 *
 * <p>通过分析玩家速度、方向和移动模式，在玩家到达之前预加载区块 /
 * Pre-loads chunks before the player reaches them by analyzing velocity,
 * direction, and movement mode.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "chunk-preload")
public class ChunkPreloadConfig implements IConfigModule {

    @ConfigInfo(name = "enabled", comments = """
            启用玩家行为预测区块预加载 / Enable predictive chunk pre-loading.
            分析玩家速度和方向，提前加载前方区块 / Analyzes player velocity and
            direction to pre-load chunks ahead of time.""")
    public static boolean enabled = true;

    @ConfigInfo(name = "base-preload-radius", comments = """
            基础预加载半径（区块数）/ Base pre-load radius in chunks.
            正常移动时在预测位置周围加载此范围的区块 / Chunks loaded around the
            predicted position during normal movement. 推荐 / Recommended: 2-4.""")
    public static int basePreloadRadius = 4;

    @ConfigInfo(name = "look-ahead-ticks", comments = """
            正常移动前瞻 tick 数 (1s = 20 ticks) / Look-ahead ticks for normal movement.
            预测玩家未来 N tick 的位置并预加载 / Predicts player position N ticks
            ahead and pre-loads that area. 推荐 / Recommended: 15-30.""")
    public static int lookAheadTicks = 25;

    @ConfigInfo(name = "max-speed-radius", comments = """
            高速移动时最大预加载半径（区块数）/ Maximum pre-load radius during high-speed
            movement in chunks. 鞘翅/激流三叉戟时的上限 / Upper limit for elytra/trident.
            推荐 / Recommended: 6-10.""")
    public static int maxSpeedRadius = 10;

    @ConfigInfo(name = "teleport-preload-radius", comments = """
            传送预加载半径（区块数）/ Teleport pre-load radius in chunks.
            RTP/末影珍珠/传送门等场景使用 / Used for RTP/ender pearl/portal scenarios.
            推荐 / Recommended: 5-9.""")
    public static int teleportPreloadRadius = 8;

    @ConfigInfo(name = "max-concurrent-loads-per-player", comments = """
            每玩家最大并发预加载区块数 / Maximum concurrent pre-load chunks per player.
            防止单玩家占用过多 IO / Prevents a single player from saturating chunk IO.
            推荐 / Recommended: 16-32.""")
    public static int maxConcurrentLoadsPerPlayer = 32;

    @ConfigInfo(name = "preload-ticket-duration-ticks", comments = """
            预加载 ticket 保留 tick 数 / How long pre-load tickets remain active.
            过期后区块可正常卸载 / Chunks unload normally after ticket expires.
            推荐 / Recommended: 40-100.""")
    public static int preloadTicketDurationTicks = 80;

    @ConfigInfo(name = "high-speed-threshold", comments = """
            速度阈值 (blocks/tick)，超过视为高速 / Speed threshold in blocks/tick.
            约 0.6 = 12 blocks/s (疾跑) / ~0.6 = 12 blocks/s (sprinting).
            推荐 / Recommended: 0.5-0.8.""")
    public static double highSpeedThreshold = 0.7;

    @ConfigInfo(name = "elytra-multiplier", comments = """
            鞘翅飞行时预测半径倍数 / Elytra flight prediction radius multiplier.
            鞘翅速度约 30-60 blocks/s / Elytra speed is ~30-60 blocks/s.
            推荐 / Recommended: 1.5-3.0.""")
    public static double elytraMultiplier = 2.5;

    @ConfigInfo(name = "trident-multiplier", comments = """
            激流三叉戟预测半径倍数 / Trident riptide prediction radius multiplier.
            激流速度约 40-80 blocks/s / Riptide speed is ~40-80 blocks/s.
            推荐 / Recommended: 2.0-3.5.""")
    public static double tridentMultiplier = 3.0;

    @ConfigInfo(name = "sample-interval-ticks", comments = """
            速度采样间隔 tick 数 / Velocity sampling interval in ticks.
            每 N tick 采样一次玩家位置计算速度 / Sample player position every N ticks
            to calculate velocity. 推荐 / Recommended: 3-5.""")
    public static int sampleIntervalTicks = 4;

    @ConfigInfo(name = "debug", comments = """
            调试日志 / Enable debug logging.
            输出预加载事件到控制台 / Logs pre-load events to console.""")
    public static boolean debug = false;
}
