package net.minecraft.world.entity.ai.goal;

import com.mojang.datafixers.DataFixUtils;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;

public class FollowFlockLeaderGoal extends Goal {
    private static final int INTERVAL_TICKS = 200;
    private final AbstractSchoolingFish mob;
    private int timeToRecalcPath;
    private int nextStartTick;

    public FollowFlockLeaderGoal(AbstractSchoolingFish mob) {
        this.mob = mob;
        this.nextStartTick = this.nextStartTick(mob);
    }

    protected int nextStartTick(AbstractSchoolingFish taskOwner) {
        return reducedTickDelay(200 + taskOwner.getRandom().nextInt(200) % 20);
    }

    @Override
    public boolean canUse() {
        if (this.mob.hasFollowers()) {
            return false;
        } else if (this.mob.isFollower()) {
            return true;
        } else if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        } else {
            this.nextStartTick = this.nextStartTick(this.mob);
            Predicate<AbstractSchoolingFish> predicate = fish -> fish.canBeFollowed() || !fish.isFollower();
            List<? extends AbstractSchoolingFish> entitiesOfClass = this.mob
                .level()
                .getEntitiesOfClass((Class<? extends AbstractSchoolingFish>)this.mob.getClass(), this.mob.getBoundingBox().inflate(8.0, 8.0, 8.0), predicate);
            AbstractSchoolingFish abstractSchoolingFish = DataFixUtils.orElse(
                entitiesOfClass.stream().filter(AbstractSchoolingFish::canBeFollowed).findAny(), this.mob
            );
            abstractSchoolingFish.addFollowers(entitiesOfClass.stream().filter(fish -> !fish.isFollower()));
            return this.mob.isFollower();
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isFollower() && this.mob.inRangeOfLeader();
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.mob.stopFollowing();
    }

    @Override
    public void tick() {
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            this.mob.pathToLeader();
        }
    }
}
