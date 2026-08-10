package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Tilt;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BigDripleafBlock extends HorizontalDirectionalBlock implements BonemealableBlock, SimpleWaterloggedBlock {
    public static final MapCodec<BigDripleafBlock> CODEC = simpleCodec(BigDripleafBlock::new);
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final EnumProperty<Tilt> TILT = BlockStateProperties.TILT;
    private static final int NO_TICK = -1;
    private static final Object2IntMap<Tilt> DELAY_UNTIL_NEXT_TILT_STATE = Util.make(new Object2IntArrayMap<>(), map -> {
        map.defaultReturnValue(-1);
        map.put(Tilt.UNSTABLE, 10);
        map.put(Tilt.PARTIAL, 10);
        map.put(Tilt.FULL, 100);
    });
    private static final int MAX_GEN_HEIGHT = 5;
    private static final int ENTITY_DETECTION_MIN_Y = 11;
    private static final int LOWEST_LEAF_TOP = 13;
    private static final Map<Tilt, VoxelShape> SHAPE_LEAF = Maps.newEnumMap(
        Map.of(
            Tilt.NONE,
            Block.column(16.0, 11.0, 15.0),
            Tilt.UNSTABLE,
            Block.column(16.0, 11.0, 15.0),
            Tilt.PARTIAL,
            Block.column(16.0, 11.0, 13.0),
            Tilt.FULL,
            Shapes.empty()
        )
    );
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<BigDripleafBlock> codec() {
        return CODEC;
    }

    protected BigDripleafBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false).setValue(FACING, Direction.NORTH).setValue(TILT, Tilt.NONE));
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.column(6.0, 0.0, 13.0).move(0.0, 0.0, 0.25).optimize());
        return this.getShapeForEachState(blockState -> Shapes.or(SHAPE_LEAF.get(blockState.getValue(TILT)), map.get(blockState.getValue(FACING))), WATERLOGGED);
    }

    public static void placeWithRandomHeight(LevelAccessor level, RandomSource random, BlockPos pos, Direction direction) {
        int randomInt = Mth.nextInt(random, 2, 5);
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        int i = 0;

        while (i < randomInt && canPlaceAt(level, mutableBlockPos, level.getBlockState(mutableBlockPos))) {
            i++;
            mutableBlockPos.move(Direction.UP);
        }

        int i1 = pos.getY() + i - 1;
        mutableBlockPos.setY(pos.getY());

        while (mutableBlockPos.getY() < i1) {
            BigDripleafStemBlock.place(level, mutableBlockPos, level.getFluidState(mutableBlockPos), direction);
            mutableBlockPos.move(Direction.UP);
        }

        place(level, mutableBlockPos, level.getFluidState(mutableBlockPos), direction);
    }

    private static boolean canReplace(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.SMALL_DRIPLEAF);
    }

    protected static boolean canPlaceAt(LevelHeightAccessor level, BlockPos pos, BlockState state) {
        return !level.isOutsideBuildHeight(pos) && canReplace(state);
    }

    protected static boolean place(LevelAccessor level, BlockPos pos, FluidState fluidState, Direction direction) {
        BlockState blockState = Blocks.BIG_DRIPLEAF
            .defaultBlockState()
            .setValue(WATERLOGGED, fluidState.isSourceOfType(Fluids.WATER))
            .setValue(FACING, direction);
        return level.setBlock(pos, blockState, Block.UPDATE_ALL);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        this.setTiltAndScheduleTick(state, level, hit.getBlockPos(), Tilt.FULL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, projectile); // CraftBukkit
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.is(this) || blockState.is(Blocks.BIG_DRIPLEAF_STEM) || blockState.is(BlockTags.BIG_DRIPLEAF_PLACEABLE);
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
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if (state.getValue(WATERLOGGED)) {
                scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return direction == Direction.UP && neighborState.is(this)
                ? Blocks.BIG_DRIPLEAF_STEM.withPropertiesOf(state)
                : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockState blockState = level.getBlockState(pos.above());
        return canReplace(blockState);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockPos = pos.above();
        BlockState blockState = level.getBlockState(blockPos);
        if (canPlaceAt(level, blockPos, blockState)) {
            Direction direction = state.getValue(FACING);
            BigDripleafStemBlock.place(level, pos, state.getFluidState(), direction);
            place(level, blockPos, blockState.getFluidState(), direction);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (!level.isClientSide()) {
            if (state.getValue(TILT) == Tilt.NONE && canEntityTilt(pos, entity) && !level.hasNeighborSignal(pos)) {
                // CraftBukkit start - tilt dripleaf
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
                this.setTiltAndScheduleTick(state, level, pos, Tilt.UNSTABLE, null, entity);
                // CraftBukkit end
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.hasNeighborSignal(pos)) {
            resetTilt(state, level, pos);
        } else {
            Tilt tilt = state.getValue(TILT);
            if (tilt == Tilt.UNSTABLE) {
                this.setTiltAndScheduleTick(state, level, pos, Tilt.PARTIAL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, null); // CraftBukkit
            } else if (tilt == Tilt.PARTIAL) {
                this.setTiltAndScheduleTick(state, level, pos, Tilt.FULL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, null); // CraftBukkit
            } else if (tilt == Tilt.FULL) {
                resetTilt(state, level, pos);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.hasNeighborSignal(pos)) {
            resetTilt(state, level, pos);
        }
    }

    private static void playTiltSound(Level level, BlockPos pos, SoundEvent sound) {
        float f = Mth.randomBetween(level.random, 0.8F, 1.2F);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, f);
    }

    private static boolean canEntityTilt(BlockPos pos, Entity entity) {
        return entity.onGround() && entity.position().y > pos.getY() + 0.6875F;
    }

    private void setTiltAndScheduleTick(BlockState state, Level level, BlockPos pos, Tilt tilt, @Nullable SoundEvent sound, @Nullable Entity entity) {
        if (!setTilt(state, level, pos, tilt, entity)) {
            return;
        }
        // CraftBukkit end
        if (sound != null) {
            playTiltSound(level, pos, sound);
        }

        int _int = DELAY_UNTIL_NEXT_TILT_STATE.getInt(tilt);
        if (_int != -1) {
            level.scheduleTick(pos, this, _int);
        }
    }

    private static void resetTilt(BlockState state, Level level, BlockPos pos) {
        setTilt(state, level, pos, Tilt.NONE, null); // CraftBukkit
        if (state.getValue(TILT) != Tilt.NONE) {
            playTiltSound(level, pos, SoundEvents.BIG_DRIPLEAF_TILT_UP);
        }
    }

    // CraftBukkit start
    private static boolean setTilt(BlockState state, Level level, BlockPos pos, Tilt tilt, @Nullable Entity entity) {
        if (entity != null) {
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, state.setValue(BigDripleafBlock.TILT, tilt))) {
                return false;
            }
        }
        // CraftBukkit end
        Tilt tilt1 = state.getValue(TILT);
        level.setBlock(pos, state.setValue(TILT, tilt), Block.UPDATE_CLIENTS);
        if (tilt.causesVibration() && tilt != tilt1) {
            level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
        }
        return true; // CraftBukkit
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_LEAF.get(state.getValue(TILT));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos().below());
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = blockState.is(Blocks.BIG_DRIPLEAF) || blockState.is(Blocks.BIG_DRIPLEAF_STEM);
        return this.defaultBlockState()
            .setValue(WATERLOGGED, fluidState.isSourceOfType(Fluids.WATER))
            .setValue(FACING, flag ? blockState.getValue(FACING) : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, FACING, TILT);
    }
}
