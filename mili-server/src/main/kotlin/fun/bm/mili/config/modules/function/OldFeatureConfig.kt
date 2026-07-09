package `fun`.bm.mili.config.modules.function

import com.electronwill.nightconfig.core.file.CommentedFileConfig
import `fun`.bm.mili.config.modules.ConfigModule
import me.earthme.luminol.config.flags.ConfigClassInfo
import me.earthme.luminol.config.flags.ConfigInfo
import me.earthme.luminol.enums.EnumConfigCategory

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "old-feature")
class OldFeatureConfig : ConfigModule {
    companion object {
        @ConfigInfo(name = "villager-void-trade", comments = "Allow trading with void villagers") @JvmField var villagerVoidTrade = false
        @ConfigInfo(name = "old-explosion-damage-calculator", comments = "Use old explosion damage calculator") @JvmField var oldExplosionDamageCalculator = false
        @ConfigInfo(name = "spawn-invulnerable-time", comments = "Set spawn invulnerable time") @JvmField var spawnInvulnerableTime = false
        @ConfigInfo(name = "old-zombie-reinforcement", comments = "Use old zombie reinforcement") @JvmField var oldZombieReinforcement = false
        @ConfigInfo(name = "old-raid-behavior", comments = "Use old raid behavior") @JvmField var oldRaidBehavior = false
    }
    override fun onLoaded(c: CommentedFileConfig) {}
    override fun onUnloaded(c: CommentedFileConfig) {}
}
