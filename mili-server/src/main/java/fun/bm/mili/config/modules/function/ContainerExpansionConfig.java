package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "container-expansion")
public class ContainerExpansionConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable container expansion") public static boolean enabled = false;
    @ConfigInfo(name = "barrel-rows", comments = "Barrel rows count (3 to 6)") public static int barrelRows = 3;
    @ConfigInfo(name = "enderchest-rows", comments = "Enderchest rows count (1 to 6)") public static int enderchestRows = 3;
    @ConfigInfo(name = "nbt-shulker-stackable", comments = "Enable NBT shulker stacking") public static boolean nbtShulkerStackable = false;
    @ConfigInfo(name = "shulker-count", comments = "Max shulker stack count") public static int shulkerCount = 1;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
