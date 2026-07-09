package `fun`.bm.mili.config.modules.function
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "container-expansion")
class ContainerExpansionConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable container expansion") @JvmField var enabled = false
        @ConfigInfo(name = "barrel-rows", comments = "Barrel rows count (3 to 6)") @JvmField var barrelRows = 3
        @ConfigInfo(name = "enderchest-rows", comments = "Enderchest rows count (1 to 6)") @JvmField var enderchestRows = 3
        @ConfigInfo(name = "nbt-shulker-stackable", comments = "Enable NBT shulker stacking") @JvmField var nbtShulkerStackable = false
        @ConfigInfo(name = "shulker-count", comments = "Max shulker stack count") @JvmField var shulkerCount = 1
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}