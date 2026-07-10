package fun.bm.mili.config.modules.function.protocol;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "syncmatica", directory = {"protocol"})
public class SyncmaticaProtocolConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable Syncmatica protocol support") public static boolean enabled = false;
    @ConfigInfo(name = "use-quota", comments = "Enable sync quota") public static boolean useQuota = false;
    @ConfigInfo(name = "quota-limit", comments = "Sync quota limit") public static int quotaLimit = 400000;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
