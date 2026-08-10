package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CactusFlowerBlock extends VegetationBlock {
    public static final MapCodec<CactusFlowerBlock> CODEC = simpleCodec(CactusFlowerBlock::new);
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 12.0);

    @Override
    public MapCodec<? extends CactusFlowerBlock> codec() {
        return CODEC;
    }

    public CactusFlowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(Blocks.CACTUS) || blockState.is(Blocks.FARMLAND) || blockState.isFaceSturdy(level, pos, Direction.UP, SupportType.CENTER);
    }
}
