package `fun`.bm.mili.config.modules.misc
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "auto-update")
class AutoUpdateConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable auto update checks") @JvmField var enabled = true
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}