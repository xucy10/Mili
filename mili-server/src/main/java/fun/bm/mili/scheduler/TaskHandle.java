package fun.bm.mili.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A scheduled unit of work and its lifecycle controller.
 * <p>
 * fix.md §4: a task that reports {@code FAILED} because of a timeout used to keep
 * running on its virtual thread while the scheduler believed it was dead — dangerous
 * for Minecraft world mutation.  Cancellation therefore has to be <b>real</b>:
 * <pre>
 *     CANCELLED = state flipped
 *               + executing thread interrupted
 *               + cooperative token flipped
 * </pre>
 * <p>
 * fix.md §5: {@code Thread.interrupt()} alone is not enough (server hot loops rarely
 * check the interrupt flag), so every task also carries a {@link CancellationToken}
 * the task body can poll.
 */
public final class TaskHandle {

    private final long taskId;
    private final UUID taskUuid;
    private final long regionId;
    private final Object region;
    private final Runnable work;
    private final long deadlineNanos;
    private final CancellationToken token = CancellationToken.never();
    private final CountDownLatch completion = new CountDownLatch(1);
    private final long enqueueNanos = System.nanoTime();

    /** Work folded into this handle by the merge backpressure path (fix.md §3). */
    private final List<Runnable> mergedWork = new ArrayList<>(2);

    private volatile TaskState state = TaskState.QUEUED;
    /** Thread currently executing this task, or null. Written by the worker before it runs the body. */
    private volatile Thread executingThread;
    private volatile long startNanos;
    private volatile long endNanos;

    TaskHandle(long taskId, UUID taskUuid, long regionId, Object region, Runnable work, long deadlineNanos) {
        this.taskId = taskId;
        this.taskUuid = taskUuid;
        this.regionId = regionId;
        this.region = region;
        this.work = work;
        this.deadlineNanos = deadlineNanos;
    }

    public long taskId() { return taskId; }
    public UUID taskUuid() { return taskUuid; }
    public long regionId() { return regionId; }
    public Object region() { return region; }
    public Runnable work() { return work; }
    public CancellationToken token() { return token; }
    public TaskState state() { return state; }
    public Thread executingThread() { return executingThread; }
    public long enqueueNanos() { return enqueueNanos; }
    public long startNanos() { return startNanos; }
    public long endNanos() { return endNanos; }
    public long deadlineNanos() { return deadlineNanos; }

    public boolean isDone() { return completion.getCount() == 0; }

    /**
     * Attempt to claim the task for execution.
     *
     * @return {@code false} if the task was already claimed, cancelled or finished
     */
    public boolean tryStart() {
        if (state != TaskState.QUEUED) return false;
        executingThread = Thread.currentThread();
        startNanos = System.nanoTime();
        state = TaskState.RUNNING;
        return true;
    }

    /**
     * Run the task body with cancellation semantics.
     *
     * @return {@code true} if the body ran to completion
     */
    /**
     * Fold {@code extra} into this already-queued task instead of enqueueing it
     * separately (fix.md §3 "MERGED" backpressure branch).
     *
     * @return {@code false} if this task can no longer accept merged work
     */
    public boolean tryMerge(Runnable extra) {
        if (extra == null) return false;
        if (state != TaskState.QUEUED) return false;
        synchronized (mergedWork) {
            if (state != TaskState.QUEUED) return false;
            mergedWork.add(extra);
        }
        return true;
    }

    public int mergedCount() {
        synchronized (mergedWork) {
            return mergedWork.size();
        }
    }

    public boolean runBody() {
        if (!tryStart()) return false;
        try {
            if (token.isCancelled()) {
                complete(TaskState.CANCELLED);
                return false;
            }
            work.run();
            runMerged();
            complete(TaskState.COMPLETED);
            return true;
        } catch (CancellationToken.CancelledException ce) {
            complete(TaskState.CANCELLED);
            return false;
        } catch (Throwable t) {
            complete(TaskState.FAILED);
            throw t;
        }
    }

    private void runMerged() {
        if (mergedWork.isEmpty()) return;
        List<Runnable> snapshot;
        synchronized (mergedWork) {
            if (mergedWork.isEmpty()) return;
            snapshot = new ArrayList<>(mergedWork);
            mergedWork.clear();
        }
        for (Runnable extra : snapshot) {
            if (token.isCancelled()) break;
            extra.run();
        }
    }

    /**
     * Cancel the task for real: flip the token, interrupt the executing thread and move
     * the state to {@link TaskState#CANCELLED}.
     *
     * @return {@code true} if this call performed the cancellation
     */
    public boolean cancel() {
        return cancel("cancelled");
    }

    public boolean cancel(String reason) {
        token.cancel(reason);

        Thread thread = executingThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }

        if (state == TaskState.QUEUED || state == TaskState.RUNNING) {
            state = TaskState.CANCELLED;
            endNanos = System.nanoTime();
            completion.countDown();
            return true;
        }
        // Already terminal: still make sure waiters are released.
        completion.countDown();
        return false;
    }

    /** Mark the task as merged into another task (fix.md §3 backpressure). */
    public void markMerged() {
        state = TaskState.MERGED;
        endNanos = System.nanoTime();
        completion.countDown();
    }

    public void complete(TaskState terminal) {
        state = terminal;
        endNanos = System.nanoTime();
        executingThread = null;
        completion.countDown();
    }

    /** Wait for completion. Used by {@code submitAndWait} (fix.md §2.1). */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return completion.await(timeout, unit);
    }

    /** Wait, converting interruption into a cancellation instead of propagating. */
    public boolean awaitQuietly(long timeout, TimeUnit unit) {
        try {
            return completion.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public long elapsedNanos() {
        long end = endNanos;
        return end == 0 ? System.nanoTime() - startNanos : end - startNanos;
    }

    public boolean isPastDeadline(long nowNanos) {
        return deadlineNanos > 0 && nowNanos > deadlineNanos;
    }

    @Override
    public String toString() {
        return "TaskHandle[id=" + taskId + ", region=" + regionId + ", state=" + state + "]";
    }
}
