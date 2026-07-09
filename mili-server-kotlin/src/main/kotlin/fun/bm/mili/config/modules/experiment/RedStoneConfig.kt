package `fun`.bm.mili.config.modules.experiment

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "redstone")
class RedStoneConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "old-block-remove-behaviour", comments = "Use old block remove behaviour") @JvmField var oldBlockRemoveBehaviour = false
        @ConfigInfo(name = "instant-block-updater", comments = "Use instant block updater") @JvmField var instantBlockUpdater = false
        @ConfigInfo(name = "redstone-ignore-upwards-update", comments = "Ignore upwards redstone updates") @JvmField var redstoneIgnoreUpwardsUpdate = false
        @ConfigInfo(name = "cce", comments = "Shulker box CCE reintroduced") @JvmField var cce = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
