package fun.bm.mili.scheduler;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

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

    /**
     * Mili start - fix: every state change used to be a plain field write guarded by a
     * non-atomic {@code if (state.isTerminal())} check, so two racing writers could both
     * pass the check and one could overwrite the other's terminal state.  A transaction
     * used to be able to end up {@code COMMITTED} after it had already failed.
     */
    private static final AtomicReferenceFieldUpdater<CrossRegionTransaction, TransactionState> STATE =
            AtomicReferenceFieldUpdater.newUpdater(
                    CrossRegionTransaction.class, TransactionState.class, "state");

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
     * <p>
     * Mili start - fix: the mapping from {@link SubmissionResult} to the transaction state
     * is now explicit, because the two enums have different granularity
     * (fix.md §3 vs §11):
     * <pre>
     *     ACCEPTED -> ENQUEUED   (will be executed by the target region)
     *     MERGED   -> ENQUEUED   (folded into an equivalent queued task)
     *     DEFERRED -> DEFERRED   (still queued for a later round: MUST stay live)
     *     REJECTED -> FAILED     (dropped for real)
     * </pre>
     * Treating {@code DEFERRED} as a failure used to kill a transaction whose work the
     * scheduler had deliberately kept alive in its retry queue.
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
        switch (result) {
            case ACCEPTED, MERGED -> {
                transition(TransactionState.PREPARE, TransactionState.ENQUEUED);
                return result;
            }
            case DEFERRED -> {
                transition(TransactionState.PREPARE, TransactionState.DEFERRED);
                return SubmissionResult.DEFERRED;
            }
            default -> {
                // Rejected (past deadline, region gone, ...): the work is genuinely gone
                // (fix.md §9: never leave unfinished work behind).
                fail("queue-rejected");
                return SubmissionResult.REJECTED;
            }
        }
    }

    /**
     * Run the operation. Only legal on the target region's owning thread.
     * <p>
     * Mili start - fix: the guard used to test {@code CANCELLED} and {@code COMMITTED} by
     * name and therefore missed {@code FAILED}, so a transaction that had already been
     * failed was still allowed to run its operation.  It now converges on
     * {@link TransactionState#isTerminal()} and claims {@code EXECUTING} with a CAS, so the
     * operation can run at most once.
     */
    public void execute() {
        for (;;) {
            TransactionState current = state;
            if (current.isTerminal() || current == TransactionState.EXECUTING) return;
            if (STATE.compareAndSet(this, current, TransactionState.EXECUTING)) break;
        }
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
        if (!transitionToTerminal(TransactionState.COMMITTED)) return;
        committedNanos = System.nanoTime();
        COMMITTED.incrementAndGet();
        LIVE.remove(id);
    }

    public void fail(String reason) {
        if (!transitionToTerminal(TransactionState.FAILED)) return;
        FAILED.incrementAndGet();
        LIVE.remove(id);
    }

    public void cancel(String reason) {
        if (state.isTerminal()) return;
        token.cancel(reason);
        if (!transitionToTerminal(TransactionState.CANCELLED)) return;
        CANCELLED.incrementAndGet();
        LIVE.remove(id);
    }

    /**
     * CAS a non-terminal state to {@code next}.
     *
     * @return {@code false} if the transaction was already terminal
     */
    private boolean transitionToTerminal(TransactionState next) {
        for (;;) {
            TransactionState current = state;
            if (current.isTerminal()) return false;
            if (STATE.compareAndSet(this, current, next)) return true;
        }
    }

    /** CAS a specific non-terminal state to {@code next}. */
    private boolean transition(TransactionState expected, TransactionState next) {
        return STATE.compareAndSet(this, expected, next);
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

    /**
     * Mili start - fix: a transaction whose submission was {@code DEFERRED} stays live
     * until the scheduler actually drains it.  Nothing else removes it from {@link #LIVE},
     * so a target region that never ticks would leak it forever.  This sweep bounds the
     * leak and makes the drop traceable instead of silent.
     *
     * @return number of transactions cancelled
     */
    public static int sweepStale(long maxAgeNanos) {
        if (maxAgeNanos <= 0L) return 0;
        long now = System.nanoTime();
        int swept = 0;
        for (CrossRegionTransaction tx : LIVE.values()) {
            if (!tx.state.isTerminal() && now - tx.createdNanos > maxAgeNanos) {
                tx.cancel("stale-transaction");
                swept++;
            }
        }
        return swept;
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
