package fun.bm.mili.config.modules.fixes;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.portal.PortalLinkListener;
import fun.bm.mili.portal.PortalLinkManager;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FIXES, name = "portal_link_fix", comments = """
        防止下界传送门串门。
        通过追踪传送门配对关系，确保传送门始终链接到正确的对应传送门。
        支持手动绑定和自动追踪。
        """)
public class PortalLinkFixConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = "启用传送门链接修复")
    public static boolean enabled = true;

    @ConfigInfo(name = "strict-matching", comments = "严格匹配模式：只允许链接到已注册的传送门对")
    public static boolean strictMatching = false;

    @ConfigInfo(name = "search-radius", comments = "传送门搜索半径（格），覆盖原版搜索范围。越小越不容易串门")
    public static int searchRadius = 16;

    @ConfigInfo(name = "auto-record", comments = "自动记录新的传送门配对关系")
    public static boolean autoRecord = true;

    @DoNotLoad
    private static PortalLinkListener listener = null;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        PortalLinkManager.setEnabled(enabled);
        PortalLinkManager.setSearchRadius(searchRadius);
        PortalLinkManager.setStrictMatching(strictMatching);
        PortalLinkManager.load();
        if (enabled && listener == null) {
            listener = new PortalLinkListener();
            listener.register();
        }
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        PortalLinkManager.setEnabled(false);
        if (listener != null) {
            listener.unregister();
            listener = null;
        }
    }
}
