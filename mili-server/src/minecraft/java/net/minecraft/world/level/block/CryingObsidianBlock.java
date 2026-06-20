package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class CryingObsidianBlock extends Block {
    public static final MapCodec<CryingObsidianBlock> CODEC = simpleCodec(CryingObsidianBlock::new);

    @Override
    public MapCodec<CryingObsidianBlock> codec() {
        return CODEC;
    }

    public CryingObsidianBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            Direction random1 = Direction.getRandom(random);
            if (random1 != Direction.UP) {
                BlockPos blockPos = pos.relative(random1);
                BlockState blockState = level.getBlockState(blockPos);
                if (!state.canOcclude() || !blockState.isFaceSturdy(level, blockPos, random1.getOpposite())) {
                    double d = random1.getStepX() == 0 ? random.nextDouble() : 0.5 + random1.getStepX() * 0.6;
                    double d1 = random1.getStepY() == 0 ? random.nextDouble() : 0.5 + random1.getStepY() * 0.6;
                    double d2 = random1.getStepZ() == 0 ? random.nextDouble() : 0.5 + random1.getStepZ() * 0.6;
                    level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, pos.getX() + d, pos.getY() + d1, pos.getZ() + d2, 0.0, 0.0, 0.0);
                }
            }
        }
    }
}
