@file:JvmName("CreativeFlyConfigKt")
package fun.bm.mili.config.modules.function
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import fun.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "creative-fly-noclip")
class CreativeFlyNoClipConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable creative fly no-clip") @JvmField var enabled = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}