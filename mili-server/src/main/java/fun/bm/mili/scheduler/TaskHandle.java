package fun.bm.mili.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

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

    /**
     * Mili start - fix: the state field was a plain {@code volatile} that every transition
     * wrote directly, so {@code cancel()} and {@code complete()} could overwrite each other
     * (a cancelled task could be reported {@code COMPLETED} and vice versa).  All
     * transitions now go through this updater; the field stays {@code volatile} so the
     * lock-free {@link #state()} reader and the updater agree on visibility.
     */
    private static final AtomicReferenceFieldUpdater<TaskHandle, TaskState> STATE =
            AtomicReferenceFieldUpdater.newUpdater(TaskHandle.class, TaskState.class, "state");

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
     * <p>
     * Mili start - fix: the claim is a CAS, so a task cancelled while still {@code QUEUED}
     * can never be started afterwards.  The executing thread and start timestamp are
     * published <i>before</i> the CAS so that a concurrent {@code cancel()} always sees a
     * thread to interrupt; if the CAS loses, both are rolled back because the body will
     * never run.
     *
     * @return {@code false} if the task was already claimed, cancelled or finished
     */
    public boolean tryStart() {
        executingThread = Thread.currentThread();
        startNanos = System.nanoTime();
        if (STATE.compareAndSet(this, TaskState.QUEUED, TaskState.RUNNING)) {
            return true;
        }
        executingThread = null;
        startNanos = 0L;
        return false;
    }

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

    /**
     * Run the task body with cancellation semantics.
     *
     * @return {@code true} if the body ran to completion
     */
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
     * Cancel the task for real: flip the token, interrupt the executing thread and drive
     * the state towards {@link TaskState#CANCELLED}.
     * <p>
     * Mili start - fix: cancellation of a <i>running</i> task is a two-step handshake.
     * <ul>
     *   <li>{@code QUEUED -> CANCELLED}: CAS, the body will never start.</li>
     *   <li>{@code RUNNING -> CANCELLING}: CAS, waiters are released immediately so
     *       {@code submitAndWait} returns without burning its whole timeout, but the
     *       terminal {@code CANCELLED} is only published by the body's own
     *       {@link #complete(TaskState)}.  That is the first instant Mili can prove the
     *       task stopped touching protected state.</li>
     *   <li>Anything already terminal is never overwritten, so a cancelled task can no
     *       longer be relabelled {@code COMPLETED} by a racing writer.</li>
     * </ul>
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

        for (;;) {
            TaskState current = state;
            if (current.isTerminal()) {
                // Already terminal: never overwrite, but still release waiters.
                completion.countDown();
                return false;
            }
            if (current == TaskState.CANCELLING) {
                completion.countDown();
                return false;
            }
            TaskState next = current == TaskState.QUEUED
                    ? TaskState.CANCELLED
                    : TaskState.CANCELLING;
            if (STATE.compareAndSet(this, current, next)) {
                if (next == TaskState.CANCELLED) {
                    endNanos = System.nanoTime();
                }
                completion.countDown();
                return true;
            }
            // Lost the race: re-read and decide again.
        }
    }

    /**
     * Mark the task as merged into another task (fix.md §3 backpressure).
     * <p>
     * Mili start - fix: only a task that is still {@code QUEUED} may be marked merged.
     * The old implementation wrote the state unconditionally, which let a rejected /
     * cancelled task be relabelled {@code MERGED} and report work that had in fact been
     * dropped.
     *
     * @return {@code true} if this call performed the merge transition
     */
    public boolean markMerged() {
        if (STATE.compareAndSet(this, TaskState.QUEUED, TaskState.MERGED)) {
            endNanos = System.nanoTime();
            completion.countDown();
            return true;
        }
        // Already finished or already merged: keep waiters unblocked.
        completion.countDown();
        return false;
    }

    /**
     * Publish the body's terminal state.
     * <p>
     * Mili start - fix: only {@code RUNNING}/{@code CANCELLING} may be finished, and a
     * cancellation request always wins over the body's own verdict — a body that returns
     * normally after its token was flipped must not be reported {@code COMPLETED}.
     *
     * @param terminal the state to publish when the task was not cancelled meanwhile
     */
    public void complete(TaskState terminal) {
        for (;;) {
            TaskState current = state;
            if (current != TaskState.RUNNING && current != TaskState.CANCELLING) {
                break;
            }
            TaskState next = (current == TaskState.CANCELLING || token.isCancelled())
                    ? TaskState.CANCELLED
                    : terminal;
            if (STATE.compareAndSet(this, current, next)) {
                break;
            }
        }
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
