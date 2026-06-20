package net.minecraft.world.level.block;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

public interface SculkBehaviour {
    SculkBehaviour DEFAULT = new SculkBehaviour() {
        @Override
        public boolean attemptSpreadVein(
            LevelAccessor level, BlockPos pos, BlockState state, @Nullable Collection<Direction> directions, boolean markForPostprocessing
        ) {
            if (directions == null) {
                return ((SculkVeinBlock)Blocks.SCULK_VEIN).getSameSpaceSpreader().spreadAll(level.getBlockState(pos), level, pos, markForPostprocessing) > 0L;
            } else {
                return !directions.isEmpty()
                    ? (state.isAir() || state.getFluidState().is(Fluids.WATER)) && SculkVeinBlock.regrow(level, pos, state, directions)
                    : SculkBehaviour.super.attemptSpreadVein(level, pos, state, directions, markForPostprocessing);
            }
        }

        @Override
        public int attemptUseCharge(
            SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos pos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks
        ) {
            return cursor.getDecayDelay() > 0 ? cursor.getCharge() : 0;
        }

        @Override
        public int updateDecayDelay(int currentDecayDelay) {
            return Math.max(currentDecayDelay - 1, 0);
        }
    };

    default byte getSculkSpreadDelay() {
        return 1;
    }

    default void onDischarged(LevelAccessor level, BlockState state, BlockPos pos, RandomSource random) {
    }

    default boolean depositCharge(LevelAccessor level, BlockPos pos, RandomSource random) {
        return false;
    }

    default boolean attemptSpreadVein(
        LevelAccessor level, BlockPos pos, BlockState state, @Nullable Collection<Direction> directions, boolean markForPostprocessing
    ) {
        return ((MultifaceSpreadeableBlock)Blocks.SCULK_VEIN).getSpreader().spreadAll(state, level, pos, markForPostprocessing) > 0L;
    }

    default boolean canChangeBlockStateOnSpread() {
        return true;
    }

    default int updateDecayDelay(int currentDecayDelay) {
        return 1;
    }

    int attemptUseCharge(
        SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos pos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks
    );
}
