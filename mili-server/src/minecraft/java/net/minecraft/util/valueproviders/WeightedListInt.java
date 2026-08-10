package net.minecraft.util.valueproviders;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;

public class WeightedListInt extends IntProvider {
    public static final MapCodec<WeightedListInt> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                WeightedList.nonEmptyCodec(IntProvider.CODEC).fieldOf("distribution").forGetter(weightedListInt -> weightedListInt.distribution)
            )
            .apply(instance, WeightedListInt::new)
    );
    private final WeightedList<IntProvider> distribution;
    private final int minValue;
    private final int maxValue;

    public WeightedListInt(WeightedList<IntProvider> distribution) {
        this.distribution = distribution;
        int i = Integer.MAX_VALUE;
        int i1 = Integer.MIN_VALUE;

        for (Weighted<IntProvider> weighted : distribution.unwrap()) {
            int minValue = weighted.value().getMinValue();
            int maxValue = weighted.value().getMaxValue();
            i = Math.min(i, minValue);
            i1 = Math.max(i1, maxValue);
        }

        this.minValue = i;
        this.maxValue = i1;
    }

    @Override
    public int sample(RandomSource random) {
        return this.distribution.getRandomOrThrow(random).sample(random);
    }

    @Override
    public int getMinValue() {
        return this.minValue;
    }

    @Override
    public int getMaxValue() {
        return this.maxValue;
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.WEIGHTED_LIST;
    }
}
