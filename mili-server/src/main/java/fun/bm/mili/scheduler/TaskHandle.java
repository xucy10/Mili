package fun.bm.mili.scheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handle for one unit of scheduled work.
 * <p>
 * This is where fix.md §4 lands. The previous design could publish
 * {@code FAILED} / {@code CANCELLED} while the task body was still running and
 * still mutating Minecraft state, so "the scheduler thinks it is gone" and "the
 * code has actually stopped" were two different things. Here they are the same
 * thing:
 *
 * <ul>
 *   <li>the handle records {@link #executingThread} for the whole duration of the body;</li>
 *   <li>{@link #requestCancel(String)} cancels the {@link CancellationToken}
 *       <em>and</em> interrupts that thread;</li>
 *   <li>a terminal cancelled state is only published once the body has returned —
 *       either in the {@code finally} of {@link #runTask()} or, when no thread was
 *       ever running, immediately.</li>
 * </ul>
 *
 * <p>A handle is single-use. Once terminal it is never reset; pooled reuse is not
 * attempted because correctness beats one allocation per submitted task.
 */
public final class TaskHandle {

    /** Value for "no deadline". */
    public static final long NO_DEADLINE = 0L;

    /** Priority at or above which a full queue still accepts the task (bounded overshoot). */
    public static final int HIGH_PRIORITY = 80;

    private static final AtomicInteger NEXT_TASK_ID = new AtomicInteger(0);

    private final long taskId = NEXT_TASK_ID.incrementAndGet();
    private final long regionId;
    private final Object region;
    private final Runnable work;
    private final int priority;
    private final CancellationToken token;
    private final long deadlineNanos;
    private final String description;

    /**
     * Non-null when two tasks with equal keys are interchangeable and one may be
     * folded into the other. Waited-on tasks always pass {@code null}, because a
     * waiter must be able to observe its own completion.
     */
    private final Object mergeKey;

    private final AtomicReference<TaskState> state = new AtomicReference<>(TaskState.CREATED);
    private final CountDownLatch done = new CountDownLatch(1);

    private final AtomicInteger absorbedCount = new AtomicInteger(0);

    /**
     * The thread currently inside {@link #runTask()}, or {@code null}.
     * <p>
     * Deliberately not {@code volatile}-only: writes and reads are ordered by the
     * state CAS in {@link #runTask()}, and the field is read on cancel paths that
     * also consult the token. Marking it volatile keeps the JIT honest anyway.
     */
    private volatile Thread executingThread;

    /** The {@link Throwable} that failed this task, retained for diagnostics. */
    private volatile Throwable failure;

    private final long createdAtNanos = System.nanoTime();
    private volatile long startedAtNanos;
    private volatile long finishedAtNanos;

    public TaskHandle(long regionId, Object region, Runnable work, int priority,
                      CancellationToken token, long deadlineNanos, Object mergeKey, String description) {
        this.regionId = regionId;
        this.region = region;
        this.work = work;
        this.priority = priority;
        this.token = token == null ? CancellationToken.create() : token;
        this.deadlineNanos = deadlineNanos;
        this.mergeKey = mergeKey;
        this.description = description == null ? "task" : description;
    }

    /** Convenience factory for a plain, non-mergeable, non-cancellable task. */
    public static TaskHandle of(long regionId, Object region, Runnable work, String description) {
        return new TaskHandle(regionId, region, work, 0, CancellationToken.never(),
                NO_DEADLINE, null, description);
    }

    // ---------- Identity ----------

    public long taskId() {
        return taskId;
    }

    public long regionId() {
        return regionId;
    }

    /** The opaque region this task belongs to. Never used to mutate game state here. */
    public Object region() {
        return region;
    }

    public int priority() {
        return priority;
    }

    public String description() {
        return description;
    }

    public CancellationToken token() {
        return token;
    }

    public TaskState state() {
        return state.get();
    }

    public Throwable failure() {
        return failure;
    }

    public Object mergeKey() {
        return mergeKey;
    }

    public boolean isMergeable() {
        return mergeKey != null;
    }

    /** Whether this task must be waited on by its submitter. */
    public boolean isWaitedOn() {
        return mergeKey == null;
    }

    public long createdAtNanos() {
        return createdAtNanos;
    }

    public long startedAtNanos() {
        return startedAtNanos;
    }

    public long finishedAtNanos() {
        return finishedAtNanos;
    }

    /** How long this task waited in the queue before it started (or so far). */
    public long queueWaitNanos() {
        long start = startedAtNanos == 0L ? System.nanoTime() : startedAtNanos;
        return start - createdAtNanos;
    }

    /** How long the body ran. */
    public long runNanos() {
        if (startedAtNanos == 0L) return 0L;
        long end = finishedAtNanos == 0L ? System.nanoTime() : finishedAtNanos;
        return end - startedAtNanos;
    }

    public boolean hasDeadline() {
        return deadlineNanos != NO_DEADLINE;
    }

    /** Whether the deadline has already passed. Tasks past deadline are rejected, never run. */
    public boolean isExpired() {
        return hasDeadline() && System.nanoTime() > deadlineNanos;
    }

    public long remainingNanos() {
        return hasDeadline() ? Math.max(0L, deadlineNanos - System.nanoTime()) : Long.MAX_VALUE;
    }

    /** True once the body has started. */
    public boolean hasStarted() {
        return startedAtNanos != 0L;
    }

    public boolean isTerminal() {
        return state.get().isTerminal();
    }

    /** Whether the body actually stopped because of cancellation. */
    public boolean wasCancelled() {
        TaskState s = state.get();
        return s == TaskState.CANCELLED || s == TaskState.TIMED_OUT;
    }

    /** How many other tasks were folded into this one. */
    public int absorbedCount() {
        return absorbedCount.get();
    }

    // ---------- Execution ----------

    /**
     * Run the body on the calling thread.
     * <p>
     * The caller is responsible for making sure this is only ever invoked on a
     * thread that legally owns {@link #region()} — that is the scheduler's job,
     * not this class's.
     *
     * @return {@code true} if the body completed normally
     */
    public boolean runTask() {
        TaskState before = state.get();
        if (before == TaskState.CANCELLED || before == TaskState.TIMED_OUT
                || before == TaskState.MERGED || before == TaskState.COMPLETED) {
            done.countDown();
            return false;
        }
        if (!state.compareAndSet(before, TaskState.RUNNING)) {
            // Someone else got here first (cancel or duplicate dispatch).
            done.countDown();
            return false;
        }

        startedAtNanos = System.nanoTime();
        executingThread = Thread.currentThread();
        try {
            // A task cancelled between queueing and dispatch must not touch the world.
            token.throwIfCancelled("cancelled before execution");
            work.run();
            if (token.isCancelled()) {
                state.set(TaskState.CANCELLED);
            } else {
                state.compareAndSet(TaskState.RUNNING, TaskState.COMPLETED);
            }
        } catch (TaskCancelledException e) {
            state.set(TaskState.CANCELLED);
        } catch (Throwable t) {
            failure = t;
            state.set(TaskState.FAILED);
        } finally {
            finishedAtNanos = System.nanoTime();
            executingThread = null;
            // Only now — after the body has provably left — may a pending
            // cancellation become the published terminal state.
            if (token.isCancelled() && state.get() == TaskState.RUNNING) {
                state.set(TaskState.CANCELLED);
            }
            done.countDown();
        }
        return state.get() == TaskState.COMPLETED;
    }

    // ---------- Cancellation ----------

    /**
     * Ask this task to stop.
     * <p>
     * Three things happen, in this order:
     * <ol>
     *   <li>the cooperative token is cancelled, so the body's own checkpoints fire;</li>
     *   <li>the executing thread (if any) is interrupted, for bodies that do consult it;</li>
     *   <li>if nothing was executing, the terminal state is published immediately —
     *       a task that never started is genuinely gone the moment it is cancelled.</li>
     * </ol>
     *
     * <p>When a body <em>is</em> executing, the state stays {@code RUNNING} for now
     * and {@link #runTask()}'s {@code finally} publishes {@code CANCELLED}. That is
     * what makes the fix.md §5 invariant hold.
     *
     * @return {@code true} if this call transitioned the task to a cancelled state
     */
    public boolean requestCancel(String reason) {
        if (state.get().isTerminal()) return false;

        boolean firstCancel = token.cancel(reason == null ? "cancelled" : reason);

        Thread running = executingThread;
        if (running != null) {
            try {
                running.interrupt();
            } catch (Throwable ignored) {
                // SecurityException / already-dead thread: the token still carries the signal.
            }
        }

        if (running == null) {
            // Never started, or finished between our two reads.
            TaskState current = state.get();
            while (current != TaskState.COMPLETED && !current.isTerminal()) {
                if (state.compareAndSet(current, TaskState.CANCELLED)) {
                    done.countDown();
                    return true;
                }
                current = state.get();
            }
            return false;
        }
        return firstCancel;
    }

    /** Mark this task as folded into an equivalent one. Never runs its own body. */
    boolean markMerged() {
        if (state.compareAndSet(TaskState.CREATED, TaskState.MERGED)) {
            done.countDown();
            return true;
        }
        TaskState current = state.get();
        if (current == TaskState.QUEUED) {
            if (state.compareAndSet(TaskState.QUEUED, TaskState.MERGED)) {
                done.countDown();
                return true;
            }
        }
        return false;
    }

    /** Record that another handle was folded into this one. */
    void absorb(TaskHandle other) {
        absorbedCount.incrementAndGet();
    }

    /** Called by the queue when the handle is accepted, so cancel can tell "queued" from "created". */
    void markQueued() {
        state.compareAndSet(TaskState.CREATED, TaskState.QUEUED);
    }

    /** Called by the timeout sweeper when the deadline has passed. */
    boolean markTimedOut() {
        if (!token.cancel("deadline exceeded")) return false;
        Thread running = executingThread;
        if (running != null) {
            try {
                running.interrupt();
            } catch (Throwable ignored) {
                // Token still carries the signal.
            }
            return true;
        }
        return state.compareAndSet(TaskState.CREATED, TaskState.TIMED_OUT)
                || state.compareAndSet(TaskState.QUEUED, TaskState.TIMED_OUT);
    }

    // ---------- Waiting ----------

    /**
     * Wait for this task to reach a terminal state.
     *
     * @return {@code true} if the task actually completed successfully before the timeout
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        boolean finished = done.await(timeout, unit);
        return finished && state.get() == TaskState.COMPLETED;
    }

    /** Wait indefinitely. Used only by callers that knowingly accept the risk of hanging. */
    public boolean awaitForever() throws InterruptedException {
        done.await();
        return state.get() == TaskState.COMPLETED;
    }

    /** Whether the task has reached a terminal state right now. */
    public boolean isDone() {
        return done.getCount() == 0L;
    }

    @Override
    public String toString() {
        return "TaskHandle[#" + taskId
                + " region=" + RegionIdRegistry.labelOf(regionId)
                + " state=" + state.get()
                + " prio=" + priority
                + " desc=" + description + "]";
    }
}
