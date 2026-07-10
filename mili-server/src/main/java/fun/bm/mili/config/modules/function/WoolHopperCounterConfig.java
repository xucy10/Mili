package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "wool-hopper-counter")
public class WoolHopperCounterConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable wool hopper counter") public static boolean enabled = false;
    @ConfigInfo(name = "unlimited-speed", comments = "Enable unlimited hopper speed") public static boolean unlimitedSpeed = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
