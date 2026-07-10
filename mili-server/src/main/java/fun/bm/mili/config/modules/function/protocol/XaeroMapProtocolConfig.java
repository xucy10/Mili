package fun.bm.mili.config.modules.function.protocol;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "xaeromap", directory = {"protocol"})
public class XaeroMapProtocolConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable XaeroMap protocol support") public static boolean enabled = false;
    @ConfigInfo(name = "xaero-map-server-id", comments = "XaeroMap server ID") public static int xaeroMapServerID = 0;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
