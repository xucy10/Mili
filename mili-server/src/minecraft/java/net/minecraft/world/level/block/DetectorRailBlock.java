package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

public class DetectorRailBlock extends BaseRailBlock {
    public static final MapCodec<DetectorRailBlock> CODEC = simpleCodec(DetectorRailBlock::new);
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int PRESSED_CHECK_PERIOD = 20;

    @Override
    public MapCodec<DetectorRailBlock> codec() {
        return CODEC;
    }

    public DetectorRailBlock(BlockBehaviour.Properties properties) {
        super(true, properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (!level.isClientSide()) {
            if (!state.getValue(POWERED)) {
                this.checkPressed(level, pos, state);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            this.checkPressed(level, pos, state);
        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (!state.getValue(POWERED)) {
            return 0;
        } else {
            return side == Direction.UP ? 15 : 0;
        }
    }

    private void checkPressed(Level level, BlockPos pos, BlockState state) {
        if (this.canSurvive(state, level, pos)) {
            if (!state.is(this)) { return; } // Paper - Fix some rails connecting improperly
            boolean poweredValue = state.getValue(POWERED);
            boolean flag = false;
            List<AbstractMinecart> interactingMinecartOfType = this.getInteractingMinecartOfType(level, pos, AbstractMinecart.class, entity -> true);
            if (!interactingMinecartOfType.isEmpty()) {
                flag = true;
            }

            // CraftBukkit start
            if (poweredValue != flag) {
                org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);

                org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(block, flag ? 15 : 0, flag ? 15 : 0);
                level.getCraftServer().getPluginManager().callEvent(eventRedstone);

                flag = eventRedstone.getNewCurrent() > 0;
            }
            // CraftBukkit end
            if (flag && !poweredValue) {
                BlockState blockState = state.setValue(POWERED, true);
                level.setBlock(pos, blockState, Block.UPDATE_ALL);
                this.updatePowerToConnected(level, pos, blockState, true);
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
                level.setBlocksDirty(pos, state, blockState);
            }

            if (!flag && poweredValue) {
                BlockState blockState = state.setValue(POWERED, false);
                level.setBlock(pos, blockState, Block.UPDATE_ALL);
                this.updatePowerToConnected(level, pos, blockState, false);
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
                level.setBlocksDirty(pos, state, blockState);
            }

            if (flag) {
                level.scheduleTick(pos, this, 20);
            }

            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    protected void updatePowerToConnected(Level level, BlockPos pos, BlockState state, boolean powered) {
        RailState railState = new RailState(level, pos, state);

        for (BlockPos blockPos : railState.getConnections()) {
            BlockState blockState = !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, blockPos, 16) ? null : level.getBlockState(blockPos); // Folia - block updates in unloaded chunks
            if (blockState != null) level.neighborChanged(blockState, blockPos, blockState.getBlock(), null, false); // Folia - block updates in unloaded chunks
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            BlockState blockState = this.updateState(state, level, pos, movedByPiston);
            this.checkPressed(level, pos, blockState);
        }
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (state.getValue(POWERED)) {
            List<MinecartCommandBlock> interactingMinecartOfType = this.getInteractingMinecartOfType(level, pos, MinecartCommandBlock.class, cartEntity -> true);
            if (!interactingMinecartOfType.isEmpty()) {
                return interactingMinecartOfType.get(0).getCommandBlock().getSuccessCount();
            }

            List<AbstractMinecart> interactingMinecartOfType1 = this.getInteractingMinecartOfType(
                level, pos, AbstractMinecart.class, EntitySelector.CONTAINER_ENTITY_SELECTOR
            );
            if (!interactingMinecartOfType1.isEmpty()) {
                return AbstractContainerMenu.getRedstoneSignalFromContainer((Container)interactingMinecartOfType1.get(0));
            }
        }

        return 0;
    }

    private <T extends AbstractMinecart> List<T> getInteractingMinecartOfType(Level level, BlockPos pos, Class<T> cartType, Predicate<Entity> filter) {
        return level.getEntitiesOfClass(cartType, this.getSearchBB(pos), filter);
    }

    private AABB getSearchBB(BlockPos pos) {
        double d = 0.2;
        return new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2, pos.getX() + 1 - 0.2, pos.getY() + 1 - 0.2, pos.getZ() + 1 - 0.2);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        RailShape railShape = state.getValue(SHAPE);
        RailShape railShape1 = this.rotate(railShape, rotation);
        return state.setValue(SHAPE, railShape1);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        RailShape railShape = state.getValue(SHAPE);
        RailShape railShape1 = this.mirror(railShape, mirror);
        return state.setValue(SHAPE, railShape1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, POWERED, WATERLOGGED);
    }
}
