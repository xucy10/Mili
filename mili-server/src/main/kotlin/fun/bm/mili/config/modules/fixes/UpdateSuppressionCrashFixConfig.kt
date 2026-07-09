package fun.bm.mili.config.modules.fixes
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import fun.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FIXES, name = "update-suppression-crash-fix")
class UpdateSuppressionCrashFixConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Fix update suppression crash") @JvmField var enabled = true
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}