package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class LeavesBlock extends Block implements SimpleWaterloggedBlock {
    public static final int DECAY_DISTANCE = 7;
    public static final IntegerProperty DISTANCE = BlockStateProperties.DISTANCE;
    public static final BooleanProperty PERSISTENT = BlockStateProperties.PERSISTENT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected final float leafParticleChance;
    private static final int TICK_DELAY = 1;
    private static boolean cutoutLeaves = true;

    @Override
    public abstract MapCodec<? extends LeavesBlock> codec();

    public LeavesBlock(float leafParticleChance, BlockBehaviour.Properties properties) {
        super(properties);
        this.leafParticleChance = leafParticleChance;
        this.registerDefaultState(this.stateDefinition.any().setValue(DISTANCE, 7).setValue(PERSISTENT, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return !cutoutLeaves && adjacentState.getBlock() instanceof LeavesBlock || super.skipRendering(state, adjacentState, direction);
    }

    public static void setCutoutLeaves(boolean cutoutLeaves) {
        LeavesBlock.cutoutLeaves = cutoutLeaves;
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(DISTANCE) == 7 && !state.getValue(PERSISTENT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.decaying(state)) {
            // CraftBukkit start
            org.bukkit.event.block.LeavesDecayEvent event = new org.bukkit.event.block.LeavesDecayEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos));
            level.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled() || !level.getBlockState(pos).is(this)) {
                return;
            }
            // CraftBukkit end
            dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }
    }

    protected boolean decaying(BlockState state) {
        return !state.getValue(PERSISTENT) && state.getValue(DISTANCE) == 7;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, updateDistance(state, level, pos), Block.UPDATE_ALL);
    }

    @Override
    protected int getLightBlock(BlockState state) {
        return 1;
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
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        int i = getDistanceAt(neighborState) + 1;
        if (i != 1 || state.getValue(DISTANCE) != i) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        return state;
    }

    private static BlockState updateDistance(BlockState state, LevelAccessor level, BlockPos pos) {
        int i = 7;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            mutableBlockPos.setWithOffset(pos, direction);
            i = Math.min(i, getDistanceAt(level.getBlockState(mutableBlockPos)) + 1);
            if (i == 1) {
                break;
            }
        }

        return state.setValue(DISTANCE, i);
    }

    private static int getDistanceAt(BlockState neighbor) {
        return getOptionalDistanceAt(neighbor).orElse(7);
    }

    public static OptionalInt getOptionalDistanceAt(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return OptionalInt.of(0);
        } else {
            return state.hasProperty(DISTANCE) ? OptionalInt.of(state.getValue(DISTANCE)) : OptionalInt.empty();
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        makeDrippingWaterParticles(level, pos, random, blockState, blockPos);
        this.makeFallingLeavesParticles(level, pos, random, blockState, blockPos);
    }

    private static void makeDrippingWaterParticles(Level level, BlockPos pos, RandomSource random, BlockState blockBelow, BlockPos belowPos) {
        if (level.isRainingAt(pos.above())) {
            if (random.nextInt(15) == 1) {
                if (!blockBelow.canOcclude() || !blockBelow.isFaceSturdy(level, belowPos, Direction.UP)) {
                    ParticleUtils.spawnParticleBelow(level, pos, random, ParticleTypes.DRIPPING_WATER);
                }
            }
        }
    }

    private void makeFallingLeavesParticles(Level level, BlockPos pos, RandomSource random, BlockState blockBelow, BlockPos belowPos) {
        if (!(random.nextFloat() >= this.leafParticleChance)) {
            if (!isFaceFull(blockBelow.getCollisionShape(level, belowPos), Direction.UP)) {
                this.spawnFallingLeavesParticle(level, pos, random);
            }
        }
    }

    protected abstract void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DISTANCE, PERSISTENT, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        BlockState blockState = this.defaultBlockState().setValue(PERSISTENT, true).setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
        return updateDistance(blockState, context.getLevel(), context.getClickedPos());
    }
}
