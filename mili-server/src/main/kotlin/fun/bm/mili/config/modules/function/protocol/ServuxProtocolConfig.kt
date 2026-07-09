package `fun`.bm.mili.config.modules.function.protocol
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "servux", directory = ["protocol"])
class ServuxProtocolConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "enabled", comments = "Enable Servux protocol support")
        @JvmField var enabled = false
        @ConfigInfo(name = "hud-logger-protocol", comments = "Enable HUD logger protocol") @JvmField var hudLoggerProtocol = false
        @ConfigInfo(name = "hud-metadata-share-seed", comments = "Share seed via HUD metadata") @JvmField var hudMetadataShareSeed = false
        @ConfigInfo(name = "hud-enabled-loggers", comments = "Enabled HUD loggers") @JvmField var hudEnabledLoggers: List<String> = emptyList()
        @ConfigInfo(name = "hud-update-interval", comments = "HUD update interval") @JvmField var hudUpdateInterval = 20
        @ConfigInfo(name = "hud-metadata-protocol", comments = "Enable HUD metadata protocol") @JvmField var hudMetadataProtocol = false
        @ConfigInfo(name = "max-delay", comments = "Max delay for structure sync") @JvmField var maxDelay = 600
        @ConfigInfo(name = "litematics-max-nbt-size", comments = "Max NBT size for Litematics") @JvmField var litematicsMaxNbtSize = -1
        @ConfigInfo(name = "litematics-enabled", comments = "Enable Litematics protocol") @JvmField var litematicsEnabled = false
        @ConfigInfo(name = "entity-protocol", comments = "Enable entity protocol") @JvmField var entityProtocol = false
        @ConfigInfo(name = "structure-protocol", comments = "Enable structure protocol") @JvmField var structureProtocol = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}