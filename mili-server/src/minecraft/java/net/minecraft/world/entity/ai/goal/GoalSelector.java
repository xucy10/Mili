package net.minecraft.world.entity.ai.goal;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class GoalSelector {
    private static final WrappedGoal NO_GOAL = new WrappedGoal(Integer.MAX_VALUE, new Goal() {
        @Override
        public boolean canUse() {
            return false;
        }
    }) {
        @Override
        public boolean isRunning() {
            return false;
        }
    };
    private final Map<Goal.Flag, WrappedGoal> lockedFlags = new EnumMap<>(Goal.Flag.class);
    private final Set<WrappedGoal> availableGoals = new ObjectLinkedOpenHashSet<>();
    private static final Goal.Flag[] GOAL_FLAG_VALUES = Goal.Flag.values(); // Paper - remove streams from GoalSelector
    private final ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<net.minecraft.world.entity.ai.goal.Goal.Flag> goalTypes = new ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<>(Goal.Flag.class); // Paper - remove streams from GoalSelector
    private int curRate; // Paper - EAR 2

    public void addGoal(int priority, Goal goal) {
        this.availableGoals.add(new WrappedGoal(priority, goal));
    }

    public void removeAllGoals(Predicate<Goal> filter) {
        this.availableGoals.removeIf(wrappedGoal -> filter.test(wrappedGoal.getGoal()));
    }

    // Paper start - EAR 2
    public boolean inactiveTick() {
        this.curRate++;
        return this.curRate % 3 == 0; // TODO newGoalRate was already unused in 1.20.4, check if this is correct
    }

    public boolean hasTasks() {
        for (WrappedGoal task : this.availableGoals) {
            if (task.isRunning()) {
                return true;
            }
        }
        return false;
    }
    // Paper end - EAR 2

    public void removeGoal(Goal goal) {
        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.getGoal() == goal && wrappedGoal.isRunning()) {
                wrappedGoal.stop();
            }
        }

        this.availableGoals.removeIf(wrappedGoal1 -> wrappedGoal1.getGoal() == goal);
    }

    // Paper start - Perf: optimize goal types
    private static boolean goalContainsAnyFlags(WrappedGoal goal, ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<Goal.Flag> flags) {
        return goal.getFlags().hasCommonElements(flags);
    }

    private static boolean goalCanBeReplacedForAllFlags(WrappedGoal goal, Map<Goal.Flag, WrappedGoal> flag) {
        long flagIterator = goal.getFlags().getBackingSet();
        int wrappedGoalSize = goal.getFlags().size();
        for (int i = 0; i < wrappedGoalSize; ++i) {
            final Goal.Flag flag1 = GOAL_FLAG_VALUES[Long.numberOfTrailingZeros(flagIterator)];
            flagIterator ^= ca.spottedleaf.concurrentutil.util.IntegerUtil.getTrailingBit(flagIterator);
            // Paper end - Perf: optimize goal types
            if (!flag.getOrDefault(flag1, NO_GOAL).canBeReplacedBy(goal)) {
                return false;
            }
        }

        return true;
    }

    public void tick() {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("goalCleanup");

        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.isRunning() && (goalContainsAnyFlags(wrappedGoal, this.goalTypes) || !wrappedGoal.canContinueToUse())) { // Paper - Perf: optimize goal types by removing streams
                wrappedGoal.stop();
            }
        }

        this.lockedFlags.entrySet().removeIf(entry -> !entry.getValue().isRunning());
        profilerFiller.pop();
        profilerFiller.push("goalUpdate");

        for (WrappedGoal wrappedGoalx : this.availableGoals) {
            // Paper start
            if (!wrappedGoalx.isRunning() && !goalContainsAnyFlags(wrappedGoalx, this.goalTypes) && goalCanBeReplacedForAllFlags(wrappedGoalx, this.lockedFlags) && wrappedGoalx.canUse()) {
                long flagIterator = wrappedGoalx.getFlags().getBackingSet();
                int wrappedGoalSize = wrappedGoalx.getFlags().size();
                for (int i = 0; i < wrappedGoalSize; ++i) {
                    final Goal.Flag flag = GOAL_FLAG_VALUES[Long.numberOfTrailingZeros(flagIterator)];
                    flagIterator ^= ca.spottedleaf.concurrentutil.util.IntegerUtil.getTrailingBit(flagIterator);
                    // Paper end
                    WrappedGoal wrappedGoal1 = this.lockedFlags.getOrDefault(flag, NO_GOAL);
                    wrappedGoal1.stop();
                    this.lockedFlags.put(flag, wrappedGoalx);
                }

                wrappedGoalx.start();
            }
        }

        profilerFiller.pop();
        this.tickRunningGoals(true);
    }

    public void tickRunningGoals(boolean tickAllRunning) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("goalTick");

        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.isRunning() && (tickAllRunning || wrappedGoal.requiresUpdateEveryTick())) {
                wrappedGoal.tick();
            }
        }

        profilerFiller.pop();
    }

    public Set<WrappedGoal> getAvailableGoals() {
        return this.availableGoals;
    }

    public void disableControlFlag(Goal.Flag flag) {
        this.goalTypes.addUnchecked(flag); // Paper - remove streams from GoalSelector
    }

    public void enableControlFlag(Goal.Flag flag) {
        this.goalTypes.removeUnchecked(flag); // Paper - remove streams from GoalSelector
    }

    public void setControlFlag(Goal.Flag flag, boolean enabled) {
        if (enabled) {
            this.enableControlFlag(flag);
        } else {
            this.disableControlFlag(flag);
        }
    }
}
