package fun.bm.mili.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "disable-check")
public class DisableCheckConfig implements ConfigModule {
    @ConfigInfo(name = "disable-op-fly-check", comments = "Disable fly check for operators") public static boolean disableOpFlyCheck = false;
    @ConfigInfo(name = "disable-op-move-check", comments = "Disable move check for operators") public static boolean disableOpMoveCheck = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
