package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DoorBlock extends Block {
    public static final MapCodec<DoorBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter(DoorBlock::type), propertiesCodec()).apply(instance, DoorBlock::new)
    );
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(16.0, 13.0, 16.0));
    private final BlockSetType type;

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }

    protected DoorBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(HINGE, DoorHingeSide.LEFT)
                .setValue(POWERED, false)
                .setValue(HALF, DoubleBlockHalf.LOWER)
        );
    }

    public BlockSetType type() {
        return this.type;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        Direction direction1 = state.getValue(OPEN)
            ? (state.getValue(HINGE) == DoorHingeSide.RIGHT ? direction.getCounterClockWise() : direction.getClockWise())
            : direction;
        return SHAPES.get(direction1);
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
        DoubleBlockHalf doubleBlockHalf = state.getValue(HALF);
        if (direction.getAxis() != Direction.Axis.Y || doubleBlockHalf == DoubleBlockHalf.LOWER != (direction == Direction.UP)) {
            return doubleBlockHalf == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            return neighborState.getBlock() instanceof DoorBlock && neighborState.getValue(HALF) != doubleBlockHalf
                ? neighborState.setValue(HALF, doubleBlockHalf)
                : Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks() && state.getValue(HALF) == DoubleBlockHalf.LOWER && this.type.canOpenByWindCharge() && !state.getValue(POWERED)) {
            this.setOpen(null, level, state, pos, !this.isOpen(state));
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state))) {
            DoublePlantBlock.preventDropFromBottomPart(level, pos, state, player);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return switch (pathComputationType) {
            case LAND, AIR -> state.getValue(OPEN);
            case WATER -> false;
        };
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        if (clickedPos.getY() < level.getMaxY() && level.getBlockState(clickedPos.above()).canBeReplaced(context)) {
            boolean flag = level.hasNeighborSignal(clickedPos) || level.hasNeighborSignal(clickedPos.above());
            return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(HINGE, this.getHinge(context))
                .setValue(POWERED, flag)
                .setValue(OPEN, flag)
                .setValue(HALF, DoubleBlockHalf.LOWER);
        } else {
            return null;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    private DoorHingeSide getHinge(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction horizontalDirection = context.getHorizontalDirection();
        BlockPos blockPos = clickedPos.above();
        Direction counterClockWise = horizontalDirection.getCounterClockWise();
        BlockPos blockPos1 = clickedPos.relative(counterClockWise);
        BlockState blockState = level.getBlockState(blockPos1);
        BlockPos blockPos2 = blockPos.relative(counterClockWise);
        BlockState blockState1 = level.getBlockState(blockPos2);
        Direction clockWise = horizontalDirection.getClockWise();
        BlockPos blockPos3 = clickedPos.relative(clockWise);
        BlockState blockState2 = level.getBlockState(blockPos3);
        BlockPos blockPos4 = blockPos.relative(clockWise);
        BlockState blockState3 = level.getBlockState(blockPos4);
        int i = (blockState.isCollisionShapeFullBlock(level, blockPos1) ? -1 : 0)
            + (blockState1.isCollisionShapeFullBlock(level, blockPos2) ? -1 : 0)
            + (blockState2.isCollisionShapeFullBlock(level, blockPos3) ? 1 : 0)
            + (blockState3.isCollisionShapeFullBlock(level, blockPos4) ? 1 : 0);
        boolean flag = blockState.getBlock() instanceof DoorBlock && blockState.getValue(HALF) == DoubleBlockHalf.LOWER;
        boolean flag1 = blockState2.getBlock() instanceof DoorBlock && blockState2.getValue(HALF) == DoubleBlockHalf.LOWER;
        if ((!flag || flag1) && i <= 0) {
            if ((!flag1 || flag) && i >= 0) {
                int stepX = horizontalDirection.getStepX();
                int stepZ = horizontalDirection.getStepZ();
                Vec3 clickLocation = context.getClickLocation();
                double d = clickLocation.x - clickedPos.getX();
                double d1 = clickLocation.z - clickedPos.getZ();
                return (stepX >= 0 || !(d1 < 0.5)) && (stepX <= 0 || !(d1 > 0.5)) && (stepZ >= 0 || !(d > 0.5)) && (stepZ <= 0 || !(d < 0.5))
                    ? DoorHingeSide.LEFT
                    : DoorHingeSide.RIGHT;
            } else {
                return DoorHingeSide.LEFT;
            }
        } else {
            return DoorHingeSide.RIGHT;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!this.type.canOpenByHand()) {
            return InteractionResult.PASS;
        } else {
            state = state.cycle(OPEN);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
            this.playSound(player, level, pos, state.getValue(OPEN));
            level.gameEvent(player, this.isOpen(state) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            return InteractionResult.SUCCESS;
        }
    }

    public boolean isOpen(BlockState state) {
        return state.getValue(OPEN);
    }

    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean _open) {
        if (state.is(this) && state.getValue(OPEN) != _open) {
            level.setBlock(pos, state.setValue(OPEN, _open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
            this.playSound(entity, level, pos, _open);
            level.gameEvent(entity, _open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        // CraftBukkit start
        BlockPos otherHalf = pos.relative(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
        org.bukkit.block.Block blockTop = org.bukkit.craftbukkit.block.CraftBlock.at(level, otherHalf);

        int power = bukkitBlock.getBlockPower();
        int powerTop = blockTop.getBlockPower();
        if (powerTop > power) power = powerTop;
        int oldPower = state.getValue(DoorBlock.POWERED) ? net.minecraft.world.level.redstone.Redstone.SIGNAL_MAX : net.minecraft.world.level.redstone.Redstone.SIGNAL_MIN;

        if (oldPower == 0 ^ power == 0) {
            org.bukkit.event.block.BlockRedstoneEvent event = new org.bukkit.event.block.BlockRedstoneEvent(bukkitBlock, oldPower, power);
            event.callEvent();

            boolean flag = event.getNewCurrent() > 0;
            // CraftBukkit end
            if (flag != state.getValue(OPEN)) {
                this.playSound(null, level, pos, flag);
                level.gameEvent(null, flag ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            }

            level.setBlock(pos, state.setValue(POWERED, flag).setValue(OPEN, flag), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? blockState.isFaceSturdy(level, blockPos, Direction.UP) : blockState.is(this);
    }

    private void playSound(@Nullable Entity source, Level level, BlockPos pos, boolean isOpening) {
        level.playSound(
            source, pos, isOpening ? this.type.doorOpen() : this.type.doorClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F
        );
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(HINGE);
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos.getX(), pos.below(state.getValue(HALF) == DoubleBlockHalf.LOWER ? 0 : 1).getY(), pos.getZ());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING, OPEN, HINGE, POWERED);
    }

    public static boolean isWoodenDoor(Level level, BlockPos pos) {
        return isWoodenDoor(level.getBlockState(pos));
    }

    public static boolean isWoodenDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock doorBlock && doorBlock.type().canOpenByHand();
    }
}
