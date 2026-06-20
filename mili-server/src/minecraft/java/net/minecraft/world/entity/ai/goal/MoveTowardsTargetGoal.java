package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MoveTowardsTargetGoal extends Goal {
    private final PathfinderMob mob;
    private @Nullable LivingEntity target;
    private double wantedX;
    private double wantedY;
    private double wantedZ;
    private final double speedModifier;
    private final float within;

    public MoveTowardsTargetGoal(PathfinderMob mob, double speedModifier, float within) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.within = within;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        this.target = this.mob.getTarget();
        if (this.target == null) {
            return false;
        } else if (this.target.distanceToSqr(this.mob) > this.within * this.within) {
            return false;
        } else {
            Vec3 posTowards = DefaultRandomPos.getPosTowards(this.mob, 16, 7, this.target.position(), (float) (Math.PI / 2));
            if (posTowards == null) {
                return false;
            } else {
                this.wantedX = posTowards.x;
                this.wantedY = posTowards.y;
                this.wantedZ = posTowards.z;
                return true;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone() && this.target.isAlive() && this.target.distanceToSqr(this.mob) < this.within * this.within;
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
    }
}
