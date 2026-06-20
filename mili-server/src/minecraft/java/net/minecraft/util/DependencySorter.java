package net.minecraft.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DependencySorter<K, V extends DependencySorter.Entry<K>> {
    private final Map<K, V> contents = new HashMap<>();

    public DependencySorter<K, V> addEntry(K key, V value) {
        this.contents.put(key, value);
        return this;
    }

    private void visitDependenciesAndElement(Multimap<K, K> dependencies, Set<K> visited, K element, BiConsumer<K, V> action) {
        if (visited.add(element)) {
            dependencies.get(element).forEach(object -> this.visitDependenciesAndElement(dependencies, visited, (K)object, action));
            V entry = this.contents.get(element);
            if (entry != null) {
                action.accept(element, entry);
            }
        }
    }

    private static <K> boolean isCyclic(Multimap<K, K> dependencies, K source, K target) {
        Collection<K> collection = dependencies.get(target);
        return collection.contains(source) || collection.stream().anyMatch(object -> isCyclic(dependencies, source, (K)object));
    }

    private static <K> void addDependencyIfNotCyclic(Multimap<K, K> dependencies, K source, K target) {
        if (!isCyclic(dependencies, source, target)) {
            dependencies.put(source, target);
        }
    }

    public void orderByDependencies(BiConsumer<K, V> action) {
        Multimap<K, K> multimap = HashMultimap.create();
        this.contents.forEach((object, entry) -> entry.visitRequiredDependencies(object1 -> addDependencyIfNotCyclic(multimap, (K)object, object1)));
        this.contents.forEach((object, entry) -> entry.visitOptionalDependencies(object1 -> addDependencyIfNotCyclic(multimap, (K)object, object1)));
        Set<K> set = new HashSet<>();
        this.contents.keySet().forEach(object -> this.visitDependenciesAndElement(multimap, set, (K)object, action));
    }

    public interface Entry<K> {
        void visitRequiredDependencies(Consumer<K> visitor);

        void visitOptionalDependencies(Consumer<K> visitor);
    }
}
