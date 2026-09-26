package fun.bm.mili.scheduler;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A cross-region operation as an explicit transaction (fix.md §11 and §22.7).
 * <p>
 * Cross-region work must never be "let another thread touch that object". It is a
 * message with a source, a resolved target, a state and an operation:
 * <pre>
 *     Region A -> Transaction -> Region B Queue -> Region B Execute -> Commit -> Region A Result
 * </pre>
 * Redstone, entity damage, teleport, projectiles, portals, block updates and AI can all
 * share this mechanism.
 */
public final class CrossRegionTransaction {

    private static final AtomicLong ID_GEN = new AtomicLong(0);
    private static final Map<Long, CrossRegionTransaction> LIVE = new ConcurrentHashMap<>();
    private static final AtomicLong COMMITTED = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();
    private static final AtomicLong CANCELLED = new AtomicLong();

    private final long id;
    private final long sourceRegionId;
    private final long targetRegionId;
    private final Object source;
    private final Object target;
    private final Runnable operation;
    private final CancellationToken token = CancellationToken.never();
    private final long createdNanos = System.nanoTime();

    private volatile TransactionState state = TransactionState.PREPARE;
    private volatile long committedNanos;

    private CrossRegionTransaction(long sourceRegionId, long targetRegionId,
                                   Object source, Object target, Runnable operation) {
        this.id = ID_GEN.incrementAndGet();
        this.sourceRegionId = sourceRegionId;
        this.targetRegionId = targetRegionId;
        this.source = source;
        this.target = target;
        this.operation = operation;
    }

    /**
     * Create a transaction from a source region to a resolved target region.
     *
     * @param operation the work to run <b>on the target region's owning thread</b>
     */
    public static CrossRegionTransaction create(Object source, Object target, Runnable operation) {
        CrossRegionTransaction tx = new CrossRegionTransaction(
                RegionIdRegistry.idOf(source),
                RegionIdRegistry.idOf(target),
                source, target, operation);
        LIVE.put(tx.id, tx);
        return tx;
    }

    public long id() { return id; }
    public long sourceRegionId() { return sourceRegionId; }
    public long targetRegionId() { return targetRegionId; }
    public Object source() { return source; }
    public Object target() { return target; }
    public CancellationToken token() { return token; }
    public TransactionState state() { return state; }
    public long createdNanos() { return createdNanos; }

    public boolean isCrossRegion() {
        return sourceRegionId != targetRegionId;
    }

    /**
     * Hand the transaction to the target region's queue.
     *
     * @return {@link SubmissionResult#ACCEPTED} when queued, {@code REJECTED} otherwise
     */
    public SubmissionResult enqueue() {
        if (state != TransactionState.PREPARE) return SubmissionResult.REJECTED;
        if (target == null) {
            fail("null-target");
            return SubmissionResult.REJECTED;
        }
        if (!isCrossRegion()) {
            // Same region: nothing to hand over.
            commit();
            return SubmissionResult.ACCEPTED;
        }
        SubmissionResult result = MiliSchedulerImpl.instance().trySubmit(target, this::execute);
        if (result == SubmissionResult.ACCEPTED || result == SubmissionResult.MERGED) {
            state = TransactionState.ENQUEUED;
            return result;
        }
        // Rejected or deferred past its deadline: the transaction must not be left
        // dangling in PREPARE (fix.md §9: never leave unfinished work behind).
        fail("queue-rejected");
        return SubmissionResult.REJECTED;
    }

    /** Run the operation. Only legal on the target region's owning thread. */
    public void execute() {
        if (state == TransactionState.CANCELLED || state == TransactionState.COMMITTED) return;
        state = TransactionState.EXECUTING;
        try {
            if (token.isCancelled()) {
                cancel("cancelled-before-execution");
                return;
            }
            operation.run();
            commit();
        } catch (Throwable t) {
            fail(t.getClass().getSimpleName());
        }
    }

    public void commit() {
        if (state.isTerminal()) return;
        state = TransactionState.COMMITTED;
        committedNanos = System.nanoTime();
        COMMITTED.incrementAndGet();
        LIVE.remove(id);
    }

    public void fail(String reason) {
        if (state.isTerminal()) return;
        state = TransactionState.FAILED;
        FAILED.incrementAndGet();
        LIVE.remove(id);
    }

    public void cancel(String reason) {
        if (state.isTerminal()) return;
        token.cancel(reason);
        state = TransactionState.CANCELLED;
        CANCELLED.incrementAndGet();
        LIVE.remove(id);
    }

    // ---------- registry ----------

    public static CrossRegionTransaction get(long id) { return LIVE.get(id); }

    public static Collection<CrossRegionTransaction> live() { return LIVE.values(); }

    public static int liveCount() { return LIVE.size(); }

    /** Cancel every transaction targeting a region that is going away (fix.md §9). */
    public static int cancelTargeting(long regionId) {
        int cancelled = 0;
        for (CrossRegionTransaction tx : LIVE.values()) {
            if (tx.targetRegionId == regionId) {
                tx.cancel("target-region-destroyed");
                cancelled++;
            }
        }
        return cancelled;
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("live", LIVE.size());
        stats.put("committed", COMMITTED.get());
        stats.put("failed", FAILED.get());
        stats.put("cancelled", CANCELLED.get());
        return stats;
    }
}
