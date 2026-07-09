package `fun`.bm.mili.config.modules.experiment
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "cross-region-helper")
class CrossRegionHelperConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable cross-region event helper") @JvmField var enabled = false
        @ConfigInfo(name = "queue-poll-timeout-ms", comments = "Poll timeout in ms") @JvmField var queuePollTimeoutMs = 100L
        @ConfigInfo(name = "max-pending-events-per-region", comments = "Max pending events per region") @JvmField var maxPendingEventsPerRegion = 256
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}