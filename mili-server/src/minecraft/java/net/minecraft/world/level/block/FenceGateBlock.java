package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class FenceGateBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FenceGateBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(fenceGateBlock -> fenceGateBlock.type), propertiesCodec())
            .apply(instance, FenceGateBlock::new)
    );
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty IN_WALL = BlockStateProperties.IN_WALL;
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.cube(16.0, 16.0, 4.0));
    private static final Map<Direction.Axis, VoxelShape> SHAPES_WALL = Maps.newEnumMap(
        Util.mapValues(SHAPES, voxelShape -> Shapes.join(voxelShape, Block.column(16.0, 13.0, 16.0), BooleanOp.ONLY_FIRST))
    );
    private static final Map<Direction.Axis, VoxelShape> SHAPE_COLLISION = Shapes.rotateHorizontalAxis(Block.column(16.0, 4.0, 0.0, 24.0));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_SUPPORT = Shapes.rotateHorizontalAxis(Block.column(16.0, 4.0, 5.0, 24.0));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_OCCLUSION = Shapes.rotateHorizontalAxis(
        Shapes.or(Block.box(0.0, 5.0, 7.0, 2.0, 16.0, 9.0), Block.box(14.0, 5.0, 7.0, 16.0, 16.0, 9.0))
    );
    private static final Map<Direction.Axis, VoxelShape> SHAPE_OCCLUSION_WALL = Maps.newEnumMap(
        Util.mapValues(SHAPE_OCCLUSION, voxelShape -> voxelShape.move(0.0, -0.1875, 0.0).optimize())
    );
    private final WoodType type;

    @Override
    public MapCodec<FenceGateBlock> codec() {
        return CODEC;
    }

    public FenceGateBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(OPEN, false).setValue(POWERED, false).setValue(IN_WALL, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        return (state.getValue(IN_WALL) ? SHAPES_WALL : SHAPES).get(axis);
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
        Direction.Axis axis = direction.getAxis();
        if (state.getValue(FACING).getClockWise().getAxis() != axis) {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            boolean flag = this.isWall(neighborState) || this.isWall(level.getBlockState(pos.relative(direction.getOpposite())));
            return state.setValue(IN_WALL, flag);
        }
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        return state.getValue(OPEN) ? Shapes.empty() : SHAPE_SUPPORT.get(axis);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        return state.getValue(OPEN) ? Shapes.empty() : SHAPE_COLLISION.get(axis);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        return (state.getValue(IN_WALL) ? SHAPE_OCCLUSION_WALL : SHAPE_OCCLUSION).get(axis);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        switch (pathComputationType) {
            case LAND:
                return state.getValue(OPEN);
            case WATER:
                return false;
            case AIR:
                return state.getValue(OPEN);
            default:
                return false;
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        boolean hasNeighborSignal = level.hasNeighborSignal(clickedPos);
        Direction horizontalDirection = context.getHorizontalDirection();
        Direction.Axis axis = horizontalDirection.getAxis();
        boolean flag = axis == Direction.Axis.Z && (this.isWall(level.getBlockState(clickedPos.west())) || this.isWall(level.getBlockState(clickedPos.east())))
            || axis == Direction.Axis.X && (this.isWall(level.getBlockState(clickedPos.north())) || this.isWall(level.getBlockState(clickedPos.south())));
        return this.defaultBlockState()
            .setValue(FACING, horizontalDirection)
            .setValue(OPEN, hasNeighborSignal)
            .setValue(POWERED, hasNeighborSignal)
            .setValue(IN_WALL, flag);
    }

    private boolean isWall(BlockState state) {
        return state.is(BlockTags.WALLS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(OPEN)) {
            state = state.setValue(OPEN, false);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        } else {
            Direction direction = player.getDirection();
            if (state.getValue(FACING) == direction.getOpposite()) {
                state = state.setValue(FACING, direction);
            }

            state = state.setValue(OPEN, true);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        }

        boolean openValue = state.getValue(OPEN);
        level.playSound(
            player,
            pos,
            openValue ? this.type.fenceGateOpen() : this.type.fenceGateClose(),
            SoundSource.BLOCKS,
            1.0F,
            level.getRandom().nextFloat() * 0.1F + 0.9F
        );
        level.gameEvent(player, openValue ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks() && !state.getValue(POWERED)) {
            boolean openValue = state.getValue(OPEN);
            level.setBlockAndUpdate(pos, state.setValue(OPEN, !openValue));
            level.playSound(
                null,
                pos,
                openValue ? this.type.fenceGateClose() : this.type.fenceGateOpen(),
                SoundSource.BLOCKS,
                1.0F,
                level.getRandom().nextFloat() * 0.1F + 0.9F
            );
            level.gameEvent(openValue ? GameEvent.BLOCK_CLOSE : GameEvent.BLOCK_OPEN, pos, GameEvent.Context.of(state));
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean hasNeighborSignal = level.hasNeighborSignal(pos);
            // CraftBukkit start
            boolean oldPowered = state.getValue(FenceGateBlock.POWERED);
            if (oldPowered != hasNeighborSignal) {
                int newPower = hasNeighborSignal ? 15 : 0;
                int oldPower = oldPowered ? 15 : 0;
                org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(bukkitBlock, oldPower, newPower);
                level.getCraftServer().getPluginManager().callEvent(eventRedstone);
                hasNeighborSignal = eventRedstone.getNewCurrent() > 0;
            }
            // CraftBukkit end
            if (state.getValue(POWERED) != hasNeighborSignal) {
                level.setBlock(pos, state.setValue(POWERED, hasNeighborSignal).setValue(OPEN, hasNeighborSignal), Block.UPDATE_CLIENTS);
                if (state.getValue(OPEN) != hasNeighborSignal) {
                    level.playSound(
                        null,
                        pos,
                        hasNeighborSignal ? this.type.fenceGateOpen() : this.type.fenceGateClose(),
                        SoundSource.BLOCKS,
                        1.0F,
                        level.getRandom().nextFloat() * 0.1F + 0.9F
                    );
                    level.gameEvent(null, hasNeighborSignal ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
                }
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED, IN_WALL);
    }

    public static boolean connectsToDirection(BlockState state, Direction direction) {
        return state.getValue(FACING).getAxis() == direction.getClockWise().getAxis();
    }
}
