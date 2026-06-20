package net.minecraft.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public class ByIdMap {
    private static <T> IntFunction<T> createMap(ToIntFunction<T> keyExtractor, T[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty value list");
        } else {
            Int2ObjectMap<T> map = new Int2ObjectOpenHashMap<>();

            for (T object : values) {
                int i = keyExtractor.applyAsInt(object);
                T object1 = map.put(i, object);
                if (object1 != null) {
                    throw new IllegalArgumentException("Duplicate entry on id " + i + ": current=" + object + ", previous=" + object1);
                }
            }

            return map;
        }
    }

    public static <T> IntFunction<T> sparse(ToIntFunction<T> keyExtractor, T[] values, T fallback) {
        IntFunction<T> intFunction = createMap(keyExtractor, values);
        return key -> Objects.requireNonNullElse(intFunction.apply(key), fallback);
    }

    private static <T> T[] createSortedArray(ToIntFunction<T> keyExtractor, T[] values) {
        int i = values.length;
        if (i == 0) {
            throw new IllegalArgumentException("Empty value list");
        } else {
            T[] objects = (T[])values.clone();
            Arrays.fill(objects, null);

            for (T object : values) {
                int i1 = keyExtractor.applyAsInt(object);
                if (i1 < 0 || i1 >= i) {
                    throw new IllegalArgumentException("Values are not continous, found index " + i1 + " for value " + object);
                }

                T object1 = objects[i1];
                if (object1 != null) {
                    throw new IllegalArgumentException("Duplicate entry on id " + i1 + ": current=" + object + ", previous=" + object1);
                }

                objects[i1] = object;
            }

            for (int i2 = 0; i2 < i; i2++) {
                if (objects[i2] == null) {
                    throw new IllegalArgumentException("Missing value at index: " + i2);
                }
            }

            return objects;
        }
    }

    public static <T> IntFunction<T> continuous(ToIntFunction<T> keyExtractor, T[] values, ByIdMap.OutOfBoundsStrategy outOfBoundsStrategy) {
        T[] objects = createSortedArray(keyExtractor, values);
        int i = objects.length;

        return switch (outOfBoundsStrategy) {
            case ZERO -> {
                T object = objects[0];
                yield key -> key >= 0 && key < i ? objects[key] : object;
            }
            case WRAP -> key -> objects[Mth.positiveModulo(key, i)];
            case CLAMP -> key -> objects[Mth.clamp(key, 0, i - 1)];
        };
    }

    public static enum OutOfBoundsStrategy {
        ZERO,
        WRAP,
        CLAMP;
    }
}
