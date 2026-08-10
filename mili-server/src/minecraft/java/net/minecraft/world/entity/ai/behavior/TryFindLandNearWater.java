package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.apache.commons.lang3.mutable.MutableLong;

public class TryFindLandNearWater {
    public static BehaviorControl<PathfinderMob> create(int range, float speedModifier) {
        MutableLong mutableLong = new MutableLong(0L);
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.absent(MemoryModuleType.ATTACK_TARGET),
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET)
                )
                .apply(
                    instance,
                    (attackTarget, walkTarget, lookTarget) -> (level, mob, gameTime) -> {
                        if (level.getFluidState(mob.blockPosition()).is(FluidTags.WATER)) {
                            return false;
                        } else if (gameTime < mutableLong.longValue()) {
                            mutableLong.setValue(gameTime + 40L);
                            return true;
                        } else {
                            CollisionContext collisionContext = CollisionContext.of(mob);
                            BlockPos blockPos = mob.blockPosition();
                            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                            label45:
                            for (BlockPos blockPos1 : BlockPos.withinManhattan(blockPos, range, range, range)) {
                                if ((blockPos1.getX() != blockPos.getX() || blockPos1.getZ() != blockPos.getZ())
                                    && level.getBlockState(blockPos1).getCollisionShape(level, blockPos1, collisionContext).isEmpty()
                                    && !level.getBlockState(mutableBlockPos.setWithOffset(blockPos1, Direction.DOWN))
                                        .getCollisionShape(level, blockPos1, collisionContext)
                                        .isEmpty()) {
                                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                                        mutableBlockPos.setWithOffset(blockPos1, direction);
                                        if (level.getBlockState(mutableBlockPos).isAir()
                                            && level.getBlockState(mutableBlockPos.move(Direction.DOWN)).is(Blocks.WATER)) {
                                            lookTarget.set(new BlockPosTracker(blockPos1));
                                            walkTarget.set(new WalkTarget(new BlockPosTracker(blockPos1), speedModifier, 0));
                                            break label45;
                                        }
                                    }
                                }
                            }

                            mutableLong.setValue(gameTime + 40L);
                            return true;
                        }
                    }
                )
        );
    }
}
