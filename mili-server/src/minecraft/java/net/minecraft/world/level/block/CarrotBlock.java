package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CarrotBlock extends CropBlock {
    public static final MapCodec<CarrotBlock> CODEC = simpleCodec(CarrotBlock::new);
    private static final VoxelShape[] SHAPES = Block.boxes(7, i -> Block.column(16.0, 0.0, 2 + i));

    @Override
    public MapCodec<CarrotBlock> codec() {
        return CODEC;
    }

    public CarrotBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return Items.CARROT;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[this.getAge(state)];
    }
}
