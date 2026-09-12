package fun.bm.mili.utils.dagschedule;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Describes a single task as a node in a Directed Acyclic Graph used for
 * dependency-aware parallel tick scheduling.
 *
 * <p>Tasks form a DAG where an edge A → B means "B depends on A" (A must
 * finish before B can start). The {@link DAGScheduler} topologically sorts the
 * graph and schedules tasks so independent branches run concurrently while
 * respecting all dependency constraints.</p>
 */
public final class DAGTask {

    /**
     * Scheduling status of a single DAG task.
     */
    public enum Status {
        PENDING,
        READY,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    final long taskId;
    final Object scheduleRef;
    final Runnable work;
    final Set<Long> dependencies;
    final java.util.Set<Long> dependents;
    final AtomicInteger status = new AtomicInteger(Status.PENDING.ordinal());
    final AtomicInteger unresolvedDeps;
    volatile double priority;
    final long enqueueNanos;
    volatile long completionNanos = 0L;
    volatile Throwable failureCause;

    public DAGTask(long taskId, Object scheduleRef, Runnable work,
                   Set<Long> dependencies, double priority) {
        this.taskId = taskId;
        this.scheduleRef = scheduleRef;
        this.work = work;
        this.dependencies = Set.copyOf(dependencies);
        this.dependents = new java.util.HashSet<>();
        this.priority = priority;
        this.enqueueNanos = System.nanoTime();
        this.unresolvedDeps = new AtomicInteger(this.dependencies.size());
    }

    boolean onDependencyCompleted() {
        return unresolvedDeps.decrementAndGet() == 0;
    }

    boolean tryTransition(Status expected, Status target) {
        return status.compareAndSet(expected.ordinal(), target.ordinal());
    }

    void forceStatus(Status target) {
        status.set(target.ordinal());
    }

    @NotNull
    public Status getStatus() {
        return Status.values()[status.get()];
    }

    public boolean isTerminal() {
        Status s = getStatus();
        return s == Status.COMPLETED || s == Status.FAILED || s == Status.CANCELLED;
    }

    public boolean isReadyForDispatch() {
        return getStatus() == Status.READY;
    }

    @Override
    public String toString() {
        return "DAGTask{id=" + taskId +
                ", deps=" + dependencies.size() +
                ", rem=" + unresolvedDeps.get() +
                ", status=" + getStatus() +
                ", ref=" + scheduleRef + '}';
    }
}
