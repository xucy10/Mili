package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class HopperBlock extends BaseEntityBlock implements org.leavesmc.leaves.lithium.common.block.entity.ShapeUpdateHandlingBlockBehaviour { // Leaves - Lithium Sleeping Block Entity
    public static final MapCodec<HopperBlock> CODEC = simpleCodec(HopperBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING_HOPPER;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
    private final Function<BlockState, VoxelShape> shapes;
    private final Map<Direction, VoxelShape> interactionShapes;

    @Override
    public MapCodec<HopperBlock> codec() {
        return CODEC;
    }

    public HopperBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(ENABLED, true));
        VoxelShape voxelShape = Block.column(12.0, 11.0, 16.0);
        this.shapes = this.makeShapes(voxelShape);
        this.interactionShapes = ImmutableMap.<Direction, VoxelShape>builderWithExpectedSize(5)
            .putAll(Shapes.rotateHorizontal(Shapes.or(voxelShape, Block.boxZ(4.0, 8.0, 10.0, 0.0, 4.0))))
            .put(Direction.DOWN, voxelShape)
            .build();
    }

    private Function<BlockState, VoxelShape> makeShapes(VoxelShape shape) {
        VoxelShape voxelShape = Shapes.or(Block.column(16.0, 10.0, 16.0), Block.column(8.0, 4.0, 10.0));
        VoxelShape voxelShape1 = Shapes.join(voxelShape, shape, BooleanOp.ONLY_FIRST);
        Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(4.0, 4.0, 8.0, 0.0, 8.0), new Vec3(8.0, 6.0, 8.0).scale(0.0625));
        return this.getShapeForEachState(state -> Shapes.or(voxelShape1, Shapes.join(map.get(state.getValue(FACING)), Shapes.block(), BooleanOp.AND)), ENABLED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.interactionShapes.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction opposite = context.getClickedFace().getOpposite();
        return this.defaultBlockState().setValue(FACING, opposite.getAxis() == Direction.Axis.Y ? Direction.DOWN : opposite).setValue(ENABLED, true);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HopperBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null : createTickerHelper(blockEntityType, BlockEntityType.HOPPER, HopperBlockEntity::pushItemsTick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            this.checkPoweredState(level, pos, state);
            // Leaves start - Lithium Sleeping Block Entity
            //invalidate caches of nearby hoppers when placing an update suppressed hopper
            if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && level.getBlockState(pos) != state) {
                for (Direction direction : UPDATE_SHAPE_ORDER) {
                    BlockEntity hopper = level.lithium$getLoadedExistingBlockEntity(pos.relative(direction));
                    if (hopper instanceof org.leavesmc.leaves.lithium.common.hopper.UpdateReceiver updateReceiver) {
                        updateReceiver.lithium$invalidateCacheOnNeighborUpdate(direction == Direction.DOWN);
                    }
                }
            }
            // Leaves end - Lithium Sleeping Block Entity
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HopperBlockEntity hopperBlockEntity && player.openMenu(hopperBlockEntity).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
            player.awardStat(Stats.INSPECT_HOPPER);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && level.lithium$getLoadedExistingBlockEntity(pos) instanceof org.leavesmc.leaves.lithium.common.hopper.UpdateReceiver updateReceiver) updateReceiver.lithium$invalidateCacheOnUndirectedNeighborUpdate(); // Leaves - Lithium Sleeping Block Entity /* invalidate cache when the block is replaced */
        this.checkPoweredState(level, pos, state);
    }

    private void checkPoweredState(Level level, BlockPos pos, BlockState state) {
        boolean flag = !level.hasNeighborSignal(pos);
        if (flag != state.getValue(ENABLED)) {
            level.setBlock(pos, state.setValue(ENABLED, flag), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
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
        builder.add(FACING, ENABLED);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof HopperBlockEntity) {
            HopperBlockEntity.entityInside(level, pos, state, entity, (HopperBlockEntity)blockEntity);
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    // Leaves start - Lithium Sleeping Block Entity
    @Override
    public void lithium$handleShapeUpdate(net.minecraft.world.level.LevelReader levelReader, BlockState myBlockState, BlockPos myPos, BlockPos posFrom, BlockState newState) {
        //invalidate cache when composters change state
        if (newState.getBlock() instanceof net.minecraft.world.WorldlyContainerHolder) {
            this.updateHopper(levelReader, myBlockState, myPos, posFrom);
        }
    }

    private void updateHopper(net.minecraft.world.level.LevelReader world, BlockState myBlockState, BlockPos myPos, BlockPos posFrom) {
        Direction facing = myBlockState.getValue(HopperBlock.FACING);
        boolean above = posFrom.getY() == myPos.getY() + 1;
        if (above || posFrom.getX() == myPos.getX() + facing.getStepX() && posFrom.getY() == myPos.getY() + facing.getStepY() && posFrom.getZ() == myPos.getZ() + facing.getStepZ()) {
            BlockEntity hopper = ((net.minecraft.world.level.Level) world).lithium$getLoadedExistingBlockEntity(myPos);
            if (hopper instanceof org.leavesmc.leaves.lithium.common.hopper.UpdateReceiver updateReceiver) {
                updateReceiver.lithium$invalidateCacheOnNeighborUpdate(above);
            }
        }
    }
    // Leaves end - Lithium Sleeping Block Entity
}
