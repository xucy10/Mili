package fun.bm.mili.config.modules.function;

import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.protocol.CarpetServerProtocol;

import java.util.Set;

@ConfigClassInfo(name = "creative_fly_no_clip", category = EnumConfigCategory.FUNCTION)
public class CreativeFlyNoClipConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            是否启用创造模式飞行无碰撞。
            启用后，创造模式玩家飞行时不会与方块碰撞。
            可允许玩家无障碍穿过方块。""")
    public static boolean enabled = false;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> e) {
        CarpetServerProtocol.CarpetRules.register(CarpetServerProtocol.CarpetRule.of("carpet", "creativeNoClip", enabled));
    }
}