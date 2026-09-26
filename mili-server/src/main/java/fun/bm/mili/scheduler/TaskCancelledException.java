package fun.bm.mili.scheduler;

/**
 * Thrown by {@link CancellationToken#throwIfCancelled()} when a task runs past
 * its cancellation point.
 * <p>
 * It is a {@link RuntimeException} on purpose: server hot loops are not declared
 * to throw, and the intent is for the check to be cheap enough to sprinkle into
 * existing code without changing signatures. Callers that must not abort (for
 * example a {@code finally} block finishing a data structure) should simply not
 * call {@code throwIfCancelled()} there and use
 * {@link CancellationToken#isCancelled()} instead.
 */
public class TaskCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reason;

    public TaskCancelledException(String reason) {
        super("Mili task cancelled: " + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }

    /**
     * Cancellation is a normal control-flow outcome, not a crash: it must never
     * be reported as an unexpected failure with a stack trace.
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
