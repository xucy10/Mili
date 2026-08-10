package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SculkSensorBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<SculkSensorBlock> CODEC = simpleCodec(SculkSensorBlock::new);
    public static final int ACTIVE_TICKS = 30;
    public static final int COOLDOWN_TICKS = 10;
    public static final EnumProperty<SculkSensorPhase> PHASE = BlockStateProperties.SCULK_SENSOR_PHASE;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 8.0);
    private static final float[] RESONANCE_PITCH_BEND = Util.make(new float[16], pitchBends -> {
        int[] ints = new int[]{0, 0, 2, 4, 6, 7, 9, 10, 12, 14, 15, 18, 19, 21, 22, 24};

        for (int i = 0; i < 16; i++) {
            pitchBends[i] = NoteBlock.getPitchFromNote(ints[i]);
        }
    });

    @Override
    public MapCodec<? extends SculkSensorBlock> codec() {
        return CODEC;
    }

    public SculkSensorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PHASE, SculkSensorPhase.INACTIVE).setValue(POWER, 0).setValue(WATERLOGGED, false));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos clickedPos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(clickedPos);
        return this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (getPhase(state) != SculkSensorPhase.ACTIVE) {
            if (getPhase(state) == SculkSensorPhase.COOLDOWN) {
                level.setBlock(pos, state.setValue(PHASE, SculkSensorPhase.INACTIVE), Block.UPDATE_ALL);
                if (!state.getValue(WATERLOGGED)) {
                    level.playSound(null, pos, SoundEvents.SCULK_CLICKING_STOP, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.2F + 0.8F);
                }
            }
        } else {
            deactivate(level, pos, state);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide()
            && canActivate(state, pos) // Mili - Sound update suppression
            && entity.getType() != EntityType.WARDEN
            && level.getBlockEntity(pos) instanceof SculkSensorBlockEntity sculkSensorBlockEntity
            && level instanceof ServerLevel serverLevel
            && sculkSensorBlockEntity.getVibrationUser().canReceiveVibration(serverLevel, pos, GameEvent.STEP, GameEvent.Context.of(state))) {
            // CraftBukkit start
            org.bukkit.event.Cancellable cancellable;
            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(player, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
            } else {
                cancellable = new org.bukkit.event.entity.EntityInteractEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos));
                level.getCraftServer().getPluginManager().callEvent((org.bukkit.event.entity.EntityInteractEvent) cancellable);
            }
            if (cancellable.isCancelled()) {
                return;
            }
            // CraftBukkit end
            sculkSensorBlockEntity.getListener().forceScheduleVibration(serverLevel, GameEvent.STEP, GameEvent.Context.of(entity), entity.position());
        }

        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            if (state.getValue(POWER) > 0 && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.setBlock(pos, state.setValue(POWER, 0), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    // Leaves start - behaviour 1.21.1-
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (getPhase(state) == SculkSensorPhase.ACTIVE) {
                updateNeighbours(level, pos, state);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
    // Leaves end - behaviour 1.21.1-

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (getPhase(state) == SculkSensorPhase.ACTIVE) {
            updateNeighbours(level, pos, state);
        }
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

    private static void updateNeighbours(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.below(), block);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SculkSensorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return !level.isClientSide()
            ? createTickerHelper(
                blockEntityType,
                BlockEntityType.SCULK_SENSOR,
                (level1, pos, state1, blockEntity) -> VibrationSystem.Ticker.tick(level1, blockEntity.getVibrationData(), blockEntity.getVibrationUser())
            )
            : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? state.getSignal(level, pos, direction) : 0;
    }

    public static SculkSensorPhase getPhase(BlockState state) {
        return state.getValue(PHASE);
    }

    // Mili start - Sound update suppression
    public static boolean canActivate(BlockState state, BlockPos pos) {
        if (fun.bm.mili.config.modules.function.OldFeatureConfig.soundUpdateSuppression && !(state.getBlock() instanceof SculkSensorBlock)) {
            throw new org.leavesmc.leaves.util.UpdateSuppressionException(pos, null, state.getBlock(), null, new IllegalArgumentException());
        }
        return canActivate(state);
    }
    // Mili end - Sound update suppression

    public static boolean canActivate(BlockState state) {
        return state.getBlock() instanceof SculkSensorBlock &&  getPhase(state) == SculkSensorPhase.INACTIVE; // Paper - Check for a valid type
    }

    public static void deactivate(Level level, BlockPos pos, BlockState state) {
        // CraftBukkit start
        org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), state.getValue(SculkSensorBlock.POWER), 0);
        level.getCraftServer().getPluginManager().callEvent(eventRedstone);

        if (eventRedstone.getNewCurrent() > 0) {
            level.setBlock(pos, state.setValue(SculkSensorBlock.POWER, eventRedstone.getNewCurrent()), Block.UPDATE_ALL);
            return;
        }
        // CraftBukkit end
        level.setBlock(pos, state.setValue(PHASE, SculkSensorPhase.COOLDOWN).setValue(POWER, 0), Block.UPDATE_ALL);
        level.scheduleTick(pos, state.getBlock(), 10);
        updateNeighbours(level, pos, state);
    }

    @VisibleForTesting
    public int getActiveTicks() {
        return 30;
    }

    public void activate(@Nullable Entity entity, Level level, BlockPos pos, BlockState state, int power, int frequency) {
        // CraftBukkit start
        org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), state.getValue(SculkSensorBlock.POWER), power);
        level.getCraftServer().getPluginManager().callEvent(eventRedstone);

        if (eventRedstone.getNewCurrent() <= 0) {
            return;
        }
        power = eventRedstone.getNewCurrent();
        // CraftBukkit end
        level.setBlock(pos, state.setValue(PHASE, SculkSensorPhase.ACTIVE).setValue(POWER, power), Block.UPDATE_ALL);
        level.scheduleTick(pos, state.getBlock(), this.getActiveTicks());
        updateNeighbours(level, pos, state);
        tryResonateVibration(entity, level, pos, frequency);
        level.gameEvent(entity, GameEvent.SCULK_SENSOR_TENDRILS_CLICKING, pos);
        if (!state.getValue(WATERLOGGED)) {
            level.playSound(
                null,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                SoundEvents.SCULK_CLICKING,
                SoundSource.BLOCKS,
                1.0F,
                level.random.nextFloat() * 0.2F + 0.8F
            );
        }
    }

    public static void tryResonateVibration(@Nullable Entity entity, Level level, BlockPos pos, int frequency) {
        for (Direction direction : Direction.values()) {
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.is(BlockTags.VIBRATION_RESONATORS)) {
                level.gameEvent(VibrationSystem.getResonanceEventByFrequency(frequency), blockPos, GameEvent.Context.of(entity, blockState));
                float f = RESONANCE_PITCH_BEND[frequency];
                level.playSound(null, blockPos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, f);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (getPhase(state) == SculkSensorPhase.ACTIVE) {
            Direction random1 = Direction.getRandom(random);
            if (random1 != Direction.UP && random1 != Direction.DOWN) {
                double d = pos.getX() + 0.5 + (random1.getStepX() == 0 ? 0.5 - random.nextDouble() : random1.getStepX() * 0.6);
                double d1 = pos.getY() + 0.25;
                double d2 = pos.getZ() + 0.5 + (random1.getStepZ() == 0 ? 0.5 - random.nextDouble() : random1.getStepZ() * 0.6);
                double d3 = random.nextFloat() * 0.04;
                level.addParticle(DustColorTransitionOptions.SCULK_TO_REDSTONE, d, d1, d2, 0.0, d3, 0.0);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE, POWER, WATERLOGGED);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof SculkSensorBlockEntity sculkSensorBlockEntity) {
            return getPhase(state) == SculkSensorPhase.ACTIVE ? sculkSensorBlockEntity.getLastVibrationFrequency() : 0;
        } else {
            return 0;
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
        // CraftBukkit start - Delegate to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        if (dropExperience) {
            return this.tryDropExperience(level, pos, stack, ConstantInt.of(5));
        }

        return 0;
        // CraftBukkit end
    }
}
