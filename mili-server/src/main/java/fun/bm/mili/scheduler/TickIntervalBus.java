package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * tick 间隔写入总线 —— {@code TickRegionScheduler.TIME_BETWEEN_TICKS} 的<b>唯一</b>写入者。
 *
 * <p>这是 tick 仲裁层的第一个组件。它<b>不做决策，只做仲裁</b>：各家控制器保留自己的算法，
 * 只是把"我想让 tick 间隔变成 X"改成向总线提案，由总线按权威等级裁决后统一写入。</p>
 *
 * <p>为什么要存在这个类：改造前有四个地方在写同一个全局字段
 * （{@code AdaptiveTPSManager} 两处、{@code TickDurationGovernor} 两处），
 * 互斥关系靠 {@code RegionBalancer} 里一段被复制了两次的 if 分支维持，
 * 而 {@code AdaptiveTPSManager.setTickInterval} 这个 public 方法更是任何外部调用者都能绕过互斥。
 * 多写者每秒互相覆盖，实际生效值会在两者之间随机抖动。</p>
 *
 * <p><b>冲突消解规则</b>（刻意保持简单、可预测）：</p>
 * <ol>
 *   <li>权威等级高者生效；</li>
 *   <li>同级时最近提出者生效；</li>
 *   <li>所有提案撤回后，恢复到基准值 50ms（20 TPS）；</li>
 *   <li>从未有人提案时不写入，保持 Folia 原生行为 —— 符合"默认行为 = Paper/Folia"公理。</li>
 * </ol>
 */
public final class TickIntervalBus {

    /** Folia 的基准 tick 间隔：50ms = 20 TPS。 */
    public static final long DEFAULT_INTERVAL_NS = 50_000_000L;

    private static final Logger LOGGER = LogUtils.getClassLogger();

    /**
     * 提案者的权威等级。数值越大越权威，冲突时优先采用。
     *
     * <p>之所以让 PI 控制器高于自适应 TPS：后者只看平均负载做线性外推，
     * 前者带有 cpu 预算、队列深度、worker 利用率三道硬上限，能自证"不会越追赶越卡"。</p>
     */
    public enum Authority {
        /** 兜底/观测性质的提案，几乎不与其他出让。 */
        FALLBACK(10),
        /** 平均负载线性调节。 */
        ADAPTIVE_TPS(50),
        /** PI 控制器 + 追赶上限。 */
        GOVERNOR(90);

        /** 数值权重，越大越优先。 */
        public final int weight;

        Authority(final int weight) {
            this.weight = weight;
        }
    }

    /** 一次提案。不可变，便于在锁外组装。 */
    private record Proposal(String name, int weight, long intervalNs, long proposedAtNanos) {
    }

    /** 总线当前的裁决结果。 */
    public record Decision(String activeName, long activeIntervalNs, int proposalCount) {
    }

    private static final Map<String, Proposal> PROPOSALS = new ConcurrentHashMap<>();

    /**
     * 写入锁。{@code TIME_BETWEEN_TICKS} 本身是 volatile，单次写已原子；
     * 这里加锁是为了让"比较上次值 + 写入 + 更新生效者"成为一个不可分割的步骤，
     * 避免两个提案者交错写入导致末值不属于当前生效者。
     */
    private static final Object WRITE_LOCK = new Object();

    private static volatile String activeName;
    private static volatile long activeIntervalNs;
    private static volatile boolean everProposed;

    private TickIntervalBus() {
    }

    /**
     * 提出或更新一个 tick 间隔提案。
     *
     * <p>调用后立即仲裁：若提案方正是当前生效者且目标值有变化，则写入；
     * 若被更高权威压制，则仅记录，等待前者撤回。</p>
     *
     * @param name      提案者名称，全局唯一；重复调用视为更新
     * @param authority 权威等级
     * @param intervalNs 期望的 tick 间隔（纳秒），须为正
     */
    public static void propose(@NotNull final String name,
                              @NotNull final Authority authority,
                              final long intervalNs) {
        if (intervalNs <= 0L) {
            throw new IllegalArgumentException("intervalNs must be positive, got " + intervalNs);
        }
        PROPOSALS.put(name, new Proposal(name, authority.weight, intervalNs, System.nanoTime()));
        apply();
    }

    /**
     * 撤回提案。组件停止控制时调用，此后 Bus 会把控制权交给次高权威的提案者，
     * 若已无提案者则恢复基准值 —— 这保证了不会有组件停机后把 tick 间隔留在某个异常值上。
     *
     * @param name 提案者名称
     */
    public static void withdraw(@NotNull final String name) {
        if (PROPOSALS.remove(name) != null) {
            apply();
        }
    }

    /**
     * 当前裁决结果与提案概况。
     *
     * @return 裁决快照；{@code activeName} 为 {@code null} 表示无人控制
     */
    public static Decision decide() {
        return new Decision(activeName, activeIntervalNs, PROPOSALS.size());
    }

    /**
     * 全部提案者概览，按权威降序、提出时间倒序排列，便于观测"谁在压着谁"。
     *
     * @return 描述列表
     */
    public static List<String> proposals() {
        final List<Proposal> all = new ArrayList<>(PROPOSALS.values());
        all.sort(Comparator
                .comparingInt(Proposal::weight).reversed()
                .thenComparing(Comparator.comparingLong(Proposal::proposedAtNanos).reversed()));
        final List<String> out = new ArrayList<>(all.size());
        for (Proposal proposal : all) {
            out.add(proposal.name() + " w=" + proposal.weight()
                    + " -> " + (proposal.intervalNs() / 1_000_000L) + "ms"
                    + (proposal.name().equals(activeName) ? " [active]" : ""));
        }
        return Collections.unmodifiableList(out);
    }

    private static void apply() {
        synchronized (WRITE_LOCK) {
            final Proposal winner = pickWinner();
            if (winner == null) {
                if (everProposed) {
                    // 曾有控制者，现已全部退出：不能把最后一个非默认值留在那里。
                    writeIfChanged(DEFAULT_INTERVAL_NS);
                    noteSwitch(null, DEFAULT_INTERVAL_NS);
                }
                return;
            }
            everProposed = true;
            writeIfChanged(winner.intervalNs());
            noteSwitch(winner.name(), winner.intervalNs());
        }
    }

    private static Proposal pickWinner() {
        Proposal best = null;
        for (Proposal candidate : PROPOSALS.values()) {
            if (best == null
                    || candidate.weight() > best.weight()
                    || (candidate.weight() == best.weight()
                        && candidate.proposedAtNanos() > best.proposedAtNanos())) {
                best = candidate;
            }
        }
        return best;
    }

    private static void writeIfChanged(final long intervalNs) {
        if (activeIntervalNs == intervalNs) {
            return;
        }
        TickRegionScheduler.TIME_BETWEEN_TICKS = intervalNs;
        activeIntervalNs = intervalNs;
    }

    private static void noteSwitch(final String name, final long intervalNs) {
        if (name == null || !name.equals(activeName)) {
            if (activeName != null || name != null) {
                LOGGER.info("[Mili] Tick interval control: {} -> {} ({}ms)",
                        activeName, name, intervalNs / 1_000_000L);
            }
            activeName = name;
        }
    }
}
