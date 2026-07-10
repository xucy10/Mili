package fun.bm.mili.config.modules.removed;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.REMOVED, name = "removed")
public class RemovedConfig implements ConfigModule {
    @ConfigInfo(name = "removed-features", comments = "List of removed feature IDs") public static java.util.List<String> removedFeatures = new java.util.ArrayList<>();
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
