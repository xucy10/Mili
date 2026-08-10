package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class RingBell {
    private static final float BELL_RING_CHANCE = 0.95F;
    public static final int RING_BELL_FROM_DISTANCE = 3;

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.MEETING_POINT)).apply(instance, meetingPoint -> (level, entity, gameTime) -> {
                if (level.random.nextFloat() <= 0.95F) {
                    return false;
                } else {
                    BlockPos blockPos = instance.get(meetingPoint).pos();
                    if (blockPos.closerThan(entity.blockPosition(), 3.0)) {
                        BlockState blockState = level.getBlockState(blockPos);
                        if (blockState.is(Blocks.BELL)) {
                            BellBlock bellBlock = (BellBlock)blockState.getBlock();
                            bellBlock.attemptToRing(entity, level, blockPos, null);
                        }
                    }

                    return true;
                }
            })
        );
    }
}
