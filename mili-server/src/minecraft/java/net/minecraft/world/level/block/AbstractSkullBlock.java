package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

public abstract class AbstractSkullBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final SkullBlock.Type type;

    public AbstractSkullBlock(SkullBlock.Type type, BlockBehaviour.Properties properties) {
        super(properties);
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected abstract MapCodec<? extends AbstractSkullBlock> codec();

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkullBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            boolean flag = state.is(Blocks.DRAGON_HEAD)
                || state.is(Blocks.DRAGON_WALL_HEAD)
                || state.is(Blocks.PIGLIN_HEAD)
                || state.is(Blocks.PIGLIN_WALL_HEAD);
            if (flag) {
                return createTickerHelper(blockEntityType, BlockEntityType.SKULL, SkullBlockEntity::animation);
            }
        }

        return null;
    }

    public SkullBlock.Type getType() {
        return this.type;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean hasNeighborSignal = level.hasNeighborSignal(pos);
            if (hasNeighborSignal != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, hasNeighborSignal), Block.UPDATE_CLIENTS);
            }
        }
    }
}
