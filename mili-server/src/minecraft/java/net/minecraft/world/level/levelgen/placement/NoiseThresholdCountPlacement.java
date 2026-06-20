package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public class NoiseThresholdCountPlacement extends RepeatingPlacement {
    public static final MapCodec<NoiseThresholdCountPlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.DOUBLE.fieldOf("noise_level").forGetter(placement -> placement.noiseLevel),
                Codec.INT.fieldOf("below_noise").forGetter(placement -> placement.belowNoise),
                Codec.INT.fieldOf("above_noise").forGetter(placement -> placement.aboveNoise)
            )
            .apply(instance, NoiseThresholdCountPlacement::new)
    );
    private final double noiseLevel;
    private final int belowNoise;
    private final int aboveNoise;

    private NoiseThresholdCountPlacement(double noiseLevel, int belowNoise, int aboveNoise) {
        this.noiseLevel = noiseLevel;
        this.belowNoise = belowNoise;
        this.aboveNoise = aboveNoise;
    }

    public static NoiseThresholdCountPlacement of(double noiseLevel, int belowNoise, int aboveNoise) {
        return new NoiseThresholdCountPlacement(noiseLevel, belowNoise, aboveNoise);
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        double value = Biome.BIOME_INFO_NOISE.getValue(pos.getX() / 200.0, pos.getZ() / 200.0, false);
        return value < this.noiseLevel ? this.belowNoise : this.aboveNoise;
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.NOISE_THRESHOLD_COUNT;
    }
}
