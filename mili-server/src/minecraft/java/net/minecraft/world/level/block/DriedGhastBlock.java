package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DriedGhastBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<DriedGhastBlock> CODEC = simpleCodec(DriedGhastBlock::new);
    public static final int MAX_HYDRATION_LEVEL = 3;
    public static final IntegerProperty HYDRATION_LEVEL = BlockStateProperties.DRIED_GHAST_HYDRATION_LEVELS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int HYDRATION_TICK_DELAY = 5000;
    private static final VoxelShape SHAPE = Block.column(10.0, 10.0, 0.0, 10.0);

    @Override
    public MapCodec<DriedGhastBlock> codec() {
        return CODEC;
    }

    public DriedGhastBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HYDRATION_LEVEL, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HYDRATION_LEVEL, WATERLOGGED);
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

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public int getHydrationLevel(BlockState state) {
        return state.getValue(HYDRATION_LEVEL);
    }

    private boolean isReadyToSpawn(BlockState state) {
        return this.getHydrationLevel(state) == 3;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            this.tickWaterlogged(state, level, pos, random);
        } else {
            int hydrationLevel = this.getHydrationLevel(state);
            if (hydrationLevel > 0) {
                level.setBlock(pos, state.setValue(HYDRATION_LEVEL, hydrationLevel - 1), Block.UPDATE_CLIENTS);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
            }
        }
    }

    private void tickWaterlogged(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.isReadyToSpawn(state)) {
            // Paper start - Call BlockGrowEvent
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, state.setValue(HYDRATION_LEVEL, this.getHydrationLevel(state) + 1), Block.UPDATE_CLIENTS)) {
                return;
            }
            // Paper end - Call BlockGrowEvent
            level.playSound(null, pos, SoundEvents.DRIED_GHAST_TRANSITION, SoundSource.BLOCKS, 1.0F, 1.0F);
            // level.setBlock(pos, state.setValue(HYDRATION_LEVEL, this.getHydrationLevel(state) + 1), Block.UPDATE_CLIENTS); // Paper - handled above
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
        } else {
            // Paper start - Call BlockFadeEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, state.getFluidState().createLegacyBlock()).isCancelled()) {
                return;
            }
            // Paper end - Call BlockFadeEvent
            this.spawnGhastling(level, pos, state);
        }
    }

    private void spawnGhastling(ServerLevel level, BlockPos pos, BlockState state) {
        level.removeBlock(pos, false);
        HappyGhast happyGhast = EntityType.HAPPY_GHAST.create(level, EntitySpawnReason.BREEDING);
        if (happyGhast != null) {
            Vec3 bottomCenter = pos.getBottomCenter();
            happyGhast.setBaby(true);
            float yRot = Direction.getYRot(state.getValue(FACING));
            happyGhast.setYHeadRot(yRot);
            happyGhast.snapTo(bottomCenter.x(), bottomCenter.y(), bottomCenter.z(), yRot, 0.0F);
            level.addFreshEntity(happyGhast, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REHYDRATION); // Paper - spawn reason
            level.playSound(null, happyGhast, SoundEvents.GHASTLING_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d = pos.getX() + 0.5;
        double d1 = pos.getY() + 0.5;
        double d2 = pos.getZ() + 0.5;
        if (!state.getValue(WATERLOGGED)) {
            if (random.nextInt(40) == 0 && level.getBlockState(pos.below()).is(BlockTags.TRIGGERS_AMBIENT_DRIED_GHAST_BLOCK_SOUNDS)) {
                level.playLocalSound(d, d1, d2, SoundEvents.DRIED_GHAST_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }

            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, d, d1, d2, 0.0, 0.02, 0.0);
            }
        } else {
            if (random.nextInt(40) == 0) {
                level.playLocalSound(d, d1, d2, SoundEvents.DRIED_GHAST_AMBIENT_WATER, SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }

            if (random.nextInt(6) == 0) {
                level.addParticle(
                    ParticleTypes.HAPPY_VILLAGER,
                    d + (random.nextFloat() * 2.0F - 1.0F) / 3.0F,
                    d1 + 0.4,
                    d2 + (random.nextFloat() * 2.0F - 1.0F) / 3.0F,
                    0.0,
                    random.nextFloat(),
                    0.0
                );
            }
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((state.getValue(WATERLOGGED) || state.getValue(HYDRATION_LEVEL) > 0) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 5000);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = fluidState.getType() == Fluids.WATER;
        return super.getStateForPlacement(context).setValue(WATERLOGGED, flag).setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!state.getValue(BlockStateProperties.WATERLOGGED) && fluidState.getType() == Fluids.WATER) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
                level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
                level.playSound(null, pos, SoundEvents.DRIED_GHAST_PLACE_IN_WATER, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.playSound(
            null, pos, state.getValue(WATERLOGGED) ? SoundEvents.DRIED_GHAST_PLACE_IN_WATER : SoundEvents.DRIED_GHAST_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F
        );
    }

    @Override
    public boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
