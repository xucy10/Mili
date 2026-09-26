package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

/*
 * This file is only for showing auto update config.
 * The function is implemented in luminol-server and not provide any function.
 * Please use luminol-server's auto update config.
 */
@ConfigClassInfo(
        category = EnumConfigCategory.MISC,
        name = "auto_update",
        comments = """
                定时检查 GitHub Releases 是否有新版本 jar。
                下载文件暂存于 auto_update/mili 并写入 auto_update/core.path，
                Hyacinthusclip 可在下次重启时使用。
                
                注意：完整配置选项应在 luminol 配置系统 >> misc >> auto_update 中编辑"""
)
public class AutoUpdateConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            服务器是否自动检查更新。
            你可以通过编辑此配置来启用它，因为它们都控制同一功能。
            其中任意一个启用，完整功能即会启用。""")
    public static boolean enabled = false;
}