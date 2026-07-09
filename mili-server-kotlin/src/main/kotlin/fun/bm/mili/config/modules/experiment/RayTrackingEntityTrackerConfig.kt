package `fun`.bm.mili.config.modules.experiment

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "ray-tracking-entity-tracker")
class RayTrackingEntityTrackerConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable ray tracking entity tracker") @JvmField var enabled = false
        @ConfigInfo(name = "tracing-distance", comments = "Ray tracing distance") @JvmField var tracingDistance = 128
        @ConfigInfo(name = "hitbox-limit", comments = "Hitbox limit") @JvmField var hitboxLimit = 32
        @ConfigInfo(name = "check-interval-ms", comments = "Check interval in ms") @JvmField var checkIntervalMs = 50L
        @ConfigInfo(name = "skip-marker-armor-stands", comments = "Skip marker armor stands") @JvmField var skipMarkerArmorStands = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
