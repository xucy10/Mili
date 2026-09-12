package fun.bm.mili.utils.dagschedule;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performs Kahn's topological sort over a batch of {@link DAGTask} nodes.
 *
 * <p>The sorter groups tasks into <b>levels</b> (also called "waves" or "strata"),
 * where all tasks in level {@code i} share the property that their dependencies
 * all reside in levels {@code 0 .. i-1}. This means all tasks within a level
 * can safely run concurrently.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 *     List<DAGTask> tasks = ...;
 *     TopologicalSorter sorter = new TopologicalSorter(tasks);
 *     List<List<DAGTask>> levels = sorter.sort();
 *     // levels.get(0) has no dependencies — dispatch immediately
 *     // levels.get(1) depends on levels.get(0), dispatch after level 0 completes
 * }</pre>
 *
 * <p>If the input contains a cycle, {@link #sort()} will throw
 * {@link IllegalStateException} — this is a programming error in the DAG
 * construction and should never happen at runtime.</p>
 */
public final class TopologicalSorter {

    private final Map<Long, DAGTask> byId;
    private final Map<Long, Set<Long>> depsByTask;
    private final int totalTasks;

    public TopologicalSorter(@NotNull List<DAGTask> tasks) {
        this.byId = new HashMap<>(tasks.size());
        this.depsByTask = new HashMap<>(tasks.size());
        this.totalTasks = tasks.size();

        for (DAGTask task : tasks) {
            byId.put(task.taskId, task);
            depsByTask.put(task.taskId, new HashSet<>(task.dependencies));
        }
        // Populate reverse edges for efficient "who depends on me" queries
        for (DAGTask task : tasks) {
            for (Long depId : task.dependencies) {
                DAGTask dep = byId.get(depId);
                if (dep != null) {
                    dep.dependents.add(task.taskId);
                }
            }
        }
    }

    /**
     * Executes Kahn's algorithm and returns a level-ordered partition of tasks.
     *
     * @return ordered list of levels; each level's tasks are independent of each other
     * @throws IllegalStateException if a cycle is detected in the DAG
     */
    @NotNull
    public List<List<DAGTask>> sort() {
        // Compute in-degree
        Map<Long, AtomicInteger> inDegree = new HashMap<>(totalTasks);
        for (DAGTask task : byId.values()) {
            inDegree.put(task.taskId, new AtomicInteger(task.dependencies.size()));
        }

        List<List<DAGTask>> levels = new ArrayList<>();

        // Level 0: zero in-degree tasks
        List<DAGTask> zeroDegree = new ArrayList<>();
        for (DAGTask task : byId.values()) {
            if (task.dependencies.isEmpty()) {
                zeroDegree.add(task);
            }
        }

        Set<Long> processed = new HashSet<>();

        while (!zeroDegree.isEmpty()) {
            // Current level = all zero in-degree tasks
            levels.add(new ArrayList<>(zeroDegree));
            processed.addAll(zeroDegree.stream().map(t -> t.taskId).toList());

            // Compute next level: decrement in-degree for each dependent
            List<DAGTask> nextLevel = new ArrayList<>();
            for (DAGTask completed : zeroDegree) {
                for (Long depId : completed.dependents) {
                    AtomicInteger deg = inDegree.get(depId);
                    if (deg != null && deg.decrementAndGet() == 0) {
                        nextLevel.add(byId.get(depId));
                    }
                }
            }
            zeroDegree = nextLevel;
        }

        if (processed.size() != totalTasks) {
            // Cycle detected — compute which tasks are stuck
            List<DAGTask> stuck = new ArrayList<>();
            for (DAGTask task : byId.values()) {
                if (!processed.contains(task.taskId)) {
                    stuck.add(task);
                }
            }
            throw new IllegalStateException(
                    "DAG cycle detected! " + stuck.size() + " tasks could not be topologically sorted. "
                    + "Sample stuck tasks: " + stuck.subList(0, Math.min(5, stuck.size())));
        }

        return levels;
    }

    /**
     * Builds an incremental sorter that tracks which tasks from the previous
     * wave have completed, so the next wave can be computed without re-sorting.
     *
     * @return an IncrementalSorter for wave-based dispatch
     */
    @NotNull
    public IncrementalSorter incremental() {
        return new IncrementalSorter(byId, depsByTask, totalTasks);
    }

    /**
     * Incremental variant: maintains in-degree state across waves so the
     * scheduler can advance to the next level without full re-sorting.
     */
    public static final class IncrementalSorter {
        private final Map<Long, DAGTask> byId;
        private final Map<Long, Set<Long>> depsByTask;
        private final Map<Long, AtomicInteger> inDegree;
        private final Set<Long> completedTasks = new HashSet<>();
        private final List<Long> currentFrontier = new ArrayList<>();
        private boolean initialized = false;

        IncrementalSorter(Map<Long, DAGTask> byId, Map<Long, Set<Long>> depsByTask, int totalTasks) {
            this.byId = byId;
            this.depsByTask = depsByTask;
            this.inDegree = new HashMap<>(totalTasks);
            for (Long taskId : byId.keySet()) {
                Set<Long> deps = depsByTask.get(taskId);
                this.inDegree.put(taskId, new AtomicInteger(deps != null ? deps.size() : 0));
            }
        }

        /**
         * Returns the first level of tasks (zero in-degree).
         */
        @NotNull
        public List<DAGTask> firstLevel() {
            if (!initialized) {
                initialized = true;
                for (DAGTask task : byId.values()) {
                    if (task.dependencies.isEmpty()) {
                        currentFrontier.add(task.taskId);
                    }
                }
            }
            return currentFrontier.stream().map(byId::get).toList();
        }

        /**
         * Submit a completion notification for a task.
         * Advances the frontier to include any tasks whose dependencies are now all satisfied.
         *
         * @param taskId id of the completed task
         * @return tasks newly transitioned to READY in this call, empty if none
         */
        @NotNull
        public List<DAGTask> onCompleted(long taskId) {
            completedTasks.add(taskId);
            DAGTask completed = byId.get(taskId);
            if (completed == null) return List.of();

            List<DAGTask> newlyReady = new ArrayList<>();
            for (Long dependentId : completed.dependents) {
                AtomicInteger deg = inDegree.get(dependentId);
                if (deg != null && deg.decrementAndGet() == 0) {
                    newlyReady.add(byId.get(dependentId));
                }
            }
            currentFrontier.remove(taskId);
            currentFrontier.addAll(newlyReady.stream().map(t -> t.taskId).toList());
            return newlyReady;
        }

        /**
         * True when all tasks have completed and there are no remaining frontier items.
         */
        public boolean isFinished() {
            return completedTasks.size() == byId.size();
        }
    }
}
