package fun.bm.mili.config.modules.function.protocol;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "appleskin", directory = {"protocol"})
public class AppleSkinProtocolConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable AppleSkin protocol support") public static boolean enabled = false;
    @ConfigInfo(name = "sync-tick-interval", comments = "Sync tick interval") public static int syncTickInterval = 20;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
