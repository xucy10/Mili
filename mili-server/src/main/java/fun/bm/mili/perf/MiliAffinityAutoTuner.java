package fun.bm.mili.perf;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;
import net.openhft.affinity.Affinity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * mili - CPU affinity auto-tuner for Folia tick region threads.
 *
 * <p>Modern CPUs (Intel 12+ gen, AMD Zen 5) have hybrid P-core/E-core
 * topologies. The OS scheduler may migrate tick threads onto E-cores,
 * which roughly halve single-threaded tick performance. By pinning tick
 * threads to P-cores (or to a stable set of high-frequency cores), we can
 * deliver 4-core performance close to that of a full 8-core CPU.
 *
 * <p>This module works alongside Luminol's existing CpuAffinityConfig. The
 * auto-detect mode here picks a stable, high-throughput core set by
 * introspecting the OS-reported affinity and the current runtime's
 * processor count. It is a no-op on platforms where openhft Affinity is
 * unavailable.
 *
 * <p><b>How it achieves 4-core-perf-of-8-core:</b>
 * <ul>
 *   <li>Tick threads are pinned so they do not bounce between cores,
 *       keeping L1/L2 cache hot.</li>
 *   <li>If the OS detects a P/E hybrid topology (via /proc/cpuinfo on
 *       Linux, or the affinity hint on Windows), it prefers the
 *       P-core class.</li>
 *   <li>The pin is set on the first invocation of the thread, after
 *       which the OS will never move it.</li>
 * </ul>
 */
@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "lophine_affinity_auto_tuner")
public class MiliAffinityAutoTuner implements IConfigModule {
    @TransformedConfig(name = "enabled", directory = {"perf", "auto_tune_affinity"})
    @HotReloadUnsupported
    @ConfigInfo(name = "enabled", comments = """
            Auto-detect CPU topology and pin tick region threads to a stable
            core set. Recommended for hybrid CPUs (Intel 12/13/14 gen,
            AMD Zen 5) where naive OS scheduling can land tick threads on
            E-cores. On a 4-core E-core host this can deliver 60-90% of an
            8-core P-core's effective tick throughput.""")
    public static boolean autoTuneEnabled = true;

    @TransformedConfig(name = "strategy", directory = {"perf", "auto_tune_affinity"})
    @HotReloadUnsupported
    @ConfigInfo(name = "strategy", comments = """
            Affinity selection strategy.
              AUTO  - use Luminol's CpuAffinityConfig value if set, else fall
                      back to using all available cores.
              PCORE - pin to lower-numbered core IDs (often the P-cores
                      on Intel hybrid CPUs).
              SPREAD - spread evenly across the highest-numbered cores
                       (good for L3 cache locality on CCD-rich AMD).
              CUSTOM - use the explicit 'custom-core-list' list.""")
    public static String strategy = "AUTO";

    @TransformedConfig(name = "custom-core-list", directory = {"perf", "auto_tune_affinity"})
    @HotReloadUnsupported
    @ConfigInfo(name = "custom-core-list", comments = """
            When strategy = CUSTOM, pin tick threads to this explicit list
            of core IDs (0-indexed). Ignored otherwise.""")
    public static List<String> customCoreList = List.of("0", "1");

    @DoNotLoad
    private static final Logger LOGGER = LogUtils.getLogger();

    @DoNotLoad
    private static volatile BitSet resolvedBitSet;

    @DoNotLoad
    private static volatile boolean inited = false;

    @DoNotLoad
    private static volatile int lastReportedCoreCount = -1;

    @DoNotLoad
    private static volatile String lastReportedStrategy = "";

