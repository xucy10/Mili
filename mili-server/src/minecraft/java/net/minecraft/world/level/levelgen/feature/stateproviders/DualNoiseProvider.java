package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class DualNoiseProvider extends NoiseProvider {
    public static final MapCodec<DualNoiseProvider> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                InclusiveRange.codec(Codec.INT, 1, 64).fieldOf("variety").forGetter(provider -> provider.variety),
                NormalNoise.NoiseParameters.DIRECT_CODEC.fieldOf("slow_noise").forGetter(provider -> provider.slowNoiseParameters),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("slow_scale").forGetter(provider -> provider.slowScale)
            )
            .and(noiseProviderCodec(instance))
            .apply(instance, DualNoiseProvider::new)
    );
    private final InclusiveRange<Integer> variety;
    private final NormalNoise.NoiseParameters slowNoiseParameters;
    private final float slowScale;
    private final NormalNoise slowNoise;

    public DualNoiseProvider(
        InclusiveRange<Integer> variety,
        NormalNoise.NoiseParameters slowNoiseParameters,
        float slowScale,
        long seed,
        NormalNoise.NoiseParameters parameters,
        float scale,
        List<BlockState> states
    ) {
        super(seed, parameters, scale, states);
        this.variety = variety;
        this.slowNoiseParameters = slowNoiseParameters;
        this.slowScale = slowScale;
        this.slowNoise = NormalNoise.create(new WorldgenRandom(new LegacyRandomSource(seed)), slowNoiseParameters);
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.DUAL_NOISE_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        double slowNoiseValue = this.getSlowNoiseValue(pos);
        int i = (int)Mth.clampedMap(slowNoiseValue, -1.0, 1.0, (double)this.variety.minInclusive().intValue(), (double)(this.variety.maxInclusive() + 1));
        List<BlockState> list = Lists.newArrayListWithCapacity(i);

        for (int i1 = 0; i1 < i; i1++) {
            list.add(this.getRandomState(this.states, this.getSlowNoiseValue(pos.offset(i1 * 54545, 0, i1 * 34234))));
        }

        return this.getRandomState(list, pos, this.scale);
    }

    protected double getSlowNoiseValue(BlockPos pos) {
        return this.slowNoise.getValue(pos.getX() * this.slowScale, pos.getY() * this.slowScale, pos.getZ() * this.slowScale);
    }
}
