package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public abstract class DelegatingOps<T> implements DynamicOps<T> {
    protected final DynamicOps<T> delegate;

    protected DelegatingOps(DynamicOps<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T empty() {
        return this.delegate.empty();
    }

    @Override
    public T emptyMap() {
        return this.delegate.emptyMap();
    }

    @Override
    public T emptyList() {
        return this.delegate.emptyList();
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, T input) {
        return (U)(Objects.equals(outOps, this.delegate) ? input : this.delegate.convertTo(outOps, input));
    }

    @Override
    public DataResult<Number> getNumberValue(T input) {
        return this.delegate.getNumberValue(input);
    }

    @Override
    public T createNumeric(Number i) {
        return this.delegate.createNumeric(i);
    }

    @Override
    public T createByte(byte value) {
        return this.delegate.createByte(value);
    }

    @Override
    public T createShort(short value) {
        return this.delegate.createShort(value);
    }

    @Override
    public T createInt(int value) {
        return this.delegate.createInt(value);
    }

    @Override
    public T createLong(long value) {
        return this.delegate.createLong(value);
    }

    @Override
    public T createFloat(float value) {
        return this.delegate.createFloat(value);
    }

    @Override
    public T createDouble(double value) {
        return this.delegate.createDouble(value);
    }

    @Override
    public DataResult<Boolean> getBooleanValue(T input) {
        return this.delegate.getBooleanValue(input);
    }

    @Override
    public T createBoolean(boolean value) {
        return this.delegate.createBoolean(value);
    }

    @Override
    public DataResult<String> getStringValue(T input) {
        return this.delegate.getStringValue(input);
    }

    @Override
    public T createString(String value) {
        return this.delegate.createString(value);
    }

    @Override
    public DataResult<T> mergeToList(T list, T value) {
        return this.delegate.mergeToList(list, value);
    }

    @Override
    public DataResult<T> mergeToList(T list, List<T> values) {
        return this.delegate.mergeToList(list, values);
    }

    @Override
    public DataResult<T> mergeToMap(T map, T key, T value) {
        return this.delegate.mergeToMap(map, key, value);
    }

    @Override
    public DataResult<T> mergeToMap(T map, MapLike<T> values) {
        return this.delegate.mergeToMap(map, values);
    }

    @Override
    public DataResult<T> mergeToMap(T map, Map<T, T> values) {
        return this.delegate.mergeToMap(map, values);
    }

    @Override
    public DataResult<T> mergeToPrimitive(T prefix, T value) {
        return this.delegate.mergeToPrimitive(prefix, value);
    }

    @Override
    public DataResult<Stream<Pair<T, T>>> getMapValues(T input) {
        return this.delegate.getMapValues(input);
    }

    @Override
    public DataResult<Consumer<BiConsumer<T, T>>> getMapEntries(T input) {
        return this.delegate.getMapEntries(input);
    }

    @Override
    public T createMap(Map<T, T> map) {
        return this.delegate.createMap(map);
    }

    @Override
    public T createMap(Stream<Pair<T, T>> map) {
        return this.delegate.createMap(map);
    }

    @Override
    public DataResult<MapLike<T>> getMap(T input) {
        return this.delegate.getMap(input);
    }

    @Override
    public DataResult<Stream<T>> getStream(T input) {
        return this.delegate.getStream(input);
    }

    @Override
    public DataResult<Consumer<Consumer<T>>> getList(T input) {
        return this.delegate.getList(input);
    }

    @Override
    public T createList(Stream<T> input) {
        return this.delegate.createList(input);
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(T input) {
        return this.delegate.getByteBuffer(input);
    }

    @Override
    public T createByteList(ByteBuffer input) {
        return this.delegate.createByteList(input);
    }

    @Override
    public DataResult<IntStream> getIntStream(T input) {
        return this.delegate.getIntStream(input);
    }

    @Override
    public T createIntList(IntStream input) {
        return this.delegate.createIntList(input);
    }

    @Override
    public DataResult<LongStream> getLongStream(T input) {
        return this.delegate.getLongStream(input);
    }

    @Override
    public T createLongList(LongStream input) {
        return this.delegate.createLongList(input);
    }

    @Override
    public T remove(T input, String key) {
        return this.delegate.remove(input, key);
    }

    @Override
    public boolean compressMaps() {
        return this.delegate.compressMaps();
    }

    @Override
    public ListBuilder<T> listBuilder() {
        return new DelegatingOps.DelegateListBuilder(this.delegate.listBuilder());
    }

    @Override
    public RecordBuilder<T> mapBuilder() {
        return new DelegatingOps.DelegateRecordBuilder(this.delegate.mapBuilder());
    }

    protected class DelegateListBuilder implements ListBuilder<T> {
        private final ListBuilder<T> original;

        protected DelegateListBuilder(final ListBuilder<T> original) {
            this.original = original;
        }

        @Override
        public DynamicOps<T> ops() {
            return DelegatingOps.this;
        }

        @Override
        public DataResult<T> build(T prefix) {
            return this.original.build(prefix);
        }

        @Override
        public ListBuilder<T> add(T value) {
            this.original.add(value);
            return this;
        }

        @Override
        public ListBuilder<T> add(DataResult<T> value) {
            this.original.add(value);
            return this;
        }

        @Override
        public <E> ListBuilder<T> add(E element, Encoder<E> encoder) {
            this.original.add(encoder.encodeStart(this.ops(), element));
            return this;
        }

        @Override
        public <E> ListBuilder<T> addAll(Iterable<E> elements, Encoder<E> encoder) {
            elements.forEach(object -> this.original.add(encoder.encode((E)object, this.ops(), (T)this.ops().empty())));
            return this;
        }

        @Override
        public ListBuilder<T> withErrorsFrom(DataResult<?> result) {
            this.original.withErrorsFrom(result);
            return this;
        }

        @Override
        public ListBuilder<T> mapError(UnaryOperator<String> onError) {
            this.original.mapError(onError);
            return this;
        }

        @Override
        public DataResult<T> build(DataResult<T> prefix) {
            return this.original.build(prefix);
        }
    }

    protected class DelegateRecordBuilder implements RecordBuilder<T> {
        private final RecordBuilder<T> original;

        protected DelegateRecordBuilder(final RecordBuilder<T> original) {
            this.original = original;
        }

        @Override
        public DynamicOps<T> ops() {
            return DelegatingOps.this;
        }

        @Override
        public RecordBuilder<T> add(T key, T value) {
            this.original.add(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(T key, DataResult<T> value) {
            this.original.add(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(DataResult<T> key, DataResult<T> value) {
            this.original.add(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(String key, T value) {
            this.original.add(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(String key, DataResult<T> value) {
            this.original.add(key, value);
            return this;
        }

        @Override
        public <E> RecordBuilder<T> add(String key, E element, Encoder<E> encoder) {
            return this.original.add(key, encoder.encodeStart(this.ops(), element));
        }

        @Override
        public RecordBuilder<T> withErrorsFrom(DataResult<?> result) {
            this.original.withErrorsFrom(result);
            return this;
        }

        @Override
        public RecordBuilder<T> setLifecycle(Lifecycle lifecycle) {
            this.original.setLifecycle(lifecycle);
            return this;
        }

        @Override
        public RecordBuilder<T> mapError(UnaryOperator<String> onError) {
            this.original.mapError(onError);
            return this;
        }

        @Override
        public DataResult<T> build(T prefix) {
            return this.original.build(prefix);
        }

        @Override
        public DataResult<T> build(DataResult<T> prefix) {
            return this.original.build(prefix);
        }
    }
}
