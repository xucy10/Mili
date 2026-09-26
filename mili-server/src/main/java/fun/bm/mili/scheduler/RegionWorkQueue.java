package fun.bm.mili.scheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded work queue for one region.
 * <p>
 * This class is the concrete answer to fix.md §3. There is no "run it inline if
 * we cannot queue it" branch anywhere in here, because that branch is precisely
 * the bug being fixed. When the soft limit is reached the queue decides between
 * four documented outcomes and returns that decision to the caller:
 *
 * <pre>
 *   offer(handle)
 *      |
 *      +-- region closed / task expired      -> REJECTED  (dropped, nothing runs)
 *      +-- below soft limit                  -> ACCEPTED
 *      +-- mergeable + equivalent queued     -> MERGED    (folded in, nothing new runs)
 *      +-- HIGH priority + below hard limit  -> ACCEPTED  (bounded overshoot)
 *      +-- otherwise                         -> DEFERRED  (caller retries later)
 * </pre>
 *
 * <p><b>Why soft and hard limits:</b> a hard limit alone turns a load spike into
 * a cliff — everything above the line is dropped, including the redstone update
 * that a player is waiting on. A small, bounded overshoot reserved for
 * high-priority work absorbs the spike without giving up the bound.
 *
 * <p><b>Why a lock:</b> the queue is short (tens of entries, not thousands) and
 * is touched once per submission and once per drain. A linear scan under a lock
 * beats a heap plus its comparator indirection at this size, and it lets
 * {@link #offer} do the merge scan and the insert as one atomic step.
 */
public final class RegionWorkQueue {

    /** Default soft limit. */
    public static final int DEFAULT_SOFT_CAPACITY = 256;

    /** Default hard limit — the bounded overshoot for high-priority work. */
    public static final int DEFAULT_HARD_CAPACITY = 384;

    private final long regionId;
    private final int softCapacity;
    private final int hardCapacity;

    private final ArrayDeque<TaskHandle> queue;
    private final Object lock = new Object();

    private volatile boolean closed;

    private long acceptedTotal;
    private long mergedTotal;
    private long deferredTotal;
    private long rejectedTotal;

    public RegionWorkQueue(long regionId) {
        this(regionId, DEFAULT_SOFT_CAPACITY, DEFAULT_HARD_CAPACITY);
    }

    public RegionWorkQueue(long regionId, int softCapacity, int hardCapacity) {
        this.regionId = regionId;
        this.softCapacity = Math.max(1, softCapacity);
        this.hardCapacity = Math.max(this.softCapacity, hardCapacity);
        this.queue = new ArrayDeque<>(Math.min(this.softCapacity, 128));
    }

    public long regionId() {
        return regionId;
    }

    public boolean isClosed() {
        return closed;
    }

    public int size() {
        synchronized (lock) {
            return queue.size();
        }
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return queue.isEmpty();
        }
    }

    public int softCapacity() {
        return softCapacity;
    }

    public int hardCapacity() {
        return hardCapacity;
    }

    public boolean isOverSoftLimit() {
        synchronized (lock) {
            return queue.size() >= softCapacity;
        }
    }

    /**
     * Offer a task to this queue.
     * <p>
     * The returned {@link SubmissionResult} is authoritative: a {@code DEFERRED}
     * or {@code REJECTED} result means the task is <b>not</b> owned by the queue
     * and must not be executed by the caller either
     * ({@link SubmissionResult#allowsInlineExecution()} is always {@code false}).
     */
    public SubmissionResult offer(TaskHandle handle) {
        if (handle == null) return SubmissionResult.REJECTED;

        synchronized (lock) {
            if (closed) {
                rejectedTotal++;
                return SubmissionResult.REJECTED;
            }
            if (handle.isExpired() || handle.isTerminal()) {
                rejectedTotal++;
                return SubmissionResult.REJECTED;
            }

            int size = queue.size();

            if (size < softCapacity) {
                handle.markQueued();
                queue.addLast(handle);
                acceptedTotal++;
                return SubmissionResult.ACCEPTED;
            }

            // Full. Before dropping anything, try to fold this task into an
            // equivalent one that is already waiting.
            if (handle.isMergeable()) {
                for (TaskHandle queued : queue) {
                    if (queued.isTerminal()) continue;
                    if (queued.mergeKey() != null && queued.mergeKey().equals(handle.mergeKey())) {
                        queued.absorb(handle);
                        handle.markMerged();
                        mergedTotal++;
                        return SubmissionResult.MERGED;
                    }
                }
            }

            // Bounded overshoot, reserved for work someone is actively waiting on.
            if (size < hardCapacity && handle.priority() >= TaskHandle.HIGH_PRIORITY) {
                handle.markQueued();
                queue.addLast(handle);
                acceptedTotal++;
                return SubmissionResult.ACCEPTED;
            }

            // Still within its deadline (or has none): tell the caller to retry.
            if (!handle.isExpired()) {
                deferredTotal++;
                return SubmissionResult.DEFERRED;
            }

            rejectedTotal++;
            return SubmissionResult.REJECTED;
        }
    }

    /**
     * Remove and return the most urgent task.
     * <p>
     * Highest priority wins; ties go to the oldest task, so a steady stream of
     * medium-priority work can never starve an older peer.
     */
    public TaskHandle poll() {
        synchronized (lock) {
            if (queue.isEmpty()) return null;

            TaskHandle best = null;
            for (TaskHandle candidate : queue) {
                if (candidate.isTerminal()) {
                    // Cancelled while it sat in the queue: drop it and try again.
                    queue.remove(candidate);
                    return queue.isEmpty() ? null : pollLocked();
                }
                if (best == null) {
                    best = candidate;
                    continue;
                }
                if (candidate.priority() > best.priority()) {
                    best = candidate;
                }
                // Ties: ArrayDeque iterates head -> tail, i.e. oldest first, so
                // keeping the incumbent preserves FIFO.
            }
            if (best != null) {
                queue.remove(best);
            }
            return best;
        }
    }

    /** Assumes {@code lock} is held. */
    private TaskHandle pollLocked() {
        while (!queue.isEmpty()) {
            TaskHandle head = queue.pollFirst();
            if (head != null && !head.isTerminal()) {
                return head;
            }
        }
        return null;
    }

    /**
     * Remove up to {@code max} tasks, in priority order.
     * <p>
     * Returns a list rather than a bulk copy so the caller can interleave budget
     * checks between tasks and stop the moment the region's budget is spent.
     */
    public List<TaskHandle> drain(int max) {
        if (max <= 0) return List.of();
        List<TaskHandle> out = new ArrayList<>(Math.min(max, 32));
        for (int i = 0; i < max; i++) {
            TaskHandle next = poll();
            if (next == null) break;
            out.add(next);
        }
        return out;
    }

    /** Number of tasks in the queue that have already been cancelled but not yet reaped. */
    public int staleCount() {
        synchronized (lock) {
            int stale = 0;
            for (TaskHandle h : queue) {
                if (h.isTerminal()) stale++;
            }
            return stale;
        }
    }

    /**
     * Stop accepting work.
     *
     * @return the tasks that were still queued, so the caller can cancel them
     * explicitly instead of leaking their waiters
     */
    public List<TaskHandle> close() {
        synchronized (lock) {
            closed = true;
            List<TaskHandle> remainder = new ArrayList<>(queue);
            queue.clear();
            return remainder;
        }
    }

    public long acceptedTotal() {
        synchronized (lock) {
            return acceptedTotal;
        }
    }

    public long mergedTotal() {
        synchronized (lock) {
            return mergedTotal;
        }
    }

    public long deferredTotal() {
        synchronized (lock) {
            return deferredTotal;
        }
    }

    public long rejectedTotal() {
        synchronized (lock) {
            return rejectedTotal;
        }
    }

    @Override
    public String toString() {
        return "RegionWorkQueue[region=" + RegionIdRegistry.labelOf(regionId)
                + " size=" + size() + "/" + softCapacity
                + (closed ? " CLOSED" : "") + "]";
    }
}
