package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.feature.MiliRtpCommand;
import fun.bm.mili.feature.MiliRtpLocationPool;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * /rtp 命令配置 / RTP command configuration.
 *
 * <p>提供区块优先预加载的安全随机传送 / Provides safe random teleport with chunk-first preloading.
 * 默认开启 / Enabled by default.
 */
@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "rtp")
public class RtpConfig implements IConfigModule {

    @ConfigInfo(name = "enabled", comments = """
            启用 /rtp 命令 (随机传送) / Enable /rtp command (random teleport).
            传送前自动预加载目标区块，确保无缝着陆 / Automatically pre-loads
            destination chunks before teleport for seamless landing.""")
    public static boolean enabled = true;

    @ConfigInfo(name = "min-distance", comments = """
            最小传送距离 (方块) / Minimum teleport distance (blocks).""")
    public static int minDistance = 1000;

    @ConfigInfo(name = "max-distance", comments = """
            最大传送距离 (方块) / Maximum teleport distance (blocks).""")
    public static int maxDistance = 10000;

    @ConfigInfo(name = "cooldown-seconds", comments = """
            传送冷却时间 (秒) / Teleport cooldown (seconds).""")
    public static int cooldownSeconds = 30;

    @ConfigInfo(name = "invulnerable-seconds", comments = """
            传送后无敌时间 (秒) / Post-teleport invulnerability (seconds).
            防止传送后立即受到环境或怪物伤害 / Prevents immediate environmental
            or mob damage after landing.""")
    public static int invulnerableSeconds = 15;

    @ConfigInfo(name = "pool-size", comments = """
            每个世界的预计算位置池大小 / Pre-computed location pool size per world.
            玩家进服后后台自动搜索填充，冷却期间继续补充 / Auto-filled in background
            after player joins, replenished during cooldown. 推荐 / Recommended: 5-20.""")
    public static int poolSize = 18;

    @ConfigInfo(name = "preload-inner-radius", comments = """
            内圈预加载半径 (区块) / Inner preload radius (chunks).
            传送时以 HIGHEST 优先级加载 / Loaded at HIGHEST priority during teleport.
            推荐 / Recommended: 2-4.""")
    public static int preloadInnerRadius = 3;

    @ConfigInfo(name = "preload-outer-radius", comments = """
            外圈预加载半径 (区块) / Outer preload radius (chunks).
            传送后后台继续加载 / Loaded in background after teleport.
            推荐 / Recommended: 5-9.""")
    public static int preloadOuterRadius = 7;

    @ConfigInfo(name = "require-generated", comments = """
            仅传送到已生成的区块 / Only teleport to pre-generated chunks.
            启用后位置池只包含已生成的区块，避免实时生成地形 / Pool only contains
            generated chunks, avoiding real-time terrain generation.
            配合 Chunky 预生成效果最佳 / Works best with Chunky pre-generation.""")
    public static boolean requireGenerated = true;

    @ConfigInfo(name = "avoid-player-radius", comments = """
            避开玩家的最小距离 (方块) / Minimum distance from any player (blocks).
            位置池填充时会检查此范围内是否有玩家或玩家建筑 / Pool fill checks
            for players and player structures within this radius.
            推荐 / Recommended: 200-500.""")
    public static int avoidPlayerRadius = 400;

    @ConfigInfo(name = "generated-check-radius", comments = """
            已生成区块检查半径 (区块) / Generated chunk check radius (chunks).
            确保目标周围区块也已生成，避免传送后出现区块边界 / Ensures surrounding
            chunks are also generated to avoid chunk borders after teleport.
            推荐 / Recommended: 2-5.""")
    public static int generatedCheckRadius = 3;

    @DoNotLoad
    private static MiliRtpCommand command;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            if (command == null) {
                command = new MiliRtpCommand();
            }
            command.register();
            MiliRtpLocationPool.start();
        }
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        MiliRtpLocationPool.stop();
        if (command != null) {
            command.unregister();
            command = null;
        }
    }
}
