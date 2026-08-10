package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ClassInstanceMultiMap<T> extends AbstractCollection<T> {
    private final Map<Class<?>, List<T>> byClass = Maps.newHashMap();
    private final Class<T> baseClass;
    private final List<T> allInstances = Lists.newArrayList();

    public ClassInstanceMultiMap(Class<T> baseClass) {
        this.baseClass = baseClass;
        this.byClass.put(baseClass, this.allInstances);
    }

    @Override
    public boolean add(T value) {
        boolean flag = false;

        for (Entry<Class<?>, List<T>> entry : this.byClass.entrySet()) {
            if (entry.getKey().isInstance(value)) {
                flag |= entry.getValue().add(value);
            }
        }

        return flag;
    }

    @Override
    public boolean remove(Object key) {
        boolean flag = false;

        for (Entry<Class<?>, List<T>> entry : this.byClass.entrySet()) {
            if (entry.getKey().isInstance(key)) {
                List<T> list = entry.getValue();
                flag |= list.remove(key);
            }
        }

        return flag;
    }

    @Override
    public boolean contains(Object key) {
        return this.find(key.getClass()).contains(key);
    }

    public <S> Collection<S> find(Class<S> type) {
        if (!this.baseClass.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Don't know how to search for " + type);
        } else {
            List<? extends T> list = this.byClass
                .computeIfAbsent(type, clazz -> this.allInstances.stream().filter(clazz::isInstance).collect(Util.toMutableList()));
            return (Collection<S>)Collections.unmodifiableCollection(list);
        }
    }

    @Override
    public Iterator<T> iterator() {
        return (Iterator<T>)(this.allInstances.isEmpty() ? Collections.emptyIterator() : Iterators.unmodifiableIterator(this.allInstances.iterator()));
    }

    public List<T> getAllInstances() {
        return ImmutableList.copyOf(this.allInstances);
    }

    @Override
    public int size() {
        return this.allInstances.size();
    }
}
