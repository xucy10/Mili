package fun.bm.mili.scheduler;

/**
 * Lifecycle state of the Mili scheduler.
 * <p>
 * fix.md §18: the scheduler exposes its state so every subsystem can ask
 * "may I still submit work?" instead of discovering it through a rejected task.
 * <p>
 * {@link #acceptsSubmissions()} is the only safe gate: while {@link #DRAINING}
 * new work is refused but work already queued is still executed, which is what
 * makes the region destroy order in fix.md §13 ("stop entering, then drain the
 * backlog, then delete lifecycle data") actually enforceable.
 */
public enum SchedulerState {

    /** Not started, or fully shut down. No worker exists. */
    STOPPED,
    /** Starting up: worker pool and runtimes are being created. */
    STARTING,
    /** Normal operation. */
    RUNNING,
    /** Refusing new work, still draining what is already queued. */
    DRAINING,
    /** Everything stopped. */
    SHUTDOWN;

    public boolean acceptsSubmissions() {
        return this == RUNNING;
    }

    public boolean isTerminal() {
        return this == SHUTDOWN;
    }
}
