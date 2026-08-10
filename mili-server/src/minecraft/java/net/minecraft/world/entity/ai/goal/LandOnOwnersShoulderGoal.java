package net.minecraft.world.entity.ai.goal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.parrot.ShoulderRidingEntity;

public class LandOnOwnersShoulderGoal extends Goal {
    private final ShoulderRidingEntity entity;
    private boolean isSittingOnShoulder;

    public LandOnOwnersShoulderGoal(ShoulderRidingEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean canUse() {
        if (!(this.entity.getOwner() instanceof ServerPlayer serverPlayer)) {
            return false;
        } else {
            boolean flag = !serverPlayer.isSpectator() && !serverPlayer.getAbilities().flying && !serverPlayer.isInWater() && !serverPlayer.isInPowderSnow;
            return !this.entity.isOrderedToSit() && flag && this.entity.canSitOnShoulder();
        }
    }

    @Override
    public boolean isInterruptable() {
        return !this.isSittingOnShoulder;
    }

    @Override
    public void start() {
        this.isSittingOnShoulder = false;
    }

    @Override
    public void tick() {
        if (!this.isSittingOnShoulder && !this.entity.isInSittingPose() && !this.entity.isLeashed()) {
            if (this.entity.getOwner() instanceof ServerPlayer serverPlayer && this.entity.getBoundingBox().intersects(serverPlayer.getBoundingBox())) {
                this.isSittingOnShoulder = this.entity.setEntityOnShoulder(serverPlayer);
            }
        }
    }
}
