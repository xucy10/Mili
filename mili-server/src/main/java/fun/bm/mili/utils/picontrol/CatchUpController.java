package fun.bm.mili.utils.picontrol;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * PI (Proportional-Integral) controller for tick catch-up rate limiting.
 *
 * <p>Unlike naive catch-up logic that directly targets TPS (which creates a
 * positive feedback loop: lag → catch-up → more load → more lag), this
 * controller regulates the <em>rate</em> at which we attempt to catch up by
 * controlling these upstream indicators:</p>
 *
 * <ul>
 *   <li><b>Tick Duration</b> — how long a single tick is allowed to take</li>
 *   <li><b>Queue Depth</b> — number of pending tasks in the dispatch queue</li>
 *   <li><b>Worker Utilization</b> — ratio of busy workers to total workers</li>
 * </ul>
 *
 * <p>The controller applies hard limits on three independent budgets. Catch-up
 * is only allowed when <em>all three</em> budgets have remaining headroom.
 * This prevents the "越追赶越卡" anti-pattern where aggressive catch-up
 * increases load and creates worse lag.</p>
 *
 * <h3>Control loop:</h3>
 * <pre>
 *   error = target_actual - measured_value
 *   integral += error * dt
 *   output = Kp * error + Ki * integral
 *   output = clamp(output, 0, maxBudget)
 * </pre>
 *
 * <p>Terminology:</p>
 * <ul>
 *   <li><b>Error</b> — how far we are from the target (lag in ticks)</li>
 *   <li><b>Integral</b> — persistent lag that wasn't corrected (wind-up risk)</li>
 *   <li><b>Kp</b> — proportional gain (immediate response to lag)</li>
 *   <li><b>Ki</b> — integral gain (long-term correction)</li>
 * </ul>
 */
public final class CatchUpController {

    private CatchUpController() {}

    /**
     * Controller configuration (tunable via external config).
     */
    public static final class Config {
        // PI gains
        public static double KP = 0.3;
        public static double KI = 0.05;

        // Hard limits — these are ceilings the controller CANNOT exceed
        public static int MAX_CATCHUP_TICKS = 20;
        public static long MAX_TICK_DURATION_BUDGET_NS = 40_000_000L; // 40ms per tick
        /** Fraction (0..1] of the CPU budget above which catch-up is denied.
         *  Independent from {@code MAX_WORKER_UTILIZATION} — conflating the
         *  CPU-budget threshold with the worker-utilization limit was a bug. */
        public static double CPU_BUDGET_EXHAUST_FRACTION = 0.9;
        public static int MAX_QUEUE_DEPTH = 500;
        public static double MAX_WORKER_UTILIZATION = 0.9; // 90%
        /** Fraction (0..1] of the queue budget above which catch-up is denied. */
        public static double QUEUE_BUDGET_EXHAUST_FRACTION = 0.8;

        // Integral anti-windup
        public static double INTEGRAL_DECAY = 0.95;
        public static double MAX_INTEGRAL = 100.0;

        private Config() {}
    }

    /**
     * Snapshot of the controller state for diagnostics.
     */
    public record State(
            double proportionalTerm,
            double integralTerm,
            double output,
            long allowedCatchup,
            double cpuBudgetRemaining,
            double queueBudgetRemaining,
            double workerBudgetRemaining,
            boolean cpuBudgetExhausted,
            boolean queueBudgetExhausted,
            boolean workerBudgetExhausted
    ) {}

    /**
     * Observation inputs for the controller tick.
     */
    public record Observation(
            /** Measured average tick duration in nanoseconds (last window). */
            long avgTickDurationNanos,
            /** Current pending task queue size. */
            int queueDepth,
            /** Current active (busy) worker count. */
            int activeWorkers,
            /** Total worker count. */
            int totalWorkers,
            /** How many ticks behind we are. */
            long ticksBehind,
            /** Delta-time in seconds since last observation. */
            double dt
    ) {}

    // ---------- State ----------

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean enabled = new AtomicBoolean(true);

    /** Integral accumulator (persistent error memory). */
    private static volatile double integral = 0.0;

    /** Monotonic now-for-computation to avoid System.nanoTime() drift in control loop. */
    private static volatile double lastProportional = 0.0;
    private static volatile double lastOutput = 0.0;
    private static volatile long lastAllowedCatchup = 1;

    /** Statistics for monitoring. */
    private static final LongAdder statTotalComputations = new LongAdder();
    private static final LongAdder statBudgetLimitedEvents = new LongAdder();

    /** ---------- Lifecycle ---------- */

    public static void init() {
        if (initialized.compareAndSet(false, true)) {
            reset();
            LogUtils.getLogger().info("[Mili] CatchUpController initialized (Kp={}, Ki={}, maxCatchup={})",
                    Config.KP, Config.KI, Config.MAX_CATCHUP_TICKS);
        }
    }

    public static void shutdown() {
        if (initialized.compareAndSet(true, false)) {
            reset();
            LogUtils.getLogger().info("[Mili] CatchUpController shutdown");
        }
    }

    public static void reset() {
        integral = 0.0;
        lastProportional = 0.0;
        lastOutput = 0.0;
        lastAllowedCatchup = 1;
        statTotalComputations.reset();
        statBudgetLimitedEvents.reset();
    }

