package net.minecraft.world.level.levelgen.heightproviders;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public class WeightedListHeight extends HeightProvider {
    public static final MapCodec<WeightedListHeight> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(WeightedList.nonEmptyCodec(HeightProvider.CODEC).fieldOf("distribution").forGetter(provider -> provider.distribution))
            .apply(instance, WeightedListHeight::new)
    );
    private final WeightedList<HeightProvider> distribution;

    public WeightedListHeight(WeightedList<HeightProvider> distribution) {
        this.distribution = distribution;
    }

    @Override
    public int sample(RandomSource random, WorldGenerationContext context) {
        return this.distribution.getRandomOrThrow(random).sample(random, context);
    }

    @Override
    public HeightProviderType<?> getType() {
        return HeightProviderType.WEIGHTED_LIST;
    }
}
