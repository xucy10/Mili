package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

public class RedstoneLampBlock extends Block {
    public static final MapCodec<RedstoneLampBlock> CODEC = simpleCodec(RedstoneLampBlock::new);
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    @Override
    public MapCodec<RedstoneLampBlock> codec() {
        return CODEC;
    }

    public RedstoneLampBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LIT, false));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean litValue = state.getValue(LIT);
            if (litValue != level.hasNeighborSignal(pos)) {
                if (litValue) {
                    level.scheduleTick(pos, this, 4);
                } else {
                    // CraftBukkit start
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, 0, 15).getNewCurrent() != 15) {
                        return;
                    }
                    // CraftBukkit end
                    level.setBlock(pos, state.cycle(LIT), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && !level.hasNeighborSignal(pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, 15, 0).getNewCurrent() != 0) {
                return;
            }
            // CraftBukkit end
            level.setBlock(pos, state.cycle(LIT), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
