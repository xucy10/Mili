package fun.bm.mili.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fun.bm.mili.config.modules.ConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "item-entity")
public class ItemEntityConfig implements ConfigModule {
    @ConfigInfo(name = "follow-tick-sequence-merge", comments = "Enable follow tick sequence merge") public static boolean followTickSequenceMerge = false;
    @Override public void onLoaded(CommentedFileConfig c) {}
    @Override public void onUnloaded(CommentedFileConfig c) {}
}
