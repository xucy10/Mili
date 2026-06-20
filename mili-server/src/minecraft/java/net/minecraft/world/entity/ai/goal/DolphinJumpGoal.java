package net.minecraft.world.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class DolphinJumpGoal extends JumpGoal {
    private static final int[] STEPS_TO_CHECK = new int[]{0, 1, 4, 5, 6, 7};
    private final Dolphin dolphin;
    private final int interval;
    private boolean breached;

    public DolphinJumpGoal(Dolphin dolphin, int interval) {
        this.dolphin = dolphin;
        this.interval = reducedTickDelay(interval);
    }

    @Override
    public boolean canUse() {
        if (this.dolphin.getRandom().nextInt(this.interval) != 0) {
            return false;
        } else {
            Direction motionDirection = this.dolphin.getMotionDirection();
            int stepX = motionDirection.getStepX();
            int stepZ = motionDirection.getStepZ();
            BlockPos blockPos = this.dolphin.blockPosition();

            for (int i : STEPS_TO_CHECK) {
                if (!this.waterIsClear(blockPos, stepX, stepZ, i) || !this.surfaceIsClear(blockPos, stepX, stepZ, i)) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean waterIsClear(BlockPos pos, int dx, int dz, int scale) {
        BlockPos blockPos = pos.offset(dx * scale, 0, dz * scale);
        return this.dolphin.level().getFluidState(blockPos).is(FluidTags.WATER) && !this.dolphin.level().getBlockState(blockPos).blocksMotion();
    }

    private boolean surfaceIsClear(BlockPos pos, int dx, int dz, int scale) {
        return this.dolphin.level().getBlockState(pos.offset(dx * scale, 1, dz * scale)).isAir()
            && this.dolphin.level().getBlockState(pos.offset(dx * scale, 2, dz * scale)).isAir();
    }

    @Override
    public boolean canContinueToUse() {
        double d = this.dolphin.getDeltaMovement().y;
        return (!(d * d < 0.03F) || this.dolphin.getXRot() == 0.0F || !(Math.abs(this.dolphin.getXRot()) < 10.0F) || !this.dolphin.isInWater())
            && !this.dolphin.onGround();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        Direction motionDirection = this.dolphin.getMotionDirection();
        this.dolphin.setDeltaMovement(this.dolphin.getDeltaMovement().add(motionDirection.getStepX() * 0.6, 0.7, motionDirection.getStepZ() * 0.6));
        this.dolphin.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.dolphin.setXRot(0.0F);
    }

    @Override
    public void tick() {
        boolean flag = this.breached;
        if (!flag) {
            FluidState fluidState = this.dolphin.level().getFluidState(this.dolphin.blockPosition());
            this.breached = fluidState.is(FluidTags.WATER);
        }

        if (this.breached && !flag) {
            this.dolphin.playSound(SoundEvents.DOLPHIN_JUMP, 1.0F, 1.0F);
        }

        Vec3 deltaMovement = this.dolphin.getDeltaMovement();
        if (deltaMovement.y * deltaMovement.y < 0.03F && this.dolphin.getXRot() != 0.0F) {
            this.dolphin.setXRot(Mth.rotLerp(0.2F, this.dolphin.getXRot(), 0.0F));
        } else if (deltaMovement.length() > 1.0E-5F) {
            double d = deltaMovement.horizontalDistance();
            double d1 = Math.atan2(-deltaMovement.y, d) * 180.0F / (float)Math.PI;
            this.dolphin.setXRot((float)d1);
        }
    }
}
