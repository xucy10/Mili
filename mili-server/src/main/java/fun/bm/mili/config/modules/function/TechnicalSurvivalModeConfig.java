package fun.bm.mili.config.modules.function;

import fun.bm.mili.rust.TomlConfigData;
import fun.bm.mili.vanilla.VanillaTogglePresets;
import fun.bm.mili.vanilla.VanillaToggleRegistry;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.FUNCTION, name = "technical-survival-mode")
public class TechnicalSurvivalModeConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments =
            """
                    MC 技术性生存模式总开关。
                    启用后会自动绕过多个 Paper 限制配置，包括：
                    拥挤伤害、卡住实体 POI 重试延迟、末影水晶无敌修复、
                    TNT 每刻最大刻数、怪物生成计数、蜜蜂释放冷却、漏斗满仓冷却。
                    同时启用生电保真预设（VanillaTogglePresets.technical_survival）。""")
    public static boolean enabled = false;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exceptions) {
        // 接线 VanillaToggleRegistry：注册规则 + 按 TSM 开关应用预设
        // 幂等防护：reload 场景下先 clear 再重新注册
        VanillaToggleRegistry.clear();
        VanillaTogglePresets.registerPlaceholders();
        if (enabled) {
            VanillaToggleRegistry.applyPreset(VanillaTogglePresets.PRESET_TECHNICAL_SURVIVAL);
        }
    }
}
