package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
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
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PointedDripstoneBlock extends Block implements Fallable, SimpleWaterloggedBlock {
    public static final MapCodec<PointedDripstoneBlock> CODEC = simpleCodec(PointedDripstoneBlock::new);
    public static final EnumProperty<Direction> TIP_DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
    public static final EnumProperty<DripstoneThickness> THICKNESS = BlockStateProperties.DRIPSTONE_THICKNESS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final int MAX_SEARCH_LENGTH_WHEN_CHECKING_DRIP_TYPE = 11;
    private static final int DELAY_BEFORE_FALLING = 2;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK = 0.02F;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK_IF_UNDER_LIQUID_SOURCE = 0.12F;
    private static final int MAX_SEARCH_LENGTH_BETWEEN_STALACTITE_TIP_AND_CAULDRON = 11;
    private static final float WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.17578125F;
    private static final float LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.05859375F;
    private static final double MIN_TRIDENT_VELOCITY_TO_BREAK_DRIPSTONE = 0.6;
    private static final float STALACTITE_DAMAGE_PER_FALL_DISTANCE_AND_SIZE = 1.0F;
    private static final int STALACTITE_MAX_DAMAGE = 40;
    private static final int MAX_STALACTITE_HEIGHT_FOR_DAMAGE_CALCULATION = 6;
    private static final float STALAGMITE_FALL_DISTANCE_OFFSET = 2.5F;
    private static final int STALAGMITE_FALL_DAMAGE_MODIFIER = 2;
    private static final float AVERAGE_DAYS_PER_GROWTH = 5.0F;
    private static final float GROWTH_PROBABILITY_PER_RANDOM_TICK = 0.011377778F;
    private static final int MAX_GROWTH_LENGTH = 7;
    private static final int MAX_STALAGMITE_SEARCH_RANGE_WHEN_GROWING = 10;
    private static final VoxelShape SHAPE_TIP_MERGE = Block.column(6.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_TIP_UP = Block.column(6.0, 0.0, 11.0);
    private static final VoxelShape SHAPE_TIP_DOWN = Block.column(6.0, 5.0, 16.0);
    private static final VoxelShape SHAPE_FRUSTUM = Block.column(8.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_MIDDLE = Block.column(10.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_BASE = Block.column(12.0, 0.0, 16.0);
    private static final double STALACTITE_DRIP_START_PIXEL = SHAPE_TIP_DOWN.min(Direction.Axis.Y);
    private static final float MAX_HORIZONTAL_OFFSET = (float)SHAPE_BASE.min(Direction.Axis.X);
    private static final VoxelShape REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK = Block.column(4.0, 0.0, 16.0);

    @Override
    public MapCodec<PointedDripstoneBlock> codec() {
        return CODEC;
    }

    public PointedDripstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(TIP_DIRECTION, Direction.UP).setValue(THICKNESS, DripstoneThickness.TIP).setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIP_DIRECTION, THICKNESS, WATERLOGGED);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isValidPointedDripstonePlacement(level, pos, state.getValue(TIP_DIRECTION));
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

        if (direction != Direction.UP && direction != Direction.DOWN) {
            return state;
        } else {
            Direction direction1 = state.getValue(TIP_DIRECTION);
            if (direction1 == Direction.DOWN && scheduledTickAccess.getBlockTicks().hasScheduledTick(pos, this)) {
                return state;
            } else if (direction == direction1.getOpposite() && !this.canSurvive(state, level, pos)) {
                if (direction1 == Direction.DOWN) {
                    scheduledTickAccess.scheduleTick(pos, this, 2);
                } else {
                    scheduledTickAccess.scheduleTick(pos, this, 1);
                }

                return state;
            } else {
                boolean flag = state.getValue(THICKNESS) == DripstoneThickness.TIP_MERGE;
                DripstoneThickness dripstoneThickness = calculateDripstoneThickness(level, pos, direction1, flag);
                return state.setValue(THICKNESS, dripstoneThickness);
            }
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide()) {
            BlockPos blockPos = hit.getBlockPos();
            if (level instanceof ServerLevel serverLevel
                && projectile.mayInteract(serverLevel, blockPos)
                && projectile.mayBreak(serverLevel)
                && projectile instanceof ThrownTrident
                && projectile.getDeltaMovement().length() > 0.6) {
                // CraftBukkit start
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(projectile, blockPos, state.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                    return;
                }
                // CraftBukkit end
                level.destroyBlock(blockPos, true);
            }
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        if (state.getValue(TIP_DIRECTION) == Direction.UP && state.getValue(THICKNESS) == DripstoneThickness.TIP) {
            entity.causeFallDamage(fallDistance + 2.5, 2.0F, level.damageSources().stalagmite().eventBlockDamager(level, pos)); // CraftBukkit
        } else {
            super.fallOn(level, state, pos, entity, fallDistance);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (canDrip(state)) {
            float randomFloat = random.nextFloat();
            if (!(randomFloat > 0.12F)) {
                getFluidAboveStalactite(level, pos, state)
                    .filter(fluidInfo -> randomFloat < 0.02F || canFillCauldron(fluidInfo.fluid))
                    .ifPresent(fluidInfo -> spawnDripParticle(level, pos, state, fluidInfo.fluid, fluidInfo.pos));
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isStalagmite(state) && !this.canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        } else {
            spawnFallingStalactite(state, level, pos);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        maybeTransferFluid(state, level, pos, random.nextFloat());
        if (random.nextFloat() < 0.011377778F && isStalactiteStartPos(state, level, pos)) {
            growStalactiteOrStalagmiteIfPossible(state, level, pos, random);
        }
    }

    @VisibleForTesting
    public static void maybeTransferFluid(BlockState state, ServerLevel level, BlockPos pos, float randChance) {
        if (!(randChance > 0.17578125F) || !(randChance > 0.05859375F)) {
            if (isStalactiteStartPos(state, level, pos)) {
                Optional<PointedDripstoneBlock.FluidInfo> fluidAboveStalactite = getFluidAboveStalactite(level, pos, state);
                if (!fluidAboveStalactite.isEmpty()) {
                    Fluid fluid = fluidAboveStalactite.get().fluid;
                    float f;
                    if (fluid == Fluids.WATER) {
                        f = 0.17578125F;
                    } else {
                        if (fluid != Fluids.LAVA) {
                            return;
                        }

                        f = 0.05859375F;
                    }

                    if (!(randChance >= f)) {
                        BlockPos blockPos = findTip(state, level, pos, 11, false);
                        if (blockPos != null) {
                            if (fluidAboveStalactite.get().sourceState.is(Blocks.MUD) && fluid == Fluids.WATER) {
                                BlockState blockState = Blocks.CLAY.defaultBlockState();
                                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(level, fluidAboveStalactite.get().pos, blockState, Block.UPDATE_ALL)) { // Paper - Call BlockFormEvent
                                Block.pushEntitiesUp(fluidAboveStalactite.get().sourceState, blockState, level, fluidAboveStalactite.get().pos);
                                level.gameEvent(GameEvent.BLOCK_CHANGE, fluidAboveStalactite.get().pos, GameEvent.Context.of(blockState));
                                level.levelEvent(LevelEvent.DRIPSTONE_DRIP, blockPos, 0);
                                } // Paper - Call BlockFormEvent
                            } else {
                                BlockPos blockPos1 = findFillableCauldronBelowStalactiteTip(level, blockPos, fluid);
                                if (blockPos1 != null) {
                                    level.levelEvent(LevelEvent.DRIPSTONE_DRIP, blockPos, 0);
                                    int i = blockPos.getY() - blockPos1.getY();
                                    int i1 = 50 + i;
                                    BlockState blockState1 = level.getBlockState(blockPos1);
                                    level.scheduleTick(blockPos1, blockState1.getBlock(), i1);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction opposite = context.getNearestLookingVerticalDirection().getOpposite();
        Direction direction = calculateTipDirection(level, clickedPos, opposite);
        if (direction == null) {
            return null;
        } else {
            boolean flag = !context.isSecondaryUseActive();
            DripstoneThickness dripstoneThickness = calculateDripstoneThickness(level, clickedPos, direction, flag);
            return this.defaultBlockState()
                .setValue(TIP_DIRECTION, direction)
                .setValue(THICKNESS, dripstoneThickness)
                .setValue(WATERLOGGED, level.getFluidState(clickedPos).getType() == Fluids.WATER);
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelShape = switch ((DripstoneThickness)state.getValue(THICKNESS)) {
            case TIP_MERGE -> SHAPE_TIP_MERGE;
            case TIP -> state.getValue(TIP_DIRECTION) == Direction.DOWN ? SHAPE_TIP_DOWN : SHAPE_TIP_UP;
            case FRUSTUM -> SHAPE_FRUSTUM;
            case MIDDLE -> SHAPE_MIDDLE;
            case BASE -> SHAPE_BASE;
        };
        return voxelShape.move(state.getOffset(pos));
    }

    @Override
    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    protected float getMaxHorizontalOffset() {
        return MAX_HORIZONTAL_OFFSET;
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        if (!fallingBlock.isSilent()) {
            level.levelEvent(LevelEvent.SOUND_POINTED_DRIPSTONE_LAND, pos, 0);
        }
    }

    @Override
    public DamageSource getFallDamageSource(Entity entity) {
        return entity.damageSources().fallingStalactite(entity);
    }

    private static void spawnFallingStalactite(BlockState state, ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        BlockState blockState = state;

        while (isStalactite(blockState)) {
            FallingBlockEntity fallingBlockEntity = FallingBlockEntity.fall(level, mutableBlockPos, blockState);
            if (isTip(blockState, true)) {
                int max = Math.max(1 + pos.getY() - mutableBlockPos.getY(), 6);
                float f = 1.0F * max;
                fallingBlockEntity.setHurtsEntities(f, 40);
                break;
            }

            mutableBlockPos.move(Direction.DOWN);
            blockState = level.getBlockState(mutableBlockPos);
        }
    }

    @VisibleForTesting
    public static void growStalactiteOrStalagmiteIfPossible(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState blockState = level.getBlockState(pos.above(1));
        BlockState blockState1 = level.getBlockState(pos.above(2));
        if (canGrow(blockState, blockState1)) {
            BlockPos blockPos = findTip(state, level, pos, 7, false);
            if (blockPos != null) {
                BlockState blockState2 = level.getBlockState(blockPos);
                if (canDrip(blockState2) && canTipGrow(blockState2, level, blockPos)) {
                    if (random.nextBoolean()) {
                        grow(level, blockPos, Direction.DOWN);
                    } else {
                        growStalagmiteBelow(level, blockPos);
                    }
                }
            }
        }
    }

    private static void growStalagmiteBelow(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (int i = 0; i < 10; i++) {
            mutableBlockPos.move(Direction.DOWN);
            BlockState blockState = level.getBlockState(mutableBlockPos);
            if (!blockState.getFluidState().isEmpty()) {
                return;
            }

            if (isUnmergedTipWithDirection(blockState, Direction.UP) && canTipGrow(blockState, level, mutableBlockPos)) {
                grow(level, mutableBlockPos, Direction.UP);
                return;
            }

            if (isValidPointedDripstonePlacement(level, mutableBlockPos, Direction.UP) && !level.isWaterAt(mutableBlockPos.below())) {
                grow(level, mutableBlockPos.below(), Direction.UP);
                return;
            }

            if (!canDripThrough(level, mutableBlockPos, blockState)) {
                return;
            }
        }
    }

    private static void grow(ServerLevel server, BlockPos pos, Direction direction) {
        BlockPos blockPos = pos.relative(direction);
        BlockState blockState = server.getBlockState(blockPos);
        if (isUnmergedTipWithDirection(blockState, direction.getOpposite())) {
            createMergedTips(blockState, server, blockPos);
        } else if (blockState.isAir() || blockState.is(Blocks.WATER)) {
            createDripstone(server, blockPos, direction, DripstoneThickness.TIP, pos); // CraftBukkit
        }
    }

    private static void createDripstone(LevelAccessor level, BlockPos pos, Direction direction, DripstoneThickness thickness, BlockPos source) { // CraftBukkit
        BlockState blockState = Blocks.POINTED_DRIPSTONE
            .defaultBlockState()
            .setValue(TIP_DIRECTION, direction)
            .setValue(THICKNESS, thickness)
            .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, source, pos, blockState, Block.UPDATE_ALL); // CraftBukkit
    }

    private static void createMergedTips(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockPos blockPos1;
        BlockPos blockPos;
        if (state.getValue(TIP_DIRECTION) == Direction.UP) {
            blockPos = pos;
            blockPos1 = pos.above();
        } else {
            blockPos1 = pos;
            blockPos = pos.below();
        }

        createDripstone(level, blockPos1, Direction.DOWN, DripstoneThickness.TIP_MERGE, pos); // CraftBukkit
        createDripstone(level, blockPos, Direction.UP, DripstoneThickness.TIP_MERGE, pos); // CraftBukkit
    }

    public static void spawnDripParticle(Level level, BlockPos pos, BlockState state) {
        getFluidAboveStalactite(level, pos, state).ifPresent(fluidInfo -> spawnDripParticle(level, pos, state, fluidInfo.fluid, fluidInfo.pos));
    }

    private static void spawnDripParticle(Level level, BlockPos pos, BlockState state, Fluid fluid, BlockPos fluidPos) {
        Vec3 offset = state.getOffset(pos);
        double d = 0.0625;
        double d1 = pos.getX() + 0.5 + offset.x;
        double d2 = pos.getY() + STALACTITE_DRIP_START_PIXEL - 0.0625;
        double d3 = pos.getZ() + 0.5 + offset.z;
        ParticleOptions dripParticle = getDripParticle(level, fluid, fluidPos);
        level.addParticle(dripParticle, d1, d2, d3, 0.0, 0.0, 0.0);
    }

    private static @Nullable BlockPos findTip(BlockState state, LevelAccessor level, BlockPos pos, int maxIterations, boolean isTipMerge) {
        if (isTip(state, isTipMerge)) {
            return pos;
        } else {
            Direction direction = state.getValue(TIP_DIRECTION);
            BiPredicate<BlockPos, BlockState> biPredicate = (predPos, predState) -> predState.is(Blocks.POINTED_DRIPSTONE)
                && predState.getValue(TIP_DIRECTION) == direction;
            return findBlockVertical(level, pos, direction.getAxisDirection(), biPredicate, predState -> isTip(predState, isTipMerge), maxIterations)
                .orElse(null);
        }
    }

    private static @Nullable Direction calculateTipDirection(LevelReader level, BlockPos pos, Direction dir) {
        Direction direction;
        if (isValidPointedDripstonePlacement(level, pos, dir)) {
            direction = dir;
        } else {
            if (!isValidPointedDripstonePlacement(level, pos, dir.getOpposite())) {
                return null;
            }

            direction = dir.getOpposite();
        }

        return direction;
    }

    private static DripstoneThickness calculateDripstoneThickness(LevelReader level, BlockPos pos, Direction dir, boolean isTipMerge) {
        Direction opposite = dir.getOpposite();
        BlockState blockState = level.getBlockState(pos.relative(dir));
        if (isPointedDripstoneWithDirection(blockState, opposite)) {
            return !isTipMerge && blockState.getValue(THICKNESS) != DripstoneThickness.TIP_MERGE ? DripstoneThickness.TIP : DripstoneThickness.TIP_MERGE;
        } else if (!isPointedDripstoneWithDirection(blockState, dir)) {
            return DripstoneThickness.TIP;
        } else {
            DripstoneThickness dripstoneThickness = blockState.getValue(THICKNESS);
            if (dripstoneThickness != DripstoneThickness.TIP && dripstoneThickness != DripstoneThickness.TIP_MERGE) {
                BlockState blockState1 = level.getBlockState(pos.relative(opposite));
                return !isPointedDripstoneWithDirection(blockState1, dir) ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            } else {
                return DripstoneThickness.FRUSTUM;
            }
        }
    }

    public static boolean canDrip(BlockState state) {
        return isStalactite(state) && state.getValue(THICKNESS) == DripstoneThickness.TIP && !state.getValue(WATERLOGGED);
    }

    private static boolean canTipGrow(BlockState state, ServerLevel level, BlockPos pos) {
        Direction direction = state.getValue(TIP_DIRECTION);
        BlockPos blockPos = pos.relative(direction);
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.getFluidState().isEmpty() && (blockState.isAir() || isUnmergedTipWithDirection(blockState, direction.getOpposite()));
    }

    private static Optional<BlockPos> findRootBlock(Level level, BlockPos pos, BlockState state, int maxIterations) {
        Direction direction = state.getValue(TIP_DIRECTION);
        BiPredicate<BlockPos, BlockState> biPredicate = (predPos, predState) -> predState.is(Blocks.POINTED_DRIPSTONE)
            && predState.getValue(TIP_DIRECTION) == direction;
        return findBlockVertical(
            level, pos, direction.getOpposite().getAxisDirection(), biPredicate, predState -> !predState.is(Blocks.POINTED_DRIPSTONE), maxIterations
        );
    }

    private static boolean isValidPointedDripstonePlacement(LevelReader level, BlockPos pos, Direction dir) {
        BlockPos blockPos = pos.relative(dir.getOpposite());
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.isFaceSturdy(level, blockPos, dir) || isPointedDripstoneWithDirection(blockState, dir);
    }

    private static boolean isTip(BlockState state, boolean isTipMerge) {
        if (!state.is(Blocks.POINTED_DRIPSTONE)) {
            return false;
        } else {
            DripstoneThickness dripstoneThickness = state.getValue(THICKNESS);
            return dripstoneThickness == DripstoneThickness.TIP || isTipMerge && dripstoneThickness == DripstoneThickness.TIP_MERGE;
        }
    }

    private static boolean isUnmergedTipWithDirection(BlockState state, Direction dir) {
        return isTip(state, false) && state.getValue(TIP_DIRECTION) == dir;
    }

    private static boolean isStalactite(BlockState state) {
        return isPointedDripstoneWithDirection(state, Direction.DOWN);
    }

    private static boolean isStalagmite(BlockState state) {
        return isPointedDripstoneWithDirection(state, Direction.UP);
    }

    private static boolean isStalactiteStartPos(BlockState state, LevelReader level, BlockPos pos) {
        return isStalactite(state) && !level.getBlockState(pos.above()).is(Blocks.POINTED_DRIPSTONE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    private static boolean isPointedDripstoneWithDirection(BlockState state, Direction dir) {
        return state.is(Blocks.POINTED_DRIPSTONE) && state.getValue(TIP_DIRECTION) == dir;
    }

    private static @Nullable BlockPos findFillableCauldronBelowStalactiteTip(Level level, BlockPos pos, Fluid fluid) {
        Predicate<BlockState> predicate = state -> state.getBlock() instanceof AbstractCauldronBlock
            && ((AbstractCauldronBlock)state.getBlock()).canReceiveStalactiteDrip(fluid);
        BiPredicate<BlockPos, BlockState> biPredicate = (predPos, predState) -> canDripThrough(level, predPos, predState);
        return findBlockVertical(level, pos, Direction.DOWN.getAxisDirection(), biPredicate, predicate, 11).orElse(null);
    }

    public static @Nullable BlockPos findStalactiteTipAboveCauldron(Level level, BlockPos pos) {
        BiPredicate<BlockPos, BlockState> biPredicate = (predPos, predState) -> canDripThrough(level, predPos, predState);
        return findBlockVertical(level, pos, Direction.UP.getAxisDirection(), biPredicate, PointedDripstoneBlock::canDrip, 11).orElse(null);
    }

    public static Fluid getCauldronFillFluidType(ServerLevel level, BlockPos pos) {
        return getFluidAboveStalactite(level, pos, level.getBlockState(pos))
            .map(fluidInfo -> fluidInfo.fluid)
            .filter(PointedDripstoneBlock::canFillCauldron)
            .orElse(Fluids.EMPTY);
    }

    private static Optional<PointedDripstoneBlock.FluidInfo> getFluidAboveStalactite(Level level, BlockPos pos, BlockState state) {
        return !isStalactite(state) ? Optional.empty() : findRootBlock(level, pos, state, 11).map(rootBlockPos -> {
            BlockPos blockPos = rootBlockPos.above();
            BlockState blockState = level.getBlockState(blockPos);
            Fluid fluid;
            if (blockState.is(Blocks.MUD) && !level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, blockPos)) {
                fluid = Fluids.WATER;
            } else {
                fluid = level.getFluidState(blockPos).getType();
            }

            return new PointedDripstoneBlock.FluidInfo(blockPos, fluid, blockState);
        });
    }

    private static boolean canFillCauldron(Fluid fluid) {
        return fluid == Fluids.LAVA || fluid == Fluids.WATER;
    }

    private static boolean canGrow(BlockState dripstoneState, BlockState state) {
        return dripstoneState.is(Blocks.DRIPSTONE_BLOCK) && state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    private static ParticleOptions getDripParticle(Level level, Fluid fluid, BlockPos pos) {
        if (fluid.isSame(Fluids.EMPTY)) {
            return level.environmentAttributes().getValue(EnvironmentAttributes.DEFAULT_DRIPSTONE_PARTICLE, pos);
        } else {
            return fluid.is(FluidTags.LAVA) ? ParticleTypes.DRIPPING_DRIPSTONE_LAVA : ParticleTypes.DRIPPING_DRIPSTONE_WATER;
        }
    }

    private static Optional<BlockPos> findBlockVertical(
        LevelAccessor level,
        BlockPos pos,
        Direction.AxisDirection axis,
        BiPredicate<BlockPos, BlockState> positionalStatePredicate,
        Predicate<BlockState> statePredicate,
        int maxIterations
    ) {
        Direction direction = Direction.get(axis, Direction.Axis.Y);
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (int i = 1; i < maxIterations; i++) {
            mutableBlockPos.move(direction);
            BlockState blockState = level.getBlockState(mutableBlockPos);
            if (statePredicate.test(blockState)) {
                return Optional.of(mutableBlockPos.immutable());
            }

            if (level.isOutsideBuildHeight(mutableBlockPos.getY()) || !positionalStatePredicate.test(mutableBlockPos, blockState)) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static boolean canDripThrough(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        } else if (state.isSolidRender()) {
            return false;
        } else if (!state.getFluidState().isEmpty()) {
            return false;
        } else {
            VoxelShape collisionShape = state.getCollisionShape(level, pos);
            return !Shapes.joinIsNotEmpty(REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK, collisionShape, BooleanOp.AND);
        }
    }

    record FluidInfo(BlockPos pos, Fluid fluid, BlockState sourceState) {
    }
}
