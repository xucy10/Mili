package fun.bm.mili.config.modules.function.protocol;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "alternative-block-placement", directory = {"protocol"})
public class AlternativeBlockPlacementProtocolConfig implements ConfigModule {
    @ConfigInfo(name = "enabled", comments = "Enable alternative block placement protocol") public static boolean enabled = false;
    public static EnumAlternativePlaceType alternativeBlockPlacement = EnumAlternativePlaceType.NONE;
    public static boolean needIgnoreDistance() { return true; }
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
