package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TripWireBlock extends Block {
    public static final MapCodec<TripWireBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("hook").forGetter(tripwire -> tripwire.hook), propertiesCodec())
            .apply(instance, TripWireBlock::new)
    );
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    public static final BooleanProperty DISARMED = BlockStateProperties.DISARMED;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = CrossCollisionBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape SHAPE_ATTACHED = Block.column(16.0, 1.0, 2.5);
    private static final VoxelShape SHAPE_NOT_ATTACHED = Block.column(16.0, 0.0, 8.0);
    private static final int RECHECK_PERIOD = 10;
    private final Block hook;

    @Override
    public MapCodec<TripWireBlock> codec() {
        return CODEC;
    }

    public TripWireBlock(Block hook, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(POWERED, false)
                .setValue(ATTACHED, false)
                .setValue(DISARMED, false)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
        );
        this.hook = hook;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ATTACHED) ? SHAPE_ATTACHED : SHAPE_NOT_ATTACHED;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return this.defaultBlockState(); // Paper - place tripwire without updating
        BlockGetter level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        return this.defaultBlockState()
            .setValue(NORTH, this.shouldConnectTo(level.getBlockState(clickedPos.north()), Direction.NORTH))
            .setValue(EAST, this.shouldConnectTo(level.getBlockState(clickedPos.east()), Direction.EAST))
            .setValue(SOUTH, this.shouldConnectTo(level.getBlockState(clickedPos.south()), Direction.SOUTH))
            .setValue(WEST, this.shouldConnectTo(level.getBlockState(clickedPos.west()), Direction.WEST));
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
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return state; // Paper - prevent tripwire from updating
        return direction.getAxis().isHorizontal()
            ? state.setValue(PROPERTY_BY_DIRECTION.get(direction), this.shouldConnectTo(neighborState, direction))
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent adjacent tripwires from updating
        if (!oldState.is(state.getBlock())) {
            this.updateSource(level, pos, state);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent adjacent tripwires from updating
        if (!movedByPiston) {
            this.updateSource(level, pos, state.setValue(POWERED, true));
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return state; // Paper - prevent disarming tripwires
        if (!level.isClientSide() && !player.getMainHandItem().isEmpty() && player.getMainHandItem().is(Items.SHEARS)) {
            level.setBlock(pos, state.setValue(DISARMED, true), Block.UPDATE_NONE);
            level.gameEvent(player, GameEvent.SHEAR, pos);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    private void updateSource(Level level, BlockPos pos, BlockState state) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent adjacent tripwires from updating
        for (Direction direction : new Direction[]{Direction.SOUTH, Direction.WEST}) {
            for (int i = 1; i < 42; i++) {
                BlockPos blockPos = pos.relative(direction, i);
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.is(this.hook)) {
                    if (blockState.getValue(TripWireHookBlock.FACING) == direction.getOpposite()) {
                        TripWireHookBlock.calculateState(level, blockPos, blockState, false, true, i, state);
                    }
                    break;
                }

                if (!blockState.is(this)) {
                    break;
                }
            }
        }
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return state.getShape(level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent tripwires from detecting collision
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (!level.isClientSide()) {
            if (!state.getValue(POWERED)) {
                this.checkPressed(level, pos, List.of(entity));
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent tripwire pressed check
        if (level.getBlockState(pos).getValue(POWERED)) {
            this.checkPressed(level, pos);
        }
    }

    private void checkPressed(Level level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        List<? extends Entity> entities = level.getEntities(null, blockState.getShape(level, pos).bounds().move(pos));
        this.checkPressed(level, pos, entities);
    }

    private void checkPressed(Level level, BlockPos pos, List<? extends Entity> entities) {
        BlockState blockState = level.getBlockState(pos);
        boolean poweredValue = blockState.getValue(POWERED);
        boolean flag = false;
        if (!entities.isEmpty()) {
            for (Entity entity : entities) {
                if (!entity.isIgnoringBlockTriggers()) {
                    flag = true;
                    break;
                }
            }
        }

        // CraftBukkit start - Call interact even when triggering connected tripwire
        if (poweredValue != flag && flag && blockState.getValue(TripWireBlock.ATTACHED)) {
            org.bukkit.plugin.PluginManager manager = level.getCraftServer().getPluginManager();
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
            boolean allowed = false;

            // If all the events are cancelled block the tripwire trigger, else allow
            for (Object object : entities) {
                if (object != null) {
                    org.bukkit.event.Cancellable cancellable;

                    if (object instanceof Player) {
                        cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) object, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
                    } else if (object instanceof Entity) {
                        cancellable = new org.bukkit.event.entity.EntityInteractEvent(((Entity) object).getBukkitEntity(), block);
                        manager.callEvent((org.bukkit.event.entity.EntityInteractEvent) cancellable);
                    } else {
                        continue;
                    }

                    if (!cancellable.isCancelled()) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) {
                return;
            }
        }
        // CraftBukkit end

        if (flag != poweredValue) {
            blockState = blockState.setValue(POWERED, flag);
            level.setBlock(pos, blockState, Block.UPDATE_ALL);
            this.updateSource(level, pos, blockState);
        }

        if (flag) {
            level.scheduleTick(new BlockPos(pos), this, 10);
        }
    }

    public boolean shouldConnectTo(BlockState state, Direction direction) {
        return state.is(this.hook) ? state.getValue(TripWireHookBlock.FACING) == direction.getOpposite() : state.is(this);
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
        builder.add(POWERED, ATTACHED, DISARMED, NORTH, EAST, WEST, SOUTH);
    }
}
