package net.minecraft;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public class Optionull {
    @Deprecated
    public static <T> T orElse(@Nullable T value, T defaultValue) {
        return Objects.requireNonNullElse(value, defaultValue);
    }

    public static <T, R> @Nullable R map(@Nullable T value, Function<T, R> mapper) {
        return value == null ? null : mapper.apply(value);
    }

    public static <T, R> R mapOrDefault(@Nullable T value, Function<T, R> mapper, R defaultValue) {
        return value == null ? defaultValue : mapper.apply(value);
    }

    public static <T, R> R mapOrElse(@Nullable T value, Function<T, R> mapper, Supplier<R> supplier) {
        return value == null ? supplier.get() : mapper.apply(value);
    }

    public static <T> @Nullable T first(Collection<T> collection) {
        Iterator<T> iterator = collection.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    public static <T> T firstOrDefault(Collection<T> collection, T defaultValue) {
        Iterator<T> iterator = collection.iterator();
        return iterator.hasNext() ? iterator.next() : defaultValue;
    }

    public static <T> T firstOrElse(Collection<T> collection, Supplier<T> supplier) {
        Iterator<T> iterator = collection.iterator();
        return iterator.hasNext() ? iterator.next() : supplier.get();
    }

    public static <T> boolean isNullOrEmpty(T @Nullable [] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(boolean @Nullable [] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(byte @Nullable [] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(char @Nullable [] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(short @Nullable [] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(int @Nullable [] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(long @Nullable [] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(float @Nullable [] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(double @Nullable [] array) {
        return array == null || array.length == 0;
    }
}
