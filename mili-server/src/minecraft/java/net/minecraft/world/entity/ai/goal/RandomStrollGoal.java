package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RandomStrollGoal extends Goal {
    public static final int DEFAULT_INTERVAL = 120;
    protected final PathfinderMob mob;
    protected double wantedX;
    protected double wantedY;
    protected double wantedZ;
    protected final double speedModifier;
    protected int interval;
    protected boolean forceTrigger;
    private final boolean checkNoActionTime;

    public RandomStrollGoal(PathfinderMob mob, double speedModifier) {
        this(mob, speedModifier, 120);
    }

    public RandomStrollGoal(PathfinderMob mob, double speedModifier, int interval) {
        this(mob, speedModifier, interval, true);
    }

    public RandomStrollGoal(PathfinderMob mob, double speedModifier, int interval, boolean checkNoActionTime) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.interval = interval;
        this.checkNoActionTime = checkNoActionTime;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.hasControllingPassenger()) {
            return false;
        } else {
            if (!this.forceTrigger) {
                if (this.checkNoActionTime && this.mob.getNoActionTime() >= 100) {
                    return false;
                }

                if (this.mob.getRandom().nextInt(reducedTickDelay(this.interval)) != 0) {
                    return false;
                }
            }

            Vec3 position = this.getPosition();
            if (position == null) {
                return false;
            } else {
                this.wantedX = position.x;
                this.wantedY = position.y;
                this.wantedZ = position.z;
                this.forceTrigger = false;
                return true;
            }
        }
    }

    protected @Nullable Vec3 getPosition() {
        return DefaultRandomPos.getPos(this.mob, 10, 7);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone() && !this.mob.hasControllingPassenger();
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        super.stop();
    }

    public void trigger() {
        this.forceTrigger = true;
    }

    public void setInterval(int newChance) {
        this.interval = newChance;
    }
}
