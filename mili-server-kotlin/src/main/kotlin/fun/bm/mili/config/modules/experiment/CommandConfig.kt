package `fun`.bm.mili.config.modules.experiment

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "command")
class CommandConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "function", comments = "Enable /function command") @JvmField var function = true
        @ConfigInfo(name = "scoreboard", comments = "Enable /scoreboard command") @JvmField var scoreboard = true
        @ConfigInfo(name = "tick", comments = "Enable /tick command") @JvmField var tick = true
        @ConfigInfo(name = "save-all", comments = "Enable /save-all command") @JvmField var saveAll = true
        @ConfigInfo(name = "log-all-process", comments = "Log all save-all process") @JvmField var logAllProcess = false
        @ConfigInfo(name = "save-all-timeout", comments = "Save-all timeout in ms") @JvmField var saveAllTimeout = 60000L
        @ConfigInfo(name = "waypoint", comments = "Enable waypoint command") @JvmField var waypoint = true
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
