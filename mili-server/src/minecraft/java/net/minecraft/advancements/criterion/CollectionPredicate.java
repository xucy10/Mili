package net.minecraft.advancements.criterion;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;

public record CollectionPredicate<T, P extends Predicate<T>>(
    Optional<CollectionContentsPredicate<T, P>> contains, Optional<CollectionCountsPredicate<T, P>> counts, Optional<MinMaxBounds.Ints> size
) implements Predicate<Iterable<T>> {
    public static <T, P extends Predicate<T>> Codec<CollectionPredicate<T, P>> codec(Codec<P> testCodec) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    CollectionContentsPredicate.<T, P>codec(testCodec).optionalFieldOf("contains").forGetter(CollectionPredicate::contains),
                    CollectionCountsPredicate.<T, P>codec(testCodec).optionalFieldOf("count").forGetter(CollectionPredicate::counts),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("size").forGetter(CollectionPredicate::size)
                )
                .apply(instance, CollectionPredicate::new)
        );
    }

    @Override
    public boolean test(Iterable<T> collection) {
        return (!this.contains.isPresent() || this.contains.get().test(collection))
            && (!this.counts.isPresent() || this.counts.get().test(collection))
            && (!this.size.isPresent() || this.size.get().matches(Iterables.size(collection)));
    }
}
