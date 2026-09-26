package fun.bm.mili.scheduler;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-region counters.
 * <p>
 * fix.md §8: metrics, scheduler, registry, cross-region and entity budget must all key
 * off the same stable region id.  These counters are therefore exposed per
 * {@code regionId}, not per {@code identityHashCode}.
 */
public final class RegionMetrics {

    private final long regionId;
    private final LongAdder tasksSubmitted = new LongAdder();
    private final LongAdder tasksExecuted = new LongAdder();
    private final LongAdder tasksCancelled = new LongAdder();
    private final LongAdder tasksRejected = new LongAdder();
    private final LongAdder tasksDeferred = new LongAdder();
    private final LongAdder tasksMerged = new LongAdder();
    private final LongAdder executionNanos = new LongAdder();
    private final AtomicLong lastTickNanos = new AtomicLong(0L);

    public RegionMetrics(long regionId) {
        this.regionId = regionId;
    }

    public long regionId() {
        return regionId;
    }

    public void onSubmitted() { tasksSubmitted.increment(); }
    public void onExecuted(long nanos) {
        tasksExecuted.increment();
        executionNanos.add(nanos);
        lastTickNanos.set(nanos);
    }
    public void onCancelled() { tasksCancelled.increment(); }
    public void onRejected() { tasksRejected.increment(); }
    public void onDeferred() { tasksDeferred.increment(); }
    public void onMerged() { tasksMerged.increment(); }

    public long submitted() { return tasksSubmitted.sum(); }
    public long executed() { return tasksExecuted.sum(); }
    public long cancelled() { return tasksCancelled.sum(); }
    public long rejected() { return tasksRejected.sum(); }
    public long deferred() { return tasksDeferred.sum(); }
    public long merged() { return tasksMerged.sum(); }
    public long totalExecutionNanos() { return executionNanos.sum(); }
    public long lastTickNanos() { return lastTickNanos.get(); }

    public double averageTickNanos() {
        long executedCount = tasksExecuted.sum();
        return executedCount == 0 ? 0.0 : (double) executionNanos.sum() / (double) executedCount;
    }
}
