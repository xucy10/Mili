package net.minecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public class RandomSequence {
    public static final Codec<RandomSequence> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(XoroshiroRandomSource.CODEC.fieldOf("source").forGetter(randomSequence -> randomSequence.source))
            .apply(instance, RandomSequence::new)
    );
    private final XoroshiroRandomSource source;

    public RandomSequence(XoroshiroRandomSource source) {
        this.source = source;
    }

    public RandomSequence(long seed, Identifier location) {
        this(createSequence(seed, Optional.of(location)));
    }

    public RandomSequence(long seed, Optional<Identifier> location) {
        this(createSequence(seed, location));
    }

    private static XoroshiroRandomSource createSequence(long seed, Optional<Identifier> location) {
        RandomSupport.Seed128bit seed128bit = RandomSupport.upgradeSeedTo128bitUnmixed(seed);
        if (location.isPresent()) {
            seed128bit = seed128bit.xor(seedForKey(location.get()));
        }

        return new XoroshiroRandomSource(seed128bit.mixed());
    }

    public static RandomSupport.Seed128bit seedForKey(Identifier key) {
        return RandomSupport.seedFromHashOf(key.toString());
    }

    public RandomSource random() {
        return this.source;
    }
}
