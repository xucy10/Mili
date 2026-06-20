package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.datafixers.Products.P3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public abstract class NoiseBasedStateProvider extends BlockStateProvider {
    protected final long seed;
    protected final NormalNoise.NoiseParameters parameters;
    protected final float scale;
    protected final NormalNoise noise;

    protected static <P extends NoiseBasedStateProvider> P3<Mu<P>, Long, NormalNoise.NoiseParameters, Float> noiseCodec(Instance<P> instance) {
        return instance.group(
            Codec.LONG.fieldOf("seed").forGetter(provider -> provider.seed),
            NormalNoise.NoiseParameters.DIRECT_CODEC.fieldOf("noise").forGetter(provider -> provider.parameters),
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("scale").forGetter(provider -> provider.scale)
        );
    }

    protected NoiseBasedStateProvider(long seed, NormalNoise.NoiseParameters parameters, float scale) {
        this.seed = seed;
        this.parameters = parameters;
        this.scale = scale;
        this.noise = NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(seed)), parameters);
    }

    protected double getNoiseValue(BlockPos pos, double delta) {
        return this.noise.getValue(pos.getX() * delta, pos.getY() * delta, pos.getZ() * delta);
    }
}
