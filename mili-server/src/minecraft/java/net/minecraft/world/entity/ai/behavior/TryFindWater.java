package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.mutable.MutableLong;

public class TryFindWater {
    public static BehaviorControl<PathfinderMob> create(int range, float speedModifier) {
        MutableLong mutableLong = new MutableLong(0L);
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.absent(MemoryModuleType.ATTACK_TARGET),
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET)
                )
                .apply(instance, (attackTarget, walkTarget, lookTarget) -> (level, mob, gameTime) -> {
                    if (level.getFluidState(mob.blockPosition()).is(FluidTags.WATER)) {
                        return false;
                    } else if (gameTime < mutableLong.longValue()) {
                        mutableLong.setValue(gameTime + 20L + 2L);
                        return true;
                    } else {
                        BlockPos blockPos = null;
                        BlockPos blockPos1 = null;
                        BlockPos blockPos2 = mob.blockPosition();

                        for (BlockPos blockPos3 : BlockPos.withinManhattan(blockPos2, range, range, range)) {
                            if (blockPos3.getX() != blockPos2.getX() || blockPos3.getZ() != blockPos2.getZ()) {
                                BlockState blockState = mob.level().getBlockState(blockPos3.above());
                                BlockState blockState1 = mob.level().getBlockState(blockPos3);
                                if (blockState1.is(Blocks.WATER)) {
                                    if (blockState.isAir()) {
                                        blockPos = blockPos3.immutable();
                                        break;
                                    }

                                    if (blockPos1 == null && !blockPos3.closerToCenterThan(mob.position(), 1.5)) {
                                        blockPos1 = blockPos3.immutable();
                                    }
                                }
                            }
                        }

                        if (blockPos == null) {
                            blockPos = blockPos1;
                        }

                        if (blockPos != null) {
                            lookTarget.set(new BlockPosTracker(blockPos));
                            walkTarget.set(new WalkTarget(new BlockPosTracker(blockPos), speedModifier, 0));
                        }

                        mutableLong.setValue(gameTime + 40L);
                        return true;
                    }
                })
        );
    }
}
