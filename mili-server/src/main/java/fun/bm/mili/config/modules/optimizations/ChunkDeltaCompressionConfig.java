package fun.bm.mili.config.modules.optimizations;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.utils.ChunkDeltaCompressor;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "chunk-delta-compression")
public class ChunkDeltaCompressionConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用区块数据增量压缩""")
    public static boolean enabled = false;

    @ConfigInfo(name = "max-delta-size", comments = """
            最大增量大小（字节）""")
    public static int maxDeltaSize = 65536;

    @ConfigInfo(name = "snapshot-interval", comments = """
            快照间隔（tick）""")
    public static int snapshotInterval = 20;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        ChunkDeltaCompressor.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        ChunkDeltaCompressor.setEnabled(false);
    }
}
