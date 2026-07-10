package fun.bm.mili.config.modules.function.protocol;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "rei", directory = {"protocol"})
public class REIServerProtocolConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable REI protocol support") public static boolean enabled = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
