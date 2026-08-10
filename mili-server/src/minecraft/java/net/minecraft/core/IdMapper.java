package net.minecraft.core;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class IdMapper<T> implements IdMap<T> {
    private int nextId;
    private final Reference2IntMap<T> tToId;
    private final List<T> idToT;

    public IdMapper() {
        this(512);
    }

    public IdMapper(int expectedSize) {
        this.idToT = Lists.newArrayListWithExpectedSize(expectedSize);
        this.tToId = new Reference2IntOpenHashMap<>(expectedSize);
        this.tToId.defaultReturnValue(-1);
    }

    public void addMapping(T key, int value) {
        this.tToId.put(key, value);

        while (this.idToT.size() <= value) {
            this.idToT.add(null);
        }

        this.idToT.set(value, key);
        if (this.nextId <= value) {
            this.nextId = value + 1;
        }
    }

    public void add(T key) {
        this.addMapping(key, this.nextId);
    }

    @Override
    public int getId(T value) {
        return this.tToId.getInt(value);
    }

    @Override
    public final @Nullable T byId(int id) {
        return id >= 0 && id < this.idToT.size() ? this.idToT.get(id) : null;
    }

    @Override
    public Iterator<T> iterator() {
        return Iterators.filter(this.idToT.iterator(), Objects::nonNull);
    }

    public boolean contains(int id) {
        return this.byId(id) != null;
    }

    @Override
    public int size() {
        return this.tToId.size();
    }
}
