package net.minecraft.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;

public class LayeredRegistryAccess<T> {
    private final List<T> keys;
    private final List<RegistryAccess.Frozen> values;
    private final RegistryAccess.Frozen composite;

    public LayeredRegistryAccess(List<T> keys) {
        this(keys, Util.make(() -> {
            RegistryAccess.Frozen[] frozens = new RegistryAccess.Frozen[keys.size()];
            Arrays.fill(frozens, RegistryAccess.EMPTY);
            return Arrays.asList(frozens);
        }));
    }

    private LayeredRegistryAccess(List<T> keys, List<RegistryAccess.Frozen> values) {
        this.keys = List.copyOf(keys);
        this.values = List.copyOf(values);
        this.composite = new RegistryAccess.ImmutableRegistryAccess(collectRegistries(values.stream())).freeze();
    }

    private int getLayerIndexOrThrow(T key) {
        int index = this.keys.indexOf(key);
        if (index == -1) {
            throw new IllegalStateException("Can't find " + key + " inside " + this.keys);
        } else {
            return index;
        }
    }

    public RegistryAccess.Frozen getLayer(T key) {
        int layerIndexOrThrow = this.getLayerIndexOrThrow(key);
        return this.values.get(layerIndexOrThrow);
    }

    public RegistryAccess.Frozen getAccessForLoading(T key) {
        int layerIndexOrThrow = this.getLayerIndexOrThrow(key);
        return this.getCompositeAccessForLayers(0, layerIndexOrThrow);
    }

    public RegistryAccess.Frozen getAccessFrom(T key) {
        int layerIndexOrThrow = this.getLayerIndexOrThrow(key);
        return this.getCompositeAccessForLayers(layerIndexOrThrow, this.values.size());
    }

    private RegistryAccess.Frozen getCompositeAccessForLayers(int startIndex, int endIndex) {
        return new RegistryAccess.ImmutableRegistryAccess(collectRegistries(this.values.subList(startIndex, endIndex).stream())).freeze();
    }

    public LayeredRegistryAccess<T> replaceFrom(T key, RegistryAccess.Frozen... values) {
        return this.replaceFrom(key, Arrays.asList(values));
    }

    public LayeredRegistryAccess<T> replaceFrom(T key, List<RegistryAccess.Frozen> values) {
        int layerIndexOrThrow = this.getLayerIndexOrThrow(key);
        if (values.size() > this.values.size() - layerIndexOrThrow) {
            throw new IllegalStateException("Too many values to replace");
        } else {
            List<RegistryAccess.Frozen> list = new ArrayList<>();

            for (int i = 0; i < layerIndexOrThrow; i++) {
                list.add(this.values.get(i));
            }

            list.addAll(values);

            while (list.size() < this.values.size()) {
                list.add(RegistryAccess.EMPTY);
            }

            return new LayeredRegistryAccess<>(this.keys, list);
        }
    }

    public RegistryAccess.Frozen compositeAccess() {
        return this.composite;
    }

    private static Map<ResourceKey<? extends Registry<?>>, Registry<?>> collectRegistries(Stream<? extends RegistryAccess> accesses) {
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> map = new HashMap<>();
        accesses.forEach(access -> access.registries().forEach(registry -> {
            if (map.put(registry.key(), registry.value()) != null) {
                throw new IllegalStateException("Duplicated registry " + registry.key());
            }
        }));
        return map;
    }
}
