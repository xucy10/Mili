package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.SchedulerConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 受治理的周期任务。
 *
 * <p>它解决的是 JDK 原生定时执行的一个反直觉行为：
 * {@code scheduleAtFixedRate} 提交的任务一旦抛出未检查异常，JDK 会<b>静默取消后续所有执行</b>，
 * 且不会有任何日志。也就是说，某次内存抖动抛了个异常，内存巡检任务从此再也没跑过，
 * 而服主只会觉得"服务器跑久了会变卡"。这里把异常拦下来计数，连续失败到阈值才判定失活，
 * 并且每一步都留日志。</p>
 *
 * <p>顺带提供两个观测面：单次执行耗时（超过阈值告警）与累计失败数。
 * 一个任务该多久跑一次是业务决策，是否要在它跑不动时出声报警，则是本类的职责。</p>
 */
public final class RecurringTask {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Handle> REGISTRY = new ConcurrentHashMap<>();

    private RecurringTask() {
    }

    /**
     * 任务体。允许抛出受检查异常，由框架统一处理。
     */
    @FunctionalInterface
    public interface Body {
        void run() throws Exception;
    }

    /**
     * 触发语义。
     *
     * <p>差别只在长任务上显现，但那一处差别往往是事故现场：</p>
     * <ul>
     *   <li>{@link #FIXED_RATE}：按绝对频率触发。任务跑超时后，JDK 会连续补触发以"追赶"进度。</li>
     *   <li>{@link #FIXED_DELAY}：上次<b>完成后</b>再等待一个周期。两次执行之间至少间隔一个周期。</li>
     * </ul>
     * 备份、压缩、全量扫描这类耗时任务必须用 {@link #FIXED_DELAY}，
     * 否则一次卡顿会换来一串背靠背的重任务。
     */
    public enum Policy {
        FIXED_RATE,
        FIXED_DELAY
    }

    /**
     * 启动一个周期任务。
     *
     * @param name    全局唯一名称，用于观测与去重
     * @param periodMs 执行周期（毫秒），必须为正
     * @param body    任务体
     * @return 句柄
     */
    public static Handle start(@NotNull final String name, final long periodMs, @NotNull final Body body) {
        return start(name, periodMs, periodMs, body);
    }

    /**
     * 以 {@link Policy#FIXED_DELAY} 语义启动周期任务。
     *
     * <p>备份、压缩、全量扫描这类耗时任务应当用这个方法：它保证两次执行之间至少间隔
     * 一个周期，而不会在长任务结束后连续补触发。</p>
     *
     * @param name     全局唯一名称
     * @param periodMs 执行周期（毫秒）
     * @param body     任务体
     * @return 句柄
     */
    public static Handle startWithFixedDelay(@NotNull final String name,
                                             final long periodMs,
                                             @NotNull final Body body) {
        return start(name, periodMs, periodMs, Policy.FIXED_DELAY, body);
    }

    /**
     * 启动一个周期任务，可指定首次延迟。默认使用 {@link Policy#FIXED_RATE}。
     *
     * @param name           全局唯一名称
     * @param initialDelayMs 首次延迟（毫秒）
     * @param periodMs       执行周期（毫秒）
     * @param body           任务体
     * @return 句柄；若同名任务已存在则返回既有句柄
     */
    public static Handle start(@NotNull final String name,
                               final long initialDelayMs,
                               final long periodMs,
                               @NotNull final Body body) {
        return start(name, initialDelayMs, periodMs, Policy.FIXED_RATE, body);
    }

    /**
     * 启动一个周期任务，完整参数形式。
     *
     * @param name           全局唯一名称
     * @param initialDelayMs 首次延迟（毫秒）
     * @param periodMs       执行周期（毫秒），必须为正
     * @param policy         触发语义
     * @param body           任务体
     * @return 句柄；若同名任务已存在则返回既有句柄
     */
    public static Handle start(@NotNull final String name,
                               final long initialDelayMs,
                               final long periodMs,
                               @NotNull final Policy policy,
                               @NotNull final Body body) {
        return startOn(MiliScheduler.scheduled(), name, initialDelayMs, periodMs, policy, body);
    }

