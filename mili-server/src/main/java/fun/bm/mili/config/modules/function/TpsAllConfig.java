package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.feature.MiliTpsAllCommand;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "tpsall")
public class TpsAllConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            Enable /tpsall command (alias /mspt).
            Shows global TPS, MSPT and current region statistics in Chinese.""")
    public static boolean enabled = true;

    @ConfigInfo(name = "top-region-count", comments = """
            Number of busiest regions to display in /tpsall output.""")
    public static int topRegionCount = 5;

    @DoNotLoad
    private static MiliTpsAllCommand command;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            if (command == null) {
                command = new MiliTpsAllCommand();
            }
            command.register();
        }
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        if (command != null) {
            command.unregister();
            command = null;
        }
    }
}
