package `fun`.bm.mili.config.modules.function.protocol
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "xaeromap", directory = ["protocol"])
class XaeroMapProtocolConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable XaeroMap protocol support")
        @JvmField var enabled = false
        @ConfigInfo(name = "xaero-map-server-id", comments = "XaeroMap server ID") @JvmField var xaeroMapServerID = 0
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}