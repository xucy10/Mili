package fun.bm.mili.vanilla;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 原版行为细粒度规则注册表（Phase 0 骨架，零接线）。
 * <p>
 * 三层仲裁模型（docs/ARCHITECTURE.md 4.1）：
 * <pre>
 *   生效值 = 显式 L2 配置 > L1 预设（TechnicalSurvivalMode 等）> 规则默认值
 * </pre>
 * {@link #isEnabled(String)} 是未来补丁 hook 的<b>统一查询接口</b>：
 * 新补丁不再直读散落的 config 字段，而是查询本注册表 —— 这是消除
 * "一个行为开关散落三处配置"的结构性方案。
 * <p>
 * <b>Phase 0 约束：</b>无生产调用者、无静态副作用（注册表启动时为空）；
 * Phase 1 由 TechnicalSurvivalModeConfig / GeneralCompatConfig 加载完成后
 * 调用 {@link #register} 与 {@link VanillaTogglePresets#registerPlaceholders()}，
 * 补丁 hook（0125+）随后接入 {@link #isEnabled}。
 */
public final class VanillaToggleRegistry {

    /** 已注册规则（启动期单线程写，之后只读）。 */
    private static final Map<String, VanillaToggle> TOGGLES = new LinkedHashMap<>();

    /** L1 预设层覆盖（volatile 快照，读多写少）。 */
    private static volatile Map<String, Boolean> presetLayer = Map.of();

    /** L2 显式配置层覆盖（优先级最高）。 */
    private static volatile Map<String, Boolean> explicitLayer = Map.of();

    private VanillaToggleRegistry() {}

    /** 注册规则；重名跳过（重载幂等，不抛异常）。 */
    public static synchronized void register(VanillaToggle toggle) {
        Objects.requireNonNull(toggle, "toggle");
        if (TOGGLES.containsKey(toggle.id())) {
            return; // 幂等：reload 场景下已注册的规则跳过
        }
        TOGGLES.put(toggle.id(), toggle);
    }

    /** 清空全部注册（重载前调用以保证预设重新生效）。 */
    public static synchronized void clear() {
        TOGGLES.clear();
        presetLayer = Map.of();
        explicitLayer = Map.of();
    }

    /** 规则是否已注册。 */
    public static synchronized boolean isRegistered(String id) {
        return TOGGLES.containsKey(id);
    }

    /**
     * 查询规则生效值（三层仲裁）。
     * 未注册的 id 一律返回 {@code false} 并由调用方决定是否告警
     * （补丁只应查询自己声明过的 requiredPatches 对应规则）。
     */
    public static boolean isEnabled(String id) {
        Boolean explicit = explicitLayer.get(id);
        if (explicit != null) {
            return explicit;
        }
        Boolean preset = presetLayer.get(id);
        if (preset != null) {
            return preset;
        }
        VanillaToggle toggle;
        synchronized (VanillaToggleRegistry.class) {
            toggle = TOGGLES.get(id);
        }
        return toggle != null && toggle.defaultValue();
    }

    /** 应用 L1 预设（见 {@link VanillaTogglePresets}）；{@code null} 值表示清除该层。 */
    public static void applyPreset(String name) {
        Map<String, Boolean> resolved = VanillaTogglePresets.resolve(name);
        presetLayer = resolved == null ? Map.of() : Map.copyOf(resolved);
    }

    /** 设置 L2 显式覆盖（来自 mili_config.toml 的 vanilla 段）。 */
    public static void setExplicitOverride(String id, boolean value) {
        Map<String, Boolean> next = new LinkedHashMap<>(explicitLayer);
        next.put(id, value);
        explicitLayer = Map.copyOf(next);
    }

    /** 当前生效值快照（观测命令 /mili toggles 用，Phase 2 接线）。 */
    public static synchronized Map<String, Boolean> snapshot() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, VanillaToggle> e : TOGGLES.entrySet()) {
            out.put(e.getKey(), isEnabled(e.getKey()));
        }
        return Map.copyOf(out);
    }

    /** 已注册规则数（测试/观测用）。 */
    public static synchronized int size() {
        return TOGGLES.size();
    }
}
