package fun.bm.mili.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-region FIFO work queue.
 * <p>
 * fix.md §17: work is queued per region and taken by whichever worker legitimately
 * owns that region — the worker belongs to the scheduler, not to a region.
 * <p>
 * fix.md §3: the queue has a hard capacity. When it is full the queue returns
 * {@link SubmissionResult#DEFERRED} / {@link SubmissionResult#REJECTED}; it never
 * asks the caller to run the work itself.
 */
public final class RegionWorkQueue {

    private final long regionId;
    private final ConcurrentLinkedQueue<TaskHandle> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger depth = new AtomicInteger(0);
    private volatile int maxCapacity;

    public RegionWorkQueue(long regionId, int maxCapacity) {
        this.regionId = regionId;
        this.maxCapacity = Math.max(1, maxCapacity);
    }

    public long regionId() {
        return regionId;
    }

    public int depth() {
        return depth.get();
    }

    public int maxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int capacity) {
        this.maxCapacity = Math.max(1, capacity);
    }

    public boolean isFull() {
        return depth.get() >= maxCapacity;
    }

    /**
     * Offer work to the region queue.
     * <p>
     * A task already past its deadline is rejected outright — enqueueing work that can
     * only be cancelled a moment later just wastes capacity.
     */
    public SubmissionResult offer(TaskHandle handle) {
        if (handle == null) return SubmissionResult.REJECTED;
        if (handle.isPastDeadline(System.nanoTime())) {
            handle.markMerged(); // terminal, no capacity consumed
            return SubmissionResult.REJECTED;
        }
        if (isFull()) {
            return SubmissionResult.DEFERRED;
        }
        queue.add(handle);
        depth.incrementAndGet();
        return SubmissionResult.ACCEPTED;
    }

    public TaskHandle poll() {
        TaskHandle handle = queue.poll();
        if (handle != null) depth.decrementAndGet();
        return handle;
    }

    public TaskHandle peek() {
        return queue.peek();
    }

    /**
     * Shallow copy used by the merge path to find an equivalent queued task.
     * Allocation only happens on the queue-full backpressure branch, never on the
     * normal admission path.
     */
    public List<TaskHandle> snapshotForMerge() {
        return new ArrayList<>(queue);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Drop everything still queued, cancelling each handle. Returns how many were dropped. */
    public int clearAndCancel(String reason) {
        int dropped = 0;
        TaskHandle handle;
        while ((handle = queue.poll()) != null) {
            depth.decrementAndGet();
            handle.cancel(reason);
            dropped++;
        }
        depth.set(0);
        return dropped;
    }
}
