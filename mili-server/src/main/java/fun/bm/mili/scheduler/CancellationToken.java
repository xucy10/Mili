package fun.bm.mili.scheduler;

/**
 * Cooperative cancellation token.
 * <p>
 * fix.md §5: {@code Thread.interrupt()} alone cannot guarantee that a Minecraft
 * task stops mutating protected state — most server hot loops never check the
 * interrupt flag.  A token gives the task an explicit, cheap, allocation-free
 * checkpoint it can consult:
 * <pre>
 *     if (token.isCancelled()) return;
 * </pre>
 * <p>
 * Final invariant required by fix.md:
 * <pre>
 *     CANCELLED = scheduler stopped waiting
 *               + task no longer mutates Minecraft state
 * </pre>
 */
public final class CancellationToken {

    /** Thrown by {@link #throwIfCancelled()} when the token has been cancelled. */
    public static final class CancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CancelledException(String message) {
            super(message);
        }
    }

    private volatile boolean cancelled;
    private volatile String reason = "not-cancelled";
    private volatile long cancelledAtNanos;

    public boolean isCancelled() {
        return cancelled;
    }

    /** Cancel without a specific reason. Returns {@code true} if this call caused the cancellation. */
    public boolean cancel() {
        return cancel("cancelled");
    }

    /**
     * Cancel with a reason. Idempotent: only the first caller wins and receives {@code true}.
     *
     * @return {@code true} if this call transitioned the token to cancelled
     */
    public boolean cancel(String reason) {
        if (cancelled) return false;
        this.reason = reason == null ? "cancelled" : reason;
        this.cancelled = true;
        this.cancelledAtNanos = System.nanoTime();
        return true;
    }

    public String reason() {
        return reason;
    }

    public long cancelledAtNanos() {
        return cancelledAtNanos;
    }

    /** Cheap cooperative checkpoint for task hot loops. */
    public void throwIfCancelled() {
        if (cancelled) throw new CancelledException(reason);
    }

    public void throwIfCancelled(String context) {
        if (cancelled) throw new CancelledException(context + ": " + reason);
    }

    /** Reset for pooled/reused tokens. Only safe when no task references the token. */
    public void reset() {
        this.cancelled = false;
        this.reason = "not-cancelled";
        this.cancelledAtNanos = 0L;
    }

    public static CancellationToken never() {
        return new CancellationToken();
    }

    public static CancellationToken alreadyCancelled(String reason) {
        CancellationToken token = new CancellationToken();
        token.cancel(reason);
        return token;
    }

    @Override
    public String toString() {
        return cancelled ? "CancellationToken[cancelled, reason=" + reason + "]" : "CancellationToken[active]";
    }
}
