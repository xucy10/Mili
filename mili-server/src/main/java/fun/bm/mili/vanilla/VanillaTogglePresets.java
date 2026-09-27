package fun.bm.mili.vanilla;

import java.util.Map;

/**
 * L1 预设集定义（Phase 0 骨架）。
 * <p>
 * {@link #PRESET_TECHNICAL_SURVIVAL} 是"生电服一键开关"的规则集合：
 * 技术生存服依赖的原版可利用行为（复制机、0tick、更新抑制装置等）
 * 全部保留，个别行为通过 L2 显式配置微调。
 * <p>
 * <b>占位说明：</b>下列规则为 Phase 0 占位，多数需要对应服务端补丁
 * （0125+ 号段）才能实际改变行为；{@link VanillaToggle#requiredPatches}
 * 标注了依赖关系。Phase 1 接线顺序：
 * <ol>
 *   <li>补丁 0125+ 在行为点插入 {@code VanillaToggleRegistry.isEnabled(id)} 查询；</li>
 *   <li>TechnicalSurvivalModeConfig 加载后调用 {@code registerPlaceholders()} 与 {@code applyPreset}；</li>
 *   <li>mili_config.toml 新增 vanilla 段映射到 {@code setExplicitOverride}。</li>
 * </ol>
 */
public final class VanillaTogglePresets {

    /** 技术生存预设（与 TechnicalSurvivalModeConfig 联动）。 */
    public static final String PRESET_TECHNICAL_SURVIVAL = "technical_survival";

    private VanillaTogglePresets() {}

    /**
     * 注册占位规则（Phase 1 由配置模块在启动期调用一次）。
     * <p>
     * 语义方向说明（保留 = 与原版 1:1，修复 = 按上游修复行为）：
     * 复制机类默认值跟随 L0（Paper 默认已修复 → defaultValue=false 表示
     * "不额外还原"），TSM 预设层将其翻转为保留。
     */
    public static void registerPlaceholders() {
        // ---- duping（复制机类，均需 0125+ 补丁还原路径）----
        reg(VanillaToggle.of("tntDupingFix", "duping",
                "false=保留原版 TNT 复制机（活塞推动 BUD 态 TNT）；true=修复", false,
                java.util.Set.of("0125")));
        reg(VanillaToggle.of("sandDupingFix", "duping",
                "false=保留落沙/混凝土粉末复制机；true=修复", false,
                java.util.Set.of("0125")));
        reg(VanillaToggle.of("railDupingFix", "duping",
                "false=保留矿车/铁轨复制机；true=修复", false,
                java.util.Set.of("0125")));
        reg(VanillaToggle.of("carpetDupingFix", "duping",
                "false=保留地毯复制机（附魔台绕法等）；true=修复", false,
                java.util.Set.of("0125")));

        // ---- timing / updater（时序与更新器）----
        reg(VanillaToggle.of("instantBlockUpdaterReintroduced", "updater",
                "true=启用即时方块更新器（0110 已实现，此处仅作为统一查询入口）", false));
        reg(VanillaToggle.of("fastRedstoneDust", "timing",
                "true=红石粉使用 Alternate Current 算法（0121 已实现）", false));
        reg(VanillaToggle.of("zeroTickPlants", "timing",
                "false=允许 0tick 作物催熟；true=修复（待审计 Paper 现状）", false,
                java.util.Set.of("0126")));

        // ---- suppression（更新抑制）----
        reg(VanillaToggle.of("updateSuppressionCrashFix", "suppression",
                "true=捕获更新抑制异常防崩服（0107/0108 已实现）；false=按原版崩溃", true));

        // ---- misc（其余待审计项，Phase 1 的行为审计矩阵逐项补充）----
        reg(VanillaToggle.of("quasiConnectivity", "timing",
                "true=保留活塞准连通性（QC/BUD）；仅当上游出现行为漂移时作为还原开关", true));
    }

    /**
     * 解析预设名为覆盖表；未知预设返回 {@code null}（调用方保持当前层不变）。
     */
    public static Map<String, Boolean> resolve(String name) {
        if (PRESET_TECHNICAL_SURVIVAL.equals(name)) {
            return Map.of(
                    "tntDupingFix", false,
                    "sandDupingFix", false,
                    "railDupingFix", false,
                    "carpetDupingFix", false,
                    "zeroTickPlants", false,
                    "instantBlockUpdaterReintroduced", true,
                    "fastRedstoneDust", true);
            // updateSuppressionCrashFix / quasiConnectivity 默认已符合 TSM 诉求，无需覆盖
        }
        return null;
    }

    private static void reg(VanillaToggle toggle) {
        VanillaToggleRegistry.register(toggle);
    }
}
