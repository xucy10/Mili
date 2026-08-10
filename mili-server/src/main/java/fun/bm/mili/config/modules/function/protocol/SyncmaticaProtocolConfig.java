package fun.bm.mili.config.modules.function.protocol;

import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "syncmatica", directory = {"protocol"})
public class SyncmaticaProtocolConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用 Syncmatica 协议支持""")
    public static boolean enabled = false;
    @ConfigInfo(name = "useQuota", comments = """
            是否限制投影文件大小？""")
    public static boolean useQuota = false;
    @ConfigInfo(name = "quota-Limit", comments = """
            投影文件最大大小（字节）""")
    public static int quotaLimit = 40000000;

    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> e) {
        SyncmaticaProtocol.init(enabled);
    }
}
