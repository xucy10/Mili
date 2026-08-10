package net.minecraft.world.level.levelgen.heightproviders;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.slf4j.Logger;

public class BiasedToBottomHeight extends HeightProvider {
    public static final MapCodec<BiasedToBottomHeight> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter(provider -> provider.minInclusive),
                VerticalAnchor.CODEC.fieldOf("max_inclusive").forGetter(provider -> provider.maxInclusive),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("inner", 1).forGetter(provider -> provider.inner)
            )
            .apply(instance, BiasedToBottomHeight::new)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private final VerticalAnchor minInclusive;
    private final VerticalAnchor maxInclusive;
    private final int inner;

    private BiasedToBottomHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int inner) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.inner = inner;
    }

    public static BiasedToBottomHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int inner) {
        return new BiasedToBottomHeight(minInclusive, maxInclusive, inner);
    }

    @Override
    public int sample(RandomSource random, WorldGenerationContext context) {
        int i = this.minInclusive.resolveY(context);
        int i1 = this.maxInclusive.resolveY(context);
        if (i1 - i - this.inner + 1 <= 0) {
            LOGGER.warn("Empty height range: {}", this);
            return i;
        } else {
            int randomInt = random.nextInt(i1 - i - this.inner + 1);
            return random.nextInt(randomInt + this.inner) + i;
        }
    }

    @Override
    public HeightProviderType<?> getType() {
        return HeightProviderType.BIASED_TO_BOTTOM;
    }

    @Override
    public String toString() {
        return "biased[" + this.minInclusive + "-" + this.maxInclusive + " inner: " + this.inner + "]";
    }
}
