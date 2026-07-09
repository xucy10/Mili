package fun.bm.mili.config.modules.removed
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import fun.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.REMOVED, name = "removed")
class RemovedConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "removed-features", comments = "List of removed feature IDs") @JvmField var removedFeatures = listOf<String>()
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}