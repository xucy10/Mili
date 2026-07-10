package fun.bm.mili.config.modules.fixes;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FIXES, name = "update-suppression-crash-fix")
public class UpdateSuppressionCrashFixConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Fix update suppression crash") public static boolean enabled = true;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
