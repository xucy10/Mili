package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class NetherrackBlock extends Block implements BonemealableBlock {
    public static final MapCodec<NetherrackBlock> CODEC = simpleCodec(NetherrackBlock::new);

    @Override
    public MapCodec<NetherrackBlock> codec() {
        return CODEC;
    }

    public NetherrackBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos.above()).propagatesSkylightDown()) {
            return false;
        } else {
            for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                if (level.getBlockState(blockPos).is(BlockTags.NYLIUM)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        boolean flag = false;
        boolean flag1 = false;

        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.is(Blocks.WARPED_NYLIUM)) {
                flag1 = true;
            }

            if (blockState.is(Blocks.CRIMSON_NYLIUM)) {
                flag = true;
            }

            if (flag1 && flag) {
                break;
            }
        }

        if (flag1 && flag) {
            level.setBlock(pos, random.nextBoolean() ? Blocks.WARPED_NYLIUM.defaultBlockState() : Blocks.CRIMSON_NYLIUM.defaultBlockState(), Block.UPDATE_ALL);
        } else if (flag1) {
            level.setBlock(pos, Blocks.WARPED_NYLIUM.defaultBlockState(), Block.UPDATE_ALL);
        } else if (flag) {
            level.setBlock(pos, Blocks.CRIMSON_NYLIUM.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.NEIGHBOR_SPREADER;
    }
}
