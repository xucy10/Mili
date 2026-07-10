package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.Method;

public final class RegionBalancer {

    private RegionBalancer() {}

    public enum TaskState {
        UNKNOWN, QUEUED, RUNNING, MERGED, COMPLETED, FAILED, CANCELLED
    }

    private static final class TaskRecord {
        final long taskUid;
        final Object scheduleRef;
        final Runnable work;
        final long tickCount;
        volatile TaskState state = TaskState.QUEUED;
        volatile String trace = "queued";
        volatile int retryCount;
        volatile boolean cancelRequested;
        volatile long updatedNanos;
        volatile long createdNanos;

        TaskRecord(long taskUid, Object scheduleRef, Runnable work, long tickCount) {
            this.taskUid = taskUid;
            this.scheduleRef = scheduleRef;
            this.work = work;
            this.tickCount = tickCount;
            this.updatedNanos = System.nanoTime();
            this.createdNanos = this.updatedNanos;
        }
    }

    public static final class RegionTask implements Comparable<RegionTask> {
        final Object scheduleRef;
        final Runnable work;
        final long taskUid;
        final long enqueueNanos;
        volatile double priority;
        final long tickCount;
        final long seq;

        RegionTask(Object scheduleRef, Runnable work, long tickCount, long seq) {
            this(scheduleRef, work, tickCount, seq, nextTaskUid());
        }

        private RegionTask(Object scheduleRef, Runnable work, long tickCount, long seq, long taskUid) {
            this.scheduleRef = scheduleRef;
            this.work = work;
            this.tickCount = tickCount;
            this.enqueueNanos = System.nanoTime();
            this.priority = 0.0;
            this.seq = seq;
            this.taskUid = taskUid;
        }

        @Override
        public int compareTo(@NotNull RegionTask o) {
            int cmp = Double.compare(o.priority, this.priority);
            if (cmp != 0) return cmp;
            return Long.compare(this.seq, o.seq);
        }
    }

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static volatile ExecutorService WORKER_POOL;
    private static final PriorityBlockingQueue<RegionTask> TASK_QUEUE = new PriorityBlockingQueue<>();
    private static final ConcurrentHashMap<Integer, AtomicLong> LAST_TICK_TIME = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, TaskRecord> TASK_RECORDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, RegionTask> PENDING_TASKS = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private static final String RUST_OPTIMIZER_CLASS_NAME = "org.mili.rust.RustOptimizer";
    private static final Method RUST_SCHEDULER_METHOD = findRustMethod("scheduler", int.class, int.class);
    private static final Method RUST_TASK_UID_METHOD = findRustMethod("taskUid");
    private static final Method RUST_NETWORK_OPT_METHOD = findRustMethod("networkOptimize", String.class);

    private static final AtomicInteger activeTasks = new AtomicInteger(0);
    private static final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private static final AtomicLong totalMergeOperations = new AtomicLong(0);

