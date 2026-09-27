/**
 * 原版行为保真层（Phase 0 骨架）。
 * <p>
 * 三层开关体系：L0 默认（跟随 Paper/Folia）&lt; L1 预设
 * （{@link fun.bm.mili.vanilla.VanillaTogglePresets#PRESET_TECHNICAL_SURVIVAL}，
 * 与 TechnicalSurvivalModeConfig 联动）&lt; L2 细粒度规则
 * （mili_config.toml 的 vanilla 段）。
 * <p>
 * 设计文档：docs/ARCHITECTURE.md 第 4 章（生电子系统），
 * 原版行为审计矩阵见 4.2 节。
 */
package fun.bm.mili.vanilla;
