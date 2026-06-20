package net.minecraft.world.level.block;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class TorchBlock extends BaseTorchBlock {
    protected static final MapCodec<SimpleParticleType> PARTICLE_OPTIONS_FIELD = BuiltInRegistries.PARTICLE_TYPE
        .byNameCodec()
        .comapFlatMap(
            particleType -> particleType instanceof SimpleParticleType simpleParticleType
                ? DataResult.success(simpleParticleType)
                : DataResult.error(() -> "Not a SimpleParticleType: " + particleType),
            simpleParticleType -> (ParticleType<?>)simpleParticleType
        )
        .fieldOf("particle_options");
    public static final MapCodec<TorchBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(PARTICLE_OPTIONS_FIELD.forGetter(torchBlock -> torchBlock.flameParticle), propertiesCodec())
            .apply(instance, TorchBlock::new)
    );
    protected final SimpleParticleType flameParticle;

    @Override
    public MapCodec<? extends TorchBlock> codec() {
        return CODEC;
    }

    protected TorchBlock(SimpleParticleType flameParticle, BlockBehaviour.Properties properties) {
        super(properties);
        this.flameParticle = flameParticle;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d = pos.getX() + 0.5;
        double d1 = pos.getY() + 0.7;
        double d2 = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 0.0, 0.0);
        level.addParticle(this.flameParticle, d, d1, d2, 0.0, 0.0, 0.0);
    }
}
