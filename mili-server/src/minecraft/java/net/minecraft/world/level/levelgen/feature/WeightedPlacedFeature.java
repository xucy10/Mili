package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class WeightedPlacedFeature {
    public static final Codec<WeightedPlacedFeature> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                PlacedFeature.CODEC.fieldOf("feature").forGetter(weightedPlacedFeature -> weightedPlacedFeature.feature),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(weightedPlacedFeature -> weightedPlacedFeature.chance)
            )
            .apply(instance, WeightedPlacedFeature::new)
    );
    public final Holder<PlacedFeature> feature;
    public final float chance;

    public WeightedPlacedFeature(Holder<PlacedFeature> feature, float chance) {
        this.feature = feature;
        this.chance = chance;
    }

    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos pos) {
        return this.feature.value().place(level, chunkGenerator, random, pos);
    }
}
