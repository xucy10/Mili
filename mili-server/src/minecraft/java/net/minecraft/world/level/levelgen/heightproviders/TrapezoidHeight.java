package net.minecraft.world.level.levelgen.heightproviders;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.slf4j.Logger;

public class TrapezoidHeight extends HeightProvider {
    public static final MapCodec<TrapezoidHeight> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter(provider -> provider.minInclusive),
                VerticalAnchor.CODEC.fieldOf("max_inclusive").forGetter(provider -> provider.maxInclusive),
                Codec.INT.optionalFieldOf("plateau", 0).forGetter(provider -> provider.plateau)
            )
            .apply(instance, TrapezoidHeight::new)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private final VerticalAnchor minInclusive;
    private final VerticalAnchor maxInclusive;
    private final int plateau;

    private TrapezoidHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int plateau) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.plateau = plateau;
    }

    public static TrapezoidHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int plateau) {
        return new TrapezoidHeight(minInclusive, maxInclusive, plateau);
    }

    public static TrapezoidHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        return of(minInclusive, maxInclusive, 0);
    }

    @Override
    public int sample(RandomSource random, WorldGenerationContext context) {
        int i = this.minInclusive.resolveY(context);
        int i1 = this.maxInclusive.resolveY(context);
        if (i > i1) {
            LOGGER.warn("Empty height range: {}", this);
            return i;
        } else {
            int i2 = i1 - i;
            if (this.plateau >= i2) {
                return Mth.randomBetweenInclusive(random, i, i1);
            } else {
                int i3 = (i2 - this.plateau) / 2;
                int i4 = i2 - i3;
                return i + Mth.randomBetweenInclusive(random, 0, i4) + Mth.randomBetweenInclusive(random, 0, i3);
            }
        }
    }

    @Override
    public HeightProviderType<?> getType() {
        return HeightProviderType.TRAPEZOID;
    }

    @Override
    public String toString() {
        return this.plateau == 0
            ? "triangle (" + this.minInclusive + "-" + this.maxInclusive + ")"
            : "trapezoid(" + this.plateau + ") in [" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
