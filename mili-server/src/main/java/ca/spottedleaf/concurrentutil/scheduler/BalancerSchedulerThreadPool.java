package ca.spottedleaf.concurrentutil.scheduler;

import ca.spottedleaf.concurrentutil.util.TimeUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Mili balancer scheduler thread pool.
 *
 * A deadline-driven shared-pool scheduler implementing the {@link Scheduler}
 * contract used by Folia's TickRegionScheduler, with three Mili-specific
 * improvements over the stock {@link EDFSchedulerThreadPool}:
 *
 * <ol>
 *   <li><b>Load-aware priority</b>: among tasks sharing the same tick deadline,
 *       regions are ordered by the load priority computed by Mili's
 *       {@code RegionLoadMonitor} (heavier regions start ticking first).</li>
 *   <li><b>Intermediate task draining</b>: unlike EDF (whose notifyTasks is a
 *       no-op), idle workers opportunistically drain intermediate tasks from
 *       scheduled regions whose tick deadline is still far away, instead of
 *       parking until the deadline.</li>
 *   <li><b>Tick instrumentation</b>: every region tick feeds Mili's
 *       RegionLoadMonitor / RegionBalancer statistics when the balancer config
 *       is enabled.</li>
 * </ol>
 *
 * Thread model: workers are created through the TickRegionScheduler's thread
 * factory, i.e. they are Folia {@code TickThreadRunner} instances, so region
 * context (currentTickingRegion, profiler, watchdog, CPU affinity) behaves
 * exactly as under the stock schedulers.
 *
 * Exception semantics match EDF: a {@link Throwable} escaping
 * {@link SchedulableTick#runTick()} propagates to the thread's uncaught
 * exception handler (Folia halts the scheduler and stops the server).
 *
 * Not implemented (same as EDF): dynamic thread reallocation (setThreads is a
 * no-op for this scheduler type) and NUMA awareness.
 */
public final class BalancerSchedulerThreadPool extends Scheduler {

    private static final long MAX_IDLE_PARK_NS = TimeUnit.SECONDS.toNanos(1L);

    /**
     * Never start draining intermediate tasks when the tick deadline is closer
     * than this; also the hard stop condition for an in-progress drain.
     */
    private static final long MIN_DRAIN_WINDOW_NS = TimeUnit.MILLISECONDS.toNanos(2L);

    private static final Comparator<ScheduledState> QUEUE_COMPARATOR = (final ScheduledState s1, final ScheduledState s2) -> {
        // earliest deadline first - this dominates, tick lateness is never
        // traded away for priority
        final int timeCompare = TimeUtil.compareTimes(s1.tick.scheduledStart, s2.tick.scheduledStart);
        if (timeCompare != 0) {
            return timeCompare;
        }

        // Mili: among equal deadlines, run the heavier region first
        final int priorityCompare = Double.compare(s2.priority, s1.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        return Long.signum(s1.tick.id - s2.tick.id);
    };

    private final BalancerRunner[] runners;
    private final Thread[] threads;
    private final PriorityQueue<ScheduledState> queued = new PriorityQueue<>(QUEUE_COMPARATOR);
    // scheduled states flagged via notifyTasks, drained opportunistically
    private final PriorityQueue<ScheduledState> taskNotifyQueue = new PriorityQueue<>(QUEUE_COMPARATOR);

    private final ReentrantLock scheduleLock = new ReentrantLock();
    private final Condition workAvailable = scheduleLock.newCondition();

    private volatile boolean halted;

    public BalancerSchedulerThreadPool(final int threads, final ThreadFactory threadFactory) {
        final BalancerRunner[] runners = new BalancerRunner[threads];
        final Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; ++i) {
            runners[i] = new BalancerRunner(i);
            t[i] = threadFactory.newThread(runners[i]);
        }

        this.runners = runners;
        this.threads = t;
    }

    /**
     * Starts all scheduler threads.
     */
    public void start() {
        for (final Thread thread : this.threads) {
            thread.start();
        }
    }

    @Override
    public void halt() {
        this.halted = true;
        this.scheduleLock.lock();
        try {
            this.workAvailable.signalAll();
        } finally {
            this.scheduleLock.unlock();
        }
    }

    @Override
    public boolean join(final long msToWait) {
        try {
            return this.join(msToWait, false);
        } catch (final InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean joinInterruptable(final long msToWait) throws InterruptedException {
        return this.join(msToWait, true);
    }

    private boolean join(final long msToWait, final boolean interruptable) throws InterruptedException {
        final long nsToWait = TimeUnit.MILLISECONDS.toNanos(msToWait);
        final long start = System.nanoTime();
        final long deadline = start + nsToWait;
        boolean interrupted = false;
        try {
            for (final Thread thread : this.threads) {
                while (thread.isAlive()) {
                    try {
                        if (msToWait > 0L) {
                            final long current = System.nanoTime();
                            if (current - deadline >= 0L) {
                                return false;
                            }
                            thread.join(TimeUnit.NANOSECONDS.toMillis(deadline - current));
                        } else {
                            thread.join();
                        }
                    } catch (final InterruptedException ex) {
                        if (interruptable) {
                            throw ex;
                        }
                        interrupted = true;
                    }
                }
            }

            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Thread[] getThreads() {
        return this.threads.clone();
    }

    @Override
    public Thread[] getCoreThreads() {
        return this.getThreads();
    }

    @Override
    public Thread[] getAliveThreads() {
        final List<Thread> ret = new ArrayList<>(this.threads.length);
        for (final Thread thread : this.threads) {
            if (thread.isAlive()) {
                ret.add(thread);
            }
        }

        return ret.toArray(new Thread[0]);
    }

    private static boolean isBalancerInstrumentationEnabled() {
        // Mili start - balancer instrumentation
        try {
            return fun.bm.mili.config.modules.experiment.RegionBalancerConfig.enabled;
        } catch (final Throwable throwable) {
            return false;
        }
        // Mili end - balancer instrumentation
    }

    /**
     * Recomputes the Mili load priority for the given state. Must be invoked
     * while the state is <b>not</b> inside {@link #queued} (the comparator
     * reads this field).
     */
    private static void rescorePriority(final ScheduledState state) {
        state.priority = isBalancerInstrumentationEnabled()
                ? fun.bm.mili.utils.RegionLoadMonitor.computePriority(state.tick, state.lastTickNanos)
                : 0.0d;

        // Mili start - 跨 region 依赖屏障（软门控）
        // 未就绪的 region 被压到队尾，让已就绪者先行。
        // 之所以做"软"而不是"硬"阻塞：硬阻塞会让队首卡住整条队列，而 poll 返回 null 时
        // 等待逻辑面对已过期的 deadline 会立刻返回，退化成忙轮询。优先级降级复用现有排序
        // 机制，既没有死锁也没有忙轮询风险。且未声明任何依赖时恒为 READY，与现状一致。
        if (fun.bm.mili.scheduler.RegionTickDag.isEnabled()
                && fun.bm.mili.scheduler.RegionTickDag.readiness(state.tick)
                        != fun.bm.mili.scheduler.RegionTickDag.Readiness.READY) {
            state.priority = Double.NEGATIVE_INFINITY;
        }
        // Mili end
    }

    @Override
    public void schedule(final SchedulableTick task) {
        final ScheduledState state = new ScheduledState(task);
        if (!task.setState(state)) {
            throw new IllegalStateException("Task " + task + " is already scheduled or cancelled");
        }

        if (!state.tryMarkScheduled()) {
            throw new IllegalStateException();
        }

        state.schedulerOwnedBy = this;
        rescorePriority(state);

        this.scheduleLock.lock();
        try {
            this.queued.add(state);
            // a newly scheduled task may become the earliest deadline
            this.workAvailable.signalAll();
        } finally {
            this.scheduleLock.unlock();
        }
    }

    @Override
    public boolean cancel(final SchedulableTick task) {
        if (!(task.state instanceof ScheduledState state)) {
            return false;
        }

        if (state.schedulerOwnedBy != this) {
            return false;
        }

        this.scheduleLock.lock();
        try {
            if (!state.tryMarkCancelled()) {
                // not in SCHEDULED state: either never scheduled, already
                // cancelled, or currently executing (cancellation takes
                // effect through the isScheduled() check on requeue)
                return false;
            }

            final boolean removed = this.queued.remove(state);
            this.taskNotifyQueue.remove(state);
            if (removed) {
                // an earlier head may be gone; sleepers wake on the next
                // signal-free deadline expiry anyway, so no signal needed
            }
            return removed;
        } finally {
            this.scheduleLock.unlock();
        }
    }

    @Override
    public void notifyTasks(final SchedulableTick task) {
        if (!(task.state instanceof ScheduledState state)) {
            return;
        }

        if (state.schedulerOwnedBy != this || !state.isScheduled()) {
            return;
        }

        this.scheduleLock.lock();
        try {
            // may be added multiple times between drains - dedupe on drain
            state.hasQueuedTasks = true;
            this.taskNotifyQueue.add(state);
            this.workAvailable.signalAll();
        } finally {
            this.scheduleLock.unlock();
        }
    }

    // must be called with scheduleLock held
    private ScheduledState pollDueLocked(final long now) {
        final ScheduledState head = this.queued.peek();
        if (head == null) {
            return null;
        }

        if (TimeUtil.compareTimes(head.tick.scheduledStart, now) > 0) {
            // earliest deadline not reached yet -> no task is due
            return null;
        }

        return this.queued.poll();
    }

    // must be called with scheduleLock held
    private ScheduledState pollDrainCandidateLocked(final long now) {
        ScheduledState state;
        while ((state = this.taskNotifyQueue.poll()) != null) {
            // dedupe: the state may have been queued multiple times, drained,
            // or cancelled since being flagged
            if (!state.isScheduled() || !state.hasQueuedTasks) {
                continue;
            }
            if (state.tick.scheduledStart - now <= MIN_DRAIN_WINDOW_NS) {
                // tick imminent or overdue - ticking takes precedence, keep the flag
                this.taskNotifyQueue.add(state);
                return null;
            }

            // claim by removing from the tick queue while draining
            if (!this.queued.remove(state)) {
                // currently executing a tick on some runner - retry later
                state.hasQueuedTasks = false;
                continue;
            }

            return state;
        }

        return null;
    }

    // must be called with scheduleLock held
    private long headDeadlineNsLocked() {
        final ScheduledState head = this.queued.peek();
        final ScheduledState drainHead = this.taskNotifyQueue.peek();

        long ret = head == null ? Long.MAX_VALUE : head.tick.scheduledStart;
        if (drainHead != null) {
            final long t = drainHead.tick.scheduledStart - MIN_DRAIN_WINDOW_NS;
            if (TimeUtil.compareTimes(t, ret) < 0) {
                ret = t;
            }
        }

        return ret;
    }

    private void requeue(final ScheduledState state, final boolean reschedule) {
        this.scheduleLock.lock();
        try {
            if (reschedule && state.isScheduled()) {
                rescorePriority(state);
                this.queued.add(state);
                this.workAvailable.signalAll();
            }
        } finally {
            this.scheduleLock.unlock();
        }
    }

    private static final class ScheduledState {
        private final SchedulableTick tick;

        private static final int SCHEDULE_STATE_NOT_SCHEDULED = 0;
        private static final int SCHEDULE_STATE_SCHEDULED = 1;
        private static final int SCHEDULE_STATE_CANCELLED = 2;

        private final AtomicInteger scheduled = new AtomicInteger();
        private BalancerSchedulerThreadPool schedulerOwnedBy;

        // Mili: load priority, valid only while the state is not inside a queue
        private volatile double priority = 0.0d;
        // Mili: nanotime of the last completed tick, for priority scoring
        private volatile long lastTickNanos = 0L;
        // Mili: set via notifyTasks, cleared once drained
        private volatile boolean hasQueuedTasks = false;

        private ScheduledState(final SchedulableTick tick) {
            this.tick = tick;
        }

        private boolean tryMarkScheduled() {
            return this.scheduled.compareAndSet(SCHEDULE_STATE_NOT_SCHEDULED, SCHEDULE_STATE_SCHEDULED);
        }

        private boolean tryMarkCancelled() {
            return this.scheduled.compareAndSet(SCHEDULE_STATE_SCHEDULED, SCHEDULE_STATE_CANCELLED);
        }

        private boolean isScheduled() {
            return this.scheduled.get() == SCHEDULE_STATE_SCHEDULED;
        }
    }

    private final class BalancerRunner implements Runnable {

        public final int id;
        volatile Thread thread;

        private BalancerRunner(final int id) {
            this.id = id;
        }

        private static final int ACTION_NONE = 0;
        private static final int ACTION_RUN_TICK = 1;
        private static final int ACTION_DRAIN_TASKS = 2;

        @Override
        public void run() {
            this.thread = Thread.currentThread();

            main_loop:
            for (;;) {
                if (BalancerSchedulerThreadPool.this.halted) {
                    return;
                }

                ScheduledState target = null;
                int action = ACTION_NONE;

                BalancerSchedulerThreadPool.this.scheduleLock.lock();
                try {
                    for (;;) {
                        if (BalancerSchedulerThreadPool.this.halted) {
                            return;
                        }

                        final long now = System.nanoTime();
                        target = BalancerSchedulerThreadPool.this.pollDueLocked(now);
                        if (target != null) {
                            action = ACTION_RUN_TICK;
                            break;
                        }

                        target = BalancerSchedulerThreadPool.this.pollDrainCandidateLocked(now);
                        if (target != null) {
                            action = ACTION_DRAIN_TASKS;
                            break;
                        }

                        final long head = BalancerSchedulerThreadPool.this.headDeadlineNsLocked();
                        if (head == Long.MAX_VALUE) {
                            // nothing queued at all; wait for a schedule/notify/halt signal
                            BalancerSchedulerThreadPool.this.workAvailable.await();
                        } else {
                            final long waitNs = head - now;
                            if (waitNs > 0L) {
                                BalancerSchedulerThreadPool.this.workAvailable.awaitNanos(
                                        Math.min(waitNs, MAX_IDLE_PARK_NS)
                                );
                            }
                        }
                    }
                } catch (final InterruptedException ex) {
                    // treat as a spurious wakeup and re-evaluate
                    continue main_loop;
                } finally {
                    BalancerSchedulerThreadPool.this.scheduleLock.unlock();
                }

                // execute outside the lock
                // note: target is re-assigned in the wait loop above and thus
                // not effectively final - capture it for lambda use below
                final ScheduledState task = target;
                switch (action) {
                    case ACTION_RUN_TICK: {
                        final boolean instrumentation = isBalancerInstrumentationEnabled();
                        final long begin = instrumentation ? System.nanoTime() : 0L;
                        if (instrumentation) {
                            fun.bm.mili.utils.RegionLoadMonitor.beforeTick(task.tick);
                        }

                        // exceptions propagate to the thread's uncaught
                        // exception handler, matching EDF semantics
                        final boolean reschedule = task.tick.runTick();

                        if (instrumentation) {
                            fun.bm.mili.utils.RegionLoadMonitor.afterTick(task.tick, System.nanoTime() - begin);
                            fun.bm.mili.utils.RegionBalancer.markTicked(task.tick);
                        }

                        // Mili start - 跨 region 依赖图：记录本 region 已完成本波 tick。
                        // 刻意放在 instrumentation 之外：即便负载监控关闭，波次也必须记账，
                        // 否则下一波永远不会开始，未就绪的 region 会被永久压在队尾。
                        fun.bm.mili.scheduler.RegionTickDag.markTicked(task.tick);
                        // Mili end

                        task.lastTickNanos = System.nanoTime();
                        task.hasQueuedTasks = false;
                        BalancerSchedulerThreadPool.this.requeue(task, reschedule);
                        break;
                    }

                    case ACTION_DRAIN_TASKS: {
                        final BooleanSupplier canContinue = () -> {
                            return !BalancerSchedulerThreadPool.this.halted
                                    && task.isScheduled()
                                    && task.tick.scheduledStart - System.nanoTime() > MIN_DRAIN_WINDOW_NS;
                        };

                        // runTasks is guaranteed non-parallel with runTick:
                        // we own the state exclusively (removed from queued)
                        final boolean reschedule = task.tick.runTasks(canContinue);

                        task.hasQueuedTasks = false;
                        BalancerSchedulerThreadPool.this.requeue(task, reschedule);
                        break;
                    }

                    default: {
                        throw new IllegalStateException("Unknown action: " + action);
                    }
                }
            }
        }
    }
}