    public static void init() {
        if (!RegionBalancerConfig.enabled) return;
        if (INITIALIZED.getAndSet(true)) return;

        int poolSize = RegionBalancerConfig.getThreadPoolSize();
        WORKER_POOL = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "Mili-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 1);
            return t;
        });

        Thread dispatcher = new Thread(RegionBalancer::dispatchLoop, "Mili-Dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.setPriority(Thread.NORM_PRIORITY + 2);
        dispatcher.start();

        AdaptiveTPSManager.start();
        SmartRegionManager.init();
        MemoryOptimizer.init();
        PerformanceCollector.init();
        MiliOptimizations.init();

        org.mojang.logging.LogUtils.getClassLogger().info(
                "[Mili] RegionBalancer v3.0 initialized with {} workers", poolSize);
    }

    private static Method findRustMethod(String name, Class<?>... params) {
        try {
            Class<?> cls = Class.forName(RUST_OPTIMIZER_CLASS_NAME);
            return cls.getMethod(name, params);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long nextTaskUid() {
        if (RUST_TASK_UID_METHOD == null) return System.nanoTime();

        try {
            Object result = RUST_TASK_UID_METHOD.invoke(null);
            if (result instanceof String s) {
                String[] parts = s.split(":", 2);
                if (parts.length == 2) return Long.parseLong(parts[1]);
            }
        } catch (Exception ignored) {}
        return System.nanoTime();
    }

    private static void dispatchLoop() {
        while (!SHUTDOWN.get()) {
            try {
                RegionTask task = TASK_QUEUE.poll(100, TimeUnit.MILLISECONDS);
                if (task == null) continue;

                long waitTime = System.nanoTime() - task.enqueueNanos;
                double starvationBoost = Math.min(0.4, waitTime / 80_000_000.0);
                task.priority += starvationBoost;

                RegionLoadMonitor.RegionLoadSnapshot snap = RegionLoadMonitor.getSnapshot(task.scheduleRef);

                if (snap.isLowLoad()) {
                    List<RegionTask> batch = buildLowLoadBatch(task);
                    if (batch.size() > 1) {
                        submitMergedBatch(batch);
                        totalMergeOperations.incrementAndGet();
                        continue;
                    }
                }

                submitSingleTask(task);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                org.mojang.logging.LogUtils.getClassLogger()
                        .error("[Mili] Dispatcher error", e);
            }
        }
    }

    private static List<RegionTask> buildLowLoadBatch(RegionTask seed) {
        List<RegionTask> batch = new ArrayList<>();
        batch.add(seed);

        int maxBatch = getDynamicBatchSize(seed);
        while (batch.size() < maxBatch) {
            RegionTask next = TASK_QUEUE.poll();
            if (next == null) break;

            RegionLoadMonitor.RegionLoadSnapshot nextSnap =
                    RegionLoadMonitor.getSnapshot(next.scheduleRef);
            if (nextSnap.isLowLoad()) {
                batch.add(next);
            } else {
                TASK_QUEUE.add(next);
                break;
            }
        }
        return batch;
    }

    private static int getDynamicBatchSize(RegionTask task) {
        int baseLimit = RegionBalancerConfig.mergeBatchHardLimit;
        RegionLoadMonitor.RegionLoadSnapshot snap = RegionLoadMonitor.getSnapshot(task.scheduleRef);

        if (snap.avgTickNanos() < RegionBalancerConfig.lowLoadThresholdMs * 500_000.0) {
            return Math.min(baseLimit, RegionBalancerConfig.mergeBatchHardLimit);
        }
        return Math.min(baseLimit, RegionBalancerConfig.mergeBatchSoftLimit);
    }

    private static void submitMergedBatch(List<RegionTask> batch) {
        final List<Runnable> works = new ArrayList<>();
        for (RegionTask t : batch) works.add(t.work);

        WORKER_POOL.execute(() -> {
            int taskId = activeTasks.incrementAndGet();
            try {
                markTasks(batch, TaskState.MERGED, "merged:" + batch.size());
                markTasks(batch, TaskState.RUNNING, "running");

                for (Runnable w : works) w.run();

                markTasks(batch, TaskState.COMPLETED, "completed");
                totalTasksCompleted.addAndGet(batch.size());
            } catch (Throwable ex) {
                markTasks(batch, TaskState.FAILED, "failed");
                org.mojang.logging.LogUtils.getClassLogger()
                        .error("[Mili] Merged batch failed", ex);
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }

    private static void submitSingleTask(RegionTask task) {
        WORKER_POOL.execute(() -> {
            activeTasks.incrementAndGet();
            try {
                markTasks(List.of(task), TaskState.RUNNING, "running");
                task.work.run();
                markTasks(List.of(task), TaskState.COMPLETED, "completed");
                totalTasksCompleted.incrementAndGet();
            } catch (Throwable ex) {
                markTasks(List.of(task), TaskState.FAILED, "failed");
                org.mojang.logging.LogUtils.getClassLogger()
                        .error("[Mili] Task failed", ex);
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }

    private static void markTasks(List<RegionTask> tasks, TaskState state, String trace) {
        for (RegionTask task : tasks) {
            TaskRecord rec = TASK_RECORDS.computeIfAbsent(task.taskUid,
                    id -> new TaskRecord(id, task.scheduleRef, task.work, task.tickCount));
            rec.state = state;
            rec.trace = trace + ":" + task.seq;
            rec.updatedNanos = System.nanoTime();

            if (state == TaskState.COMPLETED || state == TaskState.FAILED ||
                    state == TaskState.CANCELLED) {
                PENDING_TASKS.remove(task.taskUid);
            }
        }
    }

    public static void submit(Object scheduleRef, long tickCount, Runnable work) {
        if (!RegionBalancerConfig.enabled || WORKER_POOL == null) {
            work.run();
            return;
        }

        int key = System.identityHashCode(scheduleRef);
        AtomicLong lastTick = LAST_TICK_TIME.computeIfAbsent(key, k -> new AtomicLong(0));
        long last = lastTick.get();

        double priority = RegionLoadMonitor.computePriority(scheduleRef, last);
        RegionTask task = new RegionTask(scheduleRef, work, tickCount,
                SEQUENCE.incrementAndGet());
        task.priority = priority;

        registerTask(task);
        TASK_QUEUE.add(task);
    }

    public static void submitAndWait(Object scheduleRef, long tickCount, Runnable work) {
        if (!RegionBalancerConfig.enabled || WORKER_POOL == null) {
            work.run();
            return;
        }

        RegionLoadMonitor.beforeTick(scheduleRef);
        long begin = System.nanoTime();
        work.run();
        RegionLoadMonitor.afterTick(scheduleRef, System.nanoTime() - begin);
        markTicked(scheduleRef);
    }

    private static void registerTask(RegionTask task) {
        TaskRecord rec = TASK_RECORDS.computeIfAbsent(task.taskUid,
                id -> new TaskRecord(id, task.scheduleRef, task.work, task.tickCount));
        rec.state = TaskState.QUEUED;
        rec.trace = "queued:" + task.seq;
        rec.updatedNanos = System.nanoTime();
        PENDING_TASKS.put(task.taskUid, task);
    }

    public static TaskState getTaskState(long uid) {
        TaskRecord rec = TASK_RECORDS.get(uid);
        return rec != null ? rec.state : TaskState.UNKNOWN;
    }

    public static String getTaskTrace(long uid) {
        TaskRecord rec = TASK_RECORDS.get(uid);
        return rec != null ? rec.trace : "unknown";
    }

    public static boolean cancelTask(long uid) {
        TaskRecord rec = TASK_RECORDS.get(uid);
        if (rec == null) return false;

        rec.cancelRequested = true;
        rec.state = TaskState.CANCELLED;
        rec.trace = "cancelled";
        rec.updatedNanos = System.nanoTime();
        PENDING_TASKS.remove(uid);
        return true;
    }

    public static boolean retryTask(long uid) {
        TaskRecord rec = TASK_RECORDS.get(uid);
        if (rec == null || (rec.cancelRequested && rec.state == TaskState.CANCELLED)) {
            return false;
        }
        if (rec.state == TaskState.RUNNING) return false;

        rec.retryCount++;
        rec.cancelRequested = false;
        rec.state = TaskState.QUEUED;
        rec.trace = "retried:" + rec.retryCount;
        rec.updatedNanos = System.nanoTime();

        if (!RegionBalancerConfig.enabled || WORKER_POOL == null) {
            rec.work.run();
            rec.state = TaskState.COMPLETED;
            return true;
        }

        RegionTask retry = new RegionTask(rec.scheduleRef, rec.work, rec.tickCount,
                SEQUENCE.incrementAndGet(), uid);
        retry.priority = RegionLoadMonitor.computePriority(rec.scheduleRef,
                System.nanoTime());
        registerTask(retry);
        TASK_QUEUE.add(retry);
        return true;
    }

    public static void clearTaskTrace(long uid) {
        TaskRecord rec = TASK_RECORDS.get(uid);
        if (rec != null) rec.trace = "cleared";
    }

    public static void markTicked(Object scheduleRef) {
        if (!RegionBalancerConfig.enabled) return;
        int key = System.identityHashCode(scheduleRef);
        AtomicLong last = LAST_TICK_TIME.get(key);
        if (last != null) last.set(System.nanoTime());
    }

    public static int pendingTasks() { return TASK_QUEUE.size(); }

    public static int activeTaskCount() { return activeTasks.get(); }

    public static long totalCompleted() { return totalTasksCompleted.get(); }

    public static long totalMerges() { return totalMergeOperations.get(); }

    public static Collection<TaskState> getAllTaskStates() {
        List<TaskState> states = new ArrayList<>();
        for (TaskRecord rec : TASK_RECORDS.values()) {
            states.add(rec.state);
        }
        return states;
    }

    public static Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("pending", TASK_QUEUE.size());
        stats.put("active", activeTasks.get());
        stats.put("completed", (int) totalTasksCompleted.get());
        stats.put("merged", (int) totalMergeOperations.get());
        stats.put("regions_tracked", LAST_TICK_TIME.size());
        stats.put("workers", WORKER != null ?
                ((ThreadPoolExecutor)WORKER_POOL).getPoolSize() : 0);
        return stats;
    }

    public static void shutdown() {
        if (SHUTDOWN.getAndSet(true)) return;

        if (WORKER_POOL != null) {
            WORKER_POOL.shutdown();
            try {
                if (!WORKER_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                    WORKER_POOL.shutdownNow();
                }
            } catch (InterruptedException e) {
                WORKER_POOL.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        SmartRegionManager.shutdown();
        MemoryOptimizer.shutdown();
        AdaptiveTPSManager.stop();

        TASK_QUEUE.clear();
        TASK_RECORDS.clear();
        PENDING_TASKS.clear();
        LAST_TICK_TIME.clear();

        org.mojang.logging.LogUtils.getClassLogger().info("[Mili] RegionBalancer v3.0 shutdown complete");
    }
}