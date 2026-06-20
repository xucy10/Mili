package net.minecraft.world.level.block;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class DoubleBlockCombiner {
    public static <S extends BlockEntity> DoubleBlockCombiner.NeighborCombineResult<S> combineWithNeigbour(
        BlockEntityType<S> blockEntityType,
        Function<BlockState, DoubleBlockCombiner.BlockType> doubleBlockTypeGetter,
        Function<BlockState, Direction> directionGetter,
        Property<Direction> directionProperty,
        BlockState state,
        LevelAccessor level,
        BlockPos pos,
        BiPredicate<LevelAccessor, BlockPos> blockedChestTest
    ) {
        S blockEntity = blockEntityType.getBlockEntity(level, pos);
        if (blockEntity == null) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        } else if (blockedChestTest.test(level, pos)) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        } else {
            DoubleBlockCombiner.BlockType blockType = doubleBlockTypeGetter.apply(state);
            boolean flag = blockType == DoubleBlockCombiner.BlockType.SINGLE;
            boolean flag1 = blockType == DoubleBlockCombiner.BlockType.FIRST;
            if (flag) {
                return new DoubleBlockCombiner.NeighborCombineResult.Single<>(blockEntity);
            } else {
                BlockPos blockPos = pos.relative(directionGetter.apply(state));
                // Paper start - Don't load Chunks from Hoppers and other things
                BlockState blockState = level.getBlockStateIfLoaded(blockPos);
                if (blockState == null) {
                    return new DoubleBlockCombiner.NeighborCombineResult.Single<>(blockEntity);
                }
                // Paper end - Don't load Chunks from Hoppers and other things
                if (blockState.is(state.getBlock())) {
                    DoubleBlockCombiner.BlockType blockType1 = doubleBlockTypeGetter.apply(blockState);
                    if (blockType1 != DoubleBlockCombiner.BlockType.SINGLE
                        && blockType != blockType1
                        && blockState.getValue(directionProperty) == state.getValue(directionProperty)) {
                        if (blockedChestTest.test(level, blockPos)) {
                            return DoubleBlockCombiner.Combiner::acceptNone;
                        }

                        S blockEntity1 = blockEntityType.getBlockEntity(level, blockPos);
                        if (blockEntity1 != null) {
                            S blockEntity2 = flag1 ? blockEntity : blockEntity1;
                            S blockEntity3 = flag1 ? blockEntity1 : blockEntity;
                            return new DoubleBlockCombiner.NeighborCombineResult.Double<>(blockEntity2, blockEntity3);
                        }
                    }
                }

                return new DoubleBlockCombiner.NeighborCombineResult.Single<>(blockEntity);
            }
        }
    }

    public static enum BlockType {
        SINGLE,
        FIRST,
        SECOND;
    }

    public interface Combiner<S, T> {
        T acceptDouble(S first, S second);

        T acceptSingle(S single);

        T acceptNone();
    }

    public interface NeighborCombineResult<S> {
        <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> combiner);

        public static final class Double<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {
            private final S first;
            private final S second;

            public Double(S first, S second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> combiner) {
                return combiner.acceptDouble(this.first, this.second);
            }
        }

        public static final class Single<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {
            private final S single;

            public Single(S single) {
                this.single = single;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> combiner) {
                return combiner.acceptSingle(this.single);
            }
        }
    }
}
