package fun.bm.mili.carpet.config.modules;

import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(
        category = EnumConfigCategory.ROOT,
        name = "hopper_counter",
        directory = {"carpet"},
        comments = """
                漏斗计数器兼容，映射到 Mili 的羊毛漏斗计数器实现。"""
)
public class CounterCompatConfig implements IConfigModule {
    @ConfigInfo(name = "hopperCounters", comments = """
            启用现有的羊毛漏斗计数器实现。""")
    public static boolean hopperCounters = false;

    @ConfigInfo(name = "hopperCountersUnlimitedSpeed", comments = """
            移除计数器漏斗的传输速度限制。
            仅在 hopperCounters 启用时生效。""")
    public static boolean hopperCountersUnlimitedSpeed = false;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
    }
}
