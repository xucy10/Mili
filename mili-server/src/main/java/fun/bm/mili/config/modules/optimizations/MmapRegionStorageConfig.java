package fun.bm.mili.config.modules.optimizations;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.utils.MmapRegionStorage;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "mmap-region-storage")
public class MmapRegionStorageConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用内存映射区域存储""")
    public static boolean enabled = false;

    @ConfigInfo(name = "max-mapped-size-mb", comments = """
            最大映射大小（MB）""")
    public static int maxMappedSizeMb = 512;

    @ConfigInfo(name = "prefetch-chunks", comments = """
            预取相邻区块""")
    public static boolean prefetchChunks = true;

    @ConfigInfo(name = "prefetch-radius", comments = """
            预取半径（区块）""")
    public static int prefetchRadius = 2;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        MmapRegionStorage.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        MmapRegionStorage.setEnabled(false);
    }
}
