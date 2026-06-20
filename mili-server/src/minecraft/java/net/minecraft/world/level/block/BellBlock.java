package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BellBlock extends BaseEntityBlock {
    public static final MapCodec<BellBlock> CODEC = simpleCodec(BellBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BellAttachType> ATTACHMENT = BlockStateProperties.BELL_ATTACHMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape BELL_SHAPE = Shapes.or(Block.column(6.0, 6.0, 13.0), Block.column(8.0, 4.0, 6.0));
    private static final VoxelShape SHAPE_CEILING = Shapes.or(BELL_SHAPE, Block.column(2.0, 13.0, 16.0));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_FLOOR = Shapes.rotateHorizontalAxis(Block.cube(16.0, 16.0, 8.0));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_DOUBLE_WALL = Shapes.rotateHorizontalAxis(
        Shapes.or(BELL_SHAPE, Block.column(2.0, 16.0, 13.0, 15.0))
    );
    private static final Map<Direction, VoxelShape> SHAPE_SINGLE_WALL = Shapes.rotateHorizontal(Shapes.or(BELL_SHAPE, Block.boxZ(2.0, 13.0, 15.0, 0.0, 13.0)));
    public static final int EVENT_BELL_RING = 1;

    @Override
    public MapCodec<BellBlock> codec() {
        return CODEC;
    }

    public BellBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ATTACHMENT, BellAttachType.FLOOR).setValue(POWERED, false)
        );
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean hasNeighborSignal = level.hasNeighborSignal(pos);
        if (hasNeighborSignal != state.getValue(POWERED)) {
            if (hasNeighborSignal) {
                this.attemptToRing(level, pos, null);
            }

            level.setBlock(pos, state.setValue(POWERED, hasNeighborSignal), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        Player player1 = projectile.getOwner() instanceof Player player ? player : null;
        this.onHit(level, state, hit, player1, true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return (InteractionResult)(this.onHit(level, state, hitResult, player, true) ? InteractionResult.SUCCESS : InteractionResult.PASS);
    }

    public boolean onHit(Level level, BlockState state, BlockHitResult result, @Nullable Player player, boolean canRingBell) {
        Direction direction = result.getDirection();
        BlockPos blockPos = result.getBlockPos();
        boolean flag = !canRingBell || this.isProperHit(state, direction, result.getLocation().y - blockPos.getY());
        if (flag) {
            boolean flag1 = this.attemptToRing(player, level, blockPos, direction);
            if (flag1 && player != null) {
                player.awardStat(Stats.BELL_RING);
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isProperHit(BlockState state, Direction direction, double distanceY) {
        if (direction.getAxis() != Direction.Axis.Y && !(distanceY > 0.8124F)) {
            Direction direction1 = state.getValue(FACING);
            BellAttachType bellAttachType = state.getValue(ATTACHMENT);
            switch (bellAttachType) {
                case FLOOR:
                    return direction1.getAxis() == direction.getAxis();
                case SINGLE_WALL:
                case DOUBLE_WALL:
                    return direction1.getAxis() != direction.getAxis();
                case CEILING:
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public boolean attemptToRing(Level level, BlockPos pos, @Nullable Direction direction) {
        return this.attemptToRing(null, level, pos, direction);
    }

    public boolean attemptToRing(@Nullable Entity entity, Level level, BlockPos pos, @Nullable Direction direction) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!level.isClientSide() && blockEntity instanceof BellBlockEntity) {
            if (direction == null) {
                direction = level.getBlockState(pos).getValue(FACING);
            }

            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBellRingEvent(level, pos, direction, entity)) {
                return false;
            }
            // CraftBukkit end
            ((BellBlockEntity)blockEntity).onHit(direction);
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
            level.gameEvent(entity, GameEvent.BLOCK_CHANGE, pos);
            return true;
        } else {
            return false;
        }
    }

    private VoxelShape getVoxelShape(BlockState state) {
        Direction direction = state.getValue(FACING);

        return switch ((BellAttachType)state.getValue(ATTACHMENT)) {
            case FLOOR -> (VoxelShape)SHAPE_FLOOR.get(direction.getAxis());
            case SINGLE_WALL -> (VoxelShape)SHAPE_SINGLE_WALL.get(direction);
            case DOUBLE_WALL -> (VoxelShape)SHAPE_DOUBLE_WALL.get(direction.getAxis());
            case CEILING -> SHAPE_CEILING;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        Direction.Axis axis = clickedFace.getAxis();
        if (axis == Direction.Axis.Y) {
            BlockState blockState = this.defaultBlockState()
                .setValue(ATTACHMENT, clickedFace == Direction.DOWN ? BellAttachType.CEILING : BellAttachType.FLOOR)
                .setValue(FACING, context.getHorizontalDirection());
            if (blockState.canSurvive(context.getLevel(), clickedPos)) {
                return blockState;
            }
        } else {
            boolean flag = axis == Direction.Axis.X
                    && level.getBlockState(clickedPos.west()).isFaceSturdy(level, clickedPos.west(), Direction.EAST)
                    && level.getBlockState(clickedPos.east()).isFaceSturdy(level, clickedPos.east(), Direction.WEST)
                || axis == Direction.Axis.Z
                    && level.getBlockState(clickedPos.north()).isFaceSturdy(level, clickedPos.north(), Direction.SOUTH)
                    && level.getBlockState(clickedPos.south()).isFaceSturdy(level, clickedPos.south(), Direction.NORTH);
            BlockState blockState = this.defaultBlockState()
                .setValue(FACING, clickedFace.getOpposite())
                .setValue(ATTACHMENT, flag ? BellAttachType.DOUBLE_WALL : BellAttachType.SINGLE_WALL);
            if (blockState.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockState;
            }

            boolean isFaceSturdy = level.getBlockState(clickedPos.below()).isFaceSturdy(level, clickedPos.below(), Direction.UP);
            blockState = blockState.setValue(ATTACHMENT, isFaceSturdy ? BellAttachType.FLOOR : BellAttachType.CEILING);
            if (blockState.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockState;
            }
        }

        return null;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks()) {
            this.attemptToRing(level, pos, null);
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
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
        BellAttachType bellAttachType = state.getValue(ATTACHMENT);
        Direction opposite = getConnectedDirection(state).getOpposite();
        if (opposite == direction && !state.canSurvive(level, pos) && bellAttachType != BellAttachType.DOUBLE_WALL) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if (direction.getAxis() == state.getValue(FACING).getAxis()) {
                if (bellAttachType == BellAttachType.DOUBLE_WALL && !neighborState.isFaceSturdy(level, neighborPos, direction)) {
                    return state.setValue(ATTACHMENT, BellAttachType.SINGLE_WALL).setValue(FACING, direction.getOpposite());
                }

                if (bellAttachType == BellAttachType.SINGLE_WALL
                    && opposite.getOpposite() == direction
                    && neighborState.isFaceSturdy(level, neighborPos, state.getValue(FACING))) {
                    return state.setValue(ATTACHMENT, BellAttachType.DOUBLE_WALL);
                }
            }

            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction opposite = getConnectedDirection(state).getOpposite();
        return opposite == Direction.UP
            ? Block.canSupportCenter(level, pos.above(), Direction.DOWN)
            : FaceAttachedHorizontalDirectionalBlock.canAttach(level, pos, opposite);
    }

    private static Direction getConnectedDirection(BlockState state) {
        switch ((BellAttachType)state.getValue(ATTACHMENT)) {
            case FLOOR:
                return Direction.UP;
            case CEILING:
                return Direction.DOWN;
            default:
                return state.getValue(FACING).getOpposite();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ATTACHMENT, POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BellBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BlockEntityType.BELL, level.isClientSide() ? BellBlockEntity::clientTick : BellBlockEntity::serverTick);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
