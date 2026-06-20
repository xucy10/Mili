package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class BuddingAmethystBlock extends AmethystBlock {
    public static final MapCodec<BuddingAmethystBlock> CODEC = simpleCodec(BuddingAmethystBlock::new);
    public static final int GROWTH_CHANCE = 5;
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public MapCodec<BuddingAmethystBlock> codec() {
        return CODEC;
    }

    public BuddingAmethystBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState = level.getBlockState(blockPos);
            Block block = null;
            if (canClusterGrowAtState(blockState)) {
                block = Blocks.SMALL_AMETHYST_BUD;
            } else if (blockState.is(Blocks.SMALL_AMETHYST_BUD) && blockState.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.MEDIUM_AMETHYST_BUD;
            } else if (blockState.is(Blocks.MEDIUM_AMETHYST_BUD) && blockState.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.LARGE_AMETHYST_BUD;
            } else if (blockState.is(Blocks.LARGE_AMETHYST_BUD) && blockState.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.AMETHYST_CLUSTER;
            }

            if (block != null) {
                BlockState blockState1 = block.defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, direction)
                    .setValue(AmethystClusterBlock.WATERLOGGED, blockState.getFluidState().getType() == Fluids.WATER);
                // Paper start - Have Amethyst throw both spread and grow events
                if (block == Blocks.SMALL_AMETHYST_BUD) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos, blockState1, Block.UPDATE_ALL); // CraftBukkit
                } else {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, blockPos, blockState1, Block.UPDATE_ALL);
                }
                // Paper end - Have Amethyst throw both spread and grow events
            }
        }
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
