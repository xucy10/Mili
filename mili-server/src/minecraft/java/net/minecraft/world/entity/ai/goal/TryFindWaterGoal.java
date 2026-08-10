package net.minecraft.world.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;

public class TryFindWaterGoal extends Goal {
    private final PathfinderMob mob;

    public TryFindWaterGoal(PathfinderMob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return this.mob.onGround() && !this.mob.level().getFluidState(this.mob.blockPosition()).is(FluidTags.WATER);
    }

    @Override
    public void start() {
        BlockPos blockPos = null;

        for (BlockPos blockPos1 : BlockPos.betweenClosed(
            Mth.floor(this.mob.getX() - 2.0),
            Mth.floor(this.mob.getY() - 2.0),
            Mth.floor(this.mob.getZ() - 2.0),
            Mth.floor(this.mob.getX() + 2.0),
            this.mob.getBlockY(),
            Mth.floor(this.mob.getZ() + 2.0)
        )) {
            if (this.mob.level().getFluidState(blockPos1).is(FluidTags.WATER)) {
                blockPos = blockPos1;
                break;
            }
        }

        if (blockPos != null) {
            this.mob.getMoveControl().setWantedPosition(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1.0);
        }
    }
}
