package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

public class ValueInputContextHelper {
    final HolderLookup.Provider lookup;
    private final DynamicOps<Tag> ops;
    final ValueInput.ValueInputList emptyChildList = new ValueInput.ValueInputList() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Stream<ValueInput> stream() {
            return Stream.empty();
        }

        @Override
        public Iterator<ValueInput> iterator() {
            return Collections.emptyIterator();
        }
    };
    private final ValueInput.TypedInputList<Object> emptyTypedList = new ValueInput.TypedInputList<Object>() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Stream<Object> stream() {
            return Stream.empty();
        }

        @Override
        public Iterator<Object> iterator() {
            return Collections.emptyIterator();
        }
    };
    private final ValueInput empty = new ValueInput() {
        @Override
        public <T> Optional<T> read(String key, Codec<T> codec) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> read(MapCodec<T> codec) {
            return Optional.empty();
        }

        @Override
        public Optional<ValueInput> child(String key) {
            return Optional.empty();
        }

        @Override
        public ValueInput childOrEmpty(String key) {
            return this;
        }

        @Override
        public Optional<ValueInput.ValueInputList> childrenList(String key) {
            return Optional.empty();
        }

        @Override
        public ValueInput.ValueInputList childrenListOrEmpty(String key) {
            return ValueInputContextHelper.this.emptyChildList;
        }

        @Override
        public <T> Optional<ValueInput.TypedInputList<T>> list(String key, Codec<T> elementCodec) {
            return Optional.empty();
        }

        @Override
        public <T> ValueInput.TypedInputList<T> listOrEmpty(String key, Codec<T> elementCodec) {
            return ValueInputContextHelper.this.emptyTypedList();
        }

        @Override
        public boolean getBooleanOr(String key, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public byte getByteOr(String key, byte defaultValue) {
            return defaultValue;
        }

        @Override
        public int getShortOr(String key, short defaultValue) {
            return defaultValue;
        }

        @Override
        public Optional<Integer> getInt(String key) {
            return Optional.empty();
        }

        @Override
        public int getIntOr(String key, int defaultValue) {
            return defaultValue;
        }

        @Override
        public long getLongOr(String key, long defaultValue) {
            return defaultValue;
        }

        @Override
        public Optional<Long> getLong(String key) {
            return Optional.empty();
        }

        @Override
        public float getFloatOr(String key, float defaultValue) {
            return defaultValue;
        }

        @Override
        public double getDoubleOr(String key, double defaultValue) {
            return defaultValue;
        }

        @Override
        public Optional<String> getString(String key) {
            return Optional.empty();
        }

        @Override
        public String getStringOr(String key, String defaultValue) {
            return defaultValue;
        }

        @Override
        public HolderLookup.Provider lookup() {
            return ValueInputContextHelper.this.lookup;
        }

        @Override
        public Optional<int[]> getIntArray(String key) {
            return Optional.empty();
        }
    };

    public ValueInputContextHelper(HolderLookup.Provider lookup, DynamicOps<Tag> ops) {
        this.lookup = lookup;
        this.ops = lookup.createSerializationContext(ops);
    }

    public DynamicOps<Tag> ops() {
        return this.ops;
    }

    public HolderLookup.Provider lookup() {
        return this.lookup;
    }

    public ValueInput empty() {
        return this.empty;
    }

    public ValueInput.ValueInputList emptyList() {
        return this.emptyChildList;
    }

    public <T> ValueInput.TypedInputList<T> emptyTypedList() {
        return (ValueInput.TypedInputList<T>)this.emptyTypedList;
    }
}
