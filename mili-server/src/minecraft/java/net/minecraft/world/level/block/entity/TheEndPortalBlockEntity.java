package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class TheEndPortalBlockEntity extends BlockEntity {
    protected TheEndPortalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public TheEndPortalBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityType.END_PORTAL, pos, blockState);
    }

    public boolean shouldRenderFace(Direction face) {
        return face.getAxis() == Direction.Axis.Y;
    }
}
