package fun.bm.mili.config.modules.function;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "old-feature")
public class OldFeatureConfig implements IConfigModule {
    @TransformedConfig(name = "spawn_invulnerable_time", directory = {"misc", "old-feature"})
    @ConfigInfo(name = "spawn_invulnerable_time")
    public static boolean spawnInvulnerableTime = false;

    @TransformedConfig(name = "old_zombie_reinforcement", directory = {"misc", "old-feature"})
    @ConfigInfo(name = "old_zombie_reinforcement")
    public static boolean oldZombieReinforcement = false;

    @TransformedConfig(name = "old_explosion_damage_calculator", directory = {"misc", "old-feature"})
    @ConfigInfo(name = "old_explosion_damage_calculator")
    public static boolean oldExplosionDamageCalculator = false;

    @TransformedConfig(name = "old_raid_behavior", directory = {"misc", "old-feature"})
    @TransformedConfig(name = "give_bad_omen_when_kill_raid_captain", directory = {"misc", "revert_raid_changes"}, transformComments = false)
    @ConfigInfo(name = "old_raid_behavior")
    public static boolean oldRaidBehavior = false;

    @TransformedConfig(name = "villager-infinite-trade", directory = {"function", "villager"}, transformComments = false)
    @TransformedConfig(name = "villager-infinite-trade", directory = {"misc", "villager"}, transformComments = false)
    @TransformedConfig(name = "villager-infinite-trade", directory = {"misc", "villager-config"}, transformComments = false)
    @ConfigInfo(name = "villager-void-trade", comments =
            """
                    Allow villager void trade.""")
    public static boolean villagerVoidTrade = false;
}