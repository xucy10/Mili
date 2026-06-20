package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;

public class TryLaySpawnOnWaterNearLand {
    public static BehaviorControl<LivingEntity> create(Block spawnBlock) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.absent(MemoryModuleType.ATTACK_TARGET),
                    instance.present(MemoryModuleType.WALK_TARGET),
                    instance.present(MemoryModuleType.IS_PREGNANT)
                )
                .apply(
                    instance,
                    (attackTarget, walkTarget, isPregnant) -> (level, entity, gameTime) -> {
                        if (!entity.isInWater() && entity.onGround()) {
                            BlockPos blockPos = entity.blockPosition().below();

                            for (Direction direction : Direction.Plane.HORIZONTAL) {
                                BlockPos blockPos1 = blockPos.relative(direction);
                                if (level.getBlockState(blockPos1).getCollisionShape(level, blockPos1).getFaceShape(Direction.UP).isEmpty()
                                    && level.getFluidState(blockPos1).is(Fluids.WATER)) {
                                    BlockPos blockPos2 = blockPos1.above();
                                    if (level.getBlockState(blockPos2).isAir()) {
                                        BlockState blockState = spawnBlock.defaultBlockState();
                                        // CraftBukkit start
                                        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, blockPos2, blockState)) {
                                            isPregnant.erase();
                                            return true;
                                        }
                                        // CraftBukkit end
                                        level.setBlock(blockPos2, blockState, Block.UPDATE_ALL);
                                        level.gameEvent(GameEvent.BLOCK_PLACE, blockPos2, GameEvent.Context.of(entity, blockState));
                                        level.playSound(null, entity, SoundEvents.FROG_LAY_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                                        isPregnant.erase();
                                        return true;
                                    }
                                }
                            }

                            return true;
                        } else {
                            return false;
                        }
                    }
                )
        );
    }
}
