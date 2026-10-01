package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.SchedulerConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mili 线程治理中枢。
 *
 * <p>职责边界：<b>不管调度算法，只管线程资源</b>。谁先 tick、tick 间隔多少、
 * 是否追赶这些"决策"属于 tick 仲裁层；这里负责的是：任何 Mili 组件想拿线程，
 * 都从这里出，接受统一的命名、预算、异常兜底、生命周期和观测。</p>
 *
 * <p>三条设计取向：</p>
 * <ol>
 *   <li><b>不碰 tick 线程身份</b>（见 {@link Tier#TICK}）——那是一条碰了必炸的硬约束，
 *       本层对该赛道只做登记与观测，绝不代创。</li>
 *   <li><b>一律 fail-open</b>：治理层关闭或超预算时降级为"照旧干活 + 告警"，
 *       绝不因为簿记问题把功能弄丢。这与项目"一切能力可降级"的公理一致。</li>
 *   <li><b>纳管不等于改写</b>：{@link #adopt} 只接手生命周期所有权，不替换池实现，
 *       使得迁移可以逐个组件进行、随时回滚。</li>
 * </ol>
 */
public final class MiliScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    /** 由本中枢创建过的线程数。只增不减，shutdown 时归零，用于预算告警。 */
    private static final AtomicInteger ALLOCATED = new AtomicInteger(0);

    private static final Map<Tier, ExecutorService> SHARED = new ConcurrentHashMap<>();
    private static final List<Managed> MANAGED = new CopyOnWriteArrayList<>();

    /** 治理层关闭时的兜底定时池，保证 {@link #scheduled()} 永不返回 null。 */
    private static volatile ScheduledExecutorService fallbackScheduled;

    private MiliScheduler() {
    }

    // ---------- 生命周期 ----------

    /**
     * 治理层是否处于可用状态。
     *
     * @return true 表示共享池可被安全使用
     */
    public static boolean isEnabled() {
        return SchedulerConfig.enabled && INITIALIZED.get() && !SHUTDOWN.get();
    }

    /**
     * 初始化治理层，预热各共享池。
     *
     * <p>必须在所有子系统之前调用，否则它们会各自开一些小池。</p>
     */
    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        SHUTDOWN.set(false);
        if (!SchedulerConfig.enabled) {
            LOGGER.info("[Mili] Scheduler governance disabled via config; components keep their own threads");
            return;
        }
        ensureShared(Tier.CPU);
        ensureShared(Tier.BLOCKING_IO);
        ensureShared(Tier.BACKGROUND);
        LOGGER.info("[Mili] Scheduler initialized (cpu={}, io={}, bg={}, threadBudget={})",
                SchedulerConfig.resolveCpuThreads(),
                SchedulerConfig.resolveIoThreads(),
                SchedulerConfig.resolveBackgroundThreads(),
                SchedulerConfig.maxManagedThreads);
    }

    /**
     * 关闭所有受管线程池。
     *
     * <p>顺序上是"先停收再存量"：共享池收口在前（它们接收新任务最频繁），
     * 纳管的具名池在后。每步都有超时，超时则强制 {@code shutdownNow} 并记录丢弃量，
     * 保证关闭过程本身不会挂死。</p>
     */
    public static void shutdown() {
        if (!INITIALIZED.compareAndSet(true, false)) {
            return;
        }
        SHUTDOWN.set(true);
        final long timeout = SchedulerConfig.shutdownTimeoutMs;

        for (Map.Entry<Tier, ExecutorService> entry : SHARED.entrySet()) {
            shutdownQuietly("shared:" + entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue(), timeout);
        }
        SHARED.clear();

        for (Managed managed : MANAGED) {
            shutdownQuietly(managed.name(), managed.service(), timeout);
        }
        MANAGED.clear();

        final ScheduledExecutorService fallback = fallbackScheduled;
        if (fallback != null) {
            fallbackScheduled = null;
            shutdownQuietly("fallback", fallback, timeout);
        }

        ALLOCATED.set(0);
        LOGGER.info("[Mili] Scheduler shutdown complete");
    }

    // ---------- 取池 ----------

    /**
     * 取指定层的共享池。
     *
     * @param tier 目标层，不可为 {@link Tier#TICK} 或 {@link Tier#VIRTUAL}
     * @return 共享池；治理层关闭时返回兜底池（fail-open）
     */
    public static ExecutorService pool(@NotNull final Tier tier) {
        if (tier == Tier.TICK) {
            throw new IllegalArgumentException("Tier.TICK is not allocatable; see Tier javadoc");
        }
        if (tier == Tier.VIRTUAL) {
            throw new IllegalArgumentException("Tier.VIRTUAL has no shared pool; use virtualExecutor(name, limit)");
        }
        // 刻意不检查 INITIALIZED：组件的静态初始化块可能早于 init() 执行
        // （典型如 AsyncKeepaliveManager），此时仍应惰性拉起共享池而不是
        // 退化到私有兜底池，否则线程收敛就形同虚设。
        if (!SchedulerConfig.enabled || SHUTDOWN.get()) {
            return unmanagedFallback();
        }
        return ensureShared(tier);
    }

    /**
     * 取共享定时池，供低频巡检与 {@link RecurringTask} 使用。
     *
     * <p>同样惰性：谁先用到谁触发创建，创建结果登记在册，最终由 {@link #shutdown()} 收口。</p>
     *
     * @return 共享定时池，永不为空
     */
    public static ScheduledExecutorService scheduled() {
        if (!SchedulerConfig.enabled || SHUTDOWN.get()) {
            return unmanagedFallback();
        }
        ensureShared(Tier.BACKGROUND);
        final ExecutorService shared = SHARED.get(Tier.BACKGROUND);
        return shared instanceof ScheduledExecutorService scheduled
                ? scheduled
                : unmanagedFallback();
    }

    /**
     * 创建一个受生命周期托管的具名线程池。
     *
     * <p>适用于确实需要独占线程语义的组件（例如必须串行执行的状态机）。
     * 纯粹"想要后台跑个活"的场合请用 {@link #pool}。</p>
     *
     * @param name   池名，同时作为线程名前缀与遥测键
     * @param tier   所属层
     * @param threads 线程数
     * @return 已纳管的池
     */
    public static ExecutorService namedPool(@NotNull final String name,
                                            @NotNull final Tier tier,
                                            final int threads) {
        if (!tier.isAllocatable()) {
            throw new IllegalArgumentException("Tier not allocatable: " + tier);
        }
        if (tier == Tier.VIRTUAL) {
            throw new IllegalArgumentException("Tier.VIRTUAL must be obtained via virtualExecutor(name, limit)");
        }
        final ExecutorService created = tier == Tier.BACKGROUND
                ? Executors.newScheduledThreadPool(Math.max(1, threads), factory(name))
                : boundedPool(name, threads);
        MANAGED.add(new Managed(name, tier, created));
        return created;
    }

    /**
     * 创建一个受托管的具名<b>定时</b>池。
     *
     * <p>适用场合：周期明显短于 BACKGROUND 层约定、或单次执行偏重的组件
     * （典型如 50ms 一次的区块处理队列）。这类任务若塞进共享定时池，会长期占满那
     * 一个线程，把其他巡检任务挤到饿死；但也不该放任它自建线程、脱离关闭流程。</p>
     *
     * <p>所以这里的取舍是：<b>保留独占线程以保证隔离性，同时交出生命周期与命名权</b>。
     * "一律共享"并不是治理的目的，"可控"才是。</p>
     *
     * @param name    池名
     * @param threads 线程数
     * @return 已纳管的定时池
     */
    public static ScheduledExecutorService namedScheduledPool(@NotNull final String name, final int threads) {
        return namedScheduledPool(name, threads, Thread.NORM_PRIORITY);
    }

    /**
     * 同 {@link #namedScheduledPool(String, int)}，但可显式指定线程优先级。
     *
     * @param name     池名
     * @param threads  线程数
     * @param priority 线程优先级
     * @return 已纳管的定时池
     */
    public static ScheduledExecutorService namedScheduledPool(@NotNull final String name,
                                                              final int threads,
                                                              final int priority) {
        final ScheduledExecutorService created = Executors.newScheduledThreadPool(
                Math.max(1, threads), factory(name, priority));
        MANAGED.add(new Managed(name, Tier.BACKGROUND, created));
        return created;
    }

    /**
     * 纳管一个已经存在的线程池。
     *
     * <p>语义是<b>所有权移交</b>：调用方从此不应再调用它的 {@code shutdown}，
     * 关闭职责由 {@link #shutdown()} 统一承担。这样每迁移一个组件就少一处
     * 漏关的线程。</p>
     *
     * @param name    登记名，用于观测与审计
     * @param tier    所属层
     * @param service 被纳管的池
     */
    public static void adopt(@NotNull final String name,
                             @NotNull final Tier tier,
                             @NotNull final ExecutorService service) {
        MANAGED.add(new Managed(name, tier, service));
    }

    /**
     * 线程工厂：命名规范 {@code Mili-<name>-<n>}，守护线程，带统一异常兜底。
     *
     * <p>把散落在各处的 {@code new Thread(r, "xxx")} 收敛到这里，顺带解决两个长期痛点：
     * 忘记 {@code setDaemon(true)} 导致 JVM 退不干净，以及任务抛异常后日志里只有一行
     * 无上下文的堆栈。</p>
     *
     * @param name 线程名前缀
     * @return 线程工厂
     */
    public static ThreadFactory factory(@NotNull final String name) {
        return factory(name, Thread.NORM_PRIORITY);
    }

    /**
     * 同 {@link #factory(String)}，但可指定线程优先级。
     *
     * <p>少数组件原本手工设置了优先级（如区域迁移决策用了 {@code NORM_PRIORITY + 2}），
     * 迁移时若不承接就会静默降级。提供这个重载是为了让"纳管"不意味着"削弱能力"。</p>
     *
     * @param name     线程名前缀
     * @param priority 线程优先级，取值应在 Thread.MIN_PRIORITY..MAX_PRIORITY 之间
     * @return 线程工厂
     */
    public static ThreadFactory factory(@NotNull final String name, final int priority) {
        return new MiliThreadFactory(name, false, priority);
    }

    /**
     * 有并发上限的虚拟线程执行器。
     *
     * <p>这是 {@link Tier#VIRTUAL} 唯一推荐的取用方式。直接使用
     * {@code Thread.ofVirtual().start(r)} 等价于无界线程：万一单批任务上万个，
     * 就会同时挂起上万个虚拟线程及其 continuation 栈。这里用信号量做闸门，
     * 超出的任务在调用方线程上排队等待许可，而不是无限扩张。</p>
     *
     * @param name            名称前缀
     * @param maxConcurrency  最大并发虚拟线程数，&lt;=0 表示不限（不推荐）
     * @return 执行器
     */
    public static Executor virtualExecutor(@NotNull final String name, final int maxConcurrency) {
        final Executor delegate = Executors.newThreadPerTaskExecutor(
                new MiliThreadFactory(name, true, Thread.NORM_PRIORITY));
        if (maxConcurrency <= 0) {
            return delegate;
        }
        final Semaphore gate = new Semaphore(maxConcurrency);
        return task -> {
            boolean acquired = false;
            try {
                gate.acquire();
                acquired = true;
                delegate.execute(() -> {
                    try {
                        task.run();
                    } finally {
                        gate.release();
                    }
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting for a virtual thread slot", ex);
            } catch (RuntimeException | Error uncheckedFailure) {
                // execute(Runnable) 的 lambda 契约不允许抛受检查异常，
                // 因此这里只接 unchecked 分支并原样重抛，同时归还已取得的许可。
                if (acquired) {
                    gate.release();
                }
                throw uncheckedFailure;
            }
        };
    }

    // ---------- 观测 ----------

    /**
     * 单个池的健康快照。poolSize/active/queued/completed 为 -1 表示该池实现不暴露这些计数。
     *
     * @param name      登记名
     * @param tier      所属层
     * @param poolSize  当前线程数
     * @param active    活动线程数
     * @param queued    排队任务数
     * @param completed 已完成任务数
     */
    public record Snapshot(String name, String tier, int poolSize, int active, long queued, long completed) {
    }

    /**
     * 采集全部受管池的快照。
     *
     * @return 快照列表，顺序为共享池在前、具名池在后
     */
    public static List<Snapshot> snapshot() {
        final List<Snapshot> out = new ArrayList<>();
        for (Map.Entry<Tier, ExecutorService> entry : SHARED.entrySet()) {
            out.add(describe("shared:" + entry.getKey().name().toLowerCase(Locale.ROOT),
                    entry.getKey().name(), entry.getValue()));
        }
        for (Managed managed : MANAGED) {
            out.add(describe(managed.name(), managed.tier().name(), managed.service()));
        }
        Collections.sort(out, java.util.Comparator.comparing(Snapshot::name));
        return out;
    }

    /**
     * 审计尚未纳管的 Mili 线程。
     *
     * <p>扫描规则：进程内所有以 {@code Mili}/{@code mili} 开头的线程，剔除本中枢已知前缀后，
     * 剩下的就是"自说自话起线程"的组件。这是迁移进度的度量工具，也是防止回潮的护栏。</p>
     *
     * @return 未纳管线程的描述列表（含线程状态）
     */
    public static List<String> auditUnmanagedThreads() {
        final Set<String> known = new java.util.HashSet<>();
        known.add("Mili-cpu-");
        known.add("Mili-io-");
        known.add("Mili-background-");
        known.add("Mili-virtual-");
        known.add("Mili-fallback-");
        for (Managed managed : MANAGED) {
            known.add("Mili-" + managed.name() + "-");
        }
        final List<String> out = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            final String name = thread.getName();
            if (!(name.startsWith("Mili") || name.startsWith("mili"))) {
                continue;
            }
            boolean managed = false;
            for (String prefix : known) {
                if (name.startsWith(prefix)) {
                    managed = true;
                    break;
                }
            }
            if (!managed) {
                out.add(name + " [" + thread.getState() + "]");
            }
        }
        Collections.sort(out);
        return out;
    }

    // ---------- 内部实现 ----------

    private record Managed(String name, Tier tier, ExecutorService service) {
    }

    private static synchronized ExecutorService ensureShared(final Tier tier) {
        final ExecutorService existing = SHARED.get(tier);
        if (existing != null && !existing.isShutdown()) {
            return existing;
        }
        final ExecutorService created = createShared(tier);
        SHARED.put(tier, created);
        return created;
    }

    private static ExecutorService createShared(final Tier tier) {
        switch (tier) {
            case CPU:
                return boundedPool("cpu", SchedulerConfig.resolveCpuThreads());
            case BLOCKING_IO:
                return boundedPool("io", SchedulerConfig.resolveIoThreads());
            case BACKGROUND:
                return Executors.newScheduledThreadPool(
                        Math.max(1, SchedulerConfig.resolveBackgroundThreads()), factory("background"));
            case VIRTUAL:
                throw new IllegalArgumentException("Tier.VIRTUAL has no shared pool");
            default:
                throw new IllegalArgumentException("Unsupported tier: " + tier);
        }
    }

    /**
     * 有界队列的固定池。
     *
     * <p>队列长度刻意设得很短（256）：后台任务是辅助性的，一旦积压说明下游已经出问题，
     * 此时快速失败并告警，远好过无限堆积直到 OOM。拒绝策略选择"在调用方线程执行"，
     * 天然形成背压。</p>
     */
    private static ExecutorService boundedPool(final String name, final int threads) {
        return new ThreadPoolExecutor(
                Math.max(1, threads),
                Math.max(1, threads),
                60L, TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(Math.max(1, SchedulerConfig.queueCapacity)),
                factory(name),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private static ScheduledExecutorService unmanagedFallback() {
        final ScheduledExecutorService existing = fallbackScheduled;
        if (existing != null && !existing.isShutdown()) {
            return existing;
        }
        synchronized (MiliScheduler.class) {
            if (fallbackScheduled == null || fallbackScheduled.isShutdown()) {
                fallbackScheduled = Executors.newSingleThreadScheduledExecutor(factory("fallback"));
            }
            return fallbackScheduled;
        }
    }

    private static void shutdownQuietly(final String name, final ExecutorService service, final long timeoutMs) {
        try {
            service.shutdown();
            if (!service.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                final List<Runnable> dropped = service.shutdownNow();
                LOGGER.warn("[Mili] Pool '{}' did not terminate in {}ms; force-stopped, {} queued task(s) dropped",
                        name, timeoutMs, dropped.size());
            }
        } catch (InterruptedException ex) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Throwable ex) {
            LOGGER.warn("[Mili] Pool '{}' shutdown encountered an error", name, ex);
        }
    }

    private static Snapshot describe(final String name, final String tier, final ExecutorService service) {
        int poolSize = -1;
        int active = -1;
        long queued = -1L;
        long completed = -1L;
        if (service instanceof ThreadPoolExecutor executor) {
            poolSize = executor.getPoolSize();
            active = executor.getActiveCount();
            queued = executor.getQueue().size();
            completed = executor.getCompletedTaskCount();
        }
        return new Snapshot(name, tier, poolSize, active, queued, completed);
    }

    /**
     * 统一命名与异常兜底的线程工厂。
     */
    private static final class MiliThreadFactory implements ThreadFactory {
        private final String prefix;
        private final boolean virtual;
        private final int priority;
        private final AtomicInteger seq = new AtomicInteger(0);

        MiliThreadFactory(final String prefix, final boolean virtual, final int priority) {
            this.prefix = prefix;
            this.virtual = virtual;
            this.priority = priority;
        }

        @Override
        public Thread newThread(@NotNull final Runnable runnable) {
            final String threadName = "Mili-" + this.prefix + "-" + this.seq.incrementAndGet();
            final Thread.Builder builder = this.virtual ? Thread.ofVirtual() : Thread.ofPlatform();
            final Thread thread = builder.name(threadName).unstarted(runnable);
            if (!this.virtual) {
                thread.setDaemon(true);
                // 虚拟线程不支持设置优先级（恒定 NORM），故仅对平台线程生效
                thread.setPriority(this.priority);
            }
            thread.setUncaughtExceptionHandler((target, cause) ->
                    LOGGER.error("[Mili] Uncaught exception in thread {}", target.getName(), cause));

            final int budget = SchedulerConfig.maxManagedThreads;
            final int now = ALLOCATED.incrementAndGet();
            if (budget > 0 && now > budget) {
                LOGGER.warn("[Mili] Managed thread budget exceeded: {} threads created (budget={}), '{}'",
                        now, budget, threadName);
            }
            return thread;
        }
    }
}
