package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.feature.MiliPerfCommand;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * mili - /mili-perf command registration.
 */
@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "mili-perf-command")
public class MiliPerfCommandConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            Enable the /mili-perf command. Provides status, affinity,
            region load, tick profiler, and deadlock stats. Permission:
            mili.commands.perf""")
    public static boolean enabled = true;

    @DoNotLoad
    private static MiliPerfCommand command;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            if (command == null) {
                command = new MiliPerfCommand();
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
