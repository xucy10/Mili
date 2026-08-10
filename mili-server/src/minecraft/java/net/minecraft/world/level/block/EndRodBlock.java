package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class EndRodBlock extends RodBlock {
    public static final MapCodec<EndRodBlock> CODEC = simpleCodec(EndRodBlock::new);

    @Override
    public MapCodec<EndRodBlock> codec() {
        return CODEC;
    }

    protected EndRodBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos().relative(clickedFace.getOpposite()));
        return blockState.is(this) && blockState.getValue(FACING) == clickedFace
            ? this.defaultBlockState().setValue(FACING, clickedFace.getOpposite())
            : this.defaultBlockState().setValue(FACING, clickedFace);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction direction = state.getValue(FACING);
        double d = pos.getX() + 0.55 - random.nextFloat() * 0.1F;
        double d1 = pos.getY() + 0.55 - random.nextFloat() * 0.1F;
        double d2 = pos.getZ() + 0.55 - random.nextFloat() * 0.1F;
        double d3 = 0.4F - (random.nextFloat() + random.nextFloat()) * 0.4F;
        if (random.nextInt(5) == 0) {
            level.addParticle(
                ParticleTypes.END_ROD,
                d + direction.getStepX() * d3,
                d1 + direction.getStepY() * d3,
                d2 + direction.getStepZ() * d3,
                random.nextGaussian() * 0.005,
                random.nextGaussian() * 0.005,
                random.nextGaussian() * 0.005
            );
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
