package `fun`.bm.mili.config.modules.function
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "language")
class LanguageConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "locale", comments = "Server language locale") @JvmField var locale = "en_us"
        @JvmField var lang = "en_us"
        @ConfigInfo(name = "full-blocking-load", comments = "Enable full blocking language load") @JvmField var full_blocking_load = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}