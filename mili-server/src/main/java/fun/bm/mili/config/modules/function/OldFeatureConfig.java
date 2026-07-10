package fun.bm.mili.config.modules.function;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "old-feature")
public class OldFeatureConfig implements ConfigModule {
    @ConfigInfo(name = "villager-void-trade", comments = "Allow trading with void villagers") public static boolean villagerVoidTrade = false;
    @ConfigInfo(name = "old-explosion-damage-calculator", comments = "Use old explosion damage calculator") public static boolean oldExplosionDamageCalculator = false;
    @ConfigInfo(name = "spawn-invulnerable-time", comments = "Set spawn invulnerable time") public static boolean spawnInvulnerableTime = false;
    @ConfigInfo(name = "old-zombie-reinforcement", comments = "Use old zombie reinforcement") public static boolean oldZombieReinforcement = false;
    @ConfigInfo(name = "old-raid-behavior", comments = "Use old raid behavior") public static boolean oldRaidBehavior = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
