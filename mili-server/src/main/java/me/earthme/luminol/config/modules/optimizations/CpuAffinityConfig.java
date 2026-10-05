package me.earthme.luminol.config.modules.optimizations;

import fun.bm.mili.rust.TomlConfigData;
import com.mojang.logging.LogUtils;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.*;
import me.earthme.luminol.enums.EnumConfigCategory;
import net.openhft.affinity.Affinity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.BitSet;
import java.util.List;
import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "cpu_affinity")
public class CpuAffinityConfig implements IConfigModule {
    @TransformedConfig(name = "enabled", directory = {"misc", "cpu_affinity"})
    @HotReloadUnsupported
    @ConfigInfo(name = "enabled", comments = "Using this you could pin the threads of tick region scheduler to cpu cores listed in the config 'tickregion_affinity' following, \n" +
            "which is useful for those CPU with P and E cores (such as 12/13/14 gen Intel Core CPUs and so on.)")
    public static boolean cpuAffinityEnabled = false;
    @HotReloadUnsupported
    @TransformedConfig(name = "enabled", directory = {"misc", "tickregion_affinity"})
    @ConfigInfo(name = "tickregion_affinity", comments = "The core number you want the tick region threads to bind on")
    public static List<String> tickRegionAffinity = Affinity.getAffinity()
            .stream()
            .mapToObj(String::valueOf)
            .toList();

    @HotReloadUnsupported
    @ConfigInfo(name = "physical-cores-only", comments = """
            自动只绑定物理核，无需手工填写 tickregion_affinity。
            多数单路 x86 上逻辑处理器编号规则是「先编各物理核的首个线程，再编各自的超线程兄弟」，
            因此前一半通常就是每个物理核的单线程实例。绑定它可以避免两个 SMT 线程争抢同一物理核，
            tick 线程被系统扔到超线程伙伴（乃至 E 核）上是「同一台机器上某些区域特别慢」的典型来源。
            注意这是启发式，P/E 核混合 CPU 上请以日志打印的实际集合和 /tps 的 MSPT 变化来验证。
            需要先把 enabled 打开；需重启生效。""")
    public static boolean physicalCoresOnly = false;

    @DoNotLoad
    private static boolean inited = false;
    @DoNotLoad
    private static final Logger LOGGER = LogUtils.getLogger();
    @DoNotLoad
    public static BitSet tickRegionAffinityBitSet;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> e) {
        if (!cpuAffinityEnabled) return;

        // Mili start - 自动只绑定物理核：无需用户手工填写核心编号
        java.util.List<String> coresToBind = tickRegionAffinity;
        if (physicalCoresOnly) {
            coresToBind = computePhysicalCoreSet();
            if (coresToBind.isEmpty()) {
                LOGGER.warn("无法可靠推断物理核心，回退为使用 tickregion_affinity 列表");
            } else {
                LOGGER.info("已启用自动物理核绑定，实际绑定集合: {}", coresToBind);
            }
        }
        // Mili end
        tickRegionAffinityBitSet = parseAffinity(coresToBind.isEmpty() ? tickRegionAffinity : coresToBind);
        LOGGER.info("Tick region thread now bound to: {}", tickRegionAffinityBitSet);

        if (!inited) {
            inited = true;
        }
    }

    /**
     * 推断物理核集合。
     * <p>
     * 多数单路 x86 机器上，逻辑处理器的编号规则是：先编所有物理核的第一个线程，再编各自的超线程兄弟，
     * 因此「前一半」通常就是每个物理核的单线程实例。绑定它可以避免两个 SMT 线程争抢同一个物理核。
     * <p>
     * 注意这是启发式：在 P/E 核混合的处理器上编号规则更复杂，无法保证前一半都是 P 核。
     * 启用后请以日志里打印的实际集合，以及 /tps 里 MSPT 的变化为准来验证效果。
     */
    private static java.util.List<String> computePhysicalCoreSet() {
        final int logical = Runtime.getRuntime().availableProcessors();
        if (logical <= 2) {
            return java.util.List.of();
        }
        final int physicalGuess = logical / 2;
        java.util.List<String> cores = new java.util.ArrayList<>(physicalGuess);
        for (int i = 0; i < physicalGuess; i++) {
            cores.add(Integer.toString(i));
        }
        return cores;
    }

    private BitSet parseAffinity(List<String> affinity) {
        int maxAvailable = Runtime.getRuntime().availableProcessors();
        BitSet affinitySet = new BitSet(affinity.size());
        affinity.stream()
                .mapToInt(str -> {
                    try {
                        return Integer.parseInt(str);
                    } catch (NumberFormatException ignored) {
                        LOGGER.warn("Unable to parse cpu id {} to a valid number, falling back to 0.", str);
                        return 0;
                    }
                })
                .distinct()
                .filter(cpuId -> {
                    if (cpuId >= 0 && cpuId < maxAvailable) {
                        return true;
                    } else {
                        LOGGER.warn("Invalid cpu id {}, ignoring.", cpuId);
                        return false;
                    }
                })
                .forEach(affinitySet::set);
        return affinitySet;
    }
}
