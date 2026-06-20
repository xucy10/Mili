package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.redstone.DefaultRedstoneWireEvaluator;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.ExperimentalRedstoneWireEvaluator;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.redstone.RedstoneWireEvaluator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class RedStoneWireBlock extends Block {
    public static final MapCodec<RedStoneWireBlock> CODEC = simpleCodec(RedStoneWireBlock::new);
    public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> EAST = BlockStateProperties.EAST_REDSTONE;
    public static final EnumProperty<RedstoneSide> SOUTH = BlockStateProperties.SOUTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> WEST = BlockStateProperties.WEST_REDSTONE;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final Map<Direction, EnumProperty<RedstoneSide>> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(
        Maps.newEnumMap(Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST))
    );
    private static final int[] COLORS = Util.make(new int[16], ints -> {
        for (int i = 0; i <= 15; i++) {
            float f = i / 15.0F;
            float f1 = f * 0.6F + (f > 0.0F ? 0.4F : 0.3F);
            float f2 = Mth.clamp(f * f * 0.7F - 0.5F, 0.0F, 1.0F);
            float f3 = Mth.clamp(f * f * 0.6F - 0.7F, 0.0F, 1.0F);
            ints[i] = ARGB.colorFromFloat(1.0F, f1, f2, f3);
        }
    });
    private static final float PARTICLE_DENSITY = 0.2F;
    private final Function<BlockState, VoxelShape> shapes;
    private final BlockState crossState;
    private final RedstoneWireEvaluator evaluator = new DefaultRedstoneWireEvaluator(this);
    //public boolean shouldSignal = true; // Folia - region threading - move to regionised world data

    @Override
    public MapCodec<RedStoneWireBlock> codec() {
        return CODEC;
    }

    public RedStoneWireBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(NORTH, RedstoneSide.NONE)
                .setValue(EAST, RedstoneSide.NONE)
                .setValue(SOUTH, RedstoneSide.NONE)
                .setValue(WEST, RedstoneSide.NONE)
                .setValue(POWER, 0)
        );
        this.shapes = this.makeShapes();
        this.crossState = this.defaultBlockState()
            .setValue(NORTH, RedstoneSide.SIDE)
            .setValue(EAST, RedstoneSide.SIDE)
            .setValue(SOUTH, RedstoneSide.SIDE)
            .setValue(WEST, RedstoneSide.SIDE);
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        int i = 1;
        int i1 = 10;
        VoxelShape voxelShape = Block.column(10.0, 0.0, 1.0);
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.boxZ(10.0, 0.0, 1.0, 0.0, 8.0));
        Map<Direction, VoxelShape> map1 = Shapes.rotateHorizontal(Block.boxZ(10.0, 16.0, 0.0, 1.0));
        return this.getShapeForEachState(blockState -> {
            VoxelShape voxelShape1 = voxelShape;

            for (Entry<Direction, EnumProperty<RedstoneSide>> entry : PROPERTY_BY_DIRECTION.entrySet()) {
                voxelShape1 = switch ((RedstoneSide)blockState.getValue(entry.getValue())) {
                    case UP -> Shapes.or(voxelShape1, map.get(entry.getKey()), map1.get(entry.getKey()));
                    case SIDE -> Shapes.or(voxelShape1, map.get(entry.getKey()));
                    case NONE -> voxelShape1;
                };
            }

            return voxelShape1;
        }, POWER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.getConnectionState(context.getLevel(), this.crossState, context.getClickedPos());
    }

    private BlockState getConnectionState(BlockGetter level, BlockState state, BlockPos pos) {
        boolean isDot = isDot(state);
        state = this.getMissingConnections(level, this.defaultBlockState().setValue(POWER, state.getValue(POWER)), pos);
        if (isDot && isDot(state)) {
            return state;
        } else {
            boolean isConnected = state.getValue(NORTH).isConnected();
            boolean isConnected1 = state.getValue(SOUTH).isConnected();
            boolean isConnected2 = state.getValue(EAST).isConnected();
            boolean isConnected3 = state.getValue(WEST).isConnected();
            boolean flag = !isConnected && !isConnected1;
            boolean flag1 = !isConnected2 && !isConnected3;
            if (!isConnected3 && flag) {
                state = state.setValue(WEST, RedstoneSide.SIDE);
            }

            if (!isConnected2 && flag) {
                state = state.setValue(EAST, RedstoneSide.SIDE);
            }

            if (!isConnected && flag1) {
                state = state.setValue(NORTH, RedstoneSide.SIDE);
            }

            if (!isConnected1 && flag1) {
                state = state.setValue(SOUTH, RedstoneSide.SIDE);
            }

            return state;
        }
    }

    private BlockState getMissingConnections(BlockGetter level, BlockState state, BlockPos pos) {
        boolean flag = !level.getBlockState(pos.above()).isRedstoneConductor(level, pos);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!state.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected()) {
                RedstoneSide connectingSide = this.getConnectingSide(level, pos, direction, flag);
                state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectingSide);
            }
        }

        return state;
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
        if (direction == Direction.DOWN) {
            return !this.canSurviveOn(level, neighborPos, neighborState) ? Blocks.AIR.defaultBlockState() : state;
        } else if (direction == Direction.UP) {
            return this.getConnectionState(level, state, pos);
        } else {
            RedstoneSide connectingSide = this.getConnectingSide(level, pos, direction);
            return connectingSide.isConnected() == state.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected() && !isCross(state)
                ? state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectingSide)
                : this.getConnectionState(
                    level, this.crossState.setValue(POWER, state.getValue(POWER)).setValue(PROPERTY_BY_DIRECTION.get(direction), connectingSide), pos
                );
        }
    }

    private static boolean isCross(BlockState state) {
        return state.getValue(NORTH).isConnected()
            && state.getValue(SOUTH).isConnected()
            && state.getValue(EAST).isConnected()
            && state.getValue(WEST).isConnected();
    }

    private static boolean isDot(BlockState state) {
        return !state.getValue(NORTH).isConnected()
            && !state.getValue(SOUTH).isConnected()
            && !state.getValue(EAST).isConnected()
            && !state.getValue(WEST).isConnected();
    }

    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, @Block.UpdateFlags int flags, int recursionLeft) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState currState; mutableBlockPos.setWithOffset(pos, direction); // Folia - block updates in unloaded chunks
            RedstoneSide redstoneSide = state.getValue(PROPERTY_BY_DIRECTION.get(direction));
            if (redstoneSide != RedstoneSide.NONE && (currState = (level instanceof net.minecraft.server.level.ServerLevel serverLevel && !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(serverLevel, pos, 16) ? null : level.getBlockStateIfLoaded(mutableBlockPos.setWithOffset(pos, direction)))) != null && !currState.is(this)) { // Folia - block updates in unloaded chunks
                mutableBlockPos.move(Direction.DOWN);
                BlockState blockState = level.getBlockState(mutableBlockPos);
                if (blockState.is(this)) {
                    BlockPos blockPos = mutableBlockPos.relative(direction.getOpposite());
                    level.neighborShapeChanged(direction.getOpposite(), mutableBlockPos, blockPos, level.getBlockState(blockPos), flags, recursionLeft);
                }

                mutableBlockPos.setWithOffset(pos, direction).move(Direction.UP);
                BlockState blockState1 = level.getBlockState(mutableBlockPos);
                if (blockState1.is(this)) {
                    BlockPos blockPos1 = mutableBlockPos.relative(direction.getOpposite());
                    level.neighborShapeChanged(direction.getOpposite(), mutableBlockPos, blockPos1, level.getBlockState(blockPos1), flags, recursionLeft);
                }
            }
        }
    }

    private RedstoneSide getConnectingSide(BlockGetter level, BlockPos pos, Direction face) {
        return this.getConnectingSide(level, pos, face, !level.getBlockState(pos.above()).isRedstoneConductor(level, pos));
    }

    private RedstoneSide getConnectingSide(BlockGetter level, BlockPos pos, Direction direction, boolean nonNormalCubeAbove) {
        BlockPos blockPos = pos.relative(direction);
        BlockState blockState = level.getBlockState(blockPos);
        if (nonNormalCubeAbove) {
            boolean flag = blockState.getBlock() instanceof TrapDoorBlock || this.canSurviveOn(level, blockPos, blockState);
            if (flag && shouldConnectTo(level.getBlockState(blockPos.above()))) {
                if (blockState.isFaceSturdy(level, blockPos, direction.getOpposite())) {
                    return RedstoneSide.UP;
                }

                return RedstoneSide.SIDE;
            }
        }

        return !shouldConnectTo(blockState, direction)
                && (blockState.isRedstoneConductor(level, blockPos) || !shouldConnectTo(level.getBlockState(blockPos.below())))
            ? RedstoneSide.NONE
            : RedstoneSide.SIDE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        return this.canSurviveOn(level, blockPos, blockState);
    }

    private boolean canSurviveOn(BlockGetter level, BlockPos pos, BlockState state) {
        return state.isFaceSturdy(level, pos, Direction.UP) || state.is(Blocks.HOPPER);
    }

    // Paper start - Optimize redstone (Eigencraft)
    // The bulk of the new functionality is found in RedstoneWireTurbo.java
    io.papermc.paper.redstone.RedstoneWireTurbo turbo = new io.papermc.paper.redstone.RedstoneWireTurbo(this);
    // Folia start - region threading
    private io.papermc.paper.redstone.RedstoneWireTurbo getTurbo(Level world) {
        return world.getCurrentWorldData().turbo;
    }
    // Folia end - region threading

    /*
     * Modified version of pre-existing updateSurroundingRedstone, which is called from
     * this.neighborChanged and a few other methods in this class.
     * Note: Added 'source' argument so as to help determine direction of information flow
     */
    private void updateSurroundingRedstone(Level worldIn, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean blockAdded) {
        if (worldIn.paperConfig().misc.redstoneImplementation == io.papermc.paper.configuration.WorldConfiguration.Misc.RedstoneImplementation.EIGENCRAFT) {
            // since 24w33a the source pos is no longer given, but instead an Orientation parameter
            // when this is not null, it can be used to find the source pos, which the turbo uses
            // to find the direction of information flow
            BlockPos source = null;
            if (orientation != null) {
                source = pos.relative(orientation.getFront().getOpposite());
            }
            getTurbo(worldIn).updateSurroundingRedstone(worldIn, pos, state, source); // Folia - region threading
            return;
        }
        updatePowerStrength(worldIn, pos, state, orientation, blockAdded);
    }

    /*
     * This method computes a wire's target strength and updates the given block state.
     * It uses the DefaultRedstoneWireEvaluator for this, which is identical to code
     * that was present in this class prior to the introduction of the experimental redstone
     * changes in 24w33a.
     * The previous implementation of this method in this patch had optimizations that have
     * not been relevant since 1.13, thus it has been greatly simplified.
     */
    public BlockState calculateCurrentChanges(Level level, BlockPos pos, BlockState state) {
        int oldPower = state.getValue(POWER);
        int newPower = ((DefaultRedstoneWireEvaluator) evaluator).calculateTargetStrength(level, pos);
        if (oldPower != newPower) {
            org.bukkit.event.block.BlockRedstoneEvent event = new org.bukkit.event.block.BlockRedstoneEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), oldPower, newPower);
            level.getCraftServer().getPluginManager().callEvent(event);

            newPower = event.getNewCurrent();

            if (level.getBlockState(pos) == state) {
                state = state.setValue(POWER, newPower);
                // [Space Walker] suppress shape updates and emit those manually to
                // bypass the new neighbor update stack.
                if (level.setBlock(pos, state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS)) {
                    this.getTurbo(level).updateNeighborShapes(level, pos, state); // Folia - region threading
                }
            }
        }
        return state;
    }
    // Paper end

    private void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean updateShape) {
        if (useExperimentalEvaluator(level)) {
            new ExperimentalRedstoneWireEvaluator(this).updatePowerStrength(level, pos, state, orientation, updateShape);
        } else {
            this.evaluator.updatePowerStrength(level, pos, state, orientation, updateShape);
        }
    }

    public int getBlockSignal(Level level, BlockPos pos) {
        io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData().shouldSignal = false; // Folia - region threading
        int bestNeighborSignal = level.getBestNeighborSignal(pos);
        io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData().shouldSignal = true; // Folia - region threading
        return bestNeighborSignal;
    }

    private void checkCornerChangeAt(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(this)) {
            level.updateNeighborsAt(pos, this);

            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            // Paper start - optimize redstone - replace call to updatePowerStrength
            if (level.paperConfig().misc.redstoneImplementation == io.papermc.paper.configuration.WorldConfiguration.Misc.RedstoneImplementation.ALTERNATE_CURRENT) {
                level.getWireHandler().onWireAdded(pos, state); // Alternate Current
            } else {
                this.updateSurroundingRedstone(level, pos, state, null, true); // Vanilla/Eigencraft
            }
            // Paper end - optimize redstone

            for (Direction direction : Direction.Plane.VERTICAL) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }

            this.updateNeighborsOfNeighboringWires(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }

            // Paper start - optimize redstone - replace call to updatePowerStrength
            if (level.paperConfig().misc.redstoneImplementation == io.papermc.paper.configuration.WorldConfiguration.Misc.RedstoneImplementation.ALTERNATE_CURRENT) {
                level.getWireHandler().onWireRemoved(pos, state); // Alternate Current
            } else {
                this.updateSurroundingRedstone(level, pos, state, null, false); // Vanilla/Eigencraft
            }
            // Paper end - optimize redstone
            this.updateNeighborsOfNeighboringWires(level, pos);
        }
    }

    private void updateNeighborsOfNeighboringWires(Level level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            this.checkCornerChangeAt(level, pos.relative(direction));
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            if (level.getBlockState(blockPos).isRedstoneConductor(level, blockPos)) {
                this.checkCornerChangeAt(level, blockPos.above());
            } else {
                this.checkCornerChangeAt(level, blockPos.below());
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            // Paper start - optimize redstone (Alternate Current)
            // Alternate Current handles breaking of redstone wires in the WireHandler.
            if (level.paperConfig().misc.redstoneImplementation == io.papermc.paper.configuration.WorldConfiguration.Misc.RedstoneImplementation.ALTERNATE_CURRENT) {
                level.getWireHandler().onWireUpdated(pos, state, orientation);
            } else
                // Paper end - optimize redstone (Alternate Current)
            if (neighborBlock != this || !useExperimentalEvaluator(level)) {
                if (state.canSurvive(level, pos)) {
                    this.updateSurroundingRedstone(level, pos, state, orientation, false); // Paper - Optimize redstone (Eigencraft)
                } else {
                    dropResources(state, level, pos);
                    level.removeBlock(pos, false);
                }
            }
        }
    }

    private static boolean useExperimentalEvaluator(Level level) {
        return level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return !io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData().shouldSignal ? 0 : state.getSignal(level, pos, side); // Folia - region threading
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData().shouldSignal && side != Direction.DOWN) { // Folia - region threading
            int powerValue = state.getValue(POWER);
            if (powerValue == 0) {
                return 0;
            } else {
                return side != Direction.UP
                        && !this.getConnectionState(level, state, pos).getValue(PROPERTY_BY_DIRECTION.get(side.getOpposite())).isConnected()
                    ? 0
                    : powerValue;
            }
        } else {
            return 0;
        }
    }

    protected static boolean shouldConnectTo(BlockState state) {
        return shouldConnectTo(state, null);
    }

    protected static boolean shouldConnectTo(BlockState state, @Nullable Direction direction) {
        if (state.is(Blocks.REDSTONE_WIRE)) {
            return true;
        } else if (state.is(Blocks.REPEATER)) {
            Direction direction1 = state.getValue(RepeaterBlock.FACING);
            return direction1 == direction || direction1.getOpposite() == direction;
        } else {
            return state.is(Blocks.OBSERVER) ? direction == state.getValue(ObserverBlock.FACING) : state.isSignalSource() && direction != null;
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();
        return worldData == null || worldData.shouldSignal;
        // Folia end - region threading
    }

    public static int getColorForPower(int power) {
        return COLORS[power];
    }

    private static void spawnParticlesAlongLine(
        Level level, RandomSource random, BlockPos pos, int color, Direction direction, Direction perpendicularDirection, float start, float end
    ) {
        float f = end - start;
        if (!(random.nextFloat() >= 0.2F * f)) {
            float f1 = 0.4375F;
            float f2 = start + f * random.nextFloat();
            double d = 0.5 + 0.4375F * direction.getStepX() + f2 * perpendicularDirection.getStepX();
            double d1 = 0.5 + 0.4375F * direction.getStepY() + f2 * perpendicularDirection.getStepY();
            double d2 = 0.5 + 0.4375F * direction.getStepZ() + f2 * perpendicularDirection.getStepZ();
            level.addParticle(new DustParticleOptions(color, 1.0F), pos.getX() + d, pos.getY() + d1, pos.getZ() + d2, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int powerValue = state.getValue(POWER);
        if (powerValue != 0) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                RedstoneSide redstoneSide = state.getValue(PROPERTY_BY_DIRECTION.get(direction));
                switch (redstoneSide) {
                    case UP:
                        spawnParticlesAlongLine(level, random, pos, COLORS[powerValue], direction, Direction.UP, -0.5F, 0.5F);
                    case SIDE:
                        spawnParticlesAlongLine(level, random, pos, COLORS[powerValue], Direction.DOWN, direction, 0.0F, 0.5F);
                        break;
                    case NONE:
                    default:
                        spawnParticlesAlongLine(level, random, pos, COLORS[powerValue], Direction.DOWN, direction, 0.0F, 0.3F);
                }
            }
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return state.setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK:
                return state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default:
                return super.mirror(state, mirror);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, POWER);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            if (isCross(state) || isDot(state)) {
                BlockState blockState = isCross(state) ? this.defaultBlockState() : this.crossState;
                blockState = blockState.setValue(POWER, state.getValue(POWER));
                blockState = this.getConnectionState(level, blockState, pos);
                if (blockState != state) {
                    level.setBlock(pos, blockState, Block.UPDATE_ALL);
                    this.updatesOnShapeChange(level, pos, state, blockState);
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        }
    }

    private void updatesOnShapeChange(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, null, Direction.UP);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            if (oldState.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected() != newState.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected()
                && level.getBlockState(blockPos).isRedstoneConductor(level, blockPos)) {
                level.updateNeighborsAtExceptFromFacing(
                    blockPos, newState.getBlock(), direction.getOpposite(), ExperimentalRedstoneUtils.withFront(orientation, direction)
                );
            }
        }
    }
}
