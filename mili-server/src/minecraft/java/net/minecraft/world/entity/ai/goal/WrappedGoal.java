package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import org.jspecify.annotations.Nullable;

public class WrappedGoal extends Goal {
    private final Goal goal;
    private final int priority;
    private boolean isRunning;

    public WrappedGoal(int priority, Goal goal) {
        this.priority = priority;
        this.goal = goal;
    }

    public boolean canBeReplacedBy(WrappedGoal other) {
        return this.isInterruptable() && other.getPriority() < this.getPriority();
    }

    @Override
    public boolean canUse() {
        return this.goal.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.goal.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return this.goal.isInterruptable();
    }

    @Override
    public void start() {
        if (!this.isRunning) {
            this.isRunning = true;
            this.goal.start();
        }
    }

    @Override
    public void stop() {
        if (this.isRunning) {
            this.isRunning = false;
            this.goal.stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return this.goal.requiresUpdateEveryTick();
    }

    @Override
    protected int adjustedTickDelay(int adjustment) {
        return this.goal.adjustedTickDelay(adjustment);
    }

    @Override
    public void tick() {
        this.goal.tick();
    }

    @Override
    public void setFlags(EnumSet<Goal.Flag> flagSet) {
        this.goal.setFlags(flagSet);
    }

    @Override
    public ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet<Goal.Flag> getFlags() { // Paper - remove streams from GoalSelector
        return this.goal.getFlags();
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public int getPriority() {
        return this.priority;
    }

    public Goal getGoal() {
        return this.goal;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other != null && this.getClass() == other.getClass() && this.goal.equals(((WrappedGoal)other).goal);
    }

    @Override
    public int hashCode() {
        return this.goal.hashCode();
    }
}
