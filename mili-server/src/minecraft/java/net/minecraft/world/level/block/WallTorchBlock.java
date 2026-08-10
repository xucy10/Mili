package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class WallTorchBlock extends TorchBlock {
    public static final MapCodec<WallTorchBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(PARTICLE_OPTIONS_FIELD.forGetter(wallTorchBlock -> wallTorchBlock.flameParticle), propertiesCodec())
            .apply(instance, WallTorchBlock::new)
    );
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(5.0, 3.0, 13.0, 11.0, 16.0));

    @Override
    public MapCodec<WallTorchBlock> codec() {
        return CODEC;
    }

    protected WallTorchBlock(SimpleParticleType flameParticle, BlockBehaviour.Properties properties) {
        super(flameParticle, properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state);
    }

    public static VoxelShape getShape(BlockState state) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSurvive(level, pos, state.getValue(FACING));
    }

    public static boolean canSurvive(LevelReader level, BlockPos pos, Direction facing) {
        BlockPos blockPos = pos.relative(facing.getOpposite());
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.isFaceSturdy(level, blockPos, facing);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = this.defaultBlockState();
        LevelReader level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction[] nearestLookingDirections = context.getNearestLookingDirections();

        for (Direction direction : nearestLookingDirections) {
            if (direction.getAxis().isHorizontal()) {
                Direction opposite = direction.getOpposite();
                blockState = blockState.setValue(FACING, opposite);
                if (blockState.canSurvive(level, clickedPos)) {
                    return blockState;
                }
            }
        }

        return null;
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
        return direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction direction = state.getValue(FACING);
        double d = pos.getX() + 0.5;
        double d1 = pos.getY() + 0.7;
        double d2 = pos.getZ() + 0.5;
        double d3 = 0.22;
        double d4 = 0.27;
        Direction opposite = direction.getOpposite();
        level.addParticle(ParticleTypes.SMOKE, d + 0.27 * opposite.getStepX(), d1 + 0.22, d2 + 0.27 * opposite.getStepZ(), 0.0, 0.0, 0.0);
        level.addParticle(this.flameParticle, d + 0.27 * opposite.getStepX(), d1 + 0.22, d2 + 0.27 * opposite.getStepZ(), 0.0, 0.0, 0.0);
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
        builder.add(FACING);
    }
}
