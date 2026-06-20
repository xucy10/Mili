package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class RepeaterBlock extends DiodeBlock {
    public static final MapCodec<RepeaterBlock> CODEC = simpleCodec(RepeaterBlock::new);
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final IntegerProperty DELAY = BlockStateProperties.DELAY;

    @Override
    public MapCodec<RepeaterBlock> codec() {
        return CODEC;
    }

    protected RepeaterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DELAY, 1).setValue(LOCKED, false).setValue(POWERED, false)
        );
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            level.setBlock(pos, state.cycle(DELAY), Block.UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected int getDelay(BlockState state) {
        return state.getValue(DELAY) * 2;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = super.getStateForPlacement(context);
        return blockState.setValue(LOCKED, this.isLocked(context.getLevel(), context.getClickedPos(), blockState));
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (direction == Direction.DOWN && !this.canSurviveOn(level, neighborPos, neighborState)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            return !level.isClientSide() && direction.getAxis() != state.getValue(FACING).getAxis()
                ? state.setValue(LOCKED, this.isLocked(level, pos, state))
                : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }
    }

    @Override
    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return this.getAlternateSignal(level, pos, state) > 0;
    }

    @Override
    protected boolean sideInputDiodesOnly() {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            Direction direction = state.getValue(FACING);
            double d = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            double d1 = pos.getY() + 0.4 + (random.nextDouble() - 0.5) * 0.2;
            double d2 = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            float f = -5.0F;
            if (random.nextBoolean()) {
                f = state.getValue(DELAY) * 2 - 1;
            }

            f /= 16.0F;
            double d3 = f * direction.getStepX();
            double d4 = f * direction.getStepZ();
            level.addParticle(DustParticleOptions.REDSTONE, d + d3, d1, d2 + d4, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DELAY, LOCKED, POWERED);
    }
}
