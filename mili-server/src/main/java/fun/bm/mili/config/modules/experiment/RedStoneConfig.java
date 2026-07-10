package fun.bm.mili.config.modules.experiment;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "redstone")
public class RedStoneConfig implements ConfigModule {
    @ConfigInfo(name = "old-block-remove-behaviour", comments = "Use old block remove behaviour") public static boolean oldBlockRemoveBehaviour = false;
    @ConfigInfo(name = "instant-block-updater", comments = "Use instant block updater") public static boolean instantBlockUpdater = false;
    @ConfigInfo(name = "redstone-ignore-upwards-update", comments = "Ignore upwards redstone updates") public static boolean redstoneIgnoreUpwardsUpdate = false;
    @ConfigInfo(name = "cce", comments = "Shulker box CCE reintroduced") public static boolean cce = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
