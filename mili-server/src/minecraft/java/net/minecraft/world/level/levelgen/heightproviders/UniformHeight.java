package net.minecraft.world.level.levelgen.heightproviders;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.slf4j.Logger;

public class UniformHeight extends HeightProvider {
    public static final MapCodec<UniformHeight> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter(provider -> provider.minInclusive),
                VerticalAnchor.CODEC.fieldOf("max_inclusive").forGetter(provider -> provider.maxInclusive)
            )
            .apply(instance, UniformHeight::new)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private final VerticalAnchor minInclusive;
    private final VerticalAnchor maxInclusive;
    private final LongSet warnedFor = new LongOpenHashSet();

    private UniformHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static UniformHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        return new UniformHeight(minInclusive, maxInclusive);
    }

    @Override
    public int sample(RandomSource random, WorldGenerationContext context) {
        int i = this.minInclusive.resolveY(context);
        int i1 = this.maxInclusive.resolveY(context);
        if (i > i1) {
            if (this.warnedFor.add((long)i << 32 | i1)) {
                LOGGER.warn("Empty height range: {}", this);
            }

            return i;
        } else {
            return Mth.randomBetweenInclusive(random, i, i1);
        }
    }

    @Override
    public HeightProviderType<?> getType() {
        return HeightProviderType.UNIFORM;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
