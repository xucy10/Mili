package fun.bm.mili.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns task lifecycle: creation, cancellation, timeout sweeping and teardown.
 * <p>
 * fix.md §14: cancellation / lifecycle / timeout / state are pulled out of the
 * scheduler itself so that the scheduler only decides <i>where and when</i> work runs,
 * while this controller decides <i>whether it still should</i>.
 */
public final class TaskController {

    private static final AtomicLong TASK_ID_GEN = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, TaskHandle> LIVE = new ConcurrentHashMap<>();

    private TaskController() {}

    public static long nextTaskId() {
        return TASK_ID_GEN.incrementAndGet();
    }

    /**
     * Create and register a task.
     *
     * @param deadlineNanos absolute deadline, or 0 for no deadline
     */
    public static TaskHandle create(long regionId, Object region, Runnable work, long deadlineNanos) {
        TaskHandle handle = new TaskHandle(
                nextTaskId(),
                java.util.UUID.randomUUID(),
                regionId,
                region,
                work,
                deadlineNanos);
        LIVE.put(handle.taskId(), handle);
        return handle;
    }

    public static TaskHandle get(long taskId) {
        return LIVE.get(taskId);
    }

    /** Forget a finished task. Called once a handle reached a terminal state. */
    public static void release(TaskHandle handle) {
        if (handle == null) return;
        LIVE.remove(handle.taskId());
    }

    /** Cancel by id. Returns false if the task is unknown or already terminal. */
    public static boolean cancel(long taskId) {
        TaskHandle handle = LIVE.get(taskId);
        return handle != null && handle.cancel("cancelled-by-controller");
    }

    public static boolean cancel(long taskId, String reason) {
        TaskHandle handle = LIVE.get(taskId);
        return handle != null && handle.cancel(reason);
    }

    /** Cancel everything still queued/running for a region. Used by lifecycle teardown. */
    public static int cancelAllForRegion(long regionId) {
        int cancelled = 0;
        for (TaskHandle handle : LIVE.values()) {
            if (handle.regionId() == regionId && handle.cancel("region-teardown")) {
                cancelled++;
            }
        }
        return cancelled;
    }

    /**
     * Cancel every task whose deadline has passed.
     * <p>
     * fix.md §4/§22.4: a timeout must not merely mean "the scheduler stopped waiting" —
     * the sweep interrupts the executing thread and flips the token so the body exits.
     *
     * @return number of tasks cancelled
     */
    public static int sweepTimedOutTasks() {
        long now = System.nanoTime();
        int cancelled = 0;
        for (TaskHandle handle : LIVE.values()) {
            if (handle.isPastDeadline(now) && !handle.isDone()) {
                if (handle.cancel("timeout")) cancelled++;
            }
        }
        return cancelled;
    }

    public static Collection<TaskHandle> liveTasks() {
        return LIVE.values();
    }

    public static List<TaskHandle> liveTasksForRegion(long regionId) {
        List<TaskHandle> result = new ArrayList<>();
        for (TaskHandle handle : LIVE.values()) {
            if (handle.regionId() == regionId) result.add(handle);
        }
        return result;
    }

    public static int liveCount() {
        return LIVE.size();
    }

    /** Emergency teardown: cancel and forget everything. */
    public static void cancelAll() {
        for (TaskHandle handle : LIVE.values()) {
            handle.cancel("controller-shutdown");
        }
        LIVE.clear();
    }
}
