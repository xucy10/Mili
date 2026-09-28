package fun.bm.mili.vanilla;

import java.util.Objects;
import java.util.Set;

/**
 * 原版行为开关定义（Carpet 风格规则名）。
 * <p>
 * 一条开关对应一个可独立还原/保留的原版行为点（如 TNT 复制机、
 * 即时方块更新器）。id 沿用 Carpet 社区命名以便生电玩家零学习成本，
 * 并与 {@code fun.bm.mili.carpet} 兼容层的既有规则名一脉相承。
 * <p>
 * 分层仲裁：显式 L2 配置 &gt; L1 预设（TechnicalSurvivalMode）&gt; 默认值
 * （见 {@link VanillaToggleRegistry}）。
 *
 * @param id               规则 id（Carpet 风格，如 {@code tntDupingFix}），全局唯一
 * @param category         分类（duping / timing / updater / suppression / legacy …）
 * @param description     中文说明（说明保留该行为 vs 修复的语义方向）
 * @param defaultValue    默认值；L0 层（跟随 Paper/Folia）的语义即"全部默认值生效"
 * @param requiredPatches 生效所需的服务端补丁号（如 "0125"）；为空表示纯配置层即可生效
 */
public record VanillaToggle(
        String id,
        String category,
        String description,
        boolean defaultValue,
        Set<String> requiredPatches) {

    public VanillaToggle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(description, "description");
        requiredPatches = requiredPatches == null ? Set.of() : Set.copyOf(requiredPatches);
    }

    /** 便捷工厂：无补丁依赖的规则。 */
    public static VanillaToggle of(String id, String category, String description, boolean defaultValue) {
        return new VanillaToggle(id, category, description, defaultValue, Set.of());
    }

    /** 便捷工厂：声明依赖补丁号的规则。 */
    public static VanillaToggle of(String id, String category, String description,
                                  boolean defaultValue, Set<String> requiredPatches) {
        return new VanillaToggle(id, category, description, defaultValue, requiredPatches);
    }
}
