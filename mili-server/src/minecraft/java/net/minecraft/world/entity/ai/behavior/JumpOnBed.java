package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.jspecify.annotations.Nullable;

public class JumpOnBed extends Behavior<Mob> {
    private static final int MAX_TIME_TO_REACH_BED = 100;
    private static final int MIN_JUMPS = 3;
    private static final int MAX_JUMPS = 6;
    private static final int COOLDOWN_BETWEEN_JUMPS = 5;
    private final float speedModifier;
    private @Nullable BlockPos targetBed;
    private int remainingTimeToReachBed;
    private int remainingJumps;
    private int remainingCooldownUntilNextJump;

    public JumpOnBed(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_BED, MemoryStatus.VALUE_PRESENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Mob owner) {
        return owner.isBaby() && this.nearBed(level, owner);
    }

    @Override
    protected void start(ServerLevel level, Mob entity, long gameTime) {
        super.start(level, entity, gameTime);
        this.getNearestBed(entity).ifPresent(targetBed -> {
            this.targetBed = targetBed;
            this.remainingTimeToReachBed = 100;
            this.remainingJumps = 3 + level.random.nextInt(4);
            this.remainingCooldownUntilNextJump = 0;
            this.startWalkingTowardsBed(entity, targetBed);
        });
    }

    @Override
    protected void stop(ServerLevel level, Mob entity, long gameTime) {
        super.stop(level, entity, gameTime);
        this.targetBed = null;
        this.remainingTimeToReachBed = 0;
        this.remainingJumps = 0;
        this.remainingCooldownUntilNextJump = 0;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Mob entity, long gameTime) {
        return entity.isBaby()
            && this.targetBed != null
            && this.isBed(level, this.targetBed)
            && !this.tiredOfWalking(level, entity)
            && !this.tiredOfJumping(level, entity);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected void tick(ServerLevel level, Mob owner, long gameTime) {
        if (!this.onOrOverBed(level, owner)) {
            this.remainingTimeToReachBed--;
        } else if (this.remainingCooldownUntilNextJump > 0) {
            this.remainingCooldownUntilNextJump--;
        } else {
            if (this.onBedSurface(level, owner)) {
                owner.getJumpControl().jump();
                this.remainingJumps--;
                this.remainingCooldownUntilNextJump = 5;
            }
        }
    }

    private void startWalkingTowardsBed(Mob mob, BlockPos pos) {
        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, this.speedModifier, 0));
    }

    private boolean nearBed(ServerLevel level, Mob mob) {
        return this.onOrOverBed(level, mob) || this.getNearestBed(mob).isPresent();
    }

    private boolean onOrOverBed(ServerLevel level, Mob mob) {
        BlockPos blockPos = mob.blockPosition();
        BlockPos blockPos1 = blockPos.below();
        return this.isBed(level, blockPos) || this.isBed(level, blockPos1);
    }

    private boolean onBedSurface(ServerLevel level, Mob mob) {
        return this.isBed(level, mob.blockPosition());
    }

    private boolean isBed(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.BEDS);
    }

    private Optional<BlockPos> getNearestBed(Mob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.NEAREST_BED);
    }

    private boolean tiredOfWalking(ServerLevel level, Mob mob) {
        return !this.onOrOverBed(level, mob) && this.remainingTimeToReachBed <= 0;
    }

    private boolean tiredOfJumping(ServerLevel level, Mob mob) {
        return this.onOrOverBed(level, mob) && this.remainingJumps <= 0;
    }
}
