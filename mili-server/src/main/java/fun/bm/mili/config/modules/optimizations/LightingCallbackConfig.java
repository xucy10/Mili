package fun.bm.mili.config.modules.optimizations;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.utils.LightCallbackManager;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "lighting-callback")
public class LightingCallbackConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            启用光照引擎回调""")
    public static boolean enabled = false;

    @ConfigInfo(name = "track-sky-light", comments = """
            追踪天空光照变化""")
    public static boolean trackSkyLight = true;

    @ConfigInfo(name = "track-block-light", comments = """
            追踪方块光照变化""")
    public static boolean trackBlockLight = true;

    @ConfigInfo(name = "callback-delay-ticks", comments = """
            回调延迟（tick）""")
    public static int callbackDelayTicks = 0;

    @Override
    public void onLoaded(CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        LightCallbackManager.setEnabled(enabled);
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        LightCallbackManager.setEnabled(false);
    }
}
