package fun.bm.mili.config.modules.function;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.command.MiliPerfCommand;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "performance-monitor")
public class PerformanceMonitorConfig implements IConfigModule {

    @DoNotLoad
    private static MiliPerfCommand command = null;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        if (command == null) {
            command = new MiliPerfCommand();
        }
        command.register();
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        if (command != null) {
            command.unregister();
        }
    }
}
