package fun.bm.mili.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A cross-region operation with an explicit lifecycle (fix.md §12/§21).
 * <p>
 * The previous cross-region design was a fire-and-forget event queue. That made
 * two questions unanswerable, and both of them matter when a region disappears:
 *
 * <ul>
 *   <li><em>What is still pointing at this region?</em> — nothing tracked it, so
 *       a destroyed region could be referenced by events nobody had processed.</li>
 *   <li><em>Did the operation actually happen?</em> — an event that was dropped
 *       on a full queue was indistinguishable from one that was applied.</li>
 * </ul>
 *
 * <p>A transaction answers both. It records source and target by
 * {@link RegionIdRegistry} id, advances through {@link TransactionState}, and is
 * registered in a global table so that
 * {@link #cancelTargeting(long)} can sever every transaction aimed at a region
 * being destroyed.
 *
 * <p>Transactions are not a distributed commit protocol — they do not roll back
 * world state. They are a <em>tracking</em> mechanism: the apply step is a single
 * region-owned mutation, and a transaction that never reaches {@code COMMITTED}
 * is reported as such instead of vanishing.
 */
public final class CrossRegionTransaction {

    /** Default deadline for a cross-region operation to be applied. */
    public static final long DEFAULT_TIMEOUT_NANOS = 1_000_000_000L; // 1 second

    private static final AtomicLong NEXT_ID = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, CrossRegionTransaction> ACTIVE = new ConcurrentHashMap<>();

    private static final AtomicLong committedTotal = new AtomicLong();
    private static final AtomicLong failedTotal = new AtomicLong();
    private static final AtomicLong cancelledTotal = new AtomicLong();
    private static final AtomicLong timedOutTotal = new AtomicLong();

    private final long txId;
    private final long sourceRegionId;
    private final long targetRegionId;
    private final String operation;
    private final long createdAtNanos = System.nanoTime();
    private final long deadlineNanos;

    private volatile TransactionState state = TransactionState.PREPARE;
    private volatile long enqueuedAtNanos;
    private volatile long committedAtNanos;
    private volatile String failure;
    private volatile Throwable cause;

    private CrossRegionTransaction(long sourceRegionId, long targetRegionId,
                                   String operation, long timeoutNanos) {
        this.txId = NEXT_ID.incrementAndGet();
        this.sourceRegionId = sourceRegionId;
        this.targetRegionId = targetRegionId;
        this.operation = operation == null ? "cross-region" : operation;
        this.deadlineNanos = createdAtNanos + Math.max(1L, timeoutNanos);
    }

    /**
     * Begin a transaction targeting {@code targetRegionId}.
     *
     * @return the transaction, or {@code null} if the target is unknown — callers
     *         must treat {@code null} as "skip the operation", never as "use the
     *         current region instead"
     */
    public static CrossRegionTransaction begin(long sourceRegionId, long targetRegionId, String operation) {
        return begin(sourceRegionId, targetRegionId, operation, DEFAULT_TIMEOUT_NANOS);
    }

    public static CrossRegionTransaction begin(long sourceRegionId, long targetRegionId,
                                               String operation, long timeoutNanos) {
        if (targetRegionId == RegionIdRegistry.UNKNOWN) return null;
        if (sourceRegionId == targetRegionId) return null; // not cross-region at all

        CrossRegionTransaction tx = new CrossRegionTransaction(
                sourceRegionId, targetRegionId, operation, timeoutNanos);
        ACTIVE.put(tx.txId, tx);
        return tx;
    }

    // ---------- Lifecycle ----------

    /** The payload has been queued into the target region's work queue. */
    public boolean markEnqueued() {
        if (state != TransactionState.PREPARE) return false;
        enqueuedAtNanos = System.nanoTime();
        state = TransactionState.ENQUEUED;
        return true;
    }

    /** The target region has started applying the payload on its owning thread. */
    public boolean markExecuting() {
        TransactionState current = state;
        if (current != TransactionState.ENQUEUED && current != TransactionState.PREPARE) return false;
        state = TransactionState.EXECUTING;
        return true;
    }

    /** Applied successfully. */
    public boolean commit() {
        TransactionState current = state;
        if (current.isTerminal()) return false;
        committedAtNanos = System.nanoTime();
        state = TransactionState.COMMITTED;
        committedTotal.incrementAndGet();
        ACTIVE.remove(txId);
        return true;
    }

    /** The apply step threw. */
    public boolean fail(Throwable t) {
        if (state.isTerminal()) return false;
        cause = t;
        failure = t == null ? "unknown" : t.getClass().getSimpleName() + ": " + t.getMessage();
        state = TransactionState.FAILED;
        failedTotal.incrementAndGet();
        ACTIVE.remove(txId);
        return true;
    }

    /** The target region disappeared, or the transaction was abandoned. */
    public boolean cancel(String reason) {
        if (state.isTerminal()) return false;
        state = TransactionState.CANCELLED;
        failure = reason == null ? "cancelled" : reason;
        if (reason != null && reason.startsWith("timeout")) {
            timedOutTotal.incrementAndGet();
        } else {
            cancelledTotal.incrementAndGet();
        }
        ACTIVE.remove(txId);
        return true;
    }

    // ---------- Queries ----------

    public long txId() {
        return txId;
    }

    public long sourceRegionId() {
        return sourceRegionId;
    }

    public long targetRegionId() {
        return targetRegionId;
    }

    public String operation() {
        return operation;
    }

    public TransactionState state() {
        return state;
    }

    public String failure() {
        return failure;
    }

    public Throwable cause() {
        return cause;
    }

    public boolean isTerminal() {
        return state.isTerminal();
    }

    public boolean isExpired() {
        return System.nanoTime() > deadlineNanos;
    }

    /** Nanos from creation to commit, or to now if still in flight. */
    public long latencyNanos() {
        long end = committedAtNanos != 0L ? committedAtNanos : System.nanoTime();
        return end - createdAtNanos;
    }

    public long queueLatencyNanos() {
        if (enqueuedAtNanos == 0L) return 0L;
        long end = committedAtNanos != 0L ? committedAtNanos : System.nanoTime();
        return end - enqueuedAtNanos;
    }

    // ---------- Global table ----------

    /**
     * Cancel every in-flight transaction aimed at {@code targetRegionId}.
     * <p>
     * This is step 4 of the region destroy sequence, and it is the reason the
     * transaction table exists at all: without it, work prepared for a region can
     * be applied after that region's lifecycle data has been torn down.
     *
     * @return how many transactions were cancelled
     */
    public static int cancelTargeting(long targetRegionId) {
        int count = 0;
        List<Long> toRemove = new ArrayList<>();
        for (CrossRegionTransaction tx : ACTIVE.values()) {
            if (tx.targetRegionId != targetRegionId) continue;
            if (tx.cancel("target region destroyed")) {
                count++;
            }
            toRemove.add(tx.txId);
        }
        for (Long id : toRemove) {
            ACTIVE.remove(id);
        }
        return count;
    }

    /** Expire transactions that were never applied in time. */
    public static int sweepExpired() {
        int count = 0;
        for (CrossRegionTransaction tx : ACTIVE.values()) {
            if (!tx.isExpired()) continue;
            if (tx.cancel("timeout")) {
                count++;
            }
        }
        return count;
    }

    public static CrossRegionTransaction get(long txId) {
        return ACTIVE.get(txId);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static int activeTargeting(long targetRegionId) {
        int count = 0;
        for (CrossRegionTransaction tx : ACTIVE.values()) {
            if (tx.targetRegionId == targetRegionId) count++;
        }
        return count;
    }

    public static Map<String, Object> stats() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("active", ACTIVE.size());
        out.put("total_started", NEXT_ID.get());
        out.put("committed", committedTotal.get());
        out.put("failed", failedTotal.get());
        out.put("cancelled", cancelledTotal.get());
        out.put("timed_out", timedOutTotal.get());
        return out;
    }

    /** Drop every transaction. Shutdown only. */
    public static void clear() {
        for (CrossRegionTransaction tx : ACTIVE.values()) {
            tx.cancel("shutdown");
        }
        ACTIVE.clear();
    }

    @Override
    public String toString() {
        return "CrossRegionTransaction[#" + txId + " " + operation
                + " " + RegionIdRegistry.labelOf(sourceRegionId)
                + " -> " + RegionIdRegistry.labelOf(targetRegionId)
                + " " + state
                + (failure == null ? "" : " (" + failure + ")") + "]";
    }
}
