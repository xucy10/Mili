package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public class NoiseBasedCountPlacement extends RepeatingPlacement {
    public static final MapCodec<NoiseBasedCountPlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.INT.fieldOf("noise_to_count_ratio").forGetter(placement -> placement.noiseToCountRatio),
                Codec.DOUBLE.fieldOf("noise_factor").forGetter(placement -> placement.noiseFactor),
                Codec.DOUBLE.fieldOf("noise_offset").orElse(0.0).forGetter(placement -> placement.noiseOffset)
            )
            .apply(instance, NoiseBasedCountPlacement::new)
    );
    private final int noiseToCountRatio;
    private final double noiseFactor;
    private final double noiseOffset;

    private NoiseBasedCountPlacement(int noiseToCountRatio, double noiseFactor, double noiseOffset) {
        this.noiseToCountRatio = noiseToCountRatio;
        this.noiseFactor = noiseFactor;
        this.noiseOffset = noiseOffset;
    }

    public static NoiseBasedCountPlacement of(int noiseToCountRatio, double noiseFactor, double noiseOffset) {
        return new NoiseBasedCountPlacement(noiseToCountRatio, noiseFactor, noiseOffset);
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        double value = Biome.BIOME_INFO_NOISE.getValue(pos.getX() / this.noiseFactor, pos.getZ() / this.noiseFactor, false);
        return (int)Math.ceil((value + this.noiseOffset) * this.noiseToCountRatio);
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.NOISE_BASED_COUNT;
    }
}
