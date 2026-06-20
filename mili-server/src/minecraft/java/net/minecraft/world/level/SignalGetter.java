package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

public interface SignalGetter extends BlockGetter {
    Direction[] DIRECTIONS = Direction.values();

    default int getDirectSignal(BlockPos pos, Direction direction) {
        return this.getBlockState(pos).getDirectSignal(this, pos, direction);
    }

    default int getDirectSignalTo(BlockPos pos) {
        int i = 0;
        i = Math.max(i, this.getDirectSignal(pos.below(), Direction.DOWN));
        if (i >= 15) {
            return i;
        } else {
            i = Math.max(i, this.getDirectSignal(pos.above(), Direction.UP));
            if (i >= 15) {
                return i;
            } else {
                i = Math.max(i, this.getDirectSignal(pos.north(), Direction.NORTH));
                if (i >= 15) {
                    return i;
                } else {
                    i = Math.max(i, this.getDirectSignal(pos.south(), Direction.SOUTH));
                    if (i >= 15) {
                        return i;
                    } else {
                        i = Math.max(i, this.getDirectSignal(pos.west(), Direction.WEST));
                        if (i >= 15) {
                            return i;
                        } else {
                            i = Math.max(i, this.getDirectSignal(pos.east(), Direction.EAST));
                            return i >= 15 ? i : i;
                        }
                    }
                }
            }
        }
    }

    default int getControlInputSignal(BlockPos pos, Direction direction, boolean diodesOnly) {
        BlockState blockState = this.getBlockState(pos);
        if (diodesOnly) {
            return DiodeBlock.isDiode(blockState) ? this.getDirectSignal(pos, direction) : 0;
        } else if (blockState.is(Blocks.REDSTONE_BLOCK)) {
            return 15;
        } else if (blockState.is(Blocks.REDSTONE_WIRE)) {
            return blockState.getValue(RedStoneWireBlock.POWER);
        } else {
            return blockState.isSignalSource() ? this.getDirectSignal(pos, direction) : 0;
        }
    }

    default boolean hasSignal(BlockPos pos, Direction direction) {
        return this.getSignal(pos, direction) > 0;
    }

    default int getSignal(BlockPos pos, Direction direction) {
        BlockState blockState = this.getBlockState(pos);
        int signal = blockState.getSignal(this, pos, direction);
        return blockState.isRedstoneConductor(this, pos) ? Math.max(signal, this.getDirectSignalTo(pos)) : signal;
    }

    default boolean hasNeighborSignal(BlockPos pos) {
        return this.getSignal(pos.below(), Direction.DOWN) > 0
            || this.getSignal(pos.above(), Direction.UP) > 0
            || this.getSignal(pos.north(), Direction.NORTH) > 0
            || this.getSignal(pos.south(), Direction.SOUTH) > 0
            || this.getSignal(pos.west(), Direction.WEST) > 0
            || this.getSignal(pos.east(), Direction.EAST) > 0;
    }

    default int getBestNeighborSignal(BlockPos pos) {
        int i = 0;

        for (Direction direction : DIRECTIONS) {
            int signal = this.getSignal(pos.relative(direction), direction);
            if (signal >= 15) {
                return 15;
            }

            if (signal > i) {
                i = signal;
            }
        }

        return i;
    }
}
