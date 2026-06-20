package net.minecraft.world.level.chunk;

import net.minecraft.world.level.block.state.BlockState;

public interface BlockColumn {
    BlockState getBlock(int pos);

    void setBlock(int pos, BlockState state);
}
