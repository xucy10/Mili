package fun.bm.mili.scheduler;

/**
 * Cooperative cancellation token (fix.md §5).
 * <p>
 * {@code Thread.interrupt()} alone cannot guarantee that a Minecraft task stops:
 * the vast majority of server hot loops never consult the interrupt flag, and a
 * task that keeps mutating world state after the scheduler has given up on it is
 * exactly the consistency problem fix.md §4 describes.
 *
 * <p>A token gives the task an explicit, allocation-free checkpoint:
 *
 * <pre>
 *     if (token.isCancelled()) return;      // cheap, no exception
 *     token.throwIfCancelled();             // or unwind via exception
 * </pre>
 *
 * <p>The invariant the whole scheduler is built on:
 *
 * <pre>
 *     CANCELLED  =  scheduler stopped waiting
 *                +  task no longer mutates protected Minecraft state
 * </pre>
 *
 * Nothing publishes {@link TaskState#CANCELLED} until the executing thread has
 * actually observed the token and left {@link TaskHandle#runTask()}.
 */
public final class CancellationToken {

    /** Shared instance for work that is never cancellable, avoiding a needless allocation. */
    private static final CancellationToken NEVER = new CancellationToken();

    private volatile boolean cancelled;
    private volatile String reason = "not-cancelled";
    private volatile long cancelledAtNanos;

    /** A token that can never be cancelled. Safe to share across threads. */
    public static CancellationToken never() {
        return NEVER;
    }

    public static CancellationToken create() {
        return new CancellationToken();
    }

    public static CancellationToken alreadyCancelled(String reason) {
        CancellationToken token = new CancellationToken();
        token.cancel(reason);
        return token;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancel the token.
     *
     * @return {@code true} if this call performed the transition; {@code false}
     * if it was already cancelled. Idempotent, and cheap enough for cancel paths
     * that race.
     */
    public boolean cancel(String reason) {
        if (cancelled) return false;
        this.reason = (reason == null || reason.isEmpty()) ? "cancelled" : reason;
        this.cancelledAtNanos = System.nanoTime();
        this.cancelled = true;
        return true;
    }

    public boolean cancel() {
        return cancel("cancelled");
    }

    public String reason() {
        return reason;
    }

    public long cancelledAtNanos() {
        return cancelledAtNanos;
    }

    /** Cheap checkpoint for hot loops that want to bail out gracefully. */
    public boolean checkpoint() {
        return cancelled;
    }

    /** Unwind the task when cancelled, without paying for a stack trace. */
    public void throwIfCancelled() {
        if (cancelled) throw new TaskCancelledException(reason);
    }

    public void throwIfCancelled(String context) {
        if (cancelled) {
            throw new TaskCancelledException(context == null ? reason : context + ": " + reason);
        }
    }

    /**
     * Reset for reuse from a pool.
     * <p>
     * Only safe when no task still holds a reference to this token — otherwise
     * the task would silently lose its cancellation signal.
     */
    public void reset() {
        this.reason = "not-cancelled";
        this.cancelledAtNanos = 0L;
        this.cancelled = false;
    }

    @Override
    public String toString() {
        return cancelled
                ? "CancellationToken[cancelled, reason=" + reason + "]"
                : "CancellationToken[active]";
    }
}
