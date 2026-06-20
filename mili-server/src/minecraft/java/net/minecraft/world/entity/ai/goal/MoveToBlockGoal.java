package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.LevelReader;

public abstract class MoveToBlockGoal extends Goal {
    private static final int GIVE_UP_TICKS = 1200;
    private static final int STAY_TICKS = 1200;
    private static final int INTERVAL_TICKS = 200;
    protected final PathfinderMob mob;
    public final double speedModifier;
    protected int nextStartTick;
    protected int tryTicks;
    private int maxStayTicks;
    protected BlockPos blockPos = BlockPos.ZERO;
    private boolean reachedTarget;
    private final int searchRange;
    private final int verticalSearchRange;
    protected int verticalSearchStart;

    public MoveToBlockGoal(PathfinderMob mob, double speedModifier, int searchRange) {
        this(mob, speedModifier, searchRange, 1);
    }
    // Paper start - activation range improvements
    @Override
    public void stop() {
        super.stop();
        this.blockPos = BlockPos.ZERO;
        this.mob.movingTarget = null;
    }
    // Paper end

    public MoveToBlockGoal(PathfinderMob mob, double speedModifier, int searchRange, int verticalSearchRange) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.searchRange = searchRange;
        this.verticalSearchStart = 0;
        this.verticalSearchRange = verticalSearchRange;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        } else {
            this.nextStartTick = this.nextStartTick(this.mob);
            return this.findNearestBlock();
        }
    }

    protected int nextStartTick(PathfinderMob creature) {
        return reducedTickDelay(200 + creature.getRandom().nextInt(200));
    }

    @Override
    public boolean canContinueToUse() {
        return this.tryTicks >= -this.maxStayTicks && this.tryTicks <= 1200 && this.isValidTarget(this.mob.level(), this.blockPos);
    }

    @Override
    public void start() {
        this.moveMobToBlock();
        this.tryTicks = 0;
        this.maxStayTicks = this.mob.getRandom().nextInt(this.mob.getRandom().nextInt(1200) + 1200) + 1200;
    }

    protected void moveMobToBlock() {
        this.mob.getNavigation().moveTo(this.blockPos.getX() + 0.5, this.blockPos.getY() + 1, this.blockPos.getZ() + 0.5, this.speedModifier);
    }

    public double acceptedDistance() {
        return 1.0;
    }

    protected BlockPos getMoveToTarget() {
        return this.blockPos.above();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos moveToTarget = this.getMoveToTarget();
        if (!moveToTarget.closerToCenterThan(this.mob.position(), this.acceptedDistance())) {
            this.reachedTarget = false;
            this.tryTicks++;
            if (this.shouldRecalculatePath()) {
                this.mob.getNavigation().moveTo(moveToTarget.getX() + 0.5, moveToTarget.getY(), moveToTarget.getZ() + 0.5, this.speedModifier);
            }
        } else {
            this.reachedTarget = true;
            this.tryTicks--;
        }
    }

    public boolean shouldRecalculatePath() {
        return this.tryTicks % 40 == 0;
    }

    protected boolean isReachedTarget() {
        return this.reachedTarget;
    }

    protected boolean findNearestBlock() {
        int i = this.searchRange;
        int i1 = this.verticalSearchRange;
        BlockPos blockPos = this.mob.blockPosition();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i2 = this.verticalSearchStart; i2 <= i1; i2 = i2 > 0 ? -i2 : 1 - i2) {
            for (int i3 = 0; i3 < i; i3++) {
                for (int i4 = 0; i4 <= i3; i4 = i4 > 0 ? -i4 : 1 - i4) {
                    for (int i5 = i4 < i3 && i4 > -i3 ? i3 : 0; i5 <= i3; i5 = i5 > 0 ? -i5 : 1 - i5) {
                        mutableBlockPos.setWithOffset(blockPos, i4, i2 - 1, i5);
                        if (!this.mob.level().hasChunkAt(mutableBlockPos)) continue; // Gale - Airplane - block goal does not load chunks - if this block isn't loaded, continue
                        if (this.mob.isWithinHome(mutableBlockPos) && this.isValidTarget(this.mob.level(), mutableBlockPos)) {
                            this.blockPos = mutableBlockPos;
                            this.mob.movingTarget = mutableBlockPos == BlockPos.ZERO ? null : mutableBlockPos.immutable(); // Paper
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    protected abstract boolean isValidTarget(LevelReader level, BlockPos pos);
}
