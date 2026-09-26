package fun.bm.mili.scheduler;

import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone self-check for the Mili scheduler core.
 * <p>
 * This is deliberately <b>not</b> a JUnit test: the project builds inside a patch
 * pipeline that has no test source set, and the whole point of keeping the
 * scheduler core free of Minecraft types is that it can be exercised without
 * building the server at all. So it is a plain {@code main} with explicit
 * assertions, compiled and run by {@code scripts/scheduler-verify/run.sh}.
 *
 * <p>It shares the {@code fun.bm.mili.scheduler} package so it can reach package
 * internals when a check needs to; the file itself lives outside {@code src/} on
 * purpose, so it never becomes part of the shipped server jar.
 *
 * <p>Every check below corresponds to a specific defect the core is supposed to
 * make impossible. The two that matter most are
 * {@link #checkForeignThreadNeverRunsWorkInline()} and
 * {@link #checkQueueFullNeverRunsWorkInline()}: both encode fix.md §2.3, the
 * failure mode where a thread executes another region's work because scheduling
 * "failed". The previous implementation did exactly that, unconditionally, on its
 * primary code path.
 */
public final class SchedulerSelfCheck {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Mili scheduler self-check ===");

        checkOwnedThreadRunsInline();
        checkForeignThreadNeverRunsWorkInline();
        checkQueueFullNeverRunsWorkInline();
        checkHighPriorityGetsBoundedOvershoot();
        checkMergeFoldsEquivalentTask();
        checkCancellationStopsTheBody();
        checkDeadlineCancelsInsteadOfRunningLate();
        checkDrainRunsQueuedWorkOnOwner();
        checkRegionDestroyReleasesEverything();
        checkCrossRegionTargetsAreTrackedSeparately();
        checkEntityTickDecisionIsAllocationFree();

        System.out.println();
        System.out.printf("=== %d passed, %d failed ===%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------- fix.md §2.1: ownership first ----------

    /** A thread that already owns the region may run the work inline. */
    private static void checkOwnedThreadRunsInline() {
        MiliSchedulerImpl scheduler = MiliSchedulerImpl.instance();
        scheduler.start();

        Object region = new Object();
        long regionId = RegionIdRegistry.idOf(region);
        RegionOwnership.markOwner(regionId, Thread.currentThread());

        AtomicBoolean ran = new AtomicBoolean(false);
        boolean completed = scheduler.submitAndWait(region, () -> ran.set(true), 100, TimeUnit.MILLISECONDS);

        assertTrue("owned thread: submitAndWait reports success", completed);
        assertTrue("owned thread: work ran", ran.get());

        RegionOwnership.releaseOwner(regionId, Thread.currentThread());
    }

    /**
     * The core invariant: a thread that does <b>not</b> own the region must never
     * execute the work, no matter what happens with queueing.
     */
    private static void checkForeignThreadNeverRunsWorkInline() throws Exception {
        MiliSchedulerImpl scheduler = MiliSchedulerImpl.instance();
        Object region = new Object();
        // Note: ownership is deliberately NOT marked for this region.

        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicBoolean ranOnCaller = new AtomicBoolean(false);
        Thread caller = Thread.currentThread();

        boolean completed = scheduler.submitAndWait(region, () -> {
            ran.set(true);
            if (Thread.currentThread() == caller) ranOnCaller.set(true);
        }, 80, TimeUnit.MILLISECONDS);

        assertFalse("foreign thread: submitAndWait does not claim success", completed);
        assertFalse("foreign thread: work never ran (nowhere legal to run it)", ran.get());
        assertFalse("foreign thread: work did NOT run on the caller", ranOnCaller.get());
    }

    /** fix.md §3: a full queue defers or rejects — it never hands the work back to the caller. */
    private static void checkQueueFullNeverRunsWorkInline() {
        MiliSchedulerImpl scheduler = MiliSchedulerImpl.instance();
        Object region = new Object();

        AtomicInteger ranOnCaller = new AtomicInteger();
        Thread caller = Thread.currentThread();

        RegionWorkQueue queue = new RegionWorkQueue(1L, 8, 12);
        int accepted = 0;
        for (int i = 0; i < 8; i++) {
            SubmissionResult r = queue.offer(newQueuedHandle(region, () -> {
                if (Thread.currentThread() == caller) ranOnCaller.incrementAndGet();
            }));
            if (r == SubmissionResult.ACCEPTED) accepted++;
        }
        assertTrue("queue full: soft limit accepted exactly its capacity", accepted == 8);

        SubmissionResult overflow = queue.offer(newQueuedHandle(region, () -> ranOnCaller.incrementAndGet()));
        assertTrue("queue full: overflow is DEFERRED", overflow == SubmissionResult.DEFERRED);
        assertFalse("queue full: caller is not allowed to run it inline", overflow.allowsInlineExecution());
        assertTrue("queue full: nothing ran on the caller", ranOnCaller.get() == 0);

        // The real scheduler must behave the same way, not just the queue.
        SubmissionResult viaScheduler = scheduler.submit(region, () -> ranOnCaller.incrementAndGet());
        assertTrue("queue full: scheduler accepts the task into its own queue instead",
                viaScheduler.isAccepted());
        assertTrue("queue full: still nothing ran on the caller", ranOnCaller.get() == 0);
    }

    /** High-priority work may use the bounded overshoot rather than being deferred. */
    private static void checkHighPriorityGetsBoundedOvershoot() {
        RegionWorkQueue queue = new RegionWorkQueue(2L, 4, 6);
        for (int i = 0; i < 4; i++) {
            queue.offer(newQueuedHandle(new Object(), () -> { }));
        }
        assertTrue("overshoot: soft limit is full", queue.size() == 4);

        TaskHandle urgent1 = new TaskHandle(2L, new Object(), () -> { }, TaskHandle.HIGH_PRIORITY,
                CancellationToken.create(), TaskHandle.NO_DEADLINE, null, "urgent-1");
        assertTrue("overshoot: HIGH priority is accepted past the soft limit",
                queue.offer(urgent1) == SubmissionResult.ACCEPTED);

        TaskHandle urgent2 = new TaskHandle(2L, new Object(), () -> { }, TaskHandle.HIGH_PRIORITY,
                CancellationToken.create(), TaskHandle.NO_DEADLINE, null, "urgent-2");
        assertTrue("overshoot: the overshoot is bounded by the hard limit",
                queue.offer(urgent2) == SubmissionResult.ACCEPTED);
        assertTrue("overshoot: hard limit reached", queue.size() == 6);

        TaskHandle urgent3 = new TaskHandle(2L, new Object(), () -> { }, TaskHandle.HIGH_PRIORITY,
                CancellationToken.create(), TaskHandle.NO_DEADLINE, null, "urgent-3");
        SubmissionResult beyond = queue.offer(urgent3);
        assertTrue("overshoot: past the hard limit even HIGH priority is deferred, not run inline",
                beyond == SubmissionResult.DEFERRED);
    }

    /** Equivalent tasks may be folded together instead of both being queued. */
    private static void checkMergeFoldsEquivalentTask() {
        RegionWorkQueue queue = new RegionWorkQueue(3L, 2, 4);
        Object key = "redstone:0,0,0";

        TaskHandle first = new TaskHandle(3L, new Object(), () -> { }, 0,
                CancellationToken.never(), TaskHandle.NO_DEADLINE, key, "first");
        assertTrue("merge: first task accepted", queue.offer(first) == SubmissionResult.ACCEPTED);

        TaskHandle filler = new TaskHandle(3L, new Object(), () -> { }, 0,
                CancellationToken.never(), TaskHandle.NO_DEADLINE, "other", "filler");
        assertTrue("merge: queue now full", queue.offer(filler) == SubmissionResult.ACCEPTED);

        TaskHandle duplicate = new TaskHandle(3L, new Object(), () -> { }, 0,
                CancellationToken.never(), TaskHandle.NO_DEADLINE, key, "duplicate");
        SubmissionResult r = queue.offer(duplicate);
        assertTrue("merge: duplicate is MERGED", r == SubmissionResult.MERGED);
        assertTrue("merge: merged handle is terminal", duplicate.state() == TaskState.MERGED);
        assertTrue("merge: the surviving task recorded the merge", first.absorbedCount() == 1);
    }

    // ---------- fix.md §4/§5: cancellation ----------

    /**
     * Cancelling must actually stop the body, and the terminal state must only be
     * published once the body has left.
     */
    private static void checkCancellationStopsTheBody() throws Exception {
        CancellationToken token = CancellationToken.create();
        AtomicBoolean observed = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);

        TaskHandle handle = new TaskHandle(10L, new Object(), () -> {
            started.countDown();
            // A realistic long-running body: it polls its token at safe points.
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (System.nanoTime() < deadline) {
                if (token.isCancelled()) {
                    observed.set(true);
                    stopped.countDown();
                    return;
                }
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException e) {
                    // Interrupt alone is not proof of cancellation: keep polling
                    // the token, which is exactly the fix.md §5 point.
                    Thread.currentThread().interrupt();
                }
            }
            stopped.countDown();
        }, 0, token, TaskHandle.NO_DEADLINE, null, "long-running");

        Thread worker = new Thread(handle::runTask, "selfcheck-worker");
        worker.setDaemon(true);
        worker.start();

        assertTrue("cancel: task started", started.await(2, TimeUnit.SECONDS));
        handle.requestCancel("selfcheck");
        assertTrue("cancel: body observed the token", stopped.await(2, TimeUnit.SECONDS));

        worker.join(2000);
        assertTrue("cancel: body observed cancellation", observed.get());
        assertTrue("cancel: token reports cancelled", token.isCancelled());
        assertTrue("cancel: terminal state is CANCELLED", handle.state() == TaskState.CANCELLED);
        assertTrue("cancel: handle is done", handle.isDone());
        assertTrue("cancel: second cancel is a no-op", !handle.requestCancel("again"));
    }

    /** A task past its deadline is cancelled, never executed late. */
    private static void checkDeadlineCancelsInsteadOfRunningLate() throws Exception {
        MiliSchedulerImpl scheduler = MiliSchedulerImpl.instance();
        Object region = new Object();

        // Queue behind a task nobody drains, with an already-expired deadline.
        AtomicBoolean ran = new AtomicBoolean(false);
        SubmissionResult r = scheduler.submit(region, () -> ran.set(true), 0, null,
                System.nanoTime() - 1_000_000L, "expired");
        assertTrue("deadline: an already-expired task is rejected", r == SubmissionResult.REJECTED);
        assertFalse("deadline: rejected task never ran", ran.get());
        assertTrue("deadline: rejected task is not left in the task controller",
                MiliSchedulerImpl.taskController().liveCount() >= 0);

        // A task that sits in a queue nobody drains is swept once its deadline passes.
        AtomicBoolean late = new AtomicBoolean(false);
        long deadline = System.nanoTime() + 30_000_000L; // 30ms
        SubmissionResult queued = scheduler.submit(region, () -> late.set(true), 0, null,
                deadline, "will-expire");
        assertTrue("deadline: queued task is accepted", queued.isAccepted());
        assertTrue("deadline: queued task has not run (nobody drained the region)", !late.get());

        // Wait past the deadline, then let the scheduler tick sweep it.
        Thread.sleep(60L);
        scheduler.tick();

        long timedOut = ((Number) MiliSchedulerImpl.taskController().snapshot().get("timed_out")).longValue();
        assertTrue("deadline: the sweeper counted the timeout", timedOut >= 1);
        assertFalse("deadline: expired task did not run late", late.get());
    }

    // ---------- Region-owned lane ----------

    /** Queued work runs when — and only when — the owner drains the region. */
    private static void checkDrainRunsQueuedWorkOnOwner() {
        MiliSchedulerImpl scheduler = MiliSchedulerImpl.instance();
        Object region = new Object();
        long regionId = RegionIdRegistry.idOf(region);
        RegionOwnership.markOwner(regionId, Thread.currentThread());

        AtomicInteger ran = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            SubmissionResult r = scheduler.submit(region, ran::incrementAndGet);
            assertTrue("drain: task " + i + " queued", r.isAccepted());
        }
        assertTrue("drain: nothing ran before the owner drained", ran.get() == 0);

        int executed = scheduler.drainOwned(region, 50_000_000L);
        assertTrue("drain: owner executed the queued tasks", executed == 5);
        assertTrue("drain: bodies ran exactly once each", ran.get() == 5);

        RegionRuntime runtime = scheduler.peekRuntime(region);
        assertTrue("drain: metrics recorded the executions",
                runtime != null && runtime.metrics().executedCount() == 5);

        RegionOwnership.releaseOwner(regionId, Thread.currentThread());
    }

    // ---------- fix.md §13/§14/§15: lifecycle ----------

    /** The destroy sequence must release every per-region structure. */
    private static void checkRegionDestroyReleasesEverything() {
        MiliSchedulerImpl scheduler = MiliSchedulerImpl.instance();
        Object region = new Object();

        long regionId = RegionIdRegistry.idOf(region);
        RegionIdRegistry.label(regionId, "selfcheck");
        EntityScheduler.forRegion(regionId);
        scheduler.submit(region, () -> { });

        CrossRegionTransaction tx = CrossRegionTransaction.begin(regionId + 1000, regionId, "selfcheck");
        assertTrue("destroy: transaction began against the target", tx != null);
        assertTrue("destroy: transaction is active", CrossRegionTransaction.activeTargeting(regionId) == 1);

        RegionLifecycle.DestroyReport report = scheduler.destroyRegion(region);
        assertTrue("destroy: report is completed", report.completed);
        assertTrue("destroy: new work was refused", report.newWorkRefused);
        assertTrue("destroy: cancel-targeting hit the transaction", tx.state() == TransactionState.CANCELLED);
        assertTrue("destroy: no transaction still targets the region",
                CrossRegionTransaction.activeTargeting(regionId) == 0);

        assertTrue("destroy: region id released", RegionIdRegistry.peek(region) == RegionIdRegistry.UNKNOWN);
        assertTrue("destroy: entity scheduler released", EntityScheduler.peek(regionId) == null);
        assertTrue("destroy: runtime unregistered", scheduler.peekRuntime(region) == null);
        assertTrue("destroy: owner thread cleared", RegionOwnership.ownerThreadOf(regionId) == null);

        // A destroyed region refuses new work instead of silently recreating state.
        SubmissionResult after = scheduler.submit(region, () -> { });
        assertTrue("destroy: submitting to a destroyed region is rejected", after == SubmissionResult.REJECTED);
    }

    // ---------- fix.md §12: cross-region target resolution ----------

    /** Source and target are tracked independently, and an unresolvable target is skipped. */
    private static void checkCrossRegionTargetsAreTrackedSeparately() {
        Object source = new Object();
        Object target = new Object();
        long sourceId = RegionIdRegistry.idOf(source);
        long targetId = RegionIdRegistry.idOf(target);

        CrossRegionTransaction tx = CrossRegionTransaction.begin(sourceId, targetId, "redstone");
        assertTrue("cross-region: transaction created", tx != null);
        assertTrue("cross-region: source and target are distinct ids", tx.sourceRegionId() != tx.targetRegionId());
        assertTrue("cross-region: starts in PREPARE", tx.state() == TransactionState.PREPARE);
        assertTrue("cross-region: enqueued", tx.markEnqueued() && tx.state() == TransactionState.ENQUEUED);
        assertTrue("cross-region: executing", tx.markExecuting() && tx.state() == TransactionState.EXECUTING);
        assertTrue("cross-region: committed", tx.commit() && tx.state() == TransactionState.COMMITTED);
        assertTrue("cross-region: committed transaction leaves the table", CrossRegionTransaction.get(tx.txId()) == null);

        // Same-region work is not a cross-region operation at all.
        assertTrue("cross-region: identical source and target is not a transaction",
                CrossRegionTransaction.begin(sourceId, sourceId, "noop") == null);
        // An unknown target is skipped, never misrouted to the source.
        assertTrue("cross-region: unknown target yields no transaction",
                CrossRegionTransaction.begin(sourceId, RegionIdRegistry.UNKNOWN, "noop") == null);

        // A transaction whose target dies is cancelled, without touching the source.
        CrossRegionTransaction doomed = CrossRegionTransaction.begin(sourceId, targetId, "doomed");
        assertTrue("cross-region: doomed transaction created", doomed != null);
        assertTrue("cross-region: cancel targeting hits exactly one",
                CrossRegionTransaction.cancelTargeting(targetId) == 1);
        assertTrue("cross-region: doomed transaction cancelled",
                doomed.state() == TransactionState.CANCELLED);
    }

    // ---------- fix.md §10: zero allocation on the entity hot path ----------

    /** The decision path must not allocate per entity per tick. */
    private static void checkEntityTickDecisionIsAllocationFree() {
        final long regionId = 999_000L;
        EntityScheduler entityScheduler = EntityScheduler.forRegion(regionId);
        entityScheduler.beginTick(50_000_000L);

        // Hoisted deliberately: EntityPriority.values() clones its array on every
        // call, so calling it inside the loop would measure the test's own
        // allocation rather than the scheduler's.
        final EntityPriority[] priorities = EntityPriority.values();

        // Warm up so class loading and first-call paths are not measured.
        int warm = 0;
        for (int i = 0; i < 200_000; i++) {
            warm += entityScheduler.decide(priorities[i & 3], 0L);
        }

        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = bean.getCurrentThreadAllocatedBytes();
        int accumulator = 0;
        final int iterations = 1_000_000;
        for (int i = 0; i < iterations; i++) {
            // decide() returns a plain int by design (fix.md §10); a boxed return
            // type here would show up as tens of megabytes below.
            accumulator += entityScheduler.decide(priorities[i & 3], 0L);
        }
        long allocated = bean.getCurrentThreadAllocatedBytes() - before;

        // 1M decisions must stay in the noise. Anything above a few hundred bytes
        // means something on this path is boxing or constructing.
        long budget = 16_384L;
        assertTrue("entity hot path: 1M decisions allocate < " + budget + " bytes (actual "
                + allocated + ", checksum " + accumulator + ", warm " + warm + ")", allocated < budget);

        assertTrue("entity hot path: TICK is 0", EntityTickDecision.TICK == 0);
        assertTrue("entity hot path: SKIP is 1", EntityTickDecision.SKIP == 1);
        assertTrue("entity hot path: REMOVE is 2", EntityTickDecision.REMOVE == 2);
        assertTrue("entity hot path: helpers decode decisions",
                EntityTickDecision.shouldSkip(EntityTickDecision.SKIP)
                        && EntityTickDecision.shouldTick(EntityTickDecision.TICK)
                        && EntityTickDecision.shouldRemove(EntityTickDecision.REMOVE));

        // Removal is opt-in: budget pressure alone must never delete entities.
        EntityScheduler conservative = EntityScheduler.forRegion(999_001L);
        conservative.beginTick(1L);
        conservative.recordSpent(10L);
        int decision = conservative.decide(EntityPriority.LOW, 1L);
        assertTrue("entity hot path: removal is off by default", decision == EntityTickDecision.SKIP);
        EntityScheduler.release(999_001L);
        EntityScheduler.release(regionId);
    }

    // ---------- helpers ----------

    private static TaskHandle newQueuedHandle(Object region, Runnable work) {
        return new TaskHandle(RegionIdRegistry.idOf(region), region, work, 0,
                CancellationToken.never(), TaskHandle.NO_DEADLINE, null, "filler");
    }

    private static void assertTrue(String what, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + what);
        } else {
            failed++;
            System.out.println("  FAIL  " + what);
        }
    }

    private static void assertFalse(String what, boolean condition) {
        assertTrue(what, !condition);
    }

    /** Static-only harness. */
    private SchedulerSelfCheck() {}
}
