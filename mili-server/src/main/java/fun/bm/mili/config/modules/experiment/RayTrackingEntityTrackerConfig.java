package fun.bm.mili.config.modules.experiment;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "ray-tracking-entity-tracker")
public class RayTrackingEntityTrackerConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable ray tracking entity tracker") public static boolean enabled = false;
    @ConfigInfo(name = "tracing-distance", comments = "Ray tracing distance") public static int tracingDistance = 128;
    @ConfigInfo(name = "hitbox-limit", comments = "Hitbox limit") public static int hitboxLimit = 32;
    @ConfigInfo(name = "check-interval-ms", comments = "Check interval in ms") public static long checkIntervalMs = 50L;
    @ConfigInfo(name = "skip-marker-armor-stands", comments = "Skip marker armor stands") public static boolean skipMarkerArmorStands = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
