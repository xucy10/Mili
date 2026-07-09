package `fun`.bm.mili.config.modules.function.protocol
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "alternative-block-placement", directory = ["protocol"])
class AlternativeBlockPlacementProtocolConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable alternative block placement protocol")
        @JvmField var enabled = false
        @ConfigInfo(name = "alternative-block-placement", comments = "Alternative block placement type") @JvmField var alternativeBlockPlacement: `fun`.bm.mili.enums.EnumAlternativePlaceType = `fun`.bm.mili.enums.EnumAlternativePlaceType.NONE

        @JvmStatic
        fun needIgnoreDistance(): Boolean = true
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}