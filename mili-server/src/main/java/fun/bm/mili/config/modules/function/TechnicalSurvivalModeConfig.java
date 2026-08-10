package fun.bm.mili.config.modules.function;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "technical-survival-mode")
public class TechnicalSurvivalModeConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments =
            """
                    MC 技术性生存模式总开关。
                    启用后会自动绕过多个 Paper 限制配置，包括：
                    拥挤伤害、卡住实体 POI 重试延迟、末影水晶无敌修复、
                    TNT 每刻最大刻数、怪物生成计数、蜜蜂释放冷却、漏斗满仓冷却。""")
    public static boolean enabled = false;
}
