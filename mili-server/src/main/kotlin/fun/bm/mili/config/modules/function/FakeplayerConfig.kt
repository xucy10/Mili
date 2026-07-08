@file:JvmName("FakeplayerConfigKt")
package fun.bm.mili.config.modules.function

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import fun.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory
import org.leavesmc.leaves.bot.ServerBot
import org.leavesmc.leaves.command.bot.BotCommand

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "fakeplayer")
class FakeplayerConfig : ConfigModule {

    companion object {
        @ConfigInfo(name = "enable", comments = "Enable fakeplayer functionality")
        @JvmField var enable = true

        @ConfigInfo(name = "unable-fakeplayer-names", comments = "List of names that cannot be used for fakeplayers")
        @JvmField var unableNames: List<String> = listOf("player-name")

        @ConfigInfo(name = "limit", comments = "Maximum number of fakeplayers allowed")
        @JvmField var limit = 10

        @ConfigInfo(name = "prefix", comments = "Prefix for fakeplayer names")
        @JvmField var prefix = ""

        @ConfigInfo(name = "suffix", comments = "Suffix for fakeplayer names")
        @JvmField var suffix = ""

        @ConfigInfo(name = "regen-amount", comments = "Regeneration amount for fakeplayers")
        @JvmField var regenAmount = 0.0

        @ConfigInfo(name = "resident-fakeplayer", comments = "Allow fakeplayers to be resident")
        @JvmField var canResident = false

        @ConfigInfo(name = "open-fakeplayer-inventory", comments = "Allow opening fakeplayer inventory")
        @JvmField var canOpenInventory = false

        @ConfigInfo(name = "use-action", comments = "Allow fakeplayers to use actions")
        @JvmField var canUseAction = true

        @ConfigInfo(name = "modify-config", comments = "Allow modifying fakeplayer config")
        @JvmField var canModifyConfig = false

        @ConfigInfo(name = "manual-save-and-load", comments = "Allow manual save and load of fakeplayers")
        @JvmField var canManualSaveAndLoad = false

        @ConfigInfo(name = "cache-skin", comments = "Use skin cache for fakeplayers")
        @JvmField var useSkinCache = false

        @ConfigInfo(name = "always-send-data", comments = "Always send data for fakeplayers")
        @JvmField var canSendDataAlways = true

        @ConfigInfo(name = "skip-sleep-check", comments = "Skip sleep check for fakeplayers")
        @JvmField var canSkipSleep = false

        @ConfigInfo(name = "spawn-phantom", comments = "Allow phantoms to spawn for fakeplayers")
        @JvmField var canSpawnPhantom = false

        @ConfigInfo(name = "simulation-distance", comments = "Simulation distance for fakeplayers (-1 for default)")
        @JvmField var simulationDistance = -1

        @ConfigInfo(name = "enable-locator-bar", comments = "Enable locator bar for fakeplayers")
        @JvmField var enableLocatorBar = false

        @JvmField var tickType: ServerBot.TickType = ServerBot.TickType.ENTITY_LIST

        @JvmStatic
        fun getSimulationDistance(bot: ServerBot): Int =
            if (simulationDistance == -1) bot.bukkitEntity.simulationDistance else simulationDistance
    }

    private var command: BotCommand? = null
    private var registered = false

    override fun onLoaded(configInstance: CommentedFileConfig) {
        if (enable) {
            command = BotCommand().also { it.register() }
            registered = true
        }
    }

    override fun onUnloaded(configInstance: CommentedFileConfig) {
        if (registered) {
            command?.unregister()
            command = null
        }
    }
}