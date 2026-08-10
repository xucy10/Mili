package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BedBlockEntity extends BlockEntity {
    public final DyeColor color;

    public BedBlockEntity(BlockPos pos, BlockState blockState) {
        this(pos, blockState, ((BedBlock)blockState.getBlock()).getColor());
    }

    public BedBlockEntity(BlockPos pos, BlockState blockState, DyeColor color) {
        super(BlockEntityType.BED, pos, blockState);
        this.color = color;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public DyeColor getColor() {
        return this.color;
    }
}
