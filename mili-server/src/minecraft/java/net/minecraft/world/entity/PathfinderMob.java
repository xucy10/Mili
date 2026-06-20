package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;

public abstract class PathfinderMob extends Mob {
    protected static final float DEFAULT_WALK_TARGET_VALUE = 0.0F;

    protected PathfinderMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public BlockPos movingTarget; public BlockPos getMovingTarget() { return movingTarget; } // Paper

    public float getWalkTargetValue(BlockPos pos) {
        return this.getWalkTargetValue(pos, this.level());
    }

    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason spawnReason) {
        return this.getWalkTargetValue(this.blockPosition(), level) >= 0.0F;
    }

    public boolean isPathFinding() {
        return !this.getNavigation().isDone();
    }

    public boolean isPanicking() {
        if (this.brain.hasMemoryValue(MemoryModuleType.IS_PANICKING)) {
            return this.brain.getMemory(MemoryModuleType.IS_PANICKING).isPresent();
        } else {
            for (WrappedGoal wrappedGoal : this.goalSelector.getAvailableGoals()) {
                if (wrappedGoal.isRunning() && wrappedGoal.getGoal() instanceof PanicGoal) {
                    return true;
                }
            }

            return false;
        }
    }

    protected boolean shouldStayCloseToLeashHolder() {
        return true;
    }

    @Override
    public void closeRangeLeashBehaviour(Entity entity) {
        super.closeRangeLeashBehaviour(entity);
        if (this.shouldStayCloseToLeashHolder() && !this.isPanicking()) {
            this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            float f = 2.0F;
            float f1 = this.distanceTo(entity);
            Vec3 vec3 = new Vec3(entity.getX() - this.getX(), entity.getY() - this.getY(), entity.getZ() - this.getZ())
                .normalize()
                .scale(Math.max(f1 - 2.0F, 0.0F));
            this.getNavigation().moveTo(this.getX() + vec3.x, this.getY() + vec3.y, this.getZ() + vec3.z, this.followLeashSpeed());
        }
    }

    @Override
    public void whenLeashedTo(Entity entity) {
        this.setHomeTo(entity.blockPosition(), (int)this.leashElasticDistance() - 1);
        super.whenLeashedTo(entity);
    }

    protected double followLeashSpeed() {
        return 1.0;
    }
}