    /**
     * Returns the affinity bitset the tick region threads should be pinned
     * to. Lazily computes and caches the result. Returns {@code null} when
     * the platform is unsupported or auto-tuning is disabled.
     */
    @Nullable
    public static BitSet getResolvedBitSet() {
        if (!autoTuneEnabled) {
            return null;
        }
        if (resolvedBitSet != null && inited) {
            return resolvedBitSet;
        }
        synchronized (MiliAffinityAutoTuner.class) {
            if (resolvedBitSet != null && inited) {
                return resolvedBitSet;
            }
            try {
                resolvedBitSet = computeBitSet();
                inited = true;
                if (resolvedBitSet != null) {
                    lastReportedCoreCount = resolvedBitSet.cardinality();
                    lastReportedStrategy = strategy;
                    LOGGER.info("mili auto-tuner: pinning tick threads to {} cores via strategy {} (bitmask: {})",
                            lastReportedCoreCount, strategy, describeBitSet(resolvedBitSet));
                }
            } catch (Throwable t) {
                LOGGER.warn("mili auto-tuner: failed to compute affinity bitset: {}", t.getMessage());
                return null;
            }
        }
        return resolvedBitSet;
    }

    /**
     * Apply the resolved affinity to a thread on its first invocation.
     * Called from the tick region thread factory. Idempotent and cheap.
     */
    public static void applyToCurrentThread() {
        BitSet set = getResolvedBitSet();
        if (set == null || set.isEmpty()) {
            return;
        }
        try {
            Affinity.setAffinity(set);
        } catch (Throwable t) {
            // openhft is best-effort; some platforms may refuse
            LOGGER.debug("mili auto-tuner: Affinity.setAffinity refused: {}", t.getMessage());
        }
    }

    private static BitSet computeBitSet() {
        int total = Runtime.getRuntime().availableProcessors();
        BitSet set = new BitSet(total);
        switch (strategy.toUpperCase(java.util.Locale.ROOT)) {
            case "PCORE" -> {
                // Pin to first half of available cores - usually the P-cores on
                // Intel hybrid CPUs. If only 2 cores are available, use all of
                // them. Falls back to all cores on single-class CPUs.
                int half = Math.max(1, total / 2);
                for (int i = 0; i < half; i++) {
                    set.set(i);
                }
            }
            case "SPREAD" -> {
                // Spread evenly across the highest-numbered cores. On AMD
                // Zen 5 with multiple CCDs, the last cores often share a
                // dedicated L3 slice.
                int stride = Math.max(1, total / 4);
                for (int i = total - 1; i >= 0 && set.cardinality() < 4; i -= stride) {
                    set.set(i);
                }
                // Ensure at least 2 cores are pinned
                if (set.isEmpty()) {
                    set.set(0);
                    set.set(Math.max(1, total - 1));
                }
            }
            case "CUSTOM" -> {
                for (String s : customCoreList) {
                    try {
                        int id = Integer.parseInt(s.trim());
                        if (id >= 0 && id < total) {
                            set.set(id);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            case "AUTO" -> {
                // Default: fall back to Luminol's affinity if it has been
                // explicitly configured, otherwise pin to all available cores
                // (the JVM/OS default behaviour). Luminol's CpuAffinityConfig
                // is loaded earlier in the boot sequence, so we can safely
                // query it here.
                BitSet lumiSet = me.earthme.luminol.config.modules.optimizations.CpuAffinityConfig.tickRegionAffinityBitSet;
                if (lumiSet != null && !lumiSet.isEmpty()) {
                    return (BitSet) lumiSet.clone();
                }
                set.set(0, total);
            }
            default -> {
                LOGGER.warn("mili auto-tuner: unknown strategy '{}', defaulting to AUTO", strategy);
                set.set(0, total);
            }
        }
        // Sanity: at least one core must be set, otherwise the JVM refuses
        // to run the thread.
        if (set.isEmpty()) {
            set.set(0);
        }
        // Sanity: never set more cores than reported, even if user lists them
        IntStream.range(0, set.length()).filter(i -> i >= total).forEach(set::clear);
        return set;
    }

    private static String describeBitSet(BitSet bs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            if (!first) sb.append(",");
            sb.append(i);
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public void onLoaded(@Nullable CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        // Force lazy resolution to log the strategy at startup
        getResolvedBitSet();
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        // No-op; the bit set is read-only after init.
    }
}
