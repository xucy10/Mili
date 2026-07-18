package fun.bm.mili.config.modules.function.protocol;

import fun.bm.mili.enums.EnumAlternativePlaceType;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "alternative_block_placement", directory = {"protocol"})
public class AlternativeBlockPlacementProtocolConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = """
            指定精确放置协议类型
            NONE 禁用精确放置协议
            CARPET 精确放置协议版本 2
            CARPET_FIX 增强精确放置协议版本 2（需要客户端安装 MasaGadget）
            LITEMATICA 精确放置协议版本 3""")
    public static EnumAlternativePlaceType alternativeBlockPlacement = EnumAlternativePlaceType.NONE;

    public static boolean needIgnoreDistance() {
        return alternativeBlockPlacement != EnumAlternativePlaceType.NONE;
    }
}