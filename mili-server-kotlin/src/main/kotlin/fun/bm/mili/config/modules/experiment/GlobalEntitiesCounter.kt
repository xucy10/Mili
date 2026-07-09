package `fun`.bm.mili.config.modules.experiment

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "global-entities-counter")
class GlobalEntitiesCounter : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable global entities counter") @JvmField var enabled = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
