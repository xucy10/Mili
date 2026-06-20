package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class GlowLichenBlock extends MultifaceSpreadeableBlock implements BonemealableBlock {
    public static final MapCodec<GlowLichenBlock> CODEC = simpleCodec(GlowLichenBlock::new);
    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    @Override
    public MapCodec<GlowLichenBlock> codec() {
        return CODEC;
    }

    public GlowLichenBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static ToIntFunction<BlockState> emission(int light) {
        return state -> MultifaceBlock.hasAnyFace(state) ? light : 0;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return Direction.stream().anyMatch(direction -> this.spreader.canSpreadInAnyDirection(state, level, pos, direction.getOpposite()));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        this.spreader.spreadFromRandomFaceTowardRandomDirection(state, level, pos, random);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return state.getFluidState().isEmpty();
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return this.spreader;
    }
}
