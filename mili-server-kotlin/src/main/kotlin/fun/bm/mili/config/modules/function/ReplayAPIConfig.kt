package `fun`.bm.mili.config.modules.function
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "replay-api")
class ReplayAPIConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable Replay API support") @JvmField var enabled = false
        @ConfigInfo(name = "enable-cache", comments = "Enable photographer cache") @JvmField var enableCache = false
        @ConfigInfo(name = "cache-photographer-time", comments = "Cache photographer time") @JvmField var cachePhotographerTime = 300L
        @ConfigInfo(name = "cache-photographer-size", comments = "Cache photographer size") @JvmField var cachePhotographerSize = 100L
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}