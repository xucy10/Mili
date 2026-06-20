package net.minecraft;

import java.util.Objects;

@FunctionalInterface
public interface CharPredicate {
    boolean test(char value);

    default CharPredicate and(CharPredicate predicate) {
        Objects.requireNonNull(predicate);
        return value -> this.test(value) && predicate.test(value);
    }

    default CharPredicate negate() {
        return value -> !this.test(value);
    }

    default CharPredicate or(CharPredicate predicate) {
        Objects.requireNonNull(predicate);
        return value -> this.test(value) || predicate.test(value);
    }
}
