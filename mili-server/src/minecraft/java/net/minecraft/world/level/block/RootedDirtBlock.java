package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class RootedDirtBlock extends Block implements BonemealableBlock {
    public static final MapCodec<RootedDirtBlock> CODEC = simpleCodec(RootedDirtBlock::new);

    @Override
    public MapCodec<RootedDirtBlock> codec() {
        return CODEC;
    }

    public RootedDirtBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.below()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, pos.below(), Blocks.HANGING_ROOTS.defaultBlockState(), Block.UPDATE_ALL); // CraftBukkit
    }

    @Override
    public BlockPos getParticlePos(BlockPos pos) {
        return pos.below();
    }
}
