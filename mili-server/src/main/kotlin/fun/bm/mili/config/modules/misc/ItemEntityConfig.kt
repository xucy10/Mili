package `fun`.bm.mili.config.modules.misc

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "item-entity")
class ItemEntityConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "follow-tick-sequence-merge", comments = "Enable follow tick sequence merge") @JvmField var followTickSequenceMerge = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
