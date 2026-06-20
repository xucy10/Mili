package net.minecraft.world.level.block;

import com.google.common.base.Predicates;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class EndPortalFrameBlock extends Block {
    public static final MapCodec<EndPortalFrameBlock> CODEC = simpleCodec(EndPortalFrameBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HAS_EYE = BlockStateProperties.EYE;
    private static final VoxelShape SHAPE_EMPTY = Block.column(16.0, 0.0, 13.0);
    private static final VoxelShape SHAPE_FULL = Shapes.or(SHAPE_EMPTY, Block.column(8.0, 13.0, 16.0));
    private static @Nullable BlockPattern portalShape;

    @Override
    public MapCodec<EndPortalFrameBlock> codec() {
        return CODEC;
    }

    public EndPortalFrameBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_EYE, false));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HAS_EYE) ? SHAPE_FULL : SHAPE_EMPTY;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HAS_EYE, false);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(HAS_EYE) ? 15 : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_EYE);
    }

    public static BlockPattern getOrCreatePortalShape() {
        if (portalShape == null) {
            portalShape = BlockPatternBuilder.start()
                .aisle("?vvv?", ">???<", ">???<", ">???<", "?^^^?")
                .where('?', BlockInWorld.hasState(BlockStatePredicate.ANY))
                .where(
                    '^',
                    BlockInWorld.hasState(
                        BlockStatePredicate.forBlock(Blocks.END_PORTAL_FRAME)
                            .where(HAS_EYE, Predicates.equalTo(true))
                            .where(FACING, Predicates.equalTo(Direction.SOUTH))
                    )
                )
                .where(
                    '>',
                    BlockInWorld.hasState(
                        BlockStatePredicate.forBlock(Blocks.END_PORTAL_FRAME)
                            .where(HAS_EYE, Predicates.equalTo(true))
                            .where(FACING, Predicates.equalTo(Direction.WEST))
                    )
                )
                .where(
                    'v',
                    BlockInWorld.hasState(
                        BlockStatePredicate.forBlock(Blocks.END_PORTAL_FRAME)
                            .where(HAS_EYE, Predicates.equalTo(true))
                            .where(FACING, Predicates.equalTo(Direction.NORTH))
                    )
                )
                .where(
                    '<',
                    BlockInWorld.hasState(
                        BlockStatePredicate.forBlock(Blocks.END_PORTAL_FRAME)
                            .where(HAS_EYE, Predicates.equalTo(true))
                            .where(FACING, Predicates.equalTo(Direction.EAST))
                    )
                )
                .build();
        }

        return portalShape;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
