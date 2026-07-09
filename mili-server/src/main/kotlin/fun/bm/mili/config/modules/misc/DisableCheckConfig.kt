package `fun`.bm.mili.config.modules.misc

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "disable-check")
class DisableCheckConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "disable-op-fly-check", comments = "Disable fly check for operators") @JvmField var disableOpFlyCheck = false
        @ConfigInfo(name = "disable-op-move-check", comments = "Disable move check for operators") @JvmField var disableOpMoveCheck = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
