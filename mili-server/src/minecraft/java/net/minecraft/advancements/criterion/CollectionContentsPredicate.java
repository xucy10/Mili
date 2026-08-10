package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public interface CollectionContentsPredicate<T, P extends Predicate<T>> extends Predicate<Iterable<T>> {
    List<P> unpack();

    static <T, P extends Predicate<T>> Codec<CollectionContentsPredicate<T, P>> codec(Codec<P> testCodec) {
        return testCodec.listOf().xmap(CollectionContentsPredicate::of, CollectionContentsPredicate::unpack);
    }

    @SafeVarargs
    static <T, P extends Predicate<T>> CollectionContentsPredicate<T, P> of(P... tests) {
        return of(List.of(tests));
    }

    static <T, P extends Predicate<T>> CollectionContentsPredicate<T, P> of(List<P> tests) {
        return (CollectionContentsPredicate<T, P>)(switch (tests.size()) {
            case 0 -> new CollectionContentsPredicate.Zero();
            case 1 -> new CollectionContentsPredicate.Single(tests.getFirst());
            default -> new CollectionContentsPredicate.Multiple(tests);
        });
    }

    public record Multiple<T, P extends Predicate<T>>(List<P> tests) implements CollectionContentsPredicate<T, P> {
        @Override
        public boolean test(Iterable<T> contents) {
            List<Predicate<T>> list = new ArrayList<>(this.tests);

            for (T object : contents) {
                list.removeIf(predicate -> predicate.test(object));
                if (list.isEmpty()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public List<P> unpack() {
            return this.tests;
        }
    }

    public record Single<T, P extends Predicate<T>>(P test) implements CollectionContentsPredicate<T, P> {
        @Override
        public boolean test(Iterable<T> contents) {
            for (T object : contents) {
                if (this.test.test(object)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public List<P> unpack() {
            return List.of(this.test);
        }
    }

    public static class Zero<T, P extends Predicate<T>> implements CollectionContentsPredicate<T, P> {
        @Override
        public boolean test(Iterable<T> contents) {
            return true;
        }

        @Override
        public List<P> unpack() {
            return List.of();
        }
    }
}
