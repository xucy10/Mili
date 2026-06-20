package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockUtil {
    public static BlockUtil.FoundRectangle getLargestRectangleAround(
        BlockPos centerPos, Direction.Axis axis1, int max1, Direction.Axis axis2, int max2, Predicate<BlockPos> posPredicate
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = centerPos.mutable();
        Direction direction = Direction.get(Direction.AxisDirection.NEGATIVE, axis1);
        Direction opposite = direction.getOpposite();
        Direction direction1 = Direction.get(Direction.AxisDirection.NEGATIVE, axis2);
        Direction opposite1 = direction1.getOpposite();
        int limit = getLimit(posPredicate, mutableBlockPos.set(centerPos), direction, max1);
        int limit1 = getLimit(posPredicate, mutableBlockPos.set(centerPos), opposite, max1);
        int i = limit;
        BlockUtil.IntBounds[] intBoundss = new BlockUtil.IntBounds[limit + 1 + limit1];
        intBoundss[limit] = new BlockUtil.IntBounds(
            getLimit(posPredicate, mutableBlockPos.set(centerPos), direction1, max2), getLimit(posPredicate, mutableBlockPos.set(centerPos), opposite1, max2)
        );
        int i1 = intBoundss[limit].min;

        for (int i2 = 1; i2 <= limit; i2++) {
            BlockUtil.IntBounds intBounds = intBoundss[i - (i2 - 1)];
            intBoundss[i - i2] = new BlockUtil.IntBounds(
                getLimit(posPredicate, mutableBlockPos.set(centerPos).move(direction, i2), direction1, intBounds.min),
                getLimit(posPredicate, mutableBlockPos.set(centerPos).move(direction, i2), opposite1, intBounds.max)
            );
        }

        for (int i2 = 1; i2 <= limit1; i2++) {
            BlockUtil.IntBounds intBounds = intBoundss[i + i2 - 1];
            intBoundss[i + i2] = new BlockUtil.IntBounds(
                getLimit(posPredicate, mutableBlockPos.set(centerPos).move(opposite, i2), direction1, intBounds.min),
                getLimit(posPredicate, mutableBlockPos.set(centerPos).move(opposite, i2), opposite1, intBounds.max)
            );
        }

        int i2 = 0;
        int i3 = 0;
        int i4 = 0;
        int i5 = 0;
        int[] ints = new int[intBoundss.length];

        for (int i6 = i1; i6 >= 0; i6--) {
            for (int i7 = 0; i7 < intBoundss.length; i7++) {
                BlockUtil.IntBounds intBounds1 = intBoundss[i7];
                int i8 = i1 - intBounds1.min;
                int i9 = i1 + intBounds1.max;
                ints[i7] = i6 >= i8 && i6 <= i9 ? i9 + 1 - i6 : 0;
            }

            Pair<BlockUtil.IntBounds, Integer> maxRectangleLocation = getMaxRectangleLocation(ints);
            BlockUtil.IntBounds intBounds1 = maxRectangleLocation.getFirst();
            int i8 = 1 + intBounds1.max - intBounds1.min;
            int i9 = maxRectangleLocation.getSecond();
            if (i8 * i9 > i4 * i5) {
                i2 = intBounds1.min;
                i3 = i6;
                i4 = i8;
                i5 = i9;
            }
        }

        return new BlockUtil.FoundRectangle(centerPos.relative(axis1, i2 - i).relative(axis2, i3 - i1), i4, i5);
    }

    private static int getLimit(Predicate<BlockPos> posPredicate, BlockPos.MutableBlockPos centerPos, Direction direction, int max) {
        int i = 0;

        while (i < max && posPredicate.test(centerPos.move(direction))) {
            i++;
        }

        return i;
    }

    @VisibleForTesting
    static Pair<BlockUtil.IntBounds, Integer> getMaxRectangleLocation(int[] heights) {
        int i = 0;
        int i1 = 0;
        int i2 = 0;
        IntStack intStack = new IntArrayList();
        intStack.push(0);

        for (int i3 = 1; i3 <= heights.length; i3++) {
            int i4 = i3 == heights.length ? 0 : heights[i3];

            while (!intStack.isEmpty()) {
                int i5 = heights[intStack.topInt()];
                if (i4 >= i5) {
                    intStack.push(i3);
                    break;
                }

                intStack.popInt();
                int i6 = intStack.isEmpty() ? 0 : intStack.topInt() + 1;
                if (i5 * (i3 - i6) > i2 * (i1 - i)) {
                    i1 = i3;
                    i = i6;
                    i2 = i5;
                }
            }

            if (intStack.isEmpty()) {
                intStack.push(i3);
            }
        }

        return new Pair<>(new BlockUtil.IntBounds(i, i1 - 1), i2);
    }

    public static Optional<BlockPos> getTopConnectedBlock(BlockGetter level, BlockPos pos, Block baseBlock, Direction direction, Block endBlock) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        BlockState blockState;
        do {
            mutableBlockPos.move(direction);
            blockState = level.getBlockState(mutableBlockPos);
        } while (blockState.is(baseBlock));

        return blockState.is(endBlock) ? Optional.of(mutableBlockPos) : Optional.empty();
    }

    public static class FoundRectangle {
        public final BlockPos minCorner;
        public final int axis1Size;
        public final int axis2Size;

        public FoundRectangle(BlockPos minCorner, int axis1Size, int axis2Size) {
            this.minCorner = minCorner;
            this.axis1Size = axis1Size;
            this.axis2Size = axis2Size;
        }
    }

    public static class IntBounds {
        public final int min;
        public final int max;

        public IntBounds(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return "IntBounds{min=" + this.min + ", max=" + this.max + "}";
        }
    }
}