    /**
     * 在<b>指定</b>的定时池上启动周期任务，默认 {@link Policy#FIXED_RATE}。
     *
     * <p>少数组件确实需要独占线程（50ms 级高频、或需要特定优先级），此时不宜塞进共享池；
     * 但它们同样不该失去异常隔离。这个方法让"独占"与"受治理"不再互相排斥。</p>
     *
     * @param executor 承载该任务的定时池
     * @param name     全局唯一名称
     * @param periodMs 执行周期（毫秒）
     * @param body     任务体
     * @return 句柄
     */
    public static Handle startOn(@NotNull final ScheduledExecutorService executor,
                                 @NotNull final String name,
                                 final long periodMs,
                                 @NotNull final Body body) {
        return startOn(executor, name, periodMs, periodMs, Policy.FIXED_RATE, body);
    }

    /**
     * 在指定定时池上启动周期任务，可指定首次延迟。默认使用 {@link Policy#FIXED_RATE}。
     *
     * <p>与 {@link #start(String, long, long, Body)} 对称：需要独占池的组件往往同样需要
     * "首次延迟与周期不同"（先让世界加载完再开始巡检、或错开启动瞬间的峰值）。
     * 少了这一档，调用方只能去补一个 {@code Policy} 实参，或者——更糟——
     * 误以为存在该重载而写出编译不过的代码。</p>
     *
     * @param executor      承载该任务的定时池
     * @param name          全局唯一名称
     * @param initialDelayMs 首次延迟（毫秒）
     * @param periodMs      执行周期（毫秒），必须为正
     * @param body          任务体
     * @return 句柄；若同名任务已存在则返回既有句柄
     */
    public static Handle startOn(@NotNull final ScheduledExecutorService executor,
                                 @NotNull final String name,
                                 final long initialDelayMs,
                                 final long periodMs,
                                 @NotNull final Body body) {
        return startOn(executor, name, initialDelayMs, periodMs, Policy.FIXED_RATE, body);
    }

    /**
     * 在指定定时池上启动周期任务，完整参数形式。
     *
     * @param executor      承载该任务的定时池
     * @param name          全局唯一名称
     * @param initialDelayMs 首次延迟（毫秒）
     * @param periodMs      执行周期（毫秒），必须为正
     * @param policy        触发语义
     * @param body          任务体
     * @return 句柄；若同名任务已存在则返回既有句柄
     */
    public static Handle startOn(@NotNull final ScheduledExecutorService executor,
                                 @NotNull final String name,
                                 final long initialDelayMs,
                                 final long periodMs,
                                 @NotNull final Policy policy,
                                 @NotNull final Body body) {
        if (periodMs <= 0L) {
            throw new IllegalArgumentException("periodMs must be positive, got " + periodMs);
        }
        final Handle created = new Handle(name, periodMs, body);
        final Handle existing = REGISTRY.putIfAbsent(name, created);
        if (existing != null) {
            LOGGER.warn("[Mili] Recurring task '{}' already registered; reusing the existing handle", name);
            return existing;
        }
        try {
            final long delay = Math.max(0L, initialDelayMs);
            switch (policy) {
                case FIXED_DELAY -> created.attach(executor.scheduleWithFixedDelay(
                        created::tick, delay, periodMs, TimeUnit.MILLISECONDS));
                case FIXED_RATE -> created.attach(executor.scheduleAtFixedRate(
                        created::tick, delay, periodMs, TimeUnit.MILLISECONDS));
            }
        } catch (RejectedExecutionException ex) {
            // 池已关闭时（多为停机过程中）不应该偷偷再拉起线程，直接判定为失活。
            created.disable();
            REGISTRY.remove(name, created);
            LOGGER.error("[Mili] Recurring task '{}' was rejected; scheduler is likely shutting down", name, ex);
        }
        return created;
    }

    /**
     * 取消指定任务。
     *
     * @param name 任务名
     * @return true 表示确有该任务并被取消
     */
    public static boolean cancel(final String name) {
        final Handle handle = REGISTRY.remove(name);
        if (handle == null) {
            return false;
        }
        handle.cancel();
        return true;
    }

    /**
     * 取消全部任务。用于重载或停机场景。
     */
    public static void cancelAll() {
        for (Handle handle : REGISTRY.values()) {
            handle.cancel();
        }
        REGISTRY.clear();
    }

