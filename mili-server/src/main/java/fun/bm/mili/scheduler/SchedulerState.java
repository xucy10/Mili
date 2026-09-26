package fun.bm.mili.scheduler;

/** Runtime state of a {@link MiliScheduler}. */
public enum SchedulerState {
    /** Never started. */
    NEW,
    /** Accepting and dispatching work. */
    RUNNING,
    /** Temporarily refusing new work (backpressure / shutdown in progress). */
    PAUSED,
    /** Terminated; all submissions are rejected. */
    SHUTDOWN;

    public boolean acceptsNewWork() {
        return this == RUNNING;
    }
}
