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
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
