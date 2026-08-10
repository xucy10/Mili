package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class ChorusPlantBlock extends PipeBlock {
    public static final MapCodec<ChorusPlantBlock> CODEC = simpleCodec(ChorusPlantBlock::new);

    @Override
    public MapCodec<ChorusPlantBlock> codec() {
        return CODEC;
    }

    protected ChorusPlantBlock(BlockBehaviour.Properties properties) {
        super(10.0F, properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableChorusPlantUpdates) return this.defaultBlockState(); // Paper - add option to disable block updates
        return getStateWithConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    public static BlockState getStateWithConnections(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState blockState = level.getBlockState(pos.below());
        BlockState blockState1 = level.getBlockState(pos.above());
        BlockState blockState2 = level.getBlockState(pos.north());
        BlockState blockState3 = level.getBlockState(pos.east());
        BlockState blockState4 = level.getBlockState(pos.south());
        BlockState blockState5 = level.getBlockState(pos.west());
        Block block = state.getBlock();
        return state.trySetValue(DOWN, blockState.is(block) || blockState.is(Blocks.CHORUS_FLOWER) || blockState.is(Blocks.END_STONE))
            .trySetValue(UP, blockState1.is(block) || blockState1.is(Blocks.CHORUS_FLOWER))
            .trySetValue(NORTH, blockState2.is(block) || blockState2.is(Blocks.CHORUS_FLOWER))
            .trySetValue(EAST, blockState3.is(block) || blockState3.is(Blocks.CHORUS_FLOWER))
            .trySetValue(SOUTH, blockState4.is(block) || blockState4.is(Blocks.CHORUS_FLOWER))
            .trySetValue(WEST, blockState5.is(block) || blockState5.is(Blocks.CHORUS_FLOWER));
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
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableChorusPlantUpdates) return state; // Paper - add option to disable block updates
        if (!state.canSurvive(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            boolean flag = neighborState.is(this)
                || neighborState.is(Blocks.CHORUS_FLOWER)
                || direction == Direction.DOWN && neighborState.is(Blocks.END_STONE);
            return state.setValue(PROPERTY_BY_DIRECTION.get(direction), flag);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableChorusPlantUpdates) return; // Paper - add option to disable block updates
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableChorusPlantUpdates) return true; // Paper - add option to disable block updates
        BlockState blockState = level.getBlockState(pos.below());
        boolean flag = !level.getBlockState(pos.above()).isAir() && !blockState.isAir();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState1 = level.getBlockState(blockPos);
            if (blockState1.is(this)) {
                if (flag) {
                    return false;
                }

                BlockState blockState2 = level.getBlockState(blockPos.below());
                if (blockState2.is(this) || blockState2.is(Blocks.END_STONE)) {
                    return true;
                }
            }
        }

        return blockState.is(this) || blockState.is(Blocks.END_STONE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