    /**
     * 全部任务句柄，按名称排序。
     *
     * @return 句柄快照
     */
    public static List<Handle> all() {
        final List<Handle> out = new ArrayList<>(REGISTRY.values());
        out.sort(Comparator.comparing(Handle::name));
        return out;
    }

    /**
     * 任务统计量。
     *
     * @param runs                成功执行次数
     * @param failures            累计失败次数
     * @param consecutiveFailures 当前连续失败次数
     * @param lastDurationMs      上次执行耗时
     * @param maxDurationMs       历史最大耗时
     * @param cancelled           是否已被停用
     */
    public record Stats(long runs, long failures, long consecutiveFailures,
                        long lastDurationMs, long maxDurationMs, boolean cancelled) {
    }

    /**
     * 周期任务句柄：用于取消与采样。
     */
    public static final class Handle {
        private final String name;
        private final long periodMs;
        private final Body body;
        private final AtomicLong runs = new AtomicLong(0);
        private final AtomicLong failures = new AtomicLong(0);
        private final AtomicLong consecutiveFailures = new AtomicLong(0);
        private final AtomicLong lastDurationMs = new AtomicLong(0);
        private final AtomicLong maxDurationMs = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> future;

        private Handle(final String name, final long periodMs, final Body body) {
            this.name = name;
            this.periodMs = periodMs;
            this.body = body;
        }

        /**
         * @return 任务名
         */
        public String name() {
            return this.name;
        }

        /**
         * @return 执行周期（毫秒）
         */
        public long periodMs() {
            return this.periodMs;
        }

        /**
         * @return 是否已被停用（主动取消或连续失败达阈值）
         */
        public boolean isDisabled() {
            return this.cancelled.get();
        }

        /**
         * 取消该任务，后续不再执行。
         */
        public void cancel() {
            if (!this.cancelled.compareAndSet(false, true)) {
                return;
            }
            final ScheduledFuture<?> current = this.future;
            if (current != null) {
                current.cancel(false);
            }
            REGISTRY.remove(this.name, this);
        }

        /**
         * @return 当前统计量
         */
        public Stats stats() {
            return new Stats(this.runs.get(), this.failures.get(), this.consecutiveFailures.get(),
                    this.lastDurationMs.get(), this.maxDurationMs.get(), this.cancelled.get());
        }

        private void attach(final ScheduledFuture<?> scheduledFuture) {
            this.future = scheduledFuture;
        }

        private void disable() {
            this.cancelled.set(true);
        }

        private void tick() {
            if (this.cancelled.get()) {
                return;
            }
            final long begin = System.nanoTime();
            try {
                this.body.run();
                this.runs.incrementAndGet();
                this.consecutiveFailures.set(0L);
            } catch (Throwable ex) {
                // 刻意连 Error 一起捕获：一次偶发的 StackOverflowError 不该让备份、
                // 巡检这类任务永久停摆。是否该停由连续失败阈值决定，而不是某一次异常的类型。
                final long total = this.failures.incrementAndGet();
                final long consecutive = this.consecutiveFailures.incrementAndGet();
                final long limit = Math.max(1L, SchedulerConfig.maxConsecutiveFailures);
                LOGGER.error("[Mili] Recurring task '{}' failed ({}/{} consecutive, {} total)",
                        this.name, consecutive, limit, total, ex);
                if (consecutive >= limit) {
                    this.cancel();
                    LOGGER.error("[Mili] Recurring task '{}' disabled after {} consecutive failures",
                            this.name, consecutive);
                }
            } finally {
                final long millis = (System.nanoTime() - begin) / 1_000_000L;
                this.lastDurationMs.set(millis);
                this.raiseMax(millis);
                final long slowThreshold = SchedulerConfig.slowTaskWarnMs;
                if (slowThreshold > 0L && millis > slowThreshold) {
                    LOGGER.warn("[Mili] Recurring task '{}' took {}ms (warn threshold {}ms)",
                            this.name, millis, slowThreshold);
                }
            }
        }

        private void raiseMax(final long candidate) {
            long current = this.maxDurationMs.get();
            while (candidate > current) {
                if (this.maxDurationMs.compareAndSet(current, candidate)) {
                    return;
                }
                current = this.maxDurationMs.get();
            }
        }
    }
}
