package net.minecraft.nbt;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class CompoundTag implements Tag {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<CompoundTag> CODEC = Codec.PASSTHROUGH
        .comapFlatMap(
            tag -> {
                Tag tag1 = tag.convert(NbtOps.INSTANCE).getValue();
                return tag1 instanceof CompoundTag compoundTag
                    ? DataResult.success(compoundTag == tag.getValue() ? compoundTag.copy() : compoundTag)
                    : DataResult.error(() -> "Not a compound tag: " + tag1);
            },
            tag -> new Dynamic<>(NbtOps.INSTANCE, tag.copy())
        );
    private static final int SELF_SIZE_IN_BYTES = 48;
    private static final int MAP_ENTRY_SIZE_IN_BYTES = 32;
    public static final TagType<CompoundTag> TYPE = new TagType.VariableSize<CompoundTag>() {
        @Override
        public CompoundTag load(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            CompoundTag var3;
            try {
                var3 = loadCompound(input, accounter);
            } finally {
                accounter.popDepth();
            }

            return var3;
        }

        private static CompoundTag loadCompound(DataInput input, NbtAccounter nbtAccounter) throws IOException {
            nbtAccounter.accountBytes(48L);
            it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<String, Tag> map = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(8, 0.8f); // Paper - Reduce memory footprint of CompoundTag

            byte b;
            while ((b = input.readByte()) != 0) {
                String string = readString(input, nbtAccounter);
                Tag namedTagData = CompoundTag.readNamedTagData(TagTypes.getType(b), string, input, nbtAccounter);
                if (map.put(string, namedTagData) == null) {
                    nbtAccounter.accountBytes(36L);
                }
            }

            return new CompoundTag(map);
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            StreamTagVisitor.ValueResult var4;
            try {
                var4 = parseCompound(input, visitor, accounter);
            } finally {
                accounter.popDepth();
            }

            return var4;
        }

        private static StreamTagVisitor.ValueResult parseCompound(DataInput input, StreamTagVisitor visitor, NbtAccounter nbtAccounter) throws IOException {
            nbtAccounter.accountBytes(48L);

            byte b;
            label35:
            while ((b = input.readByte()) != 0) {
                TagType<?> type = TagTypes.getType(b);
                switch (visitor.visitEntry(type)) {
                    case HALT:
                        return StreamTagVisitor.ValueResult.HALT;
                    case BREAK:
                        StringTag.skipString(input);
                        type.skip(input, nbtAccounter);
                        break label35;
                    case SKIP:
                        StringTag.skipString(input);
                        type.skip(input, nbtAccounter);
                        break;
                    default:
                        String string = readString(input, nbtAccounter);
                        switch (visitor.visitEntry(type, string)) {
                            case HALT:
                                return StreamTagVisitor.ValueResult.HALT;
                            case BREAK:
                                type.skip(input, nbtAccounter);
                                break label35;
                            case SKIP:
                                type.skip(input, nbtAccounter);
                                break;
                            default:
                                nbtAccounter.accountBytes(36L);
                                switch (type.parse(input, visitor, nbtAccounter)) {
                                    case HALT:
                                        return StreamTagVisitor.ValueResult.HALT;
                                    case BREAK:
                                }
                        }
                }
            }

            if (b != 0) {
                while ((b = input.readByte()) != 0) {
                    StringTag.skipString(input);
                    TagTypes.getType(b).skip(input, nbtAccounter);
                }
            }

            return visitor.visitContainerEnd();
        }

        private static String readString(DataInput input, NbtAccounter accounter) throws IOException {
            String utf = input.readUTF();
            accounter.accountBytes(28L);
            accounter.accountBytes(2L, utf.length());
            return utf;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            byte b;
            try {
                while ((b = input.readByte()) != 0) {
                    StringTag.skipString(input);
                    TagTypes.getType(b).skip(input, accounter);
                }
            } finally {
                accounter.popDepth();
            }
        }

        @Override
        public String getName() {
            return "COMPOUND";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Compound";
        }
    };
    private final Map<String, Tag> tags;

    CompoundTag(Map<String, Tag> tags) {
        this.tags = tags;
    }

    public CompoundTag() {
        this(new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(8, 0.8f)); // Paper - Reduce memory footprint of CompoundTag
    }

    @Override
    public void write(DataOutput output) throws IOException {
        for (String string : this.tags.keySet()) {
            Tag tag = this.tags.get(string);
            writeNamedTag(string, tag, output);
        }

        output.writeByte(0);
    }

    @Override
    public int sizeInBytes() {
        int i = 48;

        for (Entry<String, Tag> entry : this.tags.entrySet()) {
            i += 28 + 2 * entry.getKey().length();
            i += 36;
            i += entry.getValue().sizeInBytes();
        }

        return i;
    }

    public Set<String> keySet() {
        return this.tags.keySet();
    }

    public Set<Entry<String, Tag>> entrySet() {
        return this.tags.entrySet();
    }

    public Collection<Tag> values() {
        return this.tags.values();
    }

    public void forEach(BiConsumer<String, Tag> action) {
        this.tags.forEach(action);
    }

    @Override
    public byte getId() {
        return Tag.TAG_COMPOUND;
    }

    @Override
    public TagType<CompoundTag> getType() {
        return TYPE;
    }

    public int size() {
        return this.tags.size();
    }

    public @Nullable Tag put(String key, Tag value) {
        return this.tags.put(key, value);
    }

    public void putByte(String key, byte value) {
        this.tags.put(key, ByteTag.valueOf(value));
    }

    public void putShort(String key, short value) {
        this.tags.put(key, ShortTag.valueOf(value));
    }

    public void putInt(String key, int value) {
        this.tags.put(key, IntTag.valueOf(value));
    }

    public void putLong(String key, long value) {
        this.tags.put(key, LongTag.valueOf(value));
    }

    public void putFloat(String key, float value) {
        this.tags.put(key, FloatTag.valueOf(value));
    }

    public void putDouble(String key, double value) {
        this.tags.put(key, DoubleTag.valueOf(value));
    }

    public void putString(String key, String value) {
        this.tags.put(key, StringTag.valueOf(value));
    }

    public void putByteArray(String key, byte[] value) {
        this.tags.put(key, new ByteArrayTag(value));
    }

    public void putIntArray(String key, int[] value) {
        this.tags.put(key, new IntArrayTag(value));
    }

    public void putLongArray(String key, long[] value) {
        this.tags.put(key, new LongArrayTag(value));
    }

    public void putBoolean(String key, boolean value) {
        this.tags.put(key, ByteTag.valueOf(value));
    }

    public @Nullable Tag get(String key) {
        return this.tags.get(key);
    }

    public boolean contains(String key) {
        return this.tags.containsKey(key);
    }

    private Optional<Tag> getOptional(String key) {
        return Optional.ofNullable(this.tags.get(key));
    }

    public Optional<Byte> getByte(String key) {
        return this.getOptional(key).flatMap(Tag::asByte);
    }

    public byte getByteOr(String key, byte defaultValue) {
        return this.tags.get(key) instanceof NumericTag numericTag ? numericTag.byteValue() : defaultValue;
    }

    public Optional<Short> getShort(String key) {
        return this.getOptional(key).flatMap(Tag::asShort);
    }

    public short getShortOr(String key, short defaultValue) {
        return this.tags.get(key) instanceof NumericTag numericTag ? numericTag.shortValue() : defaultValue;
    }

    public Optional<Integer> getInt(String key) {
        return this.getOptional(key).flatMap(Tag::asInt);
    }

    public int getIntOr(String key, int defaultValue) {
        return this.tags.get(key) instanceof NumericTag numericTag ? numericTag.intValue() : defaultValue;
    }

    public Optional<Long> getLong(String key) {
        return this.getOptional(key).flatMap(Tag::asLong);
    }

    public long getLongOr(String key, long defaultValue) {
        return this.tags.get(key) instanceof NumericTag numericTag ? numericTag.longValue() : defaultValue;
    }

    public Optional<Float> getFloat(String key) {
        return this.getOptional(key).flatMap(Tag::asFloat);
    }

    public float getFloatOr(String key, float defaultValue) {
        return this.tags.get(key) instanceof NumericTag numericTag ? numericTag.floatValue() : defaultValue;
    }

    public Optional<Double> getDouble(String key) {
        return this.getOptional(key).flatMap(Tag::asDouble);
    }

    public double getDoubleOr(String key, double defaultValue) {
        return this.tags.get(key) instanceof NumericTag numericTag ? numericTag.doubleValue() : defaultValue;
    }

    public Optional<String> getString(String key) {
        return this.getOptional(key).flatMap(Tag::asString);
    }

    public String getStringOr(String key, String defaultValue) {
        return this.tags.get(key) instanceof StringTag(String var8) ? var8 : defaultValue;
    }

    public Optional<byte[]> getByteArray(String key) {
        return this.tags.get(key) instanceof ByteArrayTag byteArrayTag ? Optional.of(byteArrayTag.getAsByteArray()) : Optional.empty();
    }

    public Optional<int[]> getIntArray(String key) {
        return this.tags.get(key) instanceof IntArrayTag intArrayTag ? Optional.of(intArrayTag.getAsIntArray()) : Optional.empty();
    }

    public Optional<long[]> getLongArray(String key) {
        return this.tags.get(key) instanceof LongArrayTag longArrayTag ? Optional.of(longArrayTag.getAsLongArray()) : Optional.empty();
    }

    public Optional<CompoundTag> getCompound(String key) {
        return this.tags.get(key) instanceof CompoundTag compoundTag ? Optional.of(compoundTag) : Optional.empty();
    }

    public CompoundTag getCompoundOrEmpty(String key) {
        return this.getCompound(key).orElseGet(CompoundTag::new);
    }

    public Optional<ListTag> getList(String key) {
        return this.tags.get(key) instanceof ListTag listTag ? Optional.of(listTag) : Optional.empty();
    }

    public ListTag getListOrEmpty(String key) {
        return this.getList(key).orElseGet(ListTag::new);
    }

    public Optional<Boolean> getBoolean(String key) {
        return this.getOptional(key).flatMap(Tag::asBoolean);
    }

    public boolean getBooleanOr(String key, boolean defaultValue) {
        return this.getByteOr(key, (byte)(defaultValue ? 1 : 0)) != 0;
    }

    public @Nullable Tag remove(String key) {
        return this.tags.remove(key);
    }

    @Override
    public String toString() {
        StringTagVisitor stringTagVisitor = new StringTagVisitor();
        stringTagVisitor.visitCompound(this);
        return stringTagVisitor.build();
    }

    public boolean isEmpty() {
        return this.tags.isEmpty();
    }

    protected CompoundTag shallowCopy() {
        return new CompoundTag(new HashMap<>(this.tags));
    }

    @Override
    public CompoundTag copy() {
        // Paper start - Reduce memory footprint of CompoundTag
        it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<String, Tag> ret = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(this.tags.size(), 0.8f);
        java.util.Iterator<java.util.Map.Entry<String, Tag>> iterator = (this.tags instanceof it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap) ? ((it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap)this.tags).object2ObjectEntrySet().fastIterator() : this.tags.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Tag> entry = iterator.next();
            ret.put(entry.getKey(), entry.getValue().copy());
        }

        return new CompoundTag(ret);
        // Paper end - Reduce memory footprint of CompoundTag
    }

    @Override
    public Optional<CompoundTag> asCompound() {
        return Optional.of(this);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CompoundTag && Objects.equals(this.tags, ((CompoundTag)other).tags);
    }

    @Override
    public int hashCode() {
        return this.tags.hashCode();
    }

    private static void writeNamedTag(String name, Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            output.writeUTF(name);
            tag.write(output);
        }
    }

    static Tag readNamedTagData(TagType<?> type, String name, DataInput input, NbtAccounter accounter) {
        try {
            return type.load(input, accounter);
        } catch (IOException var7) {
            CrashReport crashReport = CrashReport.forThrowable(var7, "Loading NBT data");
            CrashReportCategory crashReportCategory = crashReport.addCategory("NBT Tag");
            crashReportCategory.setDetail("Tag name", name);
            crashReportCategory.setDetail("Tag type", type.getName());
            throw new ReportedNbtException(crashReport);
        }
    }

    public CompoundTag merge(CompoundTag other) {
        for (String string : other.tags.keySet()) {
            Tag tag = other.tags.get(string);
            if (tag instanceof CompoundTag compoundTag && this.tags.get(string) instanceof CompoundTag compoundTag1) {
                compoundTag1.merge(compoundTag);
            } else {
                this.put(string, tag.copy());
            }
        }

        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitCompound(this);
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        for (Entry<String, Tag> entry : this.tags.entrySet()) {
            Tag tag = entry.getValue();
            TagType<?> type = tag.getType();
            StreamTagVisitor.EntryResult entryResult = visitor.visitEntry(type);
            switch (entryResult) {
                case HALT:
                    return StreamTagVisitor.ValueResult.HALT;
                case BREAK:
                    return visitor.visitContainerEnd();
                case SKIP:
                    break;
                default:
                    entryResult = visitor.visitEntry(type, entry.getKey());
                    switch (entryResult) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            return visitor.visitContainerEnd();
                        case SKIP:
                            break;
                        default:
                            StreamTagVisitor.ValueResult valueResult = tag.accept(visitor);
                            switch (valueResult) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    return visitor.visitContainerEnd();
                            }
                    }
            }
        }

        return visitor.visitContainerEnd();
    }

    public <T> void store(String key, Codec<T> codec, T data) {
        this.store(key, codec, NbtOps.INSTANCE, data);
    }

    public <T> void storeNullable(String key, Codec<T> codec, @Nullable T data) {
        if (data != null) {
            this.store(key, codec, data);
        }
    }

    public <T> void store(String key, Codec<T> codec, DynamicOps<Tag> ops, T data) {
        this.put(key, codec.encodeStart(ops, data).getOrThrow());
    }

    public <T> void storeNullable(String key, Codec<T> codec, DynamicOps<Tag> ops, @Nullable T data) {
        if (data != null) {
            this.store(key, codec, ops, data);
        }
    }

    public <T> void store(MapCodec<T> mapCodec, T data) {
        this.store(mapCodec, NbtOps.INSTANCE, data);
    }

    public <T> void store(MapCodec<T> mapCodec, DynamicOps<Tag> ops, T data) {
        this.merge((CompoundTag)mapCodec.encoder().encodeStart(ops, data).getOrThrow());
    }

    public <T> Optional<T> read(String key, Codec<T> codec) { // Paper - option to read via codec without logging errors - diff on change
        return this.read(key, codec, NbtOps.INSTANCE);
    }

    public <T> Optional<T> read(String key, Codec<T> codec, DynamicOps<Tag> ops) { // Paper - option to read via codec without logging errors - diff on change
        Tag tag = this.get(key);
        return tag == null
            ? Optional.empty()
            : codec.parse(ops, tag).resultOrPartial(string -> LOGGER.error("Failed to read field ({}={}): {}", key, tag, string));
    }

    public <T> Optional<T> read(MapCodec<T> mapCodec) { // Paper - option to read via codec without logging errors - diff on change
        return this.read(mapCodec, NbtOps.INSTANCE);
    }

    public <T> Optional<T> read(MapCodec<T> mapCodec, DynamicOps<Tag> ops) { // Paper - option to read via codec without logging errors - diff on change
        return mapCodec.decode(ops, ops.getMap(this).getOrThrow()).resultOrPartial(string -> LOGGER.error("Failed to read value ({}): {}", this, string));
    }

    // Paper start - option to read via codec without logging errors
    // The below methods are 1 to 1 copies of the above-defined read methods without the logging part.
    // Copying was chosen over overloading the read methods as a boolean parameter to mark a method as quiet
    // is not intuitive and would require even more overloads.
    // Not a lot of diff in these methods is expected
    public <T> Optional<T> readQuiet(String key, Codec<T> codec) {
        return this.readQuiet(key, codec, NbtOps.INSTANCE);
    }

    public <T> Optional<T> readQuiet(String key, Codec<T> codec, DynamicOps<Tag> ops) {
        Tag tag = this.get(key);
        return tag == null
            ? Optional.empty()
            : codec.parse(ops, tag).resultOrPartial();
    }

    public <T> Optional<T> readQuiet(MapCodec<T> mapCodec) {
        return this.readQuiet(mapCodec, NbtOps.INSTANCE);
    }

    public <T> Optional<T> readQuiet(MapCodec<T> mapCodec, DynamicOps<Tag> ops) {
        return mapCodec.decode(ops, ops.getMap(this).getOrThrow()).resultOrPartial();
    }
    // Paper end - option to read via codec without logging errors
}
