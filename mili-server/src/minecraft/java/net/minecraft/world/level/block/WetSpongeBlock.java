package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WetSpongeBlock extends Block {
    public static final MapCodec<WetSpongeBlock> CODEC = simpleCodec(WetSpongeBlock::new);

    @Override
    public MapCodec<WetSpongeBlock> codec() {
        return CODEC;
    }

    protected WetSpongeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
            level.setBlock(pos, Blocks.SPONGE.defaultBlockState(), Block.UPDATE_ALL);
            level.levelEvent(LevelEvent.PARTICLES_WATER_EVAPORATING, pos, 0);
            level.playSound(null, pos, SoundEvents.WET_SPONGE_DRIES, SoundSource.BLOCKS, 1.0F, (1.0F + level.getRandom().nextFloat() * 0.2F) * 0.7F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction random1 = Direction.getRandom(random);
        if (random1 != Direction.UP) {
            BlockPos blockPos = pos.relative(random1);
            BlockState blockState = level.getBlockState(blockPos);
            if (!state.canOcclude() || !blockState.isFaceSturdy(level, blockPos, random1.getOpposite())) {
                double d = pos.getX();
                double d1 = pos.getY();
                double d2 = pos.getZ();
                if (random1 == Direction.DOWN) {
                    d1 -= 0.05;
                    d += random.nextDouble();
                    d2 += random.nextDouble();
                } else {
                    d1 += random.nextDouble() * 0.8;
                    if (random1.getAxis() == Direction.Axis.X) {
                        d2 += random.nextDouble();
                        if (random1 == Direction.EAST) {
                            d++;
                        } else {
                            d += 0.05;
                        }
                    } else {
                        d += random.nextDouble();
                        if (random1 == Direction.SOUTH) {
                            d2++;
                        } else {
                            d2 += 0.05;
                        }
                    }
                }

                level.addParticle(ParticleTypes.DRIPPING_WATER, d, d1, d2, 0.0, 0.0, 0.0);
            }
        }
    }
}
