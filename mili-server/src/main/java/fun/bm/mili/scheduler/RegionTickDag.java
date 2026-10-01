package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 跨 region 的 tick 依赖图与波次屏障。
 *
 * <p><b>为什么是"跨 region"而不是"region 内子步骤"</b>：单 region 的 tick 子阶段
 * （实体、方块实体、流体）共享同一份可变世界状态，本来就不能并行；而且它们必须跑在
 * TickThreadRunner 上，把子步骤拆到别的线程会破坏线程身份。跨 region 则不然 ——
 * 每个 region 仍然在自己的 tick 线程上完整执行，我们只在<b>波次之间</b>加屏障，
 * 表达"A region 的 tick 结果会影响 B"这种真实依赖（典型即跨区红石）。</p>
 *
 * <p><b>屏障语义采用「抗卡死」</b>（这是明确的选择，而非默认）：单个 region 卡住时，
 * 已就绪的继续推进下一波，掉队者被标记后不再阻塞下游，绝不让一个慢节点拖垮全场。
 * 与之相对的「严格时序」方案（全场等待到超时后放弃本轮）会让一个卡死的 region
 * 拖慢所有 region，在高负载下恰恰是最糟的行为。</p>
 *
 * <p>掉队者不会永久掉队：它一旦完成一次 tick，标记即清除、重新回归正常参与。
 * 连续掉队达到阈值则告警，便于定位"哪个 region 在拖后腿"。</p>
 *
 * <p><b>零生产影响</b>：本类只做仲裁查询，不接管 tick 执行。没有任何调用者声明依赖时，
 * 所有 region 恒为 {@link Readiness#READY}，行为与现状完全一致。</p>
 */
public final class RegionTickDag {

    /** 就绪性判定结果。 */
    public enum Readiness {
        /** 可以立即 tick。 */
        READY,
        /** 上游依赖尚未在本波完成，建议让位。 */
        WAIT_DEPENDENCY
    }

    /** 配置。由 {@code RegionBalancer.syncDagAndGovernorConfig()} 注入。 */
    public static final class Config {
        /** 波次屏障的超时（毫秒）。超时即强制推进，标记掉队者。 */
        public static long WAVE_TIMEOUT_MS = 100L;
        /** 连续掉队多少次后打印告警。 */
        public static int MAX_LAG_STREAK = 3;
        /** 登记节点上限，防止 region 卸载后节点无界增长。 */
        public static int MAX_NODES = 4096;

        private Config() {
        }
    }

    /** 统计快照。 */
    public record DagStats(long waveIndex, int nodes, int ready, int waiting,
                           int lagging, long waveAgeMillis) {
    }

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
    private static volatile RecurringTask.Handle advanceTask;

    /** region 标识 -> 节点。key 由调用方提供，通常是 region 数据对象。 */
    private static final Map<Object, Node> NODES = new ConcurrentHashMap<>();

    /** 波次推进锁。推进涉及重置所有节点，必须串行。 */
    private static final Object WAVE_LOCK = new Object();

    private static final AtomicLong waveIndex = new AtomicLong(0L);
    private static volatile long waveStartedAtNanos = System.nanoTime();

    private RegionTickDag() {
    }

    /**
     * 是否启用。未启用时 {@link #readiness} 恒返回 {@link Readiness#READY}。
     */
    public static boolean isEnabled() {
        return ENABLED.get();
    }

    public static void setEnabled(final boolean enabled) {
        ENABLED.set(enabled);
    }

    /**
     * 启动兜底推进任务。
     *
     * <p>为什么需要它：波次推进平时由 {@link #markTicked} 触发。但若某个波次里所有
     * region 都因依赖未满足而被压在队尾，就不会有任何 markTicked 发生，
     * 掉队标记永远不会产生，下游会一直等下去。这个周期任务保证超时一定会被兑现。</p>
     */
    public static void init() {
        if (!ENABLED.get() || advanceTask != null) {
            return;
        }
        advanceTask = RecurringTask.start("region-dag-advance",
                Math.max(1L, Config.WAVE_TIMEOUT_MS), RegionTickDag::advanceIfDue);
    }

    public static void shutdown() {
        if (advanceTask != null) {
            advanceTask.cancel();
            advanceTask = null;
        }
        clearAll();
    }

    // ---------- 依赖图 ----------

    /**
     * 声明一条依赖边：{@code downstream} 的 tick 依赖 {@code upstream} 的本波 tick 结果。
     *
     * <p>幂等，重复声明无害。未声明依赖的 region 永远 {@link Readiness#READY}，
     * 这使得本类可以在依赖来源（如跨区红石机制）尚未接线时安全存在。</p>
     */
    public static void declareDependency(@NotNull final Object upstream,
                                         @NotNull final Object downstream) {
        if (upstream == downstream) {
            return;
        }
        node(upstream).downstreams.add(downstream);
        node(downstream).upstreams.add(upstream);
    }

    /**
     * 移除一个 region 的全部依赖边。region 卸载时调用，避免节点与边泄漏。
     */
    public static void clear(@NotNull final Object region) {
        final Node removed = NODES.remove(region);
        if (removed == null) {
            return;
        }
        for (Object upstream : removed.upstreams) {
            final Node up = NODES.get(upstream);
            if (up != null) {
                up.downstreams.remove(region);
            }
        }
        for (Object downstream : removed.downstreams) {
            final Node down = NODES.get(downstream);
            if (down != null) {
                down.upstreams.remove(region);
            }
        }
    }

    /**
     * 清空整张图。用于重载或停机。
     */
    public static void clearAll() {
        NODES.clear();
    }

    // ---------- 仲裁 ----------

    /**
     * 查询某 region 当前是否可以 tick。
     *
     * <p><b>抗卡死的关键在这一行</b>：上游若处于掉队状态，其"未完成"被<b>忽略</b>，
     * 下游照常放行。否则一个卡死的 region 会沿着依赖链把整场都冻住。</p>
     */
    public static Readiness readiness(@NotNull final Object region) {
        if (!ENABLED.get()) {
            return Readiness.READY;
        }
        final Node node = NODES.get(region);
        if (node == null) {
            return Readiness.READY;
        }
        for (Object upstream : node.upstreams) {
            final Node up = NODES.get(upstream);
            if (up == null || up.completed || up.lagging) {
                continue;
            }
            return Readiness.WAIT_DEPENDENCY;
        }
        return Readiness.READY;
    }

    /**
     * 标记某 region 已完成本波 tick。由 tick 调度路径在 region tick 结束后调用。
     */
    public static void markTicked(@NotNull final Object region) {
        if (!ENABLED.get()) {
            return;
        }
        final Node node = NODES.get(region);
        if (node != null) {
            node.completed = true;
            // 掉队者一旦追上就立刻回归正常参与，不做惩罚性隔离
            node.lagging = false;
            node.lagStreak = 0;
            node.lastTickedNanos = System.nanoTime();
        }
        maybeAdvance();
    }

    /**
     * 兜底推进：即使没有 region 在 tick，也要让超时波次得以推进。
     * 由线程治理层的周期任务驱动。
     */
    public static void advanceIfDue() {
        if (!ENABLED.get()) {
            return;
        }
        maybeAdvance();
    }

    private static void maybeAdvance() {
        final long now = System.nanoTime();
        synchronized (WAVE_LOCK) {
            boolean allDone = true;
            for (Node node : NODES.values()) {
                if (!node.completed && !node.lagging) {
                    allDone = false;
                    break;
                }
            }
            final boolean timedOut = now - waveStartedAtNanos > Config.WAVE_TIMEOUT_MS * 1_000_000L;
            if (allDone || timedOut) {
                beginWave(now, !allDone);
            }
        }
    }

    /**
     * 开启下一波。
     *
     * @param forced true 表示由超时强制推进（本波有节点未赶上）
     */
    private static void beginWave(final long now, final boolean forced) {
        for (Node node : NODES.values()) {
            if (!node.completed) {
                node.lagStreak++;
                if (node.lagStreak >= Config.MAX_LAG_STREAK && !node.lagging) {
                    node.lagging = true;
                    LOGGER.warn("[Mili] Region {} lagged {} consecutive waves; "
                                    + "it no longer gates its downstream, but is still ticked",
                            describe(node), node.lagStreak);
                }
            }
            node.completed = false;
        }
        waveIndex.incrementAndGet();
        waveStartedAtNanos = now;
        if (forced && NODES.size() > Config.MAX_NODES) {
            pruneExcess();
        }
    }

    /** 节点数超限时，清理最久未 tick 的节点，防止 region 卸载后无限增长。 */
    private static void pruneExcess() {
        final List<Map.Entry<Object, Node>> entries = new ArrayList<>(NODES.entrySet());
        entries.sort((a, b) -> Long.compare(a.getValue().lastTickedNanos, b.getValue().lastTickedNanos));
        final int excess = entries.size() - Config.MAX_NODES;
        for (int i = 0; i < excess; i++) {
            clear(entries.get(i).getKey());
        }
        if (excess > 0) {
            LOGGER.warn("[Mili] RegionTickDag pruned {} stale node(s)", excess);
        }
    }

    private static Node node(final Object region) {
        return NODES.computeIfAbsent(region, key -> new Node());
    }

    private static Object describe(final Node node) {
        return node.upstreams.isEmpty() && node.downstreams.isEmpty()
                ? "node"
                : "node(up=" + node.upstreams.size() + ",down=" + node.downstreams.size() + ")";
    }

    // ---------- 观测 ----------

    public static DagStats stats() {
        int ready = 0;
        int waiting = 0;
        int lagging = 0;
        for (Map.Entry<Object, Node> entry : NODES.entrySet()) {
            final Node node = entry.getValue();
            if (node.lagging) {
                lagging++;
            } else if (readiness(entry.getKey()) == Readiness.READY) {
                ready++;
            } else {
                waiting++;
            }
        }
        final long ageMillis = (System.nanoTime() - waveStartedAtNanos) / 1_000_000L;
        return new DagStats(waveIndex.get(), NODES.size(), ready, waiting, lagging, ageMillis);
    }

    /** 节点。所有字段均已考虑跨线程可见性。 */
    private static final class Node {
        final Set<Object> upstreams = ConcurrentHashMap.newKeySet();
        final Set<Object> downstreams = ConcurrentHashMap.newKeySet();
        volatile boolean completed;
        volatile boolean lagging;
        volatile int lagStreak;
        volatile long lastTickedNanos = System.nanoTime();
    }
}
