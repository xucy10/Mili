package fun.bm.mili.config.modules.experiment;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "entity-damage-source-trace")
public class EntityDamageSourceTraceConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable entity damage source trace") public static boolean enabled = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
