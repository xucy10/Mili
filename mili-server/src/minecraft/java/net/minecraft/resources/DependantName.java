package net.minecraft.resources;

@FunctionalInterface
public interface DependantName<T, V> {
    V get(ResourceKey<T> key);

    static <T, V> DependantName<T, V> fixed(V value) {
        return key -> value;
    }
}
