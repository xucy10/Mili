package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractStringBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class NbtOps implements DynamicOps<Tag> {
    public static final NbtOps INSTANCE = new NbtOps();

    private NbtOps() {
    }

    @Override
    public Tag empty() {
        return EndTag.INSTANCE;
    }

    @Override
    public Tag emptyList() {
        return new ListTag();
    }

    @Override
    public Tag emptyMap() {
        return new CompoundTag();
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, Tag input) {
        return (U)(switch (input) {
            case EndTag endTag -> (Object)outOps.empty();
            case ByteTag(byte var34) -> (Object)outOps.createByte(var34);
            case ShortTag(short var35) -> (Object)outOps.createShort(var35);
            case IntTag(int var36) -> (Object)outOps.createInt(var36);
            case LongTag(long var37) -> (Object)outOps.createLong(var37);
            case FloatTag(float var38) -> (Object)outOps.createFloat(var38);
            case DoubleTag(double var39) -> (Object)outOps.createDouble(var39);
            case ByteArrayTag byteArrayTag -> (Object)outOps.createByteList(ByteBuffer.wrap(byteArrayTag.getAsByteArray()));
            case StringTag(String var40) -> (Object)outOps.createString(var40);
            case ListTag listTag -> (Object)this.convertList(outOps, listTag);
            case CompoundTag compoundTag -> (Object)this.convertMap(outOps, compoundTag);
            case IntArrayTag intArrayTag -> (Object)outOps.createIntList(Arrays.stream(intArrayTag.getAsIntArray()));
            case LongArrayTag longArrayTag -> (Object)outOps.createLongList(Arrays.stream(longArrayTag.getAsLongArray()));
            default -> throw new MatchException(null, null);
        });
    }

    @Override
    public DataResult<Number> getNumberValue(Tag input) {
        return input.asNumber().map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a number"));
    }

    @Override
    public Tag createNumeric(Number i) {
        return DoubleTag.valueOf(i.doubleValue());
    }

    @Override
    public Tag createByte(byte value) {
        return ByteTag.valueOf(value);
    }

    @Override
    public Tag createShort(short value) {
        return ShortTag.valueOf(value);
    }

    @Override
    public Tag createInt(int value) {
        return IntTag.valueOf(value);
    }

    @Override
    public Tag createLong(long value) {
        return LongTag.valueOf(value);
    }

    @Override
    public Tag createFloat(float value) {
        return FloatTag.valueOf(value);
    }

    @Override
    public Tag createDouble(double value) {
        return DoubleTag.valueOf(value);
    }

    @Override
    public Tag createBoolean(boolean value) {
        return ByteTag.valueOf(value);
    }

    @Override
    public DataResult<String> getStringValue(Tag input) {
        return input instanceof StringTag(String var4) ? DataResult.success(var4) : DataResult.error(() -> "Not a string");
    }

    @Override
    public Tag createString(String value) {
        return StringTag.valueOf(value);
    }

    @Override
    public DataResult<Tag> mergeToList(Tag list, Tag value) {
        return createCollector(list)
            .map(listCollector -> DataResult.success(listCollector.accept(value).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    @Override
    public DataResult<Tag> mergeToList(Tag list, List<Tag> value) {
        return createCollector(list)
            .map(listCollector -> DataResult.success(listCollector.acceptAll(value).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    @Override
    public DataResult<Tag> mergeToMap(Tag map, Tag key, Tag value) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        } else if (key instanceof StringTag(String var10)) {
            String compoundTag = var10;
            CompoundTag compoundTag1 = map instanceof CompoundTag compoundTagx ? compoundTagx.shallowCopy() : new CompoundTag();
            compoundTag1.put(compoundTag, value);
            return DataResult.success(compoundTag1);
        } else {
            return DataResult.error(() -> "key is not a string: " + key, map);
        }
    }

    @Override
    public DataResult<Tag> mergeToMap(Tag map, MapLike<Tag> values) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        } else {
            Iterator<Pair<Tag, Tag>> iterator = values.entries().iterator();
            if (!iterator.hasNext()) {
                return map == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(map);
            } else {
                CompoundTag compoundTag1 = map instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
                List<Tag> list = new ArrayList<>();
                iterator.forEachRemaining(pair -> {
                    Tag tag = pair.getFirst();
                    if (tag instanceof StringTag(String string)) {
                        compoundTag1.put(string, pair.getSecond());
                    } else {
                        list.add(tag);
                    }
                });
                return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundTag1) : DataResult.success(compoundTag1);
            }
        }
    }

    @Override
    public DataResult<Tag> mergeToMap(Tag tag, Map<Tag, Tag> map) {
        if (!(tag instanceof CompoundTag) && !(tag instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
        } else if (map.isEmpty()) {
            return tag == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(tag);
        } else {
            CompoundTag compoundTag1 = tag instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
            List<Tag> list = new ArrayList<>();

            for (Entry<Tag, Tag> entry : map.entrySet()) {
                Tag tag1 = entry.getKey();
                if (tag1 instanceof StringTag(String var10)) {
                    compoundTag1.put(var10, entry.getValue());
                } else {
                    list.add(tag1);
                }
            }

            return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundTag1) : DataResult.success(compoundTag1);
        }
    }

    @Override
    public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag input) {
        return input instanceof CompoundTag compoundTag
            ? DataResult.success(compoundTag.entrySet().stream().map(entry -> Pair.of(this.createString(entry.getKey()), entry.getValue())))
            : DataResult.error(() -> "Not a map: " + input);
    }

    @Override
    public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(Tag input) {
        return input instanceof CompoundTag compoundTag ? DataResult.success(biConsumer -> {
            for (Entry<String, Tag> entry : compoundTag.entrySet()) {
                biConsumer.accept(this.createString(entry.getKey()), entry.getValue());
            }
        }) : DataResult.error(() -> "Not a map: " + input);
    }

    @Override
    public DataResult<MapLike<Tag>> getMap(Tag input) {
        return input instanceof CompoundTag compoundTag ? DataResult.success(new MapLike<Tag>() {
            @Override
            public @Nullable Tag get(Tag tag) {
                if (tag instanceof StringTag(String var4)) {
                    return compoundTag.get(var4);
                } else {
                    throw new UnsupportedOperationException("Cannot get map entry with non-string key: " + tag);
                }
            }

            @Override
            public @Nullable Tag get(String string) {
                return compoundTag.get(string);
            }

            @Override
            public Stream<Pair<Tag, Tag>> entries() {
                return compoundTag.entrySet().stream().map(entry -> Pair.of(NbtOps.this.createString(entry.getKey()), entry.getValue()));
            }

            @Override
            public String toString() {
                return "MapLike[" + compoundTag + "]";
            }
        }) : DataResult.error(() -> "Not a map: " + input);
    }

    @Override
    public Tag createMap(Stream<Pair<Tag, Tag>> map) {
        CompoundTag compoundTag = new CompoundTag();
        map.forEach(pair -> {
            Tag tag = pair.getFirst();
            Tag tag1 = pair.getSecond();
            if (tag instanceof StringTag(String string)) {
                compoundTag.put(string, tag1);
            } else {
                throw new UnsupportedOperationException("Cannot create map with non-string key: " + tag);
            }
        });
        return compoundTag;
    }

    @Override
    public DataResult<Stream<Tag>> getStream(Tag input) {
        return input instanceof CollectionTag collectionTag ? DataResult.success(collectionTag.stream()) : DataResult.error(() -> "Not a list");
    }

    @Override
    public DataResult<Consumer<Consumer<Tag>>> getList(Tag input) {
        return input instanceof CollectionTag collectionTag ? DataResult.success(collectionTag::forEach) : DataResult.error(() -> "Not a list: " + input);
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(Tag input) {
        return input instanceof ByteArrayTag byteArrayTag
            ? DataResult.success(ByteBuffer.wrap(byteArrayTag.getAsByteArray()))
            : DynamicOps.super.getByteBuffer(input);
    }

    @Override
    public Tag createByteList(ByteBuffer input) {
        ByteBuffer byteBuffer = input.duplicate().clear();
        byte[] bytes = new byte[input.capacity()];
        byteBuffer.get(0, bytes, 0, bytes.length);
        return new ByteArrayTag(bytes);
    }

    @Override
    public DataResult<IntStream> getIntStream(Tag input) {
        return input instanceof IntArrayTag intArrayTag ? DataResult.success(Arrays.stream(intArrayTag.getAsIntArray())) : DynamicOps.super.getIntStream(input);
    }

    @Override
    public Tag createIntList(IntStream input) {
        return new IntArrayTag(input.toArray());
    }

    @Override
    public DataResult<LongStream> getLongStream(Tag input) {
        return input instanceof LongArrayTag longArrayTag
            ? DataResult.success(Arrays.stream(longArrayTag.getAsLongArray()))
            : DynamicOps.super.getLongStream(input);
    }

    @Override
    public Tag createLongList(LongStream input) {
        return new LongArrayTag(input.toArray());
    }

    @Override
    public Tag createList(Stream<Tag> input) {
        return new ListTag(input.collect(Util.toMutableList()));
    }

    @Override
    public Tag remove(Tag input, String key) {
        if (input instanceof CompoundTag compoundTag) {
            CompoundTag compoundTag1 = compoundTag.shallowCopy();
            compoundTag1.remove(key);
            return compoundTag1;
        } else {
            return input;
        }
    }

    @Override
    public String toString() {
        return "NBT";
    }

    @Override
    public RecordBuilder<Tag> mapBuilder() {
        return new NbtOps.NbtRecordBuilder();
    }

    private static Optional<NbtOps.ListCollector> createCollector(Tag value) {
        if (value instanceof EndTag) {
            return Optional.of(new NbtOps.GenericListCollector());
        } else if (value instanceof CollectionTag collectionTag) {
            if (collectionTag.isEmpty()) {
                return Optional.of(new NbtOps.GenericListCollector());
            } else {
                return switch (collectionTag) {
                    case ListTag listTag -> Optional.of(new NbtOps.GenericListCollector(listTag));
                    case ByteArrayTag byteArrayTag -> Optional.of(new NbtOps.ByteListCollector(byteArrayTag.getAsByteArray()));
                    case IntArrayTag intArrayTag -> Optional.of(new NbtOps.IntListCollector(intArrayTag.getAsIntArray()));
                    case LongArrayTag longArrayTag -> Optional.of(new NbtOps.LongListCollector(longArrayTag.getAsLongArray()));
                    default -> throw new MatchException(null, null);
                };
            }
        } else {
            return Optional.empty();
        }
    }

    static class ByteListCollector implements NbtOps.ListCollector {
        private final ByteArrayList values = new ByteArrayList();

        public ByteListCollector(byte[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof ByteTag byteTag) {
                this.values.add(byteTag.byteValue());
                return this;
            } else {
                return new NbtOps.GenericListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new ByteArrayTag(this.values.toByteArray());
        }
    }

    static class GenericListCollector implements NbtOps.ListCollector {
        private final ListTag result = new ListTag();

        GenericListCollector() {
        }

        GenericListCollector(ListTag list) {
            this.result.addAll(list);
        }

        public GenericListCollector(IntArrayList list) {
            list.forEach(i -> this.result.add(IntTag.valueOf(i)));
        }

        public GenericListCollector(ByteArrayList list) {
            list.forEach(b -> this.result.add(ByteTag.valueOf(b)));
        }

        public GenericListCollector(LongArrayList list) {
            list.forEach(l -> this.result.add(LongTag.valueOf(l)));
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            this.result.add(tag);
            return this;
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    static class IntListCollector implements NbtOps.ListCollector {
        private final IntArrayList values = new IntArrayList();

        public IntListCollector(int[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof IntTag intTag) {
                this.values.add(intTag.intValue());
                return this;
            } else {
                return new NbtOps.GenericListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new IntArrayTag(this.values.toIntArray());
        }
    }

    interface ListCollector {
        NbtOps.ListCollector accept(Tag tag);

        default NbtOps.ListCollector acceptAll(Iterable<Tag> tags) {
            NbtOps.ListCollector listCollector = this;

            for (Tag tag : tags) {
                listCollector = listCollector.accept(tag);
            }

            return listCollector;
        }

        default NbtOps.ListCollector acceptAll(Stream<Tag> tags) {
            return this.acceptAll(tags::iterator);
        }

        Tag result();
    }

    static class LongListCollector implements NbtOps.ListCollector {
        private final LongArrayList values = new LongArrayList();

        public LongListCollector(long[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof LongTag longTag) {
                this.values.add(longTag.longValue());
                return this;
            } else {
                return new NbtOps.GenericListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new LongArrayTag(this.values.toLongArray());
        }
    }

    class NbtRecordBuilder extends AbstractStringBuilder<Tag, CompoundTag> {
        protected NbtRecordBuilder() {
            super(NbtOps.this);
        }

        @Override
        protected CompoundTag initBuilder() {
            return new CompoundTag();
        }

        @Override
        protected CompoundTag append(String key, Tag value, CompoundTag tag) {
            tag.put(key, value);
            return tag;
        }

        @Override
        protected DataResult<Tag> build(CompoundTag compoundTag, Tag tag) {
            if (tag == null || tag == EndTag.INSTANCE) {
                return DataResult.success(compoundTag);
            } else if (!(tag instanceof CompoundTag compoundTag1)) {
                return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
            } else {
                CompoundTag compoundTag2 = compoundTag1.shallowCopy();

                for (Entry<String, Tag> entry : compoundTag.entrySet()) {
                    compoundTag2.put(entry.getKey(), entry.getValue());
                }

                return DataResult.success(compoundTag2);
            }
        }
    }
}
