package fun.bm.mili.carpet.config.modules;

import fun.bm.mili.carpet.CarpetCompatSync;
import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(
        category = EnumConfigCategory.ROOT,
        name = "core",
        directory = {"carpet"}
)
public class CoreConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用 Carpet 兼容功能。
            如果你想使用来自 Carpet 修改器的部分功能，
            你需要启用此项。
            （部分功能不受此选项管控）

            仅 general 目录下的规则受此选项控制。
            警告：如果你启用了此项，Mili 配置中对应的原始配置将被覆盖。""")
    public static boolean enabled = false;

    @Override
    public void beforeFinalLoad() {
        CarpetCompatSync.apply();
    }

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
    }
}
