package net.minecraft.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractUniversalBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class HashOps implements DynamicOps<HashCode> {
    private static final byte TAG_EMPTY = 1;
    private static final byte TAG_MAP_START = 2;
    private static final byte TAG_MAP_END = 3;
    private static final byte TAG_LIST_START = 4;
    private static final byte TAG_LIST_END = 5;
    private static final byte TAG_BYTE = 6;
    private static final byte TAG_SHORT = 7;
    private static final byte TAG_INT = 8;
    private static final byte TAG_LONG = 9;
    private static final byte TAG_FLOAT = 10;
    private static final byte TAG_DOUBLE = 11;
    private static final byte TAG_STRING = 12;
    private static final byte TAG_BOOLEAN = 13;
    private static final byte TAG_BYTE_ARRAY_START = 14;
    private static final byte TAG_BYTE_ARRAY_END = 15;
    private static final byte TAG_INT_ARRAY_START = 16;
    private static final byte TAG_INT_ARRAY_END = 17;
    private static final byte TAG_LONG_ARRAY_START = 18;
    private static final byte TAG_LONG_ARRAY_END = 19;
    private static final byte[] EMPTY_PAYLOAD = new byte[]{1};
    private static final byte[] FALSE_PAYLOAD = new byte[]{13, 0};
    private static final byte[] TRUE_PAYLOAD = new byte[]{13, 1};
    public static final byte[] EMPTY_MAP_PAYLOAD = new byte[]{2, 3};
    public static final byte[] EMPTY_LIST_PAYLOAD = new byte[]{4, 5};
    private static final DataResult<Object> UNSUPPORTED_OPERATION_ERROR = DataResult.error(() -> "Unsupported operation");
    private static final Comparator<HashCode> HASH_COMPARATOR = Comparator.comparingLong(HashCode::padToLong);
    private static final Comparator<Entry<HashCode, HashCode>> MAP_ENTRY_ORDER = Entry.<HashCode, HashCode>comparingByKey(HASH_COMPARATOR)
        .thenComparing(Entry.comparingByValue(HASH_COMPARATOR));
    private static final Comparator<Pair<HashCode, HashCode>> MAPLIKE_ENTRY_ORDER = Comparator.<Pair<HashCode, HashCode>, HashCode>comparing(Pair::getFirst, HASH_COMPARATOR)
        .thenComparing(Pair::getSecond, HASH_COMPARATOR);
    public static final HashOps CRC32C_INSTANCE = new HashOps(Hashing.crc32c());
    final HashFunction hashFunction;
    final HashCode empty;
    private final HashCode emptyMap;
    private final HashCode emptyList;
    private final HashCode trueHash;
    private final HashCode falseHash;

    public HashOps(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
        this.empty = hashFunction.hashBytes(EMPTY_PAYLOAD);
        this.emptyMap = hashFunction.hashBytes(EMPTY_MAP_PAYLOAD);
        this.emptyList = hashFunction.hashBytes(EMPTY_LIST_PAYLOAD);
        this.falseHash = hashFunction.hashBytes(FALSE_PAYLOAD);
        this.trueHash = hashFunction.hashBytes(TRUE_PAYLOAD);
    }

    @Override
    public HashCode empty() {
        return this.empty;
    }

    @Override
    public HashCode emptyMap() {
        return this.emptyMap;
    }

    @Override
    public HashCode emptyList() {
        return this.emptyList;
    }

    @Override
    public HashCode createNumeric(Number i) {
        return switch (i) {
            case Byte _byte -> this.createByte(_byte);
            case Short _short -> this.createShort(_short);
            case Integer integer -> this.createInt(integer);
            case Long _long -> this.createLong(_long);
            case Double _double -> this.createDouble(_double);
            case Float _float -> this.createFloat(_float);
            default -> this.createDouble(i.doubleValue());
        };
    }

    @Override
    public HashCode createByte(byte value) {
        return this.hashFunction.newHasher(2).putByte((byte)6).putByte(value).hash();
    }

    @Override
    public HashCode createShort(short value) {
        return this.hashFunction.newHasher(3).putByte((byte)7).putShort(value).hash();
    }

    @Override
    public HashCode createInt(int value) {
        return this.hashFunction.newHasher(5).putByte((byte)8).putInt(value).hash();
    }

    @Override
    public HashCode createLong(long value) {
        return this.hashFunction.newHasher(9).putByte((byte)9).putLong(value).hash();
    }

    @Override
    public HashCode createFloat(float value) {
        return this.hashFunction.newHasher(5).putByte((byte)10).putFloat(value).hash();
    }

    @Override
    public HashCode createDouble(double value) {
        return this.hashFunction.newHasher(9).putByte((byte)11).putDouble(value).hash();
    }

    @Override
    public HashCode createString(String value) {
        return this.hashFunction.newHasher().putByte((byte)12).putInt(value.length()).putUnencodedChars(value).hash();
    }

    @Override
    public HashCode createBoolean(boolean value) {
        return value ? this.trueHash : this.falseHash;
    }

    private static Hasher hashMap(Hasher hasher, Map<HashCode, HashCode> map) {
        hasher.putByte((byte)2);
        map.entrySet().stream().sorted(MAP_ENTRY_ORDER).forEach(entry -> hasher.putBytes(entry.getKey().asBytes()).putBytes(entry.getValue().asBytes()));
        hasher.putByte((byte)3);
        return hasher;
    }

    static Hasher hashMap(Hasher hasher, Stream<Pair<HashCode, HashCode>> map) {
        hasher.putByte((byte)2);
        map.sorted(MAPLIKE_ENTRY_ORDER).forEach(pair -> hasher.putBytes(pair.getFirst().asBytes()).putBytes(pair.getSecond().asBytes()));
        hasher.putByte((byte)3);
        return hasher;
    }

    @Override
    public HashCode createMap(Stream<Pair<HashCode, HashCode>> map) {
        return hashMap(this.hashFunction.newHasher(), map).hash();
    }

    @Override
    public HashCode createMap(Map<HashCode, HashCode> map) {
        return hashMap(this.hashFunction.newHasher(), map).hash();
    }

    @Override
    public HashCode createList(Stream<HashCode> input) {
        Hasher hasher = this.hashFunction.newHasher();
        hasher.putByte((byte)4);
        input.forEach(hashCode -> hasher.putBytes(hashCode.asBytes()));
        hasher.putByte((byte)5);
        return hasher.hash();
    }

    @Override
    public HashCode createByteList(ByteBuffer input) {
        Hasher hasher = this.hashFunction.newHasher();
        hasher.putByte((byte)14);
        hasher.putBytes(input);
        hasher.putByte((byte)15);
        return hasher.hash();
    }

    @Override
    public HashCode createIntList(IntStream input) {
        Hasher hasher = this.hashFunction.newHasher();
        hasher.putByte((byte)16);
        input.forEach(hasher::putInt);
        hasher.putByte((byte)17);
        return hasher.hash();
    }

    @Override
    public HashCode createLongList(LongStream input) {
        Hasher hasher = this.hashFunction.newHasher();
        hasher.putByte((byte)18);
        input.forEach(hasher::putLong);
        hasher.putByte((byte)19);
        return hasher.hash();
    }

    @Override
    public HashCode remove(HashCode input, String key) {
        return input;
    }

    @Override
    public RecordBuilder<HashCode> mapBuilder() {
        return new HashOps.MapHashBuilder();
    }

    @Override
    public ListBuilder<HashCode> listBuilder() {
        return new HashOps.ListHashBuilder();
    }

    @Override
    public String toString() {
        return "Hash " + this.hashFunction;
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, HashCode input) {
        throw new UnsupportedOperationException("Can't convert from this type");
    }

    @Override
    public Number getNumberValue(HashCode input, Number defaultValue) {
        return defaultValue;
    }

    @Override
    public HashCode set(HashCode input, String key, HashCode value) {
        return input;
    }

    @Override
    public HashCode update(HashCode input, String key, Function<HashCode, HashCode> function) {
        return input;
    }

    @Override
    public HashCode updateGeneric(HashCode input, HashCode key, Function<HashCode, HashCode> function) {
        return input;
    }

    private static <T> DataResult<T> unsupported() {
        return (DataResult<T>)UNSUPPORTED_OPERATION_ERROR;
    }

    @Override
    public DataResult<HashCode> get(HashCode input, String key) {
        return unsupported();
    }

    @Override
    public DataResult<HashCode> getGeneric(HashCode input, HashCode key) {
        return unsupported();
    }

    @Override
    public DataResult<Number> getNumberValue(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<Boolean> getBooleanValue(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<String> getStringValue(HashCode input) {
        return unsupported();
    }

    boolean isEmpty(HashCode value) {
        return value.equals(this.empty);
    }

    @Override
    public DataResult<HashCode> mergeToList(HashCode list, HashCode value) {
        return this.isEmpty(list) ? DataResult.success(this.createList(Stream.of(value))) : unsupported();
    }

    @Override
    public DataResult<HashCode> mergeToList(HashCode list, List<HashCode> values) {
        return this.isEmpty(list) ? DataResult.success(this.createList(values.stream())) : unsupported();
    }

    @Override
    public DataResult<HashCode> mergeToMap(HashCode map, HashCode key, HashCode value) {
        return this.isEmpty(map) ? DataResult.success(this.createMap(Map.of(key, value))) : unsupported();
    }

    @Override
    public DataResult<HashCode> mergeToMap(HashCode map, Map<HashCode, HashCode> values) {
        return this.isEmpty(map) ? DataResult.success(this.createMap(values)) : unsupported();
    }

    @Override
    public DataResult<HashCode> mergeToMap(HashCode map, MapLike<HashCode> values) {
        return this.isEmpty(map) ? DataResult.success(this.createMap(values.entries())) : unsupported();
    }

    @Override
    public DataResult<Stream<Pair<HashCode, HashCode>>> getMapValues(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<Consumer<BiConsumer<HashCode, HashCode>>> getMapEntries(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<Stream<HashCode>> getStream(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<Consumer<Consumer<HashCode>>> getList(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<MapLike<HashCode>> getMap(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<IntStream> getIntStream(HashCode input) {
        return unsupported();
    }

    @Override
    public DataResult<LongStream> getLongStream(HashCode input) {
        return unsupported();
    }

    class ListHashBuilder extends AbstractListBuilder<HashCode, Hasher> {
        public ListHashBuilder() {
            super(HashOps.this);
        }

        @Override
        protected Hasher initBuilder() {
            return HashOps.this.hashFunction.newHasher().putByte((byte)4);
        }

        @Override
        protected Hasher append(Hasher builder, HashCode value) {
            return builder.putBytes(value.asBytes());
        }

        @Override
        protected DataResult<HashCode> build(Hasher builder, HashCode prefix) {
            assert prefix.equals(HashOps.this.empty);

            builder.putByte((byte)5);
            return DataResult.success(builder.hash());
        }
    }

    final class MapHashBuilder extends AbstractUniversalBuilder<HashCode, List<Pair<HashCode, HashCode>>> {
        public MapHashBuilder() {
            super(HashOps.this);
        }

        @Override
        protected List<Pair<HashCode, HashCode>> initBuilder() {
            return new ArrayList<>();
        }

        @Override
        protected List<Pair<HashCode, HashCode>> append(HashCode key, HashCode value, List<Pair<HashCode, HashCode>> builder) {
            builder.add(Pair.of(key, value));
            return builder;
        }

        @Override
        protected DataResult<HashCode> build(List<Pair<HashCode, HashCode>> builder, HashCode prefix) {
            assert HashOps.this.isEmpty(prefix);

            return DataResult.success(HashOps.hashMap(HashOps.this.hashFunction.newHasher(), builder.stream()).hash());
        }
    }
}
