package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Predicate;

public interface CollectionCountsPredicate<T, P extends Predicate<T>> extends Predicate<Iterable<T>> {
    List<CollectionCountsPredicate.Entry<T, P>> unpack();

    static <T, P extends Predicate<T>> Codec<CollectionCountsPredicate<T, P>> codec(Codec<P> testCodec) {
        return CollectionCountsPredicate.Entry.<T, P>codec(testCodec).listOf().xmap(CollectionCountsPredicate::of, CollectionCountsPredicate::unpack);
    }

    @SafeVarargs
    static <T, P extends Predicate<T>> CollectionCountsPredicate<T, P> of(CollectionCountsPredicate.Entry<T, P>... entries) {
        return of(List.of(entries));
    }

    static <T, P extends Predicate<T>> CollectionCountsPredicate<T, P> of(List<CollectionCountsPredicate.Entry<T, P>> entries) {
        return (CollectionCountsPredicate<T, P>)(switch (entries.size()) {
            case 0 -> new CollectionCountsPredicate.Zero();
            case 1 -> new CollectionCountsPredicate.Single(entries.getFirst());
            default -> new CollectionCountsPredicate.Multiple(entries);
        });
    }

    public record Entry<T, P extends Predicate<T>>(P test, MinMaxBounds.Ints count) {
        public static <T, P extends Predicate<T>> Codec<CollectionCountsPredicate.Entry<T, P>> codec(Codec<P> testCodec) {
            return RecordCodecBuilder.create(
                instance -> instance.group(
                        testCodec.fieldOf("test").forGetter(CollectionCountsPredicate.Entry::test),
                        MinMaxBounds.Ints.CODEC.fieldOf("count").forGetter(CollectionCountsPredicate.Entry::count)
                    )
                    .apply(instance, CollectionCountsPredicate.Entry::new)
            );
        }

        public boolean test(Iterable<T> collection) {
            int i = 0;

            for (T object : collection) {
                if (this.test.test(object)) {
                    i++;
                }
            }

            return this.count.matches(i);
        }
    }

    public record Multiple<T, P extends Predicate<T>>(List<CollectionCountsPredicate.Entry<T, P>> entries) implements CollectionCountsPredicate<T, P> {
        @Override
        public boolean test(Iterable<T> collection) {
            for (CollectionCountsPredicate.Entry<T, P> entry : this.entries) {
                if (!entry.test(collection)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
            return this.entries;
        }
    }

    public record Single<T, P extends Predicate<T>>(CollectionCountsPredicate.Entry<T, P> entry) implements CollectionCountsPredicate<T, P> {
        @Override
        public boolean test(Iterable<T> collection) {
            return this.entry.test(collection);
        }

        @Override
        public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
            return List.of(this.entry);
        }
    }

    public static class Zero<T, P extends Predicate<T>> implements CollectionCountsPredicate<T, P> {
        @Override
        public boolean test(Iterable<T> collection) {
            return true;
        }

        @Override
        public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
            return List.of();
        }
    }
}
