package `fun`.bm.mili.config.modules.function.protocol
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "syncmatica", directory = ["protocol"])
class SyncmaticaProtocolConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable Syncmatica protocol support")
        @JvmField var enabled = false
        @ConfigInfo(name = "use-quota", comments = "Enable sync quota") @JvmField var useQuota = false
        @ConfigInfo(name = "quota-limit", comments = "Sync quota limit") @JvmField var quotaLimit = 400000
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}