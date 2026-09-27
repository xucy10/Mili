package fun.bm.mili.config.modules.experiment;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "ray_tracking_entity_tracker")
public class RayTrackingEntityTrackerConfig implements IConfigModule {
    @ConfigInfo(name = "enabled")
    public static boolean enabled = false;
    @ConfigInfo(name = "skip_marker_armor_stands")
    public static boolean skipMarkerArmorStands = true;
    @ConfigInfo(name = "check_interval_ms")
    public static int checkIntervalMs = 10;
    @ConfigInfo(name = "tracing_distance")
    public static int tracingDistance = 48;
    @ConfigInfo(name = "hitbox_limit")
    public static int hitboxLimit = 50;
    /** 剔除视锥体垂直 FOV（度），保守上限避免过剔除。 */
    @ConfigInfo(name = "cull_fov")
    public static double cullFov = 115.0;
    /** 剔除视锥体宽高比，保守上限兼容超宽屏。 */
    @ConfigInfo(name = "cull_aspect")
    public static double cullAspect = 2.34;
}
