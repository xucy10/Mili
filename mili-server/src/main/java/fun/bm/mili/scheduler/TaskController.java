package fun.bm.mili.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Owns the lifecycle of every live {@link TaskHandle} (fix.md §17/§19).
 * <p>
 * The scheduler used to keep task bookkeeping scattered between a priority queue,
 * a "taskRecords" map, and a UUID registry — three structures with three
 * different key spaces and no shared notion of "what is alive right now". This
 * class is the single source of truth:
 *
 * <ul>
 *   <li>tasks are registered before they are queued and unregistered when they
 *       reach a terminal state, so {@link #liveCount()} is meaningful;</li>
 *   <li>cancellation goes through here, which is what makes
 *       {@link #cancelAllForRegion(long, String)} possible during region destroy;</li>
 *   <li>deadline enforcement is a single sweep instead of a per-task timer thread.</li>
 * </ul>
 *
 * <p><b>Timeout policy:</b> deadlines are swept cooperatively from the scheduler
 * tick rather than by scheduling one {@code ScheduledFuture} per task. At a few
 * thousand tasks per second the per-task timer approach costs more than the work
 * it is protecting, and it makes shutdown ordering genuinely hard to reason about.
 */
public final class TaskController {

    private final ConcurrentHashMap<Long, TaskHandle> live = new ConcurrentHashMap<>();

    private final LongAdder registered = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder cancelled = new LongAdder();
    private final LongAdder timedOut = new LongAdder();
    private final LongAdder merged = new LongAdder();
    /** Declined at submission time (DEFERRED / REJECTED) — a backpressure outcome, not a failure. */
    private final LongAdder dropped = new LongAdder();

    /** Nanos spent inside the last deadline sweep, for self-observation. */
    private final AtomicLong lastSweepNanos = new AtomicLong(0);
    private final AtomicLong sweepCount = new AtomicLong(0);

    // ---------- Registration ----------

    /** Register a task. Must happen before the handle is queued. */
    public void register(TaskHandle handle) {
        if (handle == null) return;
        live.put(handle.taskId(), handle);
        registered.increment();
    }

    /**
     * Record that {@code handle} reached a terminal state and stop tracking it.
     * <p>
     * Idempotent: a handle can be completed by the dispatch path and then also be
     * swept as a timeout, and both must be safe.
     */
    public void complete(TaskHandle handle) {
        if (handle == null) return;
        if (live.remove(handle.taskId()) == null) {
            return; // already reaped
        }
        switch (handle.state()) {
            case COMPLETED -> completed.increment();
            case FAILED -> failed.increment();
            case CANCELLED -> cancelled.increment();
            case TIMED_OUT -> timedOut.increment();
            case MERGED -> merged.increment();
            case CREATED, QUEUED -> {
                // Never started and never will: the queue refused it (DEFERRED /
                // REJECTED) or it was never offered. Counted separately from
                // failures, because "we declined to schedule this" is a backpressure
                // outcome, not a defect.
                dropped.increment();
            }
            default -> {
                // A genuinely unexpected state. Counting it as failed rather than
                // losing it, because a silently untracked task would hide a bug.
                failed.increment();
            }
        }
    }

    public TaskHandle get(long taskId) {
        return live.get(taskId);
    }

    public int liveCount() {
        return live.size();
    }

    // ---------- Cancellation ----------

    /**
     * Cancel one task.
     *
     * @return {@code true} if the task existed and was asked to stop
     */
    public boolean cancel(long taskId, String reason) {
        TaskHandle handle = live.get(taskId);
        if (handle == null) return false;
        boolean cancelled = handle.requestCancel(reason);
        // Only reap now if the handle is genuinely terminal; a running body
        // publishes its own terminal state from runTask()'s finally block.
        if (handle.isTerminal()) {
            complete(handle);
        }
        return cancelled;
    }

    public boolean cancel(TaskHandle handle, String reason) {
        return handle != null && cancel(handle.taskId(), reason);
    }

    /**
     * Cancel every live task belonging to a region — the "cancel async work" step
     * of the region destroy sequence (fix.md §13).
     *
     * @return how many tasks were asked to stop
     */
    public int cancelAllForRegion(long regionId, String reason) {
        int count = 0;
        for (TaskHandle handle : live.values()) {
            if (handle.regionId() != regionId) continue;
            handle.requestCancel(reason);
            if (handle.isTerminal()) {
                complete(handle);
            }
            count++;
        }
        return count;
    }

    /** Snapshot of live tasks for one region. Diagnostics and destroy bookkeeping. */
    public List<TaskHandle> liveForRegion(long regionId) {
        List<TaskHandle> out = new ArrayList<>();
        for (TaskHandle handle : live.values()) {
            if (handle.regionId() == regionId) out.add(handle);
        }
        return out;
    }

    // ---------- Deadlines ----------

    /**
     * Cancel every live task whose deadline has passed.
     * <p>
     * Safe to call at any frequency; the scheduler wires it to its tick.
     *
     * @return how many tasks were timed out
     */
    public int sweepDeadlines() {
        long begin = System.nanoTime();
        int timedOutNow = 0;
        for (TaskHandle handle : live.values()) {
            if (!handle.hasDeadline() || handle.isTerminal()) continue;
            if (!handle.isExpired()) continue;
            if (handle.markTimedOut()) {
                timedOutNow++;
            }
            if (handle.isTerminal()) {
                complete(handle);
            }
        }
        lastSweepNanos.set(System.nanoTime() - begin);
        sweepCount.incrementAndGet();
        return timedOutNow;
    }

    public long lastSweepNanos() {
        return lastSweepNanos.get();
    }

    public long sweepCount() {
        return sweepCount.get();
    }

    // ---------- Reads ----------

    public long registeredCount() {
        return registered.sum();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("live_tasks", live.size());
        out.put("registered", registered.sum());
        out.put("completed", completed.sum());
        out.put("failed", failed.sum());
        out.put("cancelled", cancelled.sum());
        out.put("timed_out", timedOut.sum());
        out.put("merged", merged.sum());
        out.put("dropped_at_submission", dropped.sum());
        out.put("deadline_sweeps", sweepCount.get());
        out.put("last_sweep_ns", lastSweepNanos.get());
        return Collections.unmodifiableMap(out);
    }

    /**
     * Cancel everything still live. Used by scheduler shutdown.
     *
     * @return how many tasks were asked to stop
     */
    public int cancelAll(String reason) {
        int count = 0;
        for (TaskHandle handle : live.values()) {
            handle.requestCancel(reason);
            if (handle.isTerminal()) {
                complete(handle);
            }
            count++;
        }
        return count;
    }

    /** Forget every live handle. Only for shutdown, after cancellation has been requested. */
    public void clear() {
        live.clear();
    }
}
