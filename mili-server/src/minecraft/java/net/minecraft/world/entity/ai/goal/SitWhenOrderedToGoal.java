package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;

public class SitWhenOrderedToGoal extends Goal {
    private final TamableAnimal mob;

    public SitWhenOrderedToGoal(TamableAnimal mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isOrderedToSit();
    }

    @Override
    public boolean canUse() {
        boolean isOrderedToSit = this.mob.isOrderedToSit();
        if (!isOrderedToSit && !this.mob.isTame()) {
            return false;
        } else if (this.mob.isInWater()) {
            return false;
        } else if (!this.mob.onGround()) {
            return false;
        } else {
            LivingEntity owner = this.mob.getOwner();
            return owner == null
                || owner.level() != this.mob.level()
                || (!(this.mob.distanceToSqr(owner) < 144.0) || owner.getLastHurtByMob() == null) && isOrderedToSit;
        }
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
        this.mob.setInSittingPose(true);
    }

    @Override
    public void stop() {
        this.mob.setInSittingPose(false);
    }
}
