package net.minecraft.world.level;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;

public final class NoiseColumn implements BlockColumn {
    private final int minY;
    private final BlockState[] column;

    public NoiseColumn(int minY, BlockState[] column) {
        this.minY = minY;
        this.column = column;
    }

    @Override
    public BlockState getBlock(int pos) {
        int i = pos - this.minY;
        return i >= 0 && i < this.column.length ? this.column[i] : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void setBlock(int pos, BlockState state) {
        int i = pos - this.minY;
        if (i >= 0 && i < this.column.length) {
            this.column[i] = state;
        } else {
            throw new IllegalArgumentException("Outside of column height: " + pos);
        }
    }
}
