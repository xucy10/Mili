package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "old-mc")
public class OldMCConfig implements IConfigModule {
    @TransformedConfig(name = "allow_anvil_destroy_item_entities", directory = {"misc", "old_mc"}, transformComments = false)
    @ConfigInfo(name = "allow-anvil-destroy-item-entities", comments =
            """
                    允许铁砧落下时摧毁其下方的物品实体。""")
    public static boolean allowAnvilDestroyItemEntities = false;

    @TransformedConfig(name = "allow_entity_portal_with_passenger", directory = {"misc", "old_mc"}, transformComments = false)
    @ConfigInfo(name = "allow-entity-portal-with-passenger", comments =
            """
                    允许实体在作为乘骑者或携带乘骑者时使用传送门。
                    禁用此选项可修复 MC-67。""")
    public static boolean allowEntityPortalWithPassenger = false;

    @TransformedConfig(name = "disable_vault_blacklist", directory = {"misc", "old_mc"}, transformComments = false)
    @ConfigInfo(name = "disable-vault-blacklist", comments =
            """
                    禁用宝库黑名单，允许玩家从同一个宝库多次获取奖励。""")
    public static boolean disableVaultBlacklist = false;
}
