package fun.bm.mili.config.modules.function.protocol;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "servux", directory = {"protocol"})
public class ServuxProtocolConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable Servux protocol support") public static boolean enabled = false;
    @ConfigInfo(name = "hud-logger-protocol", comments = "Enable HUD logger protocol") public static boolean hudLoggerProtocol = false;
    @ConfigInfo(name = "hud-metadata-share-seed", comments = "Share seed via HUD metadata") public static boolean hudMetadataShareSeed = false;
    public static java.util.List<String> hudEnabledLoggers = java.util.Collections.emptyList();
    @ConfigInfo(name = "hud-update-interval", comments = "HUD update interval") public static int hudUpdateInterval = 20;
    @ConfigInfo(name = "hud-metadata-protocol", comments = "Enable HUD metadata protocol") public static boolean hudMetadataProtocol = false;
    @ConfigInfo(name = "max-delay", comments = "Max delay for structure sync") public static int maxDelay = 600;
    @ConfigInfo(name = "litematics-max-nbt-size", comments = "Max NBT size for Litematics") public static int litematicsMaxNbtSize = -1;
    @ConfigInfo(name = "litematics-enabled", comments = "Enable Litematics protocol") public static boolean litematicsEnabled = false;
    @ConfigInfo(name = "entity-protocol", comments = "Enable entity protocol") public static boolean entityProtocol = false;
    @ConfigInfo(name = "structure-protocol", comments = "Enable structure protocol") public static boolean structureProtocol = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
