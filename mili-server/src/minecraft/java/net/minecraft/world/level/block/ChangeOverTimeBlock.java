package net.minecraft.world.level.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ChangeOverTimeBlock<T extends Enum<T>> {
    int SCAN_DISTANCE = 4;

    Optional<BlockState> getNext(BlockState state);

    float getChanceModifier();

    default void changeOverTime(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        float f = 0.05688889F;
        if (random.nextFloat() < 0.05688889F) {
            this.getNextState(state, level, pos, random).ifPresent(blockState -> org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(level, pos, blockState, Block.UPDATE_ALL)); // CraftBukkit
        }
    }

    T getAge();

    default Optional<BlockState> getNextState(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int ordinal = this.getAge().ordinal();
        int i = 0;
        int i1 = 0;

        for (BlockPos blockPos : BlockPos.withinManhattan(pos, 4, 4, 4)) {
            int i2 = blockPos.distManhattan(pos);
            if (i2 > 4) {
                break;
            }

            if (!blockPos.equals(pos) && level.getBlockState(blockPos).getBlock() instanceof ChangeOverTimeBlock<?> changeOverTimeBlock) {
                Enum<?> age = changeOverTimeBlock.getAge();
                if (this.getAge().getClass() == age.getClass()) {
                    int ordinal1 = age.ordinal();
                    if (ordinal1 < ordinal) {
                        return Optional.empty();
                    }

                    if (ordinal1 > ordinal) {
                        i1++;
                    } else {
                        i++;
                    }
                }
            }
        }

        float f = (float)(i1 + 1) / (i1 + i + 1);
        float f1 = f * f * this.getChanceModifier();
        return random.nextFloat() < f1 ? this.getNext(state) : Optional.empty();
    }
}
