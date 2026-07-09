package `fun`.bm.mili.config.modules.function

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "redstone-function")
class RedStoneConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable redstone function") @JvmField var enabled = false
        @ConfigInfo(name = "cce", comments = "Shulker box CCE reintroduced") @JvmField var cce = false
        @ConfigInfo(name = "shears", comments = "Enable shears wrench") @JvmField var shears = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