    // ---------- Control Loop ----------

    /**
     * Given a fresh observation, compute how many catch-up ticks are safe.
     *
     * @param obs the current tick observation
     * @return the recommended catch-up tick count (>= 1)
     */
    public static long computeAllowedCatchup(@NotNull Observation obs) {
        statTotalComputations.increment();

        if (!enabled.get()) {
            return Config.MAX_CATCHUP_TICKS;
        }

        // --- Budget 1: CPU / Tick Duration ---
        // If average tick already exceeds budget, deny catch-up
        double cpuBudgetFraction = 1.0;
        if (Config.MAX_TICK_DURATION_BUDGET_NS > 0) {
            cpuBudgetFraction = Math.min(1.0,
                    (double) obs.avgTickDurationNanos() / Config.MAX_TICK_DURATION_BUDGET_NS);
        }
        boolean cpuBudgetExhausted = cpuBudgetFraction >= Config.CPU_BUDGET_EXHAUST_FRACTION;

        // --- Budget 2: Queue Depth ---
        double queueBudgetFraction = 1.0;
        if (Config.MAX_QUEUE_DEPTH > 0) {
            queueBudgetFraction = Math.min(1.0,
                    (double) obs.queueDepth() / Config.MAX_QUEUE_DEPTH);
        }
        boolean queueBudgetExhausted = queueBudgetFraction >= Config.QUEUE_BUDGET_EXHAUST_FRACTION;

        // --- Budget 3: Worker Utilization ---
        double workerUtilization = 0.0;
        if (obs.totalWorkers() > 0) {
            workerUtilization = (double) obs.activeWorkers() / obs.totalWorkers();
        }
        boolean workerBudgetExhausted = workerUtilization >= Config.MAX_WORKER_UTILIZATION;

        // --- PI computation on allowed response ---
        // Error: how many ticks is the server behind?
        double error = Math.max(0, obs.ticksBehind());

        // Integral with anti-windup decay
        integral = integral * Config.INTEGRAL_DECAY + error * obs.dt();
        integral = clamp(integral, -Config.MAX_INTEGRAL, Config.MAX_INTEGRAL);

        double proportional = Config.KP * error;
        double integralTerm = Config.KI * integral;
        double output = proportional + integralTerm;

        // Clamp to [0, maxCatchup]
        output = clamp(output, 0, Config.MAX_CATCHUP_TICKS);

        // --- Apply budget headrooms ---
        // Each budget that is not fully used gives a multiplier (0..1]
        double cpuHeadroom = cpuBudgetExhausted ? 0.0 : (1.0 - cpuBudgetFraction);
        double queueHeadroom = queueBudgetExhausted ? 0.0 : (1.0 - queueBudgetFraction);
        double workerHeadroom = workerBudgetExhausted ? 0.0 : (1.0 - workerUtilization);

        // Overall headroom is the minimum of all three (most constrained budget wins)
        double overallHeadroom = Math.min(Math.min(cpuHeadroom, queueHeadroom), workerHeadroom);

        long allowedCatchup = Math.max(1, Math.round(output * overallHeadroom));

        if (cpuBudgetExhausted || queueBudgetExhausted || workerBudgetExhausted) {
            statBudgetLimitedEvents.increment();
        }

        // Update last values for diagnostics
        lastProportional = proportional;
        lastIntegral = integral;
        lastOutput = output;
        lastAllowedCatchup = allowedCatchup;
        lastCpuBudgetFraction = cpuBudgetFraction;
        lastQueueBudgetFraction = queueBudgetFraction;
        lastWorkerUtilization = workerUtilization;

        return allowedCatchup;
    }

    // ---------- Diagnostics Fields (volatile for external reads) ----------
    private static volatile double lastIntegral = 0.0;
    private static volatile double lastCpuBudgetFraction = 0.0;
    private static volatile double lastQueueBudgetFraction = 0.0;
    private static volatile double lastWorkerUtilization = 0.0;

    public static State getState() {
        return new State(
                lastProportional,
                lastIntegral,
                lastOutput,
                lastAllowedCatchup,
                1.0 - lastCpuBudgetFraction,
                1.0 - lastQueueBudgetFraction,
                1.0 - lastWorkerUtilization,
                lastCpuBudgetFraction >= Config.CPU_BUDGET_EXHAUST_FRACTION,
                lastQueueBudgetFraction >= Config.QUEUE_BUDGET_EXHAUST_FRACTION,
                lastWorkerUtilization >= Config.MAX_WORKER_UTILIZATION
        );
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("initialized", initialized.get());
        stats.put("enabled", enabled.get());
        stats.put("integral", integral);
        stats.put("last_proportional", lastProportional);
        stats.put("last_integral", lastIntegral);
        stats.put("last_output", lastOutput);
        stats.put("last_allowed_catchup", lastAllowedCatchup);
        stats.put("total_computations", statTotalComputations.sum());
        stats.put("budget_limited_events", statBudgetLimitedEvents.sum());
        stats.put("cpu_budget_remaining", 1.0 - lastCpuBudgetFraction);
        stats.put("queue_budget_remaining", 1.0 - lastQueueBudgetFraction);
        stats.put("worker_budget_remaining", 1.0 - lastWorkerUtilization);
        return stats;
    }

    // ---------- Helpers ----------

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
