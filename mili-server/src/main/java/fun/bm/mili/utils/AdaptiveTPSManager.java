package fun.bm.mili.utils;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.rust.RustCow;
import fun.bm.mili.scheduler.RecurringTask;
import fun.bm.mili.scheduler.TickIntervalBus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 根据平均区域负载动态调整 tick 间隔。
 *
 * <p><b>写入路径已从直接赋值改为提案</b>：本类不再直接写 tick 间隔，
 * 而是经 {@link TickIntervalBus} 提案，由总线按权威等级裁决。这样即便同时启用了
 * {@code TickDurationGovernor}（权威更高），也不会出现两者每秒互相覆盖、
 * 生效值随机抖动的情况。</p>
 *
 * <p><b>线程模型</b>：原实现自建裸 Thread 并循环 sleep(1s)，且 {@code shutdown()} 从无调用者，
 * 线程随 JVM 泄漏。现已改为由线程治理层的周期任务驱动。</p>
 */
public class AdaptiveTPSManager {

    /** 本提案者的名称。 */
    private static final String PROPOSER = "adaptive-tps";

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicLong currentInterval = new AtomicLong(TickIntervalBus.DEFAULT_INTERVAL_NS);

    private static final long minIntervalNs = 20_000_000L;
    private static final long maxIntervalNs = 100_000_000L;
    private static final long baseIntervalNs = TickIntervalBus.DEFAULT_INTERVAL_NS;

    private static volatile RecurringTask.Handle task;

    private static final RustCow<Collection<RegionLoadMonitor.RegionLoadSnapshot>> snapshotCache =
            RustCow.owned(new ArrayList<>());

    public static void start() {
        if (!RegionBalancerConfig.enabled) return;
        if (running.getAndSet(true)) return;

        // Mili start - 改为治理层周期任务：自带异常隔离，且生命周期随 shutdown 收口
        task = RecurringTask.start(PROPOSER, 1_000L, AdaptiveTPSManager::tickOnce);
        // Mili end

        LogUtils.getClassLogger().info("AdaptiveTPSManager started");
    }

    /**
     * 单次采样与调节。由周期任务驱动，每秒一次。
     */
    private static void tickOnce() {
        if (!RegionBalancerConfig.enabled) return;

        double avgLoad = 0;
        int count = 0;
        for (RegionLoadMonitor.RegionLoadSnapshot snap : RegionLoadMonitor.getAllSnapshots()) {
            avgLoad += snap.loadFactor();
            count++;
        }

        if (count == 0) return;

        avgLoad /= count;

        long adjusted = (long) (baseIntervalNs * (1.0 + avgLoad * 0.5));
        adjusted = Math.max(minIntervalNs, Math.min(maxIntervalNs, adjusted));

        updateTickInterval(adjusted);

        LogUtils.getClassLogger().debug(
                "AdaptiveTPS: avgLoad={}%, interval={}ms",
                (int) (avgLoad * 100), adjusted / 1_000_000L);
    }

    /**
     * 向写入总线提出新的 tick 间隔。
     *
     * <p>注意这是<b>提案</b>而非命令：若存在权威更高的控制器（如 PI 调节器），
     * 该值会被压制，等待前者撤回后自动生效。</p>
     */
    private static void updateTickInterval(final long newInterval) {
        currentInterval.set(newInterval);
        TickIntervalBus.propose(PROPOSER, TickIntervalBus.Authority.ADAPTIVE_TPS, newInterval);
    }

    /**
     * 读取当前自适应间隔。
     */
    static long getCurrentInterval() {
        return currentInterval.get();
    }

    /**
     * 供外部调用者设置 tick 间隔。
     *
     * <p>同样只是提案，需与其他控制器竞争；这是刻意收口 —— 原先任何外部调用都能
     * 绕过互斥直接改写全局字段。</p>
     */
    public static void setTickInterval(final long newInterval) {
        currentInterval.set(newInterval);
        TickIntervalBus.propose(PROPOSER, TickIntervalBus.Authority.ADAPTIVE_TPS, newInterval);
    }

    public static void shutdown() {
        if (!running.getAndSet(false)) return;
        // Mili start - 撤回提案，把控制权交还给次级仲裁者（或基准值）
        TickIntervalBus.withdraw(PROPOSER);
        if (task != null) {
            task.cancel();
            task = null;
        }
        // Mili end
    }
}
